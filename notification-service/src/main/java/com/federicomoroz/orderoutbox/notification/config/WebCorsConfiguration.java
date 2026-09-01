package com.federicomoroz.orderoutbox.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the React dashboard, which is served from its own origin ({@code :8008}) and reads
 * {@code GET /api/notifications} directly from the browser — the whole point of the dashboard's
 * third column is that the data comes from <em>this</em> service's database, not from
 * order-service proxying it.
 *
 * <p>Read-only surface, so only {@code GET} is allowed. Lives in {@code config/} for the same
 * reason as its order-service twin: HTTP transport policy never leaks into
 * {@code domain}/{@code application}.
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
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }
}
