out vec4 MystrixPosition;
out vec3 MystrixNormal;
void head() { MystrixPosition = vec4(Position, 1.0); MystrixNormal = Normal; }
