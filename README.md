# MediConecta

**Trabajo Práctico Integrador — Desarrollo de Aplicaciones II — UADE**
**Entrega Obligatoria N.º 2 (2.º parcial)**

MediConecta es un sistema de gestión de turnos médicos construido sobre **Jakarta EE 10**
y desplegado en **WildFly**. Tiene componentes de negocio *stateless* y *stateful* en una
arquitectura estricta de tres capas, los patrones DAO, Facade, Factory, Adapter y **State**,
autenticación con **JWT** y autorización por rol, mensajería **JMS** (cola y tópico),
integración **SOAP + REST**, persistencia con **JPA + PostgreSQL**, tests unitarios y una
interfaz web que sigue el prototipo de Figma.

| | |
|---|---|
| **Aplicación** | <http://localhost:8080/mediconecta/> |
| **Prueba de vida** | <http://localhost:8080/mediconecta/api/salud> |
| **Documento de la entrega** | [docs/Entrega_Obligatoria_2.md](docs/Entrega_Obligatoria_2.md) (cierre del 13/11 en §9) · [Entrega 1](docs/Entrega_Obligatoria_1.md) |
| **Arquitectura y patrones** | [docs/arquitectura.md](docs/arquitectura.md) |
| **Guion de la defensa** | [docs/guion-defensa.md](docs/guion-defensa.md) |
| **Preguntas probables** | [docs/preguntas-defensa.md](docs/preguntas-defensa.md) |

---

## 1. Qué hace la aplicación

- **Iniciar sesión** con un **JWT** firmado que lleva los roles (PACIENTE, RECEPCION,
  MEDICO, ADMIN); la autorización la aplica el contenedor con `@RolesAllowed`.
- **Tablero** con turnos del día, pacientes activos, profesionales y turnos pendientes,
  todo calculado con datos reales de la base.
- **Turnos**: listar con buscador y filtros, ver el detalle, agendar, **cancelar**,
  **marcar atendido** y **archivar** (sacar de la agenda un turno cancelado sin borrar su
  historial). Las transiciones de estado siguen el patrón **State**: una inválida responde
  **409**.
- **Mis turnos**: un PACIENTE consulta y cancela solo sus propios turnos.
- **Pacientes**: padrón con buscador, alta, edición de ficha e historial de turnos.
- **Profesionales**: listado por especialidad, alta (solo ADMIN) y gestión de la
  disponibilidad semanal — RECEPCION y ADMIN sobre cualquier profesional, un MEDICO solo
  sobre la suya propia.
