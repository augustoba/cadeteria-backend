package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Chat interno 1 a 1 entre el admin y un cadete (spec 5.5) — no hace falta modelar salas. */
@Entity
@Table(name = "chat_mensaje")
public class ChatMensaje {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_id")
    private AutorMensaje autor;

    @Column(length = 2000)
    private String texto;

    /** Nota de voz (grabada y subida a Cloudinary desde la app) — alternativa a texto, no ambas vacías. */
    @Column(length = 500)
    private String audioUrl;

    /** Foto adjunta (mejora 88) — igual que audioUrl, ya subida a Cloudinary antes de llegar acá. */
    @Column(length = 500)
    private String imagenUrl;

    @Column(nullable = false)
    private Instant enviadoEn = Instant.now();

    @Column(nullable = false)
    private boolean leido = false;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public AutorMensaje getAutor() {
        return autor;
    }

    public void setAutor(AutorMensaje autor) {
        this.autor = autor;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(Instant enviadoEn) {
        this.enviadoEn = enviadoEn;
    }

    public boolean isLeido() {
        return leido;
    }

    public void setLeido(boolean leido) {
        this.leido = leido;
    }
}
