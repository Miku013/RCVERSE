$modelsDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/src/main/resources/assets/microdrone/models/block"
$blockstatesDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/src/main/resources/assets/microdrone/blockstates"
$itemModelsDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/src/main/resources/assets/microdrone/models/item"

$textures = @{
    "road" = "microdrone:block/modular_slope_road"
    "curb" = "microdrone:block/modular_slope_curb"
    "side" = "microdrone:block/modular_slope_side"
    "particle" = "microdrone:block/modular_slope_road"
}

$configs = @{
    "lower" = @{
        angle = -22.5
        originY = 0.0
        startY = 0.0
        length = 17.318
    }
    "upper" = @{
        angle = -22.5
        originY = 6.627
        startY = 6.627
        length = 17.318
    }
}

foreach ($segName in $configs.Keys) {
    $cfg = $configs[$segName]
    $angle = [double]$cfg.angle
    $originY = [double]$cfg.originY
    $startY = [double]$cfg.startY
    $len = [double]$cfg.length

    # 1. 道路面: touge_slope_${segName}.json
    $roadModel = @{
        "ambientocclusion" = $false
        "textures" = $textures
        "elements" = @(
            @{
                "name" = "road_surface"
                "from" = @([double]0.0, [double]$startY, [double]0.0)
                "to"   = @([double]16.0, [double]($startY + 1.0), [double]$len)
                "rotation" = @{
                    "origin" = @([double]8.0, [double]$originY, [double]0.0)
                    "axis" = "x"
                    "angle" = $angle
                }
                "faces" = @{
                    "up"    = @{ "uv" = @(0, 0, 16, 16); "texture" = "#road" }
                    "down"  = @{ "uv" = @(0, 0, 16, 16); "texture" = "#side" }
                    "north" = @{ "uv" = @(0, 0, 16, 1);  "texture" = "#road" }
                    "south" = @{ "uv" = @(0, 0, 16, 1);  "texture" = "#road" }
                    "east"  = @{ "uv" = @(0, 0, 16, 1);  "texture" = "#side" }
                    "west"  = @{ "uv" = @(0, 0, 16, 1);  "texture" = "#side" }
                }
            }
        )
    }
    $roadPath = Join-Path $modelsDir "touge_slope_${segName}.json"
    $roadJson = ConvertTo-Json $roadModel -Depth 6
    [System.IO.File]::WriteAllText($roadPath, $roadJson, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Created road model: $roadPath"

    # 2. 左側縁石: touge_slope_left_${segName}.json
    $leftModel = @{
        "ambientocclusion" = $false
        "textures" = $textures
        "elements" = @(
            @{
                "name" = "curb_left"
                "from" = @([double]0.0, [double]$startY, [double]0.0)
                "to"   = @([double]2.0, [double]($startY + 2.2), [double]$len)
                "rotation" = @{
                    "origin" = @([double]8.0, [double]$originY, [double]0.0)
                    "axis" = "x"
                    "angle" = $angle
                }
                "faces" = @{
                    "up"    = @{ "uv" = @(0, 0, 2, 16);  "texture" = "#curb" }
                    "down"  = @{ "uv" = @(0, 0, 2, 16);  "texture" = "#side" }
                    "north" = @{ "uv" = @(0, 0, 2, 2);   "texture" = "#curb" }
                    "south" = @{ "uv" = @(0, 0, 2, 2);   "texture" = "#curb" }
                    "east"  = @{ "uv" = @(0, 0, 16, 2);  "texture" = "#curb" }
                    "west"  = @{ "uv" = @(0, 0, 16, 2);  "texture" = "#side" }
                }
            }
        )
    }
    $leftPath = Join-Path $modelsDir "touge_slope_left_${segName}.json"
    $leftJson = ConvertTo-Json $leftModel -Depth 6
    [System.IO.File]::WriteAllText($leftPath, $leftJson, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Created left curb model: $leftPath"

    # 3. 右側縁石: touge_slope_right_${segName}.json
    $rightModel = @{
        "ambientocclusion" = $false
        "textures" = $textures
        "elements" = @(
            @{
                "name" = "curb_right"
                "from" = @([double]14.0, [double]$startY, [double]0.0)
                "to"   = @([double]16.0, [double]($startY + 2.2), [double]$len)
                "rotation" = @{
                    "origin" = @([double]8.0, [double]$originY, [double]0.0)
                    "axis" = "x"
                    "angle" = $angle
                }
                "faces" = @{
                    "up"    = @{ "uv" = @(14, 0, 16, 16); "texture" = "#curb" }
                    "down"  = @{ "uv" = @(14, 0, 16, 16); "texture" = "#side" }
                    "north" = @{ "uv" = @(14, 0, 16, 2);  "texture" = "#curb" }
                    "south" = @{ "uv" = @(14, 0, 16, 2);  "texture" = "#curb" }
                    "east"  = @{ "uv" = @(0, 0, 16, 2);   "texture" = "#side" }
                    "west"  = @{ "uv" = @(0, 0, 16, 2);   "texture" = "#curb" }
                }
            }
        )
    }
    $rightPath = Join-Path $modelsDir "touge_slope_right_${segName}.json"
    $rightJson = ConvertTo-Json $rightModel -Depth 6
    [System.IO.File]::WriteAllText($rightPath, $rightJson, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Created right curb model: $rightPath"
}

# アイテムモデル作成
$itemModel = @{
    "parent" = "microdrone:block/touge_slope_lower"
}
$itemPath = Join-Path $itemModelsDir "touge_slope.json"
[System.IO.File]::WriteAllText($itemPath, (ConvertTo-Json $itemModel -Depth 3), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Created item model: $itemPath"

# ブロックステート (multipart) 作成
$facingRotations = @{
    "south" = 0
    "west"  = 90
    "north" = 180
    "east"  = 270
}

$multipart = @()

foreach ($seg in @("lower", "upper")) {
    foreach ($facing in $facingRotations.Keys) {
        $rot = $facingRotations[$facing]

        # 道路ベース
        $applyBase = @{ "model" = "microdrone:block/touge_slope_${seg}" }
        if ($rot -ne 0) { $applyBase["y"] = $rot }
        $multipart += @{
            "when" = @{ "segment" = $seg; "facing" = $facing }
            "apply" = $applyBase
        }

        # 左縁石
        $applyLeft = @{ "model" = "microdrone:block/touge_slope_left_${seg}" }
        if ($rot -ne 0) { $applyLeft["y"] = $rot }
        $multipart += @{
            "when" = @{ "segment" = $seg; "facing" = $facing; "left" = "true" }
            "apply" = $applyLeft
        }

        # 右縁石
        $applyRight = @{ "model" = "microdrone:block/touge_slope_right_${seg}" }
        if ($rot -ne 0) { $applyRight["y"] = $rot }
        $multipart += @{
            "when" = @{ "segment" = $seg; "facing" = $facing; "right" = "true" }
            "apply" = $applyRight
        }
    }
}

$blockstateModel = @{
    "multipart" = $multipart
}
$blockstatePath = Join-Path $blockstatesDir "touge_slope.json"
[System.IO.File]::WriteAllText($blockstatePath, (ConvertTo-Json $blockstateModel -Depth 6), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Created blockstate: $blockstatePath"

