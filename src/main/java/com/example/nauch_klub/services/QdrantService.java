package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.JsonDocument;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.ScoredPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QdrantService {
    private final List<JsonDocument> documents = new ArrayList<>();

    public void insert(JsonDocument document) {
        documents.removeIf(existing -> existing.getId() == document.getId());
        documents.add(document);
    }

    public List<ScoredPoint> search(List<Float> vector) {
        return documents.stream()
                .map(document -> toScoredPoint(document, cosineSimilarity(vector, document.getVector())))
                .sorted(Comparator.comparing(ScoredPoint::getScore).reversed())
                .limit(5)
                .toList();
    }

    private ScoredPoint toScoredPoint(JsonDocument document, float score) {
        return ScoredPoint.newBuilder()
                .setId(PointId.newBuilder().setNum(document.getId()).build())
                .setScore(score)
                .putPayload("name", Value.newBuilder().setStringValue(document.getName()).build())
                .putPayload("description", Value.newBuilder().setStringValue(document.getDescription()).build())
                .build();
    }

    private float cosineSimilarity(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;

        for (int index = 0; index < size; index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }

        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }

        return (float) (dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
    }
}
