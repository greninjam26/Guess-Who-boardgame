package com.guesswho.client;

import com.guesswho.leaderboard.LeaderboardEntry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Retrieves leaderboard standings from the Guess Who HTTP API.
 */
public class HttpLeaderboardClient implements LeaderboardClient {
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final URI endpoint;
    private final HttpGetter httpGetter;

    /**
     * Creates a client using the {@code guesswho.server.url} system property,
     * or {@code http://localhost:8080} when it is not set.
     */
    public HttpLeaderboardClient() {
        this(URI.create(System.getProperty(
                "guesswho.server.url", DEFAULT_SERVER_URL)));
    }

    /**
     * Creates a client for a Guess Who server.
     *
     * @param serverBaseUri base URI of the server
     */
    public HttpLeaderboardClient(URI serverBaseUri) {
        this(serverBaseUri, createHttpGetter());
    }

    HttpLeaderboardClient(URI serverBaseUri, HttpGetter httpGetter) {
        this.endpoint = serverBaseUri.resolve("/api/leaderboard");
        this.httpGetter = httpGetter;
    }

    @Override
    public CompletableFuture<List<LeaderboardEntry>> fetch() {
        return httpGetter.get(endpoint)
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new IOException(
                                "Leaderboard request returned HTTP "
                                        + response.statusCode()));
                    }
                    return parse(response.body());
                });
    }

    private List<LeaderboardEntry> parse(String body) {
        try {
            return JSON_MAPPER.readValue(
                    body,
                    new TypeReference<List<LeaderboardEntry>>() {
                    });
        } catch (JacksonException exception) {
            throw new CompletionException(
                    "Leaderboard response contained invalid JSON", exception);
        }
    }

    private static HttpGetter createHttpGetter() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        return endpoint -> {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> new Response(
                            response.statusCode(), response.body()));
        };
    }

    /**
     * HTTP response data needed by the leaderboard client.
     *
     * @param statusCode HTTP status code
     * @param body response body
     */
    record Response(int statusCode, String body) {
    }

    @FunctionalInterface
    interface HttpGetter {
        CompletableFuture<Response> get(URI endpoint);
    }
}
