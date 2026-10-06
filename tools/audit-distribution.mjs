import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';

// No extraction or execution of distribution content. Supports ordinary ZIP/JAR.
function zip(file) {
    const buffer = fs.readFileSync(file);
    let end = buffer.length - 22;
    while (end >= Math.max(0, buffer.length - 65557) && buffer.readUInt32LE(end) !== 0x06054b50) end--;
    if (end < 0 || buffer.readUInt32LE(end) !== 0x06054b50) throw new Error('ZIP end record missing');
    const count = buffer.readUInt16LE(end + 10);
    let offset = buffer.readUInt32LE(end + 16);
    const entries = new Map();
    for (let i = 0; i < count; i++) {
        if (buffer.readUInt32LE(offset) !== 0x02014b50) throw new Error('Invalid ZIP directory');
        const method = buffer.readUInt16LE(offset + 10);
        const size = buffer.readUInt32LE(offset + 20);
        const nameLength = buffer.readUInt16LE(offset + 28);
        const name = buffer.toString('utf8', offset + 46, offset + 46 + nameLength);
        const local = buffer.readUInt32LE(offset + 42);
        if (!name.endsWith('/')) {
            const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
            const data = buffer.subarray(start, start + size);
            if (method !== 0 && method !== 8) throw new Error(`Unsupported compression: ${name}`);
            entries.set(name, method === 8 ? zlib.inflateRawSync(data) : data);
        }
        offset += 46 + nameLength + buffer.readUInt16LE(offset + 30) + buffer.readUInt16LE(offset + 32);
    }
    return entries;
}

// Read public/protected JVM member signatures; ignore synthetic bridge members.
function members(buffer) {
    let offset = 8;
    const u2 = () => { const value = buffer.readUInt16BE(offset); offset += 2; return value; };
    const pool = [];
    const count = u2();
    for (let i = 1; i < count; i++) {
        const tag = buffer[offset++];
        if (tag === 1) { const length = u2(); pool[i] = buffer.toString('utf8', offset, offset + length); offset += length; }
        else if ([3, 4, 9, 10, 11, 12, 17, 18].includes(tag)) offset += 4;
        else if ([5, 6].includes(tag)) { offset += 8; i++; }
        else if ([7, 8, 16, 19, 20].includes(tag)) offset += 2;
        else if (tag === 15) offset += 3;
        else throw new Error(`Unknown class constant ${tag}`);
    }
    offset += 6;
    const interfaces = u2();
    offset += interfaces * 2;
    const result = [];
    for (const kind of ['field', 'method']) {
        const count = u2();
        for (let i = 0; i < count; i++) {
            const access = u2(), name = pool[u2()], descriptor = pool[u2()];
            if ((access & 5) && !(access & 0x1000)) result.push(`${kind} ${name} ${descriptor}`);
            const attributes = u2();
            for (let j = 0; j < attributes; j++) { offset += 2; const length = buffer.readUInt32BE(offset); offset += 4 + length; }
        }
    }
    return result.sort();
}

const [reference, current, output] = process.argv.slice(2);
if (!output) throw new Error('Usage: node tools/audit-distribution.mjs reference.zip current-remapped-fabric.jar output.json');
const old = zip(reference), now = zip(current);
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
const classNames = entries => [...entries.keys()].filter(name => name.startsWith('foundry/veil/') && name.endsWith('.class')).sort();
const resourceNames = entries => [...entries.keys()].filter(name => name.startsWith('assets/') || name.startsWith('resourcepacks/')).sort();
const oldClasses = classNames(old), newClasses = classNames(now);
const missingClasses = oldClasses.filter(name => !now.has(name));
const changedApi = oldClasses.filter(name => name.startsWith('foundry/veil/api/') && now.has(name)).flatMap(name => {
    const before = members(old.get(name)), after = members(now.get(name));
    const removed = before.filter(member => !after.includes(member));
    const added = after.filter(member => !before.includes(member));
    return removed.length || added.length ? [{ name, removed, added }] : [];
});
const oldResources = resourceNames(old), newResources = resourceNames(now);
const report = {
    schemaVersion: 1,
    reference: { file: path.basename(reference), sha256: hash(fs.readFileSync(reference)), metadata: JSON.parse(old.get('fabric.mod.json')) },
    current: { file: path.basename(current), sha256: hash(fs.readFileSync(current)), metadata: JSON.parse(now.get('fabric.mod.json')) },
    limitations: [
        'Binary signatures use intermediary Fabric mappings in both artifacts; absence does not imply missing functionality.',
        'Public/protected members are compared only in retained API classes, including nested classes. No bytecode equivalence or performance claim.',
        'Reference is a Fabric distribution; it provides no evidence of old NeoForge behavior. Nested third-party jars are excluded.',
        'Current jar may contain working-branch additions. Read the narrative audit for baseline decisions.'
    ],
    counts: { oldClasses: oldClasses.length, currentClasses: newClasses.length, missingClasses: missingClasses.length, changedApiClasses: changedApi.length, oldResources: oldResources.length, currentResources: newResources.length },
    missingClasses,
    addedClasses: newClasses.filter(name => !old.has(name)),
    changedApi,
    missingResources: oldResources.filter(name => !now.has(name)),
    changedResources: oldResources.filter(name => now.has(name) && !old.get(name).equals(now.get(name))),
    identicalResources: oldResources.filter(name => now.has(name) && old.get(name).equals(now.get(name))),
    addedResources: newResources.filter(name => !old.has(name))
};
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report.counts));
console.log(`Reference SHA-256: ${report.reference.sha256}`);
console.log(`Report: ${output}`);
