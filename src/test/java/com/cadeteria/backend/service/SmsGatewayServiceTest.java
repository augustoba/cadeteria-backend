package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * El envío real ya reintenta solo (3 intentos con espera) antes de darse por vencido con un
 * pedido puntual — lo que falta es notar cuando eso le pasa a VARIOS pedidos seguidos, señal de
 * que el gateway completo está caído y no un número puntual sin señal. `registrarResultado` es
 * el punto donde ya se sabe si un envío (tras sus reintentos) salió bien o mal — se prueba
 * directo, sin tocar la parte de red/reintentos que ya existe.
 */
class SmsGatewayServiceTest {

    private WebSocketPublisher publisher;
    private SmsGatewayService service;

    @BeforeEach
    void setUp() {
        publisher = mock(WebSocketPublisher.class);
        service = new SmsGatewayService(mock(AppProperties.class), mock(PedidoRepository.class), publisher);
    }

    @Test
    void avisaTrasVariosFallosConsecutivosSeguidos() {
        service.registrarResultado(false);
        service.registrarResultado(false);
        service.registrarResultado(false);

        verify(publisher).publicarAlertaSmsGatewayCaido();
    }

    @Test
    void noAvisaSiTodaviaNoLlegaAlUmbral() {
        service.registrarResultado(false);
        service.registrarResultado(false);

        verify(publisher, never()).publicarAlertaSmsGatewayCaido();
    }

    @Test
    void unExitoCortaLaRachaYPermiteAvisarDeNuevoMasAdelante() {
        service.registrarResultado(false);
        service.registrarResultado(false);
        service.registrarResultado(true);
        service.registrarResultado(false);
        service.registrarResultado(false);
        verify(publisher, never()).publicarAlertaSmsGatewayCaido();

        service.registrarResultado(false);

        verify(publisher).publicarAlertaSmsGatewayCaido();
    }

    @Test
    void noRepiteElAvisoMientrasSigueCayendo() {
        for (int i = 0; i < 6; i++) {
            service.registrarResultado(false);
        }

        verify(publisher, times(1)).publicarAlertaSmsGatewayCaido();
    }
}
