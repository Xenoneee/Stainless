package xenon.addon.stainless.modules.autopearlstasis;

import meteordevelopment.meteorclient.settings.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized settings for AutoPearlStasis module
 */
public class APSSettings {

    // ---- Enums
    public enum Mode { MAIN, ALT }
    public enum ThrowMode { PRECISE_AIM, SIMPLE_DOWN }
    public enum ProxAltTarget { ALT1, ALT2, BOTH }

    // ---- Setting Groups
    public final SettingGroup sgGeneral;
    public final SettingGroup sgMainAlt1;
    public final SettingGroup sgMainAlt2;
    public final SettingGroup sgProx;
    public final SettingGroup sgAlt;
    public final SettingGroup sgAssist;
    public final SettingGroup sgWaterExit;
    public final SettingGroup sgInv;

    // ---- General Settings
    public final Setting<Mode> mode;

    // ---- Main Alt 1 Settings
    public final Setting<String> altName1;
    public final Setting<Integer> threshold1;
    public final Setting<Boolean> forceTeleport1;
    public final Setting<String> forceKey1;

    // ---- Main Alt 2 Settings
    public final Setting<String> altName2;
    public final Setting<Integer> threshold2;
    public final Setting<Boolean> forceTeleport2;
    public final Setting<String> forceKey2;

    // ---- Proximity Settings
    public final Setting<Boolean> enableProx;
    public final Setting<Integer> proxRadius;
    public final Setting<List<String>> proxNameList;
    public final Setting<String> proxNames;
    public final Setting<Boolean> proxMatchAnyone;
    public final Setting<Integer> proxWarmup;
    public final Setting<ProxAltTarget> proxTargetAlt;
    public final Setting<Integer> proxCooldown;
    public final Setting<Boolean> proxChatFeedback;
    public final Setting<Boolean> proxRequireMotion;

    // ---- Alt Settings
    public final Setting<String> mainName;
    public final Setting<Integer> altTriggerThreshold;
    public final Setting<Boolean> altSendConfirm;
    public final Setting<Integer> altReopenDelay;

    // ---- Stasis Assist Settings
    public final Setting<Boolean> autoApproach;
    public final Setting<Boolean> autoRethrow;
    public final Setting<Boolean> autoStartNear;
    public final Setting<ThrowMode> throwMode;
    public final Setting<Integer> downPitchDeg;
    public final Setting<Integer> pitchHoldTicks;
    public final Setting<Integer> throwWindowTicks;
    public final Setting<Integer> retryGapTicks;
    public final Setting<Boolean> stepBackAfterThrow;
    public final Setting<Integer> stepBackTicks;
    public final Setting<Double> stepBackDistance;
    public final Setting<Boolean> sneakWhileAiming;
    public final Setting<Integer> sneakAimingTicks;
    public final Setting<Integer> searchRadius;
    public final Setting<Integer> approachTimeout;
    public final Setting<Double> approachDistance;
    public final Setting<Boolean> useChatConfirm;
    public final Setting<Integer> postTpDelay;
    public final Setting<Integer> pearlVerifyTicks;
    public final Setting<Boolean> retryOnMiss;
    public final Setting<Integer> maxRetries;
    public final Setting<Boolean> useBaritone;
    public final Setting<Double> surfaceHeadClearance;
    public final Setting<Boolean> advancedWaterEscape;
    public final Setting<Boolean> debugChat;

    // ---- Water Exit Settings
    public final Setting<Integer> exitPitchDeg;
    public final Setting<Integer> exitForwardTicks;
    public final Setting<Integer> exitSprintTicks;
    public final Setting<Integer> exitJumpTicks;

    // ---- Inventory Settings
    public final Setting<Boolean> preferOffhand;
    public final Setting<Boolean> silentSwap;
    public final Setting<Boolean> swapBackOnStricterServers;
    public final Setting<Boolean> pullFromInventory;
    public final Setting<Boolean> preferEmptyHotbar;
    public final Setting<Integer> tempHotbarSlot;

    // ---- Constructor
    public APSSettings(Settings settings) {
        // Initialize groups
        sgGeneral = settings.getDefaultGroup();
        sgMainAlt1 = settings.createGroup("Main (Alt #1)");
        sgMainAlt2 = settings.createGroup("Main (Alt #2)");
        sgProx = settings.createGroup("Proximity Trigger");
        sgAlt = settings.createGroup("Alt (Receiver)");
        sgAssist = settings.createGroup("Stasis Assist");
        sgWaterExit = settings.createGroup("Water Exit Tuning");
        sgInv = settings.createGroup("Inventory");

        // General
        mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("mode").description("Run as your main account (sender) or alt account (receiver).")
            .defaultValue(Mode.MAIN).build());

