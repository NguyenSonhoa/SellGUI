# Nexo Integration Guide

## NBT Tag Information
Nexo items use NBT tag `"nexo:id"` to store the item ID.

### Example NBT structure of Nexo item:
```json
{
  "nexo:id": "custom_sword",
  "other_tags": "..."
}
```

## How Price Setter detects Nexo items:

### 1. ItemIdentifier.getItemType()
```java
// Check for Nexo items - correct NBT tag is "nexo:id"
if (nbtItem.hasTag("nexo:id")) {
    return ItemType.NEXO;
}
```

### 2. ItemIdentifier.getItemIdentifier()
```java
case NEXO:
    // Nexo items use "nexo:id" NBT tag
    String nexoId = nbtItem.getString("nexo:id");
    if (nexoId != null && !nexoId.isEmpty()) {
        return "NEXO:" + nexoId; // Keep original case for Nexo IDs
    }
    break;
```

## Config file structure (nexo.yml):
```yaml
nexo:
  custom_sword: 500.0
  magic_wand: 300.0
  rare_helmet: 750.0
  special_pickaxe: 1000.0
```

## Testing Nexo Integration:

### 1. Create Nexo item with ID "test_item"
### 2. Use Price Setter GUI:
```
/sellguiprice
```
### 3. Drag Nexo item into GUI
### 4. Set price:
```
/sellguiprice 100.0
```
### 5. Check nexo.yml:
```yaml
nexo:
  test_item: 100.0
```

## Troubleshooting:

### If Nexo item is not detected:
1. Check item NBT tags using NBT viewer plugin
2. Ensure item has `"nexo:id"` tag
3. Check console logs for error messages
4. Verify that Nexo plugin is loaded properly

### Debug commands:
```
/sellgui reload  # Reload configs
```

### Log messages to look for:
- `[SellGUI] ItemIdentifier detected NEXO item: <id>`
- `[SellGUI] Failed to save Nexo item price: <error>`
- `[SellGUI] Successfully set price for NEXO:<id>`

## Compatibility Notes:
- Tested with Nexo plugin version 1.5.0+
- Requires MythicLib for NBT reading
- Compatible with all Minecraft versions that Nexo supports
