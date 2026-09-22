package cl.duoc.dsy1107.pedidosapi.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    // Muestra los claims del token ya validado y las authorities que derivó Spring Security.
    @GetMapping("/me")
    public Map<String, Object> me(JwtAuthenticationToken authentication) {
        var jwt = authentication.getToken();
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("usuario", authentication.getName());
        respuesta.put("aud", jwt.getAudience());
        respuesta.put("iss", jwt.getClaimAsString("iss"));
        respuesta.put("scp", jwt.getClaimAsString("scp"));
        respuesta.put("roles", jwt.getClaimAsStringList("roles") == null
                ? List.of()
                : jwt.getClaimAsStringList("roles"));
        respuesta.put("exp", jwt.getExpiresAt());
        respuesta.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        return respuesta;
    }
}
