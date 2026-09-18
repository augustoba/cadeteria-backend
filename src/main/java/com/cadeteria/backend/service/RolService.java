package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.RolDtos.PermisoResponse;
import com.cadeteria.backend.dto.RolDtos.RolRequest;
import com.cadeteria.backend.dto.RolDtos.RolResponse;
import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.model.Permiso;
import com.cadeteria.backend.model.Rol;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.PermisoRepository;
import com.cadeteria.backend.repository.RolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Roles con permisos configurables (mejora pedida por el dueño 2026-09-16, reemplaza el
 * "DUENO"/"OPERADOR" fijo de antes). {@link Admin#getRol()} guarda el id de un {@link Rol}
 * — {@link #permisosEfectivos} es el único punto que sabe traducir eso a un conjunto de
 * permisos, incluida la compatibilidad con los valores viejos "DUENO"/"OPERADOR" que ya
 * puedan existir en la base (no hace falta migrar datos: "DUENO" se trata igual que
 * "admin", cualquier otra cosa (incluido "OPERADOR" o null) igual que "operador").
 */
@Service
@Transactional
public class RolService {

    private final RolRepository rolRepo;
    private final PermisoRepository permisoRepo;
    private final AdminRepository adminRepo;

    public RolService(RolRepository rolRepo, PermisoRepository permisoRepo, AdminRepository adminRepo) {
        this.rolRepo = rolRepo;
        this.permisoRepo = permisoRepo;
        this.adminRepo = adminRepo;
    }

    @Transactional(readOnly = true)
    public Set<String> permisosEfectivos(String rolId) {
        String idResuelto = "DUENO".equals(rolId) ? "admin" : rolId;
        if (idResuelto == null) idResuelto = "operador";
        Rol rol = rolRepo.findById(idResuelto).orElse(null);
        if (rol == null) return Set.of();
        return rol.getPermisos().stream().map(Permiso::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public boolean tienePermiso(Admin admin, String permisoId) {
        return permisosEfectivos(admin.getRol()).contains(permisoId);
    }

    /**
     * True si `admin` es, ÉL SOLO, el único admin habilitado que tiene ese permiso — usado
     * para no dejar el sistema sin nadie que pueda arreglar accesos (mismo criterio que el
     * viejo "último DUEÑO habilitado", generalizado a cualquier permiso).
     */
    @Transactional(readOnly = true)
    public boolean esUltimoConPermiso(Admin admin, String permisoId) {
        if (!tienePermiso(admin, permisoId)) return false;
        return adminRepo.findAll().stream()
                .filter(a -> !a.getId().equals(admin.getId()))
                .noneMatch(a -> a.isEnabled() && tienePermiso(a, permisoId));
    }

    @Transactional(readOnly = true)
    public List<PermisoResponse> catalogoPermisos() {
        return permisoRepo.findAllByOrderByCategoriaAscNombreAsc().stream()
                .map(p -> new PermisoResponse(p.getId(), p.getNombre(), p.getCategoria()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RolResponse> listar() {
        return rolRepo.findAllByOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    public RolResponse crear(RolRequest req) {
        String id = normalizarId(req.nombre());
        if (rolRepo.existsById(id)) {
            throw new BadRequestException("Ya existe un rol con ese nombre.");
        }
        Rol rol = new Rol();
        rol.setId(id);
        rol.setNombre(req.nombre().trim());
        rol.setEsSistema(false);
        rol.setPermisos(resolverPermisos(req.permisos()));
        rolRepo.save(rol);
        return toResponse(rol);
    }

    public RolResponse actualizar(String id, RolRequest req) {
        Rol rol = get(id);
        // El id (y por lo tanto a quién apunta Admin.rol) nunca cambia, solo nombre/permisos — evita romper asignaciones existentes.
        rol.setNombre(req.nombre().trim());
        Set<Permiso> nuevos = resolverPermisos(req.permisos());
        if (rol.tienePermiso("roles") && !nuevos.stream().anyMatch(p -> "roles".equals(p.getId()))) {
            validarNoUltimoConPermisoRoles(rol.getId());
        }
        rol.setPermisos(nuevos);
        rolRepo.save(rol);
        return toResponse(rol);
    }

    public void eliminar(String id) {
        Rol rol = get(id);
        if (rol.isEsSistema()) {
            throw new BadRequestException("Los roles del sistema (\"admin\", \"operador\") no se pueden borrar.");
        }
        boolean enUso = adminRepo.findAll().stream().anyMatch(a -> id.equals(a.getRol()));
        if (enUso) {
            throw new BadRequestException("No se puede borrar un rol que todavía tiene usuarios asignados.");
        }
        rolRepo.delete(rol);
    }

    /** No dejar el sistema sin ningún admin habilitado capaz de arreglar permisos (mismo criterio que el viejo "último DUEÑO"). */
    private void validarNoUltimoConPermisoRoles(String rolIdExcluidoDePermiso) {
        boolean quedaAlguno = adminRepo.findAll().stream()
                .filter(Admin::isEnabled)
                .anyMatch(a -> !rolIdExcluidoDePermiso.equals(a.getRol()) && tienePermiso(a, "roles"));
        if (!quedaAlguno) {
            throw new BadRequestException("No podés sacarle el permiso \"roles\" a este rol — quedaría nadie con acceso para administrar roles.");
        }
    }

    private Set<Permiso> resolverPermisos(List<String> ids) {
        if (ids == null || ids.isEmpty()) return new LinkedHashSet<>();
        List<Permiso> permisos = permisoRepo.findAllById(ids);
        if (permisos.size() != new LinkedHashSet<>(ids).size()) {
            throw new BadRequestException("Alguno de los permisos indicados no existe.");
        }
        return new LinkedHashSet<>(permisos);
    }

    private String normalizarId(String nombre) {
        String base = nombre.trim().toLowerCase()
                .replaceAll("[áàä]", "a").replaceAll("[éèë]", "e").replaceAll("[íìï]", "i")
                .replaceAll("[óòö]", "o").replaceAll("[úùü]", "u")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) throw new BadRequestException("Nombre de rol inválido.");
        return base;
    }

    private Rol get(String id) {
        return rolRepo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Rol", id));
    }

    private RolResponse toResponse(Rol r) {
        List<PermisoResponse> permisos = r.getPermisos().stream()
                .map(p -> new PermisoResponse(p.getId(), p.getNombre(), p.getCategoria()))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
        return new RolResponse(r.getId(), r.getNombre(), r.isEsSistema(), permisos);
    }
}
