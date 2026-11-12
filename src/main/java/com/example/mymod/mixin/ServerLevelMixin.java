package com.example.mymod.mixin;

import com.example.mymod.fx.ExplosionFx;
import com.example.mymod.fx.ModParticles;
import com.example.mymod.fx.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
	private void mymod$customExplosion(Entity source, DamageSource damageSource, ExplosionDamageCalculator calculator,
			double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction interaction,
			ParticleOptions smallParticle, ParticleOptions largeParticle,
			WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> sound, CallbackInfo ci) {
		if (smallParticle != ParticleTypes.EXPLOSION || largeParticle != ParticleTypes.EXPLOSION_EMITTER) {
			return;
		}
		ci.cancel();
		ServerLevel level = (ServerLevel) (Object) this;
		ExplosionFx.onExplode(level, new Vec3(x, y, z), radius, interaction);
		level.explode(source, damageSource, calculator, x, y, z, radius, fire, interaction,
				ModParticles.NONE, ModParticles.NONE, ExplosionFx.NO_BLOCK_PARTICLES, ModSounds.forPower(radius));
	}
}
