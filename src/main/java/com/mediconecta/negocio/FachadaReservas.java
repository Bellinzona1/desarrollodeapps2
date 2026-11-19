package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioIdempotencia;
import com.mediconecta.mensajeria.NotificadorTurnos;
import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.RegistroIdempotencia;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Patron Facade: unica puerta de entrada al caso de uso "reservar un turno".
 *
 * <p><b>Problema que resuelve.</b> Reservar un turno no es una operacion de un
 * solo componente. Hay que resolver el paciente (o darlo de alta si es la
 * primera vez), resolver el profesional, verificar que la especialidad sea
 * coherente y recien entonces agendar. Si esa coreografia viviera en
 * {@code RecursoTurnos}, la capa de presentacion tendria que inyectar y ordenar
 * tres componentes de negocio; y como el asistente stateful hace exactamente lo
 * mismo, la secuencia quedaria duplicada en dos lugares que se irian
 * desincronizando.</p>
 *
 * <p><b>Como lo resuelve.</b> La fachada concentra la coordinacion. Los dos
 * clientes -el alta directa por REST y el asistente {@code @Stateful}- llaman a
 * {@link #reservar} y no saben cuantos componentes hay detras. La fachada
 * <b>coordina pero no decide</b>: cada regla la sigue aplicando su dueno
 * ({@code GestionPacientes} valida el padron, {@code GestionTurnos} valida fecha
 * y superposicion). Es la diferencia entre un Facade y un componente que se
 * convierte en el cajon de sastre del sistema.</p>
 */
@Stateless
public class FachadaReservas {

    @Inject
    private GestionTurnos gestionTurnos;

    @Inject
    private GestionPacientes gestionPacientes;

    @Inject
    private GestionProfesionales gestionProfesionales;

    @Inject
    private NotificadorTurnos notificadorTurnos;

    @Inject
    private RepositorioIdempotencia repositorioIdempotencia;

    @Resource
    private SessionContext contexto;

    /** Cuanto tiempo se recuerda una {@code Idempotency-Key} ya usada. */
    static final Duration VIGENCIA_CLAVE = Duration.ofHours(24);

    /** Claves aceptadas: lo que generan los clientes (UUID, ULID, etc.), hasta 255 caracteres. */
    private static final Pattern FORMATO_CLAVE = Pattern.compile("[A-Za-z0-9._:-]{1,255}");

    // ---------- Consultas que alimentan la pantalla de reserva ----------

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<String> especialidades() {
        return gestionProfesionales.listarEspecialidades();
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Profesional> profesionalesDe(String especialidad) {
        return gestionProfesionales.listarPorEspecialidad(especialidad);
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<LocalDateTime> horariosLibres(Long profesionalId, LocalDate fecha) {
        return gestionProfesionales.horariosLibres(profesionalId, fecha);
    }

    /**
     * Estado general de la clinica para el tablero de inicio.
     *
     * <p>Es el otro caso donde la fachada evita que la presentacion orqueste:
     * una sola llamada en lugar de cuatro.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public ResumenAgenda resumenDelDia() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        List<Turno> deHoy = gestionTurnos.turnosDelDia(hoy);

        long pacientes = gestionPacientes.listarPacientes().stream()
                .filter(Paciente::isActivo)
                .count();

        long profesionales = gestionProfesionales.listarProfesionales().stream()
                .filter(Profesional::isActivo)
                .count();

        List<Turno> proximos = gestionTurnos.listarTurnos().stream()
                .filter(t -> t.getEstado() == EstadoTurno.PROGRAMADO)
                .filter(t -> !t.getFechaHora().isBefore(ahora))
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .limit(6)
                .toList();

        long pendientes = gestionTurnos.listarTurnos().stream()
                .filter(t -> t.getEstado() == EstadoTurno.PROGRAMADO)
                .filter(t -> !t.getFechaHora().isBefore(ahora))
                .count();

        return new ResumenAgenda(
                deHoy.stream().filter(t -> t.getEstado() != EstadoTurno.CANCELADO).count(),
                pacientes, profesionales, pendientes, proximos);
    }

    // ---------- El caso de uso completo ----------

    /**
     * Reserva un turno de punta a punta.
     *
     * <p>Pasos: resolver el paciente (alta al vuelo si el documento todavia no
     * esta en el padron), resolver el profesional (por id o, para compatibilidad
     * con la API original, por nombre) y delegar el alta en
     * {@code GestionTurnos}, que es quien valida fecha y superposicion.</p>
     *
     * <p>Un usuario con rol PACIENTE solo puede reservar para si mismo: la
     * comprobacion la hace {@code GestionPacientes}, que es quien conoce la
     * asociacion entre el usuario autenticado y su ficha.</p>
     *
     * <p><b>Transaccion declarativa de varios pasos.</b> Resolver paciente,
     * resolver profesional, agendar el turno y avisar por JMS son pasos
     * distintos que tienen que confirmarse o deshacerse juntos: si cualquiera
     * de ellos lanza una excepcion de sistema (no una {@link ReglaNegocioException}
     * prevista), el contenedor deshace todo -incluida la creacion del turno en
     * la base- porque {@code @TransactionAttribute(REQUIRED)} hace explicito
     * que este metodo corre dentro de una unica transaccion JTA. Se deja
     * explicito aca porque es el flujo critico que la materia pide demostrar;
     * en el resto de los metodos de este EJB sigue aplicando el REQUIRED por
     * omision, sin necesidad de anotarlo.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno reservar(SolicitudTurno solicitud) {
        if (solicitud == null) {
            throw new ReglaNegocioException("Debe enviar los datos del turno.");
        }

        Paciente paciente = resolverPaciente(solicitud);
        gestionPacientes.exigirPropioDocumentoSiEsPaciente(paciente.getDocumento());
        Profesional profesional = resolverProfesional(solicitud);

        Turno turno = gestionTurnos.agendar(paciente, profesional,
                solicitud.getEspecialidad(), solicitud.getFechaHora(), solicitud.getMotivo());

        notificadorTurnos.turnoConfirmado(turno);

        return turno;
    }

    /**
     * Reserva con {@code Idempotency-Key}: si el cliente reintenta el mismo
     * pedido (por un timeout, un doble clic o una red inestable) recibe el
     * turno que ya se habia creado, en lugar de un segundo turno.
     *
     * <p>Todo ocurre en la misma transaccion JTA que {@link #reservar}: el
     * registro de la clave y el turno se confirman o se deshacen juntos.
     * Reglas (draft IETF "The Idempotency-Key HTTP Header Field"):</p>
     * <ul>
     *   <li>sin clave, se comporta igual que {@link #reservar};</li>
     *   <li>misma clave y mismo pedido: se devuelve el turno original
     *       ({@code repetida = true}) y no se envia otro mensaje JMS;</li>
     *   <li>misma clave y otro pedido: {@link IdempotenciaException} REUTILIZADA;</li>
     *   <li>misma clave mientras el primer pedido sigue en curso:
     *       {@link IdempotenciaException} EN_CURSO.</li>
     * </ul>
     * <p>La clave es por usuario: dos usuarios pueden usar el mismo valor.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ResultadoReserva reservarIdempotente(SolicitudTurno solicitud, String claveIdempotencia) {
        if (claveIdempotencia == null) {
            return new ResultadoReserva(reservar(solicitud), false);
        }
        if (solicitud == null) {
            throw new ReglaNegocioException("Debe enviar los datos del turno.");
        }
        String clave = normalizarClave(claveIdempotencia);
        String usuario = contexto.getCallerPrincipal().getName();
        String huella = huella(solicitud);
        LocalDateTime ahora = LocalDateTime.now();

        Optional<RegistroIdempotencia> previo = repositorioIdempotencia.buscar(usuario, clave);
        RegistroIdempotencia registro;
        if (previo.isEmpty()) {
            registro = new RegistroIdempotencia(usuario, clave, huella, ahora);
            if (!repositorioIdempotencia.registrar(registro)) {
                throw new IdempotenciaException(IdempotenciaException.Motivo.EN_CURSO,
                        "Ya hay un pedido en curso con la misma Idempotency-Key. Reintente en unos segundos.");
            }
        } else if (previo.get().getCreadaEn().isBefore(ahora.minus(VIGENCIA_CLAVE))) {
            // Clave vencida: se trata como nueva.
            registro = previo.get();
            registro.setHuella(huella);
            registro.setCreadaEn(ahora);
            registro.setTurnoId(null);
        } else {
            registro = previo.get();
            if (!registro.getHuella().equals(huella)) {
                throw new IdempotenciaException(IdempotenciaException.Motivo.REUTILIZADA,
                        "La Idempotency-Key ya se uso con otros datos. Genere una clave nueva para este pedido.");
            }
            if (registro.getTurnoId() != null) {
                return new ResultadoReserva(gestionTurnos.obtenerTurno(registro.getTurnoId()), true);
            }
            // El intento anterior con esta clave no llego a crear el turno
            // (fallo una regla de negocio): se vuelve a intentar.
        }

        Turno turno = reservar(solicitud);
        registro.setTurnoId(turno.getId());
        repositorioIdempotencia.actualizar(registro);
        return new ResultadoReserva(turno, false);
    }

    /** Acepta la clave tal cual o como cadena de Structured Fields ({@code "..."}). */
    static String normalizarClave(String clave) {
        String texto = clave.trim();
        if (texto.length() >= 2 && texto.startsWith("\"") && texto.endsWith("\"")) {
            texto = texto.substring(1, texto.length() - 1);
        }
        if (!FORMATO_CLAVE.matcher(texto).matches()) {
            throw new ReglaNegocioException("La cabecera Idempotency-Key debe tener entre 1 y 255 caracteres"
                    + " (letras, numeros, punto, guion, guion bajo o dos puntos).");
        }
        return texto;
    }

    /** SHA-256 de los datos del pedido: identifica "el mismo pedido" aunque cambie el orden del JSON. */
    static String huella(SolicitudTurno s) {
        String texto = String.join("\n",
                valor(s.getDocumentoPaciente()), valor(s.getNombrePaciente()),
                s.getProfesionalId() == null ? "" : s.getProfesionalId().toString(),
                valor(s.getNombreProfesional()), valor(s.getEspecialidad()),
                valor(s.getFechaHora()), valor(s.getMotivo()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no soporta SHA-256", e);
        }
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private Paciente resolverPaciente(SolicitudTurno solicitud) {
        String documento = solicitud.getDocumentoPaciente();
        if (documento == null || documento.isBlank()) {
            throw new ReglaNegocioException("El paciente debe tener nombre y documento.");
        }

        try {
            return gestionPacientes.buscarPorDocumento(documento);
        } catch (NoEncontradoException noExiste) {
            if (solicitud.getNombrePaciente() == null || solicitud.getNombrePaciente().isBlank()) {
                throw new ReglaNegocioException(
                        "No hay un paciente con documento " + documento
                                + ". Registrelo primero o envie tambien su nombre.");
            }
            Paciente nuevo = new Paciente(solicitud.getNombrePaciente(), documento);
            return gestionPacientes.registrarPaciente(nuevo);
        }
    }

    private Profesional resolverProfesional(SolicitudTurno solicitud) {
        if (solicitud.getProfesionalId() != null) {
            return gestionProfesionales.obtener(solicitud.getProfesionalId());
        }

        String nombre = solicitud.getNombreProfesional();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Debe indicar el profesional que atiende.");
        }

        return gestionProfesionales.listarProfesionales().stream()
                .filter(p -> p.getNombre().equalsIgnoreCase(nombre.trim()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(
                        "No hay ningun profesional llamado " + nombre + "."));
    }
}
