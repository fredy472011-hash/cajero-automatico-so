/* ===================================================================
   Simulador de cajero automático — lógica del frontend

   REGLA QUE SE RESPETA EN TODO EL ARCHIVO:
   este archivo NO decide nada. No sabe si un PIN es correcto, ni si hay
   saldo, ni si el monto se puede formar con los billetes disponibles.
   Todo eso lo decide el backend (la clase Cajero, capa 4).

   Acá solo pasan tres cosas:
     1. se juntan las teclas que aprieta el usuario
     2. se manda al backend
     3. se dibuja el estado que el backend devuelve

   Si esta lógica viviera en el navegador, cualquiera la saltearía
   abriendo la consola. Esa es la razón, y es la misma en un sistema real.
   =================================================================== */

'use strict';

/* ---------------------------------------------------------------
   Estado local: únicamente lo que el usuario está tecleando
   --------------------------------------------------------------- */

let buffer = '';          // dígitos que se van escribiendo
let pantallaActual = '';  // última pantalla recibida del backend

/* ---------------------------------------------------------------
   Utilidades
   --------------------------------------------------------------- */

const $ = (id) => document.getElementById(id);

/** Formato paraguayo: Gs. 1.500.000 */
function guaranies(n) {
    return 'Gs. ' + Number(n).toLocaleString('es-PY');
}

/**
 * Manda una acción al backend y dibuja el estado que devuelve.
 * El cuerpo va como formulario (clave=valor), que es lo que el
 * servidor espera.
 */
async function enviar(ruta, datos = {}) {
    const cuerpo = new URLSearchParams(datos).toString();
    try {
        const respuesta = await fetch(ruta, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: cuerpo
        });
        const estado = await respuesta.json();
        if (estado.error) {
            $('pantalla-mensaje').textContent = estado.error;
            return;
        }
        dibujar(estado);
    } catch (e) {
        $('pantalla-mensaje').textContent = 'Sin conexión con el cajero';
    }
}

/* ---------------------------------------------------------------
   Dibujado de la pantalla
   --------------------------------------------------------------- */

const VISTAS = [
    'ESPERANDO_TARJETA', 'PIN', 'VALIDACION_MES', 'MENU', 'MONTOS',
    'OTRO_VALOR', 'DEPOSITO', 'CONSULTA_SALDO', 'PROCESANDO',
    'DISPENSANDO', 'RECIBO', 'ERROR', 'FINALIZADO'
];

function mostrarVista(nombre) {
    VISTAS.forEach((v) => {
        const elemento = $('vista-' + v);
        if (elemento) elemento.classList.toggle('oculta', v !== nombre);
    });
}

function dibujar(estado) {
    const cambioDePantalla = estado.pantalla !== pantallaActual;
    pantallaActual = estado.pantalla;

    $('pantalla-estado').textContent  = estado.pantalla;
    $('pantalla-titulo').textContent  = estado.titulo;
    $('pantalla-mensaje').textContent = estado.mensaje || '';

    mostrarVista(estado.pantalla);

    // Al cambiar de pantalla se descarta lo tecleado: cada paso arranca limpio
    if (cambioDePantalla) {
        buffer = '';
        dibujarBuffer();
    }

    // Pie con los datos del titular, solo si ya se autenticó
    const pie = $('pie-pantalla');
    pie.classList.toggle('oculta', !estado.autenticada);
    $('titular').textContent   = estado.titular || '—';
    $('saldo-pie').textContent = guaranies(estado.saldo);

    // Vistas que necesitan datos extra
    if (estado.pantalla === 'MONTOS')        dibujarMontos(estado.montos);
    if (estado.pantalla === 'CONSULTA_SALDO') dibujarConsulta(estado);
    if (estado.pantalla === 'DISPENSANDO')   dibujarBilletes(estado.mensaje);

    // La ranura de billetes se ilumina mientras se entrega el efectivo
    $('ranura-billetes').classList.toggle('activa', estado.pantalla === 'DISPENSANDO');

    // Panel interno: memoria RAM y memoria de largo plazo
    $('ram-pantalla').textContent = estado.pantalla;
    $('ram-tarjeta').textContent  = estado.cuenta || '—';
    $('ram-pin').textContent      = estado.autenticada ? 'sí' : 'no';
    $('ram-monto').textContent    = guaranies(estado.montoEnCurso);

    dibujarCasetes(estado.casetes, estado.efectivoTotal);
    dibujarMovimientos(estado.movimientos);
}

