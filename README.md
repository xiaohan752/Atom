# Atom
Not just a game.

*“Everything should be made as simple as possible, but not simpler.” — Albert Einstein*

> **Java Version:** 21  
> **Engine:** libGDX  
> **Build Tool:** Gradle

## 💡 Contents

- [Getting Started](#Getting-Started)
  - [Requirements](#Requirements)
  - [Installation](#Installation)
- [Highlights](#Highlights)
- [Project Structure](#Project-Structure)
- [Gameplay Overview](#Gameplay-Overview)
- [Controls](#Controls)
- [Console Commands](#Console-Commands)
- [Customizing Data Files](#Customizing-Data-Files)
- [Project Structure](#Project-Structure)
- [Trobleshooting](#Trobleshooting)
- [License](#License)
- [Credits](#Credits)
- [Contributing](#Contributing)

## 📚 Getting Started

### Requirements

- **JDK 21** (Temurin recommended)  
Verify your Java version:
```bash
java -version
```

### Installation

We recommend you to download the latest release and execute:  
**Windows:**
```bash
run.bat
```
**Linux/macOS:**
```bash
chmod +x run.sh
run.sh
```
You can also run the following command:
```bash
java -javaagent:Atom-<version>.jar -jar Atom-<version>.jar
```

## 👑 Highlights

This project renders the entire frame through Frame Buffer Objects (FBOs). Instead of drawing directly to the default backbuffer, the game renders the world and UI into off-screen textures first, then composes the final image in a final pass.

Why FBO-first?
- Deterministic composition: all layers can be ordered and blended reliably.
- Post-processing ready: screen-space effects are simple fullscreen passes.
- Stable GL3 core profile workflow: avoids relying on fixed pipeline behavior and keeps all rendering explicit.
- Multi-resolution support: render the 3D world at a different resolution than UI, or scale for performance.

## 🧩 Project Structure

### Key Folders

- `world/` voxel world, generation, lighting, circuits
- `render/` sky, precipitation, overlays
- `input/` player controller, movement, physics
- `data/` save/load (world config, player state, etc.)
- `mesh/` chunk meshing pipelines

### Save / Load World

> World save config is stored in world.json under the selected world directory.  
> Chunk storage uses a binary format with compression and integrity checks:  
>  - New format: *.bin.z 
>  - Legacy format: *.bin.gz

## 🎉 Gameplay Overview

**Atom** is a sandbox voxel game focused on exploration, building, and full-world interaction.

### Core Loop
- **Spawn into a world** generated from a unique seed.
- **Explore** diverse terrain and environments.
- **Build structures** and shape the world freely.
- **Experiment** with movement, physics, lighting, circuit system, and world simulation.

### World & Building
- **Voxel world** made of blocks that you can place and remove.
- **Procedural generation** driven by a world seed.
- **Save system** keeps your world state.

### Movement & Camera
- **First-person camera** with mouse look.
- **Ground movement** with jumping and gravity.
- **Fly mode** for fast travel and creative building.

### Environment Systems
- **Day/Night cycle** affecting atmosphere and visuals.
- **Weather system** influencing the in-game sky and ambience.

### Circuit System

Atom includes a block-based circuit system for building logic and automation.

- **Customizable colors:** circuits come in four colors, and **signals do not interfere** across colors. See below for customization details.
- **Propagation model:** signal updates use a **depth-first search** traversal.
- **Wire range:** wire signals propagate with **no distance limit**.
- **NOT gate behavior:** the **bottom face is the input**, and the **other five faces are outputs**.

### Debug & Tools
- Built-in **debug overlay** to inspect runtime values.
- In-game **console** for quick commands and testing.

> Notes:
> - Features may change rapidly during development.
> - Some systems may require an internet connection to fetch data.
> - Some blocks use **slope-shaped mesh**. Slopes are defined by the block’s **collision volume**, meaning the walkable/solid shape is determined by its collision geometry rather than the visual mesh alone.

## ⌨️ Controls

> Default keybinds. Some bindings may change during development.

### Mouse
- **Mouse Move** — Look around
- **Left Click** — Primary action
- **Right Click** — Secondary action
- **Middle Click** - Pick the block from your crosshair
- **Mouse Wheel** — Cycle hotbar / selected item

### Movement
- **W / A / S / D** — Move forward / left / backward / right
- **Space** — Jump
- **Ctrl (Hold)** — Sprint

### Fly Mode
- **F** — Toggle Fly Mode
- **W / A / S / D** — Fly forward / left / backward / right
- **Space** — Fly up
- **Shift (Hold)** — Fly down
- **Ctrl (Hold)** — Fly Faster

### UI / Debug
- **Esc** — Pause / Release mouse cursor
- **SLASH** — Toggle Console
- **F3** — Toggle Debug Overlay

## 🎮 Console Commands

- Place a block at a specific xyz or at your crosshair:
  - `setblock <x> <y> <z> <blockName>`
  - `setblock aim <blockName>`
- Pick the block you want to place with elastic search:
  - `pickblock <blockName>`
- Change the weather:
  - `weather clear|overcast|rain|snow|thunder`

> Note: More features coming soon.

## 🎨 Customizing Data Files

Atom stores gameplay data and configuration as JSON. These files are located in your game root/save directory. All the contents below are customizable and gives you the full freedom to build your world. 

### `config.json`

**Purpose:** Global gameplay settings.

**Example:**
```json
{
  "worldName": "World",
  "reach": 5.0,
  "maxProportion": 0.8
}
```

**Fields:**
- worldName (*string*) — Default world folder/name to load or create.
- reach (*float*) — Player interaction reach distance.
- maxProportion (*float*) — Determine the max lines of console display.
*(Keep between 0.0 and 1.0 unless you know what you’re doing.)*

### `blocks.json`

**Purpose:** Defines all block types: IDs, names, rendering rules, collision rules, and textures.

**Structure:**
- Root contains "blocks": `[ ... ]`
- Each entry defines a single block.

**Example:**
```json
{
  "blocks": [
    {
      "id": 0,
      "name": "air",
      "opaque": false,
      "solid": false,
      "shape": "air",
      "renderLayer": "none",
      "tiles": { "top": 0, "side": 0, "bottom": 0 },
      "texture": { "color": "#00000000", "jitter": false }
    }
  ]
}
```

**Fields:**
- id (*int*) — Unique numeric block ID with a maximum of 255. Must not collide with other blocks.
- name (*string*) — Unique block name.
- opaque (*bool*) — Whether the block blocks light.
- solid (*bool*) — Whether the block is collidable for movement.
- shape (*string*) — Mesh shape preset.
- renderLayer (*string*) — Rendering category.
- tiles (*object*) — Texture atlas indices for faces:
  - top, side, bottom (*int*)
- texture (*object*) — Visual modifiers:
  - color (*string*) — Hex RGBA like #RRGGBBAA
  - jitter (*bool*) — Whether texture slight variation is applied

> Notes:
> Changing block IDs in an existing world can corrupt saves or remap blocks unexpectedly.
> When adding new blocks, append new IDs rather than renumbering old ones.

### `player.json`

**Purpose:** Stores the player camera/body state for a world.

**Example:**
```json
{
  "x": 85.52673,
  "y": 87.621,
  "z": -120.51774,
  "yawDeg": 336.90042,
  "pitchDeg": 35.7631,
  "flyMode": true
}
```

**Fields:**
- x, y, z (*float*) — Player position.
- yawDeg (*float*) — Horizontal look angle in degrees.
- pitchDeg (*float*) — Vertical look angle in degrees.
- flyMode (*bool*) — Whether fly mode is enabled.

### `world.json`

**Purpose:** Stores world-creation metadata and locks important settings for that save.

**Example:**
```json
{
  "seed": 6385075794901389989,
  "worldMode": "normal",
  "renderDistance": 8
}
```

**Fields:**
- seed (*long*) — World generation seed.
- worldMode (*string*) — World generation mode, including:
  - normal
  - flat
  - single
- renderDistance (*int*) — Render distance in chunks.

## 🎯 Trobleshooting

**macOS: app won’t run / permissions**

If macOS blocks the app:
- Right-click the app → Open
- Or allow it in System Settings → Privacy & Security

**“Missing classes” when running JAR**

We primarily targets fat JAR and Construo packaging.
If you experiment with class issues, ensure your JAR is downloaded from the latest release. 

## License

**All rights reserved.**  
Copyright © AtomLife Studio.

See:
- `COPYRIGHT`
- `LICENSE`
- `NOTICES`

## Credits

Built with:
- libGDX + LWJGL3
- Additional open-source libraries listed in `NOTICES`

## Contributing

This repository is currently NOT accepting public contributions.  

If you found a bug or have a feature request, please open an issue with:
- Steps to reproduce if applicable
- Expected vs actual behavior
- Screenshots if possible
- Your OS + GPU + Java version
