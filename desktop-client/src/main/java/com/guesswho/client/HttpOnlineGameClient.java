package com.guesswho.client;

import com.guesswho.room.Room;
import com.guesswho.room.RoomState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plays an online game against the Guess Who HTTP API.
 *
 * <p>Every move carries a key, and a move that fails to reach the server is
 * retried with the same one. That is what makes a lost connection survivable:
 * the server has already agreed that a repeated key changes nothing, so the
 * client can retry without wondering whether the first attempt landed.</p>
 */
public class HttpOnlineGameClient implements OnlineGameClient {
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    /** One retry. More would delay telling the player something is wrong. */
    private static final int ATTEMPTS = 2;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            //A server that grows a field should not break a client that has
            //not learned about it yet.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final URI rooms;
    private final Sender sender;

    /**
     * Creates a client using the {@code guesswho.server.url} system property,
     * or {@code http://localhost:8080} when it is not set.
     */
    public HttpOnlineGameClient() {
        this(URI.create(System.getProperty("guesswho.server.url", DEFAULT_SERVER_URL)));
    }

    /**
     * @param serverBaseUri base URI of the server
     */
    public HttpOnlineGameClient(URI serverBaseUri) {
        this(serverBaseUri, defaultSender());
    }

    HttpOnlineGameClient(URI serverBaseUri, Sender sender) {
        this.rooms = serverBaseUri.resolve("/api/rooms");
        this.sender = sender;
    }

    @Override
    public CompletableFuture<OnlineOutcome<Room>> createRoom(String token) {
        return send(new Call("POST", rooms, null, token, null))
                .thenApply(response -> outcome(response, Room.class));
    }

    @Override
    public CompletableFuture<OnlineOutcome<Room>> joinRoom(String code, String token) {
        return send(new Call("POST", path(code, "players"), null, token, null))
                .thenApply(response -> outcome(response, Room.class));
    }

    @Override
    public CompletableFuture<OnlineOutcome<RoomState>> state(String code, String token) {
        return send(new Call("GET", path(code, "state"), null, token, null))
                .thenApply(response -> outcome(response, RoomState.class));
    }

    @Override
    public CompletableFuture<OnlineOutcome<RoomState>> chooseCharacter(
            String code, String character, String token) {
        return move(code, "character", "{\"character\":%s}".formatted(quoted(character)), token);
    }

    @Override
    public CompletableFuture<OnlineOutcome<RoomState>> ask(
            String code, String question, String token) {
        return move(code, "questions", "{\"question\":%s}".formatted(quoted(question)), token);
    }

    @Override
    public CompletableFuture<OnlineOutcome<RoomState>> answer(
            String code, boolean answer, String token) {
        return move(code, "answers", "{\"answer\":%s}".formatted(answer), token);
    }

    @Override
    public CompletableFuture<OnlineOutcome<RoomState>> guess(
            String code, String character, String token) {
        return move(code, "guesses", "{\"character\":%s}".formatted(quoted(character)), token);
    }

    private CompletableFuture<OnlineOutcome<RoomState>> move(
            String code, String segment, String body, String token) {
        //One key for this move, kept across retries. A fresh key on the retry
        //would make the server treat it as a second move, which is the bug the
        //keys exist to prevent.
        return send(new Call("POST", path(code, segment), body, token,
                UUID.randomUUID().toString()))
                .thenApply(response -> outcome(response, RoomState.class));
    }

    /** Sends a call, retrying once if it never reached the server. */
    private CompletableFuture<Response> send(Call call) {
        return attempt(call, 1);
    }

    private CompletableFuture<Response> attempt(Call call, int number) {
        return sender.send(call).handle((response, failure) -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(response);
            }
            if (number >= ATTEMPTS) {
                return CompletableFuture.<Response>failedFuture(failure);
            }
            //Same call, same key. The server will either apply it, or recognise
            //it as one it has already applied and change nothing.
            return attempt(call, number + 1);
        }).thenCompose(next -> next);
    }

    private static <T> OnlineOutcome<T> outcome(Response response, Class<T> type) {
        return switch (response.statusCode()) {
            case 200, 201 -> OnlineOutcome.ok(JSON_MAPPER.readValue(response.body(), type));
            case 401 -> OnlineOutcome.failed(OnlineOutcome.Kind.SIGNED_OUT,
                    "You have been signed out. Sign in again to keep playing.");
            case 404 -> OnlineOutcome.failed(OnlineOutcome.Kind.NOT_FOUND,
                    "No game with that code");
            case 409 -> OnlineOutcome.failed(OnlineOutcome.Kind.REFUSED, detail(response));
            case 429 -> OnlineOutcome.failed(OnlineOutcome.Kind.TOO_MANY_ROOMS,
                    detail(response));
            default -> OnlineOutcome.failed(OnlineOutcome.Kind.UNREACHABLE,
                    "The server could not be reached");
        };
    }

    /** Spring puts the reason in "detail"; fall back to something sayable. */
    private static String detail(Response response) {
        try {
            JsonNode node = JSON_MAPPER.readTree(response.body());
            String detail = node.path("detail").asString();
            return detail == null || detail.isBlank() ? "That is not allowed right now" : detail;
        }
        catch (RuntimeException unparseable) {
            return "That is not allowed right now";
        }
    }

    private URI path(String code, String segment) {
        return URI.create(rooms + "/" + code + "/" + segment);
    }

    /** Escapes a value going into a hand-built JSON body. */
    private static String quoted(String value) {
        return JSON_MAPPER.writeValueAsString(value);
    }

    private static Sender defaultSender() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        return call -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder(call.endpoint())
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json");
            if (call.token() != null) {
                builder.header("Authorization", "Bearer " + call.token());
            }
            if (call.moveKey() != null) {
                builder.header("Idempotency-Key", call.moveKey());
            }
            HttpRequest request = "GET".equals(call.method())
                    ? builder.GET().build()
                    : builder.POST(HttpRequest.BodyPublishers.ofString(
                            call.body() == null ? "" : call.body())).build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> new Response(response.statusCode(), response.body()));
        };
    }

    /**
     * One request.
     *
     * @param method   the HTTP method
     * @param endpoint where to send it
     * @param body     the JSON body, or null
     * @param token    the bearer token
     * @param moveKey  the key for this move, or null when it is not a move
     */
    record Call(String method, URI endpoint, String body, String token, String moveKey) {
    }

    /**
     * @param statusCode HTTP status code
     * @param body       response body
     */
    record Response(int statusCode, String body) {
    }

    @FunctionalInterface
    interface Sender {
        CompletableFuture<Response> send(Call call);
    }
}
