package cn.ussshenzhou.gsnd5130_gp2.mixin;

import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adult piglins have a 10% chance for each golden armor piece on spawn. The crossbow or golden sword is kept.
 *
 * @author USS_Shenzhou
 */
@Mixin(Piglin.class)
public abstract class PiglinMixin {

    @Inject(method = "maybeWearArmor", at = @At("HEAD"), cancellable = true)
    private void gsnd5130_gp2$skipRandomArmor(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }
}
