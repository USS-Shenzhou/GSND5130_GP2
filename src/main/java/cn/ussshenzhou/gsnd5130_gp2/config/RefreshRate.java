package cn.ussshenzhou.gsnd5130_gp2.config;

import cn.ussshenzhou.t88.gui.util.ITranslatable;

/**
 * @author USS_Shenzhou
 */
public enum RefreshRate implements ITranslatable {
    EVERY_FRAME("gui.gsnd5130_gp2.minimap.refresh.every"),
    ANOTHER_FRAME("gui.gsnd5130_gp2.minimap.refresh.another"),
    TICK("gui.gsnd5130_gp2.minimap.refresh.tick"),
    TICK_20("gui.gsnd5130_gp2.minimap.refresh.tick20");

    RefreshRate(String translateKey) {
        this.translateKey = translateKey;
    }

    private final String translateKey;

    @Override
    public String translateKey() {
        return translateKey;
    }
}
