package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.JsonDocument;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.*;

import java.util.List;

public class QdrantService {

    private static final String COLLECTION = "documents";

    private final QdrantClient client;

    public QdrantService() {

        client = new QdrantClient(
                QdrantGrpcClient.newBuilder(
                        "localhost",
                        6334,
                        false
                ).build()
        );
    }

    public void createCollection(int vectorSize) throws Exception {

        client.createCollectionAsync(
                COLLECTION,
                VectorParams.newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(vectorSize)
                        .build()
        ).get();

    }

    public void insert(JsonDocument doc) throws Exception {

        PointStruct point = PointStruct.newBuilder()

                .setId(
                        Common.PointId.newBuilder()
                                .setNum(doc.getId())
                                .build()
                )

                .setVectors(
                        Vectors.newBuilder()
                                .setVector(
                                        Vector.newBuilder()
                                                .addAllData(doc.getVector())
                                                .build()
                                )
                                .build()
                )

                .putPayload(
                        "name",
                        JsonWithInt.Value.newBuilder()
                                .setStringValue(doc.getName())
                                .build()
                )

                .putPayload(
                        "description",
                        JsonWithInt.Value.newBuilder()
                                .setStringValue(doc.getDescription())
                                .build()
                )

                .build();

        client.upsertAsync(
                COLLECTION,
                java.util.List.of(point)
        ).get();

    }

    public List<ScoredPoint> search(List<Float> vector) throws Exception {

        SearchPoints request = SearchPoints.newBuilder()
                .setCollectionName(COLLECTION)
                .addAllVector(vector)
                .setLimit(5)
                .build();

        return client.searchAsync(request).get();
    }

}