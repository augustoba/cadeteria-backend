package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/**
 * Rol de admin con permisos configurables (mejora pedida por el dueño 2026-09-16, en
 * reemplazo del "DUENO"/"OPERADOR" fijo de antes). {@code id} es un slug legible
 * (ej. "admin", "operador", "operador-zona-sur" para uno custom) — {@link Admin#getRol()}
 * guarda este id. "admin" y "operador" son {@code esSistema=true}: no se pueden borrar ni
 * renombrar el id (para no dejar el sistema sin un rol base), pero sus permisos sí se
 * pueden editar.
 */
@Entity
@Table(name = "rol")
public class Rol {

    @Id
    private String id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private boolean esSistema = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new HashSet<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isEsSistema() {
        return esSistema;
    }

    public void setEsSistema(boolean esSistema) {
        this.esSistema = esSistema;
    }

    public Set<Permiso> getPermisos() {
        return permisos;
    }

    public void setPermisos(Set<Permiso> permisos) {
        this.permisos = permisos;
    }

    public boolean tienePermiso(String permisoId) {
        return permisos.stream().anyMatch(p -> p.getId().equals(permisoId));
    }
}
