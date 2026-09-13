package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Nota de texto libre sobre un pedido puntual (ej. "entregado en porteria a Fulano") —
 * a diferencia de calificacionComentario (que carga el cliente al calificar), esto se
 * carga en cualquier momento del viaje y se ve en el detalle del panel, al estilo de la
 * pestaña "Comentarios" de la app anterior. La deja el cadete (cadete != null) o el
 * admin desde el panel (adminUsername != null) — mejora 101, antes el admin solo podía
 * leerlos.
 */
@Entity
@Table(name = "pedido_comentario")
public class PedidoComentario {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(optional = true)
    @JoinColumn(name = "cadete_id", nullable = true)
    private Cadete cadete;

    @Column(name = "admin_username", length = 100)
    private String adminUsername;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
