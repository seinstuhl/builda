package de.buildai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Konfiguration: config/buildai.json */
public class AIConfig {
    public String apiKey = "";                       // oder Umgebungsvariable ANTHROPIC_API_KEY
    public String apiUrl = "https://api.anthropic.com/v1/messages";
    public String model = "claude-sonnet-5-5";
    public int maxTokens = 8192;
    public int maxBlocks = 20000;                    // Sicherheitslimit pro Bauwerk
    public int maxSize = 64;                         // max. Kantenlaenge einer Operation
    public int timeoutSeconds = 180;

    private static AIConfig INSTANCE = new AIConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static AIConfig get() { return INSTANCE; }

    public String resolvedKey() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey.trim();
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env == null ? "" : env.trim();
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("buildai.json");
        try {
            if (Files.exists(path)) {
                INSTANCE = GSON.fromJson(Files.readString(path), AIConfig.class);
            } else {
                INSTANCE = new AIConfig();
                Files.writeString(path, GSON.toJson(INSTANCE));
            }
        } catch (Exception e) {
            BuildAIMod.LOGGER.error("Config konnte nicht geladen werden", e);
        }
    }
}
