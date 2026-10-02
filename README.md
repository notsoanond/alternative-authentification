# Alternative Authentication — Forge 1.20.1

Forge port of Alternative Authentication for Minecraft 1.20.1 / Forge 47.4.23.

This version does **not** use Mixin. On `ServerAboutToStartEvent` it replaces Minecraft's `MinecraftSessionService` inside the server's `Services` object with a delegating Authlib session service. Only `hasJoinedServer()` is changed; the other Yggdrasil behavior remains inherited from Authlib.

## Requirements
- Minecraft 1.20.1
- Forge 47.4.23
- Java 17

## Build
```bash
chmod +x ./gradlew
./gradlew clean build
```
Output: `build/libs/alternative_auth-1.1.1.jar`

## Install
Copy the JAR to `mods/` and keep `online-mode=true`.

The mod creates `config/alternative-auth.json`. Set `debug` to `true` while testing. On startup you should see:

`Installed Alternative Authentication session service`

When a player joins, debug mode should show the provider being queried and, on success, `Authenticating player via <provider>`.
