package dev.dov.hack.echo.client.handlers;

import dev.dov.hack.echo.client.event.EventBus;
import dev.dov.hack.echo.client.handlers.rotation.RotationManager;
import java.util.ArrayList;

public final class HandlerManager {
    private final ArrayList<ClientHandler> handlers = new ArrayList<>();
    private final RotationManager rotationManager = new RotationManager();

    public RotationManager getRotationManager() {
        return rotationManager;
    }

    public void initialize() {
        if (!handlers.isEmpty()) return;
        handlers.add(rotationManager);
        handlers.add(new FeatureBindHandler());
        handlers.add(new AttackCooldownTracker());
        handlers.add(new SprintOverrideHandler());
        handlers.add(new DeferredActionQueue());
        handlers.add(new BlockingTicksTracker());
        handlers.add(new ServerFallDistanceTracker());
        handlers.add(new EntityPositionTracker());
        handlers.add(new LatencyTracker());
        for (ClientHandler handler : handlers) EventBus.register(handler);
    }

    public void shutdown() {
        for (ClientHandler handler : handlers) EventBus.unregister(handler);
        DeferredActionQueue.clear();
        rotationManager.reset();
        handlers.clear();
    }
}
