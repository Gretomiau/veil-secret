// Finite segment lengths in fog volumes. All positions must use the same space.
// Rewritten from the height/wall/box experiments in the supplied Veil 2.1.0 pack.

// Length of [start, end] below a horizontal plane, including parallel segments.
float veilFogHeightLength(vec3 start, vec3 end, float height) {
    float distance = length(end - start);
    float a = start.y - height;
    float b = end.y - height;
    if (a <= 0.0 && b <= 0.0) return distance;
    if (a >= 0.0 && b >= 0.0) return 0.0;
    float crossing = clamp(a / (a - b), 0.0, 1.0);
    return distance * (a < 0.0 ? crossing : 1.0 - crossing);
}

// Length inside an axis-aligned box. End is the visible surface (depth limit).
// Reversed corners are accepted; a parallel ray on a face remains well defined.
float veilFogBoxLength(vec3 start, vec3 end, vec3 cornerA, vec3 cornerB) {
    vec3 lower = min(cornerA, cornerB);
    vec3 upper = max(cornerA, cornerB);
    if (any(lessThanEqual(upper - lower, vec3(0.0)))) return 0.0;
    vec3 delta = end - start;
    float entry = 0.0;
    float exit = 1.0;
    for (int axis = 0; axis < 3; axis++) {
        if (delta[axis] == 0.0) {
            if (start[axis] < lower[axis] || start[axis] > upper[axis]) return 0.0;
        } else {
            float a = (lower[axis] - start[axis]) / delta[axis];
            float b = (upper[axis] - start[axis]) / delta[axis];
            entry = max(entry, min(a, b));
            exit = min(exit, max(a, b));
            if (entry >= exit) return 0.0;
        }
    }
    return length(delta) * max(exit - entry, 0.0);
}

// Beer-Lambert absorption; negative density/distance disable absorption.
float veilFogTransmittance(float distance, float density) {
    return exp(-max(distance, 0.0) * max(density, 0.0));
}

// Preserve the source alpha. FogColor.a controls the strength of compositing.
vec4 veilApplyVolumeFog(vec4 source, vec4 fogColor, float distance, float density) {
    float opacity = (1.0 - veilFogTransmittance(distance, density)) * clamp(fogColor.a, 0.0, 1.0);
    return vec4(mix(source.rgb, fogColor.rgb, opacity), source.a);
}
