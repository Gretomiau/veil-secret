import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Opt-in numerical execution of the shipped GLSL, in a hidden desktop context. */
public class EffectsOpenGLTest {
    private static void linkFogProgram(String effect) throws Exception {
        int vertex = glCreateShader(GL_VERTEX_SHADER);
        int fragment = glCreateShader(GL_FRAGMENT_SHADER);
        int program = glCreateProgram();
        try {
            glShaderSource(vertex, "#version 330 core\n" + EffectsShaderTest.resource("shaders/program/blit_screen.vsh"));
            glShaderSource(fragment, "#version 330 core\n" + EffectsShaderTest.expand(
                    EffectsShaderTest.resource("shaders/program/effects/" + effect + ".fsh"), new HashSet<>(), false));
            for (int shader : new int[]{vertex, fragment}) {
                glCompileShader(shader);
                assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
                glAttachShader(program, shader);
            }
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        } finally {
            glDeleteProgram(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
        }
    }

    private static float[] evaluate(String expression) throws Exception {
        String source = "#version 330 core\n" +
                EffectsShaderTest.resource("shaders/include/fog_volume.glsl") +
                EffectsShaderTest.resource("shaders/include/procedural_noise.glsl") +
                "out vec4 Result; void main() { Result = " + expression + "; gl_Position = vec4(0,0,0,1); }";
        int shader = glCreateShader(GL_VERTEX_SHADER);
        int program = glCreateProgram();
        int array = glGenVertexArrays();
        int buffer = glGenBuffers();
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
            glAttachShader(program, shader);
            glTransformFeedbackVaryings(program, new CharSequence[]{"Result"}, GL_INTERLEAVED_ATTRIBS);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);
            glBindVertexArray(array);
            glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, buffer);
            glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER, 16L, GL_STREAM_READ);
            glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffer);
            glEnable(GL_RASTERIZER_DISCARD);
            glBeginTransformFeedback(GL_POINTS);
            glDrawArrays(GL_POINTS, 0, 1);
            glEndTransformFeedback();
            glDisable(GL_RASTERIZER_DISCARD);
            float[] result = new float[4];
            glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER, 0, result);
            return result;
        } finally {
            glUseProgram(0);
            glDeleteBuffers(buffer);
            glDeleteVertexArrays(array);
            glDeleteProgram(program);
            glDeleteShader(shader);
        }
    }

    private static void length(float expected, String expression) throws Exception {
        float actual = evaluate("vec4(" + expression + ")")[0];
        assertTrue(Float.isFinite(actual), expression);
        assertEquals(expected, actual, 0.0001f, expression);
    }

    @Test
    void shippedUtilitiesHandleVolumeBoundariesAndNoiseBudgets() throws Exception {
        assumeTrue("1".equals(System.getenv("VEIL_VERIFY_OPENGL")), "Set VEIL_VERIFY_OPENGL=1 on a machine with OpenGL 3.3");
        assertTrue(glfwInit(), "GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        long window = glfwCreateWindow(1, 1, "Veil effect verification", 0, 0);
        try {
            assertNotEquals(0L, window, "OpenGL 3.3 context unavailable");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("Effect verification GPU: " + glGetString(GL_RENDERER));
            linkFogProgram("height_fog");
            linkFogProgram("box_fog");
            length(2, "veilFogHeightLength(vec3(0,-1,0),vec3(2,-1,0),0)");
            length(0, "veilFogHeightLength(vec3(0,1,0),vec3(2,1,0),0)");
            length(1, "veilFogHeightLength(vec3(0,-1,0),vec3(0,1,0),0)");
            length(1, "veilFogHeightLength(vec3(0,1,0),vec3(0,-1,0),0)");
            length(0, "veilFogHeightLength(vec3(0),vec3(0),0)");
            length(2, "veilFogBoxLength(vec3(-2,0,0),vec3(2,0,0),vec3(-1),vec3(1))");
            length(2, "veilFogBoxLength(vec3(-2,0,0),vec3(2,0,0),vec3(1),vec3(-1))");
            length(1, "veilFogBoxLength(vec3(0),vec3(2,0,0),vec3(-1),vec3(1))");
            length(0.5f, "veilFogBoxLength(vec3(0),vec3(0.5,0,0),vec3(-1),vec3(1))");
            length(0, "veilFogBoxLength(vec3(-2,2,0),vec3(2,2,0),vec3(-1),vec3(1))");
            length(0, "veilFogBoxLength(vec3(-3,0,0),vec3(-2,0,0),vec3(-1),vec3(1))");
            length(0, "veilFogBoxLength(vec3(0),vec3(0),vec3(-1),vec3(1))");
            length(2, "veilFogBoxLength(vec3(-2,1,0),vec3(2,1,0),vec3(-1),vec3(1))");
            length(0, "veilFogBoxLength(vec3(-2,0,0),vec3(2,0,0),vec3(0),vec3(0))");
            length(1, "veilFogTransmittance(10,0)");
            length(1, "veilFogTransmittance(10,-1)");
            length((float) Math.exp(-1), "veilFogTransmittance(10,0.1)");
            float[] color = evaluate("veilApplyVolumeFog(vec4(0.2,0.3,0.4,0.25),vec4(1),10,0.1)");
            assertEquals(0.25f, color[3]);
            assertTrue(color[0] > 0.2f && color[0] < 1.0f);
            length(0, "veilFbm3(vec3(0.3),0)");
            length(0, "veilFbm3(vec3(0.3),-3)");
            float[] budget = evaluate("vec4(veilFbm3(vec3(0.3),8),veilFbm3(vec3(0.3),100),veilValueNoise3(vec3(-1.2,3.4,5.6)),veilEnergyNoise3(vec3(1,2,3),2))");
            assertEquals(budget[0], budget[1]);
            for (float value : budget) assertTrue(Float.isFinite(value) && value >= 0 && value <= 1);
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            GL.setCapabilities(null);
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
