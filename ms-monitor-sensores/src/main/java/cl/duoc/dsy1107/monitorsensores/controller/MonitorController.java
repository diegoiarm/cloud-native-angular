package cl.duoc.dsy1107.monitorsensores.controller;

import cl.duoc.dsy1107.monitorsensores.dto.AlertaRecibida;
import cl.duoc.dsy1107.monitorsensores.dto.EstadoMonitorResponse;
import cl.duoc.dsy1107.monitorsensores.dto.LecturaRecibida;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaAlertas;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaLecturas;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de lo recibido por el monitor (documento 01 S13).
 *
 *   GET /api/monitor/lecturas  lecturas en memoria (más recientes primero)
 *   GET /api/monitor/alertas   alertas del segundo topic
 *   GET /api/monitor/estado    grupo y contadores
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MemoriaLecturas lecturas;
    private final MemoriaAlertas alertas;
    private final String grupo;
    private final String topicoLecturas;
    private final String topicoAlertas;

    public MonitorController(
            MemoriaLecturas lecturas,
            MemoriaAlertas alertas,
            @Value("${sensores.grupo:grupo-monitor-s13}") String grupo,
            @Value("${sensores.topico-lecturas:sensores-temperatura}") String topicoLecturas,
            @Value("${sensores.topico-alertas:sensores-alertas}") String topicoAlertas) {
        this.lecturas = lecturas;
        this.alertas = alertas;
        this.grupo = grupo;
        this.topicoLecturas = topicoLecturas;
        this.topicoAlertas = topicoAlertas;
    }

    @GetMapping("/lecturas")
    public List<LecturaRecibida> lecturas() {
        return lecturas.listar();
    }

    @GetMapping("/alertas")
    public List<AlertaRecibida> alertas() {
        return alertas.listar();
    }

    @GetMapping("/estado")
    public EstadoMonitorResponse estado() {
        return new EstadoMonitorResponse(
                "ms-monitor-sensores",
                grupo,
                topicoLecturas,
                topicoAlertas,
                lecturas.total(),
                alertas.total());
    }
}
