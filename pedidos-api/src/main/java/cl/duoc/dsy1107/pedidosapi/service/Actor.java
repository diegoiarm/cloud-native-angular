package cl.duoc.dsy1107.pedidosapi.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Usuario autenticado, tomado del Access Token validado.
 * id = claim "oid" (identificador estable en Entra ID); si no viene, se usa "sub".
 */
public record Actor(String id, String nombre, boolean veTodos) {

    public static Actor desde(JwtAuthenticationToken authentication) {
        var jwt = authentication.getToken();
        String id = jwt.getClaimAsString("oid") != null ? jwt.getClaimAsString("oid") : jwt.getSubject();
        String nombre = jwt.getClaimAsString("preferred_username") != null
                ? jwt.getClaimAsString("preferred_username")
                : id;
        boolean veTodos = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_Admin") || a.equals("ROLE_Operador"));
        return new Actor(id, nombre, veTodos);
    }
}
