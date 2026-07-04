package com.example.nauch_klub.dto;

import java.util.List;

public class JsonDocument {

    private long id;
    private String name;
    private String description;
    private List<Float> vector;

    public JsonDocument() {}

    public JsonDocument(long id,
                        String name,
                        String description,
                        List<Float> vector) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.vector = vector;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Float> getVector() {
        return vector;
    }

    public void setVector(List<Float> vector) {
        this.vector = vector;
    }
}
