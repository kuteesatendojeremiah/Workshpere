package com.worksphere.common.realtime;

import com.worksphere.identity.JwtService;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Security for the WebSocket. Browsers cannot set an Authorization header on a WebSocket
 * handshake, so the JWT is sent in the STOMP CONNECT frame instead and checked here:
 *
 * <ul>
 *   <li>CONNECT without a valid access token is rejected.
 *   <li>SUBSCRIBE is allow-listed: a user's own notification queue, plus any destination a
 *       {@link RealtimeTopicAuthorizer} claims AND approves. Everything else is denied.
 *   <li>SEND is denied outright. Clients only listen; all writes go through REST.
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String OWN_NOTIFICATIONS = "/user/queue/notifications";

    private final JwtService jwtService;
    private final List<RealtimeTopicAuthorizer> authorizers;

    public StompAuthChannelInterceptor(JwtService jwtService, List<RealtimeTopicAuthorizer> authorizers) {
        this.jwtService = jwtService;
        this.authorizers = authorizers;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            UUID userId = authenticate(accessor).orElseThrow(() -> new MessagingException("Unauthenticated"));
            accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            requireSubscribeAllowed(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            throw new MessagingException("Clients may not send messages; use the REST API");
        }
        return message;
    }

    private Optional<UUID> authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return jwtService.parseUserId(header.substring(BEARER_PREFIX.length()));
    }

    private void requireSubscribeAllowed(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        String destination = accessor.getDestination();
        if (principal == null || destination == null) {
            throw new MessagingException("Unauthenticated");
        }
        if (OWN_NOTIFICATIONS.equals(destination)) {
            return; // Spring routes /user/... to this principal only; nobody can name another user.
        }
        UUID userId = UUID.fromString(principal.getName());
        boolean allowed = authorizers.stream()
                .filter(authorizer -> authorizer.supports(destination))
                .anyMatch(authorizer -> authorizer.canSubscribe(userId, destination));
        if (!allowed) {
            throw new MessagingException("Not allowed to subscribe to " + destination);
        }
    }
}
