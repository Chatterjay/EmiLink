# Development Guide

## Environment

- Minecraft 1.21.1, NeoForge 21.1.220
- Java 21, Gradle 8.8
- Dependencies: EMI 1.1.22, AE2 19.2.17

## Commands

```bash
./gradlew build              # Build mod jar (output in build/libs/)
./gradlew runClient          # Launch Minecraft client
./gradlew runServer          # Launch dedicated server
./gradlew runData            # Run data generation
./gradlew clean              # Clean build
```

## Mod Details

- **Mod ID**: `emilink`
- **Display Name**: EmiLink
- **Version**: 1.0-SNAPSHOT
- **License**: GNU LGPL 3.0

## Dependencies (build.gradle)

EMI and AE2 are fetched from maven repositories:
- EMI: `https://maven.terraformersmc.com/releases/`
- AE2: `https://maven.appliedenergistics.org/releases/`

## Mixin Config

`src/main/resources/emilink.mixins.json` has two sections:

- `client` — EMI-related mixins (only loaded on client)
- `mixins` — AE2 crafting logic mixins (loaded on both client and dedicated server)
