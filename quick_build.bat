@echo off
chcp 65001 > nul
echo ========================================================
echo   Micro Drone Mod - 高速ビルド実行中...
echo ========================================================
cd /d "%~dp0"
call .\gradlew.bat build -x test
if %ERRORLEVEL% EQU 0 (
    echo.
    echo [成功] ビルドが正常に完了しました！
    timeout /t 2 > nul
) else (
    echo.
    echo [エラー] ビルド中にエラーが発生しました。
    pause
)

