package com.control360.infrastructure.security;

import com.control360.application.Store;
import com.control360.domain.Models.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(Store store) {
        return username -> {
            User user = store.user(username).orElseThrow(() -> new UsernameNotFoundException("Cuenta inexistente"));
            return org.springframework.security.core.userdetails.User.withUsername(user.username())
                    .password(user.passwordHash()).roles(user.role().name()).disabled(!user.active()).build();
        };
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository contextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityContextRepository repository, Store store) throws Exception {
        return http.securityContext(context -> context.securityContextRepository(repository))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> error(res, 401, "Inicia sesión para continuar"))
                        .accessDeniedHandler((req, res, ex) -> error(res, 403, "Acceso denegado o sesión expirada")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .addFilterBefore(new RefreshSession(store), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/usuarios/**", "/api/trabajadores/**",
                                "/api/reportes/resumen", "/api/inventario/**", "/api/asistencias/*/auditoria").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/productos/**", "/api/categorias/**", "/api/ubicaciones/**", "/api/turnos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/**", "/api/categorias/**", "/api/ubicaciones/**", "/api/turnos/**", "/api/asistencias/*/aprobar", "/api/asistencias/*/corregir").hasRole("ADMIN")
                        .requestMatchers("/api/clientes/**").hasAnyRole("ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole("ADMIN", "VENDEDOR")
                        .requestMatchers("/api/asistencias/**").hasAnyRole("ADMIN", "VENDEDOR", "ALMACEN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .build();
    }

    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    /** Los cambios de rol, credenciales o estado invalidan las sesiones anteriores. */
    private static final class RefreshSession extends OncePerRequestFilter {
        private final Store store;
        RefreshSession(Store store) { this.store = store; }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof SessionIdentity identity) {
                boolean valid = store.user(identity.username()).filter(u -> u.active()
                        && u.role() == identity.role() && u.id().equals(identity.id())
                        && u.passwordHash().equals(identity.passwordHash())).isPresent();
                if (!valid) {
                    SecurityContextHolder.clearContext();
                    if (request.getSession(false) != null) request.getSession(false).invalidate();
                }
            }
            chain.doFilter(request, response);
        }
    }

    record SessionIdentity(Long id, String username, com.control360.domain.Models.Role role, String passwordHash)
            implements java.security.Principal, java.io.Serializable {
        @Override public String getName() { return username; }
    }
}
