package cn.ussshenzhou.gsnd5130_gp2.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the random equipment given on spawn.
 * <ul>
 * <li>Mob: random armor. Only zombies and skeletons reach it, through their super calls.</li>
 * <li>Zombie: random iron sword, spear or shovel. Also used by husks and zombie villagers.</li>
 * <li>Drowned: random trident or fishing rod.</li>
 * </ul>
 * Fixed weapons, such as the bow of skeletons and the golden sword of zombified piglins, are given by other overrides and are kept.
 *
 * @author USS_Shenzhou
 */
@Mixin({Mob.class, Zombie.class, Drowned.class})
public abstract class RandomEquipmentMixin {

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("HEAD"), cancellable = true)
    private void gsnd5130_gp2$skipRandomEquipment(CallbackInfo callbackInfo) {
        callbackInfo.cancel();
    }
}
