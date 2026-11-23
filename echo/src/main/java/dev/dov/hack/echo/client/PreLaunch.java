package dev.dov.hack.echo.client;

import dev.dov.hack.echo.client.init.ClientBootstrap;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class PreLaunch implements PreLaunchEntrypoint {
    
    @Override
    public void onPreLaunch() {
        ClientBootstrap.prepare();
    }
}
