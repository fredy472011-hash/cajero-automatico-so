package cajero.aplicacion;

/**
 * Maquina de estados de la interfaz, copiada del flujo real de un cajero
 * Itau Paraguay (relevado con fotos del cajero fisico).
 *
 *   ESPERANDO_TARJETA
 *        | insertar tarjeta
 *        v
 *   PIN  ---- PIN incorrecto ----> ESPERANDO_TARJETA
 *        | PIN correcto
 *        v
 *   VALIDACION_MES          (pantalla "Presione el boton que incluye su mes de nacimiento")
 *        | mes correcto
 *        v
 *   MENU  ---> CONSULTA_SALDO ---> MENU
 *        |
 *        | Extraccion
 *        v
 *   MONTOS ---> OTRO_VALOR ---+
 *        |                    |
 *        +--------------------+
 *        v
 *   PROCESANDO   ("Por favor, aguarde un momento")
 *        v
 *   RECIBO       ("Desea un recibo?"  Imprimir / Pantalla / No)
 *        v
 *   DISPENSANDO  (E/S: el proceso queda BLOQUEADO)
 *        v
 *   FINALIZADO
 *
 * Este enum es el CONTRATO con el frontend: la pantalla que dibuja Elias
 * depende unicamente de este valor.
 */
public enum EstadoPantalla {

    ESPERANDO_TARJETA("Inserte su tarjeta"),
    PIN("Digite su PIN"),
    VALIDACION_MES("Presione el boton que incluye su mes de nacimiento"),
    MENU("Elija una transaccion"),
    MONTOS("Elija el monto"),
    OTRO_VALOR("Ingrese monto"),
    CONSULTA_SALDO("Consultar cuenta"),
    DEPOSITO("Ingrese el monto a depositar"),
    PROCESANDO("Por favor, aguarde un momento"),
    RECIBO("Desea un recibo?"),
    DISPENSANDO("Retire su dinero"),
    FINALIZADO("Gracias por su visita"),
    ERROR("Operacion no disponible");

    private final String titulo;

    EstadoPantalla(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}
