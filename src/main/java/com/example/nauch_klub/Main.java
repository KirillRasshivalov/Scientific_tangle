package com.example.nauch_klub;

import com.example.nauch_klub.dto.JsonDocument;
import com.example.nauch_klub.services.QdrantService;
import io.qdrant.client.grpc.Points.ScoredPoint;

import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        QdrantService service = new QdrantService();

        List<JsonDocument> documents = List.of(

                new JsonDocument(
                        1,
                        "MacBook Pro M4",
                        "Apple laptop",
                        List.of(0.95f, 0.91f, 0.88f, 0.90f)
                ),

                new JsonDocument(
                        2,
                        "MacBook Air",
                        "Apple ultrabook",
                        List.of(0.93f, 0.90f, 0.87f, 0.89f)
                ),

                new JsonDocument(
                        3,
                        "Dell XPS",
                        "Windows laptop",
                        List.of(0.91f, 0.87f, 0.85f, 0.86f)
                ),

                new JsonDocument(
                        4,
                        "Lenovo ThinkPad",
                        "Business notebook",
                        List.of(0.89f, 0.84f, 0.83f, 0.85f)
                ),

                new JsonDocument(
                        5,
                        "HP EliteBook",
                        "Office laptop",
                        List.of(0.87f, 0.82f, 0.81f, 0.83f)
                ),

                new JsonDocument(
                        6,
                        "iPhone 16",
                        "Apple smartphone",
                        List.of(0.12f, 0.18f, 0.16f, 0.20f)
                ),

                new JsonDocument(
                        7,
                        "Samsung Galaxy",
                        "Android smartphone",
                        List.of(0.10f, 0.15f, 0.13f, 0.18f)
                ),

                new JsonDocument(
                        8,
                        "Pixel 9",
                        "Google smartphone",
                        List.of(0.11f, 0.17f, 0.14f, 0.19f)
                ),

                new JsonDocument(
                        9,
                        "PlayStation 5",
                        "Game console",
                        List.of(-0.50f, -0.40f, -0.45f, -0.48f)
                ),

                new JsonDocument(
                        10,
                        "Xbox Series X",
                        "Microsoft console",
                        List.of(-0.48f, -0.42f, -0.43f, -0.47f)
                )
        );

        for (JsonDocument doc : documents) {
            service.insert(doc);
        }

        search(service,
                "Запрос 1 (ноутбук Apple)",
                List.of(0.94f, 0.90f, 0.87f, 0.89f));

        search(service,
                "Запрос 2 (смартфон)",
                List.of(0.11f, 0.16f, 0.15f, 0.19f));

        search(service,
                "Запрос 3 (игровая консоль)",
                List.of(-0.49f, -0.41f, -0.44f, -0.46f));
    }

    private static void search(QdrantService service,
                               String title,
                               List<Float> vector) throws Exception {

        System.out.println();
        System.out.println("========== " + title + " ==========");

        List<ScoredPoint> result = service.search(vector);

        for (ScoredPoint point : result) {

            System.out.println("-----------------------------");
            System.out.println("ID = " + point.getId().getNum());
            System.out.println("Score = " + point.getScore());

            point.getPayloadMap().forEach((k, v) ->
                    System.out.println(k + " = " + v.getStringValue()));
        }
    }

}
