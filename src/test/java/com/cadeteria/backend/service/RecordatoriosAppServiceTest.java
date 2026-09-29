package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RecordatoriosAppServiceTest {

    @Test
    void sinTocarValenLosTextosDeSiempre() {
        var r = RecordatoriosAppService.desde(Map.of());

        assertThat(r.activo()).isTrue();
        assertThat(r.titulo()).isEqualTo("Antes de arrancar");
        assertThat(r.textos()).hasSize(3).first().asString().startsWith("Llevá toda la documentación");
    }

    @Test
    void editadosSeUsanEnOrdenYLosVaciosNoSalen() {
        var r = RecordatoriosAppService.desde(Map.of(
                "recordatorios_entrar_titulo", "  Hoy  ",
                "recordatorio_entrar_1", "Casco",
                "recordatorio_entrar_2", "",
                "recordatorio_entrar_4", " Chaleco ",
                "recordatorios_entrar_activo", "false"));

        assertThat(r.activo()).isFalse();
        assertThat(r.titulo()).isEqualTo("Hoy");
        assertThat(r.textos()).isEqualTo(List.of("Casco", "Chaleco"));
    }

    @Test
    void todosVaciosEsQueNoHayCartel() {
        var r = RecordatoriosAppService.desde(Map.of("recordatorio_entrar_1", "", "recordatorio_entrar_2", " "));

        assertThat(r.textos()).isEmpty();
    }

    @Test
    void validaLargosYCantidadDeRenglones() {
        assertThatNoException().isThrownBy(() -> RecordatoriosAppService.validar("recordatorio_entrar_6", "x".repeat(150)));
        assertThatNoException().isThrownBy(() -> RecordatoriosAppService.validar("frecuencia_ubicacion_seg", "x".repeat(400)));

        assertThatThrownBy(() -> RecordatoriosAppService.validar("recordatorio_entrar_1", "x".repeat(151)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("150");
        assertThatThrownBy(() -> RecordatoriosAppService.validar("recordatorio_entrar_7", "hola"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("6 renglones");
        assertThatThrownBy(() -> RecordatoriosAppService.validar("recordatorio_entrar_x", "hola"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> RecordatoriosAppService.validar("recordatorios_entrar_titulo", "x".repeat(61)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("60");
    }
}
