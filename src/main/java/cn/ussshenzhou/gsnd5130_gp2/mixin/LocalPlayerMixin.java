package cn.ussshenzhou.gsnd5130_gp2.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author USS_Shenzhou
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    protected int sprintTriggerTime;

    /**
     * Vanilla starts sprinting when forward is pressed while sprintTriggerTime is still above 0,
     * which is left by the previous forward press. Clearing it before every check disables double-tap-forward sprinting.
     * The sprint key is not affected.
     */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void gsnd5130_gp2$disableDoubleTapSprint(CallbackInfo callbackInfo) {
        sprintTriggerTime = 0;
    }
}
