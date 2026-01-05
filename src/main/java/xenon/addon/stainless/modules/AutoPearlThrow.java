package xenon.addon.stainless.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Hand;
import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import xenon.addon.stainless.modules.autopearlthrow.*;

public class AutoPearlThrow extends StainlessModule {

    // ---- Constants
    private static final byte TOTEM_POP_STATUS = 35;
    private static final int DEFAULT_TEST_DELAY_MS = 75;
    private static final int FORCE_THROW_DELAY_MS = 50;

    // ---- State
    private final PearlThrowSettings pearlSettings;
    private final PearlAimCalculator aimCalculator;
    private final PearlInventoryManager inventoryManager;
    private final PearlSafetyChecker safetyChecker;
    private final PearlThrowState state;

    // ---- Constructor
    public AutoPearlThrow() {
        super(Stainless.STAINLESS_CATEGORY, "AutoPearlThrow",
            "Automatically throws pearl on totem pop.");

        this.pearlSettings = new PearlThrowSettings(settings);
        this.state = new PearlThrowState();
        this.safetyChecker = new PearlSafetyChecker(pearlSettings);
        this.aimCalculator = new PearlAimCalculator(pearlSettings, safetyChecker);
        this.inventoryManager = new PearlInventoryManager(pearlSettings);
    }

    // ---- Lifecycle
    @Override
    public void onDeactivate() {
        state.reset();
    }

    // ---- Event Handlers
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!isPlayerReady()) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;

        if (isTotemPopForPlayer(packet)) {
            handleTotemPop();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isPlayerReady()) return;

        // Force throw keybind check
        if (handleForceThrow()) {
            return;
        }

        // Reserve guard check
        if (shouldSkipDueToReserve()) {
            return;
        }

        // Manual test trigger
        handleManualTest();

        // Process pending throw
        if (state.isPendingThrow() && mc.interactionManager != null) {
            processPendingThrow();
        }
    }

    // ---- Pop Detection
    private boolean isTotemPopForPlayer(EntityStatusS2CPacket packet) {
        return packet.getStatus() == TOTEM_POP_STATUS
            && packet.getEntity(mc.world) == mc.player;
    }

    private void handleTotemPop() {
        long now = System.currentTimeMillis();

        if (state.isOnCooldown(now, pearlSettings.getCooldownMs())) {
            debugLog("Pop: on cooldown.");
            return;
        }

        if (shouldSkipDueToReserve()) {
            debugLog("Reserve totems reached — skipping throw.");
            return;
        }

        debugLog("Totem pop -> schedule throw.");
        state.scheduleThrow(now + pearlSettings.getThrowDelayMs());
    }

    // ---- Manual Test
    private boolean handleForceThrow() {
        if (!pearlSettings.getForceThrowBind().isPressed()) {
            return false;
        }

        // Only trigger if not already pending
        if (state.isPendingThrow()) {
            return false;
        }

        debugLog("Force throw keybind pressed -> scheduling immediate throw.");
        state.scheduleThrow(System.currentTimeMillis() + FORCE_THROW_DELAY_MS);
        return true;
    }

    private void handleManualTest() {
        if (pearlSettings.isTestNowTriggered() && !state.isPendingThrow()) {
            debugLog("Manual test -> scheduling throw.");
            state.scheduleThrow(System.currentTimeMillis() + DEFAULT_TEST_DELAY_MS);
            pearlSettings.resetTestNow();
        }
    }

    // ---- Throw Processing
    private void processPendingThrow() {
        long now = System.currentTimeMillis();

        if (!state.isTimeToThrow(now)) {
            return;
        }

        // Handle jump sequence
        if (shouldHandleJump(now)) {
            return;
        }

        // Prepare pearl in inventory (one-time setup)
        PearlInventoryManager.InventoryPlan setupPlan = prepareInventorySetup();
        if (setupPlan == null) {
            return;
        }

        // Calculate aim
        PearlAimCalculator.Aim aim = calculateAim();
        if (aim == null) {
            setupPlan.cleanup();
            state.reset();
            return;
        }

        // Prepare throw (handles silent swap)
        PearlInventoryManager.ThrowPlan throwPlan = inventoryManager.prepareThrow();
        if (!throwPlan.isValid()) {
            debugLog("Pearl disappeared before throw. Cancelling.");
            setupPlan.cleanup();
            state.reset();
            return;
        }

        // Execute throw
        executeThrow(aim, setupPlan, throwPlan);
    }

    private boolean shouldHandleJump(long now) {
        if (!pearlSettings.shouldJumpOnThrow()) {
            return false;
        }

        if (state.hasJumped() || !mc.player.isOnGround()) {
            return false;
        }

        mc.player.jump();
        state.markJumped();
        state.delayThrow(now + pearlSettings.getJumpWaitMs());

        debugLog("Jumped; waiting " + pearlSettings.getJumpWaitMs() + "ms.");
        return true;
    }

    private PearlInventoryManager.InventoryPlan prepareInventorySetup() {
        PearlInventoryManager.InventoryPlan plan = inventoryManager.preparePearl();

        if (!plan.isValid()) {
            debugLog("No pearls available. Cancelling.");
            state.reset();
            return null;
        }

        return plan;
    }

    private PearlAimCalculator.Aim calculateAim() {
        PearlAimCalculator.Aim aim = aimCalculator.calculateBestAim();

        if (aim == null) {
            debugLog("All angles blocked or no escape — cancelled.");
            return null;
        }

        return aim;
    }

    private void executeThrow(PearlAimCalculator.Aim aim,
                              PearlInventoryManager.InventoryPlan setupPlan,
                              PearlInventoryManager.ThrowPlan throwPlan) {
        Runnable doThrow = () -> {
            // Use MAIN_HAND since we removed offhand support
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

            // Cleanup in reverse order
            throwPlan.cleanup();    // Swap back from silent swap
            setupPlan.cleanup();    // Return pearl to inventory if needed

            state.recordThrow(System.currentTimeMillis());
            state.reset();

            debugLog("Pearl thrown.");
        };

        if (pearlSettings.shouldRotate()) {
            Rotations.rotate(aim.yaw, aim.pitch, doThrow);
        } else {
            doThrow.run();
        }
    }

    // ---- Helper Methods
    private boolean isPlayerReady() {
        return mc.player != null && mc.world != null;
    }

    private boolean shouldSkipDueToReserve() {
        if (pearlSettings.isReserveGuardActive()) {
            if (state.isPendingThrow()) {
                state.reset();
            }
            return true;
        }
        return false;
    }

    private void debugLog(String message) {
        if (pearlSettings.isDebugEnabled()) {
            info(message);
        }
    }
}
