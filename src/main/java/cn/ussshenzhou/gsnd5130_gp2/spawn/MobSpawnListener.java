package cn.ussshenzhou.gsnd5130_gp2.spawn;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * @author USS_Shenzhou
 */
@EventBusSubscriber
public class MobSpawnListener {

    /**
     * Baby zombies, including baby husks, drowned, zombie villagers and zombified piglins, are never added to a level.
     * <p>
     * A baby zombie may ride a chicken as a chicken jockey. A chicken created for the jockey joins the level before the zombie,
     * so it is cancelled as well. An existing chicken picked as the mount is released and stays as a normal chicken.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Zombie zombie && zombie.isBaby()) {
            if (zombie.getVehicle() instanceof Chicken chicken) {
                chicken.setChickenJockey(false);
            }
            zombie.stopRiding();
            event.setCanceled(true);
        } else if (entity instanceof Chicken chicken
                && chicken.isChickenJockey()
                && chicken.getFirstPassenger() instanceof Zombie zombie
                && zombie.isBaby()) {
            event.setCanceled(true);
        }
    }
}
