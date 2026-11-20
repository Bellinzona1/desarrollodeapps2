// Login, sesion y menu segun el rol (HU-01, HU-08).

import { test, expect } from '@playwright/test';
import { CLAVE, iniciarSesion, menu } from './ayuda.js';

test('sin sesion, una pantalla interna manda al login', async ({ page }) => {
    await page.goto('app/turnos.html');
    await expect(page).toHaveURL(/\/login\.html/);
    await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
});

test('con credenciales incorrectas se muestra el error y no se entra', async ({ page }) => {
    await page.goto('login.html');
    await page.locator('#usuario').fill('usuario-inexistente');
    await page.locator('#clave').fill('no-es-la-clave');
    await page.locator('#boton-ingresar').click();
    await expect(page.locator('#mensaje')).toBeVisible();
    await expect(page.locator('#mensaje')).toContainText('incorrectos');
    await expect(page).toHaveURL(/\/login\.html/);
});

test('recepcion entra y ve el menu de su rol', async ({ page }) => {
    await iniciarSesion(page, 'recepcion');
    const opciones = await menu(page);
    expect(opciones).toEqual(expect.arrayContaining(['Inicio', 'Turnos', 'Pacientes', 'Profesionales', 'Reserva']));
    expect(opciones).not.toContain('Mis turnos');
});

test('un paciente solo ve sus opciones y la API le niega la agenda completa', async ({ page }) => {
    await iniciarSesion(page, 'paciente');
    const opciones = await menu(page);
    expect(opciones).toEqual(expect.arrayContaining(['Inicio', 'Mis turnos', 'Reserva']));
    expect(opciones).not.toContain('Pacientes');
    expect(opciones).not.toContain('Turnos');

    // Aunque escriba la URL a mano, el servidor decide: la agenda completa es 403.
    const respuesta = page.waitForResponse((r) => r.url().endsWith('/api/turnos') && r.request().method() === 'GET');
    await page.goto('app/turnos.html');
    expect((await respuesta).status()).toBe(403);
});

test('cerrar sesion borra el token y vuelve al login', async ({ page }) => {
    await iniciarSesion(page, 'medico');
    await page.getByRole('button', { name: /salir|cerrar sesión/i }).first().click();
    await expect(page).toHaveURL(/\/login\.html/);
    await page.goto('app/index.html');
    await expect(page).toHaveURL(/\/login\.html/);
    expect(CLAVE).toBeTruthy();
});
