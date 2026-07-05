package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.KnowledgeGraphEdge;
import com.example.nauch_klub.dto.KnowledgeGraphNode;
import com.example.nauch_klub.dto.KnowledgeGraphResponse;
import com.example.nauch_klub.dto.QdrantSearchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PythonAnswerClient {
    private static final Path PYTHON_REQUEST_LOG_PATH = Path.of("logs", "python-answer-requests.log");
    private static final Path LAST_PYTHON_REQUEST_BODY_PATH = Path.of("logs", "last-python-answer-request.json");
    private static final Path LAST_PYTHON_RESPONSE_BODY_PATH = Path.of("logs", "last-python-answer-response.json");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String answerEndpoint;

    public PythonAnswerClient(
            ObjectMapper objectMapper,
            @Value("${python.answer-service-url}") String answerServiceUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.objectMapper = objectMapper;
        this.answerEndpoint = answerServiceUrl.replaceAll("/+$", "") + "/v1/answer";
    }

    public PythonAnswerResponse answer(
            String query,
            List<QdrantSearchResult> chunks,
            KnowledgeGraphResponse graphContext) {
        Map<String, Object> request = Map.of(
                "query", query,
                "chunks", chunks.stream().map(this::chunkPayload).toList(),
                "graph_context", graphPayload(query, chunks, graphContext)
        );
        String requestJson = toJson(request);
        writeRequestLog(query, chunks.size(), graphContext.nodes().size(), requestJson);

        Map<String, Object> response = postJson(requestJson);

        Map<String, Object> body = Objects.requireNonNull(response, "Empty Python answer response");
        return new PythonAnswerResponse(
                stringValue(body.get("answer")),
                doubleValue(body.get("overall_confidence")),
                listOfMaps(body.get("verified_facts")),
                sources(body.get("sources")),
                listOfStrings(body.get("warnings"))
        );
    }

    private Map<String, Object> postJson(String requestJson) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(answerEndpoint))
                .timeout(Duration.ofMinutes(3))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            writeResponseLog(response.statusCode(), response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RestClientException("Python answer service returned HTTP "
                        + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new RestClientException("Python answer service IO error", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Python answer service call interrupted", exception);
        }
    }

    private void writeResponseLog(int statusCode, String responseBody) {
        try {
            Files.createDirectories(LAST_PYTHON_RESPONSE_BODY_PATH.getParent());
            Object responseJson;
            try {
                responseJson = objectMapper.readValue(responseBody, Object.class);
            } catch (JsonProcessingException exception) {
                responseJson = responseBody;
            }
            String prettyBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "createdAt", Instant.now().toString(),
                    "endpoint", answerEndpoint,
                    "statusCode", statusCode,
                    "body", responseJson
            ));
            Files.writeString(
                    LAST_PYTHON_RESPONSE_BODY_PATH,
                    prettyBody,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write Python answer response log", exception);
        }
    }

    private String toJson(Map<String, Object> request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize Python answer request", exception);
        }
    }

    private void writeRequestLog(String query, int chunkCount, int graphNodeCount, String requestJson) {
        try {
            Files.createDirectories(PYTHON_REQUEST_LOG_PATH.getParent());
            String logLine = objectMapper.writeValueAsString(Map.of(
                    "createdAt", Instant.now().toString(),
                    "endpoint", answerEndpoint,
                    "query", query,
                    "chunkCount", chunkCount,
                    "graphNodeCount", graphNodeCount,
                    "requestJson", requestJson
            )) + System.lineSeparator();
            Files.writeString(
                    PYTHON_REQUEST_LOG_PATH,
                    logLine,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            Object parsedBody = objectMapper.readValue(requestJson, Object.class);
            String prettyBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedBody);
            Files.writeString(
                    LAST_PYTHON_REQUEST_BODY_PATH,
                    prettyBody,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write Python answer request log", exception);
        }
    }

    private Map<String, Object> chunkPayload(QdrantSearchResult result) {
        return Map.of(
                "chunk_id", result.id(),
                "doc_id", result.title().isBlank() ? result.domain() : result.title(),
                "score", result.score(),
                "text", result.text()
        );
    }

    private Map<String, Object> graphPayload(
            String query,
            List<QdrantSearchResult> chunks,
            KnowledgeGraphResponse graphContext) {
        Map<String, String> nodeNamesById = graphContext.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(
                        KnowledgeGraphNode::id,
                        KnowledgeGraphNode::label,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));

        List<Map<String, Object>> entities = new java.util.ArrayList<>(
                graphContext.nodes().stream()
                        .limit(24)
                        .map(this::nodePayload)
                        .toList()
        );
        addQueryEntities(query, entities);

        return Map.of(
                "entities", deduplicateEntities(entities),
                "relations", graphContext.edges().stream()
                        .limit(40)
                        .map(edge -> edgePayload(edge, nodeNamesById))
                        .toList(),
                "measurements", extractMeasurements(query, chunks)
        );
    }

    private Map<String, Object> nodePayload(KnowledgeGraphNode node) {
        return Map.of(
                "id", node.id(),
                "name", node.label(),
                "type", node.type()
        );
    }

    private Map<String, Object> edgePayload(KnowledgeGraphEdge edge, Map<String, String> nodeNamesById) {
        return Map.of(
                "source", nodeNamesById.getOrDefault(edge.source(), edge.source()),
                "relation", edge.type(),
                "target", nodeNamesById.getOrDefault(edge.target(), edge.target())
        );
    }

    private void addQueryEntities(String query, List<Map<String, Object>> entities) {
        String text = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("католит")) {
            entities.add(Map.of("id", "material_katolit", "name", "католит", "type", "Material"));
        }
        if (text.contains("электроэкстрак")) {
            entities.add(Map.of("id", "process_nickel_electrowinning", "name", "электроэкстракция никеля", "type", "Process"));
        }
        if (text.contains("никел")) {
            entities.add(Map.of("id", "material_nickel", "name", "никель", "type", "Material"));
        }
        if (text.contains("скорост")) {
            entities.add(Map.of("id", "property_flow_velocity", "name", "скорость циркуляции", "type", "Property"));
        }
    }

    private List<Map<String, Object>> deduplicateEntities(List<Map<String, Object>> entities) {
        Map<String, Map<String, Object>> byNameAndType = new java.util.LinkedHashMap<>();
        for (Map<String, Object> entity : entities) {
            String key = String.valueOf(entity.get("name")).toLowerCase(java.util.Locale.ROOT)
                    + "::" + String.valueOf(entity.get("type"));
            byNameAndType.putIfAbsent(key, entity);
        }
        return List.copyOf(byNameAndType.values());
    }

    private List<Map<String, Object>> extractMeasurements(String query, List<QdrantSearchResult> chunks) {
        StringBuilder source = new StringBuilder(query == null ? "" : query);
        for (QdrantSearchResult chunk : chunks) {
            source.append('\n').append(chunk.text());
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?iu)(скорост[а-яё]*|расход[а-яё]*|концентрац[а-яё]*|температур[а-яё]*|содержан[а-яё]*|поток[а-яё]*)?[^\\d]{0,45}(\\d+(?:[,.]\\d+)?)\\s*(м/с|л/час|л/ч|г/л|мг/л|мг/дм3|мг/дм³|%|°c|c)"
        );

        List<Map<String, Object>> measurements = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = pattern.matcher(source);
        while (matcher.find() && measurements.size() < 12) {
            String parameter = matcher.group(1);
            String value = matcher.group(2).replace(',', '.');
            String unit = matcher.group(3);
            measurements.add(Map.of(
                    "entity", inferMeasurementEntity(source.toString()),
                    "parameter", normalizeParameter(parameter, unit),
                    "value", Double.parseDouble(value),
                    "unit", normalizeUnit(unit)
            ));
        }
        return measurements;
    }

    private String inferMeasurementEntity(String text) {
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("католит")) {
            return "католит";
        }
        if (normalized.contains("электролит")) {
            return "электролит";
        }
        if (normalized.contains("никел")) {
            return "никель";
        }
        return "технологический параметр";
    }

    private String normalizeParameter(String parameter, String unit) {
        if (parameter != null && !parameter.isBlank()) {
            String normalized = parameter.toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("скорост") || normalized.startsWith("поток") || normalized.startsWith("расход")) {
                return "скорость циркуляции";
            }
            if (normalized.startsWith("концентрац") || normalized.startsWith("содержан")) {
                return "концентрация";
            }
            if (normalized.startsWith("температур")) {
                return "температура";
            }
        }
        String normalizedUnit = unit == null ? "" : unit.toLowerCase(java.util.Locale.ROOT);
        if (normalizedUnit.contains("м/с") || normalizedUnit.contains("л/")) {
            return "скорость циркуляции";
        }
        return "значение";
    }

    private String normalizeUnit(String unit) {
        return unit == null ? "" : unit.replace("дм3", "дм³");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> next = new java.util.LinkedHashMap<>();
                    ((Map<?, ?>) item).forEach((key, mapValue) -> next.put(String.valueOf(key), mapValue));
                    return next;
                })
                .toList();
    }

    private List<String> listOfStrings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        return list.stream().map(String::valueOf).toList();
    }

    private List<Map<String, Object>> sources(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .map(item -> {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> next = new java.util.LinkedHashMap<>();
                        map.forEach((key, mapValue) -> next.put(String.valueOf(key), mapValue));
                        return next;
                    }
                    return Map.<String, Object>of("chunk_id", String.valueOf(item));
                })
                .toList();
    }

    public record PythonAnswerResponse(
            String answer,
            double overallConfidence,
            List<Map<String, Object>> verifiedFacts,
            List<Map<String, Object>> sources,
            List<String> warnings
    ) {
    }
}
