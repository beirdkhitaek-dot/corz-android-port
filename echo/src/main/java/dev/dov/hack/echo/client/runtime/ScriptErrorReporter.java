package dev.dov.hack.echo.client.runtime;

import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;

public final class ScriptErrorReporter {
    private ScriptErrorReporter() {
    }

    public static void report(Logger logger, String scriptId, String message) {
        logger.error("[{}] {}", scriptId, message);
    }

    public static void report(Logger logger, String scriptId, String operation, Throwable error) {
        if (error instanceof RhinoException scriptError) {
            logger.error("[{}] {}: {} ({}:{})", scriptId, operation, scriptError.details(), scriptError.sourceName(), scriptError.lineNumber());
            return;
        }
        error.printStackTrace();
    }
}
