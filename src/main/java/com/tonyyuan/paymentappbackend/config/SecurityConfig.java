package com.tonyyuan.paymentappbackend.config;

import com.tonyyuan.paymentappbackend.security.ApiKeyFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configure the Spring Security filter chain.
     * - Allows H2 console frames
     * - Disables CSRF for H2 console and API routes
     * - Enables CORS
     * - Permits all API endpoints (actual auth handled in ApiKeyFilter)
     * - Registers ApiKeyFilter before UsernamePasswordAuthenticationFilter
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyFilter apiKeyFilter) throws Exception {
        http
                // Allow frames for H2 console
                .headers().frameOptions().sameOrigin()
                .and()
                // Disable CSRF for H2 console and API endpoints
                .csrf().ignoringAntMatchers("/h2-console/**", "/api/**").and()
                .cors().and()
                .authorizeRequests()
                // Allow access to H2 console without authentication
                .antMatchers("/h2-console/**").permitAll()
                // Allow API routes (authentication handled via ApiKeyFilter)
                .antMatchers("/api/**").permitAll()
                // Permit everything else for now
                .anyRequest().permitAll()
                .and()
                // Insert ApiKeyFilter before the standard authentication filter
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Completely bypass the Spring Security filter chain for selected paths.
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().antMatchers(
                "/h2-console/**", "/favicon.ico", "/error"
        );
    }

    /**
     * Configure CORS to allow requests from the frontend dev server.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
