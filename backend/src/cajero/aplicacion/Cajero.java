package cajero.aplicacion;

import cajero.dominio.Cuenta;
import cajero.hardware.Dispensador;
import cajero.persistencia.RepositorioCuentas;
import cajero.so.EstadoProceso;
import cajero.so.PlanificadorCPU;
import cajero.so.ProcesoTransaccion;
import cajero.so.RegistroActividad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CAPA 4 - LOGICA DE NEGOCIO
 *
 * Es el cerebro del cajero y el UNICO lugar donde se decide que puede y que
 * no puede hacer el usuario. Ninguna regla de negocio vive en el frontend ni
 * en el servidor web: si el navegador manda un pedido fuera de orden, se
 * rechaza aca.
 *
 * Esta clase une las capas de abajo:
 *
 *   dominio      -> Cuenta        (el recurso compartido)
 *   hardware     -> Dispensador   (la entrada/salida lenta)
 *   so           -> Planificador y Procesos
 *   persistencia -> RepositorioCuentas (la memoria de largo plazo)
 *   aplicacion   -> Sesion        (la memoria RAM, volatil)
 *
 * No sabe nada de HTTP ni de HTML. Por eso el mismo Cajero funciona desde
 * consola (Main) o desde el navegador (ServidorCajero).
 */
public class Cajero {

    private static final int MAXIMO_INTENTOS_PIN = 3;

    private final RepositorioCuentas repositorio;
    private final Dispensador dispensador;
    private final PlanificadorCPU planificador;
    private final RegistroActividad bitacora;

    /** Memoria RAM: una sola sesion, porque es un solo cajero fisico. */
    private final Sesion sesion = new Sesion("S-1");

    /** Ultimo proceso lanzado, para poder informar su resultado al frontend. */
    private volatile ProcesoTransaccion ultimoProceso;

    public Cajero(RepositorioCuentas repositorio, Dispensador dispensador,
                  PlanificadorCPU planificador, RegistroActividad bitacora) {
        this.repositorio = repositorio;
        this.dispensador = dispensador;
        this.planificador = planificador;
        this.bitacora = bitacora;
    }

    // ================================================================
    // PASO 1 - insertar la tarjeta
    // ================================================================

    public synchronized void insertarTarjeta(String numero) {
        if (sesion.getPantalla() != EstadoPantalla.ESPERANDO_TARJETA) {
            sesion.setMensaje("El cajero esta ocupado");
            return;
        }

        // Lectura de la memoria de largo plazo: la cuenta existe en MySQL.
        Cuenta cuenta = repositorio.buscarPorNumero(numero);
        if (cuenta == null) {
            sesion.setMensaje("Tarjeta no reconocida");
            bitacora.registrar("CAJERO", "tarjeta rechazada: " + numero);
            return;
        }

        // A partir de aca la cuenta vive en RAM, dentro de la sesion.
        sesion.setCuenta(cuenta);
        sesion.setPantalla(EstadoPantalla.PIN);
        sesion.setMensaje("");
        bitacora.registrar("CAJERO", "tarjeta leida: " + numero + " (" + cuenta.getTitular() + ")");
    }

    // ================================================================
    // PASO 2 - validar el PIN
    // ================================================================

    public synchronized void ingresarPin(String pin) {
        if (sesion.getPantalla() != EstadoPantalla.PIN || sesion.getCuenta() == null) {
            sesion.setMensaje("Primero inserte su tarjeta");
            return;
        }

        if (sesion.getCuenta().pinCorrecto(pin)) {
            sesion.setPinValidado(true);
            sesion.setPantalla(EstadoPantalla.VALIDACION_MES);
            sesion.setMensaje("");
            bitacora.registrar("CAJERO", "PIN correcto");
            return;
        }

        sesion.sumarIntentoPin();
        if (sesion.getIntentosPin() >= MAXIMO_INTENTOS_PIN) {
            bitacora.registrar("CAJERO", "tarjeta retenida: 3 PIN incorrectos");
            sesion.limpiar();
            sesion.setMensaje("Tarjeta retenida por seguridad");
            return;
        }
        sesion.setMensaje("PIN incorrecto. Intento "
                + sesion.getIntentosPin() + " de " + MAXIMO_INTENTOS_PIN);
    }

