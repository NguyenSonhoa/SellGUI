![sellguilogo](https://github.com/user-attachments/assets/8500df19-aed9-48f3-a7b2-edafbe0c0a99)

A powerful sell GUI plugin with advanced price management system

**Requirements:** Vault & Spigot 1.20+
**Optional:** MMOItems, Nexo, EssentialsX, PlaceholderAPI, ShopGUI+

![slogo](https://github.com/user-attachments/assets/30afe54c-36f9-40df-ab9d-6c804ca5b27f)

## ✨ Key Features
- **🎯 Drag & Drop GUI** - Intuitive selling interface
- **💰 Advanced Price Setter** - Set prices for any item type with GUI
- **🔧 Multi-Plugin Support** - Vanilla, MMOItems, Nexo, ShopGUI+ items
- **⚙️ EssentialsX Integration** - Use existing Essentials prices
- **🎨 Fully Customizable** - Custom model data, commands, messages, GUIs
- **📊 Permission-based Bonuses** - Multipliers and flat bonuses
- **🎲 Price Evaluation System** - Set random or fixed item prices for evaluation
- **🔒 Secure & Safe** - No item duplication, automatic backups
- **🤖 AutoSell System** - Automatically sell items based on whitelist configuration
- **💾 Persistent Pricing** - NBT-based pricing system with support for random ranges

## 🚀 What's New in This Version? 

### 🆕 **NEW:  Price Setter System**
- **Drag & Drop Price Setting** - Visual GUI for setting item prices
- **Multi-Item Support** - Vanilla, MMOItems, Nexo, and ShopGUI+ items
- **Real-time Price Display** - See current prices and item info
- **Commands:** `/sellgui setprice` or `/sellguiprice`

### 🆕 **AutoSell Feature**
- **Smart AutoSell** - Automatically sell whitelisted items
- **Per-Item Configuration** - Enable/disable autosell per item
- **Settings GUI** - Configure autosell preferences with `/sellgui autosell`

### 🆕 **Price Evaluation System**
- **Random Price Range** - Set MIN-MAX prices for item evaluation
- **Fixed Price Mode** - Set static prices for items
- **Player-Friendly** - Players can evaluate items before selling

### 🔧 **Improvements & Fixes**
- ✅ Fixed API errors and console sender issues
- ✅ Changed from Gradle to Maven for easier building
- ✅ Added Custom Model Data support for menu items
- ✅ Added command execution on sell & confirm actions
- ✅ Improved notification system for empty GUI
- ✅ Cross-compatibility:  Essentials + MMOItems + Nexo + ShopGUI+
- ✅ Enhanced NBT-based price management system

## 📋 Commands

### Main Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/sellgui` | Open the sell GUI | `sellgui. use` |
| `/sellgui <player>` | Open sell GUI for another player | `sellgui.others` |
| `/sellgui reload` | Reload plugin configs | `sellgui.reload` |
| `/sellall` | Sell all items in inventory | `sellgui.sellall` |
| `/sellall confirm` | Confirm selling all items | `sellgui.sellall` |
| `/sellgui evaluate` | Open the Price Evaluation GUI | `sellgui.evaluate` |
| `/sellgui autosell` | Open the AutoSell Settings GUI | `sellgui.autosell` |

### Price Setter Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/sellguiprice` | Open price setter GUI | `sellgui.setprice` |
| `/sellgui setprice <amount>` | Set fixed price for handheld item | `sellgui.setprice` |
| `/sellgui setrange <min> <max>` | Set random price range for handheld item | `sellgui.setrange` |

## 🔧 PlaceholderAPI Support
| Placeholder | Description |
|-------------|-------------|
| `%sellgui_pricehand%` | Price of item in player's hand |
| `%sellgui_pricehandfull%` | Full item name + price in hand |

## 🔐 Permissions

### Basic Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `sellgui.use` | Use `/sellgui` command | `true` |
| `sellgui.sellall` | Use `/sellall` command | `true` |
| `sellgui.evaluate` | Use price evaluation system | `op` |
| `sellgui.autosell` | Access AutoSell settings | `op` |
| `sellgui.others` | Open GUI for other players | `op` |
| `sellgui.reload` | Reload plugin configs | `op` |
| `sellgui.setprice` | Use price setter GUI and commands | `op` |
| `sellgui.setrange` | Set random price ranges | `op` |

### 💰 Price Bonuses
| Permission Format | Description | Example |
|-------------------|-------------|---------|
| `sellgui.multiplier. <number>` | Multiply sell price | `sellgui.multiplier.2` = 2x price |
| `sellgui.bonus.<number>` | Add flat bonus to price | `sellgui.bonus.30` = +$30 |

## 🎮 Supported Item Types

### ✅ Fully Supported
- **Vanilla Items** - All Minecraft items with Material names
- **MMOItems** - Custom items from MMOItems plugin
- **Nexo Items** - Custom items from Nexo plugin
- **EssentialsX Worth** - Use existing Essentials item prices
- **ShopGUI+** - Use ShopGUI+ prices

### 🔧 Configuration Files
- `config.yml` - Main plugin configuration
- `gui-config.yml` - Sell GUI, Price Evaluation, and AutoSell GUIs
- `itemprices.yml` - Vanilla item prices
- `mmoitems.yml` - MMOItems prices by TYPE. ID
- `nexo.yml` - Nexo item prices by item ID
- `autosell.yml` - AutoSell settings and whitelist
- `lang. yml` - All messages and text
- `sounds.yml` - Sound configurations

## 🎯 How to Use Price Setter

### Quick Start
1. **Open GUI:** `/sellguiprice`
2. **Drag item** into the center slot
3. **Set price:** Enter the chat
4. **Save:** Click the green "Save" button

### Supported Items
- **Vanilla:** `DIAMOND` → `itemprices.yml`
- **MMOItems:** `SWORD. EXCALIBUR` → `mmoitems.yml`
- **Nexo:** `custom_sword` → `nexo.yml`
- **ShopGUI+:** Auto-detected from ShopGUI+ prices

## 🎯 How to Use AutoSell

1. **Open Settings:** `/sellgui autosell`
2. **Select Items:** Click items to toggle autosell
3. **Navigation:** Use pagination buttons to browse items
4. **Bulk Actions:** Use "Enable All" / "Disable All" buttons
5. **Search:** Use search button to find specific items

## 📖 Documentation
- [Price Setter Guide](README_PRICE_SETTER.md) - Detailed guide for the new price setter
- [Nexo Integration](NEXO_INTEGRATION_GUIDE.md) - Specific guide for Nexo items
- [AutoSell Configuration](README_AUTOSELL. md) - AutoSell system setup guide

## 🔄 Installation
1. Download the latest release
2. Place in your `plugins/` folder
3. Install Vault and an economy plugin
4. Restart your server
5. Configure prices using `/sellgui setprice` or edit config files

## 🔄 What's Coming Next? 
- 🔄 Advanced Statistics & Analytics
- 🔄 Price History & Trends
- 🔄 Multi-Server Support
- 🔄 Custom Item Categories

## 🤝 Credits & Support
- **Original Plugin:** SellGUI
- **Enhanced Version:** Improved with advanced features, AutoSell, and Price Evaluation

### 🐛 Bug Reports & Feature Requests
- Create an issue on GitHub
- Include server version, plugin version, and error logs
- Provide steps to reproduce the issue

### 💡 Contributing
- Fork the repository
- Create a feature branch
- Submit a pull request with detailed description

---
**Made with ❤️ for the Minecraft community**
