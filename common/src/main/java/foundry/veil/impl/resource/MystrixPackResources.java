package foundry.veil.impl.resource;

import foundry.veil.ext.PackResourcesExtension;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceFilterSection;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;

/**
 * Exposes legacy Pinwheel directories as Mystrix resources within each pack.
 * Resolving aliases before Minecraft merges packs preserves pack priority and
 * resource stacks. A canonical file wins over its legacy alias in the same pack.
 */
public final class MystrixPackResources implements PackResources, PackResourcesExtension {
    public static final String ROOT = "mystrix/";
    public static final String LEGACY_ROOT = "pinwheel/";
    private final PackResources delegate;

    public MystrixPackResources(PackResources delegate) {
        this.delegate = delegate;
    }

    public static ResourceLocation canonical(ResourceLocation id) {
        return id.getPath().startsWith(LEGACY_ROOT)
                ? id.withPath(ROOT + id.getPath().substring(LEGACY_ROOT.length())) : id;
    }

    private static ResourceLocation legacy(ResourceLocation id) {
        return id.withPath(LEGACY_ROOT + id.getPath().substring(ROOT.length()));
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES) return this.delegate.getResource(type, id);
        ResourceLocation canonical = canonical(id);
        IoSupplier<InputStream> resource = this.delegate.getResource(type, canonical);
        // Do not attach stale legacy metadata to a canonical file in the same pack.
        if (resource == null && canonical.getPath().startsWith(ROOT) && canonical.getPath().endsWith(".mcmeta")) {
            ResourceLocation base = canonical.withPath(canonical.getPath().substring(0, canonical.getPath().length() - 7));
            if (this.delegate.getResource(type, base) != null) return null;
        }
        return resource != null || !canonical.getPath().startsWith(ROOT)
                ? resource : this.delegate.getResource(type, legacy(canonical));
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            this.delegate.listResources(type, namespace, prefix, output);
            return;
        }
        boolean legacyRequest = prefix.equals("pinwheel") || prefix.startsWith(LEGACY_ROOT);
        if (!prefix.isEmpty() && !legacyRequest && !prefix.equals("mystrix") && !prefix.startsWith(ROOT)) {
            // Vanilla textures/fonts and unrelated mod directories need no merge.
            this.delegate.listResources(type, namespace, prefix, output);
            return;
        }
        String canonicalPrefix = legacyRequest ? "mystrix" + prefix.substring("pinwheel".length()) : prefix;
        Map<ResourceLocation, IoSupplier<InputStream>> resources = new LinkedHashMap<>();
        if (canonicalPrefix.equals("mystrix") || canonicalPrefix.startsWith(ROOT)) {
            String legacyPrefix = "pinwheel" + canonicalPrefix.substring("mystrix".length());
            this.delegate.listResources(type, namespace, legacyPrefix, (id, supplier) -> resources.put(canonical(id), supplier));
            this.delegate.listResources(type, namespace, canonicalPrefix, resources::put);
        } else {
            // Also normalize aliases when enumerating an entire namespace.
            this.delegate.listResources(type, namespace, prefix, (id, supplier) -> {
                ResourceLocation canonical = canonical(id);
                if (canonical.equals(id)) resources.put(canonical, supplier);
                else resources.putIfAbsent(canonical, supplier);
            });
        }
        resources.forEach((id, supplier) -> {
            IoSupplier<InputStream> selected = id.getPath().endsWith(".mcmeta") ? this.getResource(type, id) : supplier;
            if (selected != null) output.accept(legacyRequest ? legacy(id) : id, selected);
        });
    }

    @Override public @Nullable IoSupplier<InputStream> getRootResource(String... elements) { return this.delegate.getRootResource(elements); }
    @Override public Set<String> getNamespaces(PackType type) { return this.delegate.getNamespaces(type); }
    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        T metadata = this.delegate.getMetadataSection(serializer);
        if (serializer == ResourceFilterSection.TYPE && metadata instanceof ResourceFilterSection filter) {
            // Filters must cover both spellings of a logical path as well.
            return (T) new ResourceFilterSection(java.util.List.of()) {
                @Override public boolean isNamespaceFiltered(String namespace) { return filter.isNamespaceFiltered(namespace); }
                @Override public boolean isPathFiltered(String path) {
                    if (filter.isPathFiltered(path)) return true;
                    if (path.startsWith(ROOT)) return filter.isPathFiltered(LEGACY_ROOT + path.substring(ROOT.length()));
                    if (path.startsWith(LEGACY_ROOT)) return filter.isPathFiltered(ROOT + path.substring(LEGACY_ROOT.length()));
                    return false;
                }
            };
        }
        return metadata;
    }
    @Override public PackLocationInfo location() { return this.delegate.location(); }
    @Override public void close() { this.delegate.close(); }

    @Override public @Nullable IoSupplier<InputStream> veil$getIcon() {
        return this.delegate instanceof PackResourcesExtension ext ? ext.veil$getIcon() : this.delegate.getRootResource("pack.png");
    }
    @Override public boolean veil$blurIcon() { return this.delegate instanceof PackResourcesExtension ext && ext.veil$blurIcon(); }
    @Override public boolean veil$isStatic() { return !(this.delegate instanceof PackResourcesExtension ext) || ext.veil$isStatic(); }
    @Override public List<Path> veil$getRawResourceRoots() { return this.delegate instanceof PackResourcesExtension ext ? ext.veil$getRawResourceRoots() : List.of(); }
    @Override public Stream<PackResources> veil$listPacks() {
        return this.delegate instanceof PackResourcesExtension ext
                ? ext.veil$listPacks().map(pack -> pack == this.delegate ? this : new MystrixPackResources(pack)) : Stream.of(this);
    }
    @Override public void veil$listResources(PackResourceConsumer consumer) {
        if (this.delegate instanceof PackResourcesExtension ext) {
            // Preserve real file paths for editor writes and hot reload watchers.
            Map<EditorKey, EditorResource> entries = new LinkedHashMap<>();
            ext.veil$listResources((type, id, packPath, filePath, devPath) -> {
                if (type != PackType.CLIENT_RESOURCES || (!id.getPath().startsWith(ROOT) && !id.getPath().startsWith(LEGACY_ROOT))) {
                    consumer.accept(type, id, packPath, filePath, devPath);
                    return;
                }
                ResourceLocation canonical = type == PackType.CLIENT_RESOURCES ? canonical(id) : id;
                EditorKey key = new EditorKey(type, canonical);
                EditorResource value = new EditorResource(packPath, filePath, devPath);
                if (canonical.equals(id)) entries.put(key, value); else entries.putIfAbsent(key, value);
            });
            entries.forEach((key, value) -> consumer.accept(key.type, key.id, value.packPath, value.filePath, value.devPath));
        } else {
            for (PackType type : PackType.values()) for (String namespace : this.getNamespaces(type)) {
                this.listResources(type, namespace, "", (id, supplier) -> consumer.accept(type, id, null, null, null));
            }
        }
    }
    private record EditorKey(@Nullable PackType type, ResourceLocation id) { }
    private record EditorResource(@Nullable Path packPath, @Nullable Path filePath, @Nullable Path devPath) { }
}
