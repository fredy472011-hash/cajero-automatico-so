package cajero.so;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Planificador de CPU. Administra la cola de procesos listos y decide
 * cual se ejecuta.
 *
 * Soporta dos politicas, para poder compararlas en el informe:
 *
 *   FIFO      - primero en llegar, primero en ser atendido
 *   PRIORIDAD - las consultas (que no usan el dispensador) van antes
 *
 * El 'gradoDeMultiprogramacion' limita cuantos procesos corren a la vez,
 * igual que un sistema real no ejecuta procesos infinitos en paralelo.
 */
public class PlanificadorCPU {

    public enum Politica { FIFO, PRIORIDAD }

    private final PriorityBlockingQueue<ProcesoTransaccion> colaListos;
    private final List<ProcesoTransaccion> historial = new ArrayList<>();
    private final RegistroActividad bitacora;
    private final Politica politica;
    private final int gradoDeMultiprogramacion;

    private volatile boolean activo = true;

    public PlanificadorCPU(Politica politica, int gradoDeMultiprogramacion, RegistroActividad bitacora) {
        this.politica = politica;
        this.gradoDeMultiprogramacion = gradoDeMultiprogramacion;
        this.bitacora = bitacora;

        Comparator<ProcesoTransaccion> criterio = (politica == Politica.PRIORIDAD)
                ? Comparator.comparingInt(ProcesoTransaccion::getPrioridad)
                             .thenComparingInt(ProcesoTransaccion::getPid)
                : Comparator.comparingInt(ProcesoTransaccion::getPid);

        this.colaListos = new PriorityBlockingQueue<>(16, criterio);
    }

    /** Admite un proceso: pasa de NUEVO a LISTO y entra en la cola. */
    public void admitir(ProcesoTransaccion p) {
        p.admitir();
        colaListos.offer(p);
        synchronized (historial) {
            historial.add(p);
        }
    }

    /**
     * Despachador: saca procesos de la cola y les da CPU, respetando el
     * grado de multiprogramacion.
     */
    public void iniciar() {
        Thread despachador = new Thread(() -> {
            bitacora.registrar("PLANIFICADOR", "iniciado  politica=" + politica
                    + "  multiprogramacion=" + gradoDeMultiprogramacion);
            while (activo) {
                try {
                    if (enEjecucion() < gradoDeMultiprogramacion) {
                        ProcesoTransaccion p = colaListos.poll();
                        if (p != null) {
                            p.start();
                        } else {
                            Thread.sleep(50);
                        }
                    } else {
                        Thread.sleep(20);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Despachador");
        despachador.setDaemon(true);
        despachador.start();
    }

    public void detener() {
        activo = false;
    }

    private int enEjecucion() {
        synchronized (historial) {
            return (int) historial.stream()
                    .filter(p -> p.getEstado() == EstadoProceso.EJECUTANDO
                              || p.getEstado() == EstadoProceso.BLOQUEADO)
                    .count();
        }
    }

    public List<ProcesoTransaccion> getHistorial() {
        synchronized (historial) {
            return new ArrayList<>(historial);
        }
    }

    public int enCola() {
        return colaListos.size();
    }

    public Politica getPolitica() {
        return politica;
    }
}
