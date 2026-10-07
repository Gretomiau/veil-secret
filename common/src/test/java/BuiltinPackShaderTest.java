import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

class BuiltinPackShaderTest {
    static String resource(String path) throws Exception {
        try (var input = BuiltinPackShaderTest.class.getResourceAsStream("/resourcepacks/" + path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Test void allExampleProgramsCompileAgainstCurrentIncludes() throws Exception {
        for (String program : new String[]{"fog", "height_fog", "wall_fog", "cube_fog", "skybox", "space_test"}) {
            EffectsShaderTest.compile(EffectsShaderTest.expand(resource("fog/assets/veil/mystrix/shaders/program/" + program + ".fsh"), new HashSet<>(), true), program, shaderc_fragment_shader);
        }
        EffectsShaderTest.compile(EffectsShaderTest.expand(resource("black_hole/assets/venuslibs/mystrix/shaders/program/black_hole.fsh"), new HashSet<>(), true), "black_hole", shaderc_fragment_shader);
        EffectsShaderTest.compile(EffectsShaderTest.expand(resource("test_shaders/assets/veil/mystrix/shaders/program/test.fsh"), new HashSet<>(), true), "test_fragment", shaderc_fragment_shader);
        EffectsShaderTest.compile(resource("test_shaders/assets/veil/mystrix/shaders/program/test.vsh"), "test_vertex", shaderc_vertex_shader);
    }
}
