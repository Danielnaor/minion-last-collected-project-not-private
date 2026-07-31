# Minion Last Collected

A client-side Fabric 1.21.11 mod that remembers when Hypixel SkyBlock minions
were collected and displays the elapsed time above them.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.19.3 or newer
- Fabric API
- Fabric Language Kotlin
- Java 21

## Build

On Windows:

```bat
.\gradlew.bat clean build
```

Install `build\libs\minion-last-collected-1.0.0.jar` in the Fabric instance's
`mods` folder. Do not install the `-sources.jar`.

## Test on Hypixel

1. Enter SkyBlock and go to the island containing the minion.
2. Right-click the minion.
3. Click **Collect All**.
4. Look for the action-bar message **Collection time saved**.
5. Close the menu. The minion should show **Last Collected: ... ago**.

The data file is:

```text
config/minion-last-collected/minions.json
```

Clicking **Pickup Minion** removes that minion's saved record. Clicking a
hopper button updates the collection time, matching the behavior of the
original feature.
