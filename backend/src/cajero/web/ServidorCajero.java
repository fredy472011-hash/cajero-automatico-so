package cajero.web;

import cajero.aplicacion.Cajero;
import cajero.aplicacion.EstadoPantalla;
import cajero.aplicacion.Sesion;
import cajero.dominio.Cuenta;
import cajero.so.ProcesoTransaccion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * CAPA 5 - PRESENTACION (servidor web)
 *
 * Expone el cajero por HTTP para que el frontend (HTML/CSS/JS) lo maneje.
 *
 * Usa com.sun.net.httpserver.HttpServer, que viene DENTRO del JDK desde
 * Java 6 (modulo jdk.httpserver). No hay Spring, ni Tomcat, ni un solo .jar
 * descargado: el requisito era Java puro, y esto lo cumple.
 *
 * Esta capa NO tiene reglas de negocio. Solo traduce:
 *
 *    pedido HTTP  ->  llamada a Cajero  ->  respuesta JSON
 *
 * Si el navegador manda un pedido fuera de orden, quien lo rechaza es la
 * capa 4 (Cajero), no esta.
 *
 * API:
 *   POST /api/tarjeta    numero=1001
 *   POST /api/pin        pin=1234
 *   POST /api/mes        mes=5
 *   POST /api/operacion  operacion=EXTRACCION|DEPOSITO|SALDO
 *   POST /api/monto      monto=200000
 *   POST /api/deposito   monto=150000
 *   POST /api/cancelar
 *   POST /api/menu
 *   POST /api/finalizar
 *   GET  /api/estado     pantalla, titular, saldo, mensaje, montos
 *   GET  /api/procesos   tabla de procesos del sistema operativo
 *   GET  /api/bitacora   ultimas lineas del registro
 */
public class ServidorCajero {

    private final Cajero cajero;
    private final int puerto;
    private final Path raizFrontend;
    private HttpServer servidor;

    public ServidorCajero(Cajero cajero, int puerto, Path raizFrontend) {
        this.cajero = cajero;
        this.puerto = puerto;
        this.raizFrontend = raizFrontend;
    }

