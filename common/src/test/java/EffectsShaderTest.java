import com.google.gson.JsonParser;
import foundry.veil.api.client.render.CameraMatrices;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

/** No window, GPU, Minecraft client, or OpenGL context required. */
public class EffectsShaderTest {
    private static final String ROOT = "/assets/veil/mystrix/";

    static String resource(String path) throws IOException {
        try (var input = EffectsShaderTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(input, "Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String expand(String source, Set<String> imports, boolean spirvLayout) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\\R")) {
            if (line.startsWith("#include veil:")) {
                String include = line.substring("#include veil:".length()).trim();
                if (imports.add(include)) result.append(expand(resource("shaders/include/" + include + ".glsl"), imports, spirvLayout));
            } else if (line.equals("#veil:buffer veil:camera VeilCamera")) {
                // Use the real camera layout, rather than a test-only approximation.
                GlslTree camera = new GlslTree();
                camera.getBody().add(CameraMatrices.createLayout().createNode(false, "VeilCamera"));
                // SPIR-V requires an explicit layout; OpenGL's shared layout is
                // legal at runtime. Keep all real fields, using std140 here.
                String declaration = camera.toSourceString().replaceAll("(?m)^#version[^\\r\\n]*\\R?", "");
                result.append(spirvLayout ? declaration.replaceAll("\\bshared\\b", "std140") : declaration);
            } else {
                assertFalse(line.startsWith("#veil:"), "Unexpected Veil directive: " + line);
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    static void compile(String source, String name, int stage) throws Exception {
        // Exercise the same GLSL grammar and source writer as Veil's compiler.
        String processed = GlslParser.preprocessParse("#version 330 core\n" + source.replaceAll("(?m)^#version[^\\r\\n]*\\R?", ""), new HashMap<>()).toSourceString();
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        assertNotEquals(0L, compiler);
        assertNotEquals(0L, options);
        try {
            shaderc_compile_options_set_target_env(options, shaderc_target_env_opengl, shaderc_env_version_opengl_4_5);
            shaderc_compile_options_set_auto_map_locations(options, true);
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            long result = shaderc_compile_into_spv(compiler, processed, stage, name, "main", options);
            assertNotEquals(0L, result);
            try {
                assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                        () -> name + ": " + shaderc_result_get_error_message(result));
            } finally {
                shaderc_result_release(result);
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    @Test
    void fogProgramsCompileWithCurrentCameraAndPipelineReferences() throws Exception {
        for (String effect : new String[]{"height_fog", "box_fog"}) {
            var pipeline = JsonParser.parseString(resource("post/effects/" + effect + ".json")).getAsJsonObject();
            var stage = pipeline.getAsJsonArray("stages").get(0).getAsJsonObject();
            assertEquals("veil:blit", stage.get("type").getAsString());
            assertEquals("minecraft:main", stage.get("in").getAsString());
            assertEquals("veil:effects/" + effect, stage.get("shader").getAsString());
            var program = JsonParser.parseString(resource("shaders/program/effects/" + effect + ".json")).getAsJsonObject();
            assertEquals("veil:blit_screen", program.get("vertex").getAsString());
            assertEquals("veil:effects/" + effect, program.get("fragment").getAsString());
            compile(expand(resource("shaders/program/effects/" + effect + ".fsh"), new HashSet<>(), true), effect, shaderc_fragment_shader);
        }
        compile(resource("shaders/program/blit_screen.vsh"), "blit_screen", shaderc_vertex_shader);
    }

    @Test
    void utilityIncludesComposeWithoutHiddenUniforms() throws Exception {
        String source = resource("shaders/include/fog_volume.glsl") + resource("shaders/include/procedural_noise.glsl") + """
                in vec3 position;
                out vec4 fragColor;
                void main() {
                    float fog = veilFogHeightLength(vec3(0), position, 64.0);
                    fog += veilFogBoxLength(vec3(0), position, vec3(-8), vec3(8));
                    float noise = veilHash13(position) + veilValueNoise3(position);
                    noise += veilFbm3(position, 3) + veilEnergyNoise3(position, 1.0);
                    fragColor = veilApplyVolumeFog(vec4(noise), vec4(1), fog, 0.1);
                }
                """;
        compile(source, "composed_utilities", shaderc_fragment_shader);
    }
}
