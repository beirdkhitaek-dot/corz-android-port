package dev.dov.hack.echo.client.config;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ProfileService {
    private final ProfileStore store;

    
    public ProfileService(Path directory) {
        store = new ProfileStore(directory);
    }

    
    public static ProfileService forClient() {
        return ClientInstance.SERVICE;
    }

    
    public boolean isAvailable() {
        return store.isAvailable();
    }

    
    public JsonObject listProfiles() {
        return store.listProfiles();
    }

    
    public JsonObject getProfile(String id) {
        return store.getProfile(id);
    }

    
    public JsonObject saveProfile(ProfileStore.SaveRequest request) {
        return store.saveProfile(request);
    }

    
    public JsonObject selectProfile(String id) {
        return store.selectProfile(id);
    }

    
    public JsonObject deleteProfile(String id) {
        return store.deleteProfile(id);
    }

    
    public String executeOperation(String operation, String[] arguments) {
        return store.executeOperation(operation, arguments);
    }

    private static final class ClientInstance {
        private static final ProfileService SERVICE = new ProfileService(
                FabricLoader.getInstance().getConfigDir().resolve("echo"));
    }
}
