package com.mediconecta.integraciones;

import com.mediconecta.integraciones.simulacion.RespuestaPartner;
import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.TimeUnit;

/**
 * Adapter que habla REST con el partner moderno de obras sociales.
 *
 * <p>La URL base es configurable con la propiedad de sistema
 * {@code mediconecta.partner.baseUrl} porque en produccion apuntaria al
 * dominio real del partner, no al propio WAR de MediConecta.</p>
 *
 * <p>Timeouts de conexion y lectura de {@value #TIMEOUT_SEGUNDOS} segundos; el
 * fallback (ultimo dato bueno o "sin verificar") lo decide
 * {@link GestorCobertura} a traves de {@link ProteccionIntegracion}.</p>
 */
// @LocalBean: un EJB que implementa una interfaz solo expone esa interfaz;
// GestorCobertura inyecta cada adaptador por su clase concreta para elegir cual usar.
// NOT_SUPPORTED: una llamada remota no debe retener una transaccion de base de
// datos abierta, y una falla aca no tiene que marcar para rollback la del llamador.
@LocalBean
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AdaptadorObraSocialPartner implements ValidadorCobertura {

    /** Timeout de conexion y de lectura del cliente HTTP. */
    static final int TIMEOUT_SEGUNDOS = 5;

    private static final String URL_BASE_POR_DEFECTO =
            "http://localhost:8080/mediconecta/partner/cobertura";

    @Override
    public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
        Client cliente = ClientBuilder.newBuilder()
                .connectTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)
                .build();
        try {
            String urlBase = System.getProperty("mediconecta.partner.baseUrl", URL_BASE_POR_DEFECTO);

            try (Response respuesta = cliente.target(urlBase)
                    .queryParam("obraSocial", obraSocial)
                    .queryParam("afiliado", numeroAfiliado)
                    .request(MediaType.APPLICATION_JSON)
                    .get()) {

                if (respuesta.getStatus() != Response.Status.OK.getStatusCode()) {
                    throw new IntegracionExternaException(
                            "El partner de obras sociales respondio con codigo " + respuesta.getStatus() + ".");
                }

                RespuestaPartner cuerpo = respuesta.readEntity(RespuestaPartner.class);
                return new ResultadoCobertura(cuerpo.isVigente(), cuerpo.getPlan(), cuerpo.getCopago(), "PARTNER");
            }
        } catch (ProcessingException error) {
            throw new IntegracionExternaException("El partner de obras sociales no respondio.", error);
        } finally {
            cliente.close();
        }
    }
}
