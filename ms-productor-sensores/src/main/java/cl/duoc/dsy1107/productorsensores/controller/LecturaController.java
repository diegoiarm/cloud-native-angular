package cl.duoc.dsy1107.productorsensores.controller;

import cl.duoc.dsy1107.productorsensores.dto.EstadoProductorResponse;
import cl.duoc.dsy1107.productorsensores.dto.PublicacionResponse;
import cl.duoc.dsy1107.productorsensores.service.LecturaService;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del productor (documento 01 S13).
 *
 *   POST /api/lecturas        publica y responde 202 con partición y offset
 *   GET  /api/lecturas/estado estado del productor
 */
@RestController
@RequestMapping("/api/lecturas")
public class LecturaController {

    private final LecturaService service;

    public LecturaController(LecturaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublicacionResponse publicar(@Valid @RequestBody LecturaSensor lectura) {
        return service.publicar(lectura);
    }

    @GetMapping("/estado")
    public EstadoProductorResponse estado() {
        return service.estado();
    }
}
