// Reserva completa con el asistente @Stateful y cancelacion (HU-02, HU-04).

import { test, expect } from '@playwright/test';
import { iniciarSesion } from './ayuda.js';

test.describe.serial('reserva y cancelacion de un turno', () => {
    let turnoId;

    test('un paciente reserva un turno con el asistente paso a paso', async ({ page }) => {
        await iniciarSesion(page, 'paciente');
        await page.goto('app/reserva.html');

        // Paso 1: especialidad.
        await expect(page.locator('#paso h2')).toHaveText('Elegí una especialidad');
        await page.locator('#paso .opcion[data-valor]').first().click();

        // Paso 2: profesional.
        await expect(page.locator('#paso h2')).toHaveText('Elegí un profesional');
        await page.locator('#paso .opcion').first().click();

        // Paso 3: horario libre.
        await expect(page.locator('#paso h2')).toHaveText('Elegí un horario disponible');
        await page.locator('#horarios .horario').first().click();

        // Paso 4: confirmar.
        await expect(page.locator('#paso h2')).toHaveText('Revisá los datos antes de confirmar');
        await page.getByRole('button', { name: 'Confirmar reserva' }).click();

        await expect(page.locator('#paso h2')).toHaveText('Reserva confirmada');
        const aviso = await page.locator('#paso .aviso--exito').innerText();
        turnoId = aviso.match(/Turno #(\d+)/)[1];

        // El turno aparece en "Mis turnos" del paciente.
        await page.goto('app/mis-turnos.html');
        await expect(page.locator('main')).toContainText('#' + turnoId);
    });

    test('recepcion cancela el turno y queda en estado Cancelado', async ({ page }) => {
        test.skip(!turnoId, 'depende de la reserva anterior');
        await iniciarSesion(page, 'recepcion');
        await page.goto('app/turno.html?id=' + turnoId);
        await expect(page.locator('#estado')).toContainText('Programado');

        await page.locator('#boton-cancelar').click();
        await page.locator('.modal [data-accion="confirmar"]').click();

        await expect(page.locator('#estado')).toContainText('Cancelado');
        await expect(page.locator('#boton-archivar')).toBeVisible();
        await expect(page.locator('#boton-cancelar')).toBeHidden();
    });
});
