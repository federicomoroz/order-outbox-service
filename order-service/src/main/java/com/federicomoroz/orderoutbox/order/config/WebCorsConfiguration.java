package com.federicomoroz.orderoutbox.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the React dashboard, which is served from its own origin ({@code :8008}) and calls
 * this service's API directly from the browser.
 *
 * <p>Lives in {@code config/} — the adapter/infrastructure side of the hexagon — because it is
 * pure HTTP transport policy: {@code domain/} and {@code application/} must not know that the
 * outside world speaks HTTP at all, let alone which browser origins are allowed.
 *
 * <p>The origin list is configuration, never a hardcoded {@code localhost}: override with
 * {@code WEB_CORS_ALLOWED_ORIGINS} when the dashboard is served from somewhere else.
 */
@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfiguration(@Value("${web.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins.clone();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }
}
