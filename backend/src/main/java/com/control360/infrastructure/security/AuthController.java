package com.control360.infrastructure.security;

import com.control360.application.Store;
import com.control360.domain.Models.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.BadCredentialsException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final Store store;

    public AuthController(AuthenticationManager manager, SecurityContextRepository contexts, Store store) {
        this.manager = manager;
        this.contexts = contexts;
        this.store = store;
    }

    public record Login(@NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 72) String password) {}

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken csrf) {
        return Map.of("headerName", csrf.getHeaderName(), "token", csrf.getToken());
    }

    @PostMapping("/login")
    UserProfile login(@RequestBody @Valid Login data, HttpServletRequest request, HttpServletResponse response) {
        if (data.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        Authentication verified = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                data.username().strip(), data.password()));
        var user = store.user(verified.getName()).orElseThrow();
        var principal = new SecurityConfig.SessionIdentity(user.id(), user.username(), user.role(), user.passwordHash());
        request.getSession();
        request.changeSessionId();
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, verified.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return UserProfile.from(user);
    }

    @GetMapping("/me")
    UserProfile me(Principal principal) {
        return UserProfile.from(store.user(principal.getName()).orElseThrow());
    }
}
