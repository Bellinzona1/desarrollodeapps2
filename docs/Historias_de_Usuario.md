# MediConecta — Historias de usuario

Historias derivadas de los casos de uso ([casos-de-uso.md](casos-de-uso.md)) y del
[BRD](BRD.md). Cada una tiene prioridad **MoSCoW**, al menos tres criterios de aceptación
en **Gherkin** (en español) y la evidencia que la verifica. La
[matriz de trazabilidad](../documentacion-final/Matriz_de_Trazabilidad_Final.md) usa estos ids.

| Prioridad | Historias |
|---|---|
| **Must** (sin esto no hay producto) | HU-01, HU-02, HU-04, HU-05, HU-07, HU-08 |
| **Should** (importante, con alternativa manual) | HU-03, HU-09, HU-10, HU-12 |
| **Could** (agrega valor, prescindible) | HU-06, HU-11 |
| **Won't** (fuera de alcance de esta versión) | pagos en línea, telemedicina, envío real de e-mail/SMS, app móvil nativa |

Evidencia: **U** = test unitario (JUnit), **I** = test de integración con PostgreSQL real
(Testcontainers), **H** = prueba de humo de punta a punta (`scripts/smoke-test.sh`),
**E** = prueba E2E de interfaz (Playwright, `pruebas/e2e`).

---

## HU-01 — Iniciar sesión · Must · CU transversal

**Como** usuario de la clínica (paciente, recepción, médico o administrador)
**quiero** iniciar sesión con mi usuario y contraseña
**para** usar solo las funciones de mi rol.

```gherkin
Escenario: credenciales correctas
  Dado el usuario "recepcion" con su contraseña
  Cuando inicia sesión
  Entonces recibe un JWT firmado con sus roles
  Y ve el menú de recepción (Turnos, Pacientes, Profesionales, Reserva)

Escenario: credenciales incorrectas
  Dado un usuario con una contraseña equivocada
  Cuando inicia sesión
  Entonces la API responde 401 en formato Problem Details
  Y la pantalla muestra "Usuario o contraseña incorrectos"

Escenario: fuerza bruta
  Dado que un usuario falló 5 veces en los últimos 5 minutos
  Cuando intenta por sexta vez
  Entonces la API responde 429 con la cabecera Retry-After

Escenario: pantalla interna sin sesión
  Dado que no hay sesión iniciada
  Cuando se abre /app/turnos.html
  Entonces se redirige a la pantalla de login
```

Evidencia: U `ServicioAutenticacionTest`, `ServicioJwtTest` · H "SEGURIDAD", "SEGURIDAD EN VIVO" ·
E `sesion-y-roles.spec.js`.

## HU-02 — Reservar un turno con el asistente · Must · CU-02

**Como** paciente
**quiero** reservar un turno eligiendo especialidad, profesional y horario paso a paso
**para** no tener que llamar a la clínica.

```gherkin
Escenario: reserva completa
  Dado el paciente con DNI 30111222 en sesión
  Cuando elige especialidad, profesional y un horario libre y confirma
  Entonces se crea un turno PROGRAMADO
  Y aparece en "Mis turnos"

Escenario: el horario ya fue tomado
  Dado un horario que otro paciente reservó mientras tanto
  Cuando el paciente confirma ese horario
  Entonces la API rechaza la reserva y no hay dos turnos vigentes en el mismo horario

Escenario: especialidad que no corresponde al profesional
  Dado un profesional de Cardiología
  Cuando se pide un turno de Pediatría con ese profesional
  Entonces la API responde 400 "no corresponde al profesional seleccionado"

Escenario: el asistente vive en la sesión
  Dado que el paciente está en el paso de horario
  Cuando recarga la página
  Entonces el asistente @Stateful sigue en el mismo paso
```

Evidencia: U `AsistenteReservaTurnoTest`, `GestionTurnosTest`, `FabricaTurnosTest` · I `PersistenciaPostgresIT` ·
H "ASISTENTE DE RESERVA (@Stateful)", "TURNOS" · E `reserva.spec.js`.

## HU-03 — Reservar sin duplicar ante reintentos · Should · CU-01

**Como** integrador (app o recepción con mala conexión)
**quiero** reintentar un `POST /api/turnos` sin riesgo
**para** no crear dos turnos si se cortó la respuesta.

```gherkin
Escenario: reintento con la misma Idempotency-Key
  Dado un turno creado con Idempotency-Key "k1"
  Cuando se repite el mismo pedido con "k1"
  Entonces se devuelve el mismo turno con la cabecera Idempotent-Replayed: true

Escenario: misma clave con otro cuerpo
  Dado la clave "k1" usada para otro pedido
  Cuando se envía un pedido distinto con "k1"
  Entonces la API responde 422

Escenario: dos pedidos simultáneos con la misma clave
  Dado un pedido con "k2" todavía en curso
  Cuando llega otro con "k2"
  Entonces el segundo recibe 409 y la base guarda una sola clave (UNIQUE usuario, clave)
```

