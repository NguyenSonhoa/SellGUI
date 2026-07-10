# Changelog

## Unreleased

### Added
- Added `SellGUI-DynamicShop` addon support for DynamicShop sell prices, stock updates, and transaction logs.

## 3.1.4 - 2026-07-10

### Fixed
- Fixed worth lore disappearing from player inventory in 3.1.3.
- Worth lore now remains visible in player inventory without using stack-size-specific packet data, so identical stacks can merge.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.1.4.jar`

## 3.1.3 - 2026-07-04

### Fixed
- Fixed packet-injected worth lore preventing identical item stacks from merging in the player inventory.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.1.3.jar`

## 3.1.2 - 2026-06-30

### Fixed
- Fixed worth lore not refreshing after players drop items from their inventory.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.1.2.jar`

## 3.1 - 2026-06-06

### Added
- Added external SellGUI price provider API for addon plugins.
- Added `SellGUI-DynaShop` addon support for ShopGUIPlus-DynaShop dynamic sell prices.
- Added `plugins/SellGUI/addons/` addon loading so SellGUI can load addon jars from its own data folder.

### Fixed
- Fixed sound lookup on Youer/Paper-NeoForge hybrid servers by avoiding direct `Sound.valueOf` calls.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.1.jar`

## 3.0.5 - 2026-05-14

### Changed
- Updated plugin and Maven version to `3.0.5`.
- Worth lore packet display now respects the configured `prices.calculation-method` instead of forcing extra Essentials or ShopGUI+ fallbacks after the main price lookup.
- Worth lore packet lines are now sent with `italic: false` so tooltip formatting stays clean.

### Fixed
- Fixed worth lore still appearing for MMOItems or Nexo items without a SellGUI price when `prices.calculation-method: "config"` was intended to suppress that fallback.
- Fixed worth lore lines being rendered italic in packet-based tooltip display.
- Fixed synthetic Nexo slot refresh after inventory clicks, which could interfere with Nexo 1.21.2+ component-based attribute tooltip display.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.0.5.jar`

## 3.0.4 - 2026-05-13

### Changed
- Updated plugin and Maven version to `3.0.4`.
- Stack normalization now skips Nexo items so SellGUI does not rewrite custom Nexo metadata during normalization passes.

### Fixed
- Fixed Nexo items being normalized through Bukkit item meta during stack cleanup events such as join, quit, smelt result cleanup, and plugin shutdown cleanup.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.0.4.jar`

## 3.0.3 - 2026-05-13

### Changed
- Updated plugin and Maven version to `3.0.3`.
- Worth lore packet handling now updates only the packet lore component instead of rebuilding Bukkit item meta.

### Fixed
- Fixed Nexo custom attribute displays resetting to default/vanilla formatting when worth lore was shown.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.0.3.jar`

## 3.0.2 - 2026-05-11

### Added
- Added `item-model`, `hide-tool-tip`, and `tooltip-style` support for `custommenuitems.yml` entries.
- Added example comments for modern item component fields in `custommenuitems.yml`.

### Changed
- Updated plugin and Maven version to `3.0.2`.

### Verified
- `git diff --check`
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.0.2.jar`

## 3.0.1 - 2026-05-10

### Added
- Added `menus:` support documentation and examples in `custommenuitems.yml` so custom menu buttons can be limited to specific sell menus.
- Added `worth-lore-whitelist-gui` and `worth-lore-whitelist-gui-titles` to `config.yml`.
- Added worth-lore GUI title normalization for plain titles, legacy hex colors, and MiniMessage-style hex color tags.

### Changed
- Updated plugin and Maven version to `3.0.1`.
- Worth lore can now run in blacklist mode as before, or whitelist mode when `worth-lore-whitelist-gui` is enabled.
- Price evaluation now stores an explicit `evaluated` marker alongside `current_price`.
- Stacking normalization now preserves evaluated item lore and `current_price` instead of treating them as temporary data.

### Fixed
- Fixed sell button lore replacing lines such as `Click to review the total.` and causing duplicate `Total Value` lines.
- Fixed evaluated/random-price items losing their evaluated lore during stacking normalization.
- Fixed evaluated/random-price items losing sellability when `current_price` was removed during normalization.
- Fixed evaluated item detection so `current_price` is recognized as an evaluated state.

### Verified
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.0.1.jar`

## 3.0.0 - 2026-05-09

### Added
- Added multi-menu sell GUI support through separate files in `gui/sell_menus/`.
- Added default sell menu: `gui/sell_menus/default.yml`.
- Added fishing-only sell menu example: `gui/sell_menus/fishing.yml`.
- Added `/sellgui <menu>` support so players can open a specific sell menu.
- Added `/sellgui <player> <menu>` support for opening a specific menu for another player.
- Added tab completion for available sell menu IDs.
- Added exclusive menu item rules so configured items can only be sold in their assigned menu.
- Added item stack normalization after furnace smelting, furnace extraction, player join, player quit, and plugin disable.
- Added `stacking` config section to control item stack normalization behavior.
- Added bundled `libs/shopgui-api-3.1.0.jar` so Maven builds work after cloning the repository.

### Changed
- Reworked SellGUI core logic around `SellMenuConfig` for cleaner multi-menu handling.
- Split the old root `gui.yml` into a `gui/` folder:
  - `gui/sell_menus/default.yml`
  - `gui/sell_menus/fishing.yml`
  - `gui/price_setter.yml`
  - `gui/price_evaluation.yml`
  - `gui/autosell_settings.yml`
- Updated config loading to merge all GUI YAML files from the `gui/` folder at runtime.
- Cleaned `config.yml` so core settings stay separate from GUI layout settings.
- Updated plugin version to `3.0.0`.
- Updated Maven configuration to use the bundled ShopGUI API jar.
- Updated sell GUI rendering and confirm flow to respect the active menu.
- Updated autosell settings GUI to fit the cleaned config structure.

### Fixed
- Fixed `/sellall` selling items that should be locked to an exclusive sell menu.
- Fixed autosell selling items that should be locked to an exclusive sell menu.
- Fixed intermittent item stacking problems caused by temporary SellGUI evaluation metadata and lore.
- Fixed evaluated item metadata causing vanilla items to stay split after smelting, join/leave, or server restart.
- Fixed GUI config generation so new installs create the new `gui/` folder structure instead of a root `gui.yml`.
- Fixed build portability by committing the ShopGUI API dependency used by `pom.xml`.

### Migration Notes
- `src/main/resources/gui.yml` has been removed.
- Server configs should now use the generated `plugins/SellGUI/gui/` folder.
- Each sell menu should live in its own YAML file under `plugins/SellGUI/gui/sell_menus/`.
- Items configured as exclusive to one menu will be skipped by other sell menus, `/sellall`, and autosell.

### Verified
- `git diff --check`
- `mvn -q -DskipTests package`
- Built jar: `target/SellGUI-3.0.0.jar`
