package com.sokolov.labs.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<org.springframework.security.core.GrantedAuthority> scopes = scopesConverter.convert(jwt);
            Collection<org.springframework.security.core.GrantedAuthority> realmRoles = extractRealmRoles(jwt);
            return Stream.concat(
                    scopes == null ? Stream.empty() : scopes.stream(),
                    realmRoles.stream()
            ).collect(Collectors.toList());
        });
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @SuppressWarnings("unchecked")
    private Collection<org.springframework.security.core.GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Collections.emptyList();
        }
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> rolesCollection)) {
            return Collections.emptyList();
        }
        return rolesCollection.stream()
                .map(Object::toString)
                .map(role -> "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
