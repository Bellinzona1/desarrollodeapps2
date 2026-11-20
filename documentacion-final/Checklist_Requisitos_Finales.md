# MediConecta — Auditoría de criterios de testing (Clase 15)

**Actualización de Sonar (20/11/2026): Quality Gate APROBADO para el [PR #1](https://github.com/Bellinzona1/desarrollodeapps2/pull/1).**
Evidencia: notificación de `sonarqubecloud[bot]` aportada por el usuario, transcrita en
[`quality-gate-pr-1.json`](evidencias/sonar/quality-gate-pr-1.json). Este resultado procede
de SonarQube Cloud y es independiente de los tres jobs conservados en GitHub Actions.

**Revisión:** 20/11/2026. **Base inspeccionada:** `0ae0371` más los cambios locales de esta auditoría.
**Fuente:** `Criterios_Testing_TPO_Clase15.pdf`, 5 páginas, aportado por el usuario.
El PDF se usa como rúbrica: no autoriza publicar en Teams/Jira, firmar por integrantes,
crear tags retroactivos ni reescribir el historial.

**Actualización de alcance:** el usuario decidió conservar únicamente los jobs de calidad,
PIT y gitleaks. Sonar y los demás jobs se retiraron del pipeline. El tag `v1.0-testing`
ya fue publicado; conserva la configuración anterior. La auditoría siguiente es histórica.

**Alcance anterior acordado con el usuario:** completar Sonar y publicar el tag en
`Bellinzona1/desarrollodeapps2`. Se excluyeron de este trabajo adicional la validación de
contratos, trazabilidad, evidencias de seguridad/carga/resiliencia, tratamiento del secreto
histórico, Jira, Teams y declaración de IA. La matriz de abajo se conserva como comparación
con el PDF, no como lista de tareas adicionales autorizadas.

**Veredicto sobre la rúbrica completa: no está acreditado el cumplimiento de todos los criterios.**
La versión anterior de este checklist confundía controles configurados con resultados ejecutados.
Los informes del run #4 corresponden a `eda4b02`, no al código actual. Los números
históricos se conservan en sus informes; no acreditan automáticamente esta revisión.

Estados: **CUMPLE** = evidencia comprobada; **PARCIAL** = implementación/evidencia incompleta;
**NO CUMPLE** = brecha observada; **NO VERIFICABLE** = falta evidencia externa o ejecución.

## 1. Condiciones eliminatorias

| Id | Exigencia exacta | Estado | Hallazgo y acción necesaria |
|---|---|---|---|
| G1 | Clon limpio, un comando, sistema completo en ≤ 15 min | NO VERIFICABLE | Compose incluye PostgreSQL, WildFly, broker embebido y simuladores; falta medir desde clon y cachés limpios. El build Maven local no demuestra este requisito. |
| G2 | `v1.0-testing` en `main` antes de las 13:30 del 10/11 | NO CUMPLE en la copia local | `git tag` no devuelve tags. `v1.0-final` no sustituye el tag exigido. Una creación hoy no satisface el plazo; contrastar el remoto y cualquier excepción escrita de la cátedra. |
| G3 | CI verde sobre ese tag con todas las pruebas y análisis estático | NO VERIFICABLE | Solo hay evidencia versionada de un run anterior en rama. Sonar se omitía si faltaba token; se corrigió para bloquear. Ejecutar el CI completo y vincular el resultado al release aceptado. |
| G4 | Plan, matriz, ejecución y defectos entregados en Teams antes de las 13:30 | NO VERIFICABLE | Los archivos locales no prueban la entrega ni su fecha. Adjuntar comprobante. |
| G5 | QA en Jira; todos los casos/defectos con estado, responsable y HU | NO VERIFICABLE | El QA sigue sin asignar en el plan y los IDs Jira dicen «cargar». Completar y dar acceso de lectura al docente. |
| G6 | Sin secretos en ningún commit del historial | NO CUMPLE según D-03 | El registro reconoce un secreto JWT literal en `c285ce8`; el commit sigue accesible. Un resultado gitleaks 0 no invalida ese hallazgo. Requiere tratamiento coordinado del historial y rotación verificada; no se reescribió Git. |
| G7 | Demo en vivo del flujo principal | NO VERIFICABLE | Pruebas automatizadas y capturas no demuestran la demo docente. Ensayar y registrar la ejecución. |
| G8 | Declaración de IA completa | NO CUMPLE | `Declaracion_Uso_IA_Final.md` es un borrador con herramientas/versiones y firmas por completar. |
| G9 | Informe honesto, sin defectos conocidos ocultos | PARCIAL | Se corrigen afirmaciones sin respaldo en este checklist. Falta contrastar ejecución completa, registro y Jira. No corresponde certificar «0 críticos abiertos» sin ese contraste. |

## 2. Entregables y eje A

| Criterio | Estado | Evidencia / trabajo pendiente |
|---|---|---|
| Plan: alcance, niveles, entornos, datos, entrada/salida y riesgos P×I | PARCIAL | `Plan_de_Pruebas_Final.md` contiene esas secciones, pero sus resultados son históricos. Actualizar responsables y resultados de release. |
| PDF único `Clase15_Grupo<N>_<Dominio>_Testing.pdf` y enlaces | PARCIAL | Existe `MediConecta_Informe_Final_TPO.pdf`; hay que regenerar el paquete actualizado, identificar grupo y enlazar repo, CI, Jira, Sonar, API y reportes. |
| Matriz HU → criterio → caso → test → resultado → evidencia → defecto | NO CUMPLE | La matriz actual usa RF/CU y no desglosa los criterios de las 12 HU existentes en `docs/Historias_de_Usuario.md`. |
| Casos con ID, precondición, datos, pasos, esperado, prioridad, tipo, automatización | NO CUMPLE | El plan reconoce que falta la especificación caso por caso. Los nombres de tests y el script de humo no cubren todos esos campos. |
| ≥ 40 % de casos negativos o de borde | NO VERIFICABLE | No existe inventario clasificado completo. El 38,5 % histórico de expectativas 4xx no demuestra el mínimo y no puede sumarse a unitarias sin denominador común. |
| Informe de ejecución por nivel, tiempos, cobertura, mutación, carga y captura de Quality Gate | PARCIAL | Hay evidencias históricas y una ejecución nueva de Maven. Quality Gate del PR #1 aprobado según la notificación aportada; faltan resultados actuales de los demás niveles. |
| Registro de defectos con Jira, severidad/prioridad, reproducción, evidencia y causa raíz | PARCIAL | D-01…D-10 tienen detalle, pero faltan Jira y responsables. Las brechas de esta auditoría deben incorporarse al seguimiento. |
| Informe SEC con estados y evidencia de ZAP, SCA y secretos | PARCIAL | `Informe_Seguridad_y_Calidad.md` está referido al run anterior; necesita resultados del CI actual y tratamiento de hallazgos. |
| Tres defectos tardíos y qué prueba los habría detectado | PARCIAL | D-09/D-10 y errores de IA se describen; falta presentar tres lecciones con detección tardía y prevención explícitas. |
| Gherkin, MoSCoW, ≥ 3 criterios por HU, feliz/alternativo/error | PARCIAL | Hay 12 HU con 3–4 escenarios. HU-05/HU-08, por ejemplo, listan feliz y errores sin alternativa explícita; cotejar contra el alcance aprobado del 03/11. |
| Criterio adicional de fallo externo en HU con integración | PARCIAL | HU-09/HU-10/HU-11 incluyen caídas. Revisar también las HU de reserva que dependen de mensajería. |
| 100 % HU ejecutadas; ≥ 95 % criterios pasan; ningún Must falla | NO VERIFICABLE | Falta la matriz por criterio y su informe; no se infiere de un build verde. |
| ≥ 5 escenarios críticos E2E ejecutados en pipeline | PARCIAL | Playwright contiene 12 escenarios y está conectado al CI; falta evidencia versionada actual. Validar que al menos cinco cubran flujos críticos, no solo cabeceras o tamaños. |
| Manual ejecutado por usuario y exploratoria de 10 min | NO VERIFICABLE | Falta registro de tareas/resultado y sesión exploratoria (doble clic, atrás, expiración, recarga, concurrencia). |
| Responsive 360/768/1280; accesibilidad ≥ 90 sin críticas WCAG 2.2 AA | PARCIAL | Responsive automatizado. El checklist anterior alegaba Lighthouse 100 solo en login, sin reporte versionado; falta verificar pantallas autenticadas y violaciones críticas. |

## 3. Eje B — Código y automatización

| Criterio | Estado | Evidencia / trabajo pendiente |
|---|---|---|
| Líneas negocio/dominio ≥ 80 %; ramas ≥ 70 % | CUMPLE | Build limpio ejecutado: negocio 720/758 = 94,99 % líneas, 276/334 = 82,63 % ramas; `modelo` 82,14 % / 75 %; `modelo.estado` 95,35 % / 75 %. |
| Mutación dominio ≥ 60 % | PARCIAL | PIT configurado para negocio y State; evidencia histórica 338/396 (85 %). Reejecutar sobre el release. |
| Quality Gate de Sonar aprobado | CUMPLE para PR #1 | **Quality Gate passed**, notificación de `sonarqubecloud[bot]` del 20/11/2026. [Evidencia](evidencias/sonar/quality-gate-pr-1.json). La captura no acredita la configuración de todos los umbrales particulares de la rúbrica ni el análisis del tag. |
| Integración con base y broker reales | PARCIAL | Se ejecutaron los 6 tests PostgreSQL/Testcontainers. El humo usa JMS real en Compose; esta auditoría no reejecutó los consumidores ni la caída del broker. |
| Colección API versionada en CI, éxito y error de cada endpoint | PARCIAL | Humo bash amplio, pero no hay matriz de cobertura de las operaciones con ambos casos ni colección Postman/REST Assured/Karate equivalente a la solicitada. |
| Respuestas validadas contra OpenAPI y WSDL | NO CUMPLE | `ContratoOpenApiTest` compara rutas/metadatos. El humo comprueba publicación del WSDL y textos de SOAP; no valida respuestas contra sus esquemas. |
| Toda transición inválida devuelve 409 | PARCIAL | Unitarias de State pasan. El humo no prueba todas las combinaciones HTTP (p. ej. ATENDIDO→archivar y CANCELADO→atender). |
| Pipeline completo ≤ 15 min | NO VERIFICABLE | Los 4 min 12 s corresponden al CI anterior, sin todos los jobs actuales. Medir duración total, incluidas dependencias entre jobs. |
| Suite tres veces seguida con idéntico resultado, 0 flaky | PARCIAL | Se agregó repetición limpia de JUnit/IT e inventario idéntico sin omisiones. Extender evidencia a humo y E2E; no equivale a repetir toda la suite. |
| Pruebas efectivas, sin triviales/deshabilitadas injustificadas | PARCIAL | 207 unitarias y 6 IT pasaron sin omisiones en Docker. La primera corrida dentro del sandbox omitió los 6 IT: no cuenta como integración. El nuevo control rechaza omisiones en CI. PIT histórico aporta evidencia parcial de efectividad. |

## 4. Eje C — Integración y resiliencia

| Criterio | Estado | Evidencia / trabajo pendiente |
|---|---|---|
| SOAP válido, Fault de negocio y técnico mapeados | PARCIAL | Humo cubre válido y XML malicioso; falta acreditar Fault de negocio y técnico del proveedor y su traducción al cliente. |
| SOAP/REST sin respuesta: timeout efectivo ≤ 5 s, reintento/circuit breaker y fallback informado | PARCIAL | Tests de timeouts/fallback/disyuntor pasan; medir el límite total en vivo. Un timeout por llamada no demuestra el tiempo total de la operación. |
| Apagar y recuperar broker sin perder mensajes ni consistencia | NO VERIFICABLE | Hay procedimiento y afirmación anterior, sin evidencia nueva conservada de la secuencia. |
| Duplicados procesados una vez | PARCIAL | Unitarias e IT de unicidad pasan; verificar reentrega real del broker. |
| Mensaje inválido, N reintentos, DLQ, registro y reproceso | PARCIAL | Humo y tests implementados; falta ejecución actual y evidencia de reproceso. |
| Fallo transaccional: rollback completo; outbox/compensación si corresponde | PARCIAL | IT prueba restricciones/concurrencia y unitarias `setRollbackOnly`; falta inyectar fallo a mitad de la operación y verificar conjuntamente BD/JMS. |
| POST repetido Idempotency-Key, mismo recurso/respuesta | PARCIAL | Unitarias e IT pasan; humo implementado, pendiente reejecución actual. |
| ETag desactualizado → 412 | PARCIAL | Unitarias de etiquetas e IT de concurrencia pasan; humo pendiente. |
| Problem Details y códigos 400/401/403/404/409/422/429/5xx | PARCIAL | `ProblemasTest` pasa; completar/ejecutar matriz HTTP de errores. |
| Carga 50 concurrentes × 5 min, tres endpoints, p95 < 500 ms y error < 1 % | PARCIAL | Script configurado con 50 usuarios y meseta 5 min. Se agregaron umbrales separados para agenda/padrón/tablero. Falta reporte de esta configuración; el reporte versionado anterior usa 20 × 45 s. |

## 5. Eje D — Seguridad

| Criterio | Estado | Evidencia / trabajo pendiente |
|---|---|---|
| BOLA/IDOR y BFLA → 403/404 | PARCIAL | Unitarias pasan; humo/E2E implementados, pendientes de ejecutar sobre release. |
| JWT vencido/firma alterada/alg=none/otro emisor → 401 | PARCIAL | `ServicioJwtTest` pasa; completar evidencia HTTP de los cuatro casos. |
| Mass assignment (rol, estado, precio) nunca persistido | NO VERIFICABLE | Falta prueba explícita de campos extra y lectura posterior para comprobar que no se persistieron. |
| SQL/NoSQL/XSS en todos los campos de texto | PARCIAL | Hay pruebas seleccionadas y escape en interfaz; no hay inventario que acredite todos los campos. NoSQL no aplica a PostgreSQL. |
| XXE/DTD rechazados en SOAP | PARCIAL | Humo incluye entidad externa y expansión interna; pendiente ejecución actual. |
| Fuerza bruta → 429 | PARCIAL | Unitarias pasan; humo implementado. |
| ZAP baseline + activo autenticado, 0 High, Medium justificadas individualmente | PARCIAL | Jobs configurados; evidencia versionada solo de baseline antiguo. Falta reporte actual autenticado y tratamiento individual de Medium. |
| SCA: 0 CVE altas/críticas sin mitigación documentada | NO VERIFICABLE | Se corrigieron los gates: repositorio e imagen bloquean HIGH/CRITICAL, incluidas las sin arreglo. Falta ejecutar Trivy; no se declara que haya cero CVE. |
| CSP, HSTS, nosniff, CORS, cookies Secure/HttpOnly/SameSite | PARCIAL | Tests de cabeceras pasan; HSTS/Secure dependen de HTTPS. Compose expone HTTP: verificar despliegue TLS y cookies reales. |
| Sin trazas al cliente ni datos personales/salud/tokens en logs | PARCIAL | Problem Details probado; falta inspección de logs de los escenarios completos. |
| TLS, hash adaptativo bcrypt/Argon2 y minimización de datos | PARCIAL | Usa PBKDF2-HMAC-SHA256 (120.000), no los algoritmos citados por la rúbrica; acordar equivalencia o migrar. TLS y minimización necesitan evidencia. |

## 6. Eje E, salida y defensa

| Criterio | Estado | Evidencia / trabajo pendiente |
|---|---|---|
| IA usada: nombre/versión, propósito, prompt, aceptado/corregido/descartado, verificación | PARCIAL | Declaración existente incompleta. Se agrega la intervención de esta auditoría sin atribuir revisión humana no realizada. |
| Tests generados revisados y firmados por integrante en Git | NO VERIFICABLE | Requiere revisión y autoría responsable del equipo; esta auditoría no firma por nadie. |
| Plan de IA futura con objetivo medible y riesgo | CUMPLE documental | Sección 3 de la declaración incluye herramientas, metas y riesgos. |
| Casos de IA propios y evaluación objetiva | PARCIAL | 12 casos en `pruebas/ia/casos.json`; no hay salida versionada actual del proveedor real. |
| Prompt injection directa e indirecta; no revelar instrucciones | PARCIAL | IA-10 cubre un pedido directo de medicación; falta inyección indirecta y extracción explícita del prompt. |
| Fuera de dominio/peligroso; no filtrar datos de otro usuario; caída/lentitud | PARCIAL | Casos de urgencias/fuera de tema y unitarias de fallback; falta evaluación de fuga entre usuarios y evidencia real completa. |
| 0 bloqueantes/críticos, ≤ 3 mayores con workaround; menores registrados | NO VERIFICABLE | No alcanza con el contador histórico de defectos: registrar brechas actuales, severidad y responsables, y validar las HU Must. |
| QA conduce; cualquier integrante explica tests/defectos/herramientas | NO VERIFICABLE | Requiere ensayo con el equipo. No se puede acreditar desde el repositorio. |

## 7. Cambios de la auditoría inicial (histórico; alcance actualizado arriba)

- CI exige Sonar configurado; ya no puede quedar verde porque omitió el análisis.
- CI ejecuta JUnit/IT tres veces desde `clean`, conserva XML y compara el inventario; rechaza omisiones, fallas y errores.
- Trivy bloquea HIGH/CRITICAL en repositorio e imagen, sin ocultar las que no tienen arreglo.
- k6 evalúa latencia y errores individualmente en los tres endpoints críticos.
- Se retira la afirmación global de cumplimiento y se enlaza este checklist desde los informes anteriores.

Prioridad: resolver G2/G4/G5/G6/G8 con evidencia del equipo; completar especificación y
trazabilidad por criterio; cerrar contratos, seguridad y resiliencia faltantes; ejecutar
el CI completo con Sonar; consolidar evidencia del release y regenerar el PDF.
Un job configurado, una prueba no ejecutada o una afirmación de IA no se consideran aprobados.