    // ================================================================
    // PASO 3 - validar el mes de nacimiento (segunda barrera del cajero real)
    // ================================================================

    public synchronized void ingresarMes(int mes) {
        if (sesion.getPantalla() != EstadoPantalla.VALIDACION_MES) {
            sesion.setMensaje("Paso no valido");
            return;
        }

        if (sesion.getCuenta().mesCorrecto(mes)) {
            sesion.setMesValidado(true);
            sesion.setPantalla(EstadoPantalla.MENU);
            sesion.setMensaje("");
            bitacora.registrar("CAJERO", "mes de nacimiento validado");
        } else {
            bitacora.registrar("CAJERO", "mes de nacimiento incorrecto, sesion cancelada");
            sesion.limpiar();
            sesion.setMensaje("Datos incorrectos. Retire su tarjeta");
        }
    }

    // ================================================================
    // PASO 4 - elegir la operacion
    // ================================================================

    public synchronized void elegirOperacion(String operacion) {
        if (!sesion.autenticada()) {
            sesion.setMensaje("Sesion no autenticada");
            return;
        }

        switch (operacion.toUpperCase()) {
            case "EXTRACCION" -> sesion.setPantalla(EstadoPantalla.MONTOS);
            case "DEPOSITO"   -> sesion.setPantalla(EstadoPantalla.DEPOSITO);
            case "SALDO"      -> ejecutarConsulta();
            default           -> sesion.setMensaje("Operacion no disponible");
        }
    }

    // ================================================================
    // PASO 5 - ejecutar. Aca nacen los PROCESOS del sistema operativo.
    // ================================================================

    /**
     * Consulta de saldo. No usa el dispensador, asi que el planificador por
     * prioridad la atiende antes que un retiro.
     */
    private void ejecutarConsulta() {
        ProcesoTransaccion p = lanzar(ProcesoTransaccion.Tipo.CONSULTA, 0);
        esperar(p);
        sesion.setPantalla(EstadoPantalla.CONSULTA_SALDO);
        sesion.setMensaje(p.getResultado());

        repositorio.registrarTransaccion(sesion.getCuenta().getNumero(), "CONSULTA", 0,
                sesion.getCuenta().getSaldo(), true, "consulta de saldo", p.getPid());
    }

    /**
     * Retiro. Es el camino largo y el que demuestra todos los temas:
     * proceso, cambio de estado, E/S bloqueante y escritura persistente.
     */
    public synchronized void retirar(long monto) {
        if (!sesion.autenticada()) {
            sesion.setMensaje("Sesion no autenticada");
            return;
        }
        if (monto <= 0 || monto % 50_000 != 0) {
            sesion.setPantalla(EstadoPantalla.ERROR);
            sesion.setMensaje("El monto debe ser multiplo de 50.000");
            return;
        }

        sesion.setMontoEnCurso(monto);
        sesion.setPantalla(EstadoPantalla.PROCESANDO);

        ProcesoTransaccion p = lanzar(ProcesoTransaccion.Tipo.RETIRO, monto);
        esperar(p);

        boolean exitosa = p.getResultado().startsWith("Entregado");
        Cuenta cuenta = sesion.getCuenta();

        // ESCRITURA EN MEMORIA DE LARGO PLAZO.
        // Hasta esta linea todo el movimiento vivia solo en RAM; un corte de
        // energia lo habria borrado. Despues de esta linea, ya no.
        if (exitosa) {
            repositorio.actualizarSaldo(cuenta.getNumero(), cuenta.getSaldo());
            guardarCasetes();
            sesion.setPantalla(EstadoPantalla.DISPENSANDO);
        } else {
            sesion.setPantalla(EstadoPantalla.ERROR);
        }

        repositorio.registrarTransaccion(cuenta.getNumero(), "RETIRO", monto,
                cuenta.getSaldo(), exitosa, p.getResultado(), p.getPid());

        sesion.setMensaje(p.getResultado());
    }

