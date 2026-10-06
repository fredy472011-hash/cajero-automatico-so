package cajero.so;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bitacora del sistema. Registra cada cambio de estado de los procesos
 * para que se pueda observar el comportamiento del sistema operativo.
 *
 * Es compartida por todos los hilos, asi que todos sus metodos estan
 * sincronizados: sin eso, dos procesos escribiendo a la vez corromperian
 * la lista.
 */
public final class RegistroActividad {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAXIMO = 300;

    /** Cola de tamanio acotado: las entradas viejas se descartan. */
    private final Deque<String> entradas = new ArrayDeque<>();

    public synchronized void registrar(String proceso, String mensaje) {
        String linea = "[%s] [%s] %s".formatted(LocalTime.now().format(HORA), proceso, mensaje);
        entradas.addLast(linea);
        if (entradas.size() > MAXIMO) {
            entradas.removeFirst();
        }
        System.out.println(linea);
    }

    public synchronized void cambioDeEstado(String proceso, EstadoProceso desde, EstadoProceso hacia, String motivo) {
        registrar(proceso, "%s -> %s   (%s)".formatted(desde, hacia, motivo));
    }

    /** Copia defensiva: quien la reciba no puede modificar la lista interna. */
    public synchronized List<String> ultimas(int cantidad) {
        List<String> todas = new ArrayList<>(entradas);
        int desde = Math.max(0, todas.size() - cantidad);
        return new ArrayList<>(todas.subList(desde, todas.size()));
    }
}
