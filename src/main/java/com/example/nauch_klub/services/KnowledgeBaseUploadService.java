package com.example.nauch_klub.services;

import com.example.nauch_klub.dto.KnowledgeUploadResponse;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class KnowledgeBaseUploadService {
    private static final Path UPLOAD_LOG_PATH = Path.of("logs", "knowledge-upload-requests.log");

    private final MinioClient minioClient;
    private final String bucket;

    public KnowledgeBaseUploadService(
            MinioClient minioClient,
            @Value("${minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    public KnowledgeUploadResponse upload(MultipartFile file, String username) {
        String requestId = UUID.randomUUID().toString();
        Instant uploadedAt = Instant.now();
        validateFile(file);

        String originalFilename = cleanFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        String objectName = "uploads/" + uploadedAt.toString().substring(0, 10)
                + "/" + requestId + extension;

        try {
            ensureBucketExists();
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(resolveContentType(file, extension))
                                .build()
                );
            }
        } catch (Exception exception) {
            writeUploadLog(requestId, username, originalFilename, file.getSize(), "FAILED", exception.getMessage(), uploadedAt);
            throw new IllegalStateException("Could not upload file to MinIO", exception);
        }

        writeUploadLog(requestId, username, originalFilename, file.getSize(), "UPLOADED", objectName, uploadedAt);

        return new KnowledgeUploadResponse(
                requestId,
                "UPLOADED",
                bucket,
                objectName,
                originalFilename,
                file.getSize(),
                resolveContentType(file, extension),
                uploadedAt
        );
    }

    public void logDeniedUpload(String username, MultipartFile file) {
        String originalFilename = file == null ? "" : cleanFilename(file.getOriginalFilename());
        long size = file == null ? 0 : file.getSize();
        writeUploadLog(UUID.randomUUID().toString(), username, originalFilename, size, "DENIED", "Role is not ANALYST", Instant.now());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        String filename = cleanFilename(file.getOriginalFilename());
        String extension = getExtension(filename).toLowerCase(Locale.ROOT);

        if (!".pdf".equals(extension) && !".docx".equals(extension)) {
            throw new IllegalArgumentException("Only PDF and DOCX files are supported");
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucket)
                        .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket)
                            .build()
            );
        }
    }

    private String resolveContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }

        return ".pdf".equalsIgnoreCase(extension)
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }

        return Path.of(filename).getFileName().toString();
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }

    private void writeUploadLog(
            String requestId,
            String username,
            String filename,
            long size,
            String status,
            String details,
            Instant createdAt) {
        try {
            Files.createDirectories(UPLOAD_LOG_PATH.getParent());
            String logLine = String.format(
                    "{\"createdAt\":\"%s\",\"requestId\":\"%s\",\"username\":\"%s\",\"filename\":\"%s\",\"size\":%d,\"status\":\"%s\",\"details\":\"%s\"}%n",
                    createdAt,
                    escapeJson(requestId),
                    escapeJson(username),
                    escapeJson(filename),
                    size,
                    escapeJson(status),
                    escapeJson(details == null ? "" : details)
            );
            Files.writeString(
                    UPLOAD_LOG_PATH,
                    logLine,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write upload log", exception);
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
