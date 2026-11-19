# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MediConecta: a medical appointment system ("gestión de turnos") built as a university TP (UADE, Desarrollo de Aplicaciones II). It is a **Jakarta EE 10 WAR deployed on WildFly** with PostgreSQL. All code, identifiers, comments, docs and commit messages are in **Spanish** — keep new code and docs in Spanish and follow the existing naming (`Gestion*`, `Repositorio*`, `Recurso*`, `Manejador*`).

The deliverables are graded on Jakarta EE concepts (stateless vs stateful EJBs, layered architecture, design patterns, declarative security/transactions, JMS, SOAP/REST integration). Design choices in the code are intentionally tied to those criteria and are justified in `docs/arquitectura.md` and `docs/Entrega_Obligatoria_2.md` — read those before restructuring anything, and update them when behaviour changes.

## Commands

```powershell
.\mvnw.cmd clean verify               # Maven Wrapper (Maven 3.9.9): unit tests + JaCoCo gate (80% lines / 70% branches) + target\mediconecta.war
.\mvnw.cmd test -Dtest=GestionTurnosTest#cancelarLiberaElHorario   # single unit test (class or Class#method)
.\mvnw.cmd test-compile org.pitest:pitest-maven:mutationCoverage    # PIT mutation testing (threshold 60%)
python scripts\carga\prueba-carga.py --usuarios 20 --segundos 60   # load test, fails if p95 >= 500 ms (use 127.0.0.1 on Windows)
.\scripts\db\levantar-postgres.ps1     # PostgreSQL 16 in Docker (db/user/pass: mediconecta)
.\scripts\wildfly\configurar-wildfly.ps1 -WildFly "C:\wildfly-..." [-UrlBase jdbc:postgresql://127.0.0.1:5433/mediconecta]   # JDBC module + datasource + integrated-jaspi=false
<wildfly>\bin\standalone.bat -c standalone-full.xml                  # REQUIRED profile (JMS); plain standalone.xml fails to deploy
copy target\mediconecta.war <wildfly>\standalone\deployments\
.\scripts\smoke-test.ps1 [-BaseUrl ...] [-LogWildFly <wildfly>\standalone\log\server.log]   # end-to-end API checks (+ JMS via log)
```

- Unit tests (`src/test/java`, JUnit 5, no container/DB): EJBs are instantiated as plain objects, fields set via the public `negocio.Inyeccion` helper (reflection + a proxy `SessionContext`), with the `datos.memoria` repositories; `negocio.Escenario` wires all business EJBs with a switchable caller (`como(usuario, roles...)`). `presentacion.ApiRestTest` runs the real JAX-RS resources/mappers on RESTEasy's in-memory `MockDispatcherFactory`. Test-scoped only: parsson, resteasy-client, resteasy-json-binding-provider. `AutorizacionDeclaradaTest` fails if a public EJB method lacks `@RolesAllowed`/`@PermitAll` (BFLA guard) — add new non-entry-point EJBs to its whitelist deliberately.
- CI: `.github/workflows/ci.yml` (build+coverage, PIT, gitleaks over full history, optional SonarCloud gated on `SONAR_TOKEN`).
- `scripts/smoke-test.ps1` (PowerShell 5.1, `Probar`/`Invocar`/`Abrir-Sesion` helpers) needs a running WildFly + DB; `Abrir-Sesion` logs in via JWT and puts the token in the `WebRequestSession` headers.
- On this dev machine port 5432 belongs to another project's Postgres and `localhost:5433` resolves to a WSL Postgres on `::1` — run the MediConecta container on 5433 and use `127.0.0.1`.
- Health check: `GET /mediconecta/api/salud` (public).
- Demo users live in the `usuarios` table (PBKDF2 hash), seeded by `CargaDeDatosDemo`: `paciente`, `recepcion`, `medico`, `admin`, all `Demo.2026`. `paciente` is linked to patient DNI 30111222 via `pacientes.usuario`, `medico` to Dra. Laura Fernandez via `profesionales.usuario`.
- The only runtime dependency is `jakarta.jakartaee-api:10.0.0` with scope `provided` (JUnit/parsson are `test`). Do not add implementation libraries (Hibernate, RESTEasy, etc.) to the WAR — WildFly provides them and duplicates break deployment.

## Architecture

Strict three layers under `com.mediconecta`, each depending only on the one below:

