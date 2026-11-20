-- =============================================================================
--  MediConecta - volver al juego de datos de demostracion
-- =============================================================================
--
--  Las pruebas de punta a punta (scripts/smoke-test.sh) crean pacientes,
--  profesionales y turnos en cada corrida (por ejemplo el paciente
--  "<script>alert(1)</script>" de la prueba de XSS). Antes de una demo, este
--  script deja la base como recien instalada:
--
--    * borra turnos, pacientes, profesionales, avisos, mensajes fallidos y
--      claves de idempotencia;
--    * conserva los usuarios y el historial de migraciones (Flyway).
--
--  Despues hay que redesplegar el WAR: CargaDeDatosDemo ve la base vacia y
--  vuelve a cargar los 4 profesionales, 4 pacientes y 3 turnos de ejemplo.
--
--      docker exec -i mediconecta-postgres psql -U mediconecta -d mediconecta < scripts/db/reiniciar-datos-demo.sql
--      (Docker Compose: docker compose exec -T db psql -U mediconecta -d mediconecta < scripts/db/reiniciar-datos-demo.sql
--       y luego docker compose restart app)
-- =============================================================================

TRUNCATE claves_idempotencia, notificaciones, mensajes_fallidos, turnos, disponibilidades,
         profesionales, pacientes RESTART IDENTITY CASCADE;