    public synchronized void depositar(long monto) {
        if (!sesion.autenticada()) {
            sesion.setMensaje("Sesion no autenticada");
            return;
        }
        if (monto <= 0) {
            sesion.setMensaje("Monto no valido");
            return;
        }

        sesion.setMontoEnCurso(monto);
        sesion.setPantalla(EstadoPantalla.PROCESANDO);

        ProcesoTransaccion p = lanzar(ProcesoTransaccion.Tipo.DEPOSITO, monto);
        esperar(p);

        Cuenta cuenta = sesion.getCuenta();
        boolean exitosa = p.getResultado().startsWith("Deposito de");

        if (exitosa) {
            repositorio.actualizarSaldo(cuenta.getNumero(), cuenta.getSaldo());
        }
        repositorio.registrarTransaccion(cuenta.getNumero(), "DEPOSITO", monto,
                cuenta.getSaldo(), exitosa, p.getResultado(), p.getPid());

        sesion.setPantalla(EstadoPantalla.RECIBO);
        sesion.setMensaje(p.getResultado());
    }

    // ================================================================
    // PASO 6 - cerrar
    // ================================================================

    public synchronized void finalizar() {
        sesion.setPantalla(EstadoPantalla.FINALIZADO);
        sesion.setMensaje("Gracias por su visita");
    }

    /**
     * Cancelar: simula el corte de energia o el boton CANCELAR.
     * Borra TODA la memoria volatil. El saldo en MySQL queda intacto,
     * y ahi se ve la diferencia entre los dos tipos de memoria.
     */
    public synchronized void cancelar() {
        bitacora.registrar("CAJERO", "sesion cancelada: se borra la memoria volatil");
        sesion.limpiar();
    }

    public synchronized void volverAlMenu() {
        if (sesion.autenticada()) {
            sesion.setPantalla(EstadoPantalla.MENU);
            sesion.setMensaje("");
        }
    }

    // ================================================================
    // Soporte interno
    // ================================================================

    /** Crea el proceso y lo entrega al planificador, que decide cuando corre. */
    private ProcesoTransaccion lanzar(ProcesoTransaccion.Tipo tipo, long monto) {
        ProcesoTransaccion p = new ProcesoTransaccion(
                tipo, sesion.getCuenta(), monto, dispensador, bitacora);
        ultimoProceso = p;
        planificador.admitir(p);
        return p;
    }

    /**
     * Espera a que el proceso termine.
     *
     * No se puede usar Thread.join() aca: el planificador todavia no llamo a
     * start(), y join() sobre un hilo no iniciado vuelve de inmediato. Hay
     * que esperar el estado TERMINADO, que es el verdadero fin del proceso.
     */
    private void esperar(ProcesoTransaccion p) {
        long limite = System.currentTimeMillis() + 15_000;
        while (p.getEstado() != EstadoProceso.TERMINADO) {
            if (System.currentTimeMillis() > limite) {
                bitacora.registrar("CAJERO", "tiempo agotado esperando al proceso " + p.getPid());
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** El cajero no se olvida cuantos billetes le quedan al apagarse. */
    private void guardarCasetes() {
        dispensador.estadoCasetes().forEach(repositorio::guardarCasete);
    }

    // ================================================================
    // Lectura de estado: lo que el frontend dibuja
    // ================================================================

    public Sesion getSesion()              { return sesion; }
    public Dispensador getDispensador()    { return dispensador; }
    public PlanificadorCPU getPlanificador() { return planificador; }
    public RegistroActividad getBitacora() { return bitacora; }

    public List<String> movimientos() {
        if (sesion.getCuenta() == null) return new ArrayList<>();
        return repositorio.ultimosMovimientos(sesion.getCuenta().getNumero(), 5);
    }

    /** Montos del menu rapido, segun lo que el dispensador puede formar hoy. */
    public List<Long> montosDisponibles() {
        List<Long> montos = new ArrayList<>();
        for (long m : new long[] { 100_000, 200_000, 400_000, 500_000, 1_000_000 }) {
            Map<Integer, Integer> plan = dispensador.planificar(m);
            if (plan != null) montos.add(m);
        }
        return montos;
    }
}
