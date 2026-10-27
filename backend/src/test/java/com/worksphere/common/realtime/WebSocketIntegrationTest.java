package com.worksphere.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import com.worksphere.notification.NotificationService;
import com.worksphere.notification.NotificationType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Real WebSocket connections against a real server port (spec section 15). The security rules
 * are the point: no token, no connection; only people who can see a project can listen to its
 * board; a user's notification queue is private to them; clients cannot send.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest extends AbstractIntegrationTest {

    private static final long WAIT_SECONDS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketStompClient stompClient = newClient();
    private StompSession session;
    private StompSession otherSession;

    @AfterEach
    void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (otherSession != null && otherSession.isConnected()) {
            otherSession.disconnect();
        }
        stompClient.stop();
    }

    @Test
    void connectingWithoutAValidTokenIsRefused() {
        assertThatThrownBy(() -> connect(null)).isNotNull();
        assertThatThrownBy(() -> connect("Bearer not-a-real-token")).isNotNull();
    }

    @Test
    void aProjectMemberReceivesBoardEventsWhenATaskIsCreated() throws Exception {
        User owner = createUser("ws-owner@example.com");
        UUID orgId = post("/api/v1/organizations", owner, Map.of("name", "WS Org"));
        UUID projectId = post("/api/v1/organizations/" + orgId + "/projects", owner, Map.of("name", "WS Project"));

        session = connect(bearer(owner));
        BlockingQueue<JsonNode> events = subscribe(session, "/topic/projects/" + projectId + "/board");

        UUID taskId = post("/api/v1/projects/" + projectId + "/tasks", owner, Map.of("title", "Live task"));

        JsonNode event = events.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(event).as("a TASK_CREATED event").isNotNull();
        assertThat(event.get("type").asText()).isEqualTo("TASK_CREATED");
        assertThat(event.get("taskId").asText()).isEqualTo(taskId.toString());
        // Ids only - the event must not carry task data (the browser re-fetches over REST).
        assertThat(event.has("title")).isFalse();
    }

    @Test
    void aUserFromAnotherOrganizationCannotListenToTheBoard() throws Exception {
        User owner = createUser("ws-owner2@example.com");
        User outsider = createUser("ws-outsider@example.com");
        UUID orgId = post("/api/v1/organizations", owner, Map.of("name", "WS Org 2"));
        UUID projectId = post("/api/v1/organizations/" + orgId + "/projects", owner, Map.of("name", "Private"));

        session = connect(bearer(outsider));
        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        session.subscribe("/topic/projects/" + projectId + "/board", jsonHandler(events));

        // The server answers a forbidden SUBSCRIBE with an ERROR frame and closes the connection.
        awaitDisconnected(session);
        post("/api/v1/projects/" + projectId + "/tasks", owner, Map.of("title", "Secret task"));

        assertThat(session.isConnected()).isFalse();
        assertThat(events.poll(2, TimeUnit.SECONDS)).as("outsider receives nothing").isNull();
    }

    @Test
    void aNotificationReachesOnlyItsRecipient() throws Exception {
        User recipient = createUser("ws-recipient@example.com");
        User bystander = createUser("ws-bystander@example.com");

        session = connect(bearer(recipient));
        otherSession = connect(bearer(bystander));
        BlockingQueue<JsonNode> recipientEvents = subscribe(session, "/user/queue/notifications");
        BlockingQueue<JsonNode> bystanderEvents = subscribe(otherSession, "/user/queue/notifications");

        notificationService.notify(
                recipient.getId(), NotificationType.TASK_ASSIGNED, "You were assigned", "TASK", UUID.randomUUID());

        JsonNode event = recipientEvents.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(event).as("the recipient is told").isNotNull();
        assertThat(event.get("type").asText()).isEqualTo("NOTIFICATION_CREATED");
        assertThat(bystanderEvents.poll(2, TimeUnit.SECONDS)).as("the bystander is not").isNull();
    }

    @Test
    void clientsCannotSendMessagesThroughTheSocket() throws Exception {
        User user = createUser("ws-sender@example.com");
        session = connect(bearer(user));

        session.send("/app/anything", "hello");

        // The server drops the connection when a client tries to send.
        awaitDisconnected(session);
        assertThat(session.isConnected()).isFalse();
    }

    // ----------------------------------------------------------------------------------------

    private void awaitDisconnected(StompSession stompSession) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (stompSession.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }

    private WebSocketStompClient newClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        client.setMessageConverter(converter);
        return client;
    }

    private StompSession connect(String authorizationHeader) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (authorizationHeader != null) {
            connectHeaders.add("Authorization", authorizationHeader);
        }
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private BlockingQueue<JsonNode> subscribe(StompSession stompSession, String destination) throws Exception {
        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        StompSession.Subscription subscription = stompSession.subscribe(destination, jsonHandler(events));
        // Give the broker a moment to register the subscription before anything is published.
        Thread.sleep(500);
        assertThat(subscription).isNotNull();
        return events;
    }

    private StompFrameHandler jsonHandler(BlockingQueue<JsonNode> events) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((JsonNode) payload);
            }
        };
    }

    private UUID post(String path, User user, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, bearer(user));
        String response = rest.postForObject(path, new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private User createUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId());
    }
}
