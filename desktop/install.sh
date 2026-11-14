#!/usr/bin/env bash
# Focus On'ni shu kompyuterga o'rnatadi (sudo kerak emas).
# Ilova: ~/.local/share/focuson-app   Ma'lumotlar: ~/.local/share/focuson (tegilmaydi)
set -euo pipefail
cd "$(dirname "$0")"

echo "Focus On yig'ilmoqda (birinchi marta bir necha daqiqa olishi mumkin)..."
./gradlew createDistributable --console=plain -q

SRC="build/compose/binaries/main/app/FocusOn"
DEST="$HOME/.local/share/focuson-app"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -r "$SRC"/. "$DEST"/

# app icon in every size (menu, dock, alt-tab)
for s in 16 24 32 48 64 128 256 512; do
    mkdir -p "$HOME/.local/share/icons/hicolor/${s}x${s}/apps"
    cp "res/icons/$s.png" "$HOME/.local/share/icons/hicolor/${s}x${s}/apps/focuson.png"
done
mkdir -p "$HOME/.local/share/applications"
gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" >/dev/null 2>&1 || true
cat > "$HOME/.local/share/applications/focuson.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Focus On
Comment=Diqqat bilan ishlash uchun taymer
Exec=$DEST/bin/FocusOn
Icon=focuson
Categories=Utility;Education;
StartupWMClass=uz-focuson-desktop-MainKt
Terminal=false
EOF
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
echo "Tayyor. Ilovalar menyusidan \"Focus On\" ni oching."
