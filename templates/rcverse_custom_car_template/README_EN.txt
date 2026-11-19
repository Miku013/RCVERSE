================================================================================
  🏎️ RCVERSE - Custom RC Car Starter Template Kit (Minecraft 1.21.1)
================================================================================

This template kit lets you design and drive your own custom 3D RC cars in Minecraft 
using Blockbench without writing or recompiling any code!

--------------------------------------------------------------------------------
[Folder Structure]
--------------------------------------------------------------------------------
rcverse_custom_car_template/
  ├── pack.mcmeta                               <-- Resource pack descriptor (Format: 34)
  ├── README_JA.txt                             <-- Japanese Guide
  ├── README_EN.txt                             <-- This document (English)
  ├── config/
  │   └── microdrone/
  │       └── cars/
  │           └── sample_custom_car.json        <-- [Important] Vehicle physics & config JSON
  └── assets/
      └── microdrone/
          ├── geo/
          │   └── sample_custom_car.geo.json    <-- [Important] 3D GeckoLib model for Blockbench
          └── textures/
              └── entity/
                  └── sample_custom_car.png     <-- Vehicle UV texture (128x128)

--------------------------------------------------------------------------------
[Step 1: Edit in Blockbench]
--------------------------------------------------------------------------------
1. Download and open Blockbench (https://www.blockbench.net/).
2. Click "File" > "Open Model" and select:
   `assets/microdrone/geo/sample_custom_car.geo.json`
3. Check the Outliner (bone tree) on the top-right. Keep the required bone hierarchy:

   [Required Bones]
   - root             : Origin point
     └── chassis      : Chassis pivot
         ├── body     : Main car body (chassis bounce animation automatically synced)
         ├── Glass    : Translucent windows
         ├── light    : Emissive headlights & taillights
         ├── steer_fl : Front-left steering pivot (automatic yaw steering)
         │   └── wheel_fl : Front-left wheel (rolling animation)
         ├── steer_fr : Front-right steering pivot (automatic yaw steering)
         │   └── wheel_fr : Front-right wheel (rolling animation)
         ├── wheel_rl : Rear-left wheel (rolling animation)
         └── wheel_rr : Rear-right wheel (rolling animation)

4. Feel free to modify cubes, change dimensions, or add new details under `body`.
   Do NOT rename or change the hierarchy of `steer_fl`, `wheel_fl`, etc.
5. Paint your texture in Paint mode or edit the PNG in Photoshop/GIMP/Paint.NET.
6. Export as "GeckoLib Model" (`.geo.json`) when finished.

--------------------------------------------------------------------------------
[Step 2: Tune Physics Parameters (JSON)]
--------------------------------------------------------------------------------
Open `config/microdrone/cars/sample_custom_car.json` in any text editor:

{
  "id": "my_car",                                  <-- Unique ID (lowercase alphanumeric)
  "displayName": "My Custom Sport Car",            <-- In-game display name
  "model": "microdrone:geo/sample_custom_car.geo.json",  <-- Model file path
  "texture": "microdrone:textures/entity/sample_custom_car.png", <-- Texture file path
  "animation": "microdrone:animations/rc_car.animation.json",
  "properties": {
    "maxSpeedMultiplier": 1.2,    <-- Top speed multiplier (1.0 = stock, 1.5 = high speed)
    "accelMultiplier": 1.3,       <-- Acceleration multiplier
    "turnSpeedMultiplier": 1.1,   <-- Steering responsiveness
    "gripMultiplier": 1.1,        <-- Tire grip (higher = more grip, lower = slippery drift)
    "scale": 1.0,                 <-- Vehicle scale (0.8 = micro, 1.5 = large)
    "invertSteer": false,         <-- Reverse steering direction
    "rollMultiplier": 0.5,        <-- Body roll when cornering (0.0 = flat, 1.0 = realistic)
    "maxRollAngle": 35.0          <-- Maximum roll angle in degrees
  }
}

--------------------------------------------------------------------------------
[Step 3: Install into Minecraft]
--------------------------------------------------------------------------------
1. Copy the Config:
   Copy `sample_custom_car.json` into your `.minecraft/config/microdrone/cars/` folder.

2. Copy the Resource Pack:
   Place the entire `rcverse_custom_car_template` folder (or a zipped archive of it)
   into your `.minecraft/resourcepacks/` directory.

3. Enable the Resource Pack:
   In Minecraft, go to Options > Resource Packs, move your pack to the right column, and click Done.

--------------------------------------------------------------------------------
[Step 4: Spawn Your Custom Car In-Game]
--------------------------------------------------------------------------------
Method 1: Anvil Renaming (Easiest!)
- Put a standard RC Car in an Anvil and rename it to your vehicle's `id` or `displayName`
  (e.g., `sample_custom_car`).
- Place it on the ground—it will instantly transform into your custom model and specs!

Method 2: Livery Workbench
- Right-click a Livery Workbench with an RC Car. Your custom vehicle will appear in the
  variant list. Click it to apply.

Method 3: Summon Command
- Run in chat:
  /summon microdrone:rc_car ~ ~ ~ {Variant:"sample_custom_car"}

================================================================================
🏎️ Enjoy building your dream RC fleet in RCVERSE!
================================================================================
