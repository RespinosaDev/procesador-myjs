#!/bin/sh
# Compila el procesador y genera myjs.jar (requiere JDK 11 o superior)
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes
javac -encoding UTF-8 -d build/classes $(find src -name '*.java')
printf 'Main-Class: myjs.Main\n' > build/MANIFEST.MF
jar cfm myjs.jar build/MANIFEST.MF -C build/classes .
echo "Generado myjs.jar"