        // Main Alt 1
        altName1 = sgMainAlt1.add(new StringSetting.Builder()
            .name("alt1-name").description("Alt #1 username to /msg.").defaultValue("").build());
        threshold1 = sgMainAlt1.add(new IntSetting.Builder()
            .name("alt1-totem-threshold").description("Auto-sends to Alt #1 when your totems drop to this count (or below).")
            .defaultValue(1).min(0).max(64).sliderMin(0).sliderMax(64).build());
        forceTeleport1 = sgMainAlt1.add(new BoolSetting.Builder()
            .name("alt1-force-teleport").description("Forces a /msg to Alt #1 this tick, then resets.")
            .defaultValue(false).build());
        forceKey1 = sgMainAlt1.add(new StringSetting.Builder()
            .name("alt1-force-key").description("Key to instantly force-send to Alt #1 (e.g., G, F4, SPACE). Leave blank to disable.")
            .defaultValue("").build());

        // Main Alt 2
        altName2 = sgMainAlt2.add(new StringSetting.Builder()
            .name("alt2-name").description("Alt #2 username to /msg.").defaultValue("").build());
        threshold2 = sgMainAlt2.add(new IntSetting.Builder()
            .name("alt2-totem-threshold").description("Auto-sends to Alt #2 when your totems drop to this count (or below).")
            .defaultValue(1).min(0).max(64).sliderMin(0).sliderMax(64).build());
        forceTeleport2 = sgMainAlt2.add(new BoolSetting.Builder()
            .name("alt2-force-teleport").description("Forces a /msg to Alt #2 this tick, then resets.")
            .defaultValue(false).build());
        forceKey2 = sgMainAlt2.add(new StringSetting.Builder()
            .name("alt2-force-key").description("Key to instantly force-send to Alt #2 (e.g., H, F5, LEFT_SHIFT). Leave blank to disable.")
            .defaultValue("").build());

        // Proximity
        enableProx = sgProx.add(new BoolSetting.Builder()
            .name("enable").description("Force-teleport when a target player enters render distance.")
            .defaultValue(false).build());
        proxRadius = sgProx.add(new IntSetting.Builder()
            .name("radius").description("Max distance to check (blocks).")
            .defaultValue(64).min(8).max(160).sliderMin(8).sliderMax(160).visible(enableProx::get).build());
        proxNameList = sgProx.add(new StringListSetting.Builder()
            .name("names-list").description("Click + to add player names. Leave empty = uses 'match-anyone-when-empty'.")
            .defaultValue(new ArrayList<>()).visible(enableProx::get).build());
        proxNames = sgProx.add(new StringSetting.Builder()
            .name("names-comma").description("Comma-separated names if you prefer one text box.")
            .defaultValue("").visible(enableProx::get).build());
        proxMatchAnyone = sgProx.add(new BoolSetting.Builder()
            .name("match-anyone-when-empty").description("If no names are configured, match anyone in radius.")
            .defaultValue(false).visible(enableProx::get).build());
        proxWarmup = sgProx.add(new IntSetting.Builder()
            .name("prox-warmup-ticks").description("Suppress proximity triggers for this many ticks after joining a world.")
            .defaultValue(120).min(0).max(400).sliderMin(0).sliderMax(400).visible(enableProx::get).build());
        proxTargetAlt = sgProx.add(new EnumSetting.Builder<ProxAltTarget>()
            .name("send-to").description("Which alt to contact when proximity triggers.")
            .defaultValue(ProxAltTarget.ALT1).visible(enableProx::get).build());
        proxCooldown = sgProx.add(new IntSetting.Builder()
            .name("cooldown-ticks").description("Minimum ticks between proximity triggers.")
            .defaultValue(60).min(0).max(400).sliderMin(0).sliderMax(400).visible(enableProx::get).build());
        proxChatFeedback = sgProx.add(new BoolSetting.Builder()
            .name("chat-feedback").description("Announce detected player name & coords in chat when triggering.")
            .defaultValue(true).visible(enableProx::get).build());
        proxRequireMotion = sgProx.add(new BoolSetting.Builder()
            .name("require-motion").description("Only trigger if you've been moving recently. Disable to trigger while standing still.")
            .defaultValue(false).visible(enableProx::get).build());

