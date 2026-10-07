import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinPackReferencesTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path PACK = ROOT.resolve("resourcepacks/test_particles/assets/veil/quasar");
    private static void reference(String id, String folder) {
        assertTrue(id.startsWith("veil:"), id);
        assertTrue(Files.isRegularFile(PACK.resolve(folder + "/" + id.substring(5) + ".json")), "Unresolved " + folder + " reference: " + id);
    }
    @Test void particleModulesAndSubemittersFormAClosedGraph() throws Exception {
        for (Path path : Files.list(PACK.resolve("modules/particle_data")).toList()) {
            JsonObject data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (String obsolete : Set.of("init_modules","update_modules","render_modules","forces","collision_modules")) assertFalse(data.has(obsolete),path.toString());
            for (var module : data.getAsJsonArray("modules")) if (module.isJsonPrimitive()) reference(module.getAsString(),"modules/module");
        }
        try (var files = Files.walk(PACK.resolve("modules/module"))) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                JsonObject module = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (module.has("subemitter")) reference(module.get("subemitter").getAsString(),"emitters");
                assertNotEquals("veil:init_random_rotation",module.get("module").getAsString());
            }
        }
        try (var files = Files.list(PACK.resolve("emitters"))) {
            for (Path path : files.toList()) {
                JsonObject emitter=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                reference(emitter.get("particle_data").getAsString(),"modules/particle_data");
                JsonObject settings=emitter.getAsJsonObject("emitter_settings");
                reference(settings.get("particle_settings").getAsString(),"modules/emitter/particle");
                var shapes=settings.get("shape");
                if (shapes.isJsonArray()) for (var shape : shapes.getAsJsonArray()) reference(shape.getAsString(),"modules/emitter/shape");
                else reference(shapes.getAsString(),"modules/emitter/shape");
            }
        }
    }
    @Test void allExampleShaderDefinitionsResolveBothStages() throws Exception {
        try (var files=Files.walk(ROOT.resolve("resourcepacks"))) {
            for (Path path:files.filter(p -> p.toString().replace('\\','/').contains("/shaders/program/") && p.toString().endsWith(".json")).toList()) {
                JsonObject definition=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                Path pack=ROOT.resolve("resourcepacks").resolve(ROOT.resolve("resourcepacks").relativize(path).getName(0));
                for (String stage:Set.of("vertex","fragment")) {
                    String[] id=definition.get(stage).getAsString().split(":",2);
                    String resource="assets/"+id[0]+"/mystrix/shaders/program/"+id[1]+(stage.equals("vertex")?".vsh":".fsh");
                    assertTrue(Files.isRegularFile(pack.resolve(resource)) || Files.isRegularFile(ROOT.resolve(resource)),path+" -> "+resource);
                }
            }
        }
    }
}
