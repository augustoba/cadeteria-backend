package com.cadeteria.backend.config;

import com.cadeteria.backend.model.Permiso;
import com.cadeteria.backend.model.Rol;
import com.cadeteria.backend.repository.PermisoRepository;
import com.cadeteria.backend.repository.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Catálogo de permisos + los dos roles base del sistema (mejora pedida por el dueño
 * 2026-09-16: roles configurables en vez del "DUENO"/"OPERADOR" fijo de antes). Corre
 * SIEMPRE (no depende de {@code app.demo.enabled}), como {@link DataSeeder}, porque esto
 * es catálogo real del sistema, no datos de prueba — y ANTES que {@link DataSeeder}
 * (@Order 0 vs 1) para que el admin inicial ya encuentre el rol "admin" creado.
 * <p>
 * Los permisos son fijos en código (protegen endpoints concretos de
 * {@link com.cadeteria.backend.config.SecurityConfig}), pero los DOS roles de acá son
 * solo el punto de partida: el dueño puede crear roles nuevos y editar qué permisos
 * tiene cada uno desde el panel (/roles) — "admin" y "operador" son
 * {@code esSistema=true} (no se pueden borrar), pero sus permisos también son editables
 * como cualquier otro rol.
 */
@Component
@Order(0)
public class RolSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RolSeeder.class);

    public static final String PERMISO_SISTEMA = "sistema";
    public static final String ROL_SUPERADMIN = "superadmin";

    /** id, nombre, categoría — agregar acá un permiso nuevo es lo único que hace falta para que se pueda asignar a un rol. */
    private static final List<String[]> CATALOGO = List.of(
            new String[]{"configuracion", "Configuración del sistema", "Datos sensibles"},
            new String[]{"metricas", "Métricas", "Datos sensibles"},
            new String[]{"pagos", "Pagos y liquidaciones de cadetes", "Datos sensibles"},
            new String[]{"seguridad", "Panel de seguridad (accesos, cerrar sesiones)", "Datos sensibles"},
            new String[]{"usuarios", "Administrar usuarios del panel", "Datos sensibles"},
            new String[]{"whatsapp", "Panel de WhatsApp (chips, mensajes)", "Datos sensibles"},
            new String[]{"roles", "Administrar roles y permisos", "Datos sensibles"},
            // 2026-09-29: un celular por cadete; habilitar uno nuevo desvincula el anterior.
            new String[]{"celular_cadete", "Habilitar nuevo celular de un cadete", "Cadetes"},
            // 2026-09-26: lo técnico que puede tirar el servicio (servidores, Cloudinary, borrado de lo
            // de Google, frecuencias) y borrar API keys. Solo el rol superadmin; un admin no puede
            // dárselo a nadie (RolService / AdminUsuarioService).
            new String[]{PERMISO_SISTEMA, "Sistema (configuración técnica y API keys)", "Solo superadmin"});


    private final PermisoRepository permisoRepo;
    private final RolRepository rolRepo;

    public RolSeeder(PermisoRepository permisoRepo, RolRepository rolRepo) {
        this.permisoRepo = permisoRepo;
        this.rolRepo = rolRepo;
    }

    @Override
    public void run(String... args) {
        for (String[] fila : CATALOGO) {
            if (permisoRepo.existsById(fila[0])) continue;
            Permiso p = new Permiso();
            p.setId(fila[0]);
            p.setNombre(fila[1]);
            p.setCategoria(fila[2]);
            permisoRepo.save(p);
        }

        if (!rolRepo.existsById("admin")) {
            Rol admin = new Rol();
            admin.setId("admin");
            admin.setNombre("Admin");
            admin.setEsSistema(true);
            // Todos menos "sistema": eso es solo del superadmin (el dueño de la plataforma).
            admin.setPermisos(new LinkedHashSet<>(permisoRepo.findAll().stream()
                    .filter(p -> !PERMISO_SISTEMA.equals(p.getId())).toList()));
            rolRepo.save(admin);
            log.info("Seed: rol 'admin' creado con todos los permisos menos '{}'.", PERMISO_SISTEMA);
        }

        if (!rolRepo.existsById(ROL_SUPERADMIN)) {
            Rol superadmin = new Rol();
            superadmin.setId(ROL_SUPERADMIN);
            superadmin.setNombre("Superadmin");
            superadmin.setEsSistema(true);
            superadmin.setPermisos(new LinkedHashSet<>(permisoRepo.findAll()));
            rolRepo.save(superadmin);
            log.info("Seed: rol '{}' creado con todos los permisos.", ROL_SUPERADMIN);
        } else {
            // Un permiso nuevo del catálogo lo recibe siempre el superadmin (los demás roles, a mano).
            Rol superadmin = rolRepo.findById(ROL_SUPERADMIN).orElseThrow();
            if (superadmin.getPermisos().size() < permisoRepo.count()) {
                superadmin.setPermisos(new LinkedHashSet<>(permisoRepo.findAll()));
                rolRepo.save(superadmin);
            }
        }

        if (!rolRepo.existsById("operador")) {
            Rol operador = new Rol();
            operador.setId("operador");
            operador.setNombre("Operador");
            operador.setEsSistema(true);
            operador.setPermisos(Set.of());
            rolRepo.save(operador);
            log.info("Seed: rol 'operador' creado sin permisos (día a día: pedidos, cadetes, chat, zonas, clientes, incidencias).");
        }
    }
}
