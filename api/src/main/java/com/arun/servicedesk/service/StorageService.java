package com.arun.servicedesk.service;

import com.arun.servicedesk.web.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {
    private final MinioClient client;
    private final String bucket;

    public StorageService(MinioClient client, @Value("${app.minio.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @PostConstruct
    void init() {
        try {
            ensureBucket();
        } catch (Exception e) {
            log.warn("MinIO not reachable at startup, will retry on first upload: {}", e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public String upload(MultipartFile file, String originalName, String contentType) {
        String key = UUID.randomUUID() + "-" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        try (InputStream in = file.getInputStream()) {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return key;
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "File storage is unavailable");
        }
    }

    public InputStream download(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            log.error("Download failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "File storage is unavailable");
        }
    }
}