function dibujarBuffer() {
    // PIN: se muestran puntos, nunca los dígitos
    const puntos = $('puntos-pin');
    puntos.innerHTML = '';
    for (let i = 0; i < 4; i++) {
        const punto = document.createElement('span');
        punto.className = 'punto' + (i < buffer.length ? ' lleno' : '');
        puntos.appendChild(punto);
    }

    // Montos: se muestra el número con separadores de miles
    const texto = buffer ? Number(buffer).toLocaleString('es-PY') : '0';
    $('monto-ingresado').textContent = texto;
    $('monto-deposito').textContent  = texto;

    // Número de tarjeta
    if (pantallaActual === 'ESPERANDO_TARJETA' && buffer) {
        $('numero-tarjeta').value = buffer;
    }
}

function dibujarMontos(montos) {
    const contenedor = $('montos');
    contenedor.innerHTML = '';

    if (!montos || montos.length === 0) {
        contenedor.innerHTML = '<p class="ayuda error">Sin efectivo disponible</p>';
        return;
    }

    // Los montos los calcula el backend según lo que el dispensador
    // puede formar AHORA: si se agotan los billetes, desaparecen solos.
    montos.forEach((monto) => {
        const boton = document.createElement('button');
        boton.className = 'boton';
        boton.textContent = guaranies(monto);
        boton.addEventListener('click', () => retirar(monto));
        contenedor.appendChild(boton);
    });
}

function dibujarConsulta(estado) {
    $('saldo-grande').textContent = guaranies(estado.saldo);
    const lista = $('movimientos');
    lista.innerHTML = '';
    (estado.movimientos || []).forEach((m) => {
        const li = document.createElement('li');
        li.textContent = m;
        lista.appendChild(li);
    });
}

function dibujarBilletes(mensaje) {
    // El backend manda, por ejemplo:  "Entregado: 2 x 100.000  1 x 50.000"
    const contenedor = $('billetes');
    contenedor.innerHTML = '';
    const partes = (mensaje || '').replace('Entregado:', '').trim().split(/\s{2,}/);
    partes.filter(Boolean).forEach((parte) => {
        const div = document.createElement('div');
        div.className = 'billete';
        div.textContent = parte;
        contenedor.appendChild(div);
    });
}

function dibujarCasetes(casetes, total) {
    const contenedor = $('casetes');
    contenedor.innerHTML = '';
    (casetes || []).forEach((c) => {
        const div = document.createElement('div');
        div.className = 'casete';
        const porcentaje = Math.min(100, (c.cantidad / 40) * 100);
        div.innerHTML = `
            <div class="casete-titulo">${guaranies(c.denominacion)}</div>
            <div class="casete-barra"><div class="casete-relleno" style="width:${porcentaje}%"></div></div>
            <div class="casete-cantidad">${c.cantidad} billetes</div>`;
        contenedor.appendChild(div);
    });
    $('efectivo-total').textContent = guaranies(total || 0);
}

function dibujarMovimientos(movimientos) {
    const lista = $('panel-movimientos');
    lista.innerHTML = '';
    if (!movimientos || movimientos.length === 0) {
        lista.innerHTML = '<li class="tenue">sin movimientos</li>';
        return;
    }
    movimientos.forEach((m) => {
        const li = document.createElement('li');
        li.textContent = m;
        lista.appendChild(li);
    });
}

