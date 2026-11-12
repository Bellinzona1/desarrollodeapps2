/**
 * Pantalla de login.
 *
 * Cambia usuario y contrasena por un JWT (POST /api/auth/login), lo guarda y
 * lleva al tablero. La contrasena la verifica el servidor contra un hash
 * PBKDF2; aca no se valida nada ni se deciden roles.
 */

import { api, contexto, guardarToken, leerToken, borrarToken, ErrorApi } from './api.js';

const formulario = document.getElementById('form-login');
const mensaje = document.getElementById('mensaje');
const boton = document.getElementById('boton-ingresar');

function mostrarError(texto) {
    mensaje.hidden = false;
    mensaje.textContent = texto;
}

function irAlTablero() {
    window.location.href = contexto + '/app/index.html';
}

// Si ya hay un token valido no tiene sentido volver a pedir credenciales.
if (leerToken()) {
    api.sesion()
        .then(sesion => { if (sesion && sesion.autenticado) { irAlTablero(); } })
        .catch(() => borrarToken());
}

formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    mensaje.hidden = true;

    const usuario = document.getElementById('usuario').value.trim();
    const clave = document.getElementById('clave').value;
    if (!usuario || !clave) {
        mostrarError('Ingresá usuario y contraseña.');
        return;
    }

    boton.disabled = true;
    boton.textContent = 'Ingresando...';
    try {
        const respuesta = await api.login(usuario, clave);
        guardarToken(respuesta.token);
        irAlTablero();
    } catch (error) {
        mostrarError(error instanceof ErrorApi && error.codigo === 401
            ? 'Usuario o contraseña incorrectos. Volvé a intentar.'
            : (error.message || 'No se pudo iniciar sesión.'));
        boton.disabled = false;
        boton.textContent = 'Ingresar';
    }
});