- `presentacion` — thin JAX-RS resources (`@ApplicationPath("/api")` in `AplicacionRest`), input DTOs (`PedidoTurno`, `PasoReserva`), `ExceptionMapper`s, `RecursoAutenticacion` (`POST /api/auth/login` → JWT). **No business `if`s here.**
- `negocio` — `@Stateless` EJBs `GestionTurnos`, `GestionPacientes`, `GestionProfesionales`, `FachadaReservas`; the `@Stateful` `AsistenteReservaTurno`; `FabricaTurnos`; domain exceptions; `CargaDeDatosDemo` (idempotent seed data at startup).
- `datos` — repository interfaces; `datos.jpa` implementations are active, `datos.memoria` ones are `@Alternative` (plan B without DB: uncomment the plan-B block in `beans.xml` — `<alternatives>` plus `<scan><exclude>` of `datos.jpa` — and remove `persistence.xml`).
- `modelo` — JPA entities shared by all layers. The only behaviour is the **State pattern** in `modelo.estado`: `Turno.cancelar/atender/archivar()` delegate to `EstadoTurno.comportamiento()`; invalid transitions throw `TransicionInvalidaException` → **409**. Never change a turno's state with `setEstado` outside the state classes / `FabricaTurnos`. Relations are **unidirectional** (Turno → Paciente/Profesional) so entities serialize directly to JSON without output DTOs; keep it that way to avoid cycles.
- `seguridad` — JWT (HS256, JDK + JSON-P only): `ServicioJwt` emits/validates, `MecanismoAutenticacionJwt` is a Jakarta Security `HttpAuthenticationMechanism` that calls `notifyContainerAboutLogin(user, groups)`. Secret from `-Dmediconecta.jwt.secreto` / `MEDICONECTA_JWT_SECRETO` (dev fallback logs a warning).
- `mensajeria` (Entrega 2) — `DestinosJms` declares queue `ColaConfirmaciones` and topic `TopicoRecordatorios` via `@JMSDestinationDefinition`; `NotificadorTurnos` sends through `java:/JmsXA` (XA, enlisted in the JTA tx); three MDBs only log.
- `integraciones` (Entrega 2) — Adapter pattern: `ValidadorCobertura` interface, `AdaptadorObraSocialLegado` (SOAP client) and `AdaptadorObraSocialPartner` (REST client), `GestorCobertura` picks one by obra social (PAMI/OSDE/IOMA → legacy SOAP, others → partner REST). `integraciones.simulacion` holds the simulated external systems inside the same WAR: a `@WebService` EJB (`/ServicioObraSocialLegadoImpl?wsdl`) and a REST app under `@ApplicationPath("/partner")`, deliberately outside the security constraint. Adapters must call them via URL/WSDL, never by direct class reference. Adapters need `@LocalBean` because `GestorCobertura` injects them by concrete class.
- `integraciones` — AI specialty assistant (step 1 of the wizard, "Prefiero escribirlo"): `GestorOrientacion` (`@Stateless`, `@RolesAllowed` PACIENTE/RECEPCION/ADMIN) validates the client-sent conversation (`MensajeChat`, stateless — nothing stored server-side), passes the active specialties to `OrientadorEspecialidad` (Adapter) and discards any suggested specialty not in that list. `AdaptadorIaGroq` calls an OpenAI-compatible chat-completions API (Groq by default) with `response_format=json_object`; key from `-Dmediconecta.ia.clave` / `MEDICONECTA_IA_CLAVE` (never in the repo or the browser), optional `mediconecta.ia.modelo` / `mediconecta.ia.url`. Endpoint `POST /api/reserva/orientacion`; picking the suggestion still goes through `PUT /api/reserva/especialidad`.

Checkpoint 3 additions (see `docs/plan-de-pruebas.md`, `docs/matriz-trazabilidad.md`):

- Errors are **Problem Details** (`application/problem+json`, `presentacion.Problemas`/`DetalleProblema`), keeping `error`/`codigo` as extension members for old clients. New mappers: `AccesoDenegadoException` (ownership/BOLA, extends `ReglaNegocioException`) → 403, `DemasiadosIntentosException` → 429 + `Retry-After`, `IdempotenciaException` → 409/422, `ManejadorErrorInesperado` (Throwable) → JAX-RS status or 500 without internal detail.
- `POST /api/turnos` supports `Idempotency-Key` via `FachadaReservas.reservarIdempotente` (table `claves_idempotencia`, unique per user+key, 24 h, cleaned by `LimpiezaClavesIdempotencia` timer). `ETag`/`If-None-Match`/`If-Match` via `presentacion.Etiquetas` (content hash, no `@Version` column) on turnos and pacientes.
- External calls go through `integraciones.ProteccionIntegracion` (5 s hard timeout on `ManagedExecutorService`, circuit breaker 3 failures/30 s, caller-supplied fallback). Adapters are `@TransactionAttribute(NOT_SUPPORTED)`. Fallbacks: `MemoriaCobertura` (last good result, `contingencia=true`) / "SIN_VERIFICAR"; IA → `OrientadorPorPalabrasClave` (`@Vetoed`, `origen: REGLAS`).
- JMS: PERSISTENT messages with property `claveIdempotencia`; MDBs go through `mensajeria.ConsumidorIdempotente` (table `mensajes_procesados`); malformed messages throw → redelivery → `ColaConfirmacionesDLQ` (`MonitorColaMuerta`); topic MDBs use durable subscriptions. Broker settings (3 attempts, DLQ) in `scripts/wildfly/configurar-mensajeria.cli`. ADMIN diagnostics at `/api/mensajeria/*`.
- Login brute force (`seguridad.ProteccionAbuso`): 5 failures/15 min per account, 50 per IP → 429; IA: 10 requests/min per user. In-memory, single node.
- `FiltroCabecerasSeguridad` adds CSP/nosniff/X-Frame-Options; `FiltroCacheApi` defaults API responses to `no-store`. Frontend must keep using module scripts (CSP `script-src 'self'`, no inline JS).
- JWT secret: no hardcoded fallback anymore — without config a random one is generated per boot (tokens die on restart).
- 401s: `MecanismoAutenticacionJwt` sets request attribute `ATRIBUTO_MOTIVO` and returns `responseUnauthorized()`; the Problem Details body is written by `seguridad.RespuestaNoAutenticado` (`<error-page>` 401 in `web.xml`). Never write+flush the response in the mechanism (Undertow then logs a stack trace per request).
- `Paciente.documento` must be a 6–10 digit DNI (`GestionPacientes.FORMATO_DOCUMENTO`); it is part of resource URLs. Build `Location` headers with `path("{x}").build(valor)`, never `path(valor)`.
- No inline `style="..."` or inline scripts in the frontend: CSP is `script-src 'self'; style-src 'self'` (use the `u-*` utility classes in `styles.css`). `JSESSIONID` gets `SameSite=Strict` from `WEB-INF/undertow-handlers.conf` (no comments allowed in that file).
- Security/IA tooling: `scripts/seguridad/pruebas-seguridad.py`, `scripts/seguridad/zap-api.yaml`, `scripts/ia/proveedor-ia-simulado.py`.