/* ---------------------------------------------------------------
   Acciones del usuario
   --------------------------------------------------------------- */

function insertarTarjeta() {
    const numero = $('numero-tarjeta').value.trim();
    enviar('/api/tarjeta', { numero });
}

function retirar(monto) {
    // Se muestra PROCESANDO de inmediato porque el retiro tarda:
    // el proceso se bloquea 1,8 s en el dispensador. Mientras eso pasa,
    // el panel de la derecha sigue actualizándose y se ve el estado
    // BLOQUEADO en vivo. Eso es justamente lo que hay que mostrar.
    mostrarVista('PROCESANDO');
    $('pantalla-titulo').textContent = 'Por favor, aguarde un momento';
    $('pantalla-mensaje').textContent = '';
    enviar('/api/monto', { monto });
}

/** El teclado numérico escribe en el buffer según la pantalla activa. */
function teclear(digito) {
    const limites = {
        'PIN': 4,
        'ESPERANDO_TARJETA': 20,
        'OTRO_VALOR': 9,
        'DEPOSITO': 9
    };
    const limite = limites[pantallaActual];
    if (limite === undefined) return;      // esa pantalla no usa el teclado

    if (buffer.length < limite) {
        buffer += digito;
        dibujarBuffer();
    }

    // El PIN se manda solo al cuarto dígito, como en un cajero real
    if (pantallaActual === 'PIN' && buffer.length === 4) {
        const pin = buffer;
        buffer = '';
        enviar('/api/pin', { pin });
    }
}

/** La tecla verde CONFIRMAR hace lo que corresponda a la pantalla activa. */
function confirmar() {
    switch (pantallaActual) {
        case 'ESPERANDO_TARJETA':
            insertarTarjeta();
            break;
        case 'OTRO_VALOR':
            if (buffer) retirar(Number(buffer));
            break;
        case 'DEPOSITO':
            if (buffer) enviar('/api/deposito', { monto: Number(buffer) });
            break;
        case 'DISPENSANDO':
        case 'RECIBO':
            enviar('/api/finalizar');
            break;
        case 'FINALIZADO':
            enviar('/api/cancelar');
            break;
        default:
            break;
    }
}

function borrar() {
    buffer = buffer.slice(0, -1);
    dibujarBuffer();
}

/* ---------------------------------------------------------------
   Panel interno: procesos y bitácora
   --------------------------------------------------------------- */

async function refrescarPanel() {
    try {
        const [procesos, bitacora] = await Promise.all([
            fetch('/api/procesos').then((r) => r.json()),
            fetch('/api/bitacora').then((r) => r.json())
        ]);
        dibujarProcesos(procesos);
        dibujarBitacora(bitacora);
    } catch (e) {
        /* el cajero está apagado: se reintenta en el próximo ciclo */
    }
}

function dibujarProcesos(datos) {
    $('politica').textContent = 'política: ' + datos.politica;
    $('en-cola').textContent  = 'en cola: ' + datos.enCola;

    const cuerpo = $('tabla-procesos');
    cuerpo.innerHTML = '';

    if (!datos.procesos || datos.procesos.length === 0) {
        cuerpo.innerHTML = '<tr><td colspan="5" class="tenue">sin procesos todavía</td></tr>';
        return;
    }

    // Los últimos arriba: es el proceso que está pasando ahora
    datos.procesos.slice().reverse().forEach((p) => {
        const fila = document.createElement('tr');
        fila.innerHTML = `
            <td>${p.pid}</td>
            <td>${p.tipo}</td>
            <td><span class="estado ${p.estado}">${p.estado}</span></td>
            <td>${p.prioridad}</td>
            <td>${p.resultado || '—'}</td>`;
        cuerpo.appendChild(fila);
    });
}

