package cajero.aplicacion;

import cajero.dominio.Cuenta;

/**
 * Sesion de un usuario frente al cajero.
 *
 * ESTO ES LA MEMORIA RAM del trabajo: todo lo que vive aca es VOLATIL.
 * Si el usuario cancela, si se acaba el tiempo o si se corta la luz, se
 * pierde completo y no queda rastro.
 *
 * Contrastar con la memoria de LARGO PLAZO (el historial de transacciones y
 * el saldo real de la cuenta), que persiste aunque la sesion desaparezca.
 *
 * Esa distincion es justamente lo que pide la materia.
 */
public class Sesion {

    private final String id;
    private final long creadaEn;

    private Cuenta cuenta;                 // la tarjeta leida
    private boolean pinValidado;
    private boolean mesValidado;
    private EstadoPantalla pantalla = EstadoPantalla.ESPERANDO_TARJETA;
    private int intentosPin;
    private long montoEnCurso;
    private String mensaje = "";

    public Sesion(String id) {
        this.id = id;
        this.creadaEn = System.currentTimeMillis();
    }

    /** Borra todo lo volatil. Simula el corte de energia o la cancelacion. */
    public void limpiar() {
        cuenta = null;
        pinValidado = false;
        mesValidado = false;
        intentosPin = 0;
        montoEnCurso = 0;
        mensaje = "";
        pantalla = EstadoPantalla.ESPERANDO_TARJETA;
    }

    public boolean autenticada() {
        return cuenta != null && pinValidado && mesValidado;
    }

    public long segundosDeVida() {
        return (System.currentTimeMillis() - creadaEn) / 1000;
    }

    // --- acceso ---
    public String getId()                      { return id; }
    public Cuenta getCuenta()                  { return cuenta; }
    public void setCuenta(Cuenta c)            { this.cuenta = c; }
    public boolean isPinValidado()             { return pinValidado; }
    public void setPinValidado(boolean v)      { this.pinValidado = v; }
    public boolean isMesValidado()             { return mesValidado; }
    public void setMesValidado(boolean v)      { this.mesValidado = v; }
    public EstadoPantalla getPantalla()        { return pantalla; }
    public void setPantalla(EstadoPantalla p)  { this.pantalla = p; }
    public int getIntentosPin()                { return intentosPin; }
    public void sumarIntentoPin()              { this.intentosPin++; }
    public long getMontoEnCurso()              { return montoEnCurso; }
    public void setMontoEnCurso(long m)        { this.montoEnCurso = m; }
    public String getMensaje()                 { return mensaje; }
    public void setMensaje(String m)           { this.mensaje = m; }
}
