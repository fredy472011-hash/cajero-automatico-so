package cajero.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * CAPA 3 - MEMORIA DE LARGO PLAZO
 *
 * Provee la conexion a MySQL. Todo lo que pasa por aca SOBREVIVE:
 * al apagado del cajero, a una cancelacion y a un corte de energia.
 *
 * Contraste directo con cajero.aplicacion.Sesion, que vive en RAM y
 * se pierde apenas el usuario cancela. Esa diferencia es justamente
 * lo que pide la materia.
 *
 * Las credenciales se leen de variables de entorno; nunca van escritas
 * en el codigo.
 */
public final class BaseDeDatos {

    private static final String URL = System.getenv().getOrDefault(
            "DB_URL", "jdbc:mysql://localhost:3306/cajero?useSSL=false&serverTimezone=UTC");
    private static final String USUARIO = System.getenv().getOrDefault("DB_USER", "root");
    private static final String CLAVE   = System.getenv().getOrDefault("DB_PASSWORD", "root");

    private BaseDeDatos() { }

    public static Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }

    /** Comprueba que la base responda antes de arrancar el cajero. */
    public static boolean disponible() {
        try (Connection c = obtenerConexion()) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            System.err.println("No se pudo conectar a MySQL: " + e.getMessage());
            return false;
        }
    }
}
