#include veil:space_helper
#include veil:fog_volume

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform float FogHeight = 64.0;
uniform float FogDensity = 0.05;
uniform vec4 FogColor = vec4(0.5, 0.6, 0.7, 1.0);

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler0, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3 end = screenToLocalSpace(texCoord, depth).xyz;
    vec3 start = (VeilCamera.IViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    // Local space avoids adding large absolute world coordinates to the ray.
    float height = FogHeight - VeilCamera.CameraPosition.y;
    float distance = veilFogHeightLength(start, end, height);
    fragColor = veilApplyVolumeFog(source, FogColor, distance, FogDensity);
}
