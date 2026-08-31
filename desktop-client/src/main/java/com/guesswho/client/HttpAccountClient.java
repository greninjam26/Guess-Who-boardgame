package com.guesswho.client;

import com.guesswho.account.Account;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers, logs in, and logs out against the Guess Who HTTP API.
 *
 * <p>Every outcome comes back as a value rather than an exception, because
 * every one of them is something the login screen has to say out loud: a name
 * already taken, a wrong password, and a server that cannot be reached are
 * three different sentences.</p>
 */
public class HttpAccountClient implements AccountClient {
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final URI accounts;
    private final URI sessions;
    private final Sender sender;

    /**
     * Creates a client using the {@code guesswho.server.url} system property,
     * or {@code http://localhost:8080} when it is not set.
     */
    public HttpAccountClient() {
        this(URI.create(System.getProperty("guesswho.server.url", DEFAULT_SERVER_URL)));
    }

    /**
     * @param serverBaseUri base URI of the server
     */
    public HttpAccountClient(URI serverBaseUri) {
        this(serverBaseUri, defaultSender());
    }

    HttpAccountClient(URI serverBaseUri, Sender sender) {
        this.accounts = serverBaseUri.resolve("/api/accounts");
        this.sessions = serverBaseUri.resolve("/api/sessions");
        this.sender = sender;
    }

    @Override
    public CompletableFuture<Outcome> register(String username, String password) {
        return sender.send(new Call("POST", accounts, credentials(username, password), null))
                .handle((response, failure) -> {
                    if (failure != null) {
                        return Outcome.unreachable();
                    }
                    return switch (response.statusCode()) {
                        case 201 -> Outcome.registered(accountFrom(response.body()));
                        case 409 -> Outcome.usernameTaken();
                        case 400 -> Outcome.rejected(messageFrom(response.body()));
                        default -> Outcome.unreachable();
                    };
                });
    }

    @Override
    public CompletableFuture<Outcome> logIn(String username, String password) {
        return sender.send(new Call("POST", sessions, credentials(username, password), null))
                .handle((response, failure) -> {
                    if (failure != null) {
                        return Outcome.unreachable();
                    }
                    return switch (response.statusCode()) {
                        case 201 -> loggedIn(response.body());
                        case 401 -> Outcome.wrongCredentials();
                        default -> Outcome.unreachable();
                    };
                });
    }

    @Override
    public CompletableFuture<Optional<Account>> whoAmI(String token) {
        return sender.send(new Call("GET", sessions.resolve("/api/sessions/current"),
                        null, token))
                .handle((response, failure) -> {
                    if (failure != null || response.statusCode() != 200) {
                        return Optional.<Account>empty();
                    }
                    return Optional.ofNullable(accountFrom(response.body()));
                });
    }

    @Override
    public CompletableFuture<Void> logOut(String token) {
        return sender.send(new Call("DELETE", sessions.resolve("/api/sessions/current"),
                        null, token))
                //A logout the server never heard leaves a token that still
                //works, but the game has already forgotten it and there is
                //nothing useful to tell the player about it.
                .handle((response, failure) -> null);
    }

    private static Outcome loggedIn(String body) {
        JsonNode node = JSON_MAPPER.readTree(body);
        JsonNode account = node.get("account");
        return Outcome.loggedIn(
                node.path("token").asString(),
                account == null ? null : new Account(
                        account.path("id").asLong(), account.path("username").asString()));
    }

    private static Account accountFrom(String body) {
        JsonNode node = JSON_MAPPER.readTree(body);
        return new Account(node.path("id").asLong(), node.path("username").asString());
    }

    /** Spring puts the reason in a "detail" field; fall back to something sayable. */
    private static String messageFrom(String body) {
        try {
            JsonNode node = JSON_MAPPER.readTree(body);
            String detail = node.path("detail").asString();
            return detail == null || detail.isBlank() ? "That was not accepted" : detail;
        }
        catch (RuntimeException unparseable) {
            return "That was not accepted";
        }
    }

    private static String credentials(String username, String password) {
        return JSON_MAPPER.writeValueAsString(
                new com.guesswho.account.Credentials(username, password));
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
            HttpRequest request = switch (call.method()) {
                case "GET" -> builder.GET().build();
                case "DELETE" -> builder.DELETE().build();
                default -> builder.POST(
                        HttpRequest.BodyPublishers.ofString(call.body())).build();
            };
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> new Response(response.statusCode(), response.body()));
        };
    }

    /**
     * One request, in the shape this client needs to make them.
     *
     * @param method   the HTTP method
     * @param endpoint where to send it
     * @param body     the JSON body, or null for a request without one
     * @param token    the bearer token, or null when not logged in
     */
    record Call(String method, URI endpoint, String body, String token) {
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
