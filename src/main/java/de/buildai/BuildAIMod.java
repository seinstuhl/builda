package de.buildai;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildAIMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("buildai");

    @Override
    public void onInitialize() {
        AIConfig.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BuildCommand.register(dispatcher));
        LOGGER.info("Build AI geladen. Nutze /aibuild <beschreibung>");
    }
}
