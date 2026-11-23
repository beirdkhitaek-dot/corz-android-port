package dev.dov.hack.echo.client.modules.impl.internals;

import dev.dov.hack.echo.client.EchoClient;
import dev.dov.hack.echo.client.init.CatalogSettings;
import dev.dov.hack.echo.client.init.CatalogText;
import dev.dov.hack.echo.client.config.ModuleConfig;
import dev.dov.hack.echo.client.config.NameRegistry;
import dev.dov.hack.echo.client.event.EventBus;
import dev.dov.hack.echo.client.modules.impl.internals.ClientCleanupAction;
import dev.dov.hack.echo.client.modules.ModuleCategory;
import dev.dov.hack.echo.client.modules.Module;
import dev.dov.hack.echo.client.modules.ModuleInfo;
import dev.dov.hack.echo.client.hud.HudRenderer;
import dev.dov.hack.echo.client.gfx.FullscreenEffects;
import dev.dov.hack.echo.client.gui.ScreenManager;
import dev.dov.hack.echo.client.gui.dashboard.DashboardController;
import dev.dov.hack.echo.client.settings.BooleanSetting;
import dev.dov.hack.echo.client.settings.TextSetting;
import dev.dov.hack.echo.client.utils.audio.AudioUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public class SelfDestruct extends Module {
    private boolean destructionStarted;
    private static BooleanSetting replaceMods;
    private static TextSetting url;

    public SelfDestruct() {
        super(new ModuleInfo(CatalogText.literal("Self Destruct"), CatalogText.literal("Self destructs the client to hide any traces of itself"), ModuleCategory.INTERNALS));
    }

    @Override
    public void postInitialize() {
        url.setVisibilityPredicate(ignored -> replaceMods.getValue());
    }

    @Override
    public boolean isPersistent() { return false; }

    @Override
    public void onEnable() {
        if (destructionStarted || EchoClient.eventsDisabled) return;
        initializeClass(ModuleConfig.class);
        if (EchoClient.ModuleConfig != null) {
            try {
                initializeClass(ModuleConfig.class);
                EchoClient.ModuleConfig.save("_autosave");
            } catch (RuntimeException saveFailure) {
            }
        }
        destructionStarted = true;
        EchoClient.eventsDisabled = true;
        initializeClass(ScreenManager.class);
        if (EchoClient.screenManager != null) {
            initializeClass(ScreenManager.class);
            EchoClient.screenManager.close();
        }
        initializeClass(MinecraftClient.class);
        client.send(this::finishSelfDestruct);
    }

    private void finishSelfDestruct() {
        List<ClientCleanupAction> actions = new ClientStringCleanup(EchoClient.class).collectActions(
                NameRegistry.class, () -> DashboardController.INSTANCE, () -> HudRenderer.INSTANCE);
        if (replaceMods.getValue()) replaceModFiles();
        if (EchoClient.screenManager != null) {
            EchoClient.screenManager.close();
            EchoClient.screenManager.destroy();
        }
        if (EchoClient.handlerManager != null) EchoClient.handlerManager.shutdown();
        if (EchoClient.ModuleManager != null) EchoClient.ModuleManager.shutdownExcept(this);
        AudioUtils.shutdown();
        FullscreenEffects.releaseResources();
        EventBus.clear();
        EchoClient.externalRendererActive = false;
        if (EchoClient.externalRenderer != null) {
            try {
                EchoClient.externalRenderer.close();
            } catch (RuntimeException rendererFailure) {
            }
            EchoClient.externalRenderer = null;
        }
        EchoClient.screenManager = null;
        EchoClient.handlerManager = null;
        EchoClient.ModuleManager = null;
        setEnabled(false);
        if (EchoClient.ModuleConfig != null) EchoClient.ModuleConfig.close();
        EchoClient.ModuleConfig = null;
        Set<String> strings = Collections.newSetFromMap(new IdentityHashMap<>());
        NameRegistry.collectAssignedNames(strings);
        HudRenderer.INSTANCE.collectKeybindIds(strings);
        HudRenderer.INSTANCE.resetTransientState();
        NameRegistry.clearAssignments();
        actions.forEach(ClientCleanupAction::clear);
        actions.clear();
        ClientStringCleanup.clearStrings(strings);
        strings.clear();
        System.gc();
    }

    
    private void replaceModFiles() {
        return;
    }

    
    private Path resolveModPath(String expectedProtocol, String modId) {
        try {
            CodeSource source = EchoClient.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null
                    && expectedProtocol.equalsIgnoreCase(source.getLocation().getProtocol())) {
                Path path = Path.of(source.getLocation().toURI());
                if (Files.isRegularFile(path)) return path;
            }
        } catch (Exception unavailableCodeSource) {
        }
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getOrigin().getPaths())
                .filter(paths -> !paths.isEmpty())
                .map(paths -> paths.get(0))
                .filter(Files::isRegularFile)
                .orElse(null);
    }

    private static void initializeClass(Class<?> type) {
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Resolved class initialization failed", failure);
        }
    }

    
    public static void appendBootstrapSettings(SelfDestruct Module) {
        CatalogSettings.append(Module, replaceMods);
        CatalogSettings.append(Module, url);
    }

    
    public static void assignBootstrapSettings(SelfDestruct Module, BooleanSetting preparedReplaceMods, TextSetting preparedUrl) {
        replaceMods = preparedReplaceMods;
        url = preparedUrl;
    }
}
