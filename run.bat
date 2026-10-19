@echo off
REM Compila y ejecuta la practica en Windows.
REM Uso: run.bat [ruta_imagen]
cd /d "%~dp0"

javac -d out src\*.java || exit /b 1
java -Xmx3g -cp out DeteccionBordes %*
