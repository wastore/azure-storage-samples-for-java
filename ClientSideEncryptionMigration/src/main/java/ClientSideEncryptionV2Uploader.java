// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.core.cryptography.AsyncKeyEncryptionKeyResolver;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptionVersion;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public class ClientSideEncryptionV2Uploader implements EncryptionUploader {

    private final AsyncKeyEncryptionKeyResolver resolver;

    ClientSideEncryptionV2Uploader(AsyncKeyEncryptionKeyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void uploadBlobWithEncryption(BlobClient blobClient, InputStream data, String keyid, String keyWrapAlgorithm,
            BlobHttpHeaders headers, Map<String, String> metadata, Map<String, String> tags, AccessTier tier,
            BlobRequestConditions requestConditions, boolean computeMd5,
            ParallelTransferOptions parallelTransferOptions, Duration timeout, Context context) {
        new EncryptedBlobClientBuilder(EncryptionVersion.V2)
                .blobClient(blobClient)
                .key(resolver.buildAsyncKeyEncryptionKey(keyid).block(), keyWrapAlgorithm)
                .buildEncryptedBlobClient()
                .uploadWithResponse(new BlobParallelUploadOptions(data)
                                .setMetadata(metadata)
                                .setHeaders(headers)
                                .setTags(tags)
                                .setTier(tier)
                                .setRequestConditions(requestConditions)
                                .setComputeMd5(computeMd5)
                                .setParallelTransferOptions(parallelTransferOptions),
                        timeout, context);
    }
}