Key cross-cutting rules:

- **Authorization lives in the business layer** (`@RolesAllowed` on EJB methods, role names in `negocio.Roles`), not on REST resources, so it applies to every caller. Authentication is a JWT in `Authorization: Bearer` (no `<login-config>`; `jboss-web.xml` binds the WAR to security domain `other`, which needs `integrated-jaspi=false`). Public: `/api/salud`, `/api/auth/*`, and the static `/app/*` pages (JS redirects to `login.html` without a token). Ownership checks that roles can't express (a PACIENTE seeing only their own data, a MEDICO editing only their own availability) are done programmatically via `SessionContext.getCallerPrincipal()` — e.g. `GestionPacientes.exigirPropioDocumentoSiEsPaciente`.
- Errors: business code throws `ReglaNegocioException` / `NoEncontradoException` / `IntegracionExternaException` (no HTTP knowledge; `IntegracionExternaException` is `rollback = true`, the others `rollback = false`); mappers turn them into Problem Details with 400/404/502; `TransicionInvalidaException` → 409; `CredencialesInvalidasException` and missing/invalid token → 401; `EJBAccessException` and `AccesoDenegadoException` → 403. Ownership checks must throw `AccesoDenegadoException`, not plain `ReglaNegocioException`.
- Transactions are container-managed JTA per EJB method; repositories never begin/commit. `FachadaReservas.reservar()` is the single entry point for booking (used by both `POST /api/turnos` and the stateful wizard) and runs patient resolution, turno creation and JMS sends in one transaction.
- Every `Turno` must be created through `FabricaTurnos.crear(...)` (forces `PROGRAMADO`, truncates `fechaHora` to the minute, checks specialty coherence). Bypassing it breaks the overlap rule.
- The stateful wizard is bound to the HTTP session by `ConversacionDeReserva` (`@SessionScoped`, holds `Instance<AsistenteReservaTurno>` so it can destroy and recreate it).
- `Turno.fechaHora` uses `@JsonbDateFormat("yyyy-MM-dd'T'HH:mm")` to keep the API contract without seconds. `POST /api/turnos` accepts both the legacy body (embedded paciente + profesional name) and the short one (`documentoPaciente`, `profesionalId`, `fechaHora`) — see `PedidoTurno`.
- Schema is generated by Hibernate (`hbm2ddl.auto=update`); `scripts/db/esquema.sql` documents the equivalent DDL — keep it in sync with entity changes. Datasource `java:/jdbc/MediConectaDS` is defined in WildFly, never in the repo.

## Frontend

`src/main/webapp`: plain HTML + CSS + native ES modules, no framework, no build step (intentional). One HTML page + one JS module per screen under `app/` and `js/`; shared modules `api.js` (derives context root from `import.meta.url`, keeps the JWT in `sessionStorage` and sends it as `Authorization`), `ui.js`, `layout.js` (draws sidebar/topbar). Escape all server data with `esc()` before inserting into HTML.

## Docs

`README.md` (setup, full REST endpoint/role table), `docs/arquitectura.md` (layers, patterns, security matrix), `docs/Entrega_Obligatoria_{1,2}.md` (per-deliverable write-ups; Entrega 2 §9 maps the 13/11 requirements to evidence). `arquitectura.md` §5.1/§5.5 still describe the old FORM login on purpose (Entrega 1 history).
