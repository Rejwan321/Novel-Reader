package com.reader.Novel.Reader.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class securityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // Disable default CSRF to avoid breaking AJAX posts (since we use custom session checks)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Permit all requests, leaving authorization to UserStatusInterceptor and custom logic
                )
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())) // Allow h2-console in iframe from same origin
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.springframework.security.provisioning.InMemoryUserDetailsManager userDetailsService() {
        return new org.springframework.security.provisioning.InMemoryUserDetailsManager();
    }
}
