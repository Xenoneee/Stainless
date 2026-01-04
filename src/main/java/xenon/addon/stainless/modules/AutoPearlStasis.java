package xenon.addon.stainless.modules;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import xenon.addon.stainless.modules.autopearlstasis.*;

public class AutoPearlStasis extends StainlessModule {
    public AutoPearlStasis() {
        super(Stainless.STAINLESS_CATEGORY, "AutoPearlStasis",
            "Instantly Teleports you to your pearl chamber (alt account required).");
    }

    // -------------------- Core Components --------------------
    private APSSettings apsSettings;
    private APSUtil util;
    private APSInventoryManager invManager;
    private APSMovementController movement;
    private APSStasisFinder stasisFinder;
    private APSBaritoneHelper baritone;
    private APSMainMode mainMode;
    private APSAltMode altMode;
    private APSAssistStateMachine assistMachine;

    // -------------------- State Tracking --------------------
    private int joinCooldown = 60;
    private int ticksSinceJoin = 0;
    private boolean needCleanup = false;

    @Override
    public void onActivate() {
        super.onActivate();

        // Initialize components
        apsSettings = new APSSettings(settings);
        util = new APSUtil();
        invManager = new APSInventoryManager(apsSettings);
        movement = new APSMovementController(apsSettings);
        stasisFinder = new APSStasisFinder(apsSettings);
        baritone = new APSBaritoneHelper(apsSettings);

        mainMode = new APSMainMode(
            apsSettings,
            this::triggerAssist,
            () -> apsDebug("State: " + assistMachine.getState()),
            () -> apsInfo("No armed stasis (pearl-in-water) found nearby.")
        );

        altMode = new APSAltMode(
            apsSettings,
            stasisFinder,
            () -> apsDebug("AltMode debug"),
            () -> apsInfo("No armed stasis (pearl-in-water) found nearby.")
        );

        assistMachine = new APSAssistStateMachine(
            apsSettings,
            invManager,
            movement,
            stasisFinder,
            baritone,
            () -> apsDebug("State: " + assistMachine.getState()),
            () -> apsInfo("No stasis found in radius.")
        );

        // Reset state
        joinCooldown = 60;
        ticksSinceJoin = 0;
        needCleanup = false;

        if (apsSettings.mode.get() == APSSettings.Mode.MAIN) {
            mainMode.reset();
            assistMachine.reset();
        } else {
            altMode.reset();
        }
    }

    @EventHandler
    private void onJoin(GameJoinedEvent e) {
        if (mc.player == null || mc.world == null) return;
        joinCooldown = 60;
        ticksSinceJoin = 0;
        needCleanup = false;

        if (apsSettings.mode.get() == APSSettings.Mode.MAIN) {
            mainMode.reset();
            assistMachine.reset();
        } else {
            altMode.reset();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.world == null) return;
        if (apsSettings.mode.get() == APSSettings.Mode.MAIN) {
            tickMain();
        } else {
            tickAlt();
        }
    }

    private void tickMain() {
        if (mc == null || mc.player == null || mc.world == null) return;

        // Cleanup if needed
        if (needCleanup) {
            invManager.cleanupInventoryState();
            needCleanup = false;
        }

        ticksSinceJoin++;

        // Join cooldown
        if (joinCooldown > 0) {
            joinCooldown--;
            return;
        }

        // Main mode tick (totem tracking, proximity, hotkeys)
        mainMode.tick(ticksSinceJoin);

        // Auto-start assist if near stasis
        if (assistMachine.canAutoStart()) {
            assistMachine.tryAutoStart();
        }

        // Run assist state machine
        if (assistMachine.isActive()) {
            assistMachine.tick();
        }
    }

    private void tickAlt() {
        if (mc == null || mc.player == null || mc.world == null) return;
        altMode.tick();
    }

    private void triggerAssist() {
        assistMachine.trigger();
    }

    // -------------------- Logging Helpers --------------------
    private void apsDebug(String msg) {
        if (apsSettings.debugChat.get()) {
            sendDebugMsg("[APS] " + msg);
        }
    }

    private void apsInfo(String msg) {
        sendInfoMsg("[APS] " + msg);
    }
}
