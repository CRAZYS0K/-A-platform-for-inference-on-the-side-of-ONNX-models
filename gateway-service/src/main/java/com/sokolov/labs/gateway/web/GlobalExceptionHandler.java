package com.sokolov.labs.gateway.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Catches authn failures from the upstream backend (expired token, refresh
 * grant rejected, etc.) and bounces the user through {@code /logout} so they
 * re-authenticate cleanly instead of hitting Spring's Whitelabel error page.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            HttpClientErrorException.Unauthorized.class,
            HttpClientErrorException.Forbidden.class,
            ClientAuthorizationException.class
    })
    public RedirectView onAuthError(Exception ex, HttpServletRequest request,
                                    HttpServletResponse response) {
        log.warn("Auth error proxying {} ({}): {}", request.getRequestURI(),
                ex.getClass().getSimpleName(), ex.getMessage());
        return new RedirectView("/logout");
    }
}
