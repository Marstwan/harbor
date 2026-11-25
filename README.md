# Harbor

[![License](https://img.shields.io/badge/license-MIT-brightgreen.svg)](https://github.com/Marstwan/harbor/blob/master/LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-blue.svg)](https://github.com/Marstwan/harbor/releases)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://github.com/Marstwan/harbor)

**Harbor** is a lightweight, modernized sleep mechanics enhancement plugin for Spigot and Paper servers, updated for **Minecraft 26.3**. It makes skipping the night smooth and customizable when a configurable percentage of online players are in bed.

Download the latest version on the **[Releases page](https://github.com/Marstwan/harbor/releases)**.

---

## Features

- **Percentage-Based Night Skip:** Skip the night smoothly (with a time-acceleration animation or instantly, and optional cancellation if players leave bed) when a percentage of players in a world are sleeping.
- **Multi-Language Interface (`en`, `es`, `ca`):** Built-in translations for **English**, **Spanish (Castellano)**, and **Catalan (Català)** selectable directly in `config.yml` (`language: en | es | ca | custom`).
- **Minimalist BossBar UI:** Clean, non-invasive animated progress bar at the top of the screen (zero chat or HUD clutter), supporting legacy color codes (`&a`) and **RGB Hex colors** (`&#RRGGBB` / `#RRGGBB`).
- **Smart Player Exclusions:** Automatically ignores AFK players (with built-in fallback detection or **EssentialsX** integration), vanished players, NPCs, specific game modes, or players with `harbor.ignored`.
- **Weather & Phantom Control:** Automatically clears rain/thunder and resets phantom insomnia statistics upon skipping the night.
- **PlaceholderAPI Support:** Full support for PlaceholderAPI placeholders in player sleep notifications.

---

## Commands & Permissions

| Command | Description | Permission | Default |
| :--- | :--- | :--- | :--- |
| `/harbor` | Displays plugin version information. | — | Everyone |
| `/harbor reload` | Reloads `config.yml` and language files. | `harbor.admin` | OP |
| `/forceskip` | Forces a night skip in your current world. | `harbor.forceskip` | OP |
| — | Excludes a player from the required sleeping count. | `harbor.ignored` | `false` |

---

## Building from Source

Make sure you have **Java 17+**, **Git**, and **Maven** installed:

```bash
git clone https://github.com/Marstwan/harbor.git
cd harbor
mvn clean package
```

The compiled JAR will be generated in the `target/` directory.

---

## Credits & License

This project is a modernized fork of the original [Harbor](https://github.com/nkomarn/harbor) plugin created by [nkomarn](https://github.com/nkomarn).

Licensed under the **MIT License**. See [LICENSE](LICENSE) for more details.
