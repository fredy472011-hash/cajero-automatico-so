package cajero.web;

import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

/**
 * CAPA 5 - Armador de JSON minimo, escrito a mano.
 *
 * El proyecto no usa librerias externas (ni Jackson, ni Gson, ni Spring):
 * solo el JDK. Para devolver JSON hace falta entonces armar el texto, y eso
 * se hace aca en un solo lugar en vez de repartir comillas por todo el codigo.
 *
 * Lo importante es escapar: si el titular se llamara  Ana "La" Paz  y no se
 * escapara la comilla, el JSON quedaria roto y el frontend fallaria.
 */
final class Json {

    private Json() { }

    /** Objeto JSON a partir de pares clave/valor ya formateados. */
    static String objeto(Map<String, String> campos) {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        campos.forEach((k, v) -> sj.add(texto(k) + ":" + v));
        return sj.toString();
    }

    /** Arreglo de cadenas, cada una escapada. */
    static String arregloDeTextos(Collection<String> valores) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        valores.forEach(v -> sj.add(texto(v)));
        return sj.toString();
    }

    /** Arreglo de fragmentos JSON ya armados (objetos, numeros). */
    static String arreglo(Collection<String> fragmentos) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        fragmentos.forEach(sj::add);
        return sj.toString();
    }

    /** Cadena JSON, con los caracteres especiales escapados. */
    static String texto(String valor) {
        if (valor == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append("\\u%04x".formatted((int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    static String numero(long valor) {
        return Long.toString(valor);
    }

    static String booleano(boolean valor) {
        return Boolean.toString(valor);
    }
}
