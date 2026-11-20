# MediConecta — Registro de defectos

**Fecha:** 20/11/2026 · **Código:** `eda4b02` · Severidades según §9 de los *Criterios de Testing*.
Los Id de Jira deben completarse al cargar cada defecto (G5).

| Id | Jira | Severidad / Prioridad | Defecto | Pasos para reproducir | Esperado vs. obtenido | Causa raíz | Corrección y evidencia | Estado |
|---|---|---|---|---|---|---|---|---|
| D-01 | *(cargar)* | Crítica / Alta | IDOR en turnos | Paciente A: `GET /api/turnos/{id de B}` | 403 vs. 200 con el turno | Sin control de propiedad en `obtenerTurno` | `GestionTurnos`; `GestionTurnosTest.unPacienteSoloPuedeConsultarSusPropiosTurnos`, humo | Cerrado |
| D-02 | *(cargar)* | Crítica / Alta | BOLA en fichas | Paciente A: `GET /api/pacientes/{doc de B}` | 403 vs. 200 con la ficha | Recurso usaba la búsqueda interna | `GestionPacientes.consultarFicha`; `GestionPacientesTest`, humo | Cerrado |
| D-03 | *(cargar)* | Crítica / Alta | Secreto JWT y contraseña demo en el código | Leer `ServicioJwt.java` en `c285ce8` | Configuración externa vs. literal en el código | Valores de desarrollo por omisión | Secreto por entorno, sin valor por omisión; `ServicioJwtTest`. **El literal sigue en el historial** (riesgo G6) | Cerrado (riesgo residual) |
| D-04 | *(cargar)* | Mayor / Alta | Los MDB perdían mensajes ante un error | Enviar un mensaje que falla al procesar | Reintento y DLQ vs. mensaje descartado | `catch` que solo logueaba | `setRollbackOnly`, 4 intentos, DLQ; `ConsumoDeEventosTest` | Cerrado |
| D-05 | *(cargar)* | Mayor / Media | `@Remove` del asistente fallaba al vencer la sesión | Dejar vencer la sesión con una reserva abierta | Conversación cerrada vs. `EJBAccessException` en el log | `@RolesAllowed` de clase sin usuario al expirar | `@PermitAll` en `finalizar()`; revisión del log | Cerrado |
| D-06 | *(cargar)* | Menor / Media | Sin timeouts hacia externos | Partner que no responde | Corte a 5 s vs. hilo bloqueado | Clientes sin timeout | `TiemposDeEspera`, fallback; `FallbacksTest`, `TiemposDeEsperaTest` | Cerrado |
| D-07 | *(cargar)* | Mayor / Alta | Recurso ajeno devolvía 400 | Paciente A pide turno de B | 403/404 vs. 400 | Se usaba `ReglaNegocioException` para propiedad | `AccesoDenegadoException` → 403 (de `947ba9d`); `ProblemasTest`, humo | Cerrado |
| D-08 | *(cargar)* | Mayor / Alta | Documento sin validar | `POST /api/pacientes` con documento `{x}` o `<script>` | 400 vs. 500 en `Location` / dato basura | Sin formato de documento | Regex `[0-9]{6,10}` (de `947ba9d`); `GestionPacientesTest.rechazaDocumentosQueNoSonNumericosDe6a10Digitos` | Cerrado |
| D-09 | *(cargar)* | Bloqueante (CI) / Alta | `mvnw` sin permiso de ejecución tras la integración | Push de `4fa4efe` | CI verde vs. *Permission denied* en 3 jobs | Resolución en Windows (`core.filemode=false`) | Modo 755 en `9c4a39e`; CI run #3 | Cerrado |
| D-10 | *(cargar)* | Menor / Media | Verificación del humo con expectativa vieja (400) | CI run #3 | 403 esperado vs. script esperando 400 | Expectativa no actualizada | `eda4b02`; humo 148/148 en run #4 | Cerrado |

## Observaciones abiertas (no son defectos del flujo principal)

| Id | Severidad propuesta | Observación | Acción |
|---|---|---|---|
| O-01 | Mayor | Sin reintento acotado ni circuit breaker ante externos (eje C) | Portar `ProteccionIntegracion` de `947ba9d` o equivalente |
| O-02 | Menor | `SondaMensajeria.responde()` y `MonitorSalud.verificar()` sin `@RolesAllowed`/`@PermitAll` | Anotar explícitamente |
| O-03 | Menor | Capturas de la presentación y el informe son de la interfaz anterior (17/09) | Tomar capturas nuevas |

Defectos abiertos Bloqueantes o Críticos: **0 en el registro histórico anterior**; no acredita el estado del release actual. Ver la auditoría posterior y contrastar con Jira.

## Auditoría posterior del 20/11/2026 (base `0ae0371`)

Los IDs siguientes son locales, todavía sin ticket Jira ni responsable designado.

| Id | Severidad / prioridad | Reproducción; esperado vs. observado | Causa raíz | Corrección / evidencia | Estado |
|---|---|---|---|---|---|
| D-11 | Mayor / Alta | Ejecutar CI sin SONAR_TOKEN: debería impedir acreditar Quality Gate; el job terminaba exitosamente sin análisis | Paso informativo en vez de fallo obligatorio | CI exige token, organización y proyecto. Validado YAML; falta ejecución remota con Sonar | Corregido localmente, validación remota pendiente |
| D-12 | Mayor / Alta | Escanear imagen con HIGH/CRITICAL: debería bloquear; no tenía exit-code 1 y ocultaba vulnerabilidades sin arreglo | Gate SCA incompleto; shell implícito no garantiza pipefail en pipelines con tee | Trivy exige exit-code 1, sin ignore-unfixed; shell bash explícito activa pipefail en Actions | Corregido localmente, escaneo pendiente |
| D-13 | Mayor / Alta | Comparar checklist con contrato/tests/evidencias: declaraba todos los ejes cumplidos, aunque no había validación de esquemas ni trazabilidad completa | Se confundía configuración con evidencia; documentos de diferentes revisiones | Checklist sustituido por auditoría criterio por criterio y avisos en documentos históricos | Corregido el checklist; paquete PDF y matriz pendientes |
| D-14 | Mayor / Alta | Ejecutar verify sin Docker: 6 IT omitidos y build verde; no sirve para acreditar integración | disabledWithoutDocker=true sin control del resultado en CI | Nuevo verificador de tres inventarios JUnit rechaza omisiones y ausencia de IT; se probaron sus caminos de aceptación/rechazo | Corregido localmente |
| D-15 | Mayor / Media | Un endpoint lento podía quedar diluido entre los restantes: el umbral de k6 era global | Falta segmentación por endpoint | Etiquetas y umbrales para agenda, padrón y tablero; sintaxis JS validada | Corregido localmente, carga completa pendiente |

O-01 está superada en el código actual por `DisyuntorDeIntegracion`, probado con JUnit.
Las demás brechas de [Checklist_Requisitos_Finales.md](Checklist_Requisitos_Finales.md) requieren
triage, responsable y tickets; no son un diagnóstico automático de vulnerabilidades explotables.
