package dev.dov.hack.echo.client.runtime;

import dev.dov.hack.echo.client.EchoClient;
import dev.dov.hack.echo.client.event.runtime.ScriptLoadedEvent;
import dev.dov.hack.echo.client.event.runtime.ScriptUnloadedEvent;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mozilla.javascript.Scriptable;

public class ScriptManager {
    private final Path directory;
    private final ScriptEngine engine;
    private final Map<String, ScriptFeature> loadedScripts;

    
    public ScriptManager(ScriptEngine engine, Path directory) {
        this.directory = directory;
        loadedScripts = new HashMap<>();
        this.engine = engine;
    }

    
    public void loadAll() {
        ensureDirectory();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.js")) {
            for (Path path : paths) load(path);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        if (!loadedScripts.isEmpty()) {
            ScriptEngine.LOGGER.info("Loaded {} script Module(s)", loadedScripts.size());
        }
    }

    
    public void reload(String filename) {
        unload(filename);
        Path path = directory.resolve(filename);
        if (Files.exists(path)) load(path);
    }

    
    public void unload(String filename) {
        ScriptFeature Module = loadedScripts.remove(filename);
        if (Module != null) {
            Module.setEnabled(false);
            EchoClient.ModuleManager.removeFeatureFromLists(Module);
            new ScriptUnloadedEvent(Module, filename).post();
        }
    }

    
    public List<String> listFilenames() {
        ensureDirectory();
        ArrayList<String> result = new ArrayList<>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.js")) {
            for (Path path : paths) result.add(path.getFileName().toString());
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return result;
    }

    private void load(Path path) {
        String filename = path.getFileName().toString();
        try {
            String source = Files.readString(path);
            Scriptable scope = engine.run(source, filename);
            if (scope == null) return;
            ScriptDefinition definition = ScriptDefinitionParser.parse(scope, filename);
            if (definition == null) return;
            ScriptFeature Module = new ScriptFeature(definition, engine);
            Module.initSettings();
            EchoClient.ModuleManager.addFeatureToLists(Module);
            loadedScripts.put(filename, Module);
            new ScriptLoadedEvent(Module, filename).post();
            ScriptEngine.LOGGER.info("Loaded '{}' as '{}'", filename, definition.name());
        } catch (Exception exception) {
            ScriptErrorReporter.report(ScriptEngine.LOGGER, filename, "failed to load", exception);
        }
    }

    private void ensureDirectory() {
        try {
            if (!Files.exists(directory)) Files.createDirectories(directory);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    
    public Map<String, ScriptFeature> loadedScripts() { return loadedScripts; }
}
