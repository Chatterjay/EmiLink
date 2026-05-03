# CLAUDE.md

NeoForge mod (1.21.1) that adds EMI-AE2 utilities: F key search fill and recipe type pre-fill for ExtendedAE_Plus ProviderSelectScreen.

## Build

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot" ./gradlew build
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot" ./gradlew runClient
./gradlew runClient
```

## Structure

```
src/main/java/org/chatterjay/emiextend/
  EmiAE2.java                        -- @Mod entry point
  client/
    InputEvents.java                  -- F key pressed handler (ScreenEvent.KeyPressed.Pre)
    ModKeybindings.java               -- KeyMapping bound to F
  mixin/
    MEStorageScreenAccessor.java      -- @Accessor for searchField + @Invoker for setSearchText
    EmiEncodePatternHandlerMixin.java -- Captures recipe type when EMI transfers recipe to pattern terminal
  util/
    ModLogger.java                    -- Debug logging wrapper (Slf4j)
    ProviderSearchHelper.java         -- Reflection bridge to ExtendedAE_Plus's RecipeTypeNameConfig
```

## Features

1. **F Search** — Press F while hovering an item in EMI sidebar to fill AE2 terminal search with its display name. Only triggers on MEStorageScreen.

2. **Recipe Type Pre-fill** — When EMI transfers a recipe to the Pattern Encoding Terminal (+ button), the recipe type name is captured and stored. If ExtendedAE_Plus's ProviderSelectScreen is opened afterward (upload flow), it automatically fills the search box with the mapped recipe type name (e.g., "熔炉" for smelting, "组装机" for assembler).

## Recipe Type Flow

```
EMI + button clicked on recipe
  → EmiEncodePatternHandler.transferRecipe()
  → EmiEncodePatternHandlerMixin captures recipe
  → ProviderSearchHelper → RecipeTypeNameConfig.setLastProcessingName()
  → (later) ExtendedAE_Plus opens ProviderSelectScreen
  → RecipeTypeNameConfig.consumeLastProviderSearchKey() → search pre-filled
```

## Dependencies

- NeoForge (required)
- EMI (required, compileOnly + runtime)
- AE2 (required, compileOnly + runtime)
- ExtendedAE_Plus (optional development dependency, local JAR in libs/)

## Conventions

- Mixin methods use `emilink$` prefix
- Mod ID: `emilink`, group: `org.chatterjay`
