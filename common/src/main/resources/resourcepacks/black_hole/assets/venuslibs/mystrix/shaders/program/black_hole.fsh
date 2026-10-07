#version 330
#include veil:space_helper
#include veil:procedural_noise

// Adapted Mystrix prototype: bounded artistic lensing, not relativistic simulation.
uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform vec3 BlackHolePosition = vec3(0.0, 80.0, 0.0);
uniform float BlackHoleScale = 4.0;
uniform float TimeSeconds = 0.0;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler0, texCoord);
    vec3 start = (VeilCamera.IViewMat * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 end = screenToLocalSpace(texCoord, texture(DiffuseDepthSampler, texCoord).r).xyz;
    vec3 delta = end - start;
    float visibleLength = length(delta);
    float radius = max(BlackHoleScale, 0.001);
    if (BlackHoleScale <= 0.0 || visibleLength <= 0.00001) { fragColor = source; return; }
    vec3 dir = delta / visibleLength;
    vec3 center = BlackHolePosition - VeilCamera.CameraPosition;
    vec3 offset = start - center;
    float b = dot(offset, dir);
    float discriminant = b * b - dot(offset, offset) + 36.0 * radius * radius;
    if (discriminant < 0.0) { fragColor = source; return; }
    float entry = max(0.0, -b - sqrt(discriminant));
    if (entry >= visibleLength) { fragColor = source; return; }
    vec3 point = start + dir * entry;
    float stepLength = radius / 8.0;
    float transmittance = 1.0;
    vec3 emission = vec3(0.0);
    bool captured = false;
    float traveled = entry;
    for (int i = 0; i < 128; ++i) {
        if (traveled >= visibleLength) break;
        vec3 relative = point - center;
        float distance = length(relative);
        if (distance < radius) { captured = true; break; }
        if (i > 0 && distance > radius * 6.0) break;
        float radial = length(relative.xz);
        float disk = exp(-abs(relative.y) / (radius * 0.08))
            * smoothstep(radius * 1.4, radius * 1.8, radial)
            * (1.0 - smoothstep(radius * 3.5, radius * 4.5, radial));
        float noise = 0.65 + 0.35 * veilValueNoise3(relative / radius * 7.0 + vec3(TimeSeconds * 0.3));
        float opacity = clamp(disk * noise * 0.12, 0.0, 0.2);
        emission += transmittance * opacity * vec3(3.0, 0.8, 0.16);
        transmittance *= 1.0 - opacity;
        dir = normalize(dir - relative / max(distance, radius) * (radius * radius / max(distance * distance, radius * radius)) * 0.035);
        float stepDistance = min(stepLength, visibleLength - traveled);
        point += dir * stepDistance;
        traveled += stepDistance;
    }
    vec4 projected = VeilCamera.ProjMat * VeilCamera.ViewMat * vec4(dir, 0.0);
    vec2 uv = abs(projected.w) > 0.00001 ? projected.xy / projected.w * 0.5 + 0.5 : texCoord;
    vec3 background = texture(DiffuseSampler0, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
    if (captured) background = vec3(0.0);
    fragColor = vec4(emission + background * transmittance, source.a);
}
