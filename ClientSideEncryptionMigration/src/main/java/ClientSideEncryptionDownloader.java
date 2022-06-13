// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.azure.core.cryptography.AsyncKeyEncryptionKeyResolver;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptionVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;

public class ClientSideEncryptionDownloader {

    private final AsyncKeyEncryptionKeyResolver resolver;
    private final DownloadRetryOptions downloadRetryOptions;
    private final BlobRequestConditions requestConditions;
    private final boolean getRangeContentMd5;
    private final Duration timeout;
    private final Context context;
    private String keyId;
    private String keyWrapAlgorithm;

    public String getKeyId() {
        return this.keyId;
    }

    public String getKeyWrapAlgorithm() {
        return this.keyWrapAlgorithm;
    }

    ClientSideEncryptionDownloader(AsyncKeyEncryptionKeyResolver resolver, DownloadRetryOptions downloadRetryOptions,
            BlobRequestConditions requestConditions, boolean getRangeContentMd5, Duration timeout, Context context) {
        this.resolver = resolver;
        this.downloadRetryOptions = downloadRetryOptions;
        this.requestConditions = requestConditions;
        this.getRangeContentMd5 = getRangeContentMd5;
        this.timeout = timeout;
        this.context = context;
    }

    public boolean downloadV1ClientSideEncryptedBlobOrDefault(BlobClient blob, OutputStream destination) {
        try {
            new EncryptedBlobClientBuilder(EncryptionVersion.V1)
                    .blobClient(blob)
                    .keyResolver(this.resolver)
                    .buildEncryptedBlobClient()
                    .downloadStreamWithResponse(destination, null, downloadRetryOptions, requestConditions,
                            getRangeContentMd5, timeout, context);
        } catch (BlobStorageException e) {
            return false;
        }
        return true;
    }

    private boolean isClientSideEncryptedV1(BlobProperties properties) throws JsonProcessingException {
        String encryptionMetadata = properties.getMetadata().getOrDefault("encryptiondata", null);
        if (encryptionMetadata == null) {
            return false;
        }

        Map<String, String> objectMap = new ObjectMapper().readValue(encryptionMetadata,
                new TypeReference<Map<String, String>>() {
                });

        getKeyWrappingInfo(objectMap);

        String encryptionAgentStr = objectMap.get("EncryptionAgent");
        Map<String, String> encryptionAgentMap = new ObjectMapper().readValue(encryptionAgentStr,
                new TypeReference<Map<String, String>>() {
                });

        return encryptionAgentMap.get("Protocol").equals("1.0");
    }

    private void getKeyWrappingInfo(Map<String, String> encryptionDataMap) throws JsonProcessingException {
        String wrappedKeyString = encryptionDataMap.get("WrappedContentKey");
        Map<String, String> wrappedKeyMap = new ObjectMapper().readValue(wrappedKeyString,
                new TypeReference<Map<String, String>>() {
                });

        this.keyId = wrappedKeyMap.get("KeyId");
        this.keyWrapAlgorithm = wrappedKeyMap.get("Algorithm");
    }
}
