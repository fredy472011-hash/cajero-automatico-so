package cajero.dominio;

/**
 * Cuenta bancaria. Es el RECURSO COMPARTIDO del sistema: varios cajeros
 * (varios hilos) pueden operar sobre la misma cuenta al mismo tiempo.
 *
 * Aca vive la demostracion central del trabajo: la condicion de carrera.
 *
 * El metodo retirar() tiene DOS versiones a proposito:
 *
 *   retirarSinSincronizar()  -> permite que el saldo quede negativo
 *   retirar()                -> correcto, con synchronized
 *
 * Mostrar el error primero y despues corregirlo es lo que demuestra que se
 * entendio el problema, no solo que se escribio la solucion.
 */
public class Cuenta {

    private final String numero;
    private final String titular;
    private final String pin;
    private final int mesNacimiento;   // 1 a 12, para la validacion extra
    private long saldo;

    public Cuenta(String numero, String titular, String pin, int mesNacimiento, long saldoInicial) {
        this.numero = numero;
        this.titular = titular;
        this.pin = pin;
        this.mesNacimiento = mesNacimiento;
        this.saldo = saldoInicial;
    }

    // ---------- version INSEGURA: para demostrar la condicion de carrera ----------

    /**
     * Retiro SIN sincronizar. Entre leer el saldo y escribirlo hay una ventana
     * donde otro hilo puede leer el mismo valor viejo.
     *
     * El Thread.sleep() solo ensancha esa ventana para que la falla sea
     * reproducible; el error existe igual sin el.
     */
    public boolean retirarSinSincronizar(long monto) {
        if (saldo >= monto) {              // 1. LEER
            dormir(50);                    //    <- otro hilo entra aca
            saldo = saldo - monto;         // 2. ESCRIBIR
            return true;
        }
        return false;
    }

    // ---------- version CORRECTA ----------

    /**
     * Retiro sincronizado. 'synchronized' garantiza que un solo hilo a la vez
     * ejecute este metodo sobre esta instancia, asi que leer y escribir se
     * vuelven una operacion indivisible (atomica).
     */
    public synchronized boolean retirar(long monto) {
        if (monto <= 0) return false;
        if (saldo < monto) return false;
        saldo = saldo - monto;
        return true;
    }

    public synchronized boolean depositar(long monto) {
        if (monto <= 0) return false;
        saldo = saldo + monto;
        return true;
    }

    public synchronized long getSaldo() {
        return saldo;
    }

    public boolean pinCorrecto(String intento) {
        return pin.equals(intento);
    }

    public boolean mesCorrecto(int mes) {
        return mesNacimiento == mes;
    }

    public String getNumero()  { return numero; }
    public String getTitular() { return titular; }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
