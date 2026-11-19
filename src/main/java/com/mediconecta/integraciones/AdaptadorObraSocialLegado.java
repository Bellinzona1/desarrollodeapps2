package com.mediconecta.integraciones;

import com.mediconecta.integraciones.simulacion.ServicioObraSocialLegado;
import com.mediconecta.negocio.IntegracionExternaException;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebServiceException;

import javax.xml.namespace.QName;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter que habla SOAP con el sistema legado de obras sociales.
 *
 * <p>La URL del WSDL es configurable con la propiedad de sistema
 * {@code mediconecta.soap.wsdl} porque depende de donde quedo publicado
 * {@code ServicioObraSocialLegadoImpl} en el WildFly real. El valor por omision
 * es el del despliegue estandar del proyecto
 * ({@code http://localhost:8080/mediconecta/ServicioObraSocialLegadoImpl?wsdl},
 * verificado en la Entrega 2).</p>
 *
 * <p><b>Resiliencia.</b> El {@link Service} (que lee y analiza el WSDL) se crea
 * una sola vez por URL y se reutiliza: leer el WSDL en cada consulta duplicaba
 * las llamadas remotas. Cada puerto tiene timeouts de conexion y de respuesta de
 * {@value #TIMEOUT_MILISEGUNDOS} ms (se fijan las dos variantes del nombre de
 * propiedad, {@code jakarta.*} y {@code javax.*}, que reconocen las distintas
 * versiones de JBossWS/CXF). El corte duro a los 5 segundos, el circuit breaker
 * y el fallback los aplica {@link ProteccionIntegracion} desde
 * {@link GestorCobertura}.</p>
 */
// @LocalBean: un EJB que implementa una interfaz solo expone esa interfaz;
// GestorCobertura inyecta cada adaptador por su clase concreta para elegir cual usar.
// NOT_SUPPORTED: una llamada remota no debe retener una transaccion de base de datos.
@LocalBean
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class AdaptadorObraSocialLegado implements ValidadorCobertura {

    static final int TIMEOUT_MILISEGUNDOS = 5_000;

    private static final String WSDL_POR_DEFECTO =
            "http://localhost:8080/mediconecta/ServicioObraSocialLegadoImpl?wsdl";

    private static final QName NOMBRE_SERVICIO =
            new QName("http://simulacion.integraciones.mediconecta.com/", "ServicioObraSocialLegadoImplService");

    /** Propiedades de timeout que reconocen las implementaciones de JAX-WS. */
    static final String[] PROPIEDADES_TIMEOUT = {
            "jakarta.xml.ws.client.connectionTimeout", "jakarta.xml.ws.client.receiveTimeout",
            "javax.xml.ws.client.connectionTimeout", "javax.xml.ws.client.receiveTimeout"};

    /** {@code Service} ya construido por URL de WSDL (es seguro compartirlo entre hilos). */
    private static final Map<String, Service> SERVICIOS = new ConcurrentHashMap<>();

    @Override
    public ResultadoCobertura validar(String obraSocial, String numeroAfiliado) {
        try {
            ServicioObraSocialLegado puerto = puerto(System.getProperty("mediconecta.soap.wsdl", WSDL_POR_DEFECTO));

            boolean vigente = puerto.consultarVigencia(obraSocial, numeroAfiliado);
            String plan = puerto.consultarPlan(obraSocial, numeroAfiliado);

            return new ResultadoCobertura(vigente, plan, 0, "LEGADO");
        } catch (WebServiceException error) {
            throw new IntegracionExternaException(
                    "El sistema legado de obras sociales no respondio.", error);
        }
    }

    private static ServicioObraSocialLegado puerto(String urlWsdl) {
        Service servicio = SERVICIOS.computeIfAbsent(urlWsdl, url -> Service.create(aUrl(url), NOMBRE_SERVICIO));
        // El puerto (proxy) no es seguro entre hilos: uno por consulta.
        ServicioObraSocialLegado puerto = servicio.getPort(ServicioObraSocialLegado.class);
        configurarTimeouts(((BindingProvider) puerto).getRequestContext());
        return puerto;
    }

    static void configurarTimeouts(Map<String, Object> contextoDelPedido) {
        for (String propiedad : PROPIEDADES_TIMEOUT) {
            contextoDelPedido.put(propiedad, TIMEOUT_MILISEGUNDOS);
        }
    }

    private static URL aUrl(String texto) {
        try {
            return new URL(texto);
        } catch (MalformedURLException error) {
            throw new IntegracionExternaException(
                    "La URL del sistema legado de obras sociales esta mal configurada.", error);
        }
    }
}
