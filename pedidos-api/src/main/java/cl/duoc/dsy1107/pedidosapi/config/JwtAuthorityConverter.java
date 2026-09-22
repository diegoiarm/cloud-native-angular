package cl.duoc.dsy1107.pedidosapi.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Convierte los claims del Access Token de Microsoft Entra ID en authorities:
 * - scp   (permisos delegados, ej. Pedidos.Read) -> SCOPE_Pedidos.Read
 * - roles (App Roles asignados, ej. Admin)        -> ROLE_Admin
 */
public class JwtAuthorityConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String SCOPES_CLAIM = "scp";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        valores(jwt.getClaims().get(SCOPES_CLAIM)).forEach(scope ->
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        valores(jwt.getClaims().get(ROLES_CLAIM)).forEach(rol ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rol)));
        return authorities;
    }

    // Entra entrega scp como texto separado por espacios y roles como arreglo.
    private static List<String> valores(Object claim) {
        if (claim == null) {
            return Collections.emptyList();
        }
        if (claim instanceof Collection<?> collection) {
            return collection.stream()
                .map(String::valueOf)
                .toList();
        }
        return Arrays.stream(String.valueOf(claim).split(" "))
            .filter(valor -> !valor.isBlank())
            .toList();
    }
}
