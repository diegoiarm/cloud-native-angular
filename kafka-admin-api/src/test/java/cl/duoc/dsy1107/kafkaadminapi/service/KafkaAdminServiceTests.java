package cl.duoc.dsy1107.kafkaadminapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.dsy1107.kafkaadminapi.dto.LagResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoRequest;
import cl.duoc.dsy1107.kafkaadminapi.exception.AdminKafkaException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Reglas del administrador sin Spring ni broker. */
class KafkaAdminServiceTests {

    private static final String PROTEGIDA =
            "^sensores-(temperatura|alertas)(\\.DLT)?$|^__consumer_offsets$";

    private final KafkaAdminPort port = mock(KafkaAdminPort.class);
    private final KafkaAdminService service =
            new KafkaAdminService(port, PROTEGIDA, 3, 1, 86400000L);

    @Test
    void crearTopicoSinValoresUsaDefaults() {
        service.crearTopico(new TopicoRequest("lab.auditoria", null, null, null));

        verify(port).crearTopico("lab.auditoria", 3, 1, 86400000L);
    }

    @Test
    void crearTopicoConValoresRespetaLaSolicitud() {
        service.crearTopico(new TopicoRequest("lab.auditoria", 6, 3, 3600000L));

        verify(port).crearTopico("lab.auditoria", 6, 3, 3600000L);
    }

    @Test
    void eliminarTopicoProtegidoDevuelve409() {
        assertThatThrownBy(() -> service.eliminarTopico("sensores-temperatura"))
                .isInstanceOf(AdminKafkaException.class)
                .extracting(e -> ((AdminKafkaException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(port, never()).eliminarTopico("sensores-temperatura");
    }

    @Test
    void eliminarDltProtegidoDevuelve409() {
        assertThatThrownBy(() -> service.eliminarTopico("sensores-alertas.DLT"))
                .isInstanceOf(AdminKafkaException.class)
                .extracting(e -> ((AdminKafkaException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void eliminarTopicoNoProtegidoDelegaEnElPuerto() {
        service.eliminarTopico("lab.auditoria");

        verify(port).eliminarTopico("lab.auditoria");
    }

    @Test
    void nombreInvalidoDevuelve400() {
        assertThatThrownBy(() -> service.describirTopico("topic con espacios"))
                .isInstanceOf(AdminKafkaException.class)
                .extracting(e -> ((AdminKafkaException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void particionesCeroDevuelve400() {
        assertThatThrownBy(() ->
                service.crearTopico(new TopicoRequest("lab.auditoria", 0, null, null)))
                .isInstanceOf(AdminKafkaException.class)
                .extracting(e -> ((AdminKafkaException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void lagDelegaEnElPuerto() {
        LagResponse respuesta = new LagResponse("grupo-monitor-s13", 0L, List.of());
        when(port.lag("grupo-monitor-s13")).thenReturn(respuesta);

        assertThat(service.lag("grupo-monitor-s13")).isSameAs(respuesta);
    }
}
