package com.sokolov.labs.gateway.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final List<String> PROTECTED_PATTERNS = List.of(
            "/models", "/models/**",
            "/datasets", "/datasets/**",
            "/tasks", "/tasks/**",
            "/notifications", "/notifications/**");
    /**
     * Exempt patterns — these endpoints are gated by authn but are called many times
     * from a single page load (e.g. an inference gallery has dozens of images).
     */
    private static final List<String> EXEMPT_PATTERNS = List.of(
            "/tasks/*/images/**",
            "/tasks/*/results.json",
            "/tasks/*/labels.zip");

    private final ProxyManager<String> proxyManager;
    private final RateLimitProperties properties;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitFilter(ProxyManager<String> proxyManager, RateLimitProperties properties) {
        this.proxyManager = proxyManager;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!properties.isEnabled() || !isProtected(request)) {
            chain.doFilter(request, response);
            return;
        }
        String key = resolveKey(request);
        BucketProxy bucket = proxyManager.builder().build(key, configurationSupplier());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"retryAfter\":"
                    + retryAfterSeconds + "}");
            log.warn("Rate limit hit for key={} path={}", key, request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    private Supplier<BucketConfiguration> configurationSupplier() {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(properties.getCapacity())
                .refillIntervally(properties.getRefillTokens(),
                        Duration.ofNanos(properties.getRefillPeriod().toNanos()))
                .build();
        return () -> BucketConfiguration.builder().addLimit(bandwidth).build();
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : EXEMPT_PATTERNS) {
            if (matcher.match(pattern, path)) {
                return false;
            }
        }
        for (String pattern : PROTECTED_PATTERNS) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return "ip:" + ip;
    }
}
