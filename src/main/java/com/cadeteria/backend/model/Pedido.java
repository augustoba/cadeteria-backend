package com.cadeteria.backend.model;

import com.cadeteria.backend.util.TelefonoUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    private String id;

    /**
     * Número correlativo simple para mostrar/comunicar (arranca en 1500000, ver
     * DataSeeder y ConfiguracionService.siguienteNumeroPedido) — el id UUID sigue
     * siendo la clave técnica interna, esto es solo para humanos (panel, comprobante).
     */
    @Column(nullable = false, unique = true)
    private Long numero;

    @Column(nullable = false)
    private String clienteTelefono;

    /**
     * Snapshot editable por pedido — nunca se persiste como nombre "del cliente" a futuro,
     * es solo el dato de este pedido puntual (spec 5.6, para no repetir el problema de
     * modelar el telefono como clave unica de cliente).
     */
    @Column(nullable = false)
    private String clienteNombre;

    @Column(nullable = false, length = 500)
    private String origenDireccion;
    @Column(nullable = false)
    private Double origenLat;
    @Column(nullable = false)
    private Double origenLng;

    @Column(nullable = false, length = 500)
    private String destinoDireccion;
    @Column(nullable = false)
    private Double destinoLat;
    @Column(nullable = false)
    private Double destinoLng;

    /** Valor del tramite/servicio de envio. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /**
     * "Dinero" que el cadete tiene que transportar (puede ser 0 si el pedido no
     * involucra efectivo) — es el monto que cuenta contra el tope de 5.2, no el
     * precio del tramite.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoDeclarado = BigDecimal.ZERO;

    /**
     * Declarado por el cliente al pedir (mejora 2026-09-23): transporta objetos de valor
     * (no dinero en efectivo, que ya se cubre con montoDeclarado) — solo informativo, no
     * afecta el tope de 5.2 ni la asignación, se muestra en el seguimiento público como
     * confirmación de lo declarado.
     */
    @Column(nullable = false)
    private boolean llevaValores = false;

    @Column(length = 1000)
    private String detalle;

    /**
     * Piso, depto y observaciones de cada dirección (mejora 2026-09-24): opcionales, para
     * edificios — piso y depto por separado, si no "1 15" no se sabe cuál es cuál o indicaciones ("timbre roto", "portón verde"). Van aparte de la dirección
     * para no ensuciar el geocoding. El cadete los ve recién al aceptar el viaje.
     */
    @Column(length = 20)
    private String origenPiso;
    @Column(length = 20)
    private String origenDepto;
    @Column(length = 300)
    private String origenObservaciones;
    @Column(length = 20)
    private String destinoPiso;
    @Column(length = 20)
    private String destinoDepto;
    @Column(length = 300)
    private String destinoObservaciones;

    /** Pedido cargado para el futuro (panel actual: "¿Pedido programado?"). */
    @Column(nullable = false)
    private boolean programado = false;
    private Instant fechaProgramada;

    @ManyToOne
    @JoinColumn(name = "zona_id")
    private Zona zona;

    /**
     * Tildado: el matching automático solo ofrece el pedido a cadetes en MOTO. Sin tildar
     * (default): entra cualquiera, moto o bici — la bici queda sujeta a los topes de
     * distancia (ver PedidoService.buscarCandidato).
     */
    @Column(nullable = false)
    private boolean requiereMoto = false;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estado_id")
    private EstadoPedido estado;

    @ManyToOne
    @JoinColumn(name = "cadete_asignado_id")
    private Cadete cadeteAsignado;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();
    private Instant asignadoEn;
    /**
     * Cuándo el cadete abrió la pantalla del viaje por primera vez estando la oferta
     * todavía PENDIENTE (antes no había forma de saber si el cadete ya lo había visto y
     * estaba esperando a que se le venza el tiempo para que se reasigne solo, o
     * directamente no lo había abierto). Se limpia cada vez que el pedido se vuelve a
     * ofertar (ver PedidoService.ofertar) para no arrastrar la marca de una oferta
     * anterior a otro cadete.
     */
    private Instant vistoEn;
    private Instant aceptadoEn;
    /** Cuando el cadete toco "Retirado" (paso por lo del cliente a buscar el pedido). */
    private Instant retiradoEn;
    private Instant finalizadoEn;
    /** Solo se completa si el pedido termina CANCELADO — para el timeline y para no seguir contando "tiempo de espera". */
    private Instant canceladoEn;

    /** Auditoría (ronda 4, punto 28): qué admin asignó/canceló el pedido, para reclamos. */
    private String asignadoPorUsername;
    private String canceladoPorUsername;

    /**
     * De dónde salió el pedido (mejora 2026-09-24, para Métricas): WEB = lo cargó el cliente
     * desde "/pedir" o lo repitió desde el seguimiento; PANEL = lo cargó alguien con cuenta
     * en el panel. Null en los pedidos anteriores a la mejora (no se sabe).
     */
    @Column(length = 10)
    private String origenCarga;

    /** Usuario del panel que lo cargó (solo PANEL) — para ver cuántos cargó cada empleado. */
    private String creadoPorUsername;

    /**
     * Comisión ya descontada del crédito del cadete (ronda 7, modalidad PORCENTAJE) al
     * aceptar este viaje — null si el cadete es SEMANAL o si el viaje no llegó a
     * aceptarse. Se reembolsa (y se vuelve a poner en null) si el viaje se destraba sin
     * terminarse (Anular, Quitar, Reasignar, No se pudo entregar) — solo se cobra por lo
     * que efectivamente se concreta.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal comisionDescontada;

    /** Para no repetir el aviso de demora al cadete en cada corrida del scheduler (ver PedidoService.avisosDemora). */
    @Column(nullable = false)
    private boolean avisoRetiroEnviado = false;
    @Column(nullable = false)
    private boolean avisoFinalizacionEnviado = false;

    /** Para no repetir la alerta al admin (ver PedidoService.alertaInactividadSospechosa). */
    @Column(nullable = false)
    private boolean alertaInactividadEnviada = false;

    /** Foto de como el cadete recibe el pedido en el origen (distinta de la foto de entrega) — opcional. */
    @Column(length = 500)
    private String fotoRecepcionUrl;

    /** Uno de los dos al finalizar el viaje. */
    private String entregaReceptorNombre;
    @Column(length = 500)
    private String entregaFotoUrl;

    /** Firma digital del receptor al finalizar (spec: obligatoria u opcional según Configuración). */
    @Column(length = 500)
    private String firmaReceptorUrl;

    /**
     * Ubicación del celular del cadete al marcar "Retirado"/"Finalizado" (opcional, según
     * disponibilidad de GPS) — para que el admin pueda verificar en el mapa que coincide
     * con la dirección de origen/destino declarada por el cliente.
     */
    private Double retiroLat;
    private Double retiroLng;
    private Double entregaLat;
    private Double entregaLng;

    /** "CLIENTE" (canceló el cliente) u "OTRO" — solo tiene sentido si estado = CANCELADO. Para métricas. */
    private String motivoCancelacion;

    /**
     * Botón "No se pudo entregar" (ej. el cliente no atendió) — distinto de CANCELADO:
     * el pedido no se anula, el admin puede reintentarlo (vuelve a SIN_ASIGNAR) sin
     * cargarlo de cero.
     */
    @Column(length = 500)
    private String motivoNoEntrega;
    private Instant noEntregadoEn;

    /** true si se agotaron los reintentos de SMS (aceptación o entrega) sin poder avisarle al cliente — para el ícono del dashboard. */
    @Column(nullable = false)
    private boolean smsFallido = false;

    /** Marca manual del admin para destacarlo en el dashboard (mejora 93) — no cambia ninguna lógica de asignación. */
    @Column(nullable = false)
    private boolean prioritario = false;

    /** 1 a 5 — la carga el cliente desde la página pública de seguimiento, una sola vez, cuando el pedido ya está FINALIZADO. */
    private Integer calificacionEstrellas;
    @Column(length = 500)
    private String calificacionComentario;
    private Instant calificadoEn;

    /**
     * "Contrasena" del link de seguimiento publico enviado por SMS (diseno-tecnico.md
     * sección 8) — no requiere login del cliente, solo conocer este token.
     */
    @Column(nullable = false, unique = true, length = 36)
    private String tokenSeguimiento;

    /**
     * Paradas intermedias de un pedido con varias entregas en la misma vuelta (ronda 3,
     * punto 38) — el origen/destino de arriba siguen siendo la recogida y la última
     * entrega; esto son las paradas de en medio, en el orden en que se cargaron.
     */
    @OneToMany(mappedBy = "pedido")
    private List<PedidoParada> paradas = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getNumero() {
        return numero;
    }

    public void setNumero(Long numero) {
        this.numero = numero;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }

    public void setClienteTelefono(String clienteTelefono) {
        this.clienteTelefono = clienteTelefono;
    }

    @PrePersist
    @PreUpdate
    private void normalizarTelefono() {
        this.clienteTelefono = TelefonoUtils.normalizar(this.clienteTelefono);
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getOrigenDireccion() {
        return origenDireccion;
    }

    public void setOrigenDireccion(String origenDireccion) {
        this.origenDireccion = origenDireccion;
    }

    public Double getOrigenLat() {
        return origenLat;
    }

    public void setOrigenLat(Double origenLat) {
        this.origenLat = origenLat;
    }

    public Double getOrigenLng() {
        return origenLng;
    }

    public void setOrigenLng(Double origenLng) {
        this.origenLng = origenLng;
    }

    public String getDestinoDireccion() {
        return destinoDireccion;
    }

    public void setDestinoDireccion(String destinoDireccion) {
        this.destinoDireccion = destinoDireccion;
    }

    public Double getDestinoLat() {
        return destinoLat;
    }

    public void setDestinoLat(Double destinoLat) {
        this.destinoLat = destinoLat;
    }

    public Double getDestinoLng() {
        return destinoLng;
    }

    public void setDestinoLng(Double destinoLng) {
        this.destinoLng = destinoLng;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public BigDecimal getMontoDeclarado() {
        return montoDeclarado;
    }

    public void setMontoDeclarado(BigDecimal montoDeclarado) {
        this.montoDeclarado = montoDeclarado;
    }

    public boolean isLlevaValores() {
        return llevaValores;
    }

    public void setLlevaValores(boolean llevaValores) {
        this.llevaValores = llevaValores;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public boolean isProgramado() {
        return programado;
    }

    public void setProgramado(boolean programado) {
        this.programado = programado;
    }

    public Instant getFechaProgramada() {
        return fechaProgramada;
    }

    public void setFechaProgramada(Instant fechaProgramada) {
        this.fechaProgramada = fechaProgramada;
    }

    public Zona getZona() {
        return zona;
    }

    public void setZona(Zona zona) {
        this.zona = zona;
    }

    public boolean isRequiereMoto() {
        return requiereMoto;
    }

    public void setRequiereMoto(boolean requiereMoto) {
        this.requiereMoto = requiereMoto;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public Cadete getCadeteAsignado() {
        return cadeteAsignado;
    }

    public void setCadeteAsignado(Cadete cadeteAsignado) {
        this.cadeteAsignado = cadeteAsignado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getAsignadoEn() {
        return asignadoEn;
    }

    public void setAsignadoEn(Instant asignadoEn) {
        this.asignadoEn = asignadoEn;
    }

    public Instant getVistoEn() {
        return vistoEn;
    }

    public void setVistoEn(Instant vistoEn) {
        this.vistoEn = vistoEn;
    }

    public Instant getAceptadoEn() {
        return aceptadoEn;
    }

    public void setAceptadoEn(Instant aceptadoEn) {
        this.aceptadoEn = aceptadoEn;
    }

    public Instant getRetiradoEn() {
        return retiradoEn;
    }

    public void setRetiradoEn(Instant retiradoEn) {
        this.retiradoEn = retiradoEn;
    }

    public Instant getFinalizadoEn() {
        return finalizadoEn;
    }

    public void setFinalizadoEn(Instant finalizadoEn) {
        this.finalizadoEn = finalizadoEn;
    }

    public Instant getCanceladoEn() {
        return canceladoEn;
    }

    public void setCanceladoEn(Instant canceladoEn) {
        this.canceladoEn = canceladoEn;
    }

    public String getAsignadoPorUsername() {
        return asignadoPorUsername;
    }

    public void setAsignadoPorUsername(String asignadoPorUsername) {
        this.asignadoPorUsername = asignadoPorUsername;
    }

    public String getCanceladoPorUsername() {
        return canceladoPorUsername;
    }

    public void setCanceladoPorUsername(String canceladoPorUsername) {
        this.canceladoPorUsername = canceladoPorUsername;
    }

    public BigDecimal getComisionDescontada() {
        return comisionDescontada;
    }

    public void setComisionDescontada(BigDecimal comisionDescontada) {
        this.comisionDescontada = comisionDescontada;
    }

    public boolean isAvisoRetiroEnviado() {
        return avisoRetiroEnviado;
    }

    public void setAvisoRetiroEnviado(boolean avisoRetiroEnviado) {
        this.avisoRetiroEnviado = avisoRetiroEnviado;
    }

    public boolean isAvisoFinalizacionEnviado() {
        return avisoFinalizacionEnviado;
    }

    public void setAvisoFinalizacionEnviado(boolean avisoFinalizacionEnviado) {
        this.avisoFinalizacionEnviado = avisoFinalizacionEnviado;
    }

    public boolean isAlertaInactividadEnviada() {
        return alertaInactividadEnviada;
    }

    public void setAlertaInactividadEnviada(boolean alertaInactividadEnviada) {
        this.alertaInactividadEnviada = alertaInactividadEnviada;
    }

    public String getFotoRecepcionUrl() {
        return fotoRecepcionUrl;
    }

    public void setFotoRecepcionUrl(String fotoRecepcionUrl) {
        this.fotoRecepcionUrl = fotoRecepcionUrl;
    }

    public String getEntregaReceptorNombre() {
        return entregaReceptorNombre;
    }

    public void setEntregaReceptorNombre(String entregaReceptorNombre) {
        this.entregaReceptorNombre = entregaReceptorNombre;
    }

    public String getEntregaFotoUrl() {
        return entregaFotoUrl;
    }

    public void setEntregaFotoUrl(String entregaFotoUrl) {
        this.entregaFotoUrl = entregaFotoUrl;
    }

    public String getFirmaReceptorUrl() {
        return firmaReceptorUrl;
    }

    public void setFirmaReceptorUrl(String firmaReceptorUrl) {
        this.firmaReceptorUrl = firmaReceptorUrl;
    }

    public Double getRetiroLat() {
        return retiroLat;
    }

    public void setRetiroLat(Double retiroLat) {
        this.retiroLat = retiroLat;
    }

    public Double getRetiroLng() {
        return retiroLng;
    }

    public void setRetiroLng(Double retiroLng) {
        this.retiroLng = retiroLng;
    }

    public Double getEntregaLat() {
        return entregaLat;
    }

    public void setEntregaLat(Double entregaLat) {
        this.entregaLat = entregaLat;
    }

    public Double getEntregaLng() {
        return entregaLng;
    }

    public void setEntregaLng(Double entregaLng) {
        this.entregaLng = entregaLng;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public void setMotivoCancelacion(String motivoCancelacion) {
        this.motivoCancelacion = motivoCancelacion;
    }

    public String getMotivoNoEntrega() {
        return motivoNoEntrega;
    }

    public void setMotivoNoEntrega(String motivoNoEntrega) {
        this.motivoNoEntrega = motivoNoEntrega;
    }

    public Instant getNoEntregadoEn() {
        return noEntregadoEn;
    }

    public void setNoEntregadoEn(Instant noEntregadoEn) {
        this.noEntregadoEn = noEntregadoEn;
    }

    public String getTokenSeguimiento() {
        return tokenSeguimiento;
    }

    public void setTokenSeguimiento(String tokenSeguimiento) {
        this.tokenSeguimiento = tokenSeguimiento;
    }

    public List<PedidoParada> getParadas() {
        return paradas;
    }

    public void setParadas(List<PedidoParada> paradas) {
        this.paradas = paradas;
    }

    public boolean isSmsFallido() {
        return smsFallido;
    }

    public void setSmsFallido(boolean smsFallido) {
        this.smsFallido = smsFallido;
    }

    public boolean isPrioritario() {
        return prioritario;
    }

    public void setPrioritario(boolean prioritario) {
        this.prioritario = prioritario;
    }

    public Integer getCalificacionEstrellas() {
        return calificacionEstrellas;
    }

    public void setCalificacionEstrellas(Integer calificacionEstrellas) {
        this.calificacionEstrellas = calificacionEstrellas;
    }

    public String getCalificacionComentario() {
        return calificacionComentario;
    }

    public void setCalificacionComentario(String calificacionComentario) {
        this.calificacionComentario = calificacionComentario;
    }

    public Instant getCalificadoEn() {
        return calificadoEn;
    }

    public void setCalificadoEn(Instant calificadoEn) {
        this.calificadoEn = calificadoEn;
    }

    public String getOrigenPiso() {
        return origenPiso;
    }

    public void setOrigenPiso(String origenPiso) {
        this.origenPiso = origenPiso;
    }

    public String getOrigenDepto() {
        return origenDepto;
    }

    public void setOrigenDepto(String origenDepto) {
        this.origenDepto = origenDepto;
    }

    public String getOrigenObservaciones() {
        return origenObservaciones;
    }

    public void setOrigenObservaciones(String origenObservaciones) {
        this.origenObservaciones = origenObservaciones;
    }

    public String getDestinoPiso() {
        return destinoPiso;
    }

    public void setDestinoPiso(String destinoPiso) {
        this.destinoPiso = destinoPiso;
    }

    public String getDestinoDepto() {
        return destinoDepto;
    }

    public void setDestinoDepto(String destinoDepto) {
        this.destinoDepto = destinoDepto;
    }

    public String getDestinoObservaciones() {
        return destinoObservaciones;
    }

    public void setDestinoObservaciones(String destinoObservaciones) {
        this.destinoObservaciones = destinoObservaciones;
    }

    public String getOrigenCarga() {
        return origenCarga;
    }

    public void setOrigenCarga(String origenCarga) {
        this.origenCarga = origenCarga;
    }

    public String getCreadoPorUsername() {
        return creadoPorUsername;
    }

    public void setCreadoPorUsername(String creadoPorUsername) {
        this.creadoPorUsername = creadoPorUsername;
    }
}
