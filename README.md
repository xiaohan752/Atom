# Atom
Not just a game.

*“Everything should be made as simple as possible, but not simpler.” — Albert Einstein*

> **Java Version:** 21  
> **Engine:** libGDX  
> **Build Tool:** Gradle

## Contents

 - [Getting Started](#GettingStarted)
   - [Requirements](#Requirements)
   - [Installation](#Installation)
 - [Gameplay Overview](#GameplayOverview)
 - [Controls](#Controls)
 - [Console Commands](#ConsoleCommands)
 - [Project Structure](#ProjectStructure)
 - [Trobleshooting](#Trobleshooting)
 - [License](#License)
 - [Credits](#Credits)
 - [Contributing](#Contributing)

## Getting Started

### Requirements

- **JDK 21** (Temurin recommended)  
Verify your Java version:
```bash
java -version
```

### Installation

Simply download the latest release and enjoy :)

## Project Structure

### Key Folders

- `world/` voxel world, generation, lighting, circuits
- `render/` sky, precipitation, overlays
- `input/` player controller, movement, physics
- `data/` save/load (world config, player state, etc.)
- `mesh/` chunk meshing pipelines

### Save / Load Notes

> World save config is stored in world.json under the selected world directory.  
> Chunk storage uses a binary format with compression and integrity checks:  
>  - New format: *.bin.z 
>  - Legacy format: *.bin.gz

## Gameplay Overview

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

### Debug & Tools
- Built-in **debug overlay** to inspect runtime values.
- In-game **console** for quick commands and testing.

> Notes:
> - Features and balancing may change rapidly during development.
> - Some systems may require an internet connection to fetch data.

## Controls

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
- **` (Backquote)** — Toggle Console
- **F3** — Toggle Debug Overlay

## Console Commands

- Place a block at a specific xyz or at your crosshair:
  - `setblock <x> <y> <z> <blockName>`
  - `setblock aim <blockName>`
- Pick the block you want to place with elastic search:
  - `pickblock <blockName>`
- Change the weather:
  - `weather clear|overcast|rain|snow|thunder`

> Note: More features comming soon. 

## Trobleshooting

**macOS: app won’t run / permissions**

If macOS blocks the app:
 - Right-click the app → Open
 - Or allow it in System Settings → Privacy & Security

**“Missing classes” when running a thin JAR**

We primarily targets fat JAR and Construo packaging.
If you experiment with thin distributions, ensure your Jar is downloaded from the latest release. 

## License

**All rights reserved.**  
Copyright © AtomLife Studio.

See:
 - ```COPYRIGHT```
 - ```LICENSE```
 - ```NOTICES```

## Credits

Built with:
 - libGDX + LWJGL3
 - Additional open-source libraries listed in ```NOTICES```

## Contributing

This repository is currently NOT accepting public contributions.  

If you found a bug or have a feature request, please open an issue with:
- Steps to reproduce if applicable
- Expected vs actual behavior
- Screenshots if possible
- Your OS + GPU + Java version
