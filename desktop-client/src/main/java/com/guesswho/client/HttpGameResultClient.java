package com.guesswho.client;

import com.guesswho.game.GameResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Sends completed games to the Guess Who HTTP API.
 */
public class HttpGameResultClient implements GameResultClient {
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final URI endpoint;
    private final HttpPoster httpPoster;

    /**
     * Creates a client using the {@code guesswho.server.url} system property,
     * or {@code http://localhost:8080} when it is not set.
     */
    public HttpGameResultClient() {
        this(URI.create(System.getProperty(
                "guesswho.server.url", DEFAULT_SERVER_URL)));
    }

    /**
     * Creates a client for a Guess Who server.
     *
     * @param serverBaseUri base URI of the server
     */
    public HttpGameResultClient(URI serverBaseUri) {
        this(serverBaseUri, createHttpPoster());
    }

    HttpGameResultClient(URI serverBaseUri, HttpPoster httpPoster) {
        this.endpoint = serverBaseUri.resolve("/api/game-results");
        this.httpPoster = httpPoster;
    }

    @Override
    public CompletableFuture<Void> submit(GameResult gameResult) {
        return httpPoster.post(endpoint, toJson(gameResult))
                .thenCompose(statusCode -> {
                    if (statusCode == 201) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.failedFuture(new IOException(
                            "Game-result submission returned HTTP " + statusCode));
                });
    }

    private static HttpPoster createHttpPoster() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        return (endpoint, body) -> {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(HttpResponse::statusCode);
        };
    }

    private String toJson(GameResult gameResult) {
        StringBuilder json = new StringBuilder("{\"participants\":[");
        for (int i = 0; i < gameResult.participants().size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendParticipant(json, gameResult.participants().get(i));
        }
        json.append("],\"winner\":");
        appendQuoted(json, gameResult.winner());
        json.append(",\"mode\":");
        appendQuoted(json, gameResult.mode().name());
        json.append(",\"difficulty\":");
        if (gameResult.difficulty() == null) {
            json.append("null");
        }
        else {
            appendQuoted(json, gameResult.difficulty().name());
        }
        json.append(",\"questionMode\":");
        appendQuoted(json, gameResult.questionMode().name());
        return json.append('}').toString();
    }

    private void appendParticipant(StringBuilder json, GameResult.Participant participant) {
        json.append("{\"name\":");
        appendQuoted(json, participant.name());
        json.append(",\"selectedCharacter\":");
        appendQuoted(json, participant.selectedCharacter());
        json.append(",\"questionAnswers\":[");
        for (int i = 0; i < participant.questionAnswers().size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            GameResult.QuestionAnswer questionAnswer = participant.questionAnswers().get(i);
            json.append("{\"question\":");
            appendQuoted(json, questionAnswer.question());
            json.append(",\"answer\":").append(questionAnswer.answer()).append('}');
        }
        json.append("]");
        if (participant.commitment() != null) {
            json.append(",\"commitment\":{\"hash\":");
            appendQuoted(json, participant.commitment().hash());
            json.append(",\"nonce\":");
            appendQuoted(json, participant.commitment().nonce());
            json.append('}');
        }
        json.append("}");
    }

    private void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> appendJsonCharacter(json, character);
            }
        }
        json.append('"');
    }

    private void appendJsonCharacter(StringBuilder json, char character) {
        if (character < 0x20) {
            json.append(String.format("\\u%04x", (int) character));
        } else {
            json.append(character);
        }
    }

    @FunctionalInterface
    interface HttpPoster {
        CompletableFuture<Integer> post(URI endpoint, String body);
    }
}
