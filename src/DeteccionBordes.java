import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Deteccion de bordes con el operador Sobel, secuencial vs. Java Threads.
 *
 * Descomposicion de dominio: la imagen se divide en franjas horizontales de
 * filas y cada hilo calcula solo sus filas. La entrada (gris) es de solo
 * lectura y cada hilo escribe en posiciones distintas de la salida, por eso
 * no hace falta synchronized: no hay condicion de carrera.
 */
public class DeteccionBordes {

    // Mascaras (kernels) de Sobel 3x3.
    static final int[][] GX = {
        {-1, 0, 1},
        {-2, 0, 2},
        {-1, 0, 1}
    };

    static final int[][] GY = {
        {-1, -2, -1},
        { 0,  0,  0},
        { 1,  2,  1}
    };

    static final int TAMANO_MINIMO = 6000;
    static final int CALENTAMIENTO = 3;   // corridas descartadas (JIT)
    static final int REPETICIONES = 5;    // corridas medidas, se usa la mediana
    static final int[] HILOS_A_PROBAR = {1, 2, 4, 8, 16};

    public static void main(String[] args) throws Exception {
        String rutaEntrada = args.length > 0 ? args[0] : "imagenes/original.jpg";

        BufferedImage imagen = ImageIO.read(new File(rutaEntrada));
        if (imagen == null) {
            throw new IOException("No se pudo leer la imagen: " + rutaEntrada);
        }

        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        System.out.printf("Imagen: %d x %d (%.1f MP)%n", ancho, alto, ancho * (double) alto / 1e6);

        if (ancho <= TAMANO_MINIMO || alto <= TAMANO_MINIMO) {
            throw new IllegalArgumentException("La imagen debe superar 6000 x 6000 pixeles.");
        }

        long t0 = System.nanoTime();
        byte[] gris = aEscalaDeGrises(imagen);
        System.out.printf("Conversion a gris: %.1f ms%n", (System.nanoTime() - t0) / 1e6);

        // Referencia secuencial: un bucle normal, sin crear ningun hilo.
        byte[] salidaSecuencial = new byte[ancho * alto];
        double tSecuencial = medir(() -> sobelFilas(gris, salidaSecuencial, ancho, 1, alto - 1));

        System.out.println();
        System.out.println("Hilos | Tiempo (ms) | Speedup | Eficiencia | Igual al secuencial");
        System.out.println("------+-------------+---------+------------+--------------------");
        System.out.printf("  sec | %11.1f |   1.00x |     100.0%% | -%n", tSecuencial);

        byte[] salidaParalela = null;
        for (int numeroHilos : HILOS_A_PROBAR) {
            byte[] salida = new byte[ancho * alto];
            double t = medir(() -> sobelConHilos(gris, salida, ancho, alto, numeroHilos));
            double speedup = tSecuencial / t;
            System.out.printf("%5d | %11.1f | %6.2fx | %9.1f%% | %s%n",
                numeroHilos, t, speedup, speedup / numeroHilos * 100,
                Arrays.equals(salida, salidaSecuencial) ? "si" : "NO");
            salidaParalela = salida;
        }

        new File("resultados").mkdirs();
        guardar(salidaSecuencial, ancho, alto, "resultados/bordes_secuencial.png");
        guardar(salidaParalela, ancho, alto, "resultados/bordes_threads.png");
        System.out.println("\nResultados guardados en resultados/");
    }

    /** Luminancia ITU-R BT.601: Y = 0.299 R + 0.587 G + 0.114 B. */
    static byte[] aEscalaDeGrises(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        byte[] gris = new byte[ancho * alto];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int rgb = imagen.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gris[y * ancho + x] = (byte) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return gris;
    }

    /**
     * Aplica Sobel a las filas [filaInicio, filaFin). Es el mismo codigo que
     * ejecuta la version secuencial y cada hilo: solo cambia el rango.
     * Los bordes de la imagen (fila/columna 0 y ultima) quedan en negro
     * porque la mascara 3x3 no tiene vecinos completos ahi.
     */
    static void sobelFilas(byte[] gris, byte[] salida, int ancho, int filaInicio, int filaFin) {
        for (int y = filaInicio; y < filaFin; y++) {
            for (int x = 1; x < ancho - 1; x++) {
                int gx = 0;
                int gy = 0;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int pixel = gris[(y + ky) * ancho + (x + kx)] & 0xFF;
                        gx += pixel * GX[ky + 1][kx + 1];
                        gy += pixel * GY[ky + 1][kx + 1];
                    }
                }
                int magnitud = (int) Math.sqrt((double) gx * gx + (double) gy * gy);
                salida[y * ancho + x] = (byte) Math.min(255, magnitud);
            }
        }
    }

    /** Reparte las filas interiores en franjas contiguas, una por hilo. */
    static void sobelConHilos(byte[] gris, byte[] salida, int ancho, int alto, int numeroHilos)
            throws InterruptedException {
        int filasInteriores = alto - 2;
        Thread[] hilos = new Thread[numeroHilos];

        for (int i = 0; i < numeroHilos; i++) {
            int inicio = 1 + (int) ((long) i * filasInteriores / numeroHilos);
            int fin = 1 + (int) ((long) (i + 1) * filasInteriores / numeroHilos);
            hilos[i] = new Thread(new TrabajadorSobel(gris, salida, ancho, inicio, fin), "sobel-" + i);
            hilos[i].start();
        }
        for (Thread hilo : hilos) {
            hilo.join();   // barrera: esperar a que todos terminen
        }
    }

    /** Tarea de cada hilo: procesar su franja de filas. */
    static class TrabajadorSobel implements Runnable {
        private final byte[] gris;
        private final byte[] salida;
        private final int ancho;
        private final int filaInicio;
        private final int filaFin;

        TrabajadorSobel(byte[] gris, byte[] salida, int ancho, int filaInicio, int filaFin) {
            this.gris = gris;
            this.salida = salida;
            this.ancho = ancho;
            this.filaInicio = filaInicio;
            this.filaFin = filaFin;
        }

        @Override
        public void run() {
            sobelFilas(gris, salida, ancho, filaInicio, filaFin);
        }
    }

    interface Tarea {
        void ejecutar() throws InterruptedException;
    }

    /** Calienta el JIT y devuelve la mediana de varias corridas, en ms. */
    static double medir(Tarea tarea) throws InterruptedException {
        for (int i = 0; i < CALENTAMIENTO; i++) {
            tarea.ejecutar();
        }
        double[] tiempos = new double[REPETICIONES];
        for (int i = 0; i < REPETICIONES; i++) {
            long inicio = System.nanoTime();
            tarea.ejecutar();
            tiempos[i] = (System.nanoTime() - inicio) / 1e6;
        }
        Arrays.sort(tiempos);
        return tiempos[REPETICIONES / 2];
    }

    static void guardar(byte[] pixeles, int ancho, int alto, String ruta) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_BYTE_GRAY);
        byte[] destino = ((DataBufferByte) imagen.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixeles, 0, destino, 0, pixeles.length);
        ImageIO.write(imagen, "png", new File(ruta));
    }
}
