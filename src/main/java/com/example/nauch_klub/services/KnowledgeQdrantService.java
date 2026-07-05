package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.QdrantSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KnowledgeQdrantService {
    private final RestClient restClient;
    private final YandexEmbeddingService embeddingService;
    private final String collection;
    private final int vectorSize;
    private final AtomicBoolean seeded = new AtomicBoolean(false);

    public KnowledgeQdrantService(
            RestClient.Builder restClientBuilder,
            YandexEmbeddingService embeddingService,
            @Value("${qdrant.url}") String qdrantUrl,
            @Value("${qdrant.collection}") String collection,
            @Value("${qdrant.vector-size}") int vectorSize) {
        this.restClient = restClientBuilder.baseUrl(qdrantUrl).build();
        this.embeddingService = embeddingService;
        this.collection = collection;
        this.vectorSize = vectorSize;
    }

    public List<QdrantSearchResult> searchTop(List<Double> queryVector, int limit) {
        ensureSeeded();

        Map<String, Object> body = Map.of(
                "vector", queryVector,
                "limit", limit,
                "with_payload", true
        );

        Map<String, Object> response = restClient.post()
                .uri("/collections/{collection}/points/search", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        Object rawResult = Objects.requireNonNull(response, "Empty Qdrant search response").get("result");
        if (!(rawResult instanceof List<?> rawPoints)) {
            return List.of();
        }

        List<QdrantSearchResult> results = new ArrayList<>();
        for (Object rawPoint : rawPoints) {
            if (!(rawPoint instanceof Map<?, ?> point)) {
                continue;
            }
            Object rawPayload = point.get("payload");
            Map<?, ?> payload = rawPayload instanceof Map<?, ?> map ? map : Map.of();
            results.add(new QdrantSearchResult(
                    String.valueOf(point.get("id")),
                    toDouble(point.get("score")),
                    payloadValue(payload, "title"),
                    payloadValue(payload, "text"),
                    payloadValue(payload, "domain"),
                    payloadValue(payload, "geography"),
                    payloadValue(payload, "sourceType"),
                    payloadValue(payload, "year")
            ));
        }

        return List.copyOf(results);
    }

    private void ensureSeeded() {
        if (seeded.get()) {
            return;
        }

        synchronized (seeded) {
            if (seeded.get()) {
                return;
            }

            ensureCollectionExists();
            if (countPoints() == 0) {
                upsertSeedPoints();
            }
            seeded.set(true);
        }
    }

    private void ensureCollectionExists() {
        try {
            restClient.get()
                    .uri("/collections/{collection}", collection)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw exception;
            }

            Map<String, Object> body = Map.of(
                    "vectors", Map.of(
                            "size", vectorSize,
                            "distance", "Cosine"
                    )
            );
            restClient.put()
                    .uri("/collections/{collection}", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private long countPoints() {
        Map<String, Object> response = restClient.post()
                .uri("/collections/{collection}/points/count", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("exact", true))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        Object rawResult = Objects.requireNonNull(response, "Empty Qdrant count response").get("result");
        if (!(rawResult instanceof Map<?, ?> result)) {
            return 0;
        }

        Object count = result.get("count");
        return count instanceof Number number ? number.longValue() : 0;
    }

    private void upsertSeedPoints() {
        List<Map<String, Object>> points = new ArrayList<>();
        for (SeedDocument document : seedDocuments()) {
            List<Double> vector = embeddingService.embedOne(document.text(), "doc");
            points.add(Map.of(
                    "id", document.id(),
                    "vector", vector,
                    "payload", Map.of(
                            "title", document.title(),
                            "text", document.text(),
                            "domain", document.domain(),
                            "geography", document.geography(),
                            "sourceType", document.sourceType(),
                            "year", document.year()
                    )
            ));
        }

        restClient.put()
                .uri("/collections/{collection}/points?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", points))
                .retrieve()
                .toBodilessEntity();
    }

    private List<SeedDocument> seedDocuments() {
        return List.of(
                new SeedDocument(
                        "7b5f2e04-5c65-4ac6-8dd4-4b72f0c97a10",
                        "Обессоливание оборотной воды обогатительной фабрики",
                        "Для воды с сульфатами, хлоридами, Ca, Mg и Na на уровне 200-300 мг/л и требуемым сухим остатком ниже 1000 мг/дм3 применимы обратный осмос, нанофильтрация, ионный обмен и комбинированные схемы осветление + мембраны. Ключевые ограничения: образование гипса, накипеобразование и необходимость предочистки.",
                        "ecology-water-treatment",
                        "world",
                        "review",
                        "2024"
                ),
                new SeedDocument(
                        "1cd29743-4217-476d-9d84-d2ef2a6c9f11",
                        "Циркуляция католита при электроэкстракции никеля",
                        "В мировой практике электроэкстракции никеля описаны распределительная подача католита, нижняя подача с верхним переливом, боковые коллекторы и усиленная циркуляция вдоль катодной поверхности. Оптимальная скорость потока зависит от геометрии ванны и обычно подбирается для снижения концентрационной поляризации без срыва осадка.",
                        "hydrometallurgy-nickel",
                        "world",
                        "technical-review",
                        "2023"
                ),
                new SeedDocument(
                        "8637fd17-6e3d-442b-9fb6-9539eb9a3b40",
                        "Распределение Au, Ag и МПГ между штейном и шлаком",
                        "Эксперименты последних 5 лет по распределению Au, Ag и металлов платиновой группы между медным или никелевым штейном и шлаком учитывают температуру, состав штейна, Fe/SiO2 в шлаке, кислородный потенциал и содержание серы. Данные применяются для оценки потерь благородных металлов со шлаком.",
                        "pyrometallurgy-precious-metals",
                        "world",
                        "experiment-summary",
                        "2025"
                ),
                new SeedDocument(
                        "5c8c0ab9-82f1-4c67-ae97-df39b777d4e0",
                        "Закачка шахтных вод в глубокие горизонты",
                        "В России и за рубежом применялись схемы закачки шахтных вод в глубокие поглощающие горизонты через нагнетательные скважины с предварительной фильтрацией, контролем совместимости вод и мониторингом давления. Технико-экономические показатели зависят от минерализации, приемистости пласта, глубины скважин и требований экологического контроля.",
                        "mine-water-management",
                        "russia-and-world",
                        "case-review",
                        "2022"
                ),
                new SeedDocument(
                        "e921f49b-dac9-442c-b95c-b790be43d23e",
                        "Числовые ограничения в технологическом поиске",
                        "Для многопараметрических R&D запросов важно хранить концентрации, температуры, скорости потоков, производительность и экономические показатели как числовые поля. Это позволяет фильтровать решения по диапазонам, например сульфаты меньше 300 мг/л или сухой остаток меньше 1000 мг/дм3.",
                        "knowledge-graph-metadata",
                        "generic",
                        "method-note",
                        "2024"
                )
        );
    }

    private String payloadValue(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private record SeedDocument(
            String id,
            String title,
            String text,
            String domain,
            String geography,
            String sourceType,
            String year
    ) {
    }
}
