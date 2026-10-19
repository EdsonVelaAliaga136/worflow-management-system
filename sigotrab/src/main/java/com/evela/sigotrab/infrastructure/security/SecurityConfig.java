package com.evela.sigotrab.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SigAuthenticationFailureHandler failureHandler) throws Exception{
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/webjars/**", "/login", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/offers/new", "/offers/*/edit", "/offers/*/approve", "/offers/*/reject")
                        .hasRole("REGISTRADOR")
                        .requestMatchers("/api/offers/*/acquire", "/offers/*/cancel", "/offers/*/publish")
                        .hasRole("PUBLICADOR")
                        .requestMatchers("/reports/**").hasAnyRole("ADMIN", "REGISTRADOR")
                        .requestMatchers("/audit/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login")
                        .failureHandler(failureHandler)
                        .defaultSuccessUrl("/dashboard", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .csrf(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
