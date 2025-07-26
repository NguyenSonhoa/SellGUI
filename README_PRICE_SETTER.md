# Price Setter Feature

## Overview
The Price Setter feature allows admins to set prices for in-game items through an intuitive GUI with drag & drop interface. Full support for:
- **Vanilla items** (Minecraft default items) - using Material names
- **MMOItems** (from MMOItems plugin) - using NBT tag `"MMOITEMS_ITEM_ID"`
- **Nexo items** (from Nexo plugin) - using NBT tag `"nexo:id"`

## How to Use

### 1. Open Price Setter GUI
```bash
/sellgui setprice
```
or
```bash
/sellguiprice
```

### 2. Setting Item Prices
1. **Drag & Drop** items into the center slot of the GUI
2. **View Information** about current item (type, identifier, current price)
3. **Set Price** using command: `/sellguiprice <price>`
4. **Save Price** by clicking the "Save" button (green)
5. **Delete Price** by clicking the "Delete" button (red)
6. **Cancel** by clicking the "Cancel" button or closing the GUI

### 3. Usage Examples
```bash
/sellguiprice 10.50    # Set price to $10.50
/sellguiprice 100      # Set price to $100.00
/sellguiprice 0        # Remove price (set to 0)
```

### 4. GUI Layout
```
┌─────────────────────────────────────────────┐
│  [Info]           [?]                       │
│                                             │
│           [Drag Item Here]                  │
│                                             │
│            [Price Info]                     │
│                                             │
│  [Delete]   [Save]   [Cancel]               │
└─────────────────────────────────────────────┘
```

## Permissions
- `sellgui.setprice` - Allows using the price setter feature (default: op)

## Config File Structure

### itemprices.yml (Vanilla Items)
```yaml
# Vanilla Minecraft items - sử dụng Material names (UPPERCASE)
DIAMOND: 100.0
IRON_INGOT: 5.0
GOLD_INGOT: 10.0
NETHERITE_INGOT: 500.0
EMERALD: 50.0

# Enchantment bonuses (optional)
flat-enchantment-bonus: []
multiplier-enchantment-bonus: []
```

### mmoitems.yml (MMOItems)
```yaml
mmoitems:
  SWORD:                    # Item Type (UPPERCASE)
    EXCALIBUR: 1000.0      # Item ID: Price
    STEEL_SWORD: 250.0
    FIRE_BLADE: 750.0
  MATERIAL:
    MAGIC_DUST: 50.0
    REFINED_ORE: 25.0
  AXE:
    BATTLE_AXE: 300.0
```

### nexo.yml (Nexo Items)
```yaml
nexo:
  # Nexo item IDs - preserve original case (usually lowercase)
  custom_sword: 500.0
  magic_wand: 300.0
  rare_helmet: 750.0
  fire_staff: 1200.0
  ice_boots: 400.0
```

## Technical Features

### ItemIdentifier
- **Automatic Detection** of item types (Vanilla/MMOItems/Nexo)
- **Unique Identifier Generation** for each item:
  - Vanilla: `"VANILLA:MATERIAL_NAME"`
  - MMOItems: `"MMOITEMS:TYPE.ID"`
  - Nexo: `"NEXO:item_id"`
- **NBT Detection:**
  - MMOItems: NBT tag `"MMOITEMS_ITEM_ID"`
  - Nexo: NBT tag `"nexo:id"`
- **Case Sensitivity:**
  - Vanilla & MMOItems: UPPERCASE
  - Nexo: Preserve original case
- **Safe Error Handling** with try-catch blocks

### PriceManager
- **Multi-format Support:** Manages 3 types of config files
- **Atomic Operations:** Safe save/load with error handling
- **Cache Integration:** Integrates with MMOItems price cache
- **Validation:** Validates price values (>= 0)

### GUI Features
- **Drag & Drop Interface** with slot validation
- **Real-time Price Display** and item information
- **Visual Feedback** for all actions
- **Auto-return Items** when closing GUI
- **Permission-based Access** control
- **Multi-language Support** via lang.yml

## Integration with Existing System
- **100% Compatible** with current SellGUI
- **No Impact** on existing sell/sellall features
- **Shared Systems** for permissions and configs
- **Integration** with existing price systems:
  - Essentials pricing (if enabled)
  - Permission-based bonuses/multipliers
  - Enchantment pricing bonuses
- **PlaceholderAPI Support** (if available)
- **Backward Compatible** with existing configs

## Troubleshooting

### Common Issues
1. **"No permission"** - Check permission `sellgui.setprice`
2. **"Failed to save price"** - Check file write permissions for config
3. **"Item not recognized"** - Ensure MMOItems/Nexo plugins are loaded

### Debug
- Check console logs for detailed error messages
- Use `/sellgui reload` after editing configs
- Ensure config files have proper YAML format

## Commands Summary
| Command | Description | Permission |
|---------|-------------|------------|
| `/sellgui setprice` | Open price setter GUI | `sellgui.setprice` |
| `/sellguiprice` | Open price setter GUI | `sellgui.setprice` |
| `/sellguiprice <price>` | Set price for item in GUI | `sellgui.setprice` |
