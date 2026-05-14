# SellGUI v3

<img width="800" height="200" alt="2028end" src="https://github.com/user-attachments/assets/14beab6b-c27b-4777-96fc-c91bbe6e82ae" />

SellGUI is a Minecraft selling plugin with a configurable GUI, `/sellall`, autosell, price setting tools, price evaluation, and support for custom item plugins. Version 3 focuses on cleaner configuration, multiple sell menus, menu-specific item rules, and safer item stacking behavior.

Current branch: `v3.0`

[WIKI](https://nguyensonhoa.github.io/SellGUIWiki/#home)

Plugin version: `3.0.5`

## What's New In v3

- Added multiple sell menus through the `gui/sell_menus/` folder.
- Each sell menu can live in its own YAML file, for example `default.yml` and `fishing.yml`.
- Added `/sellgui <menu>` to open a specific menu.
- Added `/sellgui <player> <menu>` so admins can open a specific menu for another player.
- Added per-menu item filters:
  - `allowed-items` allows only specific items.
  - `denied-items` blocks specific items.
  - `exclusive: true` locks listed items to that menu only.
- `/sellall` and autosell now skip items locked to exclusive menus.
- Split the old root `gui.yml` into a cleaner `gui/` folder.
- Added item stack normalization to fix items not stacking after smelting, joining, leaving, or server restart.
- Bundled `libs/shopgui-api-3.1.0.jar` so Maven builds work from a fresh clone.
- Added modern item component support for custom menu items: `item-model`, `hide-tool-tip`, and `tooltip-style`.

## Requirements

### Required

| Component | Notes |
| --- | --- |
| Java | Java 21 recommended for modern Paper/Spigot 1.20.6+ servers |
| Server | Paper/Spigot `1.20.6+`, optimized for `1.21+` |
| Vault | Required for economy support |
| Economy plugin | EssentialsX, CMI, or any Vault-compatible economy plugin |
| NBTAPI | Required dependency in `plugin.yml` |

### Optional

| Plugin | Purpose |
| --- | --- |
| EssentialsX | Use Essentials worth prices |
| ShopGUIPlus | Use ShopGUI+ sell prices |
| MMOItems | Detect and price MMOItems |
| Nexo | Detect and price Nexo items |
| MythicLib | Extra item/NBT metadata support |
| PlaceholderAPI | Registers `%sellgui_*%` placeholders |
| PacketEvents | Displays item worth lore through packets when enabled |

## Installation

1. Download the SellGUI v3 jar.
2. Place the jar in your server `plugins/` folder.
3. Make sure Vault, NBTAPI, and an economy plugin are installed.
4. Restart the server so SellGUI can generate its files.
5. Edit files inside `plugins/SellGUI/`.
6. Run `/sellgui reload` or restart the server after editing configuration files.

## Build From Source

```bash
git clone https://github.com/NguyenSonhoa/SellGUI.git
cd SellGUI
git checkout v3.0
mvn -q -DskipTests package
```

Jar output:

```text
target/SellGUI-3.0.5.jar
```

`libs/shopgui-api-3.1.0.jar` is committed so Maven can build immediately after cloning.

## Commands

Aliases for `/sellgui`: `/sg`, `/sell`

| Command | Description | Permission |
| --- | --- | --- |
| `/sellgui` | Open the default sell menu | `sellgui.use` |
| `/sellgui <menu>` | Open a specific sell menu, for example `/sellgui fishing` | `sellgui.use` and the menu permission |
| `/sellgui <player>` | Open the default menu for another player | `sellgui.others` |
| `/sellgui <player> <menu>` | Open a specific menu for another player | `sellgui.others` |
| `/sellgui help` | Show in-game help | `sellgui.use` |
| `/sellgui reload` | Reload configs and GUIs | `sellgui.reload` |
| `/sellgui evaluate` | Open the Price Evaluation GUI | `sellgui.evaluate` |
| `/sellgui autosell` | Open the Autosell Settings GUI | `sellgui.autosell` |
| `/autosell` | Open the Autosell Settings GUI | `sellgui.autosell` |
| `/sellgui setprice <amount>` | Set a fixed price for the item in hand | `sellgui.setprice` |
| `/sellgui setrange <min> <max>` | Set a random price range for the item in hand | `sellgui.setrange` |
| `/sellguiprice` | Open the Price Setter GUI | `sellgui.setprice` |
| `/sellguiprice <price>` | Enter a price for the item in the open Price Setter GUI | `sellgui.setprice` |
| `/sellall` | Preview selling all valid inventory items | `sellgui.sellall` |
| `/sellall confirm` | Confirm `/sellall` | `sellgui.sellall` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `sellgui.*` | plugin manager/op | Access to all declared SellGUI permissions |
| `sellgui.use` | true | Use the basic SellGUI command |
| `sellgui.admin` | op | General admin permission |
| `sellgui.others` | manual grant | Open SellGUI for other players |
| `sellgui.reload` | op | Reload the plugin |
| `sellgui.setprice` | op | Use the Price Setter and fixed price commands |
| `sellgui.setrange` | op | Set random price ranges |
| `sellgui.evaluate` | op | Use the Price Evaluation GUI |
| `sellgui.sellall` | true | Use `/sellall` |
| `sellgui.autosell` | true | Open the autosell GUI |
| `sellgui.menu.*` | true | Access all configured menus declared in `plugin.yml` |
| `sellgui.menu.default` | true | Open the default menu |
| `sellgui.menu.fishing` | true | Open the example fishing menu |
| `sellgui.bonus.<number>` | false | Adds a sell bonus, depending on the pricing path |
| `sellgui.multiplier.<number>` | false | Multiplies sell prices |

Examples:

```text
sellgui.multiplier.2.0
sellgui.bonus.30
sellgui.menu.fishing
```

When adding a new menu, give it its own permission in the menu file, for example `sellgui.menu.mining`, then grant that permission to the ranks that should use it.

## v3 Configuration Layout

After the first server start, SellGUI creates files under `plugins/SellGUI/`.

```text
plugins/SellGUI/
  config.yml
  itemprices.yml
  mmoitems.yml
  nexo.yml
  customitems.yml
  custommenuitems.yml
  random-prices.yml
  messages.yml
  sounds.yml
  autosell_data.yml
  gui/
    sell_menus/
      default.yml
      fishing.yml
    price_setter.yml
    price_evaluation.yml
    autosell_settings.yml
```

The old root `gui.yml` has been removed in v3. GUI layouts and sell menus now live in the `gui/` folder.

SellGUI loads and merges all `.yml` and `.yaml` files inside `gui/`, including files in subfolders. The recommended layout is one sell menu per file under `gui/sell_menus/`.

## Multi-Menu System

Default menu:

```text
plugins/SellGUI/gui/sell_menus/default.yml
```

Example fishing menu:

```text
plugins/SellGUI/gui/sell_menus/fishing.yml
```

Example `mining` menu:

```yml
sell_menus:
  mining:
    name: "Mining"
    permission: "sellgui.menu.mining"
    title: "&8/sellgui > Mining"
    size: 36
    item-filter:
      allowed-items:
        - "IRON_INGOT"
        - "GOLD_INGOT"
        - "DIAMOND"
        - "EMERALD"
      denied-items: []
      exclusive: true
    positions:
      sell_button: [31]
      confirm_button: [31]
      filler_slots: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 28, 29, 30, 32, 33, 34, 35]
      item_slots: [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25]
    items:
      sell_button:
        material: "EMERALD"
        custom-model-data: 0
        name: "&a&lCalculate Mining Sale"
        lore:
          - "&7Only mining items can be sold here."
          - "&eTotal Value: &a$0.00"
        glow: true
      confirm_button:
        material: "GREEN_CONCRETE"
        custom-model-data: 0
        name: "&a&lConfirm Mining Sale"
        lore:
          - "&fTotal: &e$%total%"
        glow: true
      filler:
        material: "GRAY_STAINED_GLASS_PANE"
        custom-model-data: 0
        name: " "
        lore: []
      no_items:
        material: "BARRIER"
        custom-model-data: 0
        name: "&c&lNo Items To Sell"
        lore:
          - "&7Add mining items to this menu."
    item_total_format: "&7%amount%x &f%item% &8= &e$%total%"
    evaluation_required_format: "&7%amount%x &f%item% &cNeeds Evaluation"
```

Open it with:

```text
/sellgui mining
```

## Item Filters And Exclusive Items

Each menu can define:

```yml
item-filter:
  allowed-items: []
  denied-items: []
  exclusive: false
```

| Key | Description |
| --- | --- |
| `allowed-items` | If empty, the menu accepts any valid item except denied items and items locked to another exclusive menu. If populated, the menu only accepts listed items. |
| `denied-items` | Blocks specific items in that menu. |
| `exclusive` | When `true`, listed allowed items can only be sold in this menu. |

With `exclusive: true`, those items are:

- Blocked from other sell menus.
- Skipped by `/sellall`.
- Skipped by autosell.

## Item Identifier Format

Vanilla items can be written by material name:

```yml
allowed-items:
  - "COD"
  - "SALMON"
```

Full identifiers are also supported:

```yml
allowed-items:
  - "VANILLA:COD"
  - "MMOITEMS:SWORD.EXCALIBUR"
  - "NEXO:CUSTOM_FISH"
```

| Item type | Format |
| --- | --- |
| Vanilla | `VANILLA:MATERIAL` or just `MATERIAL` |
| MMOItems | `MMOITEMS:TYPE.ID` |
| Nexo | `NEXO:ITEM_ID` |

## Price System

SellGUI can read prices from multiple sources:

- `itemprices.yml` for vanilla items.
- `mmoitems.yml` for MMOItems.
- `nexo.yml` for Nexo items.
- Essentials worth when `use-essentials-price: true`.
- ShopGUI+ when `use-shopguiplus-price: true`.
- NBT/PDC prices when `prices.nbt-pricing: true`.
- Random/evaluation prices from `random-prices.yml`.

Main price config:

```yml
prices:
  nbt-pricing: true
  calculation-method: "auto"
  default-price: 0.0
  multipliers:
    enabled: true
    permission-based: true
    default-multiplier: 1.0
    max-multiplier: 5.0
  random-pricing:
    enabled: false
    variation-percent: 10.0
```

`prices.calculation-method` values:

| Value | Description |
| --- | --- |
| `auto` | Automatically choose the best available price source |
| `config` | Use SellGUI config prices only |
| `essentials` | Use Essentials worth only |
| `nbt` | Use NBT price only |
| `shopguiplus` | Use ShopGUI+ only |

## Price Setter

Open the GUI:

```text
/sellguiprice
```

Workflow:

1. Place an item in the center slot.
2. Use the GUI or chat input to enter a price.
3. Click save to write the price.

Quick command:

```text
/sellgui setprice 10
```

In most price-setting flows, price `0` can be used to remove the saved price.

## Price Evaluation

Open the GUI:

```text
/sellgui evaluate
```

Use this when items must be evaluated before they can be sold. Related files:

```text
plugins/SellGUI/gui/price_evaluation.yml
plugins/SellGUI/random-prices.yml
```

If `general.allow-player-evaluation-stack: false`, players cannot evaluate a whole stack at once.

## Autosell

Open the GUI:

```text
/autosell
/sellgui autosell
```

Autosell lets players toggle automatic selling for priced items. Player data is stored in:

```text
plugins/SellGUI/autosell_data.yml
```

In v3, autosell skips items locked to exclusive sell menus so category-specific items are not sold accidentally.

## SellAll

Commands:

```text
/sellall
/sellall confirm
```

`/sellall` calculates a preview. The player then confirms with `/sellall confirm`.

In v3, `/sellall` skips:

- Items with no price.
- Enchanted items when `sell-all-command-sell-enchanted: false`.
- Items that require evaluation but have not been evaluated.
- Items locked to an exclusive sell menu.

## Item Stack Normalization

V3 adds stack normalization to fix vanilla items that stop stacking because of temporary SellGUI metadata or lore.

Config:

```yml
stacking:
  enabled: true
  normalize-on-join: true
  normalize-on-quit: true
  normalize-on-plugin-disable: true
  normalize-smelt-results: true
  normalize-after-furnace-extract: true
```

This removes temporary SellGUI metadata such as `current_price`, `evaluated`, and `Evaluated: ...` lore when appropriate, then merges similar stacks in the player's inventory.

It does not intentionally remove custom NBT or metadata from other plugins, so custom items with real metadata stay intact.

## PlaceholderAPI

When PlaceholderAPI is installed, SellGUI registers the `sellgui` identifier and processes PlaceholderAPI placeholders in supported GUI text.

### SellGUI PlaceholderAPI Placeholders

| Placeholder | Description |
| --- | --- |
| `%sellgui_pricehand%` | Price of the item in the player's main hand |
| `%sellgui_pricehandfull%` | Item name and price of the item in hand |

### Built-In Placeholders

These placeholders can be used in SellGUI text/config values. If PlaceholderAPI is installed, SellGUI lets PlaceholderAPI process placeholders first. If PlaceholderAPI is missing or fails, SellGUI falls back to these built-in placeholders.

| Placeholder | Description |
| --- | --- |
| `%player%` | Player name |
| `%player_name%` | Player name |
| `%player_displayname%` | Player display name |
| `%player_uuid%` | Player UUID |
| `%player_world%` | Player world name |
| `%player_x%` | Player block X position |
| `%player_y%` | Player block Y position |
| `%player_z%` | Player block Z position |
| `%player_health%` | Current player health |
| `%player_max_health%` | Max player health |
| `%player_food%` | Player food level |
| `%player_level%` | Player XP level |
| `%player_exp%` | Player XP progress percent |
| `%vault_eco_balance%` | Player Vault economy balance |
| `%player_balance%` | Player Vault economy balance |
| `%server_name%` | Server name |
| `%server_version%` | Full server version |
| `%server_bukkit_version%` | Bukkit version |
| `%server_online%` | Online player count |
| `%server_max_players%` | Max player count |
| `%sellgui_version%` | SellGUI plugin version |
| `%sellgui_author%` | SellGUI plugin authors |
| `%time%` | Current server local time |
| `%date%` | Current server local date |
| `%timestamp%` | Current server local date and time |

## Worth Lore Through PacketEvents

Enable in `config.yml`:

```yml
general:
  add-worth-lore: true
```

PacketEvents is required. SellGUI displays worth lore through packets so the real inventory item is not constantly rewritten with lore.

GUI titles where worth lore should not be shown:

```yml
general:
  worth-lore-blacklist-gui-titles:
    - "ah"
    - "Auction House"
    - "My Chest"
```

## Migration From Older Versions

If you are upgrading from a pre-v3 build:

1. Back up `plugins/SellGUI/`.
2. Stop the server.
3. Install the v3 jar.
4. Start the server to generate the new `gui/` folder.
5. Move old GUI layout/menu settings from `gui.yml` into files under `gui/`.
6. Put each sell menu in its own file under `gui/sell_menus/<menu>.yml`.
7. Run `/sellgui reload`.

Important changes:

| Old | New |
| --- | --- |
| Root `gui.yml` | `gui/` folder |
| One sell GUI | Multiple sell menus under `gui/sell_menus/` |
| Items sold anywhere | Items can be locked to one menu with `exclusive: true` |

## Troubleshooting

### `/sellgui fishing` says the player has no permission

Make sure the player has the menu permission:

```text
sellgui.menu.fishing
```

### An item cannot be sold in the default menu

Check whether the item belongs to another exclusive menu. For example, fish in `fishing.yml` has `exclusive: true`, so fish can only be sold through `/sellgui fishing`.

### `/sellall` does not sell some items

Check:

- The item has a price in `itemprices.yml`, `mmoitems.yml`, `nexo.yml`, Essentials, or ShopGUI+.
- The item is enchanted while `sell-all-command-sell-enchanted: false`.
- The item requires evaluation.
- The item is locked to an exclusive menu.

### Maven build fails because of ShopGUI API

Make sure this file exists:

```text
libs/shopgui-api-3.1.0.jar
```

Then build again:

```bash
mvn -q -DskipTests package
```

## Important Files

| File | Description |
| --- | --- |
| `src/main/resources/config.yml` | Default core config |
| `src/main/resources/gui/sell_menus/default.yml` | Default sell menu |
| `src/main/resources/gui/sell_menus/fishing.yml` | Example fishing sell menu |
| `src/main/resources/gui/price_setter.yml` | Price Setter GUI layout |
| `src/main/resources/gui/price_evaluation.yml` | Price Evaluation GUI layout |
| `src/main/resources/gui/autosell_settings.yml` | Autosell Settings GUI layout |
| `src/main/resources/itemprices.yml` | Vanilla item prices |
| `src/main/resources/mmoitems.yml` | MMOItems prices |
| `src/main/resources/nexo.yml` | Nexo item prices |
| `src/main/resources/random-prices.yml` | Random/evaluation prices |
| `CHANGELOG.md` | v3 changelog |

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Support

When reporting issues, include:

- Server version.
- SellGUI version.
- Related economy/item plugins.
- Related config or menu files.
- Console error logs.
- Steps to reproduce the issue.