Evidencia: U `FachadaReservasTest` · I `PersistenciaPostgresIT` · H "IDEMPOTENCY-KEY EN POST /api/turnos".

## HU-04 — Cancelar un turno · Must · CU-03

**Como** paciente o recepcionista
**quiero** cancelar un turno programado
**para** liberar el horario para otra persona.

```gherkin
Escenario: cancelar un turno programado
  Dado un turno PROGRAMADO
  Cuando recepción lo cancela
  Entonces queda CANCELADO y el horario vuelve a estar libre

Escenario: transición inválida
  Dado un turno ATENDIDO
  Cuando se intenta cancelarlo
  Entonces la API responde 409 (patrón State)

Escenario: cancelar un turno ajeno
  Dado un turno de otro paciente
  Cuando un PACIENTE intenta cancelarlo
  Entonces la API responde 403

Escenario: edición concurrente
  Dado que recepción leyó el turno en la versión 0 y otro usuario ya lo modificó
  Cuando cancela con If-Match de la versión 0
  Entonces la API responde 412
```

Evidencia: U `EstadoDelTurnoTest`, `GestionTurnosTest` · I `PersistenciaPostgresIT` ·
H "OWNERSHIP DE CANCELACION Y ARCHIVADO", "PATRON STATE", "ETAG" · E `reserva.spec.js`.

## HU-05 — Atender un turno · Must · CU-03

**Como** médico
**quiero** marcar como atendido un turno de mi agenda
**para** llevar el registro de la atención.

```gherkin
Escenario: el médico atiende su turno
  Dado un turno PROGRAMADO de la agenda de la Dra. Fernández
  Cuando ella lo marca atendido
  Entonces queda ATENDIDO

Escenario: turno de otro profesional
  Dado un turno de otro profesional
  Cuando la Dra. Fernández intenta atenderlo
  Entonces la API responde 403

Escenario: turno cancelado
  Dado un turno CANCELADO
  Cuando se intenta atenderlo
  Entonces la API responde 409
```

Evidencia: U `EstadoDelTurnoTest`, `GestionTurnosTest` · H "PATRON STATE", "OWNERSHIP".

## HU-06 — Archivar un turno cancelado · Could · CU-04

**Como** recepcionista
**quiero** sacar de la agenda los turnos cancelados sin borrarlos
**para** ver una agenda limpia sin perder el historial.

```gherkin
Escenario: archivar un cancelado
  Dado un turno CANCELADO
  Cuando recepción lo archiva
  Entonces deja de aparecer en la agenda y sigue en el historial del paciente

Escenario: archivar un programado
  Dado un turno PROGRAMADO
  Cuando se intenta archivarlo
  Entonces la API responde 409

Escenario: un paciente no archiva
  Dado un PACIENTE en sesión
  Cuando intenta archivar un turno
  Entonces la API responde 403
```

Evidencia: U `EstadoDelTurnoTest` · H "OWNERSHIP DE CANCELACION Y ARCHIVADO".

## HU-07 — Consultar la ficha y el historial · Must · CU-05

**Como** paciente
**quiero** ver mi ficha y mis turnos
**para** saber cuándo y con quién me atiendo.

```gherkin
Escenario: ver la propia ficha
  Dado el paciente 30111222 en sesión
  Cuando consulta /api/pacientes/30111222
  Entonces recibe su ficha con un ETag

Escenario: ver una ficha ajena (BOLA)
  Dado el paciente 30111222 en sesión
  Cuando consulta la ficha de otro DNI
  Entonces la API responde 403

Escenario: caché condicional
  Dado que el cliente ya tiene el ETag vigente de la ficha
  Cuando la pide con If-None-Match
  Entonces la API responde 304 sin cuerpo
```

Evidencia: U `GestionPacientesTest`, `EtiquetasTest` · H "PACIENTES", "SEGURIDAD EN VIVO", "ETAG".

## HU-08 — Gestionar profesionales y su disponibilidad · Must · CU-06

**Como** administrador o médico
**quiero** dar de alta profesionales y definir su agenda semanal
**para** que los pacientes vean horarios reales.

```gherkin
Escenario: alta por un administrador
  Dado un ADMIN en sesión
  Cuando da de alta un profesional
  Entonces la API responde 201

Escenario: alta por recepción
  Dado RECEPCION en sesión
  Cuando intenta dar de alta un profesional
  Entonces la API responde 403

Escenario: un médico edita la agenda de otro
  Dado la Dra. Fernández en sesión
  Cuando intenta definir la disponibilidad de otro profesional
  Entonces la API responde 403
```

