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

mkdir -p "$HOME/.local/share/icons/hicolor/512x512/apps" "$HOME/.local/share/applications"
cp res/icon.png "$HOME/.local/share/icons/hicolor/512x512/apps/focuson.png"
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
