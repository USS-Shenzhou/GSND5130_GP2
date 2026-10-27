package cn.ussshenzhou.gsnd5130_gp2.mixin;

import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * On October 31, zombies and skeletons have a 25% chance to spawn with a carved pumpkin or a jack o'lantern on the head.
 *
 * @author USS_Shenzhou
 */
@Mixin({Zombie.class, AbstractSkeleton.class})
public abstract class HalloweenPumpkinMixin {

    @Redirect(method = "finalizeSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SpecialDates;isHalloween()Z"))
    private boolean gsnd5130_gp2$neverHalloween() {
        return false;
    }
}
