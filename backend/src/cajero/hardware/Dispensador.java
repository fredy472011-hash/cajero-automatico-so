package cajero.hardware;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dispensador de billetes: el DISPOSITIVO DE ENTRADA/SALIDA del cajero.
 *
 * Datos tomados de un cajero real (Itau Paraguay): la pantalla "Billetes
 * disponibles" muestra solo 50.000 y 100.000. Un cajero fisico tiene pocos
 * casetes, no uno por cada denominacion que existe.
 *
 * Por eso hay montos que NO se pueden formar aunque haya saldo suficiente,
 * y eso hay que modelarlo: es la diferencia entre un trabajo pensado y uno
 * que solo resta numeros.
 */
public class Dispensador {

    /** Denominaciones de mayor a menor. El orden importa para el algoritmo voraz. */
    private static final int[] DENOMINACIONES = { 100_000, 50_000 };

    /** Casetes: denominacion -> cantidad de billetes disponibles. */
    private final Map<Integer, Integer> casetes = new LinkedHashMap<>();

    /** Milisegundos que tarda en expulsar los billetes (E/S lenta). */
    private final long demoraMs;

    public Dispensador(int billetes100k, int billetes50k, long demoraMs) {
        casetes.put(100_000, billetes100k);
        casetes.put(50_000, billetes50k);
        this.demoraMs = demoraMs;
    }

    /**
     * Calcula que billetes entregar, SIN descontarlos todavia.
     * Algoritmo voraz: toma primero la denominacion mas alta posible.
     *
     * @return el desglose, o null si el monto no se puede formar
     */
    public synchronized Map<Integer, Integer> planificar(long monto) {
        if (monto <= 0) return null;

        Map<Integer, Integer> plan = new LinkedHashMap<>();
        long restante = monto;

        for (int den : DENOMINACIONES) {
            int disponibles = casetes.getOrDefault(den, 0);
            int necesarios = (int) (restante / den);
            int usar = Math.min(necesarios, disponibles);
            if (usar > 0) {
                plan.put(den, usar);
                restante -= (long) usar * den;
            }
        }

        // Si sobra resto, el monto no se puede armar con lo que hay.
        // Ejemplos reales: pedir 30.000 (ninguna combinacion lo forma),
        // o pedir 150.000 cuando ya no quedan billetes de 50.000.
        return restante == 0 ? plan : null;
    }

    /**
     * Entrega fisica. Es la operacion de E/S: BLOQUEA al proceso que la llama
     * durante demoraMs. Mientras tanto, el planificador puede dar la CPU a otro.
     */
    public Map<Integer, Integer> dispensar(long monto) throws InterruptedException {
        Map<Integer, Integer> plan;

        // Seccion critica corta: reservar los billetes.
        synchronized (this) {
            plan = planificar(monto);
            if (plan == null) return null;
            plan.forEach((den, cant) -> casetes.merge(den, -cant, Integer::sum));
        }

        // La demora va FUERA del synchronized: si bloqueara el candado,
        // ningun otro proceso podria ni consultar el dispensador.
        Thread.sleep(demoraMs);
        return plan;
    }

    public synchronized Map<Integer, Integer> estadoCasetes() {
        return new LinkedHashMap<>(casetes);
    }

    public synchronized long efectivoTotal() {
        return casetes.entrySet().stream()
                .mapToLong(e -> (long) e.getKey() * e.getValue())
                .sum();
    }

    public static int[] denominaciones() {
        return DENOMINACIONES.clone();
    }
}