function dibujarBitacora(lineas) {
    const pre = $('bitacora');
    if (!lineas || lineas.length === 0) {
        pre.textContent = 'esperando actividad…';
        return;
    }
    const pegadoAbajo = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 12;
    pre.textContent = lineas.join('\n');
    if (pegadoAbajo) pre.scrollTop = pre.scrollHeight;
}

/* ---------------------------------------------------------------
   Arranque
   --------------------------------------------------------------- */

function construirMeses() {
    const nombres = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun',
                     'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
    const contenedor = $('meses');
    nombres.forEach((nombre, indice) => {
        const boton = document.createElement('button');
        boton.className = 'mes';
        boton.textContent = nombre;
        boton.addEventListener('click', () => enviar('/api/mes', { mes: indice + 1 }));
        contenedor.appendChild(boton);
    });
}

function conectarBotones() {
    $('btn-insertar').addEventListener('click', insertarTarjeta);

    document.querySelectorAll('[data-operacion]').forEach((boton) => {
        boton.addEventListener('click', () =>
            enviar('/api/operacion', { operacion: boton.dataset.operacion }));
    });

    $('btn-otro-valor').addEventListener('click', () => {
        buffer = '';
        pantallaActual = 'OTRO_VALOR';
        mostrarVista('OTRO_VALOR');
        $('pantalla-titulo').textContent = 'Ingrese monto';
        dibujarBuffer();
    });

    $('btn-confirmar-monto').addEventListener('click', () => {
        if (buffer) retirar(Number(buffer));
    });

    $('btn-confirmar-deposito').addEventListener('click', () => {
        if (buffer) enviar('/api/deposito', { monto: Number(buffer) });
    });

    ['btn-volver-menu', 'btn-error-menu'].forEach((id) =>
        $(id).addEventListener('click', () => enviar('/api/menu')));

    ['btn-fin-retiro', 'btn-recibo-si', 'btn-recibo-no'].forEach((id) =>
        $(id).addEventListener('click', () => enviar('/api/finalizar')));

    $('btn-nueva-sesion').addEventListener('click', () => enviar('/api/cancelar'));

    // Teclado numérico
    document.querySelectorAll('[data-tecla]').forEach((tecla) => {
        tecla.addEventListener('click', () => teclear(tecla.dataset.tecla));
    });

    $('btn-borrar').addEventListener('click', borrar);
    $('btn-confirmar').addEventListener('click', confirmar);

    // CANCELAR borra toda la memoria volátil: es el corte de energía
    $('btn-cancelar').addEventListener('click', () => {
        buffer = '';
        enviar('/api/cancelar');
    });

    // También se puede usar el teclado real de la computadora
    document.addEventListener('keydown', (evento) => {
        if (evento.key >= '0' && evento.key <= '9') teclear(evento.key);
        else if (evento.key === 'Backspace') { evento.preventDefault(); borrar(); }
        else if (evento.key === 'Enter')     confirmar();
        else if (evento.key === 'Escape')    enviar('/api/cancelar');
    });
}

function reloj() {
    $('pantalla-reloj').textContent = new Date().toLocaleTimeString('es-PY');
}

document.addEventListener('DOMContentLoaded', () => {
    construirMeses();
    conectarBotones();

    reloj();
    setInterval(reloj, 1000);

    // Primer estado y refresco periódico.
    // 400 ms es suficiente para ver un proceso pasar por BLOQUEADO
    // sin saturar el servidor.
    fetch('/api/estado').then((r) => r.json()).then(dibujar).catch(() => {
        $('pantalla-mensaje').textContent = 'El cajero no está encendido';
    });
    refrescarPanel();
    setInterval(refrescarPanel, 400);
    setInterval(() => {
        // Solo refresca la pantalla cuando el usuario no está tecleando,
        // para no borrarle lo que escribió.
        if (!buffer) fetch('/api/estado').then((r) => r.json()).then(dibujar).catch(() => {});
    }, 800);
});
