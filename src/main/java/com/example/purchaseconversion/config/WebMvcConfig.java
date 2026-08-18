package com.example.purchaseconversion.config;

import com.example.purchaseconversion.api.interceptor.EligibilityAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * Registers HTTP-layer interceptors. Kept separate from {@link WexConfig} — that
 * class wires the pure-POJO application-layer beans (ArchUnit forbids Spring
 * annotations in {@code application}); this one is purely a web-layer concern.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final EligibilityAuditInterceptor eligibilityAuditInterceptor;

    public WebMvcConfig(EligibilityAuditInterceptor eligibilityAuditInterceptor) {
        this.eligibilityAuditInterceptor =
                Objects.requireNonNull(eligibilityAuditInterceptor, "eligibilityAuditInterceptor must not be null");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(eligibilityAuditInterceptor)
                .addPathPatterns("/api/v1/benefits/**");
    }
}
