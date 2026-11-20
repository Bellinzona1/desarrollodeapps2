// XSS en pantalla, cabeceras de seguridad y diseno responsive (RNF de seguridad y usabilidad).

import { test, expect } from '@playwright/test';
import { iniciarSesion, dniNuevo } from './ayuda.js';

test('un nombre con HTML se muestra como texto y no ejecuta nada (XSS)', async ({ page }) => {
    const dni = dniNuevo();
    const malicioso = '<img src=x onerror="window.__xss=1">Ana E2E';

    await iniciarSesion(page, 'recepcion');
    await page.goto('app/pacientes.html');
    await page.locator('#abrir-alta').click();
    await page.locator('#nombre').fill(malicioso);
    await page.locator('#documento').fill(dni);
    await page.locator('#guardar').click();
    await expect(page.locator('#card-alta')).toBeHidden();

    await page.locator('#busqueda').fill(dni);
    const fila = page.locator('tr', { hasText: dni });
    await expect(fila).toContainText(malicioso);
    await expect(fila.locator('img')).toHaveCount(0);
    expect(await page.evaluate(() => window.__xss)).toBeUndefined();
});

test('las pantallas salen con CSP estricta y cabeceras de seguridad', async ({ page }) => {
    const respuesta = await page.goto('login.html');
    const cabeceras = respuesta.headers();
    expect(cabeceras['content-security-policy']).toContain("script-src 'self'");
    expect(cabeceras['content-security-policy']).toContain("frame-ancestors 'none'");
    expect(cabeceras['x-content-type-options']).toBe('nosniff');
    expect(cabeceras['x-frame-options']).toBe('DENY');
});

for (const ancho of [360, 768, 1280]) {
    test(`responsive a ${ancho} px: sin desborde horizontal`, async ({ page }) => {
        await page.setViewportSize({ width: ancho, height: 800 });
        await page.goto('login.html');
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(ancho);

        await iniciarSesion(page, 'recepcion');
        for (const pantalla of ['app/index.html', 'app/turnos.html', 'app/pacientes.html', 'app/reserva.html']) {
            await page.goto(pantalla);
            await expect(page.locator('#topbar')).not.toBeEmpty();
            await page.waitForLoadState('networkidle');
            const desborde = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
            expect(desborde, pantalla + ' desborda ' + desborde + ' px').toBeLessThanOrEqual(0);
        }
    });
}
