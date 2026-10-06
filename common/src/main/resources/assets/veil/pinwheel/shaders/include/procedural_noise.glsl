// Deterministic procedural noise adapted from Veil 2.1.0's legacy noise include.
// No implicit camera, time uniform, texture binding, or extension dependency.
float veilHash13(vec3 position) {
    position = fract(position * 0.1031);
    position += dot(position, position.zyx + 31.32);
    return fract((position.x + position.y) * position.z);
}

// Smooth value noise in [0, 1]. Coordinates should remain near the origin.
float veilValueNoise3(vec3 position) {
    vec3 cell = floor(position);
    vec3 fraction = fract(position);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    float a = mix(veilHash13(cell), veilHash13(cell + vec3(1, 0, 0)), fraction.x);
    float b = mix(veilHash13(cell + vec3(0, 1, 0)), veilHash13(cell + vec3(1, 1, 0)), fraction.x);
    float c = mix(veilHash13(cell + vec3(0, 0, 1)), veilHash13(cell + vec3(1, 0, 1)), fraction.x);
    float d = mix(veilHash13(cell + vec3(0, 1, 1)), veilHash13(cell + vec3(1, 1, 1)), fraction.x);
    return mix(mix(a, b, fraction.y), mix(c, d, fraction.y), fraction.z);
}

// Normalized fBm in [0, 1], with an explicit maximum budget of eight octaves.
float veilFbm3(vec3 position, int octaves) {
    int count = clamp(octaves, 0, 8);
    float sum = 0.0;
    float weight = 0.5;
    float total = 0.0;
    for (int octave = 0; octave < 8; octave++) {
        if (octave >= count) break;
        sum += veilValueNoise3(position) * weight;
        total += weight;
        position *= 2.0;
        weight *= 0.5;
    }
    return total > 0.0 ? sum / total : 0.0;
}

// Domain-warped energy noise. Time is supplied explicitly by the caller.
float veilEnergyNoise3(vec3 position, float timeSeconds) {
    vec3 p = position * 0.7;
    float time = timeSeconds * 0.5;
    vec3 warp = vec3(veilFbm3(p - time * 1.6, 2),
                     veilFbm3(p + time * 1.7, 2),
                     veilFbm3(p + time * 1.5, 2));
    return veilFbm3(position + (warp - 0.5) * 0.35, 3);
}
