# Build AI (Fabric, Minecraft 26.2)

Voraussetzungen: JDK 25, Gradle 9.5.1+ (Loom 1.17), Fabric Loader 0.19.3+, Fabric API 0.152.x+26.2.
Ab 26.1 ist Minecraft unobfuskiert -> Mojang-Namen, keine Yarn-Mappings, Plugin `net.fabricmc.fabric-loom`.

## Bauen
1. `gradle wrapper --gradle-version 9.5.1` (einmalig), dann `./gradlew build`
2. `build/libs/buildai-1.0.0.jar` + Fabric API in den mods-Ordner
3. Einmal starten -> `config/buildai.json` -> API-Key eintragen (oder Umgebungsvariable ANTHROPIC_API_KEY),
   dann `/aibuild reload`

## Befehle (OP / Gamemaster)
- `/aibuild <beschreibung>`
- `/aibuild undo`
- `/aibuild reload`
