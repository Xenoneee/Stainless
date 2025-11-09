package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AntiConcrete extends StainlessModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("When to place the button.")
        .defaultValue(Mode.Strict)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("smart-range")
        .description("Enemy range to trigger placing.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 7)
        .build()
    );

    private final Setting<Boolean> silentSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-inventory-swap")
        .description("Temporarily moves a button to hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hotbarSlotSetting = sgGeneral.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Which hotbar slot to place the button into.")
        .defaultValue(1)
        .min(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Integer> returnDelay = sgGeneral.add(new IntSetting.Builder()
        .name("return-delay")
        .description("Delay before returning button to inventory (in ticks). 20 ticks = 1 second.")
        .defaultValue(40)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate toward blocks when placing.")
        .defaultValue(true)
        .build()
    );

    private int returnTimer = -1;
    private int originalSlot = -1;
    private boolean waitingToReturn = false;
    private int noButtonCooldown = 0;

    public AntiConcrete() {
        super(Stainless.STAINLESS_CATEGORY, "AntiConcrete", "Places a button under yourself when enemies are nearby or dropping blocks above you.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (waitingToReturn) {
            if (--returnTimer <= 0) {
                int hotbarSlot = hotbarSlotSetting.get() - 1;
                InvUtils.move().from(hotbarSlot).to(originalSlot);
                waitingToReturn = false;
            }
        }

        if (noButtonCooldown > 0) noButtonCooldown--;

        if (TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestDistance) != null) {
            if (mode.get() == Mode.Smart) {
                if (isConcreteAbove()) tryPlaceButton();
            } else {
                tryPlaceButton();
            }
        }
    }

    private void tryPlaceButton() {
        BlockPos currentPos = mc.player.getBlockPos();
        if (isButtonBlock(mc.world.getBlockState(currentPos).getBlock())) return;

        FindItemResult button = InvUtils.findInHotbar(stack -> isButton(stack.getItem()));

        originalSlot = -1;
        boolean swapped = false;

        if (!button.found() && silentSwap.get()) {
            FindItemResult invButton = InvUtils.find(stack -> isButton(stack.getItem()));
            if (invButton.found() && invButton.slot() >= 9) {
                int hotbarSlot = hotbarSlotSetting.get() - 1;
                originalSlot = invButton.slot();

                InvUtils.move().from(invButton.slot()).to(hotbarSlot);
                swapped = true;

                button = InvUtils.findInHotbar(stack -> isButton(stack.getItem()));
            }
        }

        if (!button.found()) {
            if (noButtonCooldown == 0) {
                warning("No button in hotbar or inventory.");
                noButtonCooldown = 40; // 2 seconds
            }
            return;
        }

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(currentPos.toCenterPos()), Rotations.getPitch(currentPos.toCenterPos()));
        }

        BlockUtils.place(currentPos, button, rotate.get(), 0);

        if (swapped && originalSlot != -1) {
            returnTimer = returnDelay.get();
            waitingToReturn = true;
        }
    }

    private boolean isConcreteAbove() {
        BlockPos base = mc.player.getBlockPos();

        for (int i = 1; i <= 3; i++) {
            if (isFallingTrapBlock(mc.world.getBlockState(base.up(i)).getBlock())) return true;
        }

        Box box = new Box(
            mc.player.getX() - 0.5, mc.player.getY() + 1, mc.player.getZ() - 0.5,
            mc.player.getX() + 0.5, mc.player.getY() + 4, mc.player.getZ() + 0.5
        );

        for (Entity entity : mc.world.getOtherEntities(null, box)) {
            if (entity instanceof FallingBlockEntity falling) {
                if (isFallingTrapBlock(falling.getBlockState().getBlock())) return true;
            }
        }

        return false;
    }

    private boolean isFallingTrapBlock(Block block) {
        return block.toString().contains("concrete_powder") ||
            block == Blocks.GRAVEL || block == Blocks.SAND || block == Blocks.RED_SAND ||
            block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL;
    }

    private boolean isButtonBlock(Block block) {
        return block.toString().toLowerCase().contains("button");
    }

    private boolean isButton(Item item) {
        return item.toString().toLowerCase().contains("button");
    }

    public enum Mode {
        Strict,
        Smart
    }
}
