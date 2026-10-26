package cn.ussshenzhou.gsnd5130_gp2.hud;

import cn.ussshenzhou.gsnd5130_gp2.Gsnd5130_gp2;
import cn.ussshenzhou.gsnd5130_gp2.config.MiniMap;
import cn.ussshenzhou.gsnd5130_gp2.gui.SettingsScreen;
import cn.ussshenzhou.t88.config.ConfigHelper;
import cn.ussshenzhou.t88.gui.widegt.TImage;
import cn.ussshenzhou.t88.gui.widegt.TPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.Level;

import static net.minecraft.util.Mth.PI;

/**
 * @author USS_Shenzhou
 */
public class MiniMapPanel extends TPanel {
    private final InnerMiniMapPanel innerMiniMap = new InnerMiniMapPanel();
    private final TImage me = new TImage(Gsnd5130_gp2.id("textures/gui/me_minimap.png")) {
        @Override
        public void extractRenderState(GuiGraphicsExtractor guigraphics, int pMouseX, int pMouseY, float pPartialTick) {
            guigraphics.pose().pushMatrix();
            var cfgR = ConfigHelper.getConfigRead(MiniMap.class);
            if (!cfgR.followPlayerRotation) {
                var camera = Minecraft.getInstance().gameRenderer.mainCamera();
                guigraphics.pose().rotateAbout(PI + camera.yRot() * PI / 180, x + 8, y + 8);
            }
            super.extractRenderState(guigraphics, pMouseX, pMouseY, pPartialTick);
            guigraphics.pose().popMatrix();
        }
    };

    public MiniMapPanel() {
        super();
        me.setAlpha(0.5f);
        this.add(innerMiniMap);
        this.add(me);
    }

    @Override
    public void tickT() {
        super.tickT();
        Minecraft mc = Minecraft.getInstance();

        var visible = !mc.getDebugOverlay().showDebugScreen()
                && !mc.gui.hud.isHidden()
                && (mc.level != null && mc.level.dimension() == Level.OVERWORLD)
                && !(mc.gui.screen() instanceof SettingsScreen);
        if (!children.isEmpty() && (children.getFirst().isVisibleT() != visible)) {
            children.forEach(childTComponent -> childTComponent.setVisibleT(visible));
        }
    }

    @Override
    public void resizeAsHud(int screenWidth, int screenHeight) {
        innerMiniMap.setAbsBounds(0, 0, screenWidth, screenHeight);
        super.resizeAsHud(screenWidth, screenHeight);
        me.setAbsBounds(
                (int) (innerMiniMap.x1() + InnerMiniMapPanel.getMinimapSize() / 2f - 8),
                (int) (innerMiniMap.y1() + InnerMiniMapPanel.getMinimapSize() / 2f - 8),
                16,
                16);
    }
}
