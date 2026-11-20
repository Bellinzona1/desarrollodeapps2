# MediConecta — Checklist de requisitos finales

**Fecha:** 20/11/2026 · **Rama:** `entrega-final` (sobre `integracion-final`) · Fuente: *Criterios de Testing y
Aseguramiento de Calidad* (Clase 15).
Estados: CUMPLE (evidencia ejecutada) · PARCIAL · NO CUMPLE · PENDIENTE (acción del equipo, fuera del código).

## Condiciones de admisibilidad (eliminatorias)

| | Condición | Estado | Evidencia | Acción pendiente | Responsable |
|---|---|---|---|---|---|
| G1 | Build reproducible, un comando, ≤ 15 min | CUMPLE | `./mvnw clean verify` desde una copia limpia; `docker compose up -d --build` | — | — |
| G2 | Tag en `main` | PENDIENTE | — | Mergear `entrega-final` a `main` y crear `v1.0-final` (requiere permiso de escritura en el repo del equipo) | dueño del repo |
| G3 | CI en verde sobre el tag | PARCIAL | CI en verde en la rama; Sonar sin token | Re-ejecutar sobre el tag; cargar `SONAR_TOKEN` | dueño del repo |
| G4 | Plan, matriz, informe de ejecución, defectos en Teams | PENDIENTE (subida) | `documentacion-final/`, `docs/Historias_de_Usuario.md`, `docs/BRD.md`, `docs/adr/` | Subir a Teams | equipo |
| G5 | Jira actualizado | PENDIENTE | — | Cargar HU-01…HU-12, casos y defectos D-01…D-10 | QA |
| G6 | Sin secretos en el historial | CUMPLE | gitleaks 0; sin contraseñas por omisión en scripts ni CI | El secreto JWT de desarrollo antiguo del historial ya no se usa en ningún entorno (el secreto sale de la configuración o se genera al azar) | — |
| G7 | Demo en vivo | CUMPLE (técnico) | Docker Compose + humo + E2E en el CI; `scripts/db/reiniciar-datos-demo.sql` | Ensayar con los 5 integrantes | equipo |
| G8 | Declaración de IA | PENDIENTE (firmas) | `documentacion-final/Declaracion_Uso_IA_Final.md` (única vigente) | Firmar | cada integrante |
| G9 | Honestidad del informe | CUMPLE | Este checklist y el registro de defectos declaran lo pendiente | Contrastar con Jira | QA |

## Ejes

| Eje | Requisito | Estado | Evidencia |
|---|---|---|---|
| A | HU con ≥ 3 criterios Gherkin, MoSCoW | CUMPLE | [`docs/Historias_de_Usuario.md`](../docs/Historias_de_Usuario.md): 12 HU, 3–4 escenarios cada una |
| A | BRD y decisiones de arquitectura | CUMPLE | [`docs/BRD.md`](../docs/BRD.md), [`docs/adr/`](../docs/adr/README.md) (10 ADR) |
| A | ≥ 5 escenarios E2E de interfaz en CI | CUMPLE | `pruebas/e2e` (Playwright): 12 escenarios en el job `punta-a-punta`, pasan 12/12 después del humo (151/151) en la imagen del CI |
| A | Responsive 360/768/1280 | CUMPLE | E2E `responsive a 360/768/1280 px` sobre inicio, agenda, pacientes y reserva |
| A | Lighthouse ≥ 90 | CUMPLE (login) | Lighthouse 12 sobre `login.html` (20/11): rendimiento 99, accesibilidad 100, buenas prácticas 96, SEO 90. Las pantallas internas requieren sesión: medir desde DevTools ya logueado |
| A | Prueba del manual de usuario | PARCIAL | Los E2E recorren los flujos del manual; falta el ensayo manual del equipo |
| B | Cobertura negocio ≥ 80/70 | CUMPLE | JaCoCo 95,0 / 82,6 (el build corta por debajo) |
| B | Mutation ≥ 60 % | CUMPLE | PIT 85 % (job `mutacion`) |
| B | Quality Gate Sonar | PENDIENTE | Job listo; falta `SONAR_TOKEN` y las variables `SONAR_ORGANIZATION`/`SONAR_PROJECT_KEY` |
| B | Testcontainers/Arquillian | CUMPLE | `PersistenciaPostgresIT`: PostgreSQL 16 real, Flyway, Hibernate 6.6.7, 6 tests en `mvn verify` |
| B | Colección de API en CI | CUMPLE | `scripts/smoke-test.sh`: 151 pruebas sobre la API en cada push |
| B | Contrato OpenAPI + WSDL | CUMPLE | `ContratoOpenApiTest`; BOLA documentado como 403; WSDL publicado y probado (válido, XXE) |
| B | 409 en transiciones inválidas | CUMPLE | `EstadoDelTurnoTest` + humo |
| B | Pipeline ≤ 15 min | CUMPLE | Jobs en paralelo; el más largo (humo + E2E + carga 5 min + ZAP) < 15 min |
| B | 0 flaky (3 corridas) | CUMPLE | `./mvnw clean verify` 3 veces seguidas (20/11): 207 unitarios + 6 de integración, 0 fallas en las 3 |
| C | Timeout + reintento/circuit breaker + fallback | CUMPLE | `DisyuntorDeIntegracion` (3 fallas → abierto 30 s → semiabierto), `FallbacksTest`, `DisyuntorDeIntegracionTest` |
| C | Broker caído | CUMPLE | Prueba del 20/11 con la cola pausada: tras `kill -9` (turno 4) y tras **recrear** el contenedor (turno 5) llegó la CONFIRMACION pendiente. Journal de Artemis en el volumen `datos-wildfly` |
| C | Duplicado, DLQ, rollback, Idempotency-Key, ETag, Problem Details | CUMPLE | Tests + humo + `PersistenciaPostgresIT` (UNIQUE y `@Version` en la base real) |
| C | Carga 50 usuarios × 5 min | CUMPLE | `carga.js` por omisión 50 × 5 min; medido: p95 20,95 ms, 0 errores en 189.976 pedidos |
| D | BOLA, BFLA, JWT, inyección, 429, cabeceras, errores | CUMPLE | Humo + tests + E2E (XSS en pantalla, CSP) |
| D | XXE | CUMPLE | Humo: entidad externa y bomba de entidades rechazadas con SOAP Fault |
| D | SCA | CUMPLE | Job `dependencias` (Trivy): 0 HIGH/CRITICAL con arreglo en `pom.xml` y `package-lock.json` |
| D | ZAP activo autenticado | CUMPLE | Job `zap-activo`: ZAP API scan con JWT sobre `openapi.json`, corta ante High |
| E | Declaración y plan de IA | PENDIENTE (firmas) | `Declaracion_Uso_IA_Final.md` |
| E | Testing de la IA | CUMPLE | `pruebas/ia/evaluar-ia.sh` (12 casos) + humo (gateway, emergencia, fallback, 429) + circuit breaker |

**Veredicto:** el código y la documentación técnica cumplen todos los ejes verificables desde el repositorio.
Quedan solo acciones del equipo fuera del código: merge a `main` con el tag `v1.0-final` (G2/G3), Jira (G5),
firmas de la declaración de IA (G8), subir los documentos a Teams (G4) y el token de SonarCloud.
