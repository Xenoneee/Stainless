package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.*;

public class AutoPearlStasis extends StainlessModule {
    // ===== General =====
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode { MAIN, ALT }
    public enum ThrowMode { PRECISE_AIM, SIMPLE_DOWN }
    public enum ProxAltTarget { ALT1, ALT2, BOTH }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Run as your main account (sender) or alt account (receiver).")
        .defaultValue(Mode.MAIN).build());

    // ===== MAIN (Input) – Alt #1 =====
    private final SettingGroup sgMainAlt1 = settings.createGroup("Main (Alt #1)");

    private final Setting<String> altName1 = sgMainAlt1.add(new StringSetting.Builder()
        .name("alt1-name").description("Alt #1 username to /msg.").defaultValue("").build());

    private final Setting<Integer> threshold1 = sgMainAlt1.add(new IntSetting.Builder()
        .name("alt1-totem-threshold").description("Auto-sends to Alt #1 when your totems drop to this count (or below).")
        .defaultValue(1).min(0).max(64).sliderMin(0).sliderMax(64).build());

    private final Setting<Boolean> forceTeleport1 = sgMainAlt1.add(new BoolSetting.Builder()
        .name("alt1-force-teleport").description("Forces a /msg to Alt #1 this tick, then resets.")
        .defaultValue(false).build());

    private final Setting<String> forceKey1 = sgMainAlt1.add(new StringSetting.Builder()
        .name("alt1-force-key").description("Key to instantly force-send to Alt #1 (e.g., G, F4, SPACE). Leave blank to disable.")
        .defaultValue("").build());

    // ===== MAIN (Input) – Alt #2 =====
    private final SettingGroup sgMainAlt2 = settings.createGroup("Main (Alt #2)");

    private final Setting<String> altName2 = sgMainAlt2.add(new StringSetting.Builder()
        .name("alt2-name").description("Alt #2 username to /msg.").defaultValue("").build());

    private final Setting<Integer> threshold2 = sgMainAlt2.add(new IntSetting.Builder()
        .name("alt2-totem-threshold").description("Auto-sends to Alt #2 when your totems drop to this count (or below).")
        .defaultValue(1).min(0).max(64).sliderMin(0).sliderMax(64).build());

    private final Setting<Boolean> forceTeleport2 = sgMainAlt2.add(new BoolSetting.Builder()
        .name("alt2-force-teleport").description("Forces a /msg to Alt #2 this tick, then resets.")
        .defaultValue(false).build());

    private final Setting<String> forceKey2 = sgMainAlt2.add(new StringSetting.Builder()
        .name("alt2-force-key").description("Key to instantly force-send to Alt #2 (e.g., H, F5, LEFT_SHIFT). Leave blank to disable.")
        .defaultValue("").build());

    // ===== Proximity Trigger (MAIN) =====
    private final SettingGroup sgProx = settings.createGroup("Proximity Trigger");

    private final Setting<Boolean> enableProx = sgProx.add(new BoolSetting.Builder()
        .name("enable").description("Force-teleport when a target player enters render distance.")
        .defaultValue(false).build());

    private final Setting<Integer> proxRadius = sgProx.add(new IntSetting.Builder()
        .name("radius").description("Max distance to check (blocks).")
        .defaultValue(64).min(8).max(160).sliderMin(8).sliderMax(160).visible(enableProx::get).build());

    private final Setting<List<String>> proxNameList = sgProx.add(new StringListSetting.Builder()
        .name("names-list").description("Click + to add player names. Leave empty = uses 'match-anyone-when-empty'.")
        .defaultValue(new ArrayList<>()).visible(enableProx::get).build());

    private final Setting<String> proxNames = sgProx.add(new StringSetting.Builder()
        .name("names-comma").description("Comma-separated names if you prefer one text box.")
        .defaultValue("").visible(enableProx::get).build());

    private final Setting<Boolean> proxMatchAnyone = sgProx.add(new BoolSetting.Builder()
        .name("match-anyone-when-empty").description("If no names are configured, match anyone in radius.")
        .defaultValue(false).visible(enableProx::get).build());

    private final Setting<Integer> proxWarmup = sgProx.add(new IntSetting.Builder()
        .name("prox-warmup-ticks").description("Suppress proximity triggers for this many ticks after joining a world.")
        .defaultValue(120).min(0).max(400).sliderMin(0).sliderMax(400).visible(enableProx::get).build());

    private final Setting<ProxAltTarget> proxTargetAlt = sgProx.add(new EnumSetting.Builder<ProxAltTarget>()
        .name("send-to").description("Which alt to contact when proximity triggers.")
        .defaultValue(ProxAltTarget.ALT1).visible(enableProx::get).build());

    private final Setting<Integer> proxCooldown = sgProx.add(new IntSetting.Builder()
        .name("cooldown-ticks").description("Minimum ticks between proximity triggers.")
        .defaultValue(60).min(0).max(400).sliderMin(0).sliderMax(400).visible(enableProx::get).build());

    private final Setting<Boolean> proxChatFeedback = sgProx.add(new BoolSetting.Builder()
        .name("chat-feedback").description("Announce detected player name & coords in chat when triggering.")
        .defaultValue(true).visible(enableProx::get).build());

    // NEW: optional motion requirement toggle (default off)
    private final Setting<Boolean> proxRequireMotion = sgProx.add(new BoolSetting.Builder()
        .name("require-motion").description("Only trigger if you've been moving recently. Disable to trigger while standing still.")
        .defaultValue(false).visible(enableProx::get).build());

    // ===== ALT (Output) – Receiver =====
    private final SettingGroup sgAlt = settings.createGroup("Alt (Receiver)");

    private final Setting<String> mainName = sgAlt.add(new StringSetting.Builder()
        .name("main-account-name").description("Enter Main Account Username Here -->")
        .defaultValue("").build());

    private final Setting<Integer> altTriggerThreshold = sgAlt.add(new IntSetting.Builder()
        .name("totem-threshold").description("When received count is <= this, flip nearest trapdoor.")
        .defaultValue(1).min(0).max(64).sliderMin(0).sliderMax(64).build());

    private final Setting<Boolean> altSendConfirm = sgAlt.add(new BoolSetting.Builder()
        .name("send-tp-confirm").description("After flipping a trapdoor, whisper MAIN a 'tp-ok' confirmation.")
        .defaultValue(true).build());

    private final Setting<Integer> altReopenDelay = sgAlt.add(new IntSetting.Builder()
        .name("reopen-delay-ticks").description("After using a stasis, wait this many ticks then ensure the trapdoor is open again.")
        .defaultValue(25).min(0).max(200).sliderMin(0).sliderMax(200).build());

    // ===== Stasis Assist (MAIN: after teleport) =====
    private final SettingGroup sgAssist = settings.createGroup("Stasis Assist");

    private final Setting<Boolean> autoApproach = sgAssist.add(new BoolSetting.Builder()
        .name("auto-approach-stasis").description("After teleport, walk to the block edge next to the water, then throw.")
        .defaultValue(true).build());

    private final Setting<Boolean> autoRethrow = sgAssist.add(new BoolSetting.Builder()
        .name("auto-rethrow").description("Automatically pitch down & throw the pearl into the chamber.")
        .defaultValue(true).build());

    private final Setting<Boolean> autoStartNear = sgAssist.add(new BoolSetting.Builder()
        .name("auto-start-when-near").description("If a valid stasis is nearby while idle, auto-start the assist.")
        .defaultValue(true).build());

    private final Setting<ThrowMode> throwMode = sgAssist.add(new EnumSetting.Builder<ThrowMode>()
        .name("throw-mode").description("PRECISE_AIM: face water center & hold short. SIMPLE_DOWN: face water & pitch down.")
        .defaultValue(ThrowMode.SIMPLE_DOWN).build());

    private final Setting<Integer> downPitchDeg = sgAssist.add(new IntSetting.Builder()
        .name("down-pitch-deg").description("Pitch used when aiming down into water.")
        .defaultValue(89).min(82).max(90).sliderMin(82).sliderMax(90).build());

    private final Setting<Integer> pitchHoldTicks = sgAssist.add(new IntSetting.Builder()
        .name("pitch-hold-ticks").description("How long to hard-hold the down pitch so you see it on screen.")
        .defaultValue(20).min(4).max(40).sliderMin(4).sliderMax(40).build());

    private final Setting<Integer> throwWindowTicks = sgAssist.add(new IntSetting.Builder()
        .name("throw-window-ticks").description("How long to attempt throwing before giving up.")
        .defaultValue(40).min(8).max(60).sliderMin(8).sliderMax(60).build());

    private final Setting<Integer> retryGapTicks = sgAssist.add(new IntSetting.Builder()
        .name("retry-gap-ticks").description("Ticks between a second throw attempt if no cooldown was detected.")
        .defaultValue(6).min(3).max(15).sliderMin(3).sliderMax(15).build());

    private final Setting<Boolean> stepBackAfterThrow = sgAssist.add(new BoolSetting.Builder()
        .name("step-back-after-throw").description("Step back after throwing pearl to prevent accidental activation.")
        .defaultValue(true).build());

    private final Setting<Integer> stepBackTicks = sgAssist.add(new IntSetting.Builder()
        .name("step-back-ticks").description("Base ticks to spend stepping back from the stasis. Auto-scales with distance.")
        .defaultValue(10).min(3).max(30).sliderMin(3).sliderMax(30).build());

    private final Setting<Double> stepBackDistance = sgAssist.add(new DoubleSetting.Builder()
        .name("step-back-distance").description("Preferred retreat distance (blocks) from the stasis edge.")
        .defaultValue(2.0).min(0.5).max(3.0).sliderMin(0.5).sliderMax(3.0).build());

    private final Setting<Boolean> sneakWhileAiming = sgAssist.add(new BoolSetting.Builder()
        .name("sneak-while-aiming").description("Hold sneak while aiming/throwing to prevent sliding.")
        .defaultValue(true).build());

    private final Setting<Integer> sneakAimingTicks = sgAssist.add(new IntSetting.Builder()
        .name("sneak-aiming-ticks").description("Ticks to hold sneak while aiming & throwing.")
        .defaultValue(8).min(2).max(20).sliderMin(2).sliderMax(20).build());

    private final Setting<Integer> searchRadius = sgAssist.add(new IntSetting.Builder()
        .name("search-radius").description("Search radius (blocks) for stasis water + trapdoor.")
        .defaultValue(12).min(4).max(24).sliderMin(4).sliderMax(24).build());

    private final Setting<Integer> approachTimeout = sgAssist.add(new IntSetting.Builder()
        .name("approach-timeout-ticks").description("Give up moving after this many ticks (20t = 1s).")
        .defaultValue(360).min(40).max(2000).sliderMin(40).sliderMax(2000).build());

    private final Setting<Double> approachDistance = sgAssist.add(new DoubleSetting.Builder()
        .name("edge-distance").description("How close to the edge to stand before throwing.")
        .defaultValue(0.5).min(0.15).max(0.8).sliderMin(0.15).sliderMax(0.8).build());

    private final Setting<Boolean> useChatConfirm = sgAssist.add(new BoolSetting.Builder()
        .name("use-chat-confirm").description("Start assist after receiving 'tp-ok' from ALT; fallback is distance jump.")
        .defaultValue(true).build());

    private final Setting<Integer> postTpDelay = sgAssist.add(new IntSetting.Builder()
        .name("post-teleport-delay-ticks").description("Wait this many ticks after 'tp-ok' (or teleport jump) before starting.")
        .defaultValue(40).min(0).max(200).sliderMin(0).sliderMax(200).build());

    private final Setting<Integer> pearlVerifyTicks = sgAssist.add(new IntSetting.Builder()
        .name("pearl-verify-ticks").description("Ticks to wait and verify pearl landed in stasis water after throwing.")
        .defaultValue(12).min(5).max(30).sliderMin(5).sliderMax(30).build());

    private final Setting<Boolean> retryOnMiss = sgAssist.add(new BoolSetting.Builder()
        .name("retry-on-miss").description("Retry throwing if pearl doesn't land in stasis water.")
        .defaultValue(true).build());

    private final Setting<Integer> maxRetries = sgAssist.add(new IntSetting.Builder()
        .name("max-retries").description("Maximum number of retry attempts if pearl misses.")
        .defaultValue(2).min(1).max(5).sliderMin(1).sliderMax(5).build());

    private final Setting<Boolean> useBaritone = sgAssist.add(new BoolSetting.Builder()
        .name("use-baritone-if-present").description("Use Baritone for pathing (optional).")
        .defaultValue(true).build());

    private final Setting<Double> surfaceHeadClearance = sgAssist.add(new DoubleSetting.Builder()
        .name("surface-head-clearance").description("Required head height above water surface before leaving SURFACING.")
        .defaultValue(0.60).min(0.20).max(1.20).sliderMin(0.20).sliderMax(1.20).build());

    private final Setting<Boolean> advancedWaterEscape = sgAssist.add(new BoolSetting.Builder()
        .name("advanced-water-escape").description("Use advanced water exit mechanics to climb out reliably.")
        .defaultValue(true).build());

    private final Setting<Boolean> debugChat = sgAssist.add(new BoolSetting.Builder()
        .name("debug-chat").description("Verbose state logs (spammy).")
        .defaultValue(false).build());

    // ===== Water Exit tuning =====
    private final SettingGroup sgWaterExit = settings.createGroup("Water Exit Tuning");

    private final Setting<Integer> exitPitchDeg = sgWaterExit.add(new IntSetting.Builder()
        .name("exit-pitch-deg").description("Pitch down while exiting water (used during pathing/surfacing/boost).")
        .defaultValue(25).min(5).max(60).sliderMin(5).sliderMax(60).build());

    private final Setting<Integer> exitForwardTicks = sgWaterExit.add(new IntSetting.Builder()
        .name("forward-hold-ticks").description("How long to hold W during water exit pulses.")
        .defaultValue(6).min(1).max(20).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> exitSprintTicks = sgWaterExit.add(new IntSetting.Builder()
        .name("sprint-hold-ticks").description("How long to hold sprint during water exit pulses.")
        .defaultValue(10).min(1).max(20).sliderMin(1).sliderMax(20).build());

    private final Setting<Integer> exitJumpTicks = sgWaterExit.add(new IntSetting.Builder()
        .name("jump-pulse-ticks").description("How long to tap jump during water exit pulses.")
        .defaultValue(2).min(0).max(10).sliderMin(0).sliderMax(10).build());

    // ===== Inventory / Throw source =====
    private final SettingGroup sgInv = settings.createGroup("Inventory");

    private final Setting<Boolean> preferOffhand = sgInv.add(new BoolSetting.Builder()
        .name("prefer-offhand").description("Use offhand pearls if available.")
        .defaultValue(true).build());

    private final Setting<Boolean> silentSwap = sgInv.add(new BoolSetting.Builder()
        .name("silent-swap").description("Temporarily swap to pearls, then swap back after throwing.")
        .defaultValue(true).build());

    private final Setting<Boolean> swapBackOnStricterServers = sgInv.add(new BoolSetting.Builder()
        .name("swap-back-stricter-servers").description("Move pearls back to original inventory slot after throwing (for servers without silent swap).")
        .defaultValue(true).visible(() -> !silentSwap.get()).build());

    private final Setting<Boolean> pullFromInventory = sgInv.add(new BoolSetting.Builder()
        .name("pull-from-inventory").description("If no pearls in hotbar, pull from main inventory.")
        .defaultValue(true).build());

    private final Setting<Boolean> preferEmptyHotbar = sgInv.add(new BoolSetting.Builder()
        .name("prefer-empty-hotbar").description("Prefer an empty hotbar slot when pulling from inventory.")
        .defaultValue(true).visible(pullFromInventory::get).build());

    private final Setting<Integer> tempHotbarSlot = sgInv.add(new IntSetting.Builder()
        .name("temp-hotbar-slot").description("Fallback hotbar slot [0–8] when pulling from inventory.")
        .defaultValue(8).min(0).max(8).visible(pullFromInventory::get).build());

    // ===== State =====
    private int lastCount = -1;
    private int joinCooldown = 60;
    private boolean key1WasDown = false, key2WasDown = false;

    private enum AssistState { IDLE, SEARCH, PATHING, SURFACING, EDGE_ADJUST, THROWING, STEPPING_BACK, VERIFYING, DONE, FAILED }
    private AssistState assistState = AssistState.IDLE;

    private BlockPos stasisWater = null, standBlock = null;
    private Vec3d standEdgePos = null;

    private int assistTicks = 0;
    private int throwAttemptTicks = 0;

    private boolean thrownThisCycle = false;
    private boolean throwOnceLatch = false;
    private int suppressThrowsTicks = 0;
    private int sneakHoldTicks = 0;

    private double lastEdgeDist = Double.MAX_VALUE;
    private int noProgressTicks = 0, stuckTicks = 0, escapeBoostTicks = 0, surfacingTicks = 0;
    private Vec3d lastPos = null;

    private int verifyTicks = 0, retryCount = 0, stasisPearlCountBefore = -1;

    private Vec3d stepBackTarget = null;
    private int stepBackTicksRemaining = 0;
    private BlockPos retreatGoalBlock = null;

    private BlockPos lastTrapdoorUsed = null;
    private int reopenDelayTicks = -1;

    private boolean teleportPending = false;
    private Vec3d teleportOrigin = null;
    private int teleportWindowTicks = 0;
    private int postTeleportDelayTicks = -1;

    private int ticksSinceJoin = 0;
    private int proxMotionTicks = 0;

    private int holdForward = 0, holdSprint = 0, holdJump = 0;

    // Inventory temp state
    private boolean usingOffhand = false;
    private boolean didSilentSwap = false;
    private boolean didMoveFromInv = false;
    private int movedFromSlot = -1;
    private int movedToHotbar = -1;

    private final Set<UUID> proxSeen = new HashSet<>();
    private int proxCdTicks = 0;

    private AssistState lastLoggedState = AssistState.IDLE;

    private boolean needCleanup = false;

    public AutoPearlStasis() {
        super(Stainless.STAINLESS_CATEGORY, "AutoPearlStasis", "Instantly Teleports you to your pearl chamber (alt account required).");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        joinCooldown = 60;
        ticksSinceJoin = 0;
        if (mode.get() == Mode.MAIN) {
            lastCount = countTotems();
            key1WasDown = key2WasDown = false;
            resetAssist();
            teleportPending = false;
            teleportOrigin = null;
            teleportWindowTicks = 0;
            postTeleportDelayTicks = -1;
            proxSeen.clear();
            proxCdTicks = 0;
            proxMotionTicks = 0;
        } else {
            lastTrapdoorUsed = null;
            reopenDelayTicks = -1;
        }
        needCleanup = false;
    }

    @EventHandler
    private void onJoin(GameJoinedEvent e) {
        joinCooldown = 60;
        ticksSinceJoin = 0;
        resetAssist();
        lastTrapdoorUsed = null;
        reopenDelayTicks = -1;
        teleportPending = false;
        teleportOrigin = null;
        teleportWindowTicks = 0;
        postTeleportDelayTicks = -1;
        proxSeen.clear();
        proxCdTicks = 0;
        proxMotionTicks = 0;
        needCleanup = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post e) {
        if (mode.get() == Mode.MAIN) tickMain();
        else tickAlt();
    }

    // ================= MAIN =================
    private void tickMain() {
        if (mc == null || mc.player == null || mc.world == null) return;

        if (needCleanup) {
            cleanupInventoryState();
            needCleanup = false;
        }

        ticksSinceJoin++;

        if (lastPos != null) {
            double dx = mc.player.getX() - lastPos.x;
            double dz = mc.player.getZ() - lastPos.z;
            double sp2 = dx * dx + dz * dz;
            if (sp2 > 0.0025) proxMotionTicks = Math.min(40, proxMotionTicks + 1);
        }

        if (joinCooldown > 0) {
            joinCooldown--;
            lastPos = mc.player.getPos();
            return;
        }
        if (suppressThrowsTicks > 0) suppressThrowsTicks--;
        if (proxCdTicks > 0) proxCdTicks--;

        // Hotkeys -> force flags
        long handle = mc.getWindow() != null ? mc.getWindow().getHandle() : 0;
        int kc1 = parseKey(forceKey1.get());
        if (handle != 0 && kc1 != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = GLFW.glfwGetKey(handle, kc1) == GLFW.GLFW_PRESS;
            if (down && !key1WasDown) forceTeleport1.set(true);
            key1WasDown = down;
        } else key1WasDown = false;

        int kc2 = parseKey(forceKey2.get());
        if (handle != 0 && kc2 != GLFW.GLFW_KEY_UNKNOWN) {
            boolean down = GLFW.glfwGetKey(handle, kc2) == GLFW.GLFW_PRESS;
            if (down && !key2WasDown) forceTeleport2.set(true);
            key2WasDown = down;
        } else key2WasDown = false;

        // Manual forced teleports
        if (forceTeleport1.get()) {
            sendMessageTo(altName1.get(), Math.max(1, threshold1.get()));
            markTeleportPending();
            forceTeleport1.set(false);
        }
        if (forceTeleport2.get()) {
            sendMessageTo(altName2.get(), Math.max(1, threshold2.get()));
            markTeleportPending();
            forceTeleport2.set(false);
        }

        // Auto-send based on totem change
        int current = countTotems();
        if (current != lastCount) {
            if (current <= threshold1.get()) {
                sendMessageTo(altName1.get(), current);
                markTeleportPending();
            }
            if (current <= threshold2.get()) {
                sendMessageTo(altName2.get(), current);
                markTeleportPending();
            }
        }
        lastCount = current;

        // Proximity trigger
        if (enableProx.get()) runProximityTrigger();

        // Detect teleport by position jump
        if (teleportPending) {
            if (teleportWindowTicks-- <= 0) teleportPending = false;
            else {
                Vec3d now = mc.player.getPos();
                if (now.squaredDistanceTo(teleportOrigin) > (12 * 12) || Math.abs(now.y - teleportOrigin.y) > 4.0) {
                    teleportPending = false;
                    postTeleportDelayTicks = postTpDelay.get();
                }
            }
        }

        // Start assist after tp-ok / teleport detection
        if (postTeleportDelayTicks >= 0 && --postTeleportDelayTicks == 0) triggerAssist();

        // Optional: auto-start if stasis is already nearby
        if (assistState == AssistState.IDLE && autoApproach.get() && autoStartNear.get()) {
            if (findStasisAndEdge()) {
                stasisPearlCountBefore = countPearlsInStasis();
                startPathTo(standBlock);
                assistState = AssistState.PATHING;
                logState();
            }
        }

        // Run assist state machine
        if (assistState != AssistState.IDLE && assistState != AssistState.DONE && assistState != AssistState.FAILED) {
            assistTick();
        }

        if (mc.player != null) lastPos = mc.player.getPos();
    }

    // === Stainless-styled proximity logic ===
    private void runProximityTrigger() {
        if (mc == null || mc.player == null || mc.world == null) return;

        if (ticksSinceJoin < proxWarmup.get()) {
            apsProxDebug("Disarmed: warmup (" + ticksSinceJoin + "/" + proxWarmup.get() + " ticks)");
            return;
        }

        if (proxCdTicks > 0) {
            apsProxDebug("Cooling down: " + proxCdTicks + " ticks left");
            return;
        }

        if (proxRequireMotion.get() && proxMotionTicks < 6) {
            apsProxDebug("Disarmed: require-motion enabled, proxMotionTicks=" + proxMotionTicks);
            return;
        }

        if (teleportPending) { apsProxDebug("Disarmed: teleportPending"); return; }
        if (postTeleportDelayTicks >= 0) {
            apsProxDebug("Disarmed: postTeleportDelayTicks=" + postTeleportDelayTicks);
            return;
        }

        Set<String> watch = buildWatchSet();
        boolean matchAnyone = proxMatchAnyone.get() && watch.isEmpty();
        if (!matchAnyone && watch.isEmpty()) {
            apsProxDebug("No names configured and 'match-anyone-when-empty' is OFF.");
            return;
        }

        int r = Math.max(8, proxRadius.get());
        final double r2 = r * r;

        boolean triggered = false;
        int candidates = 0;

        for (var p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.getGameProfile() == null) continue;

            double d2 = p.squaredDistanceTo(mc.player);
            if (d2 > r2) continue;

            String name = p.getGameProfile().getName();
            if (name == null) continue;

            String norm = normalizeName(name);
            if (!matchAnyone && !watch.contains(norm)) continue;

            candidates++;

            if (proxSeen.contains(p.getUuid())) {
                apsProxDebug("Seen already in radius: " + name);
                continue;
            }

            BlockPos bp = p.getBlockPos();
            String info = "Proximity: " + name + " @ " + bp.getX() + " " + bp.getY() + " " + bp.getZ();

            boolean pinged = false;
            switch (proxTargetAlt.get()) {
                case ALT1 -> {
                    if (!altName1.get().trim().isEmpty()) {
                        sendMessageTo(altName1.get(), Math.max(1, threshold1.get()));
                        pinged = true;
                        if (proxChatFeedback.get()) apsInfo(info + " → forced TP via ALT1.");
                    } else if (proxChatFeedback.get()) apsInfo(info + " → ALT1 not set.");
                }
                case ALT2 -> {
                    if (!altName2.get().trim().isEmpty()) {
                        sendMessageTo(altName2.get(), Math.max(1, threshold2.get()));
                        pinged = true;
                        if (proxChatFeedback.get()) apsInfo(info + " → forced TP via ALT2.");
                    } else if (proxChatFeedback.get()) apsInfo(info + " → ALT2 not set.");
                }
                case BOTH -> {
                    boolean any = false;
                    if (!altName1.get().trim().isEmpty()) { sendMessageTo(altName1.get(), Math.max(1, threshold1.get())); any = true; }
                    if (!altName2.get().trim().isEmpty()) { sendMessageTo(altName2.get(), Math.max(1, threshold2.get())); any = true; }
                    pinged = any;
                    if (proxChatFeedback.get()) apsInfo(info + (any ? " → forced TP via BOTH." : " → no alts set."));
                }
            }

            if (pinged) {
                markTeleportPending();
                proxSeen.add(p.getUuid());
                proxCdTicks = Math.max(0, proxCooldown.get());
                triggered = true;
                break;
            }
        }

        proxSeen.retainAll(currentPlayerUUIDsInRadius(r2));

        apsProxDebug("Scan: radius=" + r + ", candidatesInRadius=" + candidates
            + ", watch=" + (watch.isEmpty() ? (matchAnyone ? "ANYONE" : "EMPTY") : watch)
            + (triggered ? ", TRIGGERED" : ", no match"));
    }

    // name helpers
    private Set<String> buildWatchSet() {
        Set<String> set = new LinkedHashSet<>();
        List<String> list = proxNameList.get();
        if (list != null && !list.isEmpty()) {
            for (String s : list) {
                String tok = normalizeName(s);
                if (!tok.isEmpty()) set.add(tok);
            }
        }
        String raw = proxNames.get();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String s : raw.split(",")) {
                String tok = normalizeName(s);
                if (!tok.isEmpty()) set.add(tok);
            }
        }
        return set;
    }

    private String normalizeName(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private Set<UUID> currentPlayerUUIDsInRadius(double r2) {
        Set<UUID> set = new HashSet<>();
        for (var p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.getGameProfile() == null) continue;
            if (p.squaredDistanceTo(mc.player) <= r2) set.add(p.getUuid());
        }
        return set;
    }

    private void markTeleportPending() {
        teleportPending = true;
        teleportOrigin = mc.player.getPos();
        teleportWindowTicks = 200;
        if (assistState != AssistState.IDLE) resetAssist();
        postTeleportDelayTicks = -1;
    }

    private void sendMessageTo(String alt, int count) {
        String name = alt == null ? "" : alt.trim();
        if (!name.isEmpty()) ChatUtils.sendPlayerMsg("/msg " + name + " " + count + " totem remaining");
    }

    @EventHandler
    private void onMainChat(ReceiveMessageEvent event) {
        if (mode.get() != Mode.MAIN || !useChatConfirm.get()) return;
        String m = event.getMessage().getString().toLowerCase(Locale.ROOT);

        boolean fromAlt = false;
        String a1 = altName1.get().trim().toLowerCase(Locale.ROOT);
        String a2 = altName2.get().trim().toLowerCase(Locale.ROOT);
        if (!a1.isEmpty() && m.contains(a1)) fromAlt = true;
        if (!a2.isEmpty() && m.contains(a2)) fromAlt = true;

        if ((fromAlt && m.contains("tp-ok")) || (!fromAlt && m.contains("tp-ok"))) {
            teleportPending = false;
            postTeleportDelayTicks = postTpDelay.get();
        }
    }

    // ================ ALT ===================
    private void tickAlt() {
        if (reopenDelayTicks >= 0) {
            if (reopenDelayTicks == 0 && lastTrapdoorUsed != null) {
                ensureTrapdoorOpen(lastTrapdoorUsed);
                reopenDelayTicks = -1;
                lastTrapdoorUsed = null;
            } else reopenDelayTicks--;
        }
    }

    @EventHandler
    private void onAltChat(ReceiveMessageEvent event) {
        if (mode.get() != Mode.ALT) return;

        Text txt = event.getMessage();
        String msg = txt.getString().toLowerCase(Locale.ROOT);
        String main = mainName.get().trim();
        if (main.isEmpty()) return;

        if (!msg.contains(main.toLowerCase(Locale.ROOT))) return;
        if (!msg.contains("totem remaining")) return;

        int count = -1;
        for (String w : msg.split(" ")) {
            try { count = Integer.parseInt(w); break; }
            catch (NumberFormatException ignored) {}
        }
        if (count == -1 || count > 64) return;

        if (count <= altTriggerThreshold.get()) {
            BlockPos trapdoor = useBestStasisWithPearl();
            if (trapdoor != null) {
                lastTrapdoorUsed = trapdoor;
                reopenDelayTicks = altReopenDelay.get();
                if (altSendConfirm.get()) {
                    String mainUser = mainName.get().trim();
                    if (!mainUser.isEmpty()) ChatUtils.sendPlayerMsg("/msg " + mainUser + " tp-ok");
                }
            } else infoOnce("No armed stasis (pearl-in-water) found nearby.");
        }
    }

    private BlockPos useBestStasisWithPearl() {
        if (mc == null || mc.player == null || mc.world == null) return null;

        BlockPos base = mc.player.getBlockPos();
        int r = 8;
        BlockPos bestTrapdoor = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (mc.world.getFluidState(m).getFluid() != Fluids.WATER) continue;

                    BlockPos trap = m.up();
                    if (!(mc.world.getBlockState(trap).getBlock() instanceof TrapdoorBlock)) continue;
                    if (!detectAnyPearlInWater(m)) continue;

                    double d = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(m));
                    if (d < bestDist) {
                        bestDist = d;
                        bestTrapdoor = trap.toImmutable();
                    }
                }
            }
        }

        if (bestTrapdoor == null) return null;
        interactTrapdoor(bestTrapdoor);
        return bestTrapdoor;
    }

    private void interactTrapdoor(BlockPos trapdoorPos) {
        if (mc.getNetworkHandler() == null) return;
        Vec3d hit = Vec3d.ofCenter(trapdoorPos);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
            new BlockHitResult(hit, Direction.UP, trapdoorPos, false), 0));
    }

    private void ensureTrapdoorOpen(BlockPos trapdoorPos) {
        if (mc == null || mc.world == null || trapdoorPos == null) return;
        BlockState s = mc.world.getBlockState(trapdoorPos);
        if (!(s.getBlock() instanceof TrapdoorBlock)) return;
        Boolean open = s.get(TrapdoorBlock.OPEN);
        if (open != null && !open) interactTrapdoor(trapdoorPos);
    }

    // ============== Assist (MAIN) ==============
    private void triggerAssist() {
        if (!autoApproach.get()) return;
        assistState = AssistState.SEARCH;
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
        cleanupInventoryState();
        releaseSneak();
        releaseAllMoveKeys();
        needCleanup = false;
        logState();
    }

    private void resetAssist() {
        assistState = AssistState.IDLE;
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
        cleanupInventoryState();
        releaseSneak();
        releaseAllMoveKeys();
        needCleanup = false;
        logState();
    }

    private void assistTick() {
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            cleanupInventoryState();
            resetAssist();
            return;
        }
        assistTicks++;

        switch (assistState) {
            case SEARCH -> {
                if (!findStasisAndEdge()) {
                    infoOnce("No stasis found in radius.");
                    assistState = AssistState.FAILED;
                    logState();
                    break;
                }
                stasisPearlCountBefore = countPearlsInStasis();
                startPathTo(standBlock);
                assistState = AssistState.PATHING;
                logState();
            }

            case PATHING -> {
                if (standEdgePos != null) walkTowardExact(standEdgePos);

                if (isInWater()) {
                    faceTowardXZ(standEdgePos);
                    mc.player.setPitch(-exitPitchDeg.get().floatValue());
                    pressForward(exitForwardTicks.get());
                    pressSprint(exitSprintTicks.get());
                    pressJump(exitJumpTicks.get());
                    waterEscapeWatchdog();
                    break;
                }

                if (!isInWater() && isAtEdge()) {
                    stopPath();
                    assistState = AssistState.EDGE_ADJUST;
                    logState();
                } else if (assistTicks > approachTimeout.get()) {
                    stopPath();
                    assistState = AssistState.FAILED;
                    logState();
                }
            }

            case SURFACING -> {
                if (!isInWater() || isHeadAboveSurface()) {
                    assistState = AssistState.PATHING;
                    logState();
                    break;
                }
                mc.player.setPitch(-exitPitchDeg.get().floatValue());
                faceTowardXZ(standEdgePos);
                pressForward(exitForwardTicks.get());
                pressSprint(exitSprintTicks.get());
                pressJump(exitJumpTicks.get());
                if (--surfacingTicks <= 0) {
                    assistState = AssistState.PATHING;
                    logState();
                }
            }

            case EDGE_ADJUST -> {
                if (standEdgePos != null) {
                    Vec3d snap = snapPointOnEdge(standEdgePos);
                    walkTowardExact(snap);
                    if (stasisWater != null) faceTowardXZ(Vec3d.ofCenter(stasisWater));
                }
                waterEscapeWatchdog();

                if (isInWater()) {
                    assistState = AssistState.SURFACING;
                    surfacingTicks = 10;
                    logState();
                    break;
                }

                if (isAtEdge()) {
                    releaseAllMoveKeys();
                    if (sneakWhileAiming.get() && sneakHoldTicks <= 0) {
                        pressSneak();
                        sneakHoldTicks = sneakAimingTicks.get();
                    }

                    mc.player.setPitch(downPitchDeg.get().floatValue());

                    if (!autoRethrow.get()) {
                        assistState = AssistState.DONE;
                        logState();
                        break;
                    }

                    if (!ensurePearlReady()) {
                        cleanupInventoryState();
                        assistState = AssistState.FAILED;
                        logState();
                        break;
                    }

                    throwAttemptTicks = throwWindowTicks.get();
                    throwOnceLatch = false;
                    suppressThrowsTicks = 0;
                    assistState = AssistState.THROWING;
                    logState();
                } else if (assistTicks > approachTimeout.get()) {
                    assistState = AssistState.FAILED;
                    logState();
                }
            }

            case THROWING -> {
                releaseAllMoveKeys();

                if (stasisWater != null) faceTowardXZ(Vec3d.ofCenter(stasisWater));
                float targetPitch = downPitchDeg.get().floatValue();
                mc.player.setPitch(targetPitch);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    mc.player.getYaw(), targetPitch, mc.player.isOnGround(), false
                ));

                if (isInWater()) {
                    if (--throwAttemptTicks <= 0) {
                        cleanupInventoryState();
                        assistState = AssistState.FAILED;
                        logState();
                    }
                    break;
                }

                if (!throwOnceLatch && suppressThrowsTicks == 0 && !pearlOnCooldown()) {
                    if (!ensurePearlReady()) {
                        if (--throwAttemptTicks <= 0) {
                            cleanupInventoryState();
                            assistState = AssistState.FAILED;
                            logState();
                        }
                        break;
                    }

                    Hand hand = usingOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND;

                    Runnable doThrow = () -> {
                        if (mc.interactionManager != null) {
                            mc.interactionManager.interactItem(mc.player, hand);
                            throwOnceLatch = true;
                            thrownThisCycle = true;
                            suppressThrowsTicks = 40;
                            needCleanup = true;
                            apsDebug("Pearl thrown.");
                        }
                    };

                    doThrow.run();
                }

                if (throwOnceLatch) {
                    if (stepBackAfterThrow.get()) {
                        calculateStepBackTarget();
                        if (useBaritone.get() && retreatGoalBlock != null) startPathTo(retreatGoalBlock);
                        stepBackTicksRemaining = Math.max(stepBackTicksRemaining, stepBackTicks.get());
                        assistState = AssistState.STEPPING_BACK;
                    } else {
                        verifyTicks = pearlVerifyTicks.get();
                        assistState = AssistState.VERIFYING;
                    }
                    logState();
                    break;
                }

                if (--throwAttemptTicks <= 0) {
                    cleanupInventoryState();
                    assistState = AssistState.FAILED;
                    logState();
                }

                if (sneakHoldTicks > 0) {
                    sneakHoldTicks--;
                    if (sneakHoldTicks == 0) releaseSneak();
                }
            }

            case STEPPING_BACK -> {
                if (!useBaritone.get() || retreatGoalBlock == null) {
                    if (stepBackTarget != null) stepBackFromStasis();
                }
                if (--stepBackTicksRemaining <= 0 || hasReachedStepBackTarget()) {
                    if (useBaritone.get()) stopPath();
                    verifyTicks = pearlVerifyTicks.get();
                    assistState = AssistState.VERIFYING;
                    logState();
                }
            }

            case VERIFYING -> {
                if (--verifyTicks <= 0) {
                    boolean pearlInStasis = checkPearlIncreasedInStasis()
                        || (stasisWater != null && detectAnyPearlInWater(stasisWater));

                    boolean likelyThrown = thrownThisCycle && pearlOnCooldown();

                    if (pearlInStasis || likelyThrown) {
                        assistState = AssistState.DONE;
                    } else if (retryOnMiss.get() && retryCount < maxRetries.get()) {
                        retryCount++;
                        stasisPearlCountBefore = countPearlsInStasis();
                        if (!ensurePearlReady()) {
                            cleanupInventoryState();
                            assistState = AssistState.FAILED;
                            logState();
                            break;
                        }
                        throwAttemptTicks = throwWindowTicks.get();
                        throwOnceLatch = false;
                        suppressThrowsTicks = 0;
                        assistState = AssistState.THROWING;
                    } else {
                        assistState = thrownThisCycle ? AssistState.DONE : AssistState.FAILED;
                    }
                    logState();
                }
            }

            case DONE, FAILED -> {
                cleanupInventoryState();
                resetAssist();
            }

            default -> {
                cleanupInventoryState();
                assistState = AssistState.FAILED;
                logState();
            }
        }

        maintainMovementKeys();
        if (mc.player != null) lastPos = mc.player.getPos();
    }

    // ==== Silent-swap & inventory handling ====
    private boolean ensurePearlReady() {
        if (mc.player == null || mc.interactionManager == null) return false;

        cleanupInventoryState(); // Ensure clean state before new cycle
        usingOffhand = false;

        if (preferOffhand.get() && mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) {
            usingOffhand = true;
            apsDebug("Using offhand pearl.");
            return true;
        }

        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
            apsDebug("Already holding pearl in main hand.");
            return true;
        }

        FindItemResult hotbarPearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (hotbarPearl.found()) {
            if (silentSwap.get()) {
                apsDebug("Silent-swapping to hotbar slot " + hotbarPearl.slot());
                InvUtils.swap(hotbarPearl.slot(), true);
                didSilentSwap = true;
            } else {
                apsDebug("Swapping to hotbar slot " + hotbarPearl.slot());
                InvUtils.swap(hotbarPearl.slot(), false);
                didSilentSwap = false;
            }
            return true;
        }

        if (pullFromInventory.get()) {
            FindItemResult anyPearl = InvUtils.find(Items.ENDER_PEARL);
            if (anyPearl.found()) {
                int toHotbar = pickHotbarSlot();
                apsDebug("Pulling pearl from inv slot " + anyPearl.slot() + " to hotbar " + toHotbar);
                InvUtils.move().from(anyPearl.slot()).toHotbar(toHotbar);
                InvUtils.swap(toHotbar, silentSwap.get());
                didMoveFromInv = true;
                movedFromSlot = anyPearl.slot();
                movedToHotbar = toHotbar;
                didSilentSwap = silentSwap.get();
                return true;
            }
        }

        apsDebug("No pearls available.");
        return false;
    }

    private void cleanupInventoryState() {
        cleanupInventoryState(didSilentSwap, didMoveFromInv, movedFromSlot, movedToHotbar);
    }

    private void cleanupInventoryState(boolean didSwap, boolean didMove, int fromSlot, int toHotbar) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (didSwap) {
            try {
                InvUtils.swapBack();
                apsDebug("Reverted silent swap.");
            } catch (Throwable t) {
                apsDebug("Failed to revert silent swap: " + t.getMessage());
            }
        }

        if (didMove && toHotbar >= 0 && fromSlot >= 0) {
            ItemStack hotbarStack = safeGet(toHotbar);
            if (!hotbarStack.isEmpty() && hotbarStack.getItem() == Items.ENDER_PEARL) {
                ItemStack original = safeGet(fromSlot);
                if (canAcceptPearls(original)) {
                    apsDebug("Restoring pearl to original slot " + fromSlot);
                    InvUtils.move().fromHotbar(toHotbar).to(fromSlot);
                } else if (silentSwap.get() || swapBackOnStricterServers.get()) {
                    int stackable = findMainInvPearlStackSlot();
                    if (stackable >= 0) {
                        apsDebug("Merging pearl to stack slot " + stackable);
                        InvUtils.move().fromHotbar(toHotbar).to(stackable);
                    } else {
                        int empty = findFirstEmptyMainSlot();
                        if (empty >= 0) {
                            apsDebug("Moving pearl to empty slot " + empty);
                            InvUtils.move().fromHotbar(toHotbar).to(empty);
                        } else {
                            apsDebug("Inventory full; leaving pearl in hotbar slot " + toHotbar);
                        }
                    }
                }
            } else {
                apsDebug("Hotbar slot " + toHotbar + " no longer holds pearl — nothing to restore.");
            }
        }

        usingOffhand = false;
        didSilentSwap = false;
        didMoveFromInv = false;
        movedFromSlot = -1;
        movedToHotbar = -1;
    }

    private ItemStack safeGet(int slot) {
        try {
            return mc.player.getInventory().getStack(slot);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private boolean canAcceptPearls(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() != Items.ENDER_PEARL) return false;
        return stack.getCount() < Math.min(stack.getMaxCount(), 16);
    }

    private int findMainInvPearlStackSlot() {
        int size = mc.player.getInventory().size();
        for (int i = 9; i < size; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == Items.ENDER_PEARL && s.getCount() < Math.min(s.getMaxCount(), 16)) return i;
        }
        return -1;
    }

    private int findFirstEmptyMainSlot() {
        int size = mc.player.getInventory().size();
        for (int i = 9; i < size; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private int pickHotbarSlot() {
        if (mc != null && mc.player != null && preferEmptyHotbar.get()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) return i;
            }
        }
        return MathHelper.clamp(tempHotbarSlot.get(), 0, 8);
    }

    private boolean pearlOnCooldown() {
        try {
            Object icm = mc.player.getItemCooldownManager();
            try {
                Method isCD = icm.getClass().getMethod("isCoolingDown", net.minecraft.item.Item.class);
                Object r = isCD.invoke(icm, Items.ENDER_PEARL);
                return r instanceof Boolean && (Boolean) r;
            } catch (NoSuchMethodException ignore) {}
            try {
                Method gcp = icm.getClass().getMethod("getCooldownProgress", net.minecraft.item.Item.class, float.class);
                Object r = gcp.invoke(icm, Items.ENDER_PEARL, 0f);
                return r instanceof Float && ((Float) r) > 0f;
            } catch (NoSuchMethodException ignore) {}
            try {
                Method gcp2 = icm.getClass().getMethod("getCooldownProgress", net.minecraft.item.ItemStack.class, float.class);
                Object r = gcp2.invoke(icm, new ItemStack(Items.ENDER_PEARL), 0f);
                return r instanceof Float && ((Float) r) > 0f;
            } catch (NoSuchMethodException ignore) {}
        } catch (Throwable ignored) {}
        return false;
    }

    private Vec3d snapPointOnEdge(Vec3d edge) {
        if (stasisWater == null) return edge;
        Vec3d waterCenter = Vec3d.ofCenter(stasisWater);
        Vec3d fromWater = edge.subtract(waterCenter);
        if (fromWater.lengthSquared() < 1e-6) return edge;
        return edge.add(fromWater.normalize().multiply(0.14));
    }

    private boolean isHeadAboveSurface() {
        if (mc == null || mc.player == null || mc.world == null) return false;
        double eyeY = mc.player.getEyeY();
        BlockPos eyePos = BlockPos.ofFloored(mc.player.getX(), eyeY, mc.player.getZ());
        if (mc.world.getFluidState(eyePos).getFluid() != Fluids.WATER) return true;

        BlockPos feet = mc.player.getBlockPos();
        double surfaceY = feet.getY() + 1.0;
        double headY = mc.player.getBoundingBox().maxY;
        return headY > surfaceY + surfaceHeadClearance.get();
    }

    private void waterEscapeWatchdog() {
        if (!advancedWaterEscape.get() || standEdgePos == null || mc.player == null) return;

        Vec3d p = mc.player.getPos();
        double dx = p.x - standEdgePos.x, dz = p.z - standEdgePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        double speedSq = 0;
        if (lastPos != null) {
            double sx = p.x - lastPos.x, sz = p.z - lastPos.z;
            speedSq = sx * sx + sz * sz;
        }

        if (isInWater()) {
            mc.player.setPitch(-exitPitchDeg.get().floatValue());
            faceTowardXZ(standEdgePos);

            pressForward(exitForwardTicks.get());
            pressSprint(exitSprintTicks.get());
            pressJump(exitJumpTicks.get());

            if (lastEdgeDist - dist < 0.02) noProgressTicks++; else noProgressTicks = 0;
            if (speedSq < 0.0004) stuckTicks++; else stuckTicks = 0;

            if (noProgressTicks >= 10 || stuckTicks >= 8) {
                escapeBoostTicks = Math.max(escapeBoostTicks, 6);
                noProgressTicks = 0;
                stuckTicks = 0;
            }
            if (escapeBoostTicks > 0) {
                escapeBoostTicks--;
                strongWaterEscapeBoost();
            }
        } else {
            noProgressTicks = stuckTicks = escapeBoostTicks = 0;
        }

        lastEdgeDist = dist;
    }

    private void strongWaterEscapeBoost() {
        if (mc == null || mc.player == null) return;
        Vec3d target = (standEdgePos != null) ? standEdgePos : mc.player.getPos().add(0, 0, 1);
        faceTowardXZ(target);
        try { mc.player.jump(); } catch (Throwable ignored) {}
        float yaw = mc.player.getYaw();
        Vec3d vel = mc.player.getVelocity();
        Vec3d dir = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        mc.player.setVelocity(vel.add(dir.multiply(0.20)).add(0, 0.32, 0));
        mc.player.setSprinting(true);
    }

    private boolean isAtEdge() {
        if (standEdgePos == null || mc.player == null) return false;
        return isNearExact(standEdgePos, approachDistance.get() * 1.5);
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
            double d = Math.min(Math.max(stepBackDistance.get(), 1.2), 2.4);
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
        stepBackTicksRemaining = Math.max(stepBackTicks.get(), (int) Math.ceil(12 * flatDist));
    }

    private void stepBackFromStasis() {
        if (stepBackTarget == null || mc.player == null) return;

        releaseSneak();
        faceTowardXZ(stepBackTarget);

        pressForward(5);
        pressSprint(8);

        float yaw = mc.player.getYaw();
        Vec3d vel = mc.player.getVelocity();
        Vec3d dir = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        mc.player.setVelocity(vel.add(dir.multiply(0.12)));

        if (mc.player.isOnGround() && stepBackTicksRemaining == stepBackTicks.get() - 1) {
            try { mc.player.jump(); } catch (Throwable ignored) {}
        }
    }

    private boolean hasReachedStepBackTarget() {
        if (stepBackTarget == null || mc.player == null) return true;
        Vec3d currentPos = mc.player.getPos();
        double dx = currentPos.x - stepBackTarget.x;
        double dz = currentPos.z - stepBackTarget.z;
        return (dx * dx + dz * dz) <= 0.49;
    }

    private boolean checkPearlIncreasedInStasis() {
        if (stasisWater == null || mc.world == null) return false;
        int currentCount = countPearlsInStasis();
        return currentCount > stasisPearlCountBefore;
    }

    private boolean detectAnyPearlInWater(BlockPos waterPos) {
        Vec3d c = Vec3d.ofCenter(waterPos);
        Box box = new Box(c.x - 0.5, waterPos.getY(), c.z - 0.5, c.x + 0.5, waterPos.getY() + 1.0, c.z + 0.5);
        List<EnderPearlEntity> pearls = mc.world.getEntitiesByClass(EnderPearlEntity.class, box, Entity::isAlive);
        return !pearls.isEmpty();
    }

    private int countPearlsInStasis() {
        if (stasisWater == null || mc.world == null) return 0;
        Vec3d c = Vec3d.ofCenter(stasisWater);
        Box box = new Box(c.x - 0.5, stasisWater.getY(), c.z - 0.5, c.x + 0.5, stasisWater.getY() + 1.0, c.z + 0.5);
        List<EnderPearlEntity> pearls = mc.world.getEntitiesByClass(EnderPearlEntity.class, box, Entity::isAlive);
        return pearls.size();
    }

    private void startPathTo(BlockPos targetBlock) {
        if (!useBaritone.get()) return;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock")
                .getConstructor(int.class, int.class, int.class)
                .newInstance(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
            Object cgp = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            cgp.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal"))
                .invoke(cgp, goalBlock);
        } catch (Throwable t) {
            // Fallback: manual walking handles movement
        }
    }

    private void stopPath() {
        if (!useBaritone.get()) return;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object cgp = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            try {
                cgp.getClass().getMethod("setGoal", Class.forName("baritone.api.pathing.goals.Goal"))
                    .invoke(cgp, new Object[]{null});
            } catch (NoSuchMethodException ignored) {}
            cgp.getClass().getMethod("cancel").invoke(cgp);
        } catch (Throwable ignored) {}
    }

    private void walkTowardExact(Vec3d target) {
        if (mc.player == null || target == null) return;
        Vec3d pos = mc.player.getPos();
        Vec3d flatDelta = new Vec3d(target.x - pos.x, 0, target.z - pos.z);
        if (flatDelta.lengthSquared() < 1e-5) return;

        faceTowardXZ(target);
        pressForward(3);
        pressSprint(5);

        if (mc.player.isTouchingWater()) {
            Vec3d vel = mc.player.getVelocity();
            Vec3d fwd = new Vec3d(-Math.sin(Math.toRadians(mc.player.getYaw())), 0, Math.cos(Math.toRadians(mc.player.getYaw())));
            mc.player.setVelocity(vel.add(fwd.multiply(0.04)).add(0, 0.03, 0));
        }
    }

    private boolean findStasisAndEdge() {
        BlockPos base = mc.player.getBlockPos();
        int r = searchRadius.get();

        BlockPos bestWater = null;
        BlockPos bestStand = null;
        Vec3d bestEdge = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.Mutable m = new BlockPos.Mutable();

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
                        Vec3d toWater = new Vec3d(waterCenter.x - adjCenter.x, 0, waterCenter.z - adjCenter.z);
                        if (toWater.lengthSquared() < 1e-6) continue;

                        Vec3d edgePoint = adjCenter.add(toWater.normalize().multiply(0.45));
                        double dist = mc.player.getPos().squaredDistanceTo(edgePoint);
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

        stasisWater = bestWater;
        standBlock = bestStand;
        standEdgePos = bestEdge;
        return stasisWater != null && standBlock != null && standEdgePos != null;
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

    private void faceTowardXZ(Vec3d target) {
        if (mc.player == null || target == null) return;
        Vec3d playerPos = mc.player.getPos();
        Vec3d flatDelta = new Vec3d(target.x - playerPos.x, 0, target.z - playerPos.z);
        if (flatDelta.lengthSquared() < 1e-6) return;

        float yaw = (float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(flatDelta.z, flatDelta.x)) - 90.0));
        mc.player.setYaw(yaw);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, mc.player.getPitch(), mc.player.isOnGround(), false));
    }

    private void setKeyPressed(String fieldName, boolean pressed) {
        try {
            Object opts = mc.options;
            Object key = opts.getClass().getField(fieldName).get(opts);
            key.getClass().getMethod("setPressed", boolean.class).invoke(key, pressed);
        } catch (Throwable ignored) {}
    }

    private void pressSneak() {
        try {
            Object opts = mc.options;
            var f = opts.getClass().getField("sneakKey");
            Object key = f.get(opts);
            key.getClass().getMethod("setPressed", boolean.class).invoke(key, true);
        } catch (Throwable t) {
            try { mc.player.setSneaking(true); } catch (Throwable ignored) {}
        }
    }

    private void releaseSneak() {
        try {
            Object opts = mc.options;
            var f = opts.getClass().getField("sneakKey");
            Object key = f.get(opts);
            key.getClass().getMethod("setPressed", boolean.class).invoke(key, false);
        } catch (Throwable t) {
            try { mc.player.setSneaking(false); } catch (Throwable ignored) {}
        }
    }

    private void pressForward(int ticks) { holdForward = Math.max(holdForward, ticks); }
    private void pressSprint(int ticks)  { holdSprint  = Math.max(holdSprint , ticks); }
    private void pressJump(int ticks)    { holdJump    = Math.max(holdJump   , ticks); }

    private void maintainMovementKeys() {
        setKeyPressed("forwardKey", holdForward-- > 0);
        setKeyPressed("sprintKey",  holdSprint--  > 0);
        setKeyPressed("jumpKey",    holdJump--    > 0);
    }

    private void releaseAllMoveKeys() {
        holdForward = holdSprint = holdJump = 0;
        setKeyPressed("forwardKey", false);
        setKeyPressed("sprintKey", false);
        setKeyPressed("jumpKey", false);
    }

    private void infoOnce(String message) {
        if (debugChat.get()) apsDebug(message);
    }

    private void logState() {
        if (debugChat.get() && lastLoggedState != assistState) {
            apsDebug("State: " + assistState);
            lastLoggedState = assistState;
        }
    }

    private int parseKey(String key) {
        try {
            return GLFW.class.getField("GLFW_KEY_" + key.toUpperCase(Locale.ROOT)).getInt(null);
        } catch (Throwable ignored) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    private int countTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING))
                count += mc.player.getInventory().getStack(i).getCount();
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))
            count += mc.player.getOffHandStack().getCount();
        return count;
    }

    // --------- Stainless tag helpers for this module ---------
    private void apsDebug(String msg) {
        // debug color (AQUA) via StainlessModule
        sendDebugMsg("[APS] " + msg);
    }
    private void apsProxDebug(String msg) {
        sendDebugMsg("[APS] [PROX] " + msg);
    }
    private void apsInfo(String msg) {
        // normal info (GRAY) via StainlessModule
        sendInfoMsg("[APS] " + msg);
    }
}
