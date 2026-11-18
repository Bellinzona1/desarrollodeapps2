package com.mediconecta.presentacion;

import com.mediconecta.integraciones.MensajeChat;

import java.util.List;

/** Cuerpo de {@code POST /api/reserva/orientacion}: la conversacion completa. */
public class PedidoOrientacion {

    private List<MensajeChat> mensajes;

    public List<MensajeChat> getMensajes() {
        return mensajes;
    }

    public void setMensajes(List<MensajeChat> mensajes) {
        this.mensajes = mensajes;
    }
}
