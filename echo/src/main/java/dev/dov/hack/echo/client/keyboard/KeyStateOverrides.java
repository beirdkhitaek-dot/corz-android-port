package dev.dov.hack.echo.client.keyboard;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

public final class KeyStateOverrides {
    private static final long OVERRIDE_LIFETIME_NANOS = 75_000_000L;
    private static final Map<KeyBinding, KeyOverride> overrides = new ConcurrentHashMap<>();
    private static final String BOUND_KEY_BRIDGE_METHOD = "bridge$getKey";
    private static Method boundKeyBridge;

    private KeyStateOverrides() {
    }

    
    public static void setPressed(KeyBinding binding, boolean pressed) {
        overrides.put(binding, new KeyOverride(pressed, System.nanoTime() + OVERRIDE_LIFETIME_NANOS));
    }

    
    public static boolean isPressed(KeyBinding binding) {
        Boolean override = activeOverride(binding);
        return override == null ? binding.isPressed() : override;
    }

    
    public static Boolean pressedActionForMouseButton(int code) {
        GameOptions options = MinecraftClient.getInstance().options;
        if (options == null) {
            return null;
        }
        InputKey key = InputKey.fromMouseCode(code);
        for (KeyBinding binding : new KeyBinding[]{options.attackKey, options.useKey}) {
            if (KeybindingCache.getKey(binding) == key && isPressed(binding)) {
                return true;
            }
        }
        return null;
    }

    
    public static Boolean forBoundKey(Object key) {
        for (KeyBinding binding : overrides.keySet()) {
            if (readBoundKey(binding) == key) {
                return activeOverride(binding);
            }
        }
        return null;
    }

    private static Boolean activeOverride(KeyBinding binding) {
        KeyOverride override = overrides.get(binding);
        if (override == null) {
            return null;
        }
        if (override.expiresAtNanos() >= System.nanoTime()) {
            return override.pressed();
        }
        overrides.remove(binding, override);
        return null;
    }

    private static Object readBoundKey(KeyBinding binding) {
        try {
            if (boundKeyBridge == null) {
                boundKeyBridge = binding.getClass().getMethod(BOUND_KEY_BRIDGE_METHOD);
            }
            return boundKeyBridge.invoke(binding);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
