package dev.dov.hack.echo.client.keyboard;

import dev.dov.hack.echo.client.event.input.AttackKeyEvent;
import dev.dov.hack.echo.client.event.input.KeybindingEvent;
import dev.dov.hack.echo.client.event.input.UseKeyEvent;
import dev.dov.hack.echo.client.mixin.accessor.KeyBindingAccessor;
import dev.dov.hack.echo.client.mixin.accessor.MouseAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class InputState {
    private InputState() {
    }

    
    public static void applyExtraClicks(KeybindingEvent event) {
        if (event.cancelled || event.getClickCountDelta() <= 0
                || MinecraftClient.getInstance().currentScreen != null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (event instanceof AttackKeyEvent) {
            replayMouseClicks(client, client.options.attackKey, event.getClickCountDelta());
        } else if (event instanceof UseKeyEvent) {
            replayMouseClicks(client, client.options.useKey, event.getClickCountDelta());
        }
    }

    private static void replayMouseClicks(MinecraftClient client, KeyBinding binding, int count) {
        if (KeybindingCache.isPressed(binding)) {
            return;
        }
        KeyStateOverrides.setPressed(binding, true);
        InputUtil.Key key = ((KeyBindingAccessor) binding).getBoundKey();
        if (key.getCategory() != InputUtil.Type.MOUSE) {
            return;
        }
        boolean wasPressed = binding.isPressed();
        long handle = client.getWindow().getHandle();
        MouseInput input = new MouseInput(key.getCode(), 0);
        for (int i = 0; i < count; i++) {
            ((MouseAccessor) client.mouse).invokeOnMouseButton(handle, input, GLFW.GLFW_PRESS);
            ((MouseAccessor) client.mouse).invokeOnMouseButton(handle, input, GLFW.GLFW_RELEASE);
        }
        binding.setPressed(wasPressed);
    }
}
