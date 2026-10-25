package cn.ussshenzhou.gsnd5130_gp2.config;

import cn.ussshenzhou.t88.gui.util.ITranslatable;

/**
 * @author USS_Shenzhou
 */
public enum ScreenCorner implements ITranslatable {
    TOP_LEFT("gui.gsnd5130_gp2.minimap.corner.top_left"),
    TOP_RIGHT("gui.gsnd5130_gp2.minimap.corner.top_right"),
    BOTTOM_LEFT("gui.gsnd5130_gp2.minimap.corner.bottom_left"),
    BOTTOM_RIGHT("gui.gsnd5130_gp2.minimap.corner.bottom_right");

    ScreenCorner(String translateKey) {
        this.translateKey = translateKey;
    }

    private final String translateKey;

    @Override
    public String translateKey() {
        return translateKey;
    }
}
