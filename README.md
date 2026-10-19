# Detección de bordes con máscaras Sobel y Java Threads

Práctica 03 – Computación Paralela.

El programa detecta los bordes de una fotografía de alta resolución de Unsplash
(**9504 × 6336 px, 60.2 megapíxeles**) aplicando las máscaras de convolución de **Sobel**.
El filtro se ejecuta primero de forma **secuencial** y después en **paralelo** con 1, 2, 4, 8 y 16
hilos, usando directamente la clase `Thread` (`start()` y `join()`), sin `ExecutorService`,
`parallelStream()` ni bibliotecas externas como OpenCV. Al final muestra una tabla con tiempo,
aceleración (speedup), eficiencia y una verificación de que la salida paralela es idéntica a la secuencial.

## Requisitos

| Requisito | Detalle |
|---|---|
| **JDK 17 o superior** | Probado con OpenJDK 21. Se necesita `javac`, no solo el JRE. |
| **RAM** | ~1 GB libre para la JVM (los scripts reservan hasta 3 GB con `-Xmx3g`). |
| **Disco** | ~100 MB (imagen de entrada 19 MB + dos PNG de salida de ~38 MB cada uno). |
| VS Code (opcional) | Con la extensión **Extension Pack for Java** (`vscjava.vscode-java-pack`). |

No hay dependencias: solo la biblioteca estándar de Java (`javax.imageio`, `java.awt.image`).

Comprobar que el JDK está instalado:

```bash
java -version
javac -version
```

Si `javac` no existe, instalar un JDK:

