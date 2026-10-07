#include veil:space_helper
#include veil:fog_volume

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
// World-space bounds; camera subtraction keeps intersection math local.
uniform vec3 pos1 = vec3(-8.0);
uniform vec3 pos2 = vec3(8.0);
uniform float FogDensity = 0.05;
uniform vec4 FogColor = vec4(0.5, 0.6, 0.7, 1.0);

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler0, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3 end = screenToLocalSpace(texCoord, depth).xyz;
    vec3 start = (VeilCamera.IViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    float distance = veilFogBoxLength(start, end, pos1 - VeilCamera.CameraPosition, pos2 - VeilCamera.CameraPosition);
    fragColor = veilApplyVolumeFog(source, FogColor, distance, FogDensity);
}
