package xenon.addon.stainless.modules.autopearlstasis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * State machine for the stasis assist system
 */
public class APSAssistStateMachine {

    // ---- Settings
    private final APSSettings settings;

    // ---- State
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final APSInventoryManager invManager;
    private final APSMovementController movement;
    private final APSStasisFinder stasisFinder;
    private final APSBaritoneHelper baritone;
    private final Runnable debugLogger;
    private final Runnable infoLogger;

    private State state = State.IDLE;
    private State lastLoggedState = State.IDLE;

    private BlockPos stasisWater = null;
    private BlockPos standBlock = null;
    private Vec3d standEdgePos = null;

    private int assistTicks = 0;
    private int throwAttemptTicks = 0;
    private boolean thrownThisCycle = false;
    private boolean throwOnceLatch = false;
    private int suppressThrowsTicks = 0;
    private int sneakHoldTicks = 0;

    private double lastEdgeDist = Double.MAX_VALUE;
    private int noProgressTicks = 0;
    private int stuckTicks = 0;
    private int escapeBoostTicks = 0;
    private int surfacingTicks = 0;
    private Vec3d lastPos = null;

    private int verifyTicks = 0;
    private int retryCount = 0;
    private int stasisPearlCountBefore = -1;

    private Vec3d stepBackTarget = null;
    private int stepBackTicksRemaining = 0;
    private BlockPos retreatGoalBlock = null;

    // ---- Constructor
    public APSAssistStateMachine(APSSettings settings, APSInventoryManager invManager,
                                 APSMovementController movement, APSStasisFinder stasisFinder,
                                 APSBaritoneHelper baritone, Runnable debugLogger, Runnable infoLogger) {
        this.settings = settings;
        this.invManager = invManager;
        this.movement = movement;
        this.stasisFinder = stasisFinder;
        this.baritone = baritone;
        this.debugLogger = debugLogger;
        this.infoLogger = infoLogger;
    }

    // ---- Lifecycle
    public void trigger() {
        if (!settings.autoApproach.get()) return;
        state = State.SEARCH;
        assistTicks = 0;
        throwAttemptTicks = 0;
        thrownThisCycle = false;
        throwOnceLatch = false;
        suppressThrowsTicks = 0;
        sneakHoldTicks = 0;
        verifyTicks = 0;
        retryCount = 0;
        stasisPearlCountBefore = -1;
        lastEdgeDist = Double.MAX_VALUE;
        noProgressTicks = 0;
        stuckTicks = 0;
        escapeBoostTicks = 0;
        surfacingTicks = 0;
        lastPos = mc.player != null ? mc.player.getPos() : null;
        stasisWater = null;
        standBlock = null;
        standEdgePos = null;
        stepBackTarget = null;
        retreatGoalBlock = null;
        invManager.cleanupInventoryState();
        movement.releaseSneak();
        movement.releaseAllMoveKeys();
        logState();
    }

    public void reset() {
        state = State.IDLE;
        assistTicks = 0;
        throwAttemptTicks = 0;
        thrownThisCycle = false;
        throwOnceLatch = false;
        suppressThrowsTicks = 0;
        sneakHoldTicks = 0;
        verifyTicks = 0;
        retryCount = 0;
        stasisPearlCountBefore = -1;
        lastEdgeDist = Double.MAX_VALUE;
        noProgressTicks = 0;
        stuckTicks = 0;
        escapeBoostTicks = 0;
        surfacingTicks = 0;
        lastPos = null;
        stasisWater = null;
        standBlock = null;
        standEdgePos = null;
        stepBackTarget = null;
        retreatGoalBlock = null;
        invManager.cleanupInventoryState();
        movement.releaseSneak();
        movement.releaseAllMoveKeys();
        logState();
    }

    public void tick() {
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            invManager.cleanupInventoryState();
            reset();
            return;
        }

        assistTicks++;
        if (suppressThrowsTicks > 0) suppressThrowsTicks--;

        switch (state) {
            case SEARCH -> tickSearch();
            case PATHING -> tickPathing();
            case SURFACING -> tickSurfacing();
            case EDGE_ADJUST -> tickEdgeAdjust();
            case THROWING -> tickThrowing();
            case STEPPING_BACK -> tickSteppingBack();
            case VERIFYING -> tickVerifying();
            case DONE, FAILED -> {
                invManager.cleanupInventoryState();
                reset();
            }
        }

