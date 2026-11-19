# MediConecta — Declaración de uso de inteligencia artificial (borrador)

> **Borrador para completar y firmar por el equipo.** Lo que sigue describe con precisión
> el uso que quedó registrado en el repositorio a partir del 18/11/2026. Cada integrante
> tiene que agregar el uso que hizo por su cuenta (en entregas anteriores o fuera de estas
> sesiones) antes de presentarla.

## 1. IA como parte del producto

MediConecta **incluye** una funcionalidad de IA: el asistente "Prefiero escribirlo" del
primer paso de la reserva, que sugiere una especialidad a partir de lo que cuenta la
persona.

| | |
|---|---|
| Proveedor | Groq (API compatible con *chat completions* de OpenAI) |
| Modelo | `openai/gpt-oss-120b` (configurable con `mediconecta.ia.modelo`) |
| Acceso | Solo desde el servidor, a través del gateway `GestorOrientacion`. La clave nunca llega al navegador ni al repositorio |
| Controles | Instrucciones de sistema (no diagnosticar ni recetar, derivar emergencias al 107/911, no salirse de tema), validación de la entrada, límite de 10 consultas por minuto por usuario, la especialidad sugerida se descarta si no existe, timeout de 5 s con fallback por palabras clave |
| Datos enviados | Solo el texto que escribe la persona en el chat; no se envían nombre, documento ni historia clínica. La conversación no se guarda |
| Evaluación | `pruebas/ia/evaluar-ia.sh` (12 casos: orientación, emergencias, *prompt injection*, fuera de tema, no inventar especialidades) y tests unitarios |

## 2. IA como herramienta de desarrollo

| Herramienta | Quién la usó | Para qué | Período |
|---|---|---|---|
| Claude Code (Anthropic, modelo Claude Opus) | Mateo Bellinzona | Implementar el asistente de IA de la reserva (commit `3babcd4`) y las mejoras pedidas en la corrección de la 2.ª entrega y en el 3.er checkpoint (ver detalle abajo) | 18 y 19/11/2026 |
| *(completar)* | *(completar)* | *(completar)* | *(completar)* |

**Trabajo hecho con Claude Code a partir del 18/11/2026** (commits del 19/11 en adelante en la
rama `facu`): mensajería JMS confiable (eventos JSON, consumidores idempotentes, reintentos
y DLQ), Problem Details, `Idempotency-Key`, ETag, timeouts y fallbacks, límite de intentos
(429), cabeceras de seguridad y corrección de dos vulnerabilidades BOLA/IDOR, health checks,
migraciones Flyway, Docker y Docker Compose, pipeline de CI, configuración de Checkstyle,
JaCoCo, PIT, k6, ZAP y gitleaks, la mayoría de los tests unitarios nuevos (de 58 a 194),
la prueba de punta a punta en bash y estos documentos (plan de pruebas, matriz, manual,
informe).

**Cómo se usó:** se le dieron las consignas (la devolución del profesor y la del 3.er
checkpoint). La herramienta leyó el código, propuso e implementó los cambios, y los
verificó ejecutando los tests, desplegando en WildFly y en Docker, y corriendo las pruebas
de punta a punta, de carga y de seguridad. Los resultados que figuran en el plan de pruebas
son de esas ejecuciones reales.

## 3. Responsabilidad del equipo

- El equipo es responsable del código entregado, lo haya escrito una persona o una
  herramienta. Antes de la defensa, **cada integrante tiene que revisar y entender** las
  partes generadas: la defensa es individual y puede preguntar por cualquier parte.
- Ninguna decisión de seguridad se aceptó sin una prueba que la verifique (ver la matriz de
  trazabilidad).
- No se le dieron a la herramienta datos personales reales: los datos del sistema son
  ficticios.

**Firmas:** *(nombre y fecha de cada integrante)*
