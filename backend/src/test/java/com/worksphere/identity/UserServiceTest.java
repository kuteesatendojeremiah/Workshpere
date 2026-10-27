package com.worksphere.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private com.worksphere.common.outbox.OutboxService outboxService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, tokenRepository, passwordEncoder, new SecureTokenGenerator(), auditService, outboxService);
    }

    @Test
    void registerHashesPasswordAndIssuesVerificationToken() {
        RegisterRequest request = new RegisterRequest("new@example.com", "password123", "New User");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        User user = userService.register(request);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.isEmailVerified()).isFalse();
        verify(userRepository).save(user);
        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("taken@example.com", "password123", "Someone");
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void verifyEmailRejectsUnknownToken() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyEmail("not-a-real-token"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        User user = new User("someone@example.com", "hash", "Someone");
        EmailVerificationToken expiredToken =
                new EmailVerificationToken(user, "irrelevant-hash", Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> userService.verifyEmail("expired-token"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void verifyEmailRejectsAlreadyUsedToken() {
        User user = new User("someone@example.com", "hash", "Someone");
        EmailVerificationToken usedToken =
                new EmailVerificationToken(user, "irrelevant-hash", Instant.now().plusSeconds(3600));
        usedToken.markUsed();
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> userService.verifyEmail("used-token"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void getProfileThrowsWhenUserMissing() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(missingId))
                .isInstanceOf(UserNotFoundException.class);
    }
}
