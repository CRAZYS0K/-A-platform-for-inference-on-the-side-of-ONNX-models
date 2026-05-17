package com.sokolov.labs.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class KeycloakLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    private final String endSessionEndpoint;
    private final String postLogoutRedirectUri;

    public KeycloakLogoutSuccessHandler(
            @Value("${KEYCLOAK_END_SESSION_URI:http://localhost/auth/realms/onnxi/protocol/openid-connect/logout}")
            String endSessionEndpoint,
            @Value("${app.base-url:https://localhost}/")
            String postLogoutRedirectUri) {
        this.endSessionEndpoint = endSessionEndpoint;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endSessionEndpoint)
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri);

        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String idToken = oidcUser.getIdToken().getTokenValue();
            builder.queryParam("id_token_hint", idToken);
        }

        getRedirectStrategy().sendRedirect(request, response, builder.toUriString());
    }
}