Evidencia: U `GestionProfesionalesTest` · H "PROFESIONALES", "SEGURIDAD".

## HU-09 — Validar la cobertura de la obra social · Should · CU-07

**Como** recepcionista
**quiero** saber si la obra social del paciente está vigente
**para** cobrar el copago correcto.

```gherkin
Escenario: obra social legada
  Dado un paciente de OSDE
  Cuando se valida la cobertura
  Entonces se consulta el sistema legado por SOAP (origen LEGADO)

Escenario: obra social moderna
  Dado un paciente de Swiss Medical
  Cuando se valida la cobertura
  Entonces se consulta al partner por REST (origen PARTNER)

Escenario: el sistema externo no responde
  Dado que el partner demora más de 5 segundos
  Cuando se valida la cobertura
  Entonces en 5 s se responde 200 con contingencia: true (último resultado o "a verificar")

Escenario: el sistema externo sigue caído
  Dado que el partner falló 3 veces seguidas
  Cuando se valida otra cobertura
  Entonces el circuito está abierto y se responde enseguida con el respaldo, sin llamar al partner
```

Evidencia: U `GestorCoberturaTest`, `FallbacksTest`, `DisyuntorDeIntegracionTest`, `TiemposDeEsperaTest` ·
H "COBERTURA: PATRON ADAPTER" (incluye XXE contra el SOAP).

## HU-10 — Recibir avisos del turno · Should · CU-09

**Como** paciente y como profesional
**quiero** recibir la confirmación y el recordatorio del turno
**para** no olvidarme.

```gherkin
Escenario: avisos al reservar
  Dado un turno recién reservado
  Cuando los consumidores JMS procesan los eventos
  Entonces el turno tiene CONFIRMACION (cola), RECORDATORIO y AVISO_PROFESIONAL (tópico)

Escenario: el broker reentrega un mensaje
  Dado un evento ya procesado
  Cuando llega otra vez el mismo eventoId
  Entonces no se genera un segundo aviso (UNIQUE evento_id, tipo)

Escenario: mensaje defectuoso
  Dado un mensaje que no se puede procesar
  Cuando falla 4 veces (reintentos a 1, 2 y 4 s)
  Entonces termina en la cola de mensajes fallidos (DLQ) y queda registrado

Escenario: el contenedor se recrea con mensajes pendientes
  Dado un mensaje encolado sin consumir
  Cuando el contenedor de la aplicación se recrea
  Entonces el mensaje sigue en el journal (volumen) y se entrega al volver
```

Evidencia: U `GestionNotificacionesTest`, `EventoTurnoTest`, `ConsumoDeEventosTest` · I `PersistenciaPostgresIT` ·
H "MENSAJERIA JMS".

## HU-11 — Asistente de IA para elegir la especialidad · Could · CU-02

**Como** paciente que no sabe qué especialidad necesita
**quiero** contar lo que me pasa con mis palabras
**para** que el sistema me sugiera una especialidad.

```gherkin
Escenario: sugerencia válida
  Dado "me duele la rodilla desde que me caí"
  Cuando consulta al asistente
  Entonces sugiere una especialidad de la lista activa (Traumatología)

Escenario: emergencia
  Dado "tengo un dolor fuerte en el pecho y me falta el aire"
  Cuando consulta al asistente
  Entonces no sugiere turno y recomienda llamar al 107

Escenario: la IA no responde
  Dado que el proveedor de IA no responde en 5 s o el circuito está abierto
  Cuando consulta al asistente
  Entonces responde el respaldo por palabras clave (contingencia: true)

Escenario: abuso
  Dado que el usuario hizo 10 consultas en el último minuto
  Cuando hace otra
  Entonces la API responde 429
```

Evidencia: U `GestorOrientacionTest`, `FallbacksTest`, `AdaptadorIaGroqTest` · H "ASISTENTE DE IA" ·
`pruebas/ia/evaluar-ia.sh` (12 casos).

## HU-12 — Tablero del día · Should · CU-08

**Como** recepcionista o médico
**quiero** ver el resumen de turnos del día
**para** organizar la jornada.

```gherkin
Escenario: resumen del día
  Dado RECEPCION en sesión
  Cuando abre el inicio
  Entonces ve la cantidad de turnos programados, atendidos y cancelados de hoy

Escenario: un paciente no ve el tablero de la clínica
  Dado un PACIENTE en sesión
  Cuando pide /api/tablero
  Entonces la API responde 403

Escenario: pantalla en el celular
  Dado un ancho de 360 px
  Cuando recepción abre el inicio, la agenda, los pacientes o la reserva
  Entonces no hay desplazamiento horizontal
```

Evidencia: U `GestionTurnosTest` · H "TURNOS" · E `seguridad-y-diseno.spec.js` (responsive 360/768/1280).
