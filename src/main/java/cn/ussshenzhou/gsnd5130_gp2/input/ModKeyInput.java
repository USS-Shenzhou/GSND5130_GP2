package cn.ussshenzhou.gsnd5130_gp2.input;

import cn.ussshenzhou.gsnd5130_gp2.Gsnd5130_gp2;
import cn.ussshenzhou.gsnd5130_gp2.gui.SettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * @author USS_Shenzhou
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class ModKeyInput {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Gsnd5130_gp2.id("minimap"));
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.gsnd5130_gp2.open_settings", KeyConflictContext.IN_GAME, KeyModifier.NONE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY
    );

    @SubscribeEvent
    public static void onRegisterKey(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SETTINGS);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_SETTINGS.consumeClick()) {
            SettingsScreen screen = new SettingsScreen();
            Minecraft.getInstance().gui.setScreen(screen);
            screen.layout();
        }
    }
}
