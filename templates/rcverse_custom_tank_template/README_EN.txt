================================================================================
  [RCVERSE] RC Tank Custom Model Starter Kit (Minecraft 1.21.1)
================================================================================

This template kit allows you to create your own custom RC Tank using the free
3D modeling software "Blockbench" and import it directly into Minecraft without
recompiling the mod!
Full support for turret yaw rotation, cannon elevation/depression pitch,
realistic barrel recoil animation on cannon fire, animated tracks and wheels,
muzzle flash particles, and ground dust shockwaves.

--------------------------------------------------------------------------------
[Folder Structure]
--------------------------------------------------------------------------------
rcverse_custom_tank_template/
  ├── pack.mcmeta                               <-- Resource pack metadata (Format: 34)
  ├── README_JA.txt                             <-- Japanese Guide
  ├── README_EN.txt                             <-- This document (English)
  ├── config/
  │   └── microdrone/
  │       └── tanks/
  │           └── sample_custom_tank.json       <-- [CRITICAL] Tank stats and vehicle config
  └── assets/
      └── microdrone/
          ├── geo/
          │   └── sample_custom_tank.geo.json   <-- [CRITICAL] 3D Model file for Blockbench
          └── textures/
              └── entity/
                  └── sample_custom_tank.png    <-- Texture map (256x256)

--------------------------------------------------------------------------------
[Step 1: Edit 3D Model in Blockbench]
--------------------------------------------------------------------------------
1. Download and install Blockbench (https://www.blockbench.net/).
2. Open `assets/microdrone/geo/sample_custom_tank.geo.json` in Blockbench.
3. In the outliner panel (top-right), you will find the required bone hierarchy:

   [Required Bone Structure]
   - root             : Origin reference (do not modify)
     └── chassis      : Vehicle root
         ├── camera_driver : Driver seat camera point (FPV view)
         ├── battery_slot  : Battery module slot
         ├── motor_slot    : Motor upgrade slot
         ├── antenna       : RC antenna
         │
         ├── [Left Track & Wheel Group]
         │   ├── track_l     : Left track root
         │   ├── sprocket_fl : Left front drive sprocket (rotates with movement)
         │   ├── sprocket_rl : Left rear idler sprocket
         │   ├── idler_l     : Idler wheel
         │   ├── wheels_l    : Road wheels (rotates automatically)
         │   └── ltop, lbottom, lrear_*, lfront_* : Track plates
         │
         ├── [Right Track & Wheel Group]
         │   ├── track_r     : Right track root
         │   ├── sprocket_fr : Right front drive sprocket
         │   ├── sprocket_rr : Right rear idler sprocket
         │   ├── idler_r     : Idler wheel
         │   ├── wheels_r    : Road wheels
         │   └── rtop, rbottom, rrear_*, rfront_* : Track plates
         │
         ├── turret   : Turret root (rotates 360 deg yaw based on player aiming)
         │   ├── turretbody    : Main turret armor cubes
         │   ├── hatch         : Top access hatch
         │   ├── camera_gunner : Gunner aim camera position (syncs with turret rotation)
         │   └── cannon        : Cannon root (syncs with pitch angle up/down)
         │       ├── cannonbody: Mantlet / breech base
         │       └── recoil    : [CRITICAL] Barrel recoil animation bone!
         │           │           Place your barrel tubes / muzzle brake inside this bone!
         │           │           It automatically slides backward on fire and recovers smoothly.
         │           └── muzzle: Muzzle tip origin (flash particles and projectile spawn point)
         │
         └── body     : Main hull armor cubes

4. [Modeling Tips]
   - You can add, scale, or customize cubes to create your own tanks.
   - Keep barrel cubes under the `recoil` bone so they animate during shooting.
   - Align the `muzzle` bone's pivot with the very tip of your cannon barrel.
   - Place the `camera_gunner` pivot where you want the first-person gunner sight to be.
5. Paint your texture using Blockbench Paint mode or an external image editor.
6. Export the model via "File" -> "Export" -> "Export GeckoLib Model".

--------------------------------------------------------------------------------
[Step 2: Configure Tank Stats (JSON)]
--------------------------------------------------------------------------------
Open `config/microdrone/tanks/sample_custom_tank.json` with any text editor:

{
  "id": "my_tank",                                  <-- Unique ID (lowercase alphanumeric)
  "displayName": "Heavy Tank Tiger",                <-- In-game display name
  "model": "microdrone:geo/sample_custom_tank.geo.json",  <-- Model resource path
  "texture": "microdrone:textures/entity/sample_custom_tank.png", <-- Texture path
  "animation": "microdrone:animations/rc_tank.animation.json",   <-- Default animation
  "properties": {
    "maxSpeedMultiplier": 1.0,    <-- Speed multiplier (1.0=default, 0.7=heavy, 1.3=scout)
    "accelMultiplier": 1.0,       <-- Acceleration multiplier
    "turnSpeedMultiplier": 1.0,   <-- Neutral/pivot turn speed
    "reloadTicks": 20,            <-- Cannon reload delay (20 ticks = 1 second)
    "cannonPower": 1.2,           <-- Projectile velocity and power
    "scale": 1.0,                 <-- Overall vehicle visual scale
    "invertCannonPitch": false,   <-- Invert vertical aiming controls
    "invertTurretYaw": false,     <-- Invert horizontal turret controls
    "recoilDistance": 3.0         <-- [NEW] Recoil stroke distance (default: 3.0 units)
  }
}

--------------------------------------------------------------------------------
[Step 3: Install & Play in Minecraft]
--------------------------------------------------------------------------------
Method A: Load as Resource Pack (Recommended)
1. Copy the folder containing `pack.mcmeta` and `assets` (or a .zip of it) into
   your `.minecraft/resourcepacks/` directory.
2. Copy `sample_custom_tank.json` into `.minecraft/config/microdrone/tanks/`.
3. Launch Minecraft and enable the resource pack under Options -> Resource Packs.

Method B: In-Game Summoning
1. Rename an "RC Tank" item on an Anvil to your config's `id` or `displayName`
   (e.g., `my_tank` or `Heavy Tank Tiger`).
2. Place the renamed RC Tank on the ground — it will appear with your custom 3D model!
3. Right-click with your Transmitter to bind and control!
================================================================================
