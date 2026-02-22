# BBTSkin - Custom Skin Manager for BeforeBedtime Network

A high-performance custom skin manager mod for Minecraft 1.20.1 (Fabric), featuring high-resolution skin support, local presets, and Cloudflare Worker KV synchronization.

## Features

### 🎨 Skin Selection Screen
- Modern glass-morphism UI design with dark purple-blue theme
- Searchable skin list with thumbnails
- 3D interactive player model preview with mouse rotation
- Import/Delete/Reset/Apply actions
- Status messages with fade animations

### 📥 Skin Import
- Native file browser using TinyFileDialogs
- Drag & drop support
- Real-time preview while importing
- Supports standard (64x64) and high-resolution skins (up to 8192x8192)
- Automatic skin name extraction from filename
- Slim model (Alex) support

### 🔄 Multiplayer Synchronization
- Server-side skin storage for online players
- Automatic skin sync when joining servers
- Other players can see your custom skin
- Cloudflare Worker KV integration for persistent storage

### 📁 Local Storage
- Skins stored in `.minecraft/bbtskin/` folder
- Per-player preset folders
- JSON index for skin metadata
- Automatic applied skin restoration

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.20.1
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download `bbtskin-1.0.0.jar` and place in your `mods` folder
4. Launch Minecraft

## Usage

### Opening the Skin Manager
- Press **K** (default keybind) to open the skin selection screen
- Can be changed in Options > Controls > BBTSkin

### Importing a Skin
1. Click **Import** button
2. Click **Browse Files** or drag & drop a PNG image
3. Enter a name for the skin
4. Check "Slim Model (Alex)" if your skin uses the slim arm model
5. Click **Import** to save

### Applying a Skin
1. Select a skin from the list
2. Preview it in the 3D model viewer
3. Click **Apply** to use the skin

### Resetting Your Skin
- Click **Reset** to return to your Mojang account skin

## Building from Source

```bash
# Clone the repository
git clone https://github.com/zlipfatui-ui/bbtskin
cd bbtskin

# Build with Gradle
./gradlew build

# The JAR will be in build/libs/
```

## Project Structure

```
bbtskin-mod/
├── src/main/java/com/bbt/skin/
│   ├── BBTSkin.java                    # Main mod class
│   ├── client/
│   │   ├── BBTSkinClient.java          # Client initialization
│   │   ├── gui/
│   │   │   ├── screen/
│   │   │   │   ├── SkinSelectionScreen.java
│   │   │   │   └── SkinImportScreen.java
│   │   │   ├── widget/
│   │   │   │   ├── BBTButton.java
│   │   │   │   ├── BBTCheckbox.java
│   │   │   │   ├── PlayerModelWidget.java
│   │   │   │   └── SkinListWidget.java
│   │   │   └── input/
│   │   │       └── DragDropHandler.java
│   │   ├── network/
│   │   │   └── ClientSkinHandler.java
│   │   └── render/
│   │       └── SkinTextureManager.java
│   ├── common/
│   │   ├── config/
│   │   │   └── BBTSkinConfig.java
│   │   ├── data/
│   │   │   ├── SkinData.java
│   │   │   └── SkinManager.java
│   │   └── network/
│   │       └── NetworkConstants.java
│   ├── server/
│   │   └── network/
│   │       └── ServerSkinHandler.java
│   └── mixin/
│       └── AbstractClientPlayerEntityMixin.java
└── src/main/resources/
    ├── fabric.mod.json
    ├── bbtskin.mixins.json
    ├── bbtskin.accesswidener
    ├── pack.mcmeta
    └── assets/bbtskin/
        └── lang/
            └── en_us.json
```

## Configuration

Configuration file: `.minecraft/config/bbtskin.json`

```json
{
  "cloudflareWorkerUrl": "https://beforebedtime.net/api/skins",
  "enableCloudflareSync": true,
  "enableHighResSkins": true,
  "maxSkinResolution": 8192,
  "skinCacheSize": 100,
  "enableAutoSync": true,
  "syncIntervalSeconds": 300,
  "showSkinLoadingIndicator": true,
  "enableDebugLogging": false
}
```

## Cloudflare Worker Integration

BBTSkin integrates with Cloudflare Workers KV for persistent cloud storage. The expected Worker API endpoints:

- `POST /upload` - Upload skin data
- `GET /get/{playerUUID}` - Fetch player's skin
- `DELETE /delete/{playerUUID}` - Remove skin

## Dependencies

- Minecraft 1.20.1
- Fabric Loader ≥0.16.14
- Fabric API
- Java 17+

## Credits

- UI design influenced by modern glass-morphism trends
- BeforeBedtime Network community

## License

MIT License - See [LICENSE](LICENSE) for details

## Support

For issues and feature requests, please contact the BeforeBedtime team at support@beforebedtime.net