        // Alt (Receiver)
        mainName = sgAlt.add(new StringSetting.Builder()
            .name("main-account-name").description("Enter Main Account Username Here -->")
            .defaultValue("").build());
        altTriggerThreshold = sgAlt.add(new IntSetting.Builder()
            .name("totem-threshold").description("When received count is <= this, flip nearest trapdoor.")
            .defaultValue(1).min(0).max(64).sliderMin(0).sliderMax(64).build());
        altSendConfirm = sgAlt.add(new BoolSetting.Builder()
            .name("send-tp-confirm").description("After flipping a trapdoor, whisper MAIN a 'tp-ok' confirmation.")
            .defaultValue(true).build());
        altReopenDelay = sgAlt.add(new IntSetting.Builder()
            .name("reopen-delay-ticks").description("After using a stasis, wait this many ticks then ensure the trapdoor is open again.")
            .defaultValue(25).min(0).max(200).sliderMin(0).sliderMax(200).build());

        // Stasis Assist
        autoApproach = sgAssist.add(new BoolSetting.Builder()
            .name("auto-approach-stasis").description("After teleport, walk to the block edge next to the water, then throw.")
            .defaultValue(true).build());
        autoRethrow = sgAssist.add(new BoolSetting.Builder()
            .name("auto-rethrow").description("Automatically pitch down & throw the pearl into the chamber.")
            .defaultValue(true).build());
        autoStartNear = sgAssist.add(new BoolSetting.Builder()
            .name("auto-start-when-near").description("If a valid stasis is nearby while idle, auto-start the assist.")
            .defaultValue(true).build());
        throwMode = sgAssist.add(new EnumSetting.Builder<ThrowMode>()
            .name("throw-mode").description("PRECISE_AIM: face water center & hold short. SIMPLE_DOWN: face water & pitch down.")
            .defaultValue(ThrowMode.SIMPLE_DOWN).build());
        downPitchDeg = sgAssist.add(new IntSetting.Builder()
            .name("down-pitch-deg").description("Pitch used when aiming down into water.")
            .defaultValue(89).min(82).max(90).sliderMin(82).sliderMax(90).build());
        pitchHoldTicks = sgAssist.add(new IntSetting.Builder()
            .name("pitch-hold-ticks").description("How long to hard-hold the down pitch so you see it on screen.")
            .defaultValue(20).min(4).max(40).sliderMin(4).sliderMax(40).build());
        throwWindowTicks = sgAssist.add(new IntSetting.Builder()
            .name("throw-window-ticks").description("How long to attempt throwing before giving up.")
            .defaultValue(40).min(8).max(60).sliderMin(8).sliderMax(60).build());
        retryGapTicks = sgAssist.add(new IntSetting.Builder()
            .name("retry-gap-ticks").description("Ticks between a second throw attempt if no cooldown was detected.")
            .defaultValue(6).min(3).max(15).sliderMin(3).sliderMax(15).build());
        stepBackAfterThrow = sgAssist.add(new BoolSetting.Builder()
            .name("step-back-after-throw").description("Step back after throwing pearl to prevent accidental activation.")
            .defaultValue(true).build());
        stepBackTicks = sgAssist.add(new IntSetting.Builder()
            .name("step-back-ticks").description("Base ticks to spend stepping back from the stasis. Auto-scales with distance.")
            .defaultValue(10).min(3).max(30).sliderMin(3).sliderMax(30).build());
        stepBackDistance = sgAssist.add(new DoubleSetting.Builder()
            .name("step-back-distance").description("Preferred retreat distance (blocks) from the stasis edge.")
            .defaultValue(2.0).min(0.5).max(3.0).sliderMin(0.5).sliderMax(3.0).build());
        sneakWhileAiming = sgAssist.add(new BoolSetting.Builder()
            .name("sneak-while-aiming").description("Hold sneak while aiming/throwing to prevent sliding.")
            .defaultValue(true).build());
        sneakAimingTicks = sgAssist.add(new IntSetting.Builder()
            .name("sneak-aiming-ticks").description("Ticks to hold sneak while aiming & throwing.")
            .defaultValue(8).min(2).max(20).sliderMin(2).sliderMax(20).build());
        searchRadius = sgAssist.add(new IntSetting.Builder()
            .name("search-radius").description("Search radius (blocks) for stasis water + trapdoor.")
            .defaultValue(12).min(4).max(24).sliderMin(4).sliderMax(24).build());
        approachTimeout = sgAssist.add(new IntSetting.Builder()
            .name("approach-timeout-ticks").description("Give up moving after this many ticks (20t = 1s).")
            .defaultValue(360).min(40).max(2000).sliderMin(40).sliderMax(2000).build());
        approachDistance = sgAssist.add(new DoubleSetting.Builder()
            .name("edge-distance").description("How close to the edge to stand before throwing.")
            .defaultValue(0.5).min(0.15).max(0.8).sliderMin(0.15).sliderMax(0.8).build());
        useChatConfirm = sgAssist.add(new BoolSetting.Builder()
            .name("use-chat-confirm").description("Start assist after receiving 'tp-ok' from ALT; fallback is distance jump.")
            .defaultValue(true).build());
        postTpDelay = sgAssist.add(new IntSetting.Builder()
            .name("post-teleport-delay-ticks").description("Wait this many ticks after 'tp-ok' (or teleport jump) before starting.")
            .defaultValue(40).min(0).max(200).sliderMin(0).sliderMax(200).build());
        pearlVerifyTicks = sgAssist.add(new IntSetting.Builder()
            .name("pearl-verify-ticks").description("Ticks to wait and verify pearl landed in stasis water after throwing.")
            .defaultValue(12).min(5).max(30).sliderMin(5).sliderMax(30).build());
        retryOnMiss = sgAssist.add(new BoolSetting.Builder()
            .name("retry-on-miss").description("Retry throwing if pearl doesn't land in stasis water.")
            .defaultValue(true).build());
        maxRetries = sgAssist.add(new IntSetting.Builder()
            .name("max-retries").description("Maximum number of retry attempts if pearl misses.")
            .defaultValue(2).min(1).max(5).sliderMin(1).sliderMax(5).build());
        useBaritone = sgAssist.add(new BoolSetting.Builder()
            .name("use-baritone-if-present").description("Use Baritone for pathing (optional).")
            .defaultValue(true).build());
        surfaceHeadClearance = sgAssist.add(new DoubleSetting.Builder()
            .name("surface-head-clearance").description("Required head height above water surface before leaving SURFACING.")
            .defaultValue(0.60).min(0.20).max(1.20).sliderMin(0.20).sliderMax(1.20).build());
        advancedWaterEscape = sgAssist.add(new BoolSetting.Builder()
            .name("advanced-water-escape").description("Use advanced water exit mechanics to climb out reliably.")
            .defaultValue(true).build());
        debugChat = sgAssist.add(new BoolSetting.Builder()
            .name("debug-chat").description("Verbose state logs (spammy).")
            .defaultValue(false).build());

