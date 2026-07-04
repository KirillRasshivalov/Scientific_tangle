package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.ResearchQueryRequest;
import com.example.nauch_klub.dto.ResearchQueryResponse;
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

    public ResearchQueryResponse handleQuery(ResearchQueryRequest request, String username) {
        String query = request.query() == null ? "" : request.query().trim();

        if (query.isBlank()) {
            throw new IllegalArgumentException("Query must not be empty");
        }

        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Map<String, String> filters = request.filters() == null ? Map.of() : request.filters();

        writeQueryLog(requestId, username, query, filters, now);

        return callPythonModelStub(requestId, query, filters, now);
    }

    private ResearchQueryResponse callPythonModelStub(
            String requestId,
            String query,
            Map<String, String> filters,
            Instant now) {
        String answer = """
                Заглушка Python-сервиса приняла запрос и вернула предварительный ответ.
                Следующий шаг пайплайна: нормализация терминов, извлечение материалов,
                процессов, числовых ограничений и поиск по графу знаний. Сейчас вместо
                моделей возвращается тестовый результат, чтобы проверить связку UI -> Java -> Python adapter.
                """;

        if (!filters.isEmpty()) {
            answer = answer + "\nАктивные фильтры: " + filters;
        }

        return new ResearchQueryResponse(
                requestId,
                "STUB_RESULT",
                answer,
                0.42,
                now,
                List.of("UserQuery", "NlpPipelineStub", "KnowledgeGraphStub", "StructuredAnswer"),
                List.of(
                        Map.of(
                                "title", "Python model stub",
                                "type", "internal_stub",
                                "confidence", "low"
                        ),
                        Map.of(
                                "title", "Java request log",
                                "type", "audit_log",
                                "path", QUERY_LOG_PATH.toString()
                        )
                )
        );
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
