package cn.ussshenzhou.gsnd5130_gp2.gui;

import cn.ussshenzhou.gsnd5130_gp2.config.MiniMap;
import cn.ussshenzhou.gsnd5130_gp2.config.RefreshRate;
import cn.ussshenzhou.gsnd5130_gp2.config.ScreenCorner;
import cn.ussshenzhou.gsnd5130_gp2.hud.MiniMapPanel;
import cn.ussshenzhou.t88.config.ConfigHelper;
import cn.ussshenzhou.t88.gui.HudManager;
import cn.ussshenzhou.t88.gui.advanced.TOptionsPanel;
import cn.ussshenzhou.t88.gui.screen.TScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * @author USS_Shenzhou
 */
public class SettingsScreen extends TScreen {
    TOptionsPanel settingsPanel = new TOptionsPanel();

    public SettingsScreen() {
        super(Component.literal("Settings"));
        this.add(settingsPanel);
        settingsPanel.addOptionSplitter(Component.translatable("gui.gsnd5130_gp2.minimap"));
        settingsPanel.addOptionCycleButtonInit(Component.translatable("gui.gsnd5130_gp2.minimap.minimap"), List.of(
                        "gui.gsnd5130_gp2.minimap.minimap.on",
                        "gui.gsnd5130_gp2.minimap.minimap.off"
                ),
                List.of(
                        button -> {
                            HudManager.addIfSameClassNotExist(new MiniMapPanel());
                            ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.visible = true);
                        },
                        button -> {
                            HudManager.removeInstanceOf(MiniMapPanel.class);
                            ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.visible = false);
                        }
                ),
                entry -> entry.getContent().equals(
                        HudManager.getChildren().stream().anyMatch(tComponent -> tComponent instanceof MiniMapPanel)
                                ? "gui.gsnd5130_gp2.minimap.minimap.on"
                                : "gui.gsnd5130_gp2.minimap.minimap.off"
                ));
        settingsPanel.addOptionCycleButtonInit(Component.translatable("gui.gsnd5130_gp2.minimap.rotate"), List.of(Boolean.TRUE, Boolean.FALSE),
                bool -> button -> ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.followPlayerRotation = button.getSelected().getContent()),
                entry -> entry.getContent() == ConfigHelper.getConfigRead(MiniMap.class).followPlayerRotation);
        settingsPanel.addOptionSliderDoubleInit(Component.translatable("gui.gsnd5130_gp2.minimap.range"), 8, 320,
                (component, value) -> Component.literal(String.format("%d", value.intValue())),
                null,
                (slider, value) -> ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.setCoverRange((int) slider.getAbsValue())),
                ConfigHelper.getConfigRead(MiniMap.class).getCoverRange(),
                true);

        settingsPanel.addOptionSliderDoubleInit(Component.translatable("gui.gsnd5130_gp2.minimap.ssaa"), 0.5, 4,
                (component, value) -> Component.literal(String.format("%.1f", Math.round(value * 10) / 10.0)),
                null,
                (slider, value) -> {
                    ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.ssaaRatio = (float) (Math.round(slider.getAbsValue() * 10) / 10.0));
                    resizeMiniMap();
                },
                ConfigHelper.getConfigRead(MiniMap.class).ssaaRatio,
                true
        );
        settingsPanel.addOptionCycleButtonInit(Component.translatable("gui.gsnd5130_gp2.minimap.refresh"), List.of(RefreshRate.values()),
                refreshRate -> button -> ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.refreshRate = refreshRate),
                entry -> entry.getContent() == ConfigHelper.getConfigRead(MiniMap.class).refreshRate);
        settingsPanel.addOptionCycleButtonInit(Component.translatable("gui.gsnd5130_gp2.minimap.corner"), List.of(ScreenCorner.values()),
                corner -> button -> {
                    ConfigHelper.getConfigWrite(MiniMap.class, miniMap -> miniMap.corner = corner);
                    resizeMiniMap();
                },
                entry -> entry.getContent() == ConfigHelper.getConfigRead(MiniMap.class).corner);
    }

    private static void resizeMiniMap() {
        var window = Minecraft.getInstance().getWindow();
        HudManager.getChildren().stream()
                .filter(tComponent -> tComponent instanceof MiniMapPanel)
                .forEach(miniMapPanel -> miniMapPanel.resizeAsHud(window.getGuiScaledWidth(), window.getGuiScaledHeight()));
    }

    @Override
    public void layout() {
        settingsPanel.setBounds(0, 0, width, height);
        super.layout();
    }
}