        movement.maintainMovementKeys();
        if (mc.player != null) lastPos = mc.player.getPos();
    }

    public boolean canAutoStart() {
        return state == State.IDLE && settings.autoApproach.get() && settings.autoStartNear.get();
    }

    public void tryAutoStart() {
        if (!canAutoStart()) return;
        var result = stasisFinder.findStasisAndEdge();
        if (result.isValid()) {
            stasisWater = result.water;
            standBlock = result.standBlock;
            standEdgePos = result.edgePos;
            stasisPearlCountBefore = stasisFinder.countPearlsInStasis(stasisWater);
            baritone.startPathTo(standBlock);
            state = State.PATHING;
            logState();
        }
    }

    // ---- Helpers
    private void tickSearch() {
        var result = stasisFinder.findStasisAndEdge();
        if (!result.isValid()) {
            infoLogger.run();
            state = State.FAILED;
            logState();
            return;
        }

        stasisWater = result.water;
        standBlock = result.standBlock;
        standEdgePos = result.edgePos;
        stasisPearlCountBefore = stasisFinder.countPearlsInStasis(stasisWater);

        baritone.startPathTo(standBlock);
        state = State.PATHING;
        logState();
    }

    private void tickPathing() {
        if (standEdgePos != null) movement.walkTowardExact(standEdgePos);

        if (isInWater()) {
            movement.faceTowardXZ(standEdgePos);
            mc.player.setPitch(-settings.exitPitchDeg.get().floatValue());
            movement.pressForward(settings.exitForwardTicks.get());
            movement.pressSprint(settings.exitSprintTicks.get());
            movement.pressJump(settings.exitJumpTicks.get());
            waterEscapeWatchdog();
            return;
        }

        if (!isInWater() && isAtEdge()) {
            baritone.stopPath();
            state = State.EDGE_ADJUST;
            logState();
        } else if (assistTicks > settings.approachTimeout.get()) {
            baritone.stopPath();
            state = State.FAILED;
            logState();
        }
    }

    private void tickSurfacing() {
        if (!isInWater() || isHeadAboveSurface()) {
            state = State.PATHING;
            logState();
            return;
        }

        mc.player.setPitch(-settings.exitPitchDeg.get().floatValue());
        movement.faceTowardXZ(standEdgePos);
        movement.pressForward(settings.exitForwardTicks.get());
        movement.pressSprint(settings.exitSprintTicks.get());
        movement.pressJump(settings.exitJumpTicks.get());

        if (--surfacingTicks <= 0) {
            state = State.PATHING;
            logState();
        }
    }

    private void tickEdgeAdjust() {
        if (standEdgePos != null) {
            Vec3d snap = snapPointOnEdge(standEdgePos);
            movement.walkTowardExact(snap);
            if (stasisWater != null) movement.faceTowardXZ(Vec3d.ofCenter(stasisWater));
        }
        waterEscapeWatchdog();

        if (isInWater()) {
            state = State.SURFACING;
            surfacingTicks = 10;
            logState();
            return;
        }

        if (isAtEdge()) {
            movement.releaseAllMoveKeys();
            if (settings.sneakWhileAiming.get() && sneakHoldTicks <= 0) {
                movement.pressSneak();
                sneakHoldTicks = settings.sneakAimingTicks.get();
            }

            mc.player.setPitch(settings.downPitchDeg.get().floatValue());

            if (!settings.autoRethrow.get()) {
                state = State.DONE;
                logState();
                return;
            }

            if (!invManager.ensurePearlReady()) {
                invManager.cleanupInventoryState();
                state = State.FAILED;
                logState();
                return;
            }

            throwAttemptTicks = settings.throwWindowTicks.get();
            throwOnceLatch = false;
            suppressThrowsTicks = 0;
            state = State.THROWING;
            logState();
        } else if (assistTicks > settings.approachTimeout.get()) {
            state = State.FAILED;
            logState();
        }
    }

    private void tickThrowing() {
        movement.releaseAllMoveKeys();

        if (stasisWater != null) movement.faceTowardXZ(Vec3d.ofCenter(stasisWater));
        float targetPitch = settings.downPitchDeg.get().floatValue();
        mc.player.setPitch(targetPitch);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            mc.player.getYaw(), targetPitch, mc.player.isOnGround(), false
        ));

        if (isInWater()) {
            if (--throwAttemptTicks <= 0) {
                invManager.cleanupInventoryState();
                state = State.FAILED;
                logState();
            }
            return;
        }

        if (!throwOnceLatch && suppressThrowsTicks == 0 && !invManager.pearlOnCooldown()) {
            if (!invManager.ensurePearlReady()) {
                if (--throwAttemptTicks <= 0) {
                    invManager.cleanupInventoryState();
                    state = State.FAILED;
                    logState();
                }
                return;
            }

            Hand hand = invManager.getPearlHand();
            if (mc.interactionManager != null) {
                mc.interactionManager.interactItem(mc.player, hand);
                throwOnceLatch = true;
                thrownThisCycle = true;
                suppressThrowsTicks = 40;
            }
        }

        if (throwOnceLatch) {
            if (settings.stepBackAfterThrow.get()) {
                calculateStepBackTarget();
                if (settings.useBaritone.get() && retreatGoalBlock != null) {
                    baritone.startPathTo(retreatGoalBlock);
                }
                stepBackTicksRemaining = Math.max(stepBackTicksRemaining, settings.stepBackTicks.get());
                state = State.STEPPING_BACK;
            } else {
                verifyTicks = settings.pearlVerifyTicks.get();
                state = State.VERIFYING;
            }
            logState();
            return;
        }

        if (--throwAttemptTicks <= 0) {
            invManager.cleanupInventoryState();
            state = State.FAILED;
            logState();
        }

        if (sneakHoldTicks > 0) {
            sneakHoldTicks--;
            if (sneakHoldTicks == 0) movement.releaseSneak();
        }
    }

    private void tickSteppingBack() {
        if (!settings.useBaritone.get() || retreatGoalBlock == null) {
            if (stepBackTarget != null) movement.stepBackFromStasis(stepBackTarget);
        }

        if (--stepBackTicksRemaining <= 0 || hasReachedStepBackTarget()) {
            if (settings.useBaritone.get()) baritone.stopPath();
            verifyTicks = settings.pearlVerifyTicks.get();
            state = State.VERIFYING;
            logState();
        }
    }

    private void tickVerifying() {
        if (--verifyTicks <= 0) {
            boolean pearlInStasis = checkPearlIncreasedInStasis()
                || (stasisWater != null && stasisFinder.detectAnyPearlInWater(stasisWater));

            boolean likelyThrown = thrownThisCycle && invManager.pearlOnCooldown();

            if (pearlInStasis || likelyThrown) {
                state = State.DONE;
            } else if (settings.retryOnMiss.get() && retryCount < settings.maxRetries.get()) {
                retryCount++;
                stasisPearlCountBefore = stasisFinder.countPearlsInStasis(stasisWater);
                if (!invManager.ensurePearlReady()) {
                    invManager.cleanupInventoryState();
                    state = State.FAILED;
                    logState();
                    return;
                }
                throwAttemptTicks = settings.throwWindowTicks.get();
                throwOnceLatch = false;
                suppressThrowsTicks = 0;
                state = State.THROWING;
            } else {
                state = thrownThisCycle ? State.DONE : State.FAILED;
            }
            logState();
        }
    }

    private void calculateStepBackTarget() {
        if (stasisWater == null || mc.player == null || mc.world == null) return;

        Vec3d playerPos = mc.player.getPos();
        Vec3d waterCenter = Vec3d.ofCenter(stasisWater);
        Vec3d awayXZ = new Vec3d(playerPos.x - waterCenter.x, 0, playerPos.z - waterCenter.z);
        if (awayXZ.lengthSquared() < 1e-6) awayXZ = new Vec3d(0, 0, 1);
        awayXZ = awayXZ.normalize();

        double[] radii = { 2.0, 2.2, 1.8, 2.4, 1.6 };
        double[] lateral = { 0.0, 0.4, -0.4, 0.7, -0.7 };

        Vec3d bestVec = null;
        BlockPos bestBlock = null;

        outer:
        for (double r : radii) {
            for (double lat : lateral) {
                Vec3d left = new Vec3d(-awayXZ.z, 0, awayXZ.x);
                Vec3d probe = playerPos.add(awayXZ.multiply(r)).add(left.multiply(lat));

                BlockPos feet = BlockPos.ofFloored(probe.x, Math.floor(playerPos.y + 0.001), probe.z);
                BlockPos below = feet.down();

                boolean solidBelow = !mc.world.getBlockState(below).isAir()
                    && !mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty()
                    && mc.world.getFluidState(below).getFluid() != Fluids.WATER;

                boolean feetAir = mc.world.getBlockState(feet).isAir()
                    || mc.world.getBlockState(feet).getCollisionShape(mc.world, feet).isEmpty();

                BlockPos head = feet.up();
                boolean headAir = mc.world.getBlockState(head).isAir()
                    || mc.world.getBlockState(head).getCollisionShape(mc.world, head).isEmpty();

                boolean feetIsWater = mc.world.getFluidState(feet).getFluid() == Fluids.WATER;

                if (solidBelow && feetAir && headAir && !feetIsWater) {
                    bestBlock = feet.toImmutable();
                    bestVec = new Vec3d(bestBlock.getX() + 0.5, bestBlock.getY() + 0.02, bestBlock.getZ() + 0.5);
                    break outer;
                }
            }
        }

        if (bestVec == null) {
            double d = Math.min(Math.max(settings.stepBackDistance.get(), 1.2), 2.4);
            bestVec = playerPos.add(awayXZ.multiply(d));
            bestVec = new Vec3d(bestVec.x, playerPos.y + 0.02, bestVec.z);
            bestBlock = BlockPos.ofFloored(bestVec);
        }

        stepBackTarget = bestVec;
        retreatGoalBlock = bestBlock;

        double flatDist = Math.sqrt(
            Math.pow(playerPos.x - stepBackTarget.x, 2) +
                Math.pow(playerPos.z - stepBackTarget.z, 2)
        );
        stepBackTicksRemaining = Math.max(settings.stepBackTicks.get(), (int) Math.ceil(12 * flatDist));
    }

    private void waterEscapeWatchdog() {
        if (!settings.advancedWaterEscape.get() || standEdgePos == null || mc.player == null) return;

        Vec3d p = mc.player.getPos();
        double dx = p.x - standEdgePos.x, dz = p.z - standEdgePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        double speedSq = 0;
        if (lastPos != null) {
            double sx = p.x - lastPos.x, sz = p.z - lastPos.z;
            speedSq = sx * sx + sz * sz;
        }

        if (isInWater()) {
            mc.player.setPitch(-settings.exitPitchDeg.get().floatValue());
            movement.faceTowardXZ(standEdgePos);

            movement.pressForward(settings.exitForwardTicks.get());
            movement.pressSprint(settings.exitSprintTicks.get());
            movement.pressJump(settings.exitJumpTicks.get());

            if (lastEdgeDist - dist < 0.02) noProgressTicks++; else noProgressTicks = 0;
            if (speedSq < 0.0004) stuckTicks++; else stuckTicks = 0;

            if (noProgressTicks >= 10 || stuckTicks >= 8) {
                escapeBoostTicks = Math.max(escapeBoostTicks, 6);
                noProgressTicks = 0;
                stuckTicks = 0;
            }
            if (escapeBoostTicks > 0) {
                escapeBoostTicks--;
                movement.strongWaterEscapeBoost(standEdgePos);
            }
        } else {
            noProgressTicks = stuckTicks = escapeBoostTicks = 0;
        }

        lastEdgeDist = dist;
    }

    private boolean checkPearlIncreasedInStasis() {
        if (stasisWater == null || mc.world == null) return false;
        int currentCount = stasisFinder.countPearlsInStasis(stasisWater);
        return currentCount > stasisPearlCountBefore;
    }

    private boolean hasReachedStepBackTarget() {
        if (stepBackTarget == null || mc.player == null) return true;
        Vec3d currentPos = mc.player.getPos();
        double dx = currentPos.x - stepBackTarget.x;
        double dz = currentPos.z - stepBackTarget.z;
        return (dx * dx + dz * dz) <= 0.49;
    }

    private boolean isHeadAboveSurface() {
        if (mc == null || mc.player == null || mc.world == null) return false;
        double eyeY = mc.player.getEyeY();
        BlockPos eyePos = BlockPos.ofFloored(mc.player.getX(), eyeY, mc.player.getZ());
        if (mc.world.getFluidState(eyePos).getFluid() != Fluids.WATER) return true;

        BlockPos feet = mc.player.getBlockPos();
        double surfaceY = feet.getY() + 1.0;
        double headY = mc.player.getBoundingBox().maxY;
        return headY > surfaceY + settings.surfaceHeadClearance.get();
    }

    private Vec3d snapPointOnEdge(Vec3d edge) {
        if (stasisWater == null) return edge;
        Vec3d waterCenter = Vec3d.ofCenter(stasisWater);
        Vec3d fromWater = edge.subtract(waterCenter);
        if (fromWater.lengthSquared() < 1e-6) return edge;
        return edge.add(fromWater.normalize().multiply(0.14));
    }

    private boolean isAtEdge() {
        if (standEdgePos == null || mc.player == null) return false;
        return isNearExact(standEdgePos, settings.approachDistance.get() * 1.5);
    }

    private boolean isNearExact(Vec3d target, double dist) {
        Vec3d p = mc.player.getPos();
        double dx = p.x - target.x, dz = p.z - target.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance <= dist;
    }

    private boolean isInWater() {
        return mc.player != null && mc.player.isTouchingWater();
    }

    private void logState() {
        if (settings.debugChat.get() && lastLoggedState != state) {
            debugLogger.run();
            lastLoggedState = state;
        }
    }

    public State getState() {
        return state;
    }

    public boolean isActive() {
        return state != State.IDLE && state != State.DONE && state != State.FAILED;
    }

    // ---- Enums
    public enum State { IDLE, SEARCH, PATHING, SURFACING, EDGE_ADJUST, THROWING, STEPPING_BACK, VERIFYING, DONE, FAILED }
}
