import foundry.veil.impl.resource.MystrixPackResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MystrixPackResourcesTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("veil", path); }
    private static String contents(Resource resource) throws IOException {
        try (var stream = resource.open()) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private record FakePack(String name, Map<ResourceLocation, String> files, ResourceFilterSection filter) implements PackResources, foundry.veil.ext.PackResourcesExtension {
        FakePack(String name, Map<ResourceLocation, String> files) { this(name, files, null); }
        @Override public IoSupplier<InputStream> getRootResource(String... path) { return null; }
        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            return files.containsKey(id) ? () -> new ByteArrayInputStream(files.get(id).getBytes(StandardCharsets.UTF_8)) : null;
        }
        @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
            files.forEach((id, value) -> { if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix + "/")) output.accept(id, getResource(type,id)); });
        }
        @Override public Set<String> getNamespaces(PackType type) { return Set.of("veil"); }
        @Override @SuppressWarnings("unchecked") public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) { return serializer == ResourceFilterSection.TYPE ? (T) filter : null; }
        @Override public PackLocationInfo location() { return new PackLocationInfo(name, net.minecraft.network.chat.Component.literal(name), net.minecraft.server.packs.repository.PackSource.BUILT_IN, Optional.empty()); }
        @Override public void close() { }
        @Override public IoSupplier<InputStream> veil$getIcon() { return null; }
        @Override public boolean veil$blurIcon() { return true; }
        @Override public boolean veil$isStatic() { return false; }
        @Override public java.util.List<java.nio.file.Path> veil$getRawResourceRoots() { return List.of(java.nio.file.Path.of("pack")); }
        @Override public void veil$listResources(PackResourceConsumer consumer) {
            files.forEach((id, value) -> consumer.accept(PackType.CLIENT_RESOURCES,id,java.nio.file.Path.of("pack"),java.nio.file.Path.of("pack/assets/veil/"+id.getPath()),null));
        }
    }
    @Test void legacyUserPackOverridesCanonicalLibraryAndStacksPreservePriority() throws Exception {
        PackResources library = new MystrixPackResources(new FakePack("library",Map.of(id("mystrix/post/test.json"),"library")));
        PackResources user = new MystrixPackResources(new FakePack("user",Map.of(id("pinwheel/post/test.json"),"user")));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(library,user))) {
            assertEquals("user",contents(manager.getResource(id("mystrix/post/test.json")).orElseThrow()));
            assertEquals("user",contents(manager.getResource(id("pinwheel/post/test.json")).orElseThrow()));
            assertEquals(List.of("library","user"),manager.getResourceStack(id("mystrix/post/test.json")).stream().map(Resource::sourcePackId).toList());
            assertEquals(Set.of(id("mystrix/post/test.json")),manager.listResources("mystrix/post",x -> true).keySet());
            assertEquals(2,manager.listResourceStacks("mystrix/post",x -> true).get(id("mystrix/post/test.json")).size());
        }
    }
    @Test void canonicalWinsInSamePackWithoutDuplicatingListEntriesOrLegacyMetadata() throws Exception {
        var pack = new MystrixPackResources(new FakePack("both",Map.of(id("pinwheel/post/test.json"),"old",id("mystrix/post/test.json"),"new",id("pinwheel/post/test.json.mcmeta"),"stale")));
        assertEquals("new",new String(pack.getResource(PackType.CLIENT_RESOURCES,id("pinwheel/post/test.json")).get().readAllBytes(),StandardCharsets.UTF_8));
        assertNull(pack.getResource(PackType.CLIENT_RESOURCES,id("mystrix/post/test.json.mcmeta")));
        Map<ResourceLocation,IoSupplier<InputStream>> files = new HashMap<>();
        pack.listResources(PackType.CLIENT_RESOURCES,"veil","mystrix/post",files::put);
        assertTrue(files.containsKey(id("mystrix/post/test.json")));
        assertFalse(files.containsKey(id("pinwheel/post/test.json")));
        assertEquals(id("mystrix/post/test.json"),MystrixPackResources.canonical(id("pinwheel/post/test.json")));
    }
    @Test void legacyFiltersAlsoBlockCanonicalAliases() throws Exception {
        var pattern=net.minecraft.util.ResourceLocationPattern.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"namespace\":\"veil\",\"path\":\"pinwheel/post/.*\"}")).getOrThrow();
        var library=new MystrixPackResources(new FakePack("library",Map.of(id("mystrix/post/test.json"),"library")));
        var blocker=new MystrixPackResources(new FakePack("blocker",Map.of(),new ResourceFilterSection(List.of(pattern))));
        try (var manager=new MultiPackResourceManager(PackType.CLIENT_RESOURCES,List.of(library,blocker))) {
            assertTrue(manager.getResource(id("mystrix/post/test.json")).isEmpty());
            assertTrue(manager.listResources("mystrix/post",x -> true).isEmpty());
        }
    }
    @Test void editorKeepsPhysicalLegacyPathsAndWritableRoots() {
        var pack=new MystrixPackResources(new FakePack("editor",Map.of(id("pinwheel/post/test.json"),"legacy")));
        assertFalse(pack.veil$isStatic()); assertTrue(pack.veil$blurIcon());
        assertEquals(List.of(java.nio.file.Path.of("pack")),pack.veil$getRawResourceRoots());
        pack.veil$listResources((type,name,root,file,dev) -> {
            assertEquals(id("mystrix/post/test.json"),name);
            assertEquals(java.nio.file.Path.of("pack/assets/veil/pinwheel/post/test.json"),file);
        });
        assertSame(pack,pack.veil$listPacks().findFirst().orElseThrow());
    }
}
