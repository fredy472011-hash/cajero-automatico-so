-- ---------------------------------------------------------------
-- Base de datos del simulador de cajero automatico
--
-- Esta es la MEMORIA DE LARGO PLAZO del sistema: sobrevive al
-- apagado, a la cancelacion de una sesion y al corte de energia.
--
-- Se contrapone con la clase Sesion (cajero.aplicacion.Sesion), que
-- vive solo en RAM y se pierde apenas el usuario cancela.
--
-- Uso:  mysql -u root -p < esquema.sql
-- ---------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS cajero
    DEFAULT CHARACTER SET utf8mb4;

USE cajero;

-- Cuentas de los clientes
CREATE TABLE IF NOT EXISTS cuentas (
    numero          VARCHAR(20)  NOT NULL PRIMARY KEY,
    titular         VARCHAR(100) NOT NULL,
    pin             CHAR(4)      NOT NULL,
    mes_nacimiento  TINYINT      NOT NULL,   -- 1 a 12, para la validacion extra
    saldo           BIGINT       NOT NULL DEFAULT 0,
    bloqueada       BOOLEAN      NOT NULL DEFAULT FALSE,
    creada_en       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Historial de operaciones: el registro permanente
CREATE TABLE IF NOT EXISTS transacciones (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero_cuenta   VARCHAR(20)  NOT NULL,
    tipo            ENUM('RETIRO','DEPOSITO','CONSULTA') NOT NULL,
    monto           BIGINT       NOT NULL DEFAULT 0,
    saldo_resultante BIGINT      NOT NULL,
    exitosa         BOOLEAN      NOT NULL,
    detalle         VARCHAR(255),
    pid_proceso     INT,                      -- el PID del proceso que la ejecuto
    ocurrida_en     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (numero_cuenta) REFERENCES cuentas(numero)
) ENGINE=InnoDB;

CREATE INDEX idx_trx_cuenta ON transacciones (numero_cuenta, ocurrida_en);

-- Estado de los casetes del dispensador (tambien persistente:
-- el cajero no se olvida cuantos billetes le quedan al apagarse)
CREATE TABLE IF NOT EXISTS casetes (
    denominacion    INT     NOT NULL PRIMARY KEY,
    cantidad        INT     NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- Datos de prueba
-- ---------------------------------------------------------------

INSERT INTO cuentas (numero, titular, pin, mes_nacimiento, saldo) VALUES
    ('1001', 'Fredy Cañete',   '1234',  5, 2500000),
    ('1002', 'Elias Benitez',  '4321', 11, 1800000),
    ('1003', 'Cuenta Vacia',   '0000',  1,   30000)
AS nuevo
ON DUPLICATE KEY UPDATE
    titular = nuevo.titular,
    saldo   = nuevo.saldo;

-- Un cajero real de Itau Paraguay solo tiene casetes de 50.000 y 100.000
INSERT INTO casetes (denominacion, cantidad) VALUES
    (100000, 40),
    (50000,  30)
AS nuevo
ON DUPLICATE KEY UPDATE cantidad = nuevo.cantidad;
