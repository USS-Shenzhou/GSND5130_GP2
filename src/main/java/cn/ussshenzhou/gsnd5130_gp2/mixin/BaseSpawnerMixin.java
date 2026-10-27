package cn.ussshenzhou.gsnd5130_gp2.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.SpawnData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Spawners ignore light, spawn rules and the spawner game rule, and spawn one mob at a time.
 * The player range, the spawn delay, the nearby entity limit and the collision checks are kept.
 *
 * @author USS_Shenzhou
 */
@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {

    /**
     * The game rule spawnerBlocksEnabled.
     */
    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isSpawnerBlockEnabled()Z"))
    private boolean gsnd5130_gp2$ignoreSpawnerGameRule(ServerLevel level) {
        return true;
    }

    /**
     * Only one attempt per spawn cycle. A failed attempt does not reset the delay, so the spawner retries on the next tick
     * until exactly one mob is spawned.
     */
    @Redirect(method = "serverTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/BaseSpawner;spawnCount:I", opcode = Opcodes.GETFIELD))
    private int gsnd5130_gp2$spawnOneAtATime(BaseSpawner spawner) {
        return 1;
    }

    /**
     * The block light and sky light limits written in the custom_spawn_rules of the spawn data.
     */
    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/SpawnData$CustomSpawnRules;isValidPosition(Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerLevel;)Z"))
    private boolean gsnd5130_gp2$ignoreCustomSpawnRules(SpawnData.CustomSpawnRules customSpawnRules, BlockPos pos, ServerLevel level) {
        return true;
    }

    /**
     * The per-entity-type spawn rules, such as the light level for monsters and the grass block for animals.
     */
    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/SpawnPlacements;checkSpawnRules(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z"))
    private boolean gsnd5130_gp2$ignoreSpawnPlacements(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random) {
        return true;
    }

    /**
     * By default this checks both Mob#checkSpawnRules, which is a light check again for monsters,
     * and Mob#checkSpawnObstruction, which rejects positions inside fluids or other entities. Only the latter is kept.
     */
    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;checkSpawnPositionSpawner(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/level/SpawnData;Lnet/minecraft/world/level/BaseSpawner;)Z"))
    private boolean gsnd5130_gp2$checkObstructionOnly(Mob mob, ServerLevelAccessor level, EntitySpawnReason spawnReason, SpawnData spawnData, BaseSpawner spawner) {
        return mob.checkSpawnObstruction(level);
    }
}
