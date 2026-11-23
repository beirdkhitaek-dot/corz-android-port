package dev.dov.hack.echo.client.modules;

import dev.dov.hack.echo.client.EchoClient;
import dev.dov.hack.echo.client.MinecraftAccess;
import dev.dov.hack.echo.client.event.EventBus;
import dev.dov.hack.echo.client.handlers.rotation.RotationManager;
import dev.dov.hack.echo.client.keyboard.InputKey;
import dev.dov.hack.echo.client.settings.Setting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public class Module implements MinecraftAccess {
    protected static final MinecraftClient client = MinecraftClient.getInstance();

    public List<Setting> settings;
    private boolean enabled;
    private InputKey key;
    private boolean holdMode;
    private boolean visible;
    private final ModuleInfo info;

    public Module(ModuleInfo info) {
        settings = new ArrayList<>();
        enabled = false;
        this.info = info;
        key = info == null ? InputKey.NONE : info.key();
        visible = info == null || info.visible();
    }

    protected final RotationManager rotations() {
        return EchoClient.handlerManager.getRotationManager();
    }

    
    public void initSettings(boolean clearExisting) {
        if (clearExisting) {
            settings.clear();
        }
        Class<?> type = getClass();
        while (type != null && Module.class.isAssignableFrom(type)) {
            collectDeclaredSettings(type);
            type = type.getSuperclass();
        }
        postInitialize();
    }

    public void initSettings() {
        initSettings(true);
    }

    private void collectDeclaredSettings(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(this);
                if (value instanceof Setting setting && !settings.contains(setting)) {
                    settings.add(setting);
                }
            } catch (IllegalAccessException failure) {
                failure.printStackTrace();
            }
        }
    }

    
    public void postInitialize() {
    }

    public CharSequence getName() {
        if (info == null) {
            return getClass().getSimpleName();
        }
        CharSequence name = info.name();
        return name == null || name.isEmpty() ? getClass().getSimpleName() : name;
    }

    public CharSequence getDescription() {
        if (info == null) {
            return "";
        }
        CharSequence description = info.description();
        return description == null ? "" : description;
    }

    public ModuleCategory getCategory() {
        return info == null ? ModuleCategory.INTERNALS : info.moduleCategory();
    }

    
    public void onEnable() {
        EventBus.register(this);
    }

    
    public void onDisable() {
        EventBus.unregister(this);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    
    public void setEnabled(boolean requested) {
        if (enabled == requested) {
            return;
        }
        if (requested && !canEnable()) {
            return;
        }
        enabled = requested;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasSettings() {
        return !settings.isEmpty();
    }

    public void setKey(InputKey key) {
        this.key = key;
    }

    public InputKey getKey() {
        return key;
    }

    public void setHoldMode(boolean holdMode) {
        this.holdMode = holdMode;
    }

    public boolean isHoldMode() {
        return holdMode;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    
    public String getInfoText() {
        return "";
    }

    
    public CharSequence getSuffix() {
        return "";
    }

    
    public boolean recordsStateChanges() {
        return true;
    }

    public boolean canEnable() {
        return true;
    }

    public boolean isFilteredFromDefaultList() {
        return false;
    }

    public boolean isKeybindable() {
        return true;
    }

    public boolean requiresMovementCorrection() {
        return false;
    }

    public boolean isPersistent() {
        return true;
    }

    public boolean persistsEnabledState() {
        return true;
    }

    public boolean isOutsideWorld() {
        return client.player == null || client.world == null;
    }
}
