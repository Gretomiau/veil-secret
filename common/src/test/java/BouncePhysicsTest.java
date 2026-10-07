import foundry.veil.impl.quasar.BouncePhysics;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BouncePhysicsTest {
    @Test void floorCollisionPreservesTangentialVelocity() {
        Vector3d result = new Vector3d(2, 0, 3);
        BouncePhysics.reflect(result, new Vector3d(2, -4, 3), 2, 0.6F);
        assertEquals(2, result.x); assertEquals(2.4, result.y, 1.0E-6); assertEquals(3, result.z);
    }
    @Test void cornersReflectAllBlockedAxesAndNeverGainEnergy() {
        Vector3d input = new Vector3d(2, -4, 3), result = new Vector3d();
        BouncePhysics.reflect(result, input, 7, 2.0F);
        assertEquals(new Vector3d(-2, 4, -3), result);
        assertTrue(result.lengthSquared() <= input.lengthSquared());
        BouncePhysics.reflect(result, input, 7, Float.NaN);
        assertEquals(0, result.lengthSquared());
    }
}
