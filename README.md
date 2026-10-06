# Simulador de Cajero Automático

Trabajo práctico de **Informática VI — Sistemas Operativos**.

No es un cajero: es un **sistema operativo que se puede mirar por dentro**. El cajero
es la excusa para que se vean los procesos cambiando de estado, la memoria
asignándose y liberándose, y la entrada/salida bloqueando la ejecución.

![Java](https://img.shields.io/badge/Java-21-orange)
![Sin%20frameworks](https://img.shields.io/badge/dependencias-solo%20JDBC-green)
![MySQL](https://img.shields.io/badge/MySQL-8-blue)

---

## Antes de empezar: qué hay que descargar

El repositorio **no incluye** los programas ni el driver, porque son archivos
grandes que cada uno baja una sola vez y sirven para todos los proyectos.

### 1. Clonar el proyecto

```bash
git clone https://github.com/fredy472011-hash/cajero-automatico-so.git
cd cajero-automatico-so
```

### 2. JDK 21 (o 17 en adelante)

Es el que compila y ejecuta el Java. **Hace falta el JDK, no el JRE**: el JRE solo
ejecuta, no trae `javac`.

> https://adoptium.net/es/temurin/releases/ → Windows x64, JDK, `.msi`

Durante la instalación, tildar **"Set JAVA_HOME variable"**.

Comprobar que quedó bien:

```bash
java -version      # debe decir 21 (o 17)
javac -version     # si este falla, instalaste el JRE y no el JDK
```

### 3. MySQL 8

Es donde vive la memoria de largo plazo del cajero.

> https://dev.mysql.com/downloads/installer/ → *MySQL Installer for Windows*,
> opción **Server only** (no hace falta Workbench, aunque ayuda para mirar las tablas)

En la instalación pide una contraseña para el usuario `root`. **Anotarla**: hay que
ponerla en el paso 5.

### 4. Driver JDBC de MySQL

`java.sql` viene dentro del JDK, pero es solo la **interfaz**. La implementación que
sabe hablar con MySQL está en este `.jar`, y hay que bajarlo aparte.

> https://dev.mysql.com/downloads/connector/j/ → **Platform Independent** →
> descargar el `.zip`, descomprimir y copiar `mysql-connector-j-9.4.0.jar`
> dentro de la carpeta `backend/lib/`

Si falta, el error al arrancar es:

```
No suitable driver found for jdbc:mysql://localhost:3306/cajero
```

### 5. Decirle la contraseña de MySQL al programa

Las credenciales **no están escritas en el código** —eso es una mala práctica y en un
repositorio público es directamente un problema de seguridad—. Se leen de variables
de entorno. Si no se definen, el programa prueba con `root`/`root`.

Si tu `root` tiene otra contraseña, en PowerShell:

```powershell
$env:DB_PASSWORD = "tu_contrasenha"
```

Eso vale solo para esa ventana de terminal. Para que quede fijo, agregarla en
*Variables de entorno* de Windows.

### Resumen

| Qué | Para qué | Tamaño aprox. |
|---|---|---|
| JDK 21 | compilar y ejecutar | 180 MB |
| MySQL 8 Server | la base de datos | 250 MB |
| `mysql-connector-j-9.4.0.jar` | que Java pueda hablar con MySQL | 2,5 MB |

### Nota para quien trabaje solo en el frontend

Igual hace falta todo lo anterior, porque las pantallas se dibujan con los datos que
devuelve el backend. Abrir `frontend/index.html` con doble clic **no funciona**: el
navegador lo cargaría desde el disco (`file://`) y los pedidos a `/api/estado`
fallarían. Hay que arrancar el servidor y entrar por `http://localhost:8080`.

Lo bueno es que el backend no se vuelve a compilar al editar el frontend: se cambia
el HTML, el CSS o el JS, se recarga la página con **Ctrl+F5** y listo.

---

## Cómo correrlo

```bash
# 1. Crear la base de datos (una sola vez)
mysql -u root -p < sql/esquema.sql

# 2. Compilar
javac -encoding UTF-8 -cp "backend/lib/mysql-connector-j-9.4.0.jar" \
      -d backend/out $(find backend/src -name "*.java")

# 3a. Modo web  ->  después abrir http://localhost:8080
java -cp "backend/out;backend/lib/mysql-connector-j-9.4.0.jar" cajero.MainWeb

# 3b. Modo consola: las 4 demostraciones de la materia
java -cp "backend/out" cajero.Main
```

En Linux o Mac, el separador del classpath es `:` en lugar de `;`.

**Cuentas de prueba**

| Tarjeta | PIN | Mes de nacimiento | Saldo |
|---|---|---|---|
| 1001 | 1234 | Mayo | Gs. 2.500.000 |
| 1002 | 4321 | Noviembre | Gs. 1.800.000 |
| 1003 | 0000 | Enero | Gs. 30.000 |

Las credenciales de MySQL se leen de `DB_URL`, `DB_USER` y `DB_PASSWORD`.
Si no están definidas, usa `root`/`root` en `localhost:3306`.

---

## Orden de lectura: las 6 capas

Cada carpeta es una capa y **solo puede usar las de número menor**. Esa regla es
lo que mantiene el proyecto entendible: si la capa 1 no conoce a la capa 5, se
puede leer la capa 1 sin saber nada del resto.

```
backend/src/cajero/
  ├── dominio/        1. Las reglas del dinero
  ├── hardware/       2. Los dispositivos físicos (entrada/salida)
  ├── persistencia/   3. La memoria de largo plazo (MySQL)
  ├── so/             4. El sistema operativo simulado
  ├── aplicacion/     5. La lógica de negocio
  └── web/            6. La presentación (HTTP)
frontend/             7. La pantalla (HTML, CSS, JavaScript)
```

### 1. `dominio/` — las reglas del dinero

| Archivo | Qué hace |
|---|---|
| `Cuenta.java` | El saldo y las operaciones sobre él |

**Es el recurso compartido del sistema.** Varios hilos pueden operar sobre la misma
cuenta al mismo tiempo, y ahí está la demostración central del trabajo.

Tiene **dos versiones del retiro a propósito**:

```java
// MAL: entre leer y escribir, otro hilo se mete
public boolean retirarSinSincronizar(long monto) {
    if (saldo >= monto) {              // 1. LEER
        dormir(50);                    //    <- otro hilo entra acá
        saldo = saldo - monto;         // 2. ESCRIBIR
        return true;
    }
    return false;
}

// BIEN: un solo hilo a la vez
public synchronized boolean retirar(long monto) { ... }
```

Mostrar el error primero y después corregirlo es lo que demuestra que se entendió
el problema. Con la versión mala, dos retiros de 100.000 sobre una cuenta de
150.000 dejan el saldo en **−50.000**: plata que apareció de la nada.

### 2. `hardware/` — los dispositivos (entrada/salida)

| Archivo | Qué hace |
|---|---|
| `Dispensador.java` | Los casetes de billetes y el algoritmo de cambio |

Dos cosas importantes acá:

**El dispensador solo tiene casetes de 50.000 y 100.000.** Está relevado de un
cajero físico real: la pantalla "Billetes disponibles" muestra solo esas dos
denominaciones. Por eso **hay montos que se rechazan aunque haya saldo**, y eso
hay que modelarlo:

- Pedir 30.000 → rechazado, el billete más chico es 50.000
- Pedir 450.000 con 3 de 100.000 y 2 de 50.000 → rechazado, no se puede formar

El algoritmo es **voraz** (*greedy*): toma primero la denominación más alta.

**La demora va fuera del `synchronized`:**

```java
synchronized (this) {
    plan = planificar(monto);          // sección crítica corta
    plan.forEach(...);                 // reservar los billetes
}
Thread.sleep(demoraMs);                // E/S lenta, FUERA del candado
```

Si el `sleep` estuviera adentro, ningún otro proceso podría ni consultar el
dispensador durante 1,8 segundos. Es un error clásico y vale explicarlo.

### 3. `persistencia/` — la memoria de largo plazo

| Archivo | Qué hace |
|---|---|
| `BaseDeDatos.java` | La conexión a MySQL |
| `RepositorioCuentas.java` | Leer y grabar cuentas, transacciones y casetes |

Todo lo que pasa por acá **sobrevive** al apagado, a la cancelación y al corte de
energía. Es la contracara de `Sesion` (capa 5), que vive en RAM y se pierde.

Las consultas usan `PreparedStatement` con parámetros `?`, así que son inmunes a
inyección SQL. Nunca se arma SQL concatenando texto.

### 4. `so/` — el sistema operativo simulado

| Archivo | Qué hace |
|---|---|
| `EstadoProceso.java` | Los 5 estados: NUEVO, LISTO, EJECUTANDO, BLOQUEADO, TERMINADO |
| `ProcesoTransaccion.java` | Una transacción como proceso (extiende `Thread`) |
| `PlanificadorCPU.java` | La cola de listos y la política de planificación |
| `RegistroActividad.java` | La bitácora de cambios de estado |

**El corazón del trabajo.** Cada transacción es un hilo real, así que la
concurrencia no está simulada: ocurre de verdad.

```
NUEVO ──> LISTO ──> EJECUTANDO ──> TERMINADO
             ^           │
             │           v
             └────── BLOQUEADO   (esperando entrada/salida)
```

El planificador soporta dos políticas para poder compararlas en el informe:

- **FIFO** — primero en llegar, primero en ser atendido
- **PRIORIDAD** — las consultas (que no usan el dispensador) van antes

El `gradoDeMultiprogramacion` limita cuántos procesos corren a la vez, igual que
un sistema real.

> **Detalle que costó encontrar:** no se puede usar `Thread.join()` para esperar a
> un proceso. El planificador todavía no llamó a `start()`, y `join()` sobre un
> hilo no iniciado **vuelve de inmediato**. Hay que esperar el estado `TERMINADO`.

### 5. `aplicacion/` — la lógica de negocio

| Archivo | Qué hace |
|---|---|
| `Cajero.java` | El orquestador: el único lugar donde se decide qué se puede hacer |
| `Sesion.java` | La memoria RAM: volátil, se borra al cancelar |
| `EstadoPantalla.java` | La máquina de estados de la interfaz |

`Cajero` es **el único lugar con reglas de negocio**. No sabe nada de HTTP ni de
HTML; por eso el mismo `Cajero` funciona desde consola o desde el navegador.

Si el navegador manda un pedido fuera de orden (un retiro sin haber validado el
PIN), **quien lo rechaza es esta capa**, no el frontend.

`Sesion` es la memoria volátil. `limpiar()` simula el corte de energía:

```java
public void limpiar() {
    cuenta = null;          // la tarjeta leída: se pierde
    pinValidado = false;    // la autenticación: se pierde
    montoEnCurso = 0;       // lo que estaba tecleando: se pierde
    pantalla = EstadoPantalla.ESPERANDO_TARJETA;
}
```

El saldo real, en MySQL, queda intacto. **Ese contraste es el tema de la materia.**

### 6. `web/` — la presentación

| Archivo | Qué hace |
|---|---|
| `ServidorCajero.java` | El servidor HTTP y la API |
| `Json.java` | Armador de JSON mínimo, escrito a mano |

Usa `com.sun.net.httpserver.HttpServer`, que **viene dentro del JDK** desde Java 6
(módulo `jdk.httpserver`). No hay Spring, ni Tomcat, ni un solo `.jar` descargado
aparte del driver de MySQL. El requisito era Java puro y esto lo cumple.

Esta capa solo traduce: `pedido HTTP → llamada a Cajero → respuesta JSON`.

| Método | Ruta | Cuerpo |
|---|---|---|
| POST | `/api/tarjeta` | `numero=1001` |
| POST | `/api/pin` | `pin=1234` |
| POST | `/api/mes` | `mes=5` |
| POST | `/api/operacion` | `operacion=EXTRACCION\|DEPOSITO\|SALDO` |
| POST | `/api/monto` | `monto=250000` |
| POST | `/api/deposito` | `monto=150000` |
| POST | `/api/cancelar` | — |
| POST | `/api/menu` | — |
| POST | `/api/finalizar` | — |
| GET | `/api/estado` | pantalla, titular, saldo, montos, casetes |
| GET | `/api/procesos` | la tabla de procesos |
| GET | `/api/bitacora` | las últimas 40 líneas del registro |

Todas las acciones devuelven el **estado nuevo**, así el frontend hace un solo
pedido por paso.

> El servidor usa un pool de 4 hilos. Con uno solo, mientras el retiro está
> bloqueado 1,8 s en el dispensador, los pedidos de `/api/estado` quedarían
> encolados y el panel se congelaría **justo en el momento que hay que observar**.

### 7. `frontend/` — la pantalla

| Archivo | Qué hace |
|---|---|
| `index.html` | La estructura: el cajero a la izquierda, el panel interno a la derecha |
| `css/cajero.css` | El aspecto del aparato físico y los colores por estado |
| `js/cajero.js` | Junta las teclas, las manda al backend, dibuja el estado |

**El JavaScript no decide nada.** No sabe si un PIN es correcto, ni si hay saldo,
ni si el monto se puede formar. Si esa lógica viviera en el navegador, cualquiera
la saltearía abriendo la consola. Es la misma razón que en un sistema real.

El panel derecho se refresca cada 400 ms y muestra, en vivo:

- **Memoria** — la RAM volátil al lado de los movimientos persistidos
- **Procesos** — PID, tipo, estado con color, prioridad y resultado
- **Entrada/salida** — los casetes bajando a medida que se entrega efectivo
- **Bitácora** — cada cambio de estado con su hora

---

## El mapa: cada tema de la materia, y dónde verlo

| Tema | Dónde está | Cómo se ve |
|---|---|---|
| Procesos y estados | `so/ProcesoTransaccion` | La tabla de procesos cambia de color en vivo |
| Planificación de CPU | `so/PlanificadorCPU` | FIFO vs PRIORIDAD; la consulta se adelanta al retiro |
| Multiprogramación | `so/PlanificadorCPU` | Solo 2 procesos corren a la vez |
| Concurrencia | `dominio/Cuenta` | Demo 1: dos retiros simultáneos |
| Condición de carrera | `retirarSinSincronizar()` | El saldo queda en −50.000 |
| Sincronización | `synchronized` en `retirar()` | Demo 2: el segundo retiro se rechaza bien |
| Memoria RAM (volátil) | `aplicacion/Sesion` | CANCELAR borra todo el panel izquierdo |
| Memoria de largo plazo | `persistencia/` + MySQL | El saldo sigue ahí después de cancelar |
| Entrada/salida | `hardware/Dispensador` | El proceso pasa a BLOQUEADO 1,8 s |
| Algoritmo de cambio | `Dispensador.planificar()` | Pedir 30.000 se rechaza |

---

## La demostración que hay que mostrar en clase

Correr `cajero.Main` y mirar la salida de la demo 3:

```
[10:32:01.044] [Proceso-1] NUEVO -> LISTO        (admitido en la cola de listos)
[10:32:01.051] [Proceso-1] LISTO -> EJECUTANDO   (CPU asignada)
[10:32:01.052] [Proceso-1] EJECUTANDO -> BLOQUEADO (solicita E/S: dispensador)
[10:32:01.053] [Proceso-3] LISTO -> EJECUTANDO   (CPU asignada)
[10:32:01.054] [Proceso-3] TERMINADO             (Saldo: Gs. 3.000.000)
[10:32:02.556] [Proceso-1] BLOQUEADO -> LISTO    (E/S completada)
```

Lo que se ve ahí: **el proceso 1 se bloqueó por entrada/salida y el proceso 3 usó
la CPU mientras esperaba.** El proceso 3 es la consulta, que tiene prioridad 1
porque no toca el hardware. Eso es un sistema operativo funcionando.

Y en el navegador, el contraste de memorias:

1. Insertar la tarjeta 1001, PIN 1234, mes Mayo
2. Retirar Gs. 250.000 → el panel muestra el proceso pasando por BLOQUEADO
3. Apretar **CANCELAR** → el panel de RAM queda vacío
4. El saldo en MySQL sigue en Gs. 2.250.000

---

## Decisiones y por qué

| Decisión | Motivo |
|---|---|
| Java y no JavaScript solo | JavaScript es de un solo hilo: no puede mostrar concurrencia real |
| Java puro y no Spring | El requisito era Java puro; `HttpServer` viene en el JDK |
| Web y no Swing ni consola | Permite mostrar el aparato **y** el interior al mismo tiempo |
| Hilos reales y no pseudocódigo | El pseudocódigo describe la concurrencia; los hilos la demuestran |
| `synchronized` y no semáforos | El problema es exclusión mutua sobre un objeto, que es exactamente para lo que existe `synchronized`. Un semáforo serviría para limitar *cuántos* acceden, que no es el caso |
| Formulario y no JSON en el pedido | Leer JSON sin librería obligaría a escribir un parser completo, y el valor del trabajo está en los procesos |

---

## Fuentes

- [A Simulation Project for an Operating Systems Course — ASEE](https://peer.asee.org/a-simulation-project-for-an-operating-systems-course.pdf)
- [Operating System Simulator Project — Univ. Central Florida](http://www.cs.ucf.edu/courses/cop5611/spring05/project/intro.pdf)
- [Scheduling and Memory Management — Univ. Wisconsin](https://pages.cs.wisc.edu/~remzi/Classes/537/Spring2002/p3.html)
- [Programación Concurrente con Java — Univ. Complutense de Madrid](https://grasia.fdi.ucm.es/jpavon/docencia/dso/programacionconcurrentejava.pdf)
- [Lesson: Concurrency — Java Tutorials, Oracle](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [`com.sun.net.httpserver` — documentación del JDK](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html)

---

**Fredy Luciano Cañete Trinidad** — Licenciatura en Análisis de Sistemas
