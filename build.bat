@echo off
rem Compila el procesador y genera myjs.jar (requiere JDK 11 o superior)
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes
dir /s /b src\*.java > build\fuentes.txt
javac -encoding UTF-8 -d build\classes @build\fuentes.txt || exit /b 1
echo Main-Class: myjs.Main> build\MANIFEST.MF
jar cfm myjs.jar build\MANIFEST.MF -C build\classes . || exit /b 1
echo Generado myjs.jar
