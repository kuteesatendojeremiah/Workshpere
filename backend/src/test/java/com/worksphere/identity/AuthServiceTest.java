package com.worksphere.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginAttemptGuard loginAttemptGuard;

    @Mock
    private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("irrelevant-secret-for-this-test", 15, 30);
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                jwtService,
                new SecureTokenGenerator(),
                loginAttemptGuard,
                jwtProperties,
                auditService);
    }

    @Test
    void loginRejectsUnknownEmailWithoutCallingPasswordEncoder() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptGuard).onFailure("missing@example.com");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("real@example.com", "stored-hash", "Real User");
        when(userRepository.findByEmail("real@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("real@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptGuard).onFailure("real@example.com");
    }

    @Test
    void loginSucceedsAndIssuesTokenPair() {
        User user = new User("real@example.com", "stored-hash", "Real User");
        when(userRepository.findByEmail("real@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any())).thenReturn("signed-access-token");

        TokenPairResponse result = authService.login(new LoginRequest("real@example.com", "correct-password"));

        assertThat(result.accessToken()).isEqualTo("signed-access-token");
        assertThat(result.refreshToken()).isNotBlank();
        verify(loginAttemptGuard).onSuccess("real@example.com");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("not-a-real-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRejectsExpiredToken() {
        User user = new User("real@example.com", "hash", "Real User");
        RefreshToken expired = new RefreshToken(user, "irrelevant-hash", Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshingAnAlreadyRevokedTokenCascadesRevocationToAllOfThatUsersTokens() {
        User user = new User("victim@example.com", "hash", "Victim");
        RefreshToken reused = new RefreshToken(user, "irrelevant-hash", Instant.now().plusSeconds(3600));
        reused.revoke();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(reused));

        RefreshToken stillActive = new RefreshToken(user, "other-hash", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user)).thenReturn(List.of(stillActive));

        assertThatThrownBy(() -> authService.refresh("stolen-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(stillActive.isRevoked()).isTrue();
    }

    @Test
    void confirmPasswordResetRejectsUnknownToken() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmPasswordReset("not-a-real-token", "new-password"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void confirmPasswordResetChangesPasswordAndRevokesActiveTokens() {
        User user = new User("reset@example.com", "old-hash", "Reset User");
        PasswordResetToken token =
                new PasswordResetToken(user, "irrelevant-hash", Instant.now().plusSeconds(3600));
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        RefreshToken activeSession = new RefreshToken(user, "session-hash", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user)).thenReturn(List.of(activeSession));

        authService.confirmPasswordReset("raw-token", "new-password");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.isUsed()).isTrue();
        assertThat(activeSession.isRevoked()).isTrue();
    }
}
