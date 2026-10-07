package foundry.veil.api.quasar.data.module.collision;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import foundry.veil.api.quasar.data.ParticleModuleTypeRegistry;
import foundry.veil.api.quasar.data.module.ModuleType;
import foundry.veil.api.quasar.data.module.ParticleModuleData;
import foundry.veil.api.quasar.emitters.module.CollisionParticleModule;
import foundry.veil.api.quasar.particle.ParticleModuleSet;

/** Reflects velocity on blocked world axes, with restitution between zero and one. */
public record BounceModuleData(float bounciness) implements ParticleModuleData {
    public static final MapCodec<BounceModuleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.floatRange(0.0F, 1.0F).fieldOf("bounciness").forGetter(BounceModuleData::bounciness)
    ).apply(instance, BounceModuleData::new));

    @Override public void addModules(ParticleModuleSet.Builder builder) {
        builder.addModule((CollisionParticleModule) particle -> particle.bounce(this.bounciness));
    }

    @Override public ModuleType<?> getType() { return ParticleModuleTypeRegistry.BOUNCE; }
}
