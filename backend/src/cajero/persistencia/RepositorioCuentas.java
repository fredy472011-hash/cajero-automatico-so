package cajero.persistencia;

import cajero.dominio.Cuenta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CAPA 3 - MEMORIA DE LARGO PLAZO
 *
 * Lee y graba cuentas y transacciones en MySQL.
 *
 * Todas las consultas usan PreparedStatement con parametros (?), asi que
 * son inmunes a inyeccion SQL. Nunca se arma SQL concatenando texto.
 */
public class RepositorioCuentas {

    /** Busca una cuenta por su numero. Devuelve null si no existe. */
    public Cuenta buscarPorNumero(String numero) {
        String sql = "SELECT numero, titular, pin, mes_nacimiento, saldo FROM cuentas WHERE numero = ?";
        try (Connection c = BaseDeDatos.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Cuenta(
                        rs.getString("numero"),
                        rs.getString("titular"),
                        rs.getString("pin"),
                        rs.getInt("mes_nacimiento"),
                        rs.getLong("saldo"));
            }
        } catch (SQLException e) {
            System.err.println("Error al buscar la cuenta " + numero + ": " + e.getMessage());
            return null;
        }
    }

    /** Graba el saldo actualizado. Se llama despues de cada operacion exitosa. */
    public boolean actualizarSaldo(String numero, long nuevoSaldo) {
        String sql = "UPDATE cuentas SET saldo = ? WHERE numero = ?";
        try (Connection c = BaseDeDatos.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, nuevoSaldo);
            ps.setString(2, numero);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("Error al actualizar el saldo: " + e.getMessage());
            return false;
        }
    }

    /** Deja constancia permanente de la operacion. */
    public void registrarTransaccion(String numeroCuenta, String tipo, long monto,
                                     long saldoResultante, boolean exitosa,
                                     String detalle, int pidProceso) {
        String sql = """
                INSERT INTO transacciones
                    (numero_cuenta, tipo, monto, saldo_resultante, exitosa, detalle, pid_proceso)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = BaseDeDatos.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, numeroCuenta);
            ps.setString(2, tipo);
            ps.setLong(3, monto);
            ps.setLong(4, saldoResultante);
            ps.setBoolean(5, exitosa);
            ps.setString(6, detalle);
            ps.setInt(7, pidProceso);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al registrar la transaccion: " + e.getMessage());
        }
    }

    /** Ultimas operaciones de una cuenta, para el recibo en pantalla. */
    public List<String> ultimosMovimientos(String numeroCuenta, int cantidad) {
        String sql = """
                SELECT tipo, monto, saldo_resultante, exitosa, ocurrida_en
                FROM transacciones
                WHERE numero_cuenta = ?
                ORDER BY ocurrida_en DESC
                LIMIT ?
                """;
        List<String> movimientos = new ArrayList<>();
        try (Connection c = BaseDeDatos.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, numeroCuenta);
            ps.setInt(2, cantidad);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    movimientos.add("%s  %s  Gs. %,d  ->  saldo Gs. %,d  %s".formatted(
                            rs.getTimestamp("ocurrida_en").toLocalDateTime().toLocalDate(),
                            rs.getString("tipo"),
                            rs.getLong("monto"),
                            rs.getLong("saldo_resultante"),
                            rs.getBoolean("exitosa") ? "OK" : "RECHAZADA"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al leer los movimientos: " + e.getMessage());
        }
        return movimientos;
    }

    // ---------- casetes del dispensador ----------

    public void guardarCasete(int denominacion, int cantidad) {
        String sql = "UPDATE casetes SET cantidad = ? WHERE denominacion = ?";
        try (Connection c = BaseDeDatos.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setInt(2, denominacion);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al guardar el casete: " + e.getMessage());
        }
    }

    public int leerCasete(int denominacion) {
        String sql = "SELECT cantidad FROM casetes WHERE denominacion = ?";
        try (Connection c = BaseDeDatos.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, denominacion);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("cantidad") : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }
}
