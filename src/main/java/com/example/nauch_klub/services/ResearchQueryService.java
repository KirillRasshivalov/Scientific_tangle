package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.ResearchQueryRequest;
import com.example.nauch_klub.dto.ResearchQueryResponse;
import com.example.nauch_klub.dto.KnowledgeGraphResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResearchQueryService {
    private static final Path QUERY_LOG_PATH = Path.of("logs", "user-queries.log");

    private final YandexEmbeddingService embeddingService;
    private final KnowledgeQdrantService qdrantService;
    private final KnowledgeGraphService graphService;
    private final PythonAnswerClient pythonAnswerClient;

    public ResearchQueryService(
            YandexEmbeddingService embeddingService,
            KnowledgeQdrantService qdrantService,
            KnowledgeGraphService graphService,
            PythonAnswerClient pythonAnswerClient) {
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.graphService = graphService;
        this.pythonAnswerClient = pythonAnswerClient;
    }

    public ResearchQueryResponse handleQuery(ResearchQueryRequest request, String username) {
        String query = request.query() == null ? "" : request.query().trim();

        if (query.isBlank()) {
            throw new IllegalArgumentException("Query must not be empty");
        }

        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Map<String, String> filters = request.filters() == null ? Map.of() : request.filters();

        writeQueryLog(requestId, username, query, filters, now);
        List<Double> queryEmbedding = embeddingService.embedOne(query, "query");
        List<com.example.nauch_klub.dto.QdrantSearchResult> qdrantResults = qdrantService.searchTop(queryEmbedding, 5);
        KnowledgeGraphResponse graphContext;
        List<String> pipelineWarnings = new java.util.ArrayList<>();
        try {
            graphContext = graphService.getKnowledgeGraph(query, "", 2, 40);
        } catch (RuntimeException exception) {
            graphContext = new KnowledgeGraphResponse(List.of(), List.of());
            pipelineWarnings.add("Neo4j graph context is unavailable: " + exception.getMessage());
        }

        try {
            PythonAnswerClient.PythonAnswerResponse pythonResponse = pythonAnswerClient.answer(
                    query,
                    qdrantResults,
                    graphContext
            );

            return new ResearchQueryResponse(
                    requestId,
                    "PYTHON_ANSWER",
                    pythonResponse.answer(),
                    pythonResponse.overallConfidence(),
                    now,
                    queryEmbedding.size(),
                    qdrantResults,
                    graphContext.nodes().stream().map(com.example.nauch_klub.dto.KnowledgeGraphNode::label).limit(12).toList(),
                    pythonResponse.sources(),
                    pythonResponse.verifiedFacts(),
                    mergeWarnings(pipelineWarnings, pythonResponse.warnings())
            );
        } catch (RestClientException exception) {
            pipelineWarnings.add("Python answer service call failed: " + exception.getMessage());
            return pythonUnavailableFallback(requestId, filters, now, queryEmbedding.size(), qdrantResults, pipelineWarnings);
        }
    }

    private ResearchQueryResponse pythonUnavailableFallback(
            String requestId,
            Map<String, String> filters,
            Instant now,
            int embeddingDimension,
            List<com.example.nauch_klub.dto.QdrantSearchResult> qdrantResults,
            List<String> warnings) {
        String answer = """
                Yandex query embedding received successfully.
                Qdrant top-5 search completed against seeded knowledge-base chunks.
                Python answer service is unavailable, so Java returns retrieved context without synthesis.
                """;

        if (!filters.isEmpty()) {
            answer = answer + "\nActive filters: " + filters;
        }

        if (!qdrantResults.isEmpty()) {
            answer = answer + "\nBest match: " + qdrantResults.get(0).title();
        }

        return new ResearchQueryResponse(
                requestId,
                "PYTHON_SERVICE_UNAVAILABLE",
                answer,
                0.42,
                now,
                embeddingDimension,
                qdrantResults,
                List.of("UserQuery", "YandexEmbedding", "QdrantTopK", "PythonAnswerServiceUnavailable"),
                List.of(
                        Map.of(
                                "title", "Yandex query embedding",
                                "type", "embedding",
                                "dimension", String.valueOf(embeddingDimension)
                        ),
                        Map.of(
                                "title", "Qdrant collection",
                                "type", "vector_search",
                                "topK", String.valueOf(qdrantResults.size())
                        )
                ),
                List.of(),
                warnings
        );
    }

    private List<String> mergeWarnings(List<String> first, List<String> second) {
        List<String> merged = new java.util.ArrayList<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private void writeQueryLog(
            String requestId,
            String username,
            String query,
            Map<String, String> filters,
            Instant createdAt) {
        try {
            Files.createDirectories(QUERY_LOG_PATH.getParent());
            String logLine = String.format(
                    "{\"createdAt\":\"%s\",\"requestId\":\"%s\",\"username\":\"%s\",\"query\":\"%s\",\"filters\":\"%s\"}%n",
                    createdAt,
                    escapeJson(requestId),
                    escapeJson(username),
                    escapeJson(query),
                    escapeJson(filters.toString())
            );
            Files.writeString(
                    QUERY_LOG_PATH,
                    logLine,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write query log", exception);
        }
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
