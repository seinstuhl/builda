package de.buildai;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AIClient {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String SYSTEM_PROMPT = """
        You are a Minecraft building architect. Convert the user's request into a JSON build plan.
        Respond with ONLY one JSON object. No prose, no markdown fences.

        COORDINATES (local, relative):
        - x: west (-) to east (+). y: up. z: positive z = toward the viewer (front), negative z = away (back).
        - (0,0,0) is the middle of the FRONT edge at ground level. y=0 is the lowest layer of the structure
          (the ground surface is at y=-1). Center the build on x=0 and extend it toward negative z.
        - The FRONT (entrance, door) faces +z (south). Directional block properties (stairs, doors, ladders,
          trapdoors, ...) must be written for this frame; they are rotated automatically.

        OPERATIONS:
        {"op":"fill","from":[x,y,z],"to":[x,y,z],"block":"minecraft:stone_bricks"}   solid box
        {"op":"hollow","from":[x,y,z],"to":[x,y,z],"block":"minecraft:oak_planks"}   box shell, interior cleared to air
        {"op":"set","pos":[x,y,z],"block":"minecraft:oak_stairs[facing=south,half=bottom]"} single block

        RULES:
        - Later operations overwrite earlier ones. Use fill/hollow for large areas, set for details.
        - Only valid vanilla block ids, optionally with [property=value] states. Doors need both halves.
        - Maximum %d blocks in total and %d blocks per edge of one operation.
        - Make it look good: foundation, walls, windows (glass_pane), roof with stairs/slabs, interior,
          lighting (lanterns, torches), and small details, unless the user asks for something minimal.

        OUTPUT: {"name":"short name","operations":[ ... ]}
        """;

    public static JsonObject generate(String userPrompt) throws Exception {
        AIConfig cfg = AIConfig.get();

        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);
        body.addProperty("max_tokens", cfg.maxTokens);
        body.addProperty("system", SYSTEM_PROMPT.formatted(cfg.maxBlocks, cfg.maxSize));
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", userPrompt);
        messages.add(msg);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.apiUrl))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds))
                .header("content-type", "application/json")
                .header("x-api-key", cfg.resolvedKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("API-Fehler " + resp.statusCode() + ": " + shorten(resp.body()));
        }

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        StringBuilder text = new StringBuilder();
        for (JsonElement el : json.getAsJsonArray("content")) {
            JsonObject block = el.getAsJsonObject();
            if ("text".equals(block.get("type").getAsString())) text.append(block.get("text").getAsString());
        }
        String out = text.toString();
        int start = out.indexOf('{');
        int end = out.lastIndexOf('}');
        if (start < 0 || end <= start) throw new RuntimeException("KI hat kein JSON geliefert.");
        return JsonParser.parseString(out.substring(start, end + 1)).getAsJsonObject();
    }

    private static String shorten(String s) { return s.length() > 200 ? s.substring(0, 200) + "..." : s; }
}
