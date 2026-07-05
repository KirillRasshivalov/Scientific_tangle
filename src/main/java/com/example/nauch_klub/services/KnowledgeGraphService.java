package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.KnowledgeGraphEdge;
import com.example.nauch_klub.dto.KnowledgeGraphNode;
import com.example.nauch_klub.dto.KnowledgeGraphResponse;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KnowledgeGraphService {
    private final Driver driver;
    private final String database;
    private final AtomicBoolean seeded = new AtomicBoolean(false);

    public KnowledgeGraphService(
            Driver driver,
            @Value("${neo4j.database}") String database) {
        this.driver = driver;
        this.database = database;
    }

    private static final List<String> ONTOLOGY_TYPES = List.of(
            "Material",
            "Process",
            "Equipment",
            "Property",
            "Experiment",
            "Publication",
            "Expert",
            "Facility"
    );

    public KnowledgeGraphResponse getKnowledgeGraph(String search, String type, int depth, int limit) {
        ensureSeeded();

        Map<String, KnowledgeGraphNode> nodes = new LinkedHashMap<>();
        List<KnowledgeGraphEdge> edges = new ArrayList<>();
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<String> searchTerms = extractSearchTerms(normalizedSearch);
        String normalizedType = type == null ? "" : type.trim();
        int safeDepth = Math.max(1, Math.min(depth, 4));
        int safeLimit = Math.max(20, Math.min(limit, 160));
        int safeEdgeLimit = safeLimit * 3;

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeRead(tx -> {
                loadNodes(tx, nodes, searchTerms, normalizedType, safeDepth, safeLimit);

                if (nodes.isEmpty() && !searchTerms.isEmpty()) {
                    loadNodes(tx, nodes, List.of(), normalizedType, safeDepth, safeLimit);
                }

                tx.run("""
                        MATCH (a:KnowledgeEntity)-[r]->(b:KnowledgeEntity)
                        WHERE elementId(a) IN $nodeIds
                          AND elementId(b) IN $nodeIds
                          AND type(r) IN $relationshipTypes
                        RETURN r
                        ORDER BY
                          CASE type(r)
                            WHEN 'uses_material' THEN 1
                            WHEN 'operates_at_condition' THEN 2
                            WHEN 'produces_output' THEN 3
                            WHEN 'described_in' THEN 4
                            WHEN 'validated_by' THEN 5
                            WHEN 'contradicts' THEN 6
                            ELSE 7
                          END,
                          type(r)
                        LIMIT $edgeLimit
                        """, Map.of(
                        "nodeIds", new ArrayList<>(nodes.keySet()),
                        "relationshipTypes", relationshipTypes(),
                        "edgeLimit", safeEdgeLimit
                )).list(record -> {
                    Relationship relationship = record.get("r").asRelationship();
                    edges.add(new KnowledgeGraphEdge(
                            relationship.elementId(),
                            relationship.startNodeElementId(),
                            relationship.endNodeElementId(),
                            relationship.type(),
                            relationship.get("label").asString(relationship.type())
                    ));
                    return null;
                });
                return null;
            });
        }

        return new KnowledgeGraphResponse(List.copyOf(nodes.values()), List.copyOf(edges));
    }

    private void loadNodes(
            org.neo4j.driver.TransactionContext tx,
            Map<String, KnowledgeGraphNode> nodes,
            List<String> searchTerms,
            String normalizedType,
            int safeDepth,
            int safeLimit) {
        tx.run("""
                        MATCH (seed:KnowledgeEntity)
                        WHERE seed.type IN $ontologyTypes
                          AND ($type = '' OR seed.type = $type)
                          AND (
                            size($searchTerms) = 0
                            OR any(term IN $searchTerms WHERE
                              toLower(seed.name) CONTAINS term
                              OR toLower(seed.description) CONTAINS term
                              OR toLower(seed.key) CONTAINS term
                            )
                          )
                        WITH seed
                        ORDER BY coalesce(seed.sourceCount, 0) DESC, seed.type, seed.name
                        LIMIT $seedLimit
                        CALL {
                          WITH seed
                          MATCH path = (seed)-[*0..%d]-(n:KnowledgeEntity)
                          WHERE n.type IN $ontologyTypes
                            AND all(rel IN relationships(path) WHERE type(rel) IN $relationshipTypes)
                          RETURN n
                          ORDER BY coalesce(n.sourceCount, 0) DESC, n.type, n.name
                          LIMIT $perSeedLimit
                        }
                        WITH DISTINCT n
                        ORDER BY coalesce(n.sourceCount, 0) DESC, n.type, n.name
                        LIMIT $nodeLimit
                        RETURN n
                        """.formatted(safeDepth), Map.of(
                "ontologyTypes", ONTOLOGY_TYPES,
                "relationshipTypes", relationshipTypes(),
                "type", normalizedType,
                "searchTerms", searchTerms,
                "seedLimit", Math.max(8, safeLimit / 4),
                "perSeedLimit", Math.max(8, safeLimit / 3),
                "nodeLimit", safeLimit
        )).list(record -> {
            Node node = record.get("n").asNode();
            nodes.put(node.elementId(), new KnowledgeGraphNode(
                    node.elementId(),
                    node.get("name").asString(""),
                    node.get("type").asString(""),
                    node.get("description").asString("")
            ));
            return null;
        });
    }

    private List<String> relationshipTypes() {
        return List.of(
                "uses_material",
                "operates_at_condition",
                "produces_output",
                "described_in",
                "validated_by",
                "contradicts"
        );
    }

    private List<String> extractSearchTerms(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }

        List<String> stopWords = List.of(
                "какая", "какой", "какие", "какое", "оптимальная", "обзор",
                "современных", "способов", "показать", "между", "при", "для",
                "если", "чтобы", "где", "как", "что", "или", "это", "все"
        );

        return java.util.Arrays.stream(search.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+"))
                .map(term -> term.toLowerCase(Locale.ROOT).trim())
                .filter(term -> term.length() >= 4)
                .filter(term -> !stopWords.contains(term))
                .flatMap(term -> java.util.stream.Stream.of(term, stemLite(term)))
                .filter(term -> term.length() >= 4)
                .distinct()
                .limit(24)
                .toList();
    }

    private String stemLite(String term) {
        return term
                .replaceAll("(ого|его|ому|ему|ыми|ими|ами|ями|иях|ых|их|ая|яя|ое|ее|ий|ый|ой|ую|юю|ом|ем|ам|ям|ах|ях|ов|ев|ей|ия|ие|иям|иях|а|я|ы|и|е|у|ю|о)$", "");
    }

    private void ensureSeeded() {
        if (seeded.get()) {
            return;
        }

        synchronized (seeded) {
            if (seeded.get()) {
                return;
            }

            try (Session session = driver.session(SessionConfig.forDatabase(database))) {
                session.executeWrite(tx -> {
                    createConstraints(tx);
                    return null;
                });
                session.executeWrite(tx -> {
                    seedNodes(tx);
                    seedRelationships(tx);
                    return null;
                });
            }

            seeded.set(true);
        }
    }

    private void createConstraints(TransactionContext tx) {
        tx.run("""
                CREATE CONSTRAINT knowledge_entity_key IF NOT EXISTS
                FOR (n:KnowledgeEntity)
                REQUIRE n.key IS UNIQUE
                """);
        tx.run("""
                CREATE CONSTRAINT fact_version_key IF NOT EXISTS
                FOR (n:FactVersion)
                REQUIRE n.key IS UNIQUE
                """);
    }

    private void seedNodes(TransactionContext tx) {
        tx.run("""
                UNWIND $nodes AS row
                MERGE (n:KnowledgeEntity {key: row.key})
                SET n.name = row.name,
                    n.type = row.type,
                    n.description = row.description
                """, java.util.Map.of("nodes", List.of(
                node("water_sulfate_chloride", "Вода с SO4/Cl/Ca/Mg/Na 200-300 мг/л", "Material", "Исходная вода обогатительной фабрики с умеренной минерализацией."),
                node("desalination", "Обессоливание воды", "Process", "Снижение минерализации до сухого остатка ниже 1000 мг/дм3."),
                node("reverse_osmosis", "Обратный осмос", "Process", "Мембранная технология глубокого удаления солей."),
                node("nanofiltration", "Нанофильтрация", "Process", "Мембранная предочистка и селективное удаление двухвалентных ионов."),
                node("nickel_electrowinning", "Электроэкстракция никеля", "Process", "Катодное осаждение никеля из электролита."),
                node("catholyte_circulation", "Циркуляция католита", "Process", "Организация подачи и отвода католита в электролизных ваннах."),
                node("nickel_cell", "Ванна электроэкстракции", "Equipment", "Оборудование для осаждения никеля."),
                node("matte_slag_distribution", "Распределение Au/Ag/МПГ", "Experiment", "Изучение перехода благородных металлов между штейном и шлаком."),
                node("copper_nickel_matte", "Медно-никелевый штейн", "Material", "Сульфидная фаза пирометаллургического процесса."),
                node("slag", "Шлак", "Material", "Оксидная фаза, в которой возможны потери ценных металлов."),
                node("mine_water_injection", "Закачка шахтных вод", "Process", "Утилизация шахтных вод в глубокие горизонты."),
                node("deep_aquifer", "Глубокий поглощающий горизонт", "Facility", "Геологический объект для подземной закачки."),
                node("techno_economic_metrics", "ТЭП", "Property", "Капитальные и операционные затраты, приемистость, глубина скважин."),
                node("rd_publications", "Публикации и отчеты", "Publication", "Источники, подтверждающие технологические решения."),
                node("confidence_model", "Уровень достоверности", "Property", "Привязка выводов к источникам, актуальности и подтверждающим экспериментам.")
        )));
    }

    private void seedRelationships(TransactionContext tx) {
        List<EdgeSpec> edges = List.of(
                edge("desalination", "water_sulfate_chloride", "uses_material", "uses_material"),
                edge("desalination", "reverse_osmosis", "produces_output", "produces_output"),
                edge("desalination", "nanofiltration", "produces_output", "produces_output"),
                edge("reverse_osmosis", "confidence_model", "validated_by", "validated_by"),
                edge("nanofiltration", "confidence_model", "validated_by", "validated_by"),
                edge("nickel_electrowinning", "catholyte_circulation", "operates_at_condition", "operates_at_condition"),
                edge("catholyte_circulation", "nickel_cell", "operates_at_condition", "operates_at_condition"),
                edge("catholyte_circulation", "rd_publications", "described_in", "described_in"),
                edge("matte_slag_distribution", "copper_nickel_matte", "uses_material", "uses_material"),
                edge("matte_slag_distribution", "slag", "produces_output", "produces_output"),
                edge("matte_slag_distribution", "rd_publications", "validated_by", "validated_by"),
                edge("mine_water_injection", "deep_aquifer", "operates_at_condition", "operates_at_condition"),
                edge("mine_water_injection", "techno_economic_metrics", "operates_at_condition", "operates_at_condition"),
                edge("mine_water_injection", "rd_publications", "described_in", "described_in"),
                edge("rd_publications", "confidence_model", "validated_by", "validated_by")
        );

        for (EdgeSpec edge : edges) {
            tx.run("""
                    MATCH (a:KnowledgeEntity {key: $source})
                    MATCH (b:KnowledgeEntity {key: $target})
                    MERGE (a)-[r:`%s`]->(b)
                    SET r.label = $label
                    """.formatted(edge.type()), java.util.Map.of(
                    "source", edge.source(),
                    "target", edge.target(),
                    "label", edge.label()
            ));
        }
    }

    private java.util.Map<String, String> node(String key, String name, String type, String description) {
        return java.util.Map.of(
                "key", key,
                "name", name,
                "type", type,
                "description", description
        );
    }

    private EdgeSpec edge(String source, String target, String type, String label) {
        return new EdgeSpec(source, target, type, label);
    }

    private record EdgeSpec(String source, String target, String type, String label) {
    }
}
