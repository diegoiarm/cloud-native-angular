package cl.duoc.dsy1107.pedidosapi.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // true solo en el perfil local: la ruta de laboratorio se llama con Postman sin token.
    @Value("${pedidos360.lab.sin-token:false}")
    private boolean labSinToken;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // API REST basada en Bearer Token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**")
                        .permitAll()
                        .requestMatchers(
                                "/api/publico")
                        .permitAll()
                        // Laboratorio 1 (documento 04): fallo controlado para demostrar la DLQ.
                        // LabController solo existe con pedidos360.lab.habilitado=true.
                        // En local (pedidos360.lab.sin-token=true) se invoca sin token;
                        // en la nube exige un token con rol Admin.
                        .requestMatchers(
                                "/api/lab/**")
                        .access(labSinToken
                                ? (autenticacion, contexto) -> new AuthorizationDecision(true)
                                : AuthorityAuthorizationManager.hasRole("Admin"))
                        // Toda la API de pedidos exige el scope delegado;
                        // los roles por operación se validan con @PreAuthorize en el controller.
                        .requestMatchers(
                                "/api/orders/**",
                                "/api/pedidos")
                        .hasAuthority(
                                "SCOPE_Pedidos.Read")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtAuthorityConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    // Orígenes permitidos: en la nube se define CORS_ORIGINS con el dominio del frontend.
    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String origenesPermitidos;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                Arrays.stream(origenesPermitidos.split(","))
                        .map(String::trim)
                        .filter(origen -> !origen.isEmpty())
                        .toList());
        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"));
        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(
                "/**",
                config);
        return source;
    }
}