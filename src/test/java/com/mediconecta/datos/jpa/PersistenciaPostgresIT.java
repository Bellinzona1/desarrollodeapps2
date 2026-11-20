package com.mediconecta.datos.jpa;

import com.mediconecta.modelo.CanalNotificacion;
import com.mediconecta.modelo.ClaveIdempotencia;
import com.mediconecta.modelo.Notificacion;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.TipoNotificacion;
import com.mediconecta.modelo.Turno;
import com.mediconecta.negocio.FabricaTurnos;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de integracion de la capa de datos contra un PostgreSQL 16 real.
 *
 * <p>Testcontainers levanta la base en Docker, Flyway le aplica las mismas
 * migraciones de {@code db/migraciones} que usan el CI y Docker Compose, y
 * Hibernate (la version que trae WildFly 36) arranca con
 * {@code hbm2ddl.auto=validate}: si una entidad no coincide con el esquema,
 * el test falla. Despues se prueban los repositorios JPA reales y las
 * restricciones de la base que sostienen las reglas del sistema (idempotencia
 * de avisos y de reservas, concurrencia optimista con {@code @Version}).</p>
 *
 * <p>Sin Docker en la maquina, el test se saltea; en el CI siempre corre.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class PersistenciaPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static EntityManagerFactory fabrica;
    private static MigrateResult migraciones;

    @BeforeAll
    static void migrarYArrancarHibernate() {
        migraciones = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:db/migraciones")
                .load()
                .migrate();
        fabrica = Persistence.createEntityManagerFactory("mediconecta-integracion", Map.of(
                "jakarta.persistence.jdbc.driver", "org.postgresql.Driver",
                "jakarta.persistence.jdbc.url", POSTGRES.getJdbcUrl(),
                "jakarta.persistence.jdbc.user", POSTGRES.getUsername(),
                "jakarta.persistence.jdbc.password", POSTGRES.getPassword()));
    }

    @AfterAll
    static void cerrar() {
        if (fabrica != null) {
            fabrica.close();
        }
    }

    /** Ejecuta el bloque dentro de una transaccion, como haria un EJB con JTA. */
    private static <T> T enTransaccion(Function<EntityManager, T> bloque) {
        EntityManager em = fabrica.createEntityManager();
        try {
            em.getTransaction().begin();
            T resultado = bloque.apply(em);
            em.getTransaction().commit();
            return resultado;
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }

    /** Inyecta el EntityManager en el repositorio, como haria @PersistenceContext. */
    private static <T> T repositorio(T repositorio, EntityManager em) {
        try {
            Field campo = repositorio.getClass().getDeclaredField("em");
            campo.setAccessible(true);
            campo.set(repositorio, em);
            return repositorio;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Guarda un paciente, un profesional y un turno; devuelve el id del turno. */
    private static Long turnoNuevo(LocalDateTime fechaHora) {
        return enTransaccion(em -> {
            Paciente paciente = repositorio(new RepositorioPacientesJpa(), em)
                    .guardar(new Paciente("Paciente " + unico(), String.valueOf(10_000_000 + (int) (Math.random() * 1e7))));
            Profesional profesional = repositorio(new RepositorioProfesionalesJpa(), em)
                    .guardar(new Profesional("Dra. " + unico(), "Cardiologia", "MP-" + unico()));
            Turno turno = new FabricaTurnos().crear(paciente, profesional, null, fechaHora, "control");
            return repositorio(new RepositorioTurnosJpa(), em).guardar(turno).getId();
        });
    }

    @Test
    void lasMigracionesSeAplicanYElEsquemaCoincideConLasEntidades() {
        assertTrue(migraciones.success);
        assertEquals(3, migraciones.migrationsExecuted, "V1, V2 y V3");
        // Si alguna entidad no coincidiera con las tablas, createEntityManagerFactory
        // (hbm2ddl.auto=validate) ya habria fallado en @BeforeAll.
        assertNotNull(fabrica);
    }

    @Test
    void elRepositorioDeTurnosGuardaYConsultaContraLaBaseReal() {
        LocalDateTime fecha = LocalDateTime.of(2030, 3, 4, 10, 30, 45);
        Long id = turnoNuevo(fecha);

        Turno guardado = enTransaccion(em -> repositorio(new RepositorioTurnosJpa(), em).buscarPorId(id).orElseThrow());
        assertEquals(LocalDateTime.of(2030, 3, 4, 10, 30), guardado.getFechaHora(), "la fabrica trunca al minuto");
        assertEquals("PROGRAMADO", guardado.getEstado().name());
        assertEquals(0, guardado.getVersion());

        Long profesionalId = guardado.getProfesional().getId();
        String documento = guardado.getPaciente().getDocumento();
        boolean ocupado = enTransaccion(em -> repositorio(new RepositorioTurnosJpa(), em)
                .existeTurnoVigente(profesionalId, guardado.getFechaHora()));
        assertTrue(ocupado);
        assertEquals(1, enTransaccion(em -> repositorio(new RepositorioTurnosJpa(), em)
                .listarPorPaciente(documento)).size());
        assertEquals(1, enTransaccion(em -> repositorio(new RepositorioTurnosJpa(), em)
                .listarPorProfesionalYFecha(profesionalId, fecha.toLocalDate())).size());
    }

    @Test
    void unTurnoCanceladoLiberaElHorarioYSubeLaVersion() {
        Long id = turnoNuevo(LocalDateTime.of(2030, 3, 5, 11, 0));

        Turno cancelado = enTransaccion(em -> {
            RepositorioTurnosJpa turnos = repositorio(new RepositorioTurnosJpa(), em);
            Turno turno = turnos.buscarPorId(id).orElseThrow();
            turno.cancelar();
            return turnos.actualizar(turno);
        });

        assertEquals("CANCELADO", cancelado.getEstado().name());
        assertEquals(1, cancelado.getVersion(), "el flush despues del merge ya incremento la version (ETag)");
        boolean ocupado = enTransaccion(em -> repositorio(new RepositorioTurnosJpa(), em)
                .existeTurnoVigente(cancelado.getProfesional().getId(), cancelado.getFechaHora()));
        assertFalse(ocupado);
    }

    @Test
    void laVersionDetectaDosModificacionesSimultaneas() {
        Long id = turnoNuevo(LocalDateTime.of(2030, 3, 6, 9, 0));

        EntityManager primero = fabrica.createEntityManager();
        EntityManager segundo = fabrica.createEntityManager();
        try {
            primero.getTransaction().begin();
            segundo.getTransaction().begin();
            Turno visto1 = primero.find(Turno.class, id);
            Turno visto2 = segundo.find(Turno.class, id);

            visto1.setMotivo("cambio de recepcion");
            primero.getTransaction().commit();

            visto2.setMotivo("cambio simultaneo");
            RollbackException rechazo = assertThrows(RollbackException.class, () -> segundo.getTransaction().commit());
            assertInstanceOf(OptimisticLockException.class, rechazo.getCause());
        } finally {
            primero.close();
            segundo.close();
        }
    }

    @Test
    void laBaseRechazaUnAvisoDuplicadoDelMismoEvento() {
        Long turnoId = turnoNuevo(LocalDateTime.of(2030, 3, 7, 9, 0));
        String eventoId = UUID.randomUUID().toString();
        Function<EntityManager, Notificacion> avisar = em -> repositorio(new RepositorioNotificacionesJpa(), em)
                .guardar(new Notificacion(eventoId, TipoNotificacion.CONFIRMACION, CanalNotificacion.EMAIL, turnoId,
                        "paciente@ejemplo.com", "Turno confirmado", LocalDateTime.now()));

        enTransaccion(avisar);
        assertThrows(PersistenceException.class, () -> enTransaccion(avisar),
                "UNIQUE (evento_id, tipo): una reentrega del broker no genera un segundo aviso");
        assertEquals(1, enTransaccion(em -> repositorio(new RepositorioNotificacionesJpa(), em)
                .listarPorTurno(turnoId)).size());
        boolean existe = enTransaccion(em -> repositorio(new RepositorioNotificacionesJpa(), em)
                .existe(eventoId, TipoNotificacion.CONFIRMACION));
        assertTrue(existe);
    }

    @Test
    void laMismaIdempotencyKeyDelMismoUsuarioSoloSeRegistraUnaVez() {
        Long turnoId = turnoNuevo(LocalDateTime.of(2030, 3, 8, 9, 0));
        String clave = UUID.randomUUID().toString();
        Function<EntityManager, Boolean> registrar = em -> {
            Turno turno = em.find(Turno.class, turnoId);
            return repositorio(new RepositorioIdempotenciaJpa(), em)
                    .registrar(new ClaveIdempotencia(clave, "recepcion", "huella", turno, LocalDateTime.now()));
        };

        boolean primera = enTransaccion(registrar);
        assertTrue(primera);
        EntityManager em = fabrica.createEntityManager();
        try {
            em.getTransaction().begin();
            assertFalse(registrar.apply(em), "UNIQUE (usuario, clave): el segundo registro se rechaza");
            em.getTransaction().rollback();
        } finally {
            em.close();
        }
        assertTrue(enTransaccion(e -> repositorio(new RepositorioIdempotenciaJpa(), e)
                .buscar("recepcion", clave)).isPresent());
    }
}
