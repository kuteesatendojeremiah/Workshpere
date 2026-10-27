package com.worksphere.identity;

import com.worksphere.common.mail.EmailService;
import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureTokenGenerator tokenGenerator;
    private final LoginAttemptGuard loginAttemptGuard;
    private final Duration refreshTokenTtl;
    private final AuditService auditService;
    private final EmailService emailService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SecureTokenGenerator tokenGenerator,
            LoginAttemptGuard loginAttemptGuard,
            JwtProperties jwtProperties,
            AuditService auditService,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenGenerator = tokenGenerator;
        this.loginAttemptGuard = loginAttemptGuard;
        this.refreshTokenTtl = Duration.ofDays(jwtProperties.refreshTokenTtlDays());
        this.auditService = auditService;
        this.emailService = emailService;
    }

    // LOGIN_FAILED must survive the rollback that InvalidCredentialsException would otherwise
    // trigger - we want a record of the failed attempt even though the attempt itself failed.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public TokenPairResponse login(LoginRequest request) {
        loginAttemptGuard.beforeAttempt(request.email());

        Optional<User> user = userRepository.findByEmail(request.email());
        if (user.isEmpty() || !passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
            loginAttemptGuard.onFailure(request.email());
            auditService.record(null, user.map(User::getId).orElse(null), AuditAction.LOGIN_FAILED, AuditResourceType.USER, null, null);
            throw new InvalidCredentialsException();
        }

        loginAttemptGuard.onSuccess(request.email());
        auditService.record(null, user.get().getId(), AuditAction.LOGIN_SUCCEEDED, AuditResourceType.USER, user.get().getId(), null);
        return issueTokenPair(user.get());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenPairResponse refresh(String rawToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            // The same refresh token was presented twice - a sign it may have been stolen.
            // Revoke every other active token for this user as a precaution.
            log.warn("Reused refresh token detected for user {}; revoking all its tokens", token.getUser().getId());
            revokeAllActiveTokens(token.getUser());
            throw new InvalidRefreshTokenException();
        }

        if (token.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        token.revoke();
        return issueTokenPair(token.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(tokenGenerator.hash(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke();
                auditService.record(
                        null, token.getUser().getId(), AuditAction.LOGOUT, AuditResourceType.USER, null, null);
            }
        });
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            issuePasswordResetToken(user);
            auditService.record(
                    null, user.getId(), AuditAction.PASSWORD_RESET_REQUESTED, AuditResourceType.USER, null, null);
        });
        // Always returns normally, whether or not the email exists - callers can't tell
        // which case occurred, which prevents using this endpoint to enumerate accounts.
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (token.isUsed() || token.isExpired()) {
            throw new InvalidPasswordResetTokenException();
        }

        token.markUsed();
        User user = token.getUser();
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        revokeAllActiveTokens(user);

        auditService.record(
                null, user.getId(), AuditAction.PASSWORD_RESET_COMPLETED, AuditResourceType.USER, null, null);
    }

    private TokenPairResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId());

        String rawRefreshToken = tokenGenerator.generateRawToken();
        RefreshToken refreshToken = new RefreshToken(
                user, tokenGenerator.hash(rawRefreshToken), Instant.now().plus(refreshTokenTtl));
        refreshTokenRepository.save(refreshToken);

        return TokenPairResponse.of(accessToken, rawRefreshToken);
    }

    private void revokeAllActiveTokens(User user) {
        refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user).forEach(RefreshToken::revoke);
    }

    private void issuePasswordResetToken(User user) {
        String rawToken = tokenGenerator.generateRawToken();
        PasswordResetToken token = new PasswordResetToken(
                user, tokenGenerator.hash(rawToken), Instant.now().plus(PASSWORD_RESET_TOKEN_TTL));
        passwordResetTokenRepository.save(token);

        emailService.sendPasswordReset(user.getEmail(), rawToken);
    }
}
