package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un cadete al que ya se le sacó ESTE pedido puntual sin reasignarlo a nadie ("Quitar" sin
 * elegir un nuevo cadete) queda excluido de volver a ser candidato de la asignación
 * automática para el mismo pedido — a diferencia de un rechazo real (OfertaPedido), esto NO
 * lo hizo el cadete ni cuenta para su historial/tasa de rechazo, fue una decisión del admin.
 * Ver {@code PedidoService#quitarCadete} (quien la crea) y {@code #buscarCandidato} (quien la
 * filtra). Mismo criterio que el resto de los topes de asignación automática: el admin puede
 * seguir asignando a mano a este cadete igual si le parece razonable.
 */
@Entity
@Table(name = "pedido_cadete_excluido")
public class PedidoCadeteExcluido {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

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

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
