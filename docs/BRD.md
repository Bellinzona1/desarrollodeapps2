# MediConecta — Documento de requisitos de negocio (BRD)

## 1. Contexto

Una clínica ambulatoria de mediana escala gestiona hoy los turnos por teléfono y planillas.
Eso genera turnos superpuestos, pacientes que no se presentan por falta de recordatorio,
llamadas de confirmación que ocupan a recepción y validaciones de obra social hechas a mano
contra sistemas de cada prepaga. MediConecta reemplaza ese circuito por una aplicación web.

## 2. Objetivos de negocio

| Id | Objetivo | Indicador | Meta |
|---|---|---|---|
| OB-1 | Que el paciente reserve solo, sin llamar | % de turnos reservados por el propio paciente | ≥ 50 % a los 3 meses |
| OB-2 | Eliminar turnos superpuestos | Turnos vigentes en el mismo horario y profesional | 0 (regla del sistema) |
| OB-3 | Bajar el ausentismo | Turnos con confirmación y recordatorio enviados | 100 % |
| OB-4 | Validar la cobertura sin trámite manual | Consultas de cobertura resueltas por el sistema | ≥ 95 %, con respaldo si el externo falla |
| OB-5 | Proteger los datos de salud | Accesos a datos ajenos | 0 (control por rol y por dueño) |

## 3. Alcance

**Incluye:** reserva de turnos (directa y con asistente paso a paso, con ayuda de IA para
elegir la especialidad), cancelación, atención y archivo de turnos; padrón de pacientes;
profesionales y su disponibilidad semanal; validación de cobertura contra el sistema legado
(SOAP) y el partner moderno (REST); avisos asincrónicos de confirmación y recordatorio; tablero
del día; API REST documentada (OpenAPI).

**No incluye (Won't):** pagos, facturación, historia clínica, telemedicina, envío real de
e-mail/SMS (los avisos se registran), aplicación móvil nativa.

## 4. Interesados

| Rol | Interés |
|---|---|
| Paciente | Reservar y ver sus turnos desde el celular, recibir avisos |
| Recepción | Agenda del día, padrón, cobertura, reservar por teléfono |
| Médico | Ver y atender su agenda, definir sus horarios |
| Administración | Alta de profesionales, operación y monitoreo |
| Obras sociales (sistemas externos) | Consultas de vigencia y plan por SOAP o REST |

## 5. Requisitos de negocio

| Id | Requisito | Historia |
|---|---|---|
| RN-01 | Cada usuario accede solo a las funciones de su rol | HU-01, HU-08 |
| RN-02 | El paciente reserva en pocos pasos y ve sus turnos | HU-02, HU-07 |
| RN-03 | No puede haber dos turnos vigentes del mismo profesional en el mismo horario | HU-02 |
| RN-04 | Un reintento de reserva no crea un turno duplicado | HU-03 |
| RN-05 | El ciclo del turno es PROGRAMADO → ATENDIDO o CANCELADO → archivado; nada más | HU-04, HU-05, HU-06 |
| RN-06 | Un paciente solo ve y cancela lo suyo; un médico solo atiende y edita lo suyo | HU-04, HU-05, HU-07, HU-08 |
| RN-07 | La cobertura se valida contra el sistema de cada obra social | HU-09 |
| RN-08 | Si un sistema externo falla, la atención sigue (respuesta en ≤ 5 s con respaldo) | HU-09, HU-11 |
| RN-09 | Cada turno genera confirmación al paciente y aviso al profesional, sin duplicados | HU-10 |
| RN-10 | La recepción ve el resumen del día | HU-12 |

## 6. Requisitos no funcionales de negocio

- **Disponibilidad y resiliencia:** timeout de 5 s, circuit breaker y fallback en todas las
  integraciones; mensajes persistentes con reintentos y cola de fallidos.
- **Rendimiento:** p95 < 500 ms con 50 usuarios simultáneos (medido: ~21 ms).
- **Seguridad:** JWT, autorización en la capa de negocio, control por dueño (BOLA/BFLA),
  límite de intentos, cabeceras estrictas, sin secretos en el código (OWASP API Top 10).
- **Usabilidad:** diseño responsive (360, 768 y 1280 px), mensajes de error comprensibles.
- **Operación:** despliegue con un comando (Docker Compose), health checks, CI con
  quality gates.

## 7. Supuestos y restricciones

- Tecnología fijada por la cátedra: Jakarta EE 10 sobre WildFly, PostgreSQL.
- Los sistemas de obras sociales se simulan dentro del mismo despliegue, pero se consumen
  por URL/WSDL como si fueran externos.
- El proveedor de IA es externo (API compatible con OpenAI); sin clave, funciona el respaldo.

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| Caída de un sistema externo | Timeout + circuit breaker + caché de 24 h + contingencia |
| Caída del broker de mensajes | Mensajes persistentes, journal en volumen, transacción XA |
| Respuesta inventada de la IA | El gateway descarta especialidades fuera de la lista; detección de emergencias |
| Acceso indebido a datos de salud | Roles + control por dueño en la capa de negocio; pruebas BOLA/BFLA en el CI |
