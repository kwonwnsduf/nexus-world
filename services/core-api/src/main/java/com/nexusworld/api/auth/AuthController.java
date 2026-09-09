package com.nexusworld.api.auth;

import com.nexusworld.application.auth.AuthenticationService;
import com.nexusworld.application.auth.IssuedTokenPair;
import com.nexusworld.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return response(authenticationService.login(request.username(), request.password()));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return response(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(authorization.substring("Bearer ".length()).trim(), request.refreshToken());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal CustomUserDetails principal) {
        List<String> roles = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .sorted()
                .toList();
        return new CurrentUserResponse(
                "v1",
                principal.getUserId().toString(),
                principal.getUsername(),
                roles);
    }

    private LoginResponse response(IssuedTokenPair tokens) {
        return new LoginResponse(
                "v1",
                tokens.accessToken(),
                "Bearer",
                tokens.accessExpiresInSeconds(),
                tokens.refreshToken(),
                tokens.refreshExpiresInSeconds());
    }
}
