package cl.duoc.dsy1107.pedidosapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Fallo controlado del Laboratorio 1 (documento 04, Actividad B).
 *
 * Cada bandera marca la cola que debe fallar; la cola marcada realiza el intento
 * inicial y dos reintentos antes de terminar en su DLQ. Las banderas en false
 * comprueban el desacoplamiento: esas colas procesan su mensaje con normalidad.
 */
public record LabAceptarPedidoRequest(
        @NotNull(message = "pedidoId es obligatorio")
        @Positive(message = "pedidoId debe ser positivo")
        Long pedidoId,
        @NotBlank(message = "clienteEmail es obligatorio")
        @Email(message = "clienteEmail debe ser un correo valido")
        String clienteEmail,
        boolean falloNotificaciones,
        boolean falloCocina,
        boolean falloDocumentos) {
}
