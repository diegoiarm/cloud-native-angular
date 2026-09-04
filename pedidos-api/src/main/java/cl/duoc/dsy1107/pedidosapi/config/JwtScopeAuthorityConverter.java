package cl.duoc.dsy1107.pedidosapi.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JwtScopeAuthorityConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String SCOPES_CLAIM = "scp";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object scopes = jwt.getClaims().get(SCOPES_CLAIM);
        if (scopes == null) {
            return Collections.emptyList();
        }

        List<String> scopeList;
        if (scopes instanceof Collection<?> collection) {
            scopeList = collection.stream()
                .map(String::valueOf)
                .toList();
        } else {
            scopeList = Arrays.stream(String.valueOf(scopes).split(" "))
                .toList();
        }

        return scopeList.stream()
            .map(scope -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + scope))
            .toList();
    }
}