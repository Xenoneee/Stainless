package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import xenon.addon.stainless.modules.autopearlthrow.*;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Hand;

public class AutoPearlThrow extends StainlessModule {
    public AutoPearlThrow() {
        super(Stainless.STAINLESS_CATEGORY, "AutoPearlThrow",
            "Automatically throws pearl on totem pop.");

        this.pearlSettings = new PearlThrowSettings(settings);
        this.state = new PearlThrowState();
        this.safetyChecker = new PearlSafetyChecker(pearlSettings);
        this.aimCalculator = new PearlAimCalculator(pearlSettings, safetyChecker);
        this.inventoryManager = new PearlInventoryManager(pearlSettings);
    }

    // -------------------- State --------------------
    private final PearlThrowSettings pearlSettings;
    private final PearlAimCalculator aimCalculator;
    private final PearlInventoryManager inventoryManager;
    private final PearlSafetyChecker safetyChecker;
    private final PearlThrowState state;

    @Override
    public void onDeactivate() {
        state.reset();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket p)) return;

        // Check if this is a totem pop for the player
        if (p.getStatus() == 35 && p.getEntity(mc.world) == mc.player) {
            handleTotemPop();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Reserve guard check
        if (pearlSettings.isReserveGuardActive()) {
            if (state.isPendingThrow()) {
                state.reset();
            }
            if (pearlSettings.isDebugEnabled()) {
                info("Reserve totems reached — skipping.");
            }
            return;
        }

        // Manual test trigger
        handleManualTest();

        // Process pending throw
        if (state.isPendingThrow() && mc.interactionManager != null) {
            processPendingThrow();
        }
    }

    private void handleTotemPop() {
        long now = System.currentTimeMillis();

        if (state.isOnCooldown(now, pearlSettings.getCooldownMs())) {
            if (pearlSettings.isDebugEnabled()) {
                info("Pop: on cooldown.");
            }
            return;
        }

        if (pearlSettings.isReserveGuardActive()) {
            if (pearlSettings.isDebugEnabled()) {
                info("Reserve totems reached — skipping throw.");
            }
            return;
        }

        if (pearlSettings.isDebugEnabled()) {
            info("Totem pop -> schedule throw.");
        }

        state.scheduleThrow(now + pearlSettings.getThrowDelayMs());
    }

    private void handleManualTest() {
        if (pearlSettings.isTestNowTriggered() && !state.isPendingThrow()) {
            if (pearlSettings.isDebugEnabled()) {
                info("Manual test -> scheduling throw.");
            }
            state.scheduleThrow(System.currentTimeMillis() + 75);
            pearlSettings.resetTestNow();
        }
    }

    private void processPendingThrow() {
        long now = System.currentTimeMillis();

        if (!state.isTimeToThrow(now)) {
            return;
        }

        // Handle jump sequence
        if (handleJumpSequence(now)) return;

        // Prepare inventory
        PearlInventoryManager.InventoryPlan plan = prepareInventory();
        if (plan == null) return;

        // Calculate aim
        PearlAimCalculator.Aim aim = calculateAim();
        if (aim == null) {
            plan.cleanup();
            state.reset();
            return;
        }

        // Execute throw
        executeThrow(aim, plan);
    }

    private boolean handleJumpSequence(long now) {
        if (pearlSettings.shouldJumpOnThrow() && !state.hasJumped() && mc.player.isOnGround()) {
            mc.player.jump();
            state.markJumped();
            state.delayThrow(now + pearlSettings.getJumpWaitMs());

            if (pearlSettings.isDebugEnabled()) {
                info("Jumped; waiting " + pearlSettings.getJumpWaitMs() + "ms.");
            }
            return true;
        }
        return false;
    }

    private PearlInventoryManager.InventoryPlan prepareInventory() {
        PearlInventoryManager.InventoryPlan plan = inventoryManager.preparePearl();

        if (!plan.isValid()) {
            if (pearlSettings.isDebugEnabled()) {
                info("No pearls available. Cancelling.");
            }
            plan.cleanup();
            state.reset();
            return null;
        }
        return plan;
    }

    private PearlAimCalculator.Aim calculateAim() {
        PearlAimCalculator.Aim aim = aimCalculator.calculateBestAim();

        if (aim == null) {
            if (pearlSettings.isDebugEnabled()) {
                info("All angles blocked or no escape — cancelled.");
            }
            return null;
        }
        return aim;
    }

    private void executeThrow(PearlAimCalculator.Aim aim, PearlInventoryManager.InventoryPlan plan) {
        Runnable doThrow = () -> {
            mc.interactionManager.interactItem(mc.player, plan.getHand());
            plan.cleanup();
            state.recordThrow(System.currentTimeMillis());
            state.reset();

            if (pearlSettings.isDebugEnabled()) {
                info("Pearl thrown.");
            }
        };

        if (pearlSettings.shouldRotate()) {
            Rotations.rotate(aim.yaw, aim.pitch, doThrow);
        } else {
            doThrow.run();
        }
    }
}
