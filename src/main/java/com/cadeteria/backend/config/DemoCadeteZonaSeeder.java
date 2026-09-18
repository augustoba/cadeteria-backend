package com.cadeteria.backend.config;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.EstadoCadeteRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.cadeteria.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Prerrequisito de {@link DemoPedidoSeeder}: una zona y (al menos) un cadete dados de
 * alta. En una base recién creada no existe ninguno de los dos todavía (no se los
 * inventaba antes), asi que el dashboard/Métricas quedaban vacíos aunque el backend
 * arrancara bien — nada tenía a quién asignarle los pedidos de ejemplo. Se siembra UNA
 * sola vez (a diferencia de DemoPedidoSeeder, que se recrea en cada arranque): apenas el
 * admin carga una zona o un cadete real, este seeder deja de tocar nada.
 */
@Component
@Order(2)
public class DemoCadeteZonaSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCadeteZonaSeeder.class);

    /** Contraseña de las cuentas de cadete que siembra este seeder — solo para la demo, cambiarla en producción. */
    private static final String DEMO_PASSWORD = "cadete123";

    private final ZonaRepository zonaRepo;
    private final CadeteRepository cadeteRepo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final EstadoCadeteRepository estadoCadeteRepo;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DemoCadeteZonaSeeder(ZonaRepository zonaRepo, CadeteRepository cadeteRepo,
                                 TipoVehiculoRepository tipoVehiculoRepo, EstadoCadeteRepository estadoCadeteRepo,
                                 PasswordEncoder passwordEncoder, AppProperties props) {
        this.zonaRepo = zonaRepo;
        this.cadeteRepo = cadeteRepo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.estadoCadeteRepo = estadoCadeteRepo;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.getDemo().isEnabled()) return;
        seedZona();
        seedCadetes();
    }

    private void seedZona() {
        if (zonaRepo.count() > 0) return;
        Zona zona = new Zona();
        zona.setId("demo-zona-centro");
        zona.setNombre("Centro");
        zona.setCentroLat(-26.8241);
        zona.setCentroLng(-65.2226);
        zona.setRadioM(8000);
        zona.setTarifaSugerida(new java.math.BigDecimal("700.00"));
        zonaRepo.save(zona);
        log.info("Demo: zona '{}' sembrada.", zona.getNombre());
    }

    private void seedCadetes() {
        if (cadeteRepo.count() > 0) return;
        TipoVehiculo moto = tipoVehiculoRepo.findById("MOTO")
                .orElseThrow(() -> new IllegalStateException("Falta seedear tipo_vehiculo.MOTO"));
        TipoVehiculo bici = tipoVehiculoRepo.findById("BICI")
                .orElseThrow(() -> new IllegalStateException("Falta seedear tipo_vehiculo.BICI"));

        crearCadete("jperez", "Juan", "Pérez", "30111222", "3813010001", "jperez@demo.cadeteria.local",
                moto, "Negra", "A123BCD", "Honda", "Wave", 2021, "LIBRE");
        crearCadete("mgomez", "Marcos", "Gómez", "30222333", "3813020002", "mgomez@demo.cadeteria.local",
                bici, "Roja", null, null, null, null, "DESCONECTADO");
        log.info("Demo: 2 cadetes sembrados (usuario/contraseña: jperez o mgomez / '{}').", DEMO_PASSWORD);
    }

    private void crearCadete(String username, String nombre, String apellido, String dni, String telefono,
                              String email, TipoVehiculo tipoVehiculo, String color, String patente,
                              String marca, String modelo, Integer anio, String estadoId) {
        Cadete c = new Cadete();
        c.setId(UUID.randomUUID().toString());
        c.setUsername(username);
        c.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        c.setNombre(nombre);
        c.setApellido(apellido);
        c.setDni(dni);
        c.setTelefono(telefono);
        c.setEmail(email);
        c.setTipoVehiculo(tipoVehiculo);
        c.setVehiculoColor(color);
        c.setVehiculoPatente(patente);
        c.setVehiculoMarca(marca);
        c.setVehiculoModelo(modelo);
        c.setVehiculoAnio(anio);
        c.setActivo(true);
        c.setEstado(estadoCadete(estadoId));
        cadeteRepo.save(c);
    }

    private EstadoCadete estadoCadete(String id) {
        return estadoCadeteRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("Falta seedear estado_cadete." + id));
    }
}
