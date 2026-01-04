package xenon.addon.stainless.modules.autopearlstasis;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Finds stasis chambers and edge positions
 */
public class APSStasisFinder {

    // ---- Settings
    private final APSSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ---- Constructor
    public APSStasisFinder(APSSettings settings) {
        this.settings = settings;
    }

    // ---- Helpers
    public StasisResult findStasisAndEdge() {
        BlockPos base = mc.player.getBlockPos();
        int r = settings.searchRadius.get();

        BlockPos bestWater = null;
        BlockPos bestStand = null;
        Vec3d bestEdge = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.Mutable m = new BlockPos.Mutable();

        Vec3d playerPos = Vec3d.ofCenter(mc.player.getBlockPos());

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (mc.world.getFluidState(m).getFluid() != Fluids.WATER) continue;

                    BlockPos td = m.up();
                    if (!(mc.world.getBlockState(td).getBlock() instanceof TrapdoorBlock)) continue;

                    Vec3d waterCenter = Vec3d.ofCenter(m);

                    for (Direction d : Direction.Type.HORIZONTAL) {
                        BlockPos adj = m.offset(d);
                        if (!isSolidFloor(adj) || !isHeadroomOk(adj.up())) continue;

                        Vec3d adjCenter = Vec3d.ofCenter(adj);
                        Vec3d toWater = new Vec3d(
                            waterCenter.x - adjCenter.x,
                            0,
                            waterCenter.z - adjCenter.z
                        );

                        if (toWater.lengthSquared() < 1e-6) continue;

                        Vec3d edgePoint = adjCenter.add(toWater.normalize().multiply(0.45));

                        double dist = playerPos.squaredDistanceTo(edgePoint);

                        if (dist < bestDist) {
                            bestDist = dist;
                            bestWater = m.toImmutable();
                            bestStand = adj.toImmutable();
                            bestEdge = edgePoint;
                        }
                    }
                }
            }
        }

        return new StasisResult(bestWater, bestStand, bestEdge);
    }

    public BlockPos findBestStasisWithPearl() {
        if (mc == null || mc.player == null || mc.world == null) return null;

        BlockPos base = mc.player.getBlockPos();
        int r = 8;

        BlockPos bestTrapdoor = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.Mutable m = new BlockPos.Mutable();
        Vec3d playerPos = Vec3d.ofCenter(mc.player.getBlockPos());

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (mc.world.getFluidState(m).getFluid() != Fluids.WATER) continue;

                    BlockPos trap = m.up();
                    if (!(mc.world.getBlockState(trap).getBlock() instanceof TrapdoorBlock)) continue;
                    if (!detectAnyPearlInWater(m)) continue;

                    double d = playerPos.squaredDistanceTo(Vec3d.ofCenter(m));

                    if (d < bestDist) {
                        bestDist = d;
                        bestTrapdoor = trap.toImmutable();
                    }
                }
            }
        }

        return bestTrapdoor;
    }

    public boolean detectAnyPearlInWater(BlockPos waterPos) {
        Vec3d c = Vec3d.ofCenter(waterPos);
        Box box = new Box(
            c.x - 0.5, waterPos.getY(), c.z - 0.5,
            c.x + 0.5, waterPos.getY() + 1.0, c.z + 0.5
        );

        List<EnderPearlEntity> pearls =
            mc.world.getEntitiesByClass(EnderPearlEntity.class, box, Entity::isAlive);

        return !pearls.isEmpty();
    }

    public int countPearlsInStasis(BlockPos stasisWater) {
        if (stasisWater == null || mc.world == null) return 0;

        Vec3d c = Vec3d.ofCenter(stasisWater);
        Box box = new Box(
            c.x - 0.5, stasisWater.getY(), c.z - 0.5,
            c.x + 0.5, stasisWater.getY() + 1.0, c.z + 0.5
        );

        List<EnderPearlEntity> pearls =
            mc.world.getEntitiesByClass(EnderPearlEntity.class, box, Entity::isAlive);

        return pearls.size();
    }

    private boolean isSolidFloor(BlockPos pos) {
        return !mc.world.getBlockState(pos).isAir()
            && !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()
            && mc.world.getFluidState(pos).getFluid() != Fluids.WATER;
    }

    private boolean isHeadroomOk(BlockPos posAboveFeet) {
        BlockState s = mc.world.getBlockState(posAboveFeet);
        return s.isAir() || s.getCollisionShape(mc.world, posAboveFeet).isEmpty();
    }

    public static class StasisResult {
        public final BlockPos water;
        public final BlockPos standBlock;
        public final Vec3d edgePos;

        public StasisResult(BlockPos water, BlockPos standBlock, Vec3d edgePos) {
            this.water = water;
            this.standBlock = standBlock;
            this.edgePos = edgePos;
        }

        public boolean isValid() {
            return water != null && standBlock != null && edgePos != null;
        }
    }
}
