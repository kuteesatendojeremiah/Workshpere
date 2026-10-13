package com.worksphere.identity;

import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator tokenGenerator;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            SecureTokenGenerator tokenGenerator,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.auditService = auditService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException(request.email());
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), passwordHash, request.displayName());
        userRepository.save(user);
        issueVerificationToken(user);

        auditService.record(null, user.getId(), AuditAction.USER_REGISTERED, AuditResourceType.USER, user.getId(), null);
        return user;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = tokenRepository
                .findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(InvalidVerificationTokenException::new);

        if (token.isUsed() || token.isExpired()) {
            throw new InvalidVerificationTokenException();
        }

        token.markUsed();
        token.getUser().markEmailVerified();
    }

    @Transactional(readOnly = true)
    public User getProfile(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User updateProfile(UUID userId, String displayName) {
        User user = getProfile(userId);
        user.updateDisplayName(displayName);
        return user;
    }

    private void issueVerificationToken(User user) {
        String rawToken = tokenGenerator.generateRawToken();
        EmailVerificationToken token = new EmailVerificationToken(
                user, tokenGenerator.hash(rawToken), Instant.now().plus(VERIFICATION_TOKEN_TTL));
        tokenRepository.save(token);

        log.info(
                "Email verification token for user {} <{}>: {} "
                        + "(no email service until Step 22 - logging instead of sending)",
                user.getId(),
                user.getEmail(),
                rawToken);
    }
}
