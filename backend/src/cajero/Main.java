package cajero;

import cajero.hardware.Dispensador;
import cajero.dominio.Cuenta;
import cajero.so.EstadoProceso;
import cajero.so.PlanificadorCPU;
import cajero.so.ProcesoTransaccion;
import cajero.so.RegistroActividad;

import java.util.Map;

/**
 * Demostracion de los conceptos de sistemas operativos.
 *
 * Correr con:   java -cp out cajero.Main
 *
 * Cada demo muestra un tema de la materia:
 *   1. Condicion de carrera (y por que ocurre)
 *   2. Sincronizacion con synchronized (la solucion)
 *   3. Procesos, estados, planificacion y E/S
 *   4. Limites del dispensador (algoritmo voraz)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        titulo("SIMULADOR DE CAJERO AUTOMATICO - Conceptos de Sistemas Operativos");

        demo1CondicionDeCarrera();
        demo2Sincronizacion();
        demo3ProcesosYPlanificacion();
        demo4LimitesDelDispensador();

        System.out.println("\nFin de la demostracion.");
    }

    // ------------------------------------------------------------------
    // DEMO 1: la condicion de carrera
    // ------------------------------------------------------------------
    private static void demo1CondicionDeCarrera() throws InterruptedException {
        titulo("1. CONDICION DE CARRERA  (sin sincronizar)");

        Cuenta cuenta = new Cuenta("001", "Fredy Canete", "1234", 5, 150_000);
        System.out.println("Saldo inicial: Gs. " + fmt(cuenta.getSaldo()));
        System.out.println("Dos cajeros retiran Gs. 100.000 AL MISMO TIEMPO.\n");

        Thread cajero1 = new Thread(() -> {
            boolean ok = cuenta.retirarSinSincronizar(100_000);
            System.out.println("  Cajero-1 retiro: " + (ok ? "SI" : "NO"));
        }, "Cajero-1");

        Thread cajero2 = new Thread(() -> {
            boolean ok = cuenta.retirarSinSincronizar(100_000);
            System.out.println("  Cajero-2 retiro: " + (ok ? "SI" : "NO"));
        }, "Cajero-2");

        cajero1.start();
        cajero2.start();
        cajero1.join();
        cajero2.join();

        long saldo = cuenta.getSaldo();
        System.out.println("\nSaldo final:   Gs. " + fmt(saldo));
        if (saldo < 0) {
            System.out.println(">>> ERROR: el banco entrego Gs. 200.000 teniendo 150.000.");
            System.out.println(">>> Los dos hilos leyeron 150.000 ANTES de que alguno grabara.");
        } else {
            System.out.println(">>> Esta vez no fallo. Las condiciones de carrera son INTERMITENTES:");
            System.out.println(">>> por eso son tan peligrosas. Volve a correr el programa.");
        }
    }

    // ------------------------------------------------------------------
    // DEMO 2: la solucion
    // ------------------------------------------------------------------
    private static void demo2Sincronizacion() throws InterruptedException {
        titulo("2. SINCRONIZACION  (con synchronized)");

        Cuenta cuenta = new Cuenta("002", "Fredy Canete", "1234", 5, 150_000);
        System.out.println("Saldo inicial: Gs. " + fmt(cuenta.getSaldo()));
        System.out.println("Mismo escenario, pero el metodo retirar() es synchronized.\n");

        Thread c1 = new Thread(() -> System.out.println("  Cajero-1 retiro: "
                + (cuenta.retirar(100_000) ? "SI" : "NO (rechazado correctamente)")));
        Thread c2 = new Thread(() -> System.out.println("  Cajero-2 retiro: "
                + (cuenta.retirar(100_000) ? "SI" : "NO (rechazado correctamente)")));

        c1.start();
        c2.start();
        c1.join();
        c2.join();

        System.out.println("\nSaldo final:   Gs. " + fmt(cuenta.getSaldo()));
        System.out.println(">>> Un solo hilo entra por vez: leer y descontar es una operacion atomica.");
    }

    // ------------------------------------------------------------------
    // DEMO 3: procesos, estados, planificacion y E/S
    // ------------------------------------------------------------------
    private static void demo3ProcesosYPlanificacion() throws InterruptedException {
        titulo("3. PROCESOS, ESTADOS Y PLANIFICACION");

        RegistroActividad bitacora = new RegistroActividad();
        Dispensador dispensador = new Dispensador(20, 20, 1500);
        Cuenta cuenta = new Cuenta("003", "Fredy Canete", "1234", 5, 3_000_000);

        PlanificadorCPU planificador =
                new PlanificadorCPU(PlanificadorCPU.Politica.PRIORIDAD, 2, bitacora);
        planificador.iniciar();

        System.out.println("Se admiten 4 transacciones. La CPU atiende 2 a la vez.");
        System.out.println("Observar: cuando un proceso se BLOQUEA por E/S, otro toma la CPU.\n");

        ProcesoTransaccion[] procesos = {
                new ProcesoTransaccion(ProcesoTransaccion.Tipo.RETIRO,   cuenta, 300_000, dispensador, bitacora),
                new ProcesoTransaccion(ProcesoTransaccion.Tipo.RETIRO,   cuenta, 500_000, dispensador, bitacora),
                new ProcesoTransaccion(ProcesoTransaccion.Tipo.CONSULTA, cuenta, 0,       dispensador, bitacora),
                new ProcesoTransaccion(ProcesoTransaccion.Tipo.DEPOSITO, cuenta, 200_000, dispensador, bitacora),
        };

        for (ProcesoTransaccion p : procesos) {
            planificador.admitir(p);
        }

        // OJO: join() sobre un hilo que todavia NO arranco retorna de inmediato.
        // Como el planificador despacha de a poco, hay que esperar a que cada
        // proceso llegue realmente al estado TERMINADO.
        for (ProcesoTransaccion p : procesos) {
            while (p.getEstado() != EstadoProceso.TERMINADO) {
                Thread.sleep(20);
            }
        }
        planificador.detener();

        System.out.println("\nResultados:");
        for (ProcesoTransaccion p : procesos) {
            System.out.printf("  PID %d  %-9s  %s%n", p.getPid(), p.getTipo(), p.getResultado());
        }
        System.out.println("\nSaldo final: Gs. " + fmt(cuenta.getSaldo()));
    }

    // ------------------------------------------------------------------
    // DEMO 4: los limites del dispensador
    // ------------------------------------------------------------------
    private static void demo4LimitesDelDispensador() {
        titulo("4. DISPENSADOR  (algoritmo voraz y sus limites)");

        Dispensador d = new Dispensador(3, 2, 0);   // 3 de 100.000 y 2 de 50.000
        System.out.println("Casetes: 3 billetes de 100.000 + 2 de 50.000  =  Gs. "
                + fmt(d.efectivoTotal()) + "\n");

        long[] pedidos = { 300_000, 150_000, 30_000, 450_000, 500_000 };
        for (long monto : pedidos) {
            Map<Integer, Integer> plan = d.planificar(monto);
            System.out.printf("  Pedir Gs. %-10s -> ", fmt(monto));
            if (plan == null) {
                System.out.println("RECHAZADO: no se puede formar con los billetes disponibles");
            } else {
                StringBuilder sb = new StringBuilder();
                plan.forEach((den, cant) -> sb.append(cant).append(" x ").append(fmt(den)).append("  "));
                System.out.println(sb.toString().trim());
            }
        }

        System.out.println("\n>>> Gs. 30.000 se rechaza aunque haya saldo: el menor billete es 50.000.");
        System.out.println(">>> Gs. 450.000 se rechaza: no alcanzan los billetes chicos para el resto.");
        System.out.println(">>> Un cajero real falla igual. Modelarlo es parte del trabajo.");
    }

    // ------------------------------------------------------------------
    private static void titulo(String t) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  " + t);
        System.out.println("=".repeat(70));
    }

    private static String fmt(long n) {
        return String.format("%,d", n).replace(',', '.');
    }
}
