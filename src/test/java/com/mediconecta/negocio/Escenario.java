package com.mediconecta.negocio;

import com.mediconecta.datos.memoria.RepositorioIdempotenciaEnMemoria;
import com.mediconecta.datos.memoria.RepositorioPacientesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioProfesionalesEnMemoria;
import com.mediconecta.datos.memoria.RepositorioTurnosEnMemoria;
import com.mediconecta.mensajeria.NotificadorTurnos;
import com.mediconecta.modelo.Disponibilidad;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import jakarta.ejb.SessionContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Los componentes de negocio cableados entre si como lo haria el contenedor,
 * sobre los repositorios en memoria y con un llamador que se puede cambiar.
 *
 * <p>Datos: Dra. Laura Fernandez (Cardiologia, usuario "medico", lunes a
 * viernes de 9 a 13, turnos de 30 min), Dr. Martin Ruiz (Traumatologia, sin
 * usuario), Ana Gomez (30111222, usuario "paciente", OSDE 210) y Bruno Diaz
 * (28999888, Swiss Medical SMG02).</p>
 */
public final class Escenario {

    public final RepositorioTurnosEnMemoria repoTurnos = new RepositorioTurnosEnMemoria();
    public final RepositorioPacientesEnMemoria repoPacientes = new RepositorioPacientesEnMemoria();
    public final RepositorioProfesionalesEnMemoria repoProfesionales = new RepositorioProfesionalesEnMemoria();
    public final RepositorioIdempotenciaEnMemoria repoIdempotencia = new RepositorioIdempotenciaEnMemoria();

    public final FabricaTurnos fabrica = new FabricaTurnos();
    public final GestionTurnos gestionTurnos = new GestionTurnos();
    public final GestionPacientes gestionPacientes = new GestionPacientes();
    public final GestionProfesionales gestionProfesionales = new GestionProfesionales();
    public final FachadaReservas fachada = new FachadaReservas();
    public final NotificadorDePrueba notificador = new NotificadorDePrueba();

    public final SessionContext contexto;

    public final Paciente ana;
    public final Paciente bruno;
    public final Profesional laura;
    public final Profesional martin;

    private String usuario = "recepcion";
    private Set<String> roles = Set.of(Roles.RECEPCION);

    public Escenario() {
        contexto = Inyeccion.llamadorVariable(() -> usuario, () -> roles);

        Inyeccion.campo(gestionTurnos, "repositorio", repoTurnos);
        Inyeccion.campo(gestionTurnos, "fabricaTurnos", fabrica);
        Inyeccion.campo(gestionTurnos, "contexto", contexto);

        Inyeccion.campo(gestionPacientes, "repositorio", repoPacientes);
        Inyeccion.campo(gestionPacientes, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(gestionPacientes, "contexto", contexto);

        Inyeccion.campo(gestionProfesionales, "repositorio", repoProfesionales);
        Inyeccion.campo(gestionProfesionales, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(gestionProfesionales, "contexto", contexto);

        Inyeccion.campo(fachada, "gestionTurnos", gestionTurnos);
        Inyeccion.campo(fachada, "gestionPacientes", gestionPacientes);
        Inyeccion.campo(fachada, "gestionProfesionales", gestionProfesionales);
        Inyeccion.campo(fachada, "notificadorTurnos", notificador);
        Inyeccion.campo(fachada, "repositorioIdempotencia", repoIdempotencia);
        Inyeccion.campo(fachada, "contexto", contexto);

        laura = new Profesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234");
        laura.setUsuario("medico");
        for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            laura.getDisponibilidades().add(new Disponibilidad(dia, LocalTime.of(9, 0), LocalTime.of(13, 0), 30));
        }
        laura.setActivo(true);
        repoProfesionales.guardar(laura);

        martin = new Profesional("Dr. Martin Ruiz", "Traumatologia", "MP-20876");
        martin.getDisponibilidades().add(new Disponibilidad(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(18, 0), 30));
        martin.setActivo(true);
        repoProfesionales.guardar(martin);

        ana = new Paciente("Ana Gomez", "30111222");
        ana.setUsuario("paciente");
        ana.setCobertura("OSDE 210");
        ana.setActivo(true);
        repoPacientes.guardar(ana);

        bruno = new Paciente("Bruno Diaz", "28999888");
        bruno.setCobertura("Swiss Medical SMG02");
        bruno.setActivo(true);
        repoPacientes.guardar(bruno);
    }

    /** Cambia el usuario que "llama" a los EJB. */
    public void como(String usuario, String... roles) {
        this.usuario = usuario;
        this.roles = Set.of(roles);
    }

    public void comoRecepcion() {
        como("recepcion", Roles.RECEPCION);
    }

    public void comoPaciente() {
        como("paciente", Roles.PACIENTE);
    }

    /** El proximo dia habil (a partir de manana) a la hora indicada, en el formato de la API. */
    public static String proximoHabil(int hora, int minuto) {
        LocalDate dia = LocalDate.now().plusDays(1);
        while (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dia = dia.plusDays(1);
        }
        return dia.atTime(hora, minuto).format(Turno.FORMATO_FECHA_HORA);
    }

    /** Pedido valido de turno para Ana con la Dra. Fernandez. */
    public SolicitudTurno solicitudParaAna(String fechaHora) {
        SolicitudTurno solicitud = new SolicitudTurno();
        solicitud.setDocumentoPaciente(ana.getDocumento());
        solicitud.setProfesionalId(laura.getId());
        solicitud.setEspecialidad("Cardiologia");
        solicitud.setFechaHora(fechaHora);
        solicitud.setMotivo("Control");
        return solicitud;
    }

    /** Doble de prueba del productor JMS: anota los turnos en lugar de enviarlos. */
    public static final class NotificadorDePrueba extends NotificadorTurnos {

        public final List<Turno> enviados = new ArrayList<>();

        @Override
        public void turnoConfirmado(Turno turno) {
            enviados.add(turno);
        }
    }
}
