$modelsDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/src/main/resources/assets/microdrone/models/block"

# 共通テクスチャマップ
$textures = @{
    "road" = "microdrone:block/modular_slope_road"
    "curb" = "microdrone:block/modular_slope_curb"
    "side" = "microdrone:block/modular_slope_side"
    "particle" = "microdrone:block/modular_slope_road"
}

# セグメント別定義
# startY, endY, angle, originY, length
$configs = @{
    "single" = @{
        angle = -45.0
        originY = 0.0
        startY = 0.0
        length = 22.627
        railFromY = 0.0
        railToY = 2.5
    }
    "bottom_2" = @{
        angle = -22.5
        originY = 0.0
        startY = 0.0
        length = 17.318
        railFromY = 0.0
        railToY = 2.5
    }
    "top_2" = @{
        angle = -22.5
        originY = 6.627
        startY = 6.627
        length = 17.318
        railFromY = 6.627
        railToY = 9.127
    }
    "bottom_3" = @{
        angle = -22.5
        originY = 0.0
        startY = 0.0
        length = 14.0
        railFromY = 0.0
        railToY = 2.5
    }
    "middle_3" = @{
        angle = -22.5
        originY = 5.333
        startY = 5.333
        length = 14.0
        railFromY = 5.333
        railToY = 7.833
    }
    "top_3" = @{
        angle = -22.5
        originY = 10.667
        startY = 10.667
        length = 14.0
        railFromY = 10.667
        railToY = 13.167
    }
}

foreach ($segName in $configs.Keys) {
    $cfg = $configs[$segName]
    $angle = [double]$cfg.angle
    $originY = [double]$cfg.originY
    $startY = [double]$cfg.startY
    $len = [double]$cfg.length

    # 1. ベース道路面モデル: modular_slope_${segName}.json
    # 幅は [0, 16] のフルサイズ！
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

    $roadPath = Join-Path $modelsDir "modular_slope_${segName}.json"
    $roadJson = ConvertTo-Json $roadModel -Depth 6
    [System.IO.File]::WriteAllText($roadPath, $roadJson, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Created road model: $roadPath"

    # 2. 左側ガードレール(縁石): modular_slope_left_${segName}.json
    # X: 0..2
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
    $leftPath = Join-Path $modelsDir "modular_slope_left_${segName}.json"
    $leftJson = ConvertTo-Json $leftModel -Depth 6
    [System.IO.File]::WriteAllText($leftPath, $leftJson, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Created left curb model: $leftPath"

    # 3. 右側ガードレール(縁石): modular_slope_right_${segName}.json
    # X: 14..16
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
                    "north" = @{ "uv" = @(0, 0, 2, 2);    "texture" = "#curb" }
                    "south" = @{ "uv" = @(0, 0, 2, 2);    "texture" = "#curb" }
                    "west"  = @{ "uv" = @(0, 0, 16, 2);   "texture" = "#curb" }
                    "east"  = @{ "uv" = @(0, 0, 16, 2);   "texture" = "#side" }
                }
            }
        )
    }
    $rightPath = Join-Path $modelsDir "modular_slope_right_${segName}.json"
    $rightJson = ConvertTo-Json $rightModel -Depth 6
    [System.IO.File]::WriteAllText($rightPath, $rightJson, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Created right curb model: $rightPath"
}

Write-Host "All 18 modular slope block models generated successfully."

