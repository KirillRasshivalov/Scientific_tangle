package com.example.nauch_klub.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class YandexEmbeddingService {
    private final RestClient restClient;
    private final String apiKey;
    private final String folderId;
    private final String embedUrl;
    private final int expectedDimension;
    private final int maxRetries;
    private final long delayBetweenRequestsMillis;
    private final ConcurrentMap<String, List<Double>> cache = new ConcurrentHashMap<>();
    private long nextAllowedRequestAtMillis = 0;

    public YandexEmbeddingService(
            RestClient.Builder restClientBuilder,
            @Value("${yandex.api-key}") String apiKey,
            @Value("${yandex.folder-id}") String folderId,
            @Value("${yandex.embed-url}") String embedUrl,
            @Value("${yandex.embed-rps}") double rps,
            @Value("${yandex.embed-dim}") int expectedDimension,
            @Value("${yandex.embed-max-retries}") int maxRetries) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.folderId = folderId;
        this.embedUrl = embedUrl;
        this.expectedDimension = expectedDimension;
        this.maxRetries = maxRetries;
        this.delayBetweenRequestsMillis = Math.max(1, Math.round(1000.0 / Math.max(rps, 0.1)));
    }

    public List<Double> embedOne(String text, String kind) {
        validateConfig();
        validateKind(kind);

        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isBlank()) {
            throw new IllegalArgumentException("Text for embedding must not be empty");
        }

        String cacheKey = kind + ":" + normalizedText;
        List<Double> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        RuntimeException lastException = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            throttle();
            try {
                List<Double> embedding = requestEmbedding(normalizedText, kind);
                if (expectedDimension > 0 && embedding.size() != expectedDimension) {
                    throw new IllegalStateException(
                            "Embedding dimension " + embedding.size() + " != expected " + expectedDimension
                    );
                }
                cache.putIfAbsent(cacheKey, embedding);
                return embedding;
            } catch (RetryableEmbeddingException exception) {
                lastException = exception;
                backoff(attempt);
            } catch (RestClientException exception) {
                lastException = new RetryableEmbeddingException(exception.getMessage(), exception);
                backoff(attempt);
            }
        }

        throw new IllegalStateException("Embedding failed after " + maxRetries + " attempts", lastException);
    }

    private List<Double> requestEmbedding(String text, String kind) {
        Map<String, String> payload = Map.of(
                "modelUri", "emb://" + folderId + "/text-search-" + kind + "/latest",
                "text", text
        );

        Map<String, Object> response = restClient.post()
                .uri(embedUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Api-Key " + apiKey)
                .header("x-folder-id", folderId)
                .header("x-data-logging-enabled", "false")
                .body(payload)
                .retrieve()
                .onStatus(status -> status.value() == 429 || status.is5xxServerError(),
                        (request, clientResponse) -> {
                            throw new RetryableEmbeddingException("Yandex embedding HTTP " + clientResponse.getStatusCode().value());
                        })
                .body(new ParameterizedTypeReference<>() {
                });
        Object rawEmbedding = Objects.requireNonNull(response, "Empty Yandex embedding response").get("embedding");
        if (!(rawEmbedding instanceof List<?> rawList)) {
            throw new IllegalStateException("Yandex embedding response does not contain embedding array");
        }

        List<Double> embedding = new ArrayList<>(rawList.size());
        for (Object value : rawList) {
            if (!(value instanceof Number number)) {
                throw new IllegalStateException("Yandex embedding contains non-numeric value");
            }
            embedding.add(number.doubleValue());
        }

        return List.copyOf(embedding);
    }

    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("YANDEX_API_KEY is not configured");
        }
        if (folderId == null || folderId.isBlank()) {
            throw new IllegalStateException("YANDEX_FOLDER_ID is not configured");
        }
    }

    private void validateKind(String kind) {
        if (!"doc".equals(kind) && !"query".equals(kind)) {
            throw new IllegalArgumentException("Embedding kind must be 'doc' or 'query'");
        }
    }

    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long waitMillis = nextAllowedRequestAtMillis - now;
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Embedding throttle interrupted", exception);
            }
        }
        nextAllowedRequestAtMillis = System.currentTimeMillis() + delayBetweenRequestsMillis;
    }

    private void backoff(int attempt) {
        long sleepMillis = Math.min(5000, 250L * (1L << Math.min(attempt, 5)));
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding backoff interrupted", exception);
        }
    }

    private static class RetryableEmbeddingException extends RuntimeException {
        RetryableEmbeddingException(String message) {
            super(message);
        }

        RetryableEmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
