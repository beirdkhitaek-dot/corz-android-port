package dev.dov.hack.echo.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ProfileStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_STORE_BYTES = 33554432;
    private final Path directory;

    
    public record SaveRequest(String id, String name, String description, String visibility, String type, String data) { }

    
    public ProfileStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    
    public boolean isAvailable() {
        return true;
    }

    
    public JsonObject listProfiles() {
        return JsonParser.parseString(executeOperation("list_profiles_json", null)).getAsJsonObject();
    }

    
    public JsonObject getProfile(String id) {
        return JsonParser.parseString(executeOperation("get_profile_json", new String[]{id})).getAsJsonObject();
    }

    
    public JsonObject saveProfile(SaveRequest request) {
        String[] arguments = request == null ? null : new String[]{request.id(), request.name(), request.description(), request.visibility(), request.type(), request.data()};
        return JsonParser.parseString(executeOperation("save_profile_json", arguments)).getAsJsonObject();
    }

    
    public JsonObject selectProfile(String id) {
        return JsonParser.parseString(executeOperation("select_profile_json", new String[]{id})).getAsJsonObject();
    }

    
    public JsonObject deleteProfile(String id) {
        return JsonParser.parseString(executeOperation("delete_profile_json", new String[]{id})).getAsJsonObject();
    }

    
    public synchronized String executeOperation(String operation, String[] arguments) {
        if ("is_available".equals(operation)) {
            return "true";
        }
        if (!Set.of("list_profiles_json", "get_profile_json", "save_profile_json", "select_profile_json", "delete_profile_json").contains(operation == null ? "" : operation)) {
            return JSON.toJson(failureResponse("This operation is unavailable offline"));
        }
        try {
            Files.createDirectories(this.directory);
            try (FileChannel channel = FileChannel.open(this.directory.resolve("profiles.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    return JSON.toJson(failureResponse("Local configs are in use by another instance"));
                }
                JsonObject store = this.readStore();
                JsonObject response;
                boolean changed = false;
                switch (operation) {
                    case "list_profiles_json": {
                        response = successResponse(store);
                        JsonArray configs = new JsonArray();
                        for (JsonElement element : store.getAsJsonArray("profiles")) {
                            configs.add(profileMetadata(element.getAsJsonObject(), textField(store, "activeConfigId")));
                        }
                        response.add("configs", configs);
                        break;
                    }
                    case "get_profile_json": {
                        String id = argument(arguments, 0);
                        if (id.isEmpty()) {
                            id = textField(store, "activeConfigId");
                        }
                        JsonObject profile = findProfile(store, id);
                        response = profile == null
                                ? failureResponse("Local config not found") : profileResponse(store, profile);
                        break;
                    }
                    case "save_profile_json": {
                        if (arguments == null || arguments.length != 6) {
                            throw new IllegalArgumentException("Invalid config save arguments");
                        }
                        String id = argument(arguments, 0);
                        String name = argument(arguments, 1);
                        String data = argument(arguments, 5);
                        if (name.isBlank()) {
                            throw new IllegalArgumentException("Config name is required");
                        }
                        JsonObject profile = id.isEmpty() ? null : findProfile(store, id);
                        if (!id.isEmpty() && profile == null) {
                            throw new IllegalArgumentException("Local config not found");
                        }
                        if (!data.isEmpty() && !JsonParser.parseString(data).isJsonObject()) {
                            throw new IllegalArgumentException("Config data must be a JSON object");
                        }
                        long now = System.currentTimeMillis();
                        if (profile == null) {
                            profile = new JsonObject();
                            JsonObject config = new JsonObject();
                            id = UUID.randomUUID().toString();
                            config.addProperty("id", id);
                            config.addProperty("author", "Local");
                            config.addProperty("createdAt", now);
                            for (String field : new String[]{"serverName", "serverIp", "serverIconBase64", "sourceId", "publishedId"}) {
                                config.addProperty(field, "");
                            }
                            config.addProperty("owned", true);
                            config.addProperty("publishedChanges", false);
                            config.addProperty("updateAvailable", false);
                            profile.add("config", config);
                            profile.addProperty("data", "{}");
                            store.getAsJsonArray("profiles").add(profile);
                            if (textField(store, "activeConfigId").isEmpty()) {
                                store.addProperty("activeConfigId", id);
                            }
                        }
                        JsonObject config = profile.getAsJsonObject("config");
                        config.addProperty("name", name);
                        config.addProperty("description", argument(arguments, 2));
                        config.addProperty("visibility", "public".equalsIgnoreCase(argument(arguments, 3)) ? "public" : "private");
                        config.addProperty("type", "rage".equalsIgnoreCase(argument(arguments, 4)) ? "rage" : "legit");
                        config.addProperty("updatedAt", now);
                        if (!data.isEmpty()) {
                            profile.addProperty("data", data);
                        }
                        response = profileResponse(store, profile);
                        changed = true;
                        break;
                    }
                    case "select_profile_json": {
                        String id = argument(arguments, 0);
                        JsonObject profile = findProfile(store, id);
                        if (profile == null && !id.isEmpty()) {
                            throw new IllegalArgumentException("Local config not found");
                        }
                        store.addProperty("activeConfigId", id);
                        response = profile == null ? successResponse(store) : profileResponse(store, profile);
                        if (profile == null) {
                            response.add("config", JsonNull.INSTANCE);
                            response.addProperty("data", "");
                        }
                        changed = true;
                        break;
                    }
                    case "delete_profile_json": {
                        String id = argument(arguments, 0);
                        JsonObject profile = findProfile(store, id);
                        if (profile == null) {
                            throw new IllegalArgumentException("Local config not found");
                        }
                        store.getAsJsonArray("profiles").remove(profile);
                        if (id.equals(textField(store, "activeConfigId"))) {
                            store.addProperty("activeConfigId", "");
                        }
                        response = successResponse(store);
                        response.addProperty("deletedConfigId", id);
                        changed = true;
                        break;
                    }
                    default: {
                        throw new AssertionError(operation);
                    }
                }
                if (changed) {
                    this.writeStore(store);
                }
                return JSON.toJson(response);
            }
        } catch (Exception exception) {
            String error = exception instanceof IllegalArgumentException ? exception.getMessage()
                    : "Could not read or save local configs (" + exception.getClass().getSimpleName() + ")";
            System.err.println("[echo local configs] " + error);
            return JSON.toJson(failureResponse(error));
        }
    }

    private JsonObject readStore() throws IOException {
        Path file = this.directory.resolve("profiles.json");
        if (!Files.exists(file)) {
            JsonObject store = new JsonObject();
            store.addProperty("formatVersion", 1);
            store.addProperty("activeConfigId", "");
            store.add("profiles", new JsonArray());
            return store;
        }
        if (Files.size(file) > MAX_STORE_BYTES) {
            throw new IOException("Config store is too large");
        }
        JsonObject store = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (store.get("formatVersion").getAsInt() != 1 || !store.get("profiles").isJsonArray()) {
            throw new IOException("Unsupported config store format");
        }
        Set<String> ids = new HashSet<>();
        for (JsonElement element : store.getAsJsonArray("profiles")) {
            JsonObject profile = element.getAsJsonObject();
            JsonObject config = profile.getAsJsonObject("config");
            String id = textField(config, "id");
            if (id.isEmpty() || !ids.add(id) || !JsonParser.parseString(textField(profile, "data")).isJsonObject()) {
                throw new IOException("Invalid config store");
            }
            for (String field : new String[]{"name", "description", "author", "visibility", "type", "serverName", "serverIp", "serverIconBase64", "sourceId", "publishedId"}) {
                textField(config, field);
            }
            config.get("createdAt").getAsLong();
            config.get("updatedAt").getAsLong();
        }
        String activeId = textField(store, "activeConfigId");
        if (!activeId.isEmpty() && !ids.contains(activeId)) {
            throw new IOException("Invalid selected config");
        }
        return store;
    }

    private void writeStore(JsonObject store) throws IOException {
        byte[] bytes = JSON.toJson(store).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STORE_BYTES) {
            throw new IOException("Config store is too large");
        }
        Path file = this.directory.resolve("profiles.json");
        Path temporaryFile = Files.createTempFile(this.directory, "profiles-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            if (Files.exists(file)) {
                Files.copy(file, this.directory.resolve("profiles.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static String textField(JsonObject object, String key) {
        return object.get(key).getAsString();
    }

    private static String argument(String[] arguments, int index) {
        return arguments == null || index >= arguments.length || arguments[index] == null ? "" : arguments[index];
    }

    private static JsonObject findProfile(JsonObject store, String id) {
        for (JsonElement element : store.getAsJsonArray("profiles")) {
            JsonObject profile = element.getAsJsonObject();
            if (id.equals(textField(profile.getAsJsonObject("config"), "id"))) {
                return profile;
            }
        }
        return null;
    }

    private static JsonObject profileMetadata(JsonObject profile, String activeId) {
        JsonObject config = profile.getAsJsonObject("config").deepCopy();
        config.addProperty("active", activeId.equals(textField(config, "id")));
        return config;
    }

    private static JsonObject successResponse(JsonObject store) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("error", "");
        response.addProperty("activeConfigId", textField(store, "activeConfigId"));
        return response;
    }

    private static JsonObject profileResponse(JsonObject store, JsonObject profile) {
        JsonObject response = successResponse(store);
        response.add("config", profileMetadata(profile, textField(store, "activeConfigId")));
        response.addProperty("data", textField(profile, "data"));
        return response;
    }

    private static JsonObject failureResponse(String error) {
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("error", error);
        return response;
    }
}
