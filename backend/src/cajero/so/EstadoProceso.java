package cajero.so;

/**
 * Estados por los que pasa un proceso en un sistema operativo.
 * Cada transaccion del cajero es un proceso y recorre estos estados.
 *
 *   NUEVO -> LISTO -> EJECUTANDO -> TERMINADO
 *                 ^        |
 *                 |        v
 *                 +--- BLOQUEADO   (esperando entrada/salida)
 */
public enum EstadoProceso {

    /** Creado pero todavia no admitido en la cola de listos. */
    NUEVO,

    /** En la cola, esperando que el planificador le asigne la CPU. */
    LISTO,

    /** Tiene la CPU y esta ejecutando instrucciones. */
    EJECUTANDO,

    /** Espera una operacion de entrada/salida. NO consume CPU. */
    BLOQUEADO,

    /** Termino. Sus recursos pueden liberarse. */
    TERMINADO
}
