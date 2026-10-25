package cn.ussshenzhou.gsnd5130_gp2;

import cn.ussshenzhou.gsnd5130_gp2.config.MiniMap;
import cn.ussshenzhou.t88.config.ConfigHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Gsnd5130_gp2.MODID)
public class Gsnd5130_gp2 {
    public static final String MODID = "gsnd5130_gp2";
    public Gsnd5130_gp2(IEventBus modEventBus, ModContainer modContainer) {
        ConfigHelper.loadConfig(new MiniMap());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
