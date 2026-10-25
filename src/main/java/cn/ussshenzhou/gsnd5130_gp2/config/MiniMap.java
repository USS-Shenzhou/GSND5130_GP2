package cn.ussshenzhou.gsnd5130_gp2.config;

import cn.ussshenzhou.t88.config.TConfig;
import net.minecraft.util.Mth;

/**
 * @author USS_Shenzhou
 */
@SuppressWarnings("FieldMayBeFinal")
public class MiniMap implements TConfig {
    @Override
    public String getChildDirName() {
        return "GSND5130_GP2";
    }

    private int coverRange = 96;
    public boolean followPlayerRotation = true;
    public float ssaaRatio = 1.5f;
    public boolean visible = true;
    public RefreshRate refreshRate = RefreshRate.ANOTHER_FRAME;
    public ScreenCorner corner = ScreenCorner.TOP_RIGHT;

    public int getCoverRange() {
        return Mth.clamp(coverRange, 8, 320);
    }

    public void setCoverRange(int coverRange) {
        this.coverRange = coverRange;
    }

}
