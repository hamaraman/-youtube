package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.nio.file.Path;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String publicUrl;
    private final boolean configured;

    public S3StorageService(
            @Value("${r2.endpoint:}") String endpoint,
            @Value("${r2.access-key:}") String accessKey,
            @Value("${r2.secret-key:}") String secretKey,
            @Value("${r2.bucket:}") String bucket,
            @Value("${r2.public-url:}") String publicUrl
    ) {
        this.bucket = bucket;
        String trimmedPublicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.publicUrl = trimmedPublicUrl;
        this.configured = !endpoint.isBlank() && !accessKey.isBlank() && !secretKey.isBlank()
                && !bucket.isBlank() && !publicUrl.isBlank();

        System.out.println("[R2] configured=" + configured + " endpoint=" + endpoint + " bucket=" + bucket);
        if (configured) {
            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .region(Region.of("auto"))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .checksumValidationEnabled(false)
                            .chunkedEncodingEnabled(false)
                            .build())
                    .build();
        } else {
            this.s3Client = null;
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public String upload(Path localFile, String key, String contentType) throws Exception {
        System.out.println("[R2] Uploading: " + key);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                localFile
        );
        return publicUrl + "/" + key;
    }

    public void download(String key, Path dest) throws Exception {
        s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                dest
        );
    }

    public void delete(String url) {
        if (!configured || url == null || url.isBlank()) return;
        if (!url.startsWith(publicUrl)) return;
        try {
            String key = url.substring(publicUrl.length() + 1);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception ignored) {}
    }
}
