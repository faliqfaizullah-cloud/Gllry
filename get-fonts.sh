#!/data/data/com.termux/files/usr/bin/sh
# Downloads the three fonts the new UI uses into app/src/main/assets/fonts
# Run once from ~/Gllry:   sh get-fonts.sh
set -e
D=app/src/main/assets/fonts
mkdir -p "$D"
G=https://github.com/google/fonts/raw/main/ofl
curl -fL "$G/instrumentsans/InstrumentSans%5Bwdth%2Cwght%5D.ttf" -o "$D/ui.ttf"     && echo "ui.ttf ok (Instrument Sans)"
curl -fL "$G/doto/Doto%5BROND%2Cwght%5D.ttf"                       -o "$D/pixel.ttf"  && echo "pixel.ttf ok (Doto)"
curl -fL "$G/gaegu/Gaegu-Regular.ttf"                              -o "$D/script.ttf" && echo "script.ttf ok (Gaegu)"
ls -la "$D"
