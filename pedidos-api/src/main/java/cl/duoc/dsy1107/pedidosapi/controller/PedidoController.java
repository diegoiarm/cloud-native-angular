package cl.duoc.dsy1107.pedidosapi.controller;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PedidoController {
    @GetMapping("/publico")
    public Map<String, String> publico() {
        return Map.of(
                "mensaje",
                "Endpoint público operativo");
    }

    @GetMapping("/pedidos")
    public Map<String, Object> pedidos(
            @AuthenticationPrincipal Jwt jwt) {
        String usuario = jwt.getClaimAsString(
                "preferred_username");
        if (usuario == null) {
            usuario = jwt.getSubject();
        }
        return Map.of(
                "mensaje",
                "Acceso autorizado",
                "usuario",
                usuario,
                "pedidos",
                List.of(
                        "PED-001",
                        "PED-002",
                        "PED-003"));
    }
}