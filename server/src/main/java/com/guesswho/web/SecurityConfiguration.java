package com.guesswho.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * What Spring Security does, and mostly does not do, for now.
 *
 * <p>Adding the dependency turns on a login form and locks every endpoint,
 * which would break the game the moment this merged. Nothing is protected yet
 * because nothing can log in yet; that arrives with tokens, and the endpoints
 * become authenticated one at a time rather than all at once by accident.</p>
 */
@Configuration
public class SecurityConfiguration {
    /**
     * Hashes passwords.
     *
     * <p>BCrypt at its default strength, which is deliberately slow: the point
     * is that guessing at scale costs more than it is worth.</p>
     *
     * @return the encoder used to store and check passwords
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Leaves the API open, and says so explicitly.
     *
     * <p>Only when there is a web application to secure. {@code HttpSecurity}
     * does not exist without one, and asking for it unconditionally stops the
     * context loading in tests that start the application with no web layer.</p>
     *
     * @param http the chain being configured
     * @return a chain that permits everything
     * @throws Exception if the chain cannot be built
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                //No browser and no session: the desktop client will send a
                //token. Cross-site request forgery needs a cookie to forge.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
