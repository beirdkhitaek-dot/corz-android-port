package dev.dov.hack.echo.client.modules.internals;

import dev.dov.hack.echo.client.init.CatalogSettings;
import dev.dov.hack.echo.client.init.CatalogText;
import dev.dov.hack.echo.client.event.EventBus;
import dev.dov.hack.echo.client.event.EventPriority;
import dev.dov.hack.echo.client.event.Subscribe;
import dev.dov.hack.echo.client.event.input.PlayerInputEvent;
import dev.dov.hack.echo.client.event.movement.RotationContext;
import dev.dov.hack.echo.client.event.movement.RotationEvent;
import dev.dov.hack.echo.client.modules.ModuleCategory;
import dev.dov.hack.echo.client.modules.Module;
import dev.dov.hack.echo.client.modules.ModuleInfo;
import dev.dov.hack.echo.client.handlers.rotation.AimState;
import dev.dov.hack.echo.client.settings.BooleanSetting;
import dev.dov.hack.echo.client.settings.ChoiceSetting;
import net.minecraft.util.math.MathHelper;

public class SilentAim extends Module {
    private static final CharSequence MODE_NONE = CatalogText.literal("None");
    private static final CharSequence MODE_SILENT = CatalogText.literal("Silent");
    private static final CharSequence MODE_STRICT = CatalogText.literal("Strict");
    private static final int[][] DIRECTIONS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };
    private static ChoiceSetting mode;
    private static BooleanSetting renderRotationsSetting;

    public SilentAim() {
        super(new ModuleInfo(CatalogText.literal("Silent Aim"), CatalogText.literal("Silent movement correction for rotations"), ModuleCategory.INTERNALS));
    }

    
    @Override
    public void postInitialize() {
        EventBus.register(this);
    }

    
    @Override
    public void toggle() {
    }

    
    @Override
    public void setEnabled(boolean requested) {
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean isKeybindable() {
        return false;
    }

    public boolean isCorrectionEnabled() {
        return !mode.isSelected(MODE_NONE);
    }

    public boolean shouldCorrectMovement() {
        if (mode.isSelected(MODE_SILENT)) {
            return true;
        }
        if (!mode.isSelected(MODE_STRICT)) {
            return false;
        }
        Module owner = rotations().snapshot().owner();
        return owner != null && owner.requiresMovementCorrection();
    }

    public static boolean renderRotations() {
        try {
            Class.forName(BooleanSetting.class.getName(), true, BooleanSetting.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Resolved class initialization failed", failure);
        }
        return renderRotationsSetting.getValue();
    }

    @Subscribe
    public void onRotationQuery(RotationEvent event) {
        if (event.getContext() != RotationContext.MOVEMENT || !isCorrectionEnabled()
                || !rotations().isSilentRotationActive()) {
            return;
        }
        event.setYaw(rotations().effectiveRotation().yaw());
        event.setPitch(rotations().effectiveRotation().pitch());
    }

    @Subscribe(priority = EventPriority.LOWEST)
    public void onPlayerInput(PlayerInputEvent event) {
        if (client.player == null || !shouldCorrectMovement() || !rotations().isSilentRotationActive()) {
            return;
        }
        if (rotations().snapshot().state() == AimState.OVERRIDE) {
            return;
        }
        int forward = (int) PlayerInputEvent.movementMultiplier(event.forward, event.backward);
        int strafe = (int) PlayerInputEvent.movementMultiplier(event.left, event.right);
        if (forward == 0 && strafe == 0) {
            return;
        }
        int steps = Math.round(MathHelper.wrapDegrees(
                rotations().effectiveRotation().yaw() - client.player.getYaw()) / 45.0F);
        for (int index = 0; index < DIRECTIONS.length; index++) {
            if (DIRECTIONS[index][0] != forward || DIRECTIONS[index][1] != strafe) {
                continue;
            }
            int[] corrected = DIRECTIONS[Math.floorMod(index + steps, DIRECTIONS.length)];
            event.forward = corrected[0] > 0;
            event.backward = corrected[0] < 0;
            event.left = corrected[1] > 0;
            event.right = corrected[1] < 0;
            return;
        }
    }

    
    public static CharSequence bootstrapSilentMode() {
        return MODE_SILENT;
    }

    public static CharSequence bootstrapNoneMode() {
        return MODE_NONE;
    }

    public static CharSequence bootstrapStrictMode() {
        return MODE_STRICT;
    }

    
    public static void assignBootstrapSettings(SilentAim Module, BooleanSetting preparedRenderRotations, ChoiceSetting preparedMode) {
        renderRotationsSetting = preparedRenderRotations;
        mode = preparedMode;
    }

    
    public static void appendBootstrapSettings(SilentAim Module) {
        CatalogSettings.append(Module, mode);
        CatalogSettings.append(Module, renderRotationsSetting);
    }
}