| Sistema | Comando |
|---|---|
| Arch / CachyOS | `sudo pacman -S jdk21-openjdk` |
| Ubuntu / Debian | `sudo apt install openjdk-21-jdk` |
| Fedora | `sudo dnf install java-21-openjdk-devel` |
| macOS (Homebrew) | `brew install openjdk@21` |
| Windows | Descargar el instalador de [Adoptium Temurin 21](https://adoptium.net/) y marcar *Set JAVA_HOME* y *Add to PATH*. |

## Estructura

```
src/DeteccionBordes.java       Todo el programa: carga, escala de grises, Sobel secuencial,
                               Sobel con Threads, medición y guardado
imagenes/original.jpg          Fotografía de Unsplash, tamaño original 9504 × 6336
resultados/                    Aquí se generan las imágenes de bordes
resultados/salida_consola.txt  Salida de una ejecución de referencia
informe/informe.html           Informe completo (método, resultados, imágenes y análisis)
informe/img/                   Imágenes reducidas y recortes 1:1 usados en el informe
run.sh                         Compila y ejecuta (Linux / macOS)
run.bat                        Compila y ejecuta (Windows)
.vscode/                       Configuración para ejecutar con F5 en VS Code
```

## Cómo ejecutar

Hay tres formas; todas hacen lo mismo. Se ejecutan **desde la carpeta raíz del proyecto**
(la que contiene este README), porque el programa busca `imagenes/original.jpg` y escribe en
`resultados/` con rutas relativas.

### Opción 1 – Script (la más rápida)

Linux / macOS:

```bash
git clone https://github.com/jordyromero03/Procesamiento_Paralelo_Imagenes_Sobel_Java.git
cd Procesamiento_Paralelo_Imagenes_Sobel_Java
./run.sh
```

Si aparece `Permission denied`, dar permiso de ejecución una vez: `chmod +x run.sh`.

Windows (CMD o PowerShell, dentro de la carpeta):

```bat
run.bat
```

### Opción 2 – Comandos manuales

```bash
javac -d out src/*.java
java -Xmx3g -cp out DeteccionBordes
```

- `javac -d out` compila y deja los `.class` en la carpeta `out/`.
- `-Xmx3g` permite a la JVM usar hasta 3 GB de memoria (la imagen descomprimida ocupa ~180 MB
  y cada arreglo de píxeles ~60 MB). Con `-Xmx1g` también funciona.
- `-cp out` indica dónde están las clases compiladas.

### Opción 3 – Visual Studio Code

1. Instalar la extensión **Extension Pack for Java** (VS Code la sugiere sola al abrir la carpeta).
2. `File → Open Folder…` y abrir la carpeta del proyecto (no la carpeta padre).
3. Esperar a que la barra inferior indique que el proyecto Java terminó de cargar.
4. Pulsar **F5**, o abrir `src/DeteccionBordes.java` y pulsar **Run** sobre el método `main`.

La configuración `.vscode/launch.json` ya define la clase principal, el directorio de trabajo
(`${workspaceFolder}`) y la opción `-Xmx3g`, así que no hay que configurar nada.

### Usar otra imagen

Se puede pasar la ruta como argumento. Debe superar 6000 px **en ambas dimensiones**;
si no, el programa se detiene con un error:

```bash
./run.sh imagenes/otra_foto.jpg
```

## Qué se espera ver

La ejecución completa tarda entre 30 segundos y 1 minuto (cada configuración se ejecuta 8 veces,
ver *Metodología*). Salida de referencia (Intel i7-11800H, 8 núcleos físicos / 16 lógicos, OpenJDK 21):

```
Imagen: 9504 x 6336 (60.2 MP)
Conversion a gris: 989.8 ms

Hilos | Tiempo (ms) | Speedup | Eficiencia | Igual al secuencial
------+-------------+---------+------------+--------------------
  sec |       589.7 |   1.00x |     100.0% | -
    1 |       620.6 |   0.95x |      95.0% | si
    2 |       250.8 |   2.35x |     117.6% | si
    4 |       195.8 |   3.01x |      75.3% | si
    8 |        76.3 |   7.73x |      96.6% | si
   16 |        78.7 |   7.49x |      46.8% | si

Resultados guardados en resultados/
```

Los tiempos cambian según el procesador, la memoria y lo que esté abierto en el equipo; incluso
varían entre dos ejecuciones en la misma máquina. Lo importante es la **tendencia** y que la
última columna diga siempre `si`.

Archivos generados en `resultados/`:

| Archivo | Contenido |
|---|---|
| `bordes_secuencial.png` | Bordes calculados por la versión secuencial |
| `bordes_threads.png` | Bordes calculados con hilos (última configuración, 16 hilos) |

Ambas imágenes son idénticas píxel a píxel. Se ven oscuras a tamaño reducido porque en una
foto de 60 MP cada borde mide pocos píxeles; al hacer zoom se aprecian los contornos de los edificios.

## Cómo funciona

### 1. Escala de grises

Sobel trabaja sobre la intensidad, no sobre el color. Cada píxel RGB se convierte con la
fórmula de luminancia ITU-R BT.601:

```
Y = 0.299·R + 0.587·G + 0.114·B
```

El resultado se guarda en un arreglo `byte[] gris` de `ancho × alto` posiciones.

### 2. Definición de las máscaras

Una máscara (kernel) es una matriz pequeña que se centra sobre cada píxel; se multiplican sus
coeficientes por los 9 vecinos y se suman los productos (convolución).

```
        Gx                      Gy
  | -1   0  +1 |          | -1  -2  -1 |
  | -2   0  +2 |          |  0   0   0 |
  | -1   0  +1 |          | +1  +2  +1 |
```

- **Gx** mide el cambio de intensidad de izquierda a derecha → resalta bordes **verticales**.
- **Gy** mide el cambio de arriba hacia abajo → resalta bordes **horizontales**.
- La fila/columna central pesa 2 para dar más importancia a los vecinos directos y suavizar ruido.

La magnitud del borde en cada píxel es:

```
G = √(Gx² + Gy²)      recortada a 255
```

Los píxeles del marco exterior (primera/última fila y columna) quedan en negro porque la
máscara 3×3 no tiene vecinos completos ahí.

### 3. Paralelización (descomposición de dominio)

Las filas interiores se reparten en franjas contiguas, una por hilo:

```
filas 1 .. alto-2
┌──────────────────────────┐
│  hilo 0  (sobel-0)       │
├──────────────────────────┤
│  hilo 1  (sobel-1)       │
├──────────────────────────┤
│  ...                     │
├──────────────────────────┤
│  hilo N-1                │
└──────────────────────────┘
```

- Cada hilo es un `Thread` con un `TrabajadorSobel` (`Runnable`) que procesa sus filas
  `[filaInicio, filaFin)` con el mismo método `sobelFilas` que usa la versión secuencial.
- Un hilo necesita leer una fila por encima y una por debajo de su franja; no es problema
  porque `gris` es **solo lectura** y todos lo comparten.
- Cada hilo escribe solo en **sus** filas de `salida`, así que ningún píxel es escrito por dos
  hilos: **no hay condición de carrera y no se necesita `synchronized`**.
- `join()` funciona como barrera: el hilo principal espera a que todos terminen antes de medir
  el tiempo y comparar resultados.

### 4. Metodología de medición

- La versión **secuencial** es un bucle normal sobre todas las filas, sin crear ningún hilo.
- **Calentamiento del JIT:** cada configuración se ejecuta 3 veces sin medir. Java compila el
  código a nativo durante las primeras ejecuciones; sin calentamiento, la primera medición
  (la secuencial) saldría artificialmente lenta e inflaría la aceleración.
- Luego se mide 5 veces con `System.nanoTime()` y se reporta la **mediana**, que es menos
  sensible a picos que el promedio.
- Solo se mide el filtro Sobel. La lectura de la imagen, la conversión a gris y la escritura de
  los PNG quedan fuera porque no se paralelizan.
- Se compara la salida de cada configuración con la secuencial (`Arrays.equals`).

Fórmulas usadas:

```
Speedup     S = T_secuencial / T_paralelo
Eficiencia  E = S / N × 100 %          (N = número de hilos)
```

## Análisis de resultados

- **Hasta 8 hilos** la aceleración crece casi de forma lineal: el equipo de prueba tiene 8
  núcleos físicos y cada hilo ocupa uno.
- **De 8 a 16 hilos** prácticamente no mejora y la eficiencia cae a ~47 %. Los 16 procesadores
  lógicos vienen de Hyper-Threading (dos hilos comparten un núcleo físico) y además el
  ancho de banda de memoria empieza a ser el cuello de botella.
- **1 hilo** es igual o un poco más lento que el secuencial: es el costo de crear y unir un `Thread`.
- **Eficiencias mayores a 100 %** en alguna fila son variación de medición (Turbo Boost,
  caché, otros procesos), no una aceleración superlineal real.
- **Ley de Amdahl:** la conversión a escala de grises (~1 s) es secuencial y tarda más que el
  Sobel paralelo, así que la aceleración del programa completo es mucho menor que la del filtro.
  Paralelizarla con el mismo reparto por filas sería la siguiente mejora.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `javac: command not found` | Solo está instalado el JRE. Instalar un JDK (ver *Requisitos*). |
| `No se pudo leer la imagen: imagenes/original.jpg` | Se ejecutó desde otra carpeta. Entrar a la raíz del proyecto (`cd Procesamiento_Paralelo_Imagenes_Sobel_Java`) o usar `run.sh` / `run.bat`, que lo hacen solos. |
| `La imagen debe superar 6000 x 6000 pixeles.` | La foto se descargó reducida. Ver *Imagen de entrada*. |
| `OutOfMemoryError: Java heap space` | Falta memoria para la JVM. Ejecutar con `-Xmx3g` (o más) y cerrar programas pesados. |
| `./run.sh: Permission denied` | `chmod +x run.sh` |
| VS Code no muestra *Run* sobre `main` | Abrir la carpeta del proyecto (no la padre), instalar *Extension Pack for Java* y esperar a que cargue. |
| Speedup bajo o tiempos muy variables | Cerrar navegadores, juegos o compilaciones en segundo plano y conectar el portátil a la corriente. |

## Imagen de entrada

- Fuente: [Cityscape under warm golden sky – Unsplash](https://unsplash.com/photos/cityscape-under-warm-golden-sky-wvjjZTzsdaY),
  bajo la [Licencia Unsplash](https://unsplash.com/license) (uso libre).
- Tamaño original: **9504 × 6336 px**, JPEG de ~19 MB.

La imagen ya viene en `imagenes/original.jpg`. Si falta, se puede volver a descargar:

- Desde el navegador: abrir el enlace, botón **Download → Original size** y guardarla como
  `imagenes/original.jpg`. La vista de la página es una versión reducida (3000 px), así que no
  sirve clic derecho → guardar imagen.
- Desde la terminal (CDN de Unsplash, tamaño original):

  ```bash
  curl -L -o imagenes/original.jpg "https://images.unsplash.com/photo-1791970220728-56fbb18ed2af?q=100&fm=jpg"
  ```
