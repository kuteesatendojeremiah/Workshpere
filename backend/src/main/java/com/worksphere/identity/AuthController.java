package com.worksphere.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/api/v1/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return UserProfileResponse.from(user);
    }

    @PostMapping("/api/v1/auth/verify-email")
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        userService.verifyEmail(request.token());
    }

    @PostMapping("/api/v1/auth/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/api/v1/auth/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/api/v1/auth/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/api/v1/auth/password-reset/confirm")
    public void confirmPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
    }
}
