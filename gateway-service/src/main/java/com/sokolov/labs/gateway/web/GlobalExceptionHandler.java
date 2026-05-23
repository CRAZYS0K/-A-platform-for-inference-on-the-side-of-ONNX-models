package com.sokolov.labs.gateway.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Catches authn failures from the upstream backend and either:
 * - sends 401 for AJAX/image requests (browser just skips the failed sub-resource),
 * - or redirects to /logout for HTML page navigations (clean re-login flow).
 *
 * Without this distinction a page with 50 images would issue 50 parallel /logout
 * redirects on token expiry, racing each other and confusing the user.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            HttpClientErrorException.Unauthorized.class,
            HttpClientErrorException.Forbidden.class,
            ClientAuthorizationException.class
    })
    public Object onAuthError(Exception ex, HttpServletRequest request,
                              HttpServletResponse response) {
        String accept = request.getHeader("Accept");
        boolean isHtmlNavigation = accept != null && accept.contains("text/html");
        log.warn("Auth error proxying {} ({}): {}", request.getRequestURI(),
                ex.getClass().getSimpleName(), ex.getMessage());
        if (isHtmlNavigation) {
            return "redirect:/logout";
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
