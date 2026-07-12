package com.fuoverflow.common.storage;

import com.fuoverflow.common.config.ObjectStorageProperties;
import com.fuoverflow.common.config.UploadProperties;
import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class S3CompatibleObjectStorage implements ObjectStorage {
    private final ObjectStorageProperties.S3 s3;
    private final UploadProperties uploadProperties;
    private final S3Client client;

    public S3CompatibleObjectStorage(ObjectStorageProperties properties, UploadProperties uploadProperties) {
        this.s3 = requireS3(properties);
        this.uploadProperties = uploadProperties;
        this.client = buildClient(this.s3);
    }

    @Override
    public StoredObject storeImage(MultipartFile file, String logicalFolder) {
        return storeFile(file, logicalFolder, FileKind.IMAGE);
    }

    @Override
    public StoredObject storeFile(MultipartFile file, String logicalFolder, FileKind kind) {
        ObjectStorageSupport.validateFile(file, kind, uploadProperties);
        String contentType = ObjectStorageSupport.requireContentType(file);
        String objectKey = ObjectStorageSupport.buildObjectKey(
                logicalFolder, contentType, file.getOriginalFilename());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        try {
            client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception ex) {
            throw new BadRequestException("FILE_STORE_FAILED", "Failed to store uploaded file");
        }
        String publicUrl = kind == FileKind.IMAGE ? buildPublicUrl(objectKey) : null;
        return new StoredObject(objectKey, publicUrl);
    }

    @Override
    public void storeBytes(byte[] data, String objectKey, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        client.putObject(request, RequestBody.fromBytes(data));
    }

    @Override
    public InputStream openStream(String objectKeyOrLegacyReference) {
        String objectKey = normalizeToObjectKey(objectKeyOrLegacyReference);
        if (objectKey == null) {
            throw new BadRequestException("FILE_NOT_FOUND", "File not found");
        }
        try {
            ResponseInputStream<?> stream = client.getObject(GetObjectRequest.builder()
                    .bucket(s3.bucket())
                    .key(objectKey)
                    .build());
            return stream;
        } catch (NoSuchKeyException ex) {
            throw new BadRequestException("FILE_NOT_FOUND", "File not found");
        } catch (S3Exception ex) {
            throw new BadRequestException("FILE_READ_FAILED", "Unable to read stored file");
        }
    }

    @Override
    public void delete(String objectKeyOrLegacyReference) {
        String objectKey = normalizeToObjectKey(objectKeyOrLegacyReference);
        if (objectKey == null) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3.bucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception ignored) {
            // Best-effort cleanup.
        }
    }

    @Override
    public String resolvePublicUrl(String objectKeyOrLegacyReference) {
        String objectKey = normalizeToObjectKey(objectKeyOrLegacyReference);
        if (objectKey == null) {
            return null;
        }
        return buildPublicUrl(objectKey);
    }

    @Override
    public String normalizeToObjectKey(String storedReference) {
        String value = ObjectStorageSupport.blankToNull(storedReference);
        if (value == null) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return extractKeyFromPublicUrl(value);
        }
        if (value.startsWith(ObjectStorageSupport.LEGACY_UPLOADS_PREFIX)) {
            return value.substring(ObjectStorageSupport.LEGACY_UPLOADS_PREFIX.length());
        }
        return value;
    }

    public void ensureBucketExists() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(s3.bucket()).build());
        } catch (NoSuchBucketException ex) {
            client.createBucket(CreateBucketRequest.builder().bucket(s3.bucket()).build());
        } catch (S3Exception ex) {
            throw new BadRequestException("STORAGE_BUCKET_UNAVAILABLE", "Object storage bucket is not available");
        }
    }

    private String extractKeyFromPublicUrl(String publicUrl) {
        String base = s3.publicBaseUrl().replaceAll("/+$", "");
        String prefix = base + "/" + s3.bucket() + "/";
        if (publicUrl.startsWith(prefix)) {
            return publicUrl.substring(prefix.length());
        }
        int bucketSegment = publicUrl.indexOf("/" + s3.bucket() + "/");
        if (bucketSegment >= 0) {
            return publicUrl.substring(bucketSegment + s3.bucket().length() + 2);
        }
        return publicUrl;
    }

    String buildPublicUrl(String objectKey) {
        String base = s3.publicBaseUrl().replaceAll("/+$", "");
        if (s3.pathStyleAccessOrDefault()) {
            return base + "/" + s3.bucket() + "/" + objectKey;
        }
        URI endpoint = URI.create(base);
        String host = endpoint.getHost();
        String scheme = endpoint.getScheme();
        return scheme + "://" + s3.bucket() + "." + host + "/" + objectKey;
    }

    private static ObjectStorageProperties.S3 requireS3(ObjectStorageProperties properties) {
        ObjectStorageProperties.S3 s3 = properties.s3();
        if (s3 == null
                || isBlank(s3.endpoint())
                || isBlank(s3.bucket())
                || isBlank(s3.accessKey())
                || isBlank(s3.secretKey())
                || isBlank(s3.publicBaseUrl())) {
            throw new BadRequestException("STORAGE_NOT_CONFIGURED", "S3-compatible storage is not configured");
        }
        return s3;
    }

    private static S3Client buildClient(ObjectStorageProperties.S3 s3) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(isBlank(s3.region()) ? "us-east-1" : s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                .endpointOverride(URI.create(s3.endpoint()));
        if (s3.pathStyleAccessOrDefault()) {
            builder.forcePathStyle(true);
        }
        return builder.build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
