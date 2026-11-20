// Utilidades compartidas por las pruebas E2E.

import { expect } from '@playwright/test';

export const CLAVE = process.env.MEDICONECTA_DEMO_CLAVE;
if (!CLAVE) {
    throw new Error('Defini MEDICONECTA_DEMO_CLAVE con la contrasena de los usuarios de demo');
}

/** Inicia sesion por la pantalla de login, como un usuario. */
export async function iniciarSesion(page, usuario) {
    await page.goto('login.html');
    await page.locator('#usuario').fill(usuario);
    await page.locator('#clave').fill(CLAVE);
    await page.locator('#boton-ingresar').click();
    await expect(page).toHaveURL(/\/app\/index\.html$/);
    await expect(page.locator('#sidebar')).toContainText('Inicio');
}

/** Textos de los enlaces del menu lateral. */
export async function menu(page) {
    return (await page.locator('#sidebar a').allInnerTexts()).map((t) => t.trim()).filter(Boolean);
}

/** DNI de 8 digitos que no choca con los de la carga de demo. */
export function dniNuevo() {
    return String(70_000_000 + Math.floor(Math.random() * 9_999_999));
}
