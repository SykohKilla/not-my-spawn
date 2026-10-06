# Not My Spawn!

A lightweight quality-of-life mod for Minecraft 1.21.1 on NeoForge, focused on safer and clearer bed mechanics and respawning.

## Development environment

- Minecraft 1.21.1
- NeoForge 21.1.250
- Java 21
- ModDevGradle 2.0.147
- Parchment mappings 2024.11.17

## Commands

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

The built mod JAR is written to `build/libs/`.

## Behavior

- Using a different bed or charged respawn anchor while the current respawn point is still usable opens Minecraft's standard confirmation screen.
- Confirming replays the original interaction on the server, allowing vanilla to remain authoritative for sleeping, anchor charging, explosions, obstruction, and multiplayer behavior.
- Breaking, obstructing, or depleting the saved respawn block produces one concise action-bar warning per state transition.
- Restoring a blocked respawn location produces a single recovery message.
- Health is checked after relevant block changes and player lifecycle events, with a low-frequency fallback check every five seconds.

The implementation adds no gameplay textures, items, blocks, particles, sounds, or custom-styled screens.
