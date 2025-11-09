package xenon.addon.stainless;

import meteordevelopment.meteorclient.mixininterface.IChatHud;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class StainlessModule extends Module {
    // --- Fixed look (tweak here if you ever want different colors) ---
    private static final String     PREFIX_TEXT  = "Stainless";
    private static final Formatting PREFIX_COLOR = Formatting.BLACK;
    private static final Formatting NAME_COLOR   = Formatting.WHITE;
    private static final Formatting ON_COLOR     = Formatting.GREEN;
    private static final Formatting OFF_COLOR    = Formatting.RED;
    private static final Formatting INFO_COLOR   = Formatting.GRAY;
    private static final Formatting DEBUG_COLOR  = Formatting.AQUA;
    private static final Formatting WARN_COLOR   = Formatting.YELLOW;
    private static final Formatting ERR_COLOR    = Formatting.RED;
    private static final Formatting TAG_COLOR    = Formatting.DARK_AQUA; // color for [TAG]

    public StainlessModule(Category category, String name, String description) {
        super(category, name, description);
        // Stop Meteor's per-module pink messages; we'll print our own.
        this.chatFeedback = false;
    }

    // Print explicit target state so OFF is correct from GUI/keybind toggles
    @Override public void onActivate()   { sendToggleMsg(true);  }
    @Override public void onDeactivate() { sendToggleMsg(false); }

    // ---- Public helpers -----------------------------------------------------
    public void sendInfoMsg(String text)  { sendColoredMsg(text, INFO_COLOR,  (name+"-info").hashCode()); }
    public void sendDebugMsg(String text) { sendColoredMsg(text, DEBUG_COLOR, (name+"-debug").hashCode()); }
    public void sendDisableMsg(String reason) {
        if (!shouldChat()) return;
        ChatUtils.forceNextPrefixClass(getClass());
        Text msg = Text.empty()
                .append(prefix())
                .append(Text.literal(name).formatted(NAME_COLOR))
                .append(ScreenTexts.SPACE)
                .append(Text.literal("OFF ").formatted(OFF_COLOR))
                .append(Text.literal(reason).formatted(INFO_COLOR));
        sendMessage(msg, (name + "-disable").hashCode());
    }

    // ---- Tagged helpers (for module-specific debug like [APS]) -------------
    public void sendTaggedInfo(String tag, String text)  { sendTagged(tag, text, INFO_COLOR,  (name+"-tag-info-"+tag).hashCode()); }
    public void sendTaggedDebug(String tag, String text) { sendTagged(tag, text, DEBUG_COLOR, (name+"-tag-dbg-"+tag).hashCode()); }
    public void sendTaggedWarn(String tag, String text)  { sendTagged(tag, text, WARN_COLOR,  (name+"-tag-warn-"+tag).hashCode()); }
    public void sendTaggedErr(String tag, String text)   { sendTagged(tag, text, ERR_COLOR,   (name+"-tag-err-"+tag).hashCode()); }

    public void sendTaggedInfof(String tag, String fmt, Object... args)  { sendTagged(tag, String.format(fmt, args), INFO_COLOR,  (name+"-tag-infof-"+tag).hashCode()); }
    public void sendTaggedDebugf(String tag, String fmt, Object... args) { sendTagged(tag, String.format(fmt, args), DEBUG_COLOR, (name+"-tag-dbgf-"+tag).hashCode()); }
    public void sendTaggedWarnf(String tag, String fmt, Object... args)  { sendTagged(tag, String.format(fmt, args), WARN_COLOR,  (name+"-tag-warnf-"+tag).hashCode()); }
    public void sendTaggedErrf(String tag, String fmt, Object... args)   { sendTagged(tag, String.format(fmt, args), ERR_COLOR,   (name+"-tag-errf-"+tag).hashCode()); }

    private void sendTagged(String tag, String text, Formatting color, int id) {
        if (!shouldChat()) return;
        ChatUtils.forceNextPrefixClass(getClass());
        Text msg = Text.empty()
                .append(prefix())                                   // "[Stainless] "
                .append(Text.literal("[" + tag + "] ").formatted(TAG_COLOR))
                .append(Text.literal(text).formatted(color));
        sendMessage(msg, id);
    }

    // ---- Intercept legacy calls so they use Stainless styling --------------
    @Override public void info(String message, Object... args) {
        sendColoredMsg(args.length == 0 ? message : String.format(message, args), INFO_COLOR, (name+"-info").hashCode());
    }
    @Override public void warning(String message, Object... args) {
        sendColoredMsg(args.length == 0 ? message : String.format(message, args), WARN_COLOR, (name+"-warn").hashCode());
    }
    @Override public void error(String message, Object... args) {
        sendColoredMsg(args.length == 0 ? message : String.format(message, args), ERR_COLOR, (name+"-err").hashCode());
    }

    // ---- Internals ----------------------------------------------------------
    private void sendToggleMsg(boolean enabled) {
        if (!shouldChat()) return;
        ChatUtils.forceNextPrefixClass(getClass());
        Text msg = Text.empty()
                .append(prefix())
                .append(Text.literal(name).formatted(NAME_COLOR))
                .append(ScreenTexts.SPACE)
                .append(Text.literal(enabled ? "ON" : "OFF").formatted(enabled ? ON_COLOR : OFF_COLOR));
        sendMessage(msg, hashCode());
    }

    private void sendColoredMsg(String text, Formatting color, int id) {
        if (!shouldChat()) return;
        ChatUtils.forceNextPrefixClass(getClass());
        Text msg = Text.empty()
                .append(prefix())
                .append(Text.literal(name).formatted(NAME_COLOR))
                .append(ScreenTexts.SPACE)
                .append(Text.literal(text).formatted(color));
        sendMessage(msg, id);
    }

    private Text prefix() { return Text.literal("[" + PREFIX_TEXT + "] ").formatted(PREFIX_COLOR); }

    private boolean shouldChat() { return isWorld(); }

    private boolean isWorld() {
        return mc != null && mc.world != null && mc.inGameHud != null && mc.inGameHud.getChatHud() != null;
    }

    private void sendMessage(Text text, int id) {
        ((IChatHud) mc.inGameHud.getChatHud()).meteor$add(text, id);
    }
}