        // Water Exit
        exitPitchDeg = sgWaterExit.add(new IntSetting.Builder()
            .name("exit-pitch-deg").description("Pitch down while exiting water (used during pathing/surfacing/boost).")
            .defaultValue(25).min(5).max(60).sliderMin(5).sliderMax(60).build());
        exitForwardTicks = sgWaterExit.add(new IntSetting.Builder()
            .name("forward-hold-ticks").description("How long to hold W during water exit pulses.")
            .defaultValue(6).min(1).max(20).sliderMin(1).sliderMax(20).build());
        exitSprintTicks = sgWaterExit.add(new IntSetting.Builder()
            .name("sprint-hold-ticks").description("How long to hold sprint during water exit pulses.")
            .defaultValue(10).min(1).max(20).sliderMin(1).sliderMax(20).build());
        exitJumpTicks = sgWaterExit.add(new IntSetting.Builder()
            .name("jump-pulse-ticks").description("How long to tap jump during water exit pulses.")
            .defaultValue(2).min(0).max(10).sliderMin(0).sliderMax(10).build());

        // Inventory
        preferOffhand = sgInv.add(new BoolSetting.Builder()
            .name("prefer-offhand").description("Use offhand pearls if available.")
            .defaultValue(true).build());
        silentSwap = sgInv.add(new BoolSetting.Builder()
            .name("silent-swap").description("Temporarily swap to pearls, then swap back after throwing.")
            .defaultValue(true).build());
        swapBackOnStricterServers = sgInv.add(new BoolSetting.Builder()
            .name("swap-back-stricter-servers").description("Move pearls back to original inventory slot after throwing (for servers without silent swap).")
            .defaultValue(true).visible(() -> !silentSwap.get()).build());
        pullFromInventory = sgInv.add(new BoolSetting.Builder()
            .name("pull-from-inventory").description("If no pearls in hotbar, pull from main inventory.")
            .defaultValue(true).build());
        preferEmptyHotbar = sgInv.add(new BoolSetting.Builder()
            .name("prefer-empty-hotbar").description("Prefer an empty hotbar slot when pulling from inventory.")
            .defaultValue(true).visible(pullFromInventory::get).build());
        tempHotbarSlot = sgInv.add(new IntSetting.Builder()
            .name("temp-hotbar-slot").description("Fallback hotbar slot [0–8] when pulling from inventory.")
            .defaultValue(8).min(0).max(8).visible(pullFromInventory::get).build());
    }
}