    public void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);

        // Archivos del frontend
        servidor.createContext("/", this::servirEstatico);

        // API
        servidor.createContext("/api/tarjeta",   e -> conFormulario(e, d -> cajero.insertarTarjeta(d.getOrDefault("numero", ""))));
        servidor.createContext("/api/pin",       e -> conFormulario(e, d -> cajero.ingresarPin(d.getOrDefault("pin", ""))));
        servidor.createContext("/api/mes",       e -> conFormulario(e, d -> cajero.ingresarMes(entero(d.get("mes")))));
        servidor.createContext("/api/operacion", e -> conFormulario(e, d -> cajero.elegirOperacion(d.getOrDefault("operacion", ""))));
        servidor.createContext("/api/monto",     e -> conFormulario(e, d -> cajero.retirar(largo(d.get("monto")))));
        servidor.createContext("/api/deposito",  e -> conFormulario(e, d -> cajero.depositar(largo(d.get("monto")))));
        servidor.createContext("/api/cancelar",  e -> conFormulario(e, d -> cajero.cancelar()));
        servidor.createContext("/api/menu",      e -> conFormulario(e, d -> cajero.volverAlMenu()));
        servidor.createContext("/api/finalizar", e -> conFormulario(e, d -> cajero.finalizar()));

        servidor.createContext("/api/estado",    e -> responderJson(e, 200, estadoJson()));
        servidor.createContext("/api/procesos",  e -> responderJson(e, 200, procesosJson()));
        servidor.createContext("/api/bitacora",  e -> responderJson(e, 200,
                Json.arregloDeTextos(cajero.getBitacora().ultimas(40))));

        // El servidor necesita VARIOS hilos. Con uno solo, mientras el retiro
        // esta bloqueado en el dispensador (1,8 s de E/S) los pedidos de
        // /api/estado quedarian encolados y el panel se congelaria justo en el
        // momento que hay que observar: el proceso pasando a BLOQUEADO.
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        servidor.start();

        System.out.println();
        System.out.println("  Cajero disponible en  http://localhost:" + puerto);
        System.out.println("  Frontend servido desde " + raizFrontend.toAbsolutePath());
        System.out.println("  Ctrl+C para detener");
        System.out.println();
    }

    public void detener() {
        if (servidor != null) servidor.stop(0);
    }

    // ================================================================
    // Acciones
    // ================================================================

    /** Una accion del cajero: recibe los datos del formulario y no devuelve nada. */
    private interface Accion {
        void ejecutar(Map<String, String> datos);
    }

    /**
     * Ejecuta la accion y devuelve SIEMPRE el estado nuevo. Asi el frontend
     * hace un solo pedido por paso y no tiene que preguntar el estado despues.
     */
    private void conFormulario(HttpExchange intercambio, Accion accion) throws IOException {
        if (!"POST".equals(intercambio.getRequestMethod())) {
            responderJson(intercambio, 405, "{\"error\":\"use POST\"}");
            return;
        }
        try {
            Map<String, String> datos = leerFormulario(intercambio.getRequestBody());
            accion.ejecutar(datos);
            responderJson(intercambio, 200, estadoJson());
        } catch (RuntimeException ex) {
            responderJson(intercambio, 400, Json.objeto(Map.of("error", Json.texto(String.valueOf(ex.getMessage())))));
        }
    }

    /**
     * Lee el cuerpo en formato  clave=valor&clave=valor  (el mismo que manda
     * un formulario HTML). Se eligio eso en lugar de JSON porque leer JSON
     * sin libreria obligaria a escribir un parser completo, y el valor
     * didactico del trabajo esta en los procesos, no en eso.
     */
    private static Map<String, String> leerFormulario(InputStream cuerpo) throws IOException {
        String texto = new String(cuerpo.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> datos = new HashMap<>();
        if (texto.isBlank()) return datos;

        for (String par : texto.split("&")) {
            int igual = par.indexOf('=');
            if (igual <= 0) continue;
            String clave  = URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8);
            String valor  = URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8);
            datos.put(clave, valor);
        }
        return datos;
    }

    // ================================================================
    // Estado que consume el frontend
    // ================================================================

    private String estadoJson() {
        Sesion sesion = cajero.getSesion();
        Cuenta cuenta = sesion.getCuenta();
        EstadoPantalla pantalla = sesion.getPantalla();

        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("pantalla",  Json.texto(pantalla.name()));
        campos.put("titulo",    Json.texto(pantalla.getTitulo()));
        campos.put("mensaje",   Json.texto(sesion.getMensaje()));
        campos.put("titular",   Json.texto(cuenta == null ? "" : cuenta.getTitular()));
        campos.put("cuenta",    Json.texto(cuenta == null ? "" : cuenta.getNumero()));
        campos.put("saldo",     Json.numero(cuenta == null ? 0 : cuenta.getSaldo()));
        campos.put("autenticada", Json.booleano(sesion.autenticada()));
        campos.put("intentosPin", Json.numero(sesion.getIntentosPin()));
        campos.put("montoEnCurso", Json.numero(sesion.getMontoEnCurso()));

        // Montos que el dispensador realmente puede formar ahora mismo
        List<String> montos = new ArrayList<>();
        cajero.montosDisponibles().forEach(m -> montos.add(Json.numero(m)));
        campos.put("montos", Json.arreglo(montos));

        // Casetes: lo que hay en el cajero fisico
        List<String> casetes = new ArrayList<>();
        cajero.getDispensador().estadoCasetes().forEach((den, cant) ->
                casetes.add(Json.objeto(new LinkedHashMap<>(Map.of(
                        "denominacion", Json.numero(den),
                        "cantidad",     Json.numero(cant))))));
        campos.put("casetes", Json.arreglo(casetes));
        campos.put("efectivoTotal", Json.numero(cajero.getDispensador().efectivoTotal()));

        campos.put("movimientos", Json.arregloDeTextos(cajero.movimientos()));
        return Json.objeto(campos);
    }

    private String procesosJson() {
        List<String> filas = new ArrayList<>();
        for (ProcesoTransaccion p : cajero.getPlanificador().getHistorial()) {
            Map<String, String> fila = new LinkedHashMap<>();
            fila.put("pid",        Json.numero(p.getPid()));
            fila.put("tipo",       Json.texto(p.getTipo().name()));
            fila.put("estado",     Json.texto(p.getEstado().name()));
            fila.put("prioridad",  Json.numero(p.getPrioridad()));
            fila.put("monto",      Json.numero(p.getMonto()));
            fila.put("resultado",  Json.texto(p.getResultado()));
            filas.add(Json.objeto(fila));
        }

        Map<String, String> raiz = new LinkedHashMap<>();
        raiz.put("politica", Json.texto(cajero.getPlanificador().getPolitica().name()));
        raiz.put("enCola",   Json.numero(cajero.getPlanificador().enCola()));
        raiz.put("procesos", Json.arreglo(filas));
        return Json.objeto(raiz);
    }

    // ================================================================
    // Archivos estaticos
    // ================================================================

    private void servirEstatico(HttpExchange intercambio) throws IOException {
        String pedido = intercambio.getRequestURI().getPath();
        if (pedido.equals("/") || pedido.isEmpty()) pedido = "/index.html";

        // Normalizar y comprobar que no se escape de la carpeta del frontend.
        // Sin esto, un pedido  /../../etc/passwd  leeria archivos del sistema.
        Path archivo = raizFrontend.resolve(pedido.substring(1)).normalize();
        if (!archivo.startsWith(raizFrontend.normalize()) || !Files.isRegularFile(archivo)) {
            responder(intercambio, 404, "text/plain; charset=utf-8",
                    "No encontrado: ".concat(pedido).getBytes(StandardCharsets.UTF_8));
            return;
        }

        responder(intercambio, 200, tipoDeContenido(archivo), Files.readAllBytes(archivo));
    }

    private static String tipoDeContenido(Path archivo) {
        String nombre = archivo.getFileName().toString().toLowerCase();
        if (nombre.endsWith(".html")) return "text/html; charset=utf-8";
        if (nombre.endsWith(".css"))  return "text/css; charset=utf-8";
        if (nombre.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (nombre.endsWith(".svg"))  return "image/svg+xml";
        if (nombre.endsWith(".png"))  return "image/png";
        return "application/octet-stream";
    }

    // ================================================================
    // Utilidades de respuesta
    // ================================================================

    private static void responderJson(HttpExchange intercambio, int codigo, String json) throws IOException {
        responder(intercambio, codigo, "application/json; charset=utf-8",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static void responder(HttpExchange intercambio, int codigo, String tipo, byte[] cuerpo) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private static int entero(String valor) {
        try {
            return Integer.parseInt(valor == null ? "0" : valor.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long largo(String valor) {
        try {
            return Long.parseLong(valor == null ? "0" : valor.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
