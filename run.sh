#!/usr/bin/env bash
# Compila y ejecuta la práctica.
# Uso: ./run.sh [ruta_imagen]
# Ej.: ./run.sh                          (usa imagenes/original.jpg)
#      ./run.sh imagenes/otra.jpg
set -e
cd "$(dirname "$0")"

javac -d out src/*.java
java -Xmx3g -cp out DeteccionBordes "$@"
