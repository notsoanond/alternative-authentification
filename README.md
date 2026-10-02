# Alternative Authentication — Forge 1.20.1

Forge port of **Alternative Authentication** for Minecraft **1.20.1**.

This is a server-side mod. It keeps the original provider/configuration model and authentication/profile lookup behavior, while replacing Fabric Loader/Loom with Forge/ForgeGradle and loading the Authlib hooks through Mixin.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.23 (compatible with Forge 47.x)
- Java 17
- `online-mode=true` in `server.properties`
- `enforce-secure-profile=false` is recommended when using third-party authentication providers

## Building

1. Install a JDK 17 distribution and verify it:

   ```text
   java -version
   ```

   It should report Java 17.

2. Open a terminal in this project directory.

3. Run the Gradle wrapper:

   **Linux/macOS**
   ```bash
   chmod +x ./gradlew
   ./gradlew clean build
   ```

   **Windows**
   ```bat
   gradlew.bat clean build
   ```

4. The compiled mod will be in:

   ```text
   build/libs/alternative_auth-1.1.1.jar
   ```

5. Copy that JAR into the server's `mods` directory alongside your other Forge 1.20.1 mods.

The first build downloads ForgeGradle, Forge, Minecraft's mappings/dependencies, and the Gradle distribution, so an Internet connection is required for the initial build.

## Development server

To launch the Forge development server from the project:

```bash
./gradlew runServer
```

The generated development server files live under `run/`.

## Configuration

On first startup, the mod creates:

```text
config/alternative-auth.json
```

The default configuration checks Mojang first and Ely.by second. Providers can be reordered, removed, or added. `property_url` is optional and can be used to retrieve additional signed profile properties such as textures.

Example:

```json
{
  "debug": false,
  "providers": [
    {
      "name": "Mojang",
      "check_url": "https://sessionserver.mojang.com/session/minecraft/hasJoined",
      "profiles_url": "https://api.mojang.com/profiles/minecraft"
    },
    {
      "name": "Ely.by",
      "check_url": "https://authserver.ely.by/session/hasJoined",
      "profiles_url": "https://authserver.ely.by/api/profiles/minecraft",
      "property_url": "http://skinsystem.ely.by/textures/signed/{0}"
    }
  ]
}
```

## What was changed from the Fabric project

- Replaced Fabric Loom with ForgeGradle.
- Replaced the Fabric server initializer with a Forge `@Mod` entrypoint.
- Replaced `FabricLoader`'s config directory with Forge's `FMLPaths.CONFIGDIR`.
- Recreated the Authlib Mixin hooks and registered them through Forge's `[[mixins]]` metadata.
- Uses Mixin compatibility level `JAVA_13`, which avoids the warning emitted by Forge 1.20.1's bundled Mixin 0.8.5 while still running on Java 17.
- Switched the project to Java 17, which is the runtime required by Minecraft 1.20.1.
- Replaced `fabric.mod.json` with Forge `META-INF/mods.toml`.
- Removed Fabric API/Fabric Loader dependencies.
- Updated the CI workflow to build with Java 17 and publish the JAR artifact correctly.

## Compatibility note

The port intentionally preserves the original authentication flow rather than changing it into a Forge-specific implementation. The two hooks target Mojang Authlib classes (`YggdrasilMinecraftSessionService` and `YggdrasilGameProfileRepository`), so other mods that modify the same Authlib methods may interact with these mixins.


## Troubleshooting Mixin application

For a temporary diagnostic run, add these JVM properties to the server launch command:

```text
-Dmixin.debug=true -Dmixin.debug.verbose=true -Dmixin.debug.export=true -Dmixin.dumpTargetOnFailure=true
```

Then inspect `latest.log` for Mixin activity. If `mixin.debug.export` is enabled successfully, transformed classes are normally written under `.mixin.out/`.

The mod's own `debug` setting in `config/alternative-auth.json` controls Alternative Authentication's provider logging; it does not enable Mixin's JVM-level diagnostics.
