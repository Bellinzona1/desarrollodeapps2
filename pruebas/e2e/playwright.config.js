// =============================================================================
//  MediConecta - pruebas E2E de la interfaz (Playwright)
// =============================================================================
//
//  Manejan un navegador real (Chromium) contra la aplicacion desplegada, igual
//  que un usuario: login, menu segun el rol, reserva completa con el asistente
//  @Stateful, cancelacion, XSS en pantalla y diseno responsive.
//
//      cd pruebas/e2e && npm ci && npx playwright install chromium
//      BASE_URL=http://localhost:8080/mediconecta MEDICONECTA_DEMO_CLAVE=... npx playwright test
//
//  En el CI corren dentro de la imagen oficial de Playwright, contra Docker
//  Compose, despues de la prueba de humo. El informe HTML queda en informe/.
// =============================================================================

import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
    testDir: './pruebas',
    timeout: 60_000,
    expect: { timeout: 10_000 },
    // Las pruebas comparten la base de datos (turnos, pacientes): en serie.
    workers: 1,
    fullyParallel: false,
    retries: 0,
    forbidOnly: !!process.env.CI,
    // En el CI, el reporter "github" deja cada falla como anotacion del job.
    reporter: process.env.CI
        ? [['github'], ['list'], ['html', { open: 'never', outputFolder: 'informe' }]]
        : [['list'], ['html', { open: 'never', outputFolder: 'informe' }]],
    use: {
        baseURL: (process.env.BASE_URL || 'http://localhost:8080/mediconecta') + '/',
        locale: 'es-AR',
        timezoneId: 'America/Argentina/Buenos_Aires',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
    },
    projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
