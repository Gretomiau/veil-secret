package foundry.veil.impl.quasar;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Pure collision response, kept separate from world queries for deterministic tests. */
public final class BouncePhysics {
    private BouncePhysics() { }

    public static void reflect(Vector3d velocity, Vector3dc incoming, int blockedAxes, float restitution) {
        double factor = Float.isFinite(restitution) ? Math.max(0.0F, Math.min(1.0F, restitution)) : 0.0;
        if ((blockedAxes & 1) != 0) velocity.x = -incoming.x() * factor;
        if ((blockedAxes & 2) != 0) velocity.y = -incoming.y() * factor;
        if ((blockedAxes & 4) != 0) velocity.z = -incoming.z() * factor;
    }
}
