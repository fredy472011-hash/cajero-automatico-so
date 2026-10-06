package cajero;

import cajero.aplicacion.Cajero;
import cajero.hardware.Dispensador;
import cajero.persistencia.BaseDeDatos;
import cajero.persistencia.RepositorioCuentas;
import cajero.so.PlanificadorCPU;
import cajero.so.RegistroActividad;
import cajero.web.ServidorCajero;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PUNTO DE ARRANQUE del cajero con interfaz web.
 *
 * Es el unico lugar donde se ARMAN las piezas (lo que en Spring hace la
 * inyeccion de dependencias, aca se hace a mano y a la vista):
 *
 *   RepositorioCuentas  -> memoria de largo plazo (MySQL)
 *   Dispensador         -> entrada/salida
 *   PlanificadorCPU     -> planificacion de procesos
 *   Cajero              -> logica de negocio
 *   ServidorCajero      -> presentacion HTTP
 *
 * Correr con:
 *   java -cp "backend/out;backend/lib/mysql-connector-j-9.4.0.jar" cajero.MainWeb
 *
 * Despues abrir  http://localhost:8080
 */
public class MainWeb {

    private static final int PUERTO = 8080;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("  CAJERO AUTOMATICO - modo web");
        System.out.println("=".repeat(70));

        // 1. Memoria de largo plazo
        if (!BaseDeDatos.disponible()) {
            System.err.println("""

                    No hay conexion con MySQL.

                    Revisar:
                      1. Que el servicio MySQL este corriendo
                      2. Que la base exista:   mysql -u root -p < sql/esquema.sql
                      3. Las variables DB_URL, DB_USER y DB_PASSWORD
                    """);
            return;
        }
        RepositorioCuentas repositorio = new RepositorioCuentas();
        System.out.println("  [OK] MySQL conectado");

        // 2. Hardware. Los casetes se leen de la base: el cajero recuerda
        //    cuantos billetes le quedaron de la vez anterior.
        int de100k = repositorio.leerCasete(100_000);
        int de50k  = repositorio.leerCasete(50_000);
        Dispensador dispensador = new Dispensador(de100k, de50k, 1800);
        System.out.printf("  [OK] Dispensador: %d x 100.000 + %d x 50.000%n", de100k, de50k);

        // 3. Sistema operativo simulado
        RegistroActividad bitacora = new RegistroActividad();
        PlanificadorCPU planificador =
                new PlanificadorCPU(PlanificadorCPU.Politica.PRIORIDAD, 2, bitacora);
        planificador.iniciar();
        System.out.println("  [OK] Planificador de CPU iniciado");

        // 4. Logica de negocio
        Cajero cajero = new Cajero(repositorio, dispensador, planificador, bitacora);

        // 5. Presentacion
        Path frontend = ubicarFrontend();
        ServidorCajero servidor = new ServidorCajero(cajero, PUERTO, frontend);
        servidor.iniciar();

        // Apagado ordenado: al cortar con Ctrl+C se detiene todo y se graba
        // el estado de los casetes.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nApagando el cajero...");
            dispensador.estadoCasetes().forEach(repositorio::guardarCasete);
            planificador.detener();
            servidor.detener();
            System.out.println("Estado de los casetes grabado. Listo.");
        }));
    }

    /**
     * Busca la carpeta frontend tanto si el programa se ejecuta desde la raiz
     * del proyecto como si se ejecuta desde backend/.
     */
    private static Path ubicarFrontend() {
        Path[] candidatos = {
                Path.of("frontend"),
                Path.of("..", "frontend"),
                Path.of("..", "..", "frontend")
        };
        for (Path p : candidatos) {
            if (Files.isDirectory(p) && Files.isRegularFile(p.resolve("index.html"))) {
                return p.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException(
                "No se encontro la carpeta frontend. Ejecutar desde la raiz del proyecto.");
    }
}