- **Reserva guiada en pasos**, cuyo estado intermedio vive en un EJB `@Stateful`
  del servidor y no en el navegador. En el primer paso, la opción **Prefiero escribirlo**
  abre un chat: la persona cuenta qué le pasa y un asistente de IA le sugiere la
  especialidad, con un botón para elegirla (ver [Asistente de IA](#asistente-de-ia-opcional)).
- Interfaz **responsive**: barra lateral en escritorio, navegación inferior en teléfono.

### Componentes implementados

| # | Componente | Tipo | Qué resuelve |
|---|---|---|---|
| C1 | `GestionTurnos` | EJB `@Stateless` | Reglas de la agenda: alta, consulta y cancelación de turnos |
| C2 | `GestionPacientes` | EJB `@Stateless` | Padrón de pacientes e historial de atenciones |
| C3 | `GestionProfesionales` | EJB `@Stateless` | Profesionales, especialidades, disponibilidad y horarios libres |
| C4 | `AsistenteReservaTurno` | EJB **`@Stateful`** | Conversación de reserva en cuatro pasos |

En la Entrega 2 se sumaron C5 (Notificaciones por **JMS**: cola + tópico) y C6
(Integración con obras sociales: **SOAP** legado + **REST** partner, patrón Adapter). El
detalle está en [docs/Entrega_Obligatoria_2.md](docs/Entrega_Obligatoria_2.md).

---

## 2. Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 17 o superior | El proyecto compila con `--release 17`. |
| Apache Maven | 3.9.x | `winget install Apache.Maven` si no está en el PATH. |
| WildFly | 36.0.1.Final o superior | Versión de referencia del proyecto. Ver la nota de compatibilidad más abajo. |
| PostgreSQL | 14 o superior | Puede ser una instalación local o el contenedor Docker que trae el proyecto. |

> **Nota de compatibilidad — importante y honesta.**
> El proyecto está escrito contra **Jakarta EE 10** (`jakarta.jakartaee-api:10.0.0`, scope
> `provided`), que es la plataforma de WildFly 36.0.1.Final. La Entrega 2 completa (JWT,
> patrón State, JMS, SOAP + REST) fue **verificada de punta a punta sobre WildFly
> 36.0.1.Final (`standalone-full.xml`) con JDK 24 y PostgreSQL 16**. La Entrega 1 se había
> verificado además sobre WildFly 41.0.1.Final (Jakarta EE 11, compatible hacia atrás); la
> Entrega 2 no se volvió a probar sobre la 41.

---

## 3. Puesta en marcha (copiar y pegar)

### Paso 1 — PostgreSQL

**Opción A — Docker (la más rápida):**

```powershell
.\scripts\db\levantar-postgres.ps1
```

Crea un contenedor `mediconecta-postgres` con la base `mediconecta`, el usuario
`mediconecta` y la contraseña `mediconecta`, escuchando en el puerto 5432. Volver a
ejecutarlo arranca el contenedor existente sin perder datos.

Equivalente a mano:

```bash
docker run -d --name mediconecta-postgres -e POSTGRES_DB=mediconecta -e POSTGRES_USER=mediconecta -e POSTGRES_PASSWORD=mediconecta -p 5432:5432 postgres:16-alpine
```

**Opción B — PostgreSQL instalado en la máquina:**

```powershell
psql -U postgres -f .\scripts\db\crear-base.sql
```

En los dos casos, **las tablas no hay que crearlas**: Hibernate las crea o actualiza sola
al desplegar el WAR. El esquema equivalente, por si se quiere revisar o crear a mano, está
en [`scripts/db/esquema.sql`](scripts/db/esquema.sql).

### Paso 2 — Configurar WildFly

Un solo script deja el servidor listo: instala el driver JDBC como módulo, crea el
datasource y prepara la seguridad JWT (`integrated-jaspi=false`).

```powershell
.\scripts\wildfly\configurar-wildfly.ps1 -WildFly "C:\wildfly-36.0.1.Final"
```

El script tiene dos momentos:

1. **Con WildFly apagado** descarga el driver de PostgreSQL y lo instala como módulo
   `org.postgresql`.
2. **Con WildFly encendido** crea el datasource `java:/jdbc/MediConectaDS` y aplica
   `scripts/wildfly/configurar-seguridad.cli`. Si el servidor todavía no está arriba,
   levantarlo (`bin\standalone.bat -c standalone-full.xml`) y volver a correr el script con
   `-SoloDatasource`.

Si PostgreSQL no está en `localhost:5432`, pasar la URL: `-UrlBase
"jdbc:postgresql://127.0.0.1:5433/mediconecta"`.

> **Mensajería asincrónica (Entrega 2).** A partir de la segunda entrega el WAR declara
> dos destinos JMS (`DestinosJms`, cola y tópico) que el contenedor crea solo al
> desplegar, pero **solo si el subsistema de mensajería está activo**. El perfil
> `standalone.xml` (el que se usó hasta la Entrega 1) **no lo trae**: hay que levantar
> WildFly con `bin\standalone.bat -c standalone-full.xml` (o
> `standalone-full-ha.xml`). Si se arranca con el perfil de siempre, el WAR no despliega:
> el log muestra que no encuentra el subsistema `messaging-activemq`. El detalle de la
> mensajería y de las integraciones SOAP/REST nuevas está en
> [`docs/Entrega_Obligatoria_2.md`](docs/Entrega_Obligatoria_2.md).

Si se prefiere hacerlo a mano, los pasos exactos son:

```powershell
# 1) Módulo del driver (servidor apagado)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --command="module add --name=org.postgresql --resources=C:\ruta\postgresql-42.7.4.jar --dependencies=jakarta.transaction.api,java.sql,java.logging"

# 2) Datasource (servidor encendido)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --resolve-parameter-values --file=scripts\wildfly\configurar-datasource.cli

# 3) Seguridad JWT (servidor encendido)
C:\wildfly-36.0.1.Final\bin\jboss-cli.bat --connect --file=scripts\wildfly\configurar-seguridad.cli
```

El archivo `.cli` toma la URL, el usuario y la contraseña de PostgreSQL como propiedades
del sistema (`-Dmediconecta.db.usuario=...`), con valores por omisión de desarrollo.
`--resolve-parameter-values` es necesario para que esos `-D` se apliquen.

Opcional: el secreto con el que se firman los JWT se fija con la propiedad del sistema
`mediconecta.jwt.secreto` (por ejemplo, `bin\standalone.bat -c standalone-full.xml
-Dmediconecta.jwt.secreto=...`, al menos 32 caracteres) o la variable de entorno
`MEDICONECTA_JWT_SECRETO`. Sin ella se usa un secreto de desarrollo y el log lo advierte.
**No hay ninguna credencial de base de datos dentro del código Java versionado.**

#### Asistente de IA (opcional)

El chat **Prefiero escribirlo** del primer paso de la reserva usa un modelo de lenguaje a
través de la API de [Groq](https://console.groq.com/keys). La clave **nunca** va en el
código ni en el navegador: la lee el servidor de la variable de entorno
`MEDICONECTA_IA_CLAVE` o de la propiedad del sistema `mediconecta.ia.clave`.

```bash
# Linux / macOS: agregar al final de <wildfly>/bin/standalone.conf y reiniciar WildFly
export MEDICONECTA_IA_CLAVE="gsk_..."
```

```powershell
# Windows: agregar al final de <wildfly>\bin\standalone.conf.bat y reiniciar WildFly
set "MEDICONECTA_IA_CLAVE=gsk_..."
```

Opcionales: `mediconecta.ia.modelo` (por omisión `openai/gpt-oss-120b`) y
`mediconecta.ia.url` (por omisión la API de Groq; cualquier API compatible con el formato
*chat completions* de OpenAI sirve). Sin clave, el resto de la aplicación funciona igual y
el chat responde que el asistente no está configurado.

### Paso 3 — Compilar

```powershell
mvn clean package
```

Corre los tests unitarios y genera `target\mediconecta.war`. El nombre del WAR (`<finalName>mediconecta</finalName>`)
es el que fija el context root, es decir la primera parte de todas las URLs.

### Paso 4 — Desplegar

Copiar el WAR a la carpeta de despliegues:

```powershell
copy target\mediconecta.war C:\wildfly-36.0.1.Final\standalone\deployments\
```

En el log tiene que aparecer:

```
WFLYSRV0010: Deployed "mediconecta.war"
```

y, la primera vez, la línea del juego de datos de ejemplo:

```
[DEMO] Juego de datos de ejemplo cargado: 4 profesionales, 4 pacientes, 3 turnos.
```

La carga de ejemplo es **idempotente**: si la base ya tiene datos no toca nada, así que
redesplegar no duplica registros.

Alternativa: consola de administración en <http://localhost:9990> →
**Deployments → Add → Upload Deployment**.

### Paso 5 — Entrar

Abrir <http://localhost:8080/mediconecta/> e iniciar sesión.

---

## 4. Usuarios de demostración

Son **usuarios ficticios**, creados únicamente para la defensa. Desde la Entrega 2 viven
en la tabla `usuarios` (contraseña guardada como hash PBKDF2, nunca en claro) y los crea la
aplicación la primera vez que despliega. Ya no hace falta `add-user.bat`.

| Usuario | Contraseña | Rol | Qué puede hacer |
|---|---|---|---|
| `paciente` | `Demo.2026` | PACIENTE | Ver su ficha y sus turnos, reservar con el asistente guiado |
| `recepcion` | `Demo.2026` | RECEPCION | Agenda completa, padrón de pacientes, agendar y cancelar turnos |
| `medico` | `Demo.2026` | MEDICO | Agenda completa, cancelar turnos, definir disponibilidad |
| `admin` | `Demo.2026` | ADMIN | Todo lo anterior más el alta de profesionales |

El usuario `paciente` está asociado a la ficha de **Ana Gomez (DNI 30111222)** por el campo
`usuario` de la tabla `pacientes`. Esa asociación es la que permite que, además de la
autorización declarativa, el sistema verifique que un paciente solo consulte **su propio**
historial.

---

## 5. API REST

Context root `/mediconecta`, prefijo `/api`. Todos los endpoints exigen un JWT en la
cabecera `Authorization: Bearer <token>` **salvo `/api/salud` y `/api/auth/login`**.

### Autenticación

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| POST | `/api/auth/login` | público | `{"token":"eyJ...","tipo":"Bearer","expiraEnSegundos":7200,"usuario":"recepcion","roles":["RECEPCION"]}`, o `401` |

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/mediconecta/api/auth/login \
  -H "Content-Type: application/json" -d '{"usuario":"recepcion","clave":"Demo.2026"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/mediconecta/api/turnos
```

### Salud

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/salud` | público | `200` y el texto **¡Hola desde el contenedor!** |

### Sesión

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/sesion` | todos | `{"autenticado":true,"usuario":"recepcion","roles":["RECEPCION"]}` |
| POST | `/api/sesion/logout` | todos | `204`; libera la sesión HTTP del asistente. El token lo descarta el cliente |

### Turnos

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/turnos` | RECEPCION, MEDICO, ADMIN | Agenda operativa (sin archivados) ordenada por fecha |
| GET | `/api/turnos/{id}` | todos | El turno, o `404` (funciona aunque esté archivado) |
| POST | `/api/turnos` | PACIENTE, RECEPCION, ADMIN | `201` + `Location`, o `400` con la regla incumplida. Un PACIENTE solo puede reservar para sí mismo |
| PUT | `/api/turnos/{id}/cancelar` | PACIENTE, RECEPCION, MEDICO, ADMIN | El turno en estado `CANCELADO`. Ownership: un PACIENTE solo cancela los propios, un MEDICO solo los de su agenda (`400` si no corresponde). **`409`** si ya estaba cancelado o atendido |
| PUT | `/api/turnos/{id}/atender` | **MEDICO, ADMIN** | El turno en estado `ATENDIDO`. Un MEDICO solo sobre su agenda (`400`). **`409`** si no está `PROGRAMADO` |
| PUT | `/api/turnos/{id}/archivar` | **RECEPCION, ADMIN** | Saca de la agenda un turno ya `CANCELADO` sin borrarlo: sigue en el historial del paciente. **`409`** si no está cancelado o si ya estaba archivado |

### Pacientes

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/pacientes?q=texto` | RECEPCION, MEDICO, ADMIN | Padrón, filtrado por nombre o documento |
| GET | `/api/pacientes/mi-ficha` | todos | Ficha del paciente asociado al usuario de la sesión |
| GET | `/api/pacientes/{documento}` | todos | El paciente, o `404` |
| POST | `/api/pacientes` | PACIENTE, RECEPCION, ADMIN | `201`, o `400` si el documento se repite |
| PUT | `/api/pacientes/{documento}` | RECEPCION, ADMIN | El paciente actualizado |
| GET | `/api/pacientes/{documento}/turnos` | todos | Historial; un PACIENTE solo puede ver el suyo |
| GET | `/api/pacientes/{documento}/cobertura` | todos | Cobertura validada por SOAP (PAMI, OSDE, IOMA) o REST (el resto); `502` si el sistema externo no responde |
| GET | `/api/profesionales/mi-perfil` | **MEDICO** | Ficha de profesional asociada al usuario logueado |

### Profesionales

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/profesionales` | todos | Todos los profesionales con su disponibilidad |
| GET | `/api/profesionales?especialidad=Cardiologia` | todos | Los de esa especialidad |
| GET | `/api/profesionales/especialidades` | todos | Especialidades con profesionales activos |
| GET | `/api/profesionales/{id}` | todos | El profesional, o `404` |
| POST | `/api/profesionales` | **ADMIN** | `201`, o `403` si el rol no corresponde |
| PUT | `/api/profesionales/{id}/disponibilidad` | **RECEPCION, MEDICO, ADMIN** | Reemplaza la agenda semanal. RECEPCION y ADMIN pueden editar la de cualquiera; un MEDICO solo la propia (`400` si no corresponde) |
| GET | `/api/profesionales/{id}/horarios-libres?fecha=aaaa-MM-dd` | todos | Horarios disponibles ese día |

### Reserva guiada (componente `@Stateful`)

| Método | Ruta | Roles | Qué hace |
|---|---|---|---|
| GET | `/api/reserva` | PACIENTE, RECEPCION, ADMIN | Estado actual **según lo recuerda el servidor** |
| POST | `/api/reserva/iniciar` | ídem | Paso 0: identifica al paciente |
| PUT | `/api/reserva/especialidad` | ídem | Paso 1 |
| POST | `/api/reserva/orientacion` | ídem | Paso 1 con IA: recibe `{"mensajes":[{"rol":"usuario","texto":"..."}]}` y devuelve `{"mensaje","especialidad","urgente"}`. Solo sugiere especialidades con profesionales activos; elegirla sigue siendo `PUT /especialidad`. `502` si el proveedor de IA no responde |
| PUT | `/api/reserva/profesional` | ídem | Paso 2 |
| GET | `/api/reserva/horarios?fecha=…` | ídem | Horarios libres del profesional **ya recordado** |
| PUT | `/api/reserva/horario` | ídem | Paso 3 |
| PUT | `/api/reserva/volver` | ídem | Retrocede un paso y olvida lo posterior |
| POST | `/api/reserva/confirmar` | ídem | Paso 4: crea el turno y cierra la conversación |
| DELETE | `/api/reserva` | ídem | Abandona la reserva en curso |

### Tablero

| Método | Ruta | Roles | Respuesta |
|---|---|---|---|
| GET | `/api/tablero` | RECEPCION, MEDICO, ADMIN | Métricas y próximos turnos, en una sola llamada |

### Formato de los errores

Todos los errores devuelven el mismo cuerpo:

```json
{ "error": "No se puede agendar un turno en el pasado.", "codigo": 400 }
```

`400` regla de negocio incumplida · `401` sin token, token inválido o vencido, o
credenciales incorrectas · `403` rol sin permiso · `404` no existe · **`409` transición de
estado inválida (patrón State)** · `502` falla un sistema externo.

### Compatibilidad con la API de la Entrega 1

`POST /api/turnos` sigue aceptando el cuerpo original, con el paciente embebido y el
profesional por nombre:

```json
{
  "paciente": { "nombre": "Diego Sosa", "documento": "33222111" },
  "profesional": "Dr. Martin Ruiz",
  "especialidad": "Traumatologia",
  "fechaHora": "2026-12-15T10:30"
}
```

La interfaz nueva usa la forma corta, que es la recomendada:

```json
{ "documentoPaciente": "33222111", "profesionalId": 2, "fechaHora": "2026-12-15T10:30" }
```

Las dos terminan en el mismo objeto de negocio. El detalle está en `PedidoTurno`.

---

## 6. Estructura del proyecto

```
mediconecta/
├── pom.xml                                   WAR · Jakarta EE 10 (provided) · Java 17
├── README.md
├── docs/
│   ├── Entrega_Obligatoria_1.md              documento técnico de la entrega
│   ├── arquitectura.md                       capas, componentes, patrones y decisiones
│   ├── guion-defensa.md                      recorrido de la demo, paso a paso
│   ├── preguntas-defensa.md                  preguntas probables y respuestas cortas
│   └── trello-actualizaciones.md             estado del tablero
├── scripts/
│   ├── smoke-test.ps1                        95 pruebas de la API de punta a punta
│   ├── db/
│   │   ├── levantar-postgres.ps1             PostgreSQL en Docker
│   │   ├── crear-base.sql                    base y usuario en un PostgreSQL local
│   │   └── esquema.sql                       modelo relacional documentado
│   └── wildfly/
│       ├── configurar-wildfly.ps1            driver + datasource + seguridad, en un paso
│       ├── configurar-datasource.cli         script de jboss-cli del datasource
│       └── configurar-seguridad.cli          integrated-jaspi=false para JWT
└── src/main/
    ├── java/com/mediconecta/
    │   ├── modelo/          Paciente · Profesional · Disponibilidad · Turno · EstadoTurno · Usuario
    │   │   └── estado/      patron State: TurnoProgramado · TurnoCancelado · TurnoAtendido
    │   ├── datos/           RepositorioTurnos · RepositorioPacientes · RepositorioProfesionales
    │   │   ├── jpa/         implementaciones sobre JPA + PostgreSQL (las activas)
    │   │   └── memoria/     implementaciones en memoria (@Alternative, desactivadas)
    │   ├── negocio/         GestionTurnos · GestionPacientes · GestionProfesionales
    │   │                    AsistenteReservaTurno (@Stateful) · FachadaReservas · FabricaTurnos
    │   │                    Roles · excepciones · carga de datos de ejemplo
    │   ├── seguridad/       JWT: MecanismoAutenticacionJwt · ServicioJwt · HashDeClaves
    │   ├── mensajeria/      JMS: productor + 3 MDB (cola y topico)
    │   ├── integraciones/   Adapter SOAP + REST de obras sociales (y sus simulaciones)
    │   └── presentacion/    recursos JAX-RS · ExceptionMappers
    │                        ConversacionDeReserva (puente sesión HTTP ↔ EJB stateful)
    ├── resources/META-INF/persistence.xml
    └── webapp/
        ├── index.html · login.html
        ├── css/styles.css                    design system del prototipo
        ├── js/                               módulos ES: api, ui, layout y uno por pantalla
        ├── app/                              pantallas protegidas
        └── WEB-INF/web.xml · jboss-web.xml · beans.xml
src/test/java/                                58 tests unitarios JUnit 5
```

---

## 7. Pruebas

### Tests unitarios (sin servidor ni base de datos)

```powershell
mvn test                                                  # los 58
mvn test -Dtest=EstadoDelTurnoTest                        # una clase
mvn test -Dtest=GestionTurnosTest#cancelarLiberaElHorario # un test
```

Cubren el patrón State, las reglas de `GestionTurnos`, el Factory, la emisión y validación
de JWT, el hash de contraseñas, la autenticación, el ruteo del Adapter de cobertura y las reglas del asistente de IA (sin llamar al proveedor). Los EJB
se prueban como objetos comunes con los repositorios en memoria.

### Prueba de humo (contra la aplicación desplegada)

```powershell
.\scripts\smoke-test.ps1
.\scripts\smoke-test.ps1 -LogWildFly C:\wildfly-36.0.1.Final\standalone\log\server.log
```

Recorre de punta a punta: salud, login JWT (credenciales malas, token adulterado), seguridad
por roles, turnos (fecha pasada, superposición, ownership, 409 del patrón State), pacientes,
profesionales, cobertura SOAP/REST, los cuatro pasos del asistente stateful y, con
`-LogWildFly`, que la cola y el tópico JMS entregaron los mensajes. Devuelve código de salida
0 si todas pasan.

Última ejecución (11/11/2026) sobre WildFly 36.0.1.Final (`standalone-full.xml`) + JDK 24 +
PostgreSQL 16: **95 pruebas OK, 0 fallidas**.

---

## 8. Ejecutar sin base de datos (plan B)

Si en la máquina donde se va a mostrar el TP no hay forma de levantar PostgreSQL, el
proyecto puede correr con los repositorios en memoria. Son dos cambios y **no se toca ni
una línea de la capa de negocio ni de la de presentación**, que es justamente la prueba de
que el patrón DAO funciona:

1. En `src/main/webapp/WEB-INF/beans.xml`, descomentar el bloque `<alternatives>`
   (incluye los usuarios en memoria).
2. Borrar `src/main/resources/META-INF/persistence.xml` antes de empaquetar (si queda,
   WildFly intenta crear la unidad de persistencia y el despliegue falla al no encontrar el
   datasource).

Los datos se pierden al reiniciar el servidor. Es un plan de contingencia, no el modo
normal de la entrega.

---

## 9. Limitaciones conocidas

- La carga de datos de ejemplo corre una sola vez, cuando la base está vacía. Para volver
  al estado inicial: `TRUNCATE turnos, disponibilidades, profesionales, pacientes RESTART IDENTITY CASCADE;`
  y redesplegar.
- Un turno ocupa un único horario de inicio; no se modela la duración variable de la
  atención ni el solapamiento parcial entre turnos de distinta duración.
- Marcar un turno como atendido no registra todavía el detalle de la atención (diagnóstico,
  indicaciones).
- El JWT no se puede revocar antes de que venza (no hay lista de tokens revocados): cerrar
  sesión lo descarta en el navegador. Vence a las 2 horas por omisión
  (`mediconecta.jwt.minutos`).
- Cuando el contenedor rechaza una invocación por rol, WildFly escribe un `EJBAccessException`
  con stack trace completo en el log. Es ruidoso pero correcto, y de hecho resulta útil
  durante la defensa para mostrar que quien rechaza es el contenedor y no la aplicación.
