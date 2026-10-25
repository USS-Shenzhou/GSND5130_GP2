package cn.ussshenzhou.gsnd5130_gp2.record;

import cn.ussshenzhou.gsnd5130_gp2.gui.RecordingSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.nio.file.Path;

/**
 * @author USS_Shenzhou
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class RecordingListener {
    private static final long SETUP_SCREEN_DELAY_MILLIS = 500;
    private static boolean setupScreenPending = false;
    private static long readySinceMillis = -1;

    @SubscribeEvent
    public static void onJoinWorld(ClientPlayerNetworkEvent.LoggingIn event) {
        setupScreenPending = true;
        readySinceMillis = -1;
    }

    @SubscribeEvent
    public static void onLeaveWorld(ClientPlayerNetworkEvent.LoggingOut event) {
        setupScreenPending = false;
        PositionRecorder.stop();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && PositionRecorder.isRecording()) {
            PositionRecorder.record(minecraft.player.position());
        }
        if (setupScreenPending) {
            tickSetupScreenDelay(minecraft);
        }
    }

    /**
     * The delay starts after the level loading screen has closed, and restarts whenever another screen is open.
     */
    private static void tickSetupScreenDelay(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gui.screen() != null) {
            readySinceMillis = -1;
            return;
        }
        long now = Util.getMillis();
        if (readySinceMillis < 0) {
            readySinceMillis = now;
            return;
        }
        if (now - readySinceMillis >= SETUP_SCREEN_DELAY_MILLIS) {
            setupScreenPending = false;
            RecordingSetupScreen screen = new RecordingSetupScreen();
            minecraft.gui.setScreen(screen);
            screen.layout();
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gsnd")
                .then(Commands.literal("stop")
                        .executes(context -> {
                            Path file = PositionRecorder.getRecordingFile();
                            if (file == null) {
                                context.getSource().sendFailure(Component.literal("Not recording."));
                                return 0;
                            }
                            PositionRecorder.stop();
                            context.getSource().sendSuccess(() -> Component.literal("Recording stopped: " + file.getFileName()), false);
                            return 1;
                        })
                )
        );
    }
}
