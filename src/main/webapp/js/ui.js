/**
 * Utilidades de interfaz compartidas por todas las pantallas:
 * escapado de texto, formatos de fecha, estados de carga y avisos.
 */

/**
 * Escapa texto antes de insertarlo en HTML.
 *
 * Todo dato que venga del servidor pasa por aca: un paciente podria llamarse
 * literalmente algo con etiquetas HTML adentro y no queremos que el navegador lo
 * interprete.
 */
export function esc(valor) {
    if (valor === null || valor === undefined) {
        return '';
    }
    return String(valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

const DIAS = ['domingo', 'lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado'];
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export const DIAS_SEMANA = [
    { valor: 'MONDAY', texto: 'Lunes' },
    { valor: 'TUESDAY', texto: 'Martes' },
    { valor: 'WEDNESDAY', texto: 'Miercoles' },
    { valor: 'THURSDAY', texto: 'Jueves' },
    { valor: 'FRIDAY', texto: 'Viernes' },
    { valor: 'SATURDAY', texto: 'Sabado' },
    { valor: 'SUNDAY', texto: 'Domingo' }
];

export function nombreDeDia(valor) {
    const dia = DIAS_SEMANA.find(d => d.valor === valor);
    return dia ? dia.texto : valor;
}

/** "2026-09-18T09:30" -> "09:30" */
export function hora(fechaHora) {
    return fechaHora ? fechaHora.substring(11, 16) : '';
}

/** "2026-09-18T09:30" -> "18/09/2026" */
export function fecha(fechaHora) {
    if (!fechaHora) {
        return '';
    }
    const [anio, mes, dia] = fechaHora.substring(0, 10).split('-');
    return dia + '/' + mes + '/' + anio;
}

/** "2026-09-18T09:30" -> "viernes 18 sep, 09:30" */
export function fechaLarga(fechaHora) {
    if (!fechaHora) {
        return '';
    }
    const momento = new Date(fechaHora);
    if (isNaN(momento)) {
        return fechaHora;
    }
    return DIAS[momento.getDay()] + ' ' + momento.getDate() + ' '
        + MESES[momento.getMonth()] + ', ' + hora(fechaHora);
}

/** Una fecha local en formato aaaa-MM-dd. */
function aIso(momento) {
    const mes = String(momento.getMonth() + 1).padStart(2, '0');
    const dia = String(momento.getDate()).padStart(2, '0');
    return momento.getFullYear() + '-' + mes + '-' + dia;
}

/** "2026-11-17" -> Date local (sin corrimiento por zona horaria). */
function deIso(fechaIso) {
    const [anio, mes, dia] = fechaIso.split('-').map(Number);
    return new Date(anio, mes - 1, dia);
}

/** Fecha de hoy en formato aaaa-MM-dd, lista para un <input type="date">. */
export function hoyIso() {
    return aIso(new Date());
}

/** "2026-11-17" -> "mar 17 nov" */
export function fechaCorta(fechaIso) {
    const momento = deIso(fechaIso);
    return DIAS[momento.getDay()].substring(0, 3) + ' ' + momento.getDate() + ' ' + MESES[momento.getMonth()];
}

/* -------------------- Dias de atencion de un profesional -------------------- */

// Indice de Date.getDay() (0 = domingo) -> valor de java.time.DayOfWeek.
const DIAS_JAVA = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

/** true si alguna franja de la disponibilidad semanal cae ese dia. */
export function atiendeEseDia(disponibilidad, fechaIso) {
    const dia = DIAS_JAVA[deIso(fechaIso).getDay()];
    return (disponibilidad || []).some(franja => franja.diaSemana === dia);
}

/** Las proximas fechas (aaaa-MM-dd), desde hoy, en las que el profesional atiende. */
export function proximosDiasDeAtencion(disponibilidad, cantidad = 6) {
    const dias = new Set((disponibilidad || []).map(franja => franja.diaSemana));
    const resultado = [];
    const cursor = new Date();
    for (let i = 0; i < 90 && dias.size > 0 && resultado.length < cantidad; i++) {
        if (dias.has(DIAS_JAVA[cursor.getDay()])) {
            resultado.push(aIso(cursor));
        }
        cursor.setDate(cursor.getDate() + 1);
    }
    return resultado;
}

/**
 * Disponibilidad semanal en palabras, agrupando los dias que comparten
 * horario: "lunes a viernes, de 09:00 a 13:00" o
 * "martes y jueves, de 10:00 a 16:00".
 */
export function textoDisponibilidad(disponibilidad) {
    const orden = DIAS_SEMANA.map(d => d.valor);
    const grupos = new Map();
    [...(disponibilidad || [])]
        .sort((a, b) => orden.indexOf(a.diaSemana) - orden.indexOf(b.diaSemana))
        .forEach(franja => {
            const clave = franja.horaDesde.substring(0, 5) + ' a ' + franja.horaHasta.substring(0, 5);
            if (!grupos.has(clave)) {
                grupos.set(clave, []);
            }
            grupos.get(clave).push(orden.indexOf(franja.diaSemana));
        });

    return [...grupos.entries()]
        .map(([horario, indices]) => listaDeDias(indices) + ', de ' + horario)
        .join('; ');
}

/** [0, 1, 2, 3, 4] -> "lunes a viernes"; [1, 3] -> "martes y jueves". */
function listaDeDias(indices) {
    const nombre = (indice) => DIAS_SEMANA[indice].texto.toLowerCase();
    const tramos = [];
    let inicio = 0;
    for (let i = 1; i <= indices.length; i++) {
        if (i === indices.length || indices[i] !== indices[i - 1] + 1) {
            const tramo = indices.slice(inicio, i);
            if (tramo.length >= 3) {
                tramos.push(nombre(tramo[0]) + ' a ' + nombre(tramo[tramo.length - 1]));
            } else {
                tramo.forEach(indice => tramos.push(nombre(indice)));
            }
            inicio = i;
        }
    }
    return tramos.length > 1
        ? tramos.slice(0, -1).join(', ') + ' y ' + tramos[tramos.length - 1]
        : tramos.join('');
}

/**
 * Cuantos horarios libres tiene cada dia, consultados en paralelo.
 * libres = null si la consulta de ese dia fallo.
 */
export async function lugaresPorDia(dias, horariosDelDia) {
    return Promise.all(dias.map(async (dia) => {
        try {
            return { dia, libres: (await horariosDelDia(dia)).length };
        } catch (error) {
            return { dia, libres: null };
        }
    }));
}

/** Botones de los proximos dias de atencion; los que no tienen lugar quedan deshabilitados. */
export function htmlDiasDeAtencion(lugares, diaElegido) {
    return '<div class="dias-atencion" role="group" aria-label="Próximos días de atención">'
        + lugares.map(({ dia, libres }) => {
            const completo = libres === 0;
            const detalle = completo ? 'Completo'
                : libres === null ? '' : libres + (libres === 1 ? ' horario' : ' horarios');
            return '<button type="button" class="dia-atencion'
                + (dia === diaElegido ? ' dia-atencion--elegido' : '') + '" data-dia="' + esc(dia) + '"'
                + (completo ? ' disabled' : '') + ' aria-pressed="' + (dia === diaElegido) + '">'
                + '<span>' + esc(fechaCorta(dia)) + '</span>'
                + (detalle ? '<small>' + esc(detalle) + '</small>' : '')
                + '</button>';
        }).join('')
        + '</div>';
}

/** Marca como elegido el boton de ese dia (si esta entre los proximos). */
export function marcarDiaElegido(contenedor, dia) {
    contenedor.querySelectorAll('.dia-atencion').forEach(boton => {
        const elegido = boton.dataset.dia === dia;
        boton.classList.toggle('dia-atencion--elegido', elegido);
        boton.setAttribute('aria-pressed', String(elegido));
    });
}

export function etiquetaEstado(estado) {
    const clases = {
        PROGRAMADO: 'etiqueta--programado',
        ATENDIDO: 'etiqueta--atendido',
        CANCELADO: 'etiqueta--cancelado'
    };
    const textos = {
        PROGRAMADO: 'Programado',
        ATENDIDO: 'Atendido',
        CANCELADO: 'Cancelado'
    };
    const clase = clases[estado] || '';
    return '<span class="etiqueta ' + clase + '">' + esc(textos[estado] || estado) + '</span>';
}

export function iniciales(nombre) {
    if (!nombre) {
        return '?';
    }
    return nombre.trim().split(/\s+/).slice(0, 2).map(p => p[0].toUpperCase()).join('');
}

/* ------------------------- Estados de una seccion ------------------------- */

export function mostrarCargando(elemento, texto) {
    elemento.innerHTML = '<div class="cargando">' + esc(texto || 'Cargando...') + '</div>';
}

export function mostrarVacio(elemento, titulo, detalle, botonHtml) {
    elemento.innerHTML = '<div class="estado">'
        + '<h3>' + esc(titulo) + '</h3>'
        + '<p>' + esc(detalle || '') + '</p>'
        + (botonHtml || '')
        + '</div>';
}

export function mostrarError(elemento, mensaje) {
    elemento.innerHTML = '<div class="estado">'
        + '<h3>No se pudo cargar la informacion</h3>'
        + '<p>' + esc(mensaje) + '</p>'
        + '</div>';
}

/* ------------------------------- Avisos ---------------------------------- */

export function aviso(mensaje, tipo) {
    const contenedor = document.getElementById('avisos');
    if (!contenedor) {
        return;
    }
    const caja = document.createElement('div');
    caja.className = 'toast' + (tipo ? ' toast--' + tipo : '');
    caja.setAttribute('role', 'status');
    caja.textContent = mensaje;
    contenedor.appendChild(caja);
    setTimeout(() => caja.remove(), 5000);
}

export const avisoExito = (mensaje) => aviso(mensaje, 'exito');
export const avisoError = (mensaje) => aviso(mensaje, 'error');

/** Mensaje inline dentro de un formulario. */
export function mensajeEnCaja(elemento, texto, tipo) {
    if (!texto) {
        elemento.hidden = true;
        elemento.textContent = '';
        return;
    }
    elemento.hidden = false;
    elemento.className = 'aviso aviso--' + (tipo || 'info');
    elemento.textContent = texto;
}

/* -------------------------- Confirmación modal ---------------------------- */

/**
 * Pide una confirmación antes de una acción delicada, en lugar de un alert().
 *
 * Devuelve una promesa que resuelve en true si la persona confirmó, o false si
 * canceló (con el botón, con Escape o tocando fuera del cuadro).
 */
export function confirmar(titulo, mensaje, textoConfirmar, tipo) {
    return new Promise((resolve) => {
        const fondo = document.createElement('div');
        fondo.className = 'modal-fondo';

        const caja = document.createElement('div');
        caja.className = 'modal';
        caja.setAttribute('role', 'alertdialog');
        caja.setAttribute('aria-modal', 'true');
        caja.setAttribute('aria-labelledby', 'modal-titulo');

        const botonConfirmarClase = tipo === 'peligro' ? 'boton boton--peligro' : 'boton';

        caja.innerHTML =
            '<h3 id="modal-titulo">' + esc(titulo) + '</h3>'
            + '<p>' + esc(mensaje) + '</p>'
            + '<div class="acciones acciones--derecha">'
            + '<button type="button" class="boton boton--secundario" data-accion="cancelar">Cancelar</button>'
            + '<button type="button" class="' + botonConfirmarClase + '" data-accion="confirmar">'
            + esc(textoConfirmar || 'Confirmar') + '</button>'
            + '</div>';

        fondo.appendChild(caja);
        document.body.appendChild(fondo);

        function cerrar(resultado) {
            document.removeEventListener('keydown', alTeclear);
            fondo.remove();
            resolve(resultado);
        }

        function alTeclear(evento) {
            if (evento.key === 'Escape') {
                cerrar(false);
            }
        }

        caja.querySelector('[data-accion="cancelar"]').addEventListener('click', () => cerrar(false));
        caja.querySelector('[data-accion="confirmar"]').addEventListener('click', () => cerrar(true));
        fondo.addEventListener('click', (evento) => {
            if (evento.target === fondo) {
                cerrar(false);
            }
        });
        document.addEventListener('keydown', alTeclear);

        caja.querySelector('[data-accion="confirmar"]').focus();
    });
}
