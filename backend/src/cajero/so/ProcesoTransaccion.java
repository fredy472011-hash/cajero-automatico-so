package cajero.so;

import cajero.hardware.Dispensador;
import cajero.dominio.Cuenta;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Una transaccion del cajero modelada como PROCESO del sistema operativo.
 *
 * Extiende Thread para que cada transaccion sea un hilo real: asi la
 * concurrencia no esta simulada, ocurre de verdad.
 *
 * Recorre los estados NUEVO -> LISTO -> EJECUTANDO -> BLOQUEADO -> TERMINADO
 * y deja cada transicion en la bitacora.
 */
public class ProcesoTransaccion extends Thread {

    public enum Tipo { RETIRO, DEPOSITO, CONSULTA }

    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    private final int pid;
    private final Tipo tipo;
    private final Cuenta cuenta;
    private final long monto;
    private final Dispensador dispensador;
    private final RegistroActividad bitacora;

    private volatile EstadoProceso estado = EstadoProceso.NUEVO;
    private volatile String resultado = "";
    private final int prioridad;

    public ProcesoTransaccion(Tipo tipo, Cuenta cuenta, long monto,
                              Dispensador dispensador, RegistroActividad bitacora) {
        this.pid = SECUENCIA.getAndIncrement();
        this.tipo = tipo;
        this.cuenta = cuenta;
        this.monto = monto;
        this.dispensador = dispensador;
        this.bitacora = bitacora;
        // Una consulta no toca el hardware: puede resolverse antes. Prioridad mas alta.
        this.prioridad = (tipo == Tipo.CONSULTA) ? 1 : 5;
        setName("Proceso-" + pid);
    }

    private void cambiarEstado(EstadoProceso nuevo, String motivo) {
        EstadoProceso anterior = this.estado;
        this.estado = nuevo;
        bitacora.cambioDeEstado(getName(), anterior, nuevo, motivo);
    }

    /** Lo llama el planificador cuando admite el proceso en la cola de listos. */
    public void admitir() {
        cambiarEstado(EstadoProceso.LISTO, "admitido en la cola de listos");
    }

    @Override
    public void run() {
        cambiarEstado(EstadoProceso.EJECUTANDO, "CPU asignada");
        try {
            switch (tipo) {
                case CONSULTA -> ejecutarConsulta();
                case DEPOSITO -> ejecutarDeposito();
                case RETIRO   -> ejecutarRetiro();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultado = "Interrumpido";
        }
        cambiarEstado(EstadoProceso.TERMINADO, resultado);
    }

    private void ejecutarConsulta() {
        resultado = "Saldo: Gs. " + formato(cuenta.getSaldo());
    }

    private void ejecutarDeposito() throws InterruptedException {
        bloqueadoPorES("deposito: validando billetes recibidos", 1200);
        if (cuenta.depositar(monto)) {
            resultado = "Deposito de Gs. " + formato(monto) + " acreditado";
        } else {
            resultado = "Deposito rechazado";
        }
    }

    private void ejecutarRetiro() throws InterruptedException {
        // 1. Debitar de la cuenta. Seccion critica: la cuenta es compartida.
        if (!cuenta.retirar(monto)) {
            resultado = "Saldo insuficiente";
            return;
        }

        // 2. Entregar el efectivo. Esto es E/S: el proceso se bloquea.
        cambiarEstado(EstadoProceso.BLOQUEADO, "solicita E/S: dispensador");
        Map<Integer, Integer> billetes = dispensador.dispensar(monto);
        cambiarEstado(EstadoProceso.LISTO, "E/S completada");
        cambiarEstado(EstadoProceso.EJECUTANDO, "CPU reasignada");

        if (billetes == null) {
            // El dispensador no pudo formar el monto: hay que devolver la plata.
            cuenta.depositar(monto);
            resultado = "No hay billetes para formar Gs. " + formato(monto) + " (reintegrado)";
            return;
        }

        StringBuilder sb = new StringBuilder("Entregado: ");
        billetes.forEach((den, cant) -> sb.append(cant).append(" x ").append(formato(den)).append("  "));
        resultado = sb.toString().trim();
    }

    /** Simula una operacion de entrada/salida que bloquea al proceso. */
    private void bloqueadoPorES(String motivo, long ms) throws InterruptedException {
        cambiarEstado(EstadoProceso.BLOQUEADO, motivo);
        Thread.sleep(ms);
        cambiarEstado(EstadoProceso.LISTO, "E/S completada");
        cambiarEstado(EstadoProceso.EJECUTANDO, "CPU reasignada");
    }

    private static String formato(long n) {
        return String.format("%,d", n).replace(',', '.');
    }

    public int getPid()                { return pid; }
    public Tipo getTipo()              { return tipo; }
    public EstadoProceso getEstado()   { return estado; }
    public String getResultado()       { return resultado; }
    public int getPrioridad()          { return prioridad; }
    public long getMonto()             { return monto; }
}
