package com.indrard.dbmcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

/**
 * Configuration for internationalization (i18n) support.
 * Supports language selection via:
 * 1. Accept-Language HTTP header (default)
 * 2. ?lang=es query parameter (optional override)
 * Default language can be configured via app.default.language property
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    @Value("${app.default.language:en}")
    private String defaultLanguage;

    /**
     * Configure message source for internationalization.
     * Messages are stored in messages_*.properties files.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(getDefaultLocale());
        return messageSource;
    }

    /**
     * Resolve locale from Accept-Language header.
     * Defaults to configured language if no header is present.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(getDefaultLocale());
        return resolver;
    }

    /**
     * Get default locale from configuration
     */
    private Locale getDefaultLocale() {
        return Locale.forLanguageTag(defaultLanguage);
    }

    /**
     * Allow changing locale via ?lang=es query parameter.
     * Example: /api/chat?lang=es
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
