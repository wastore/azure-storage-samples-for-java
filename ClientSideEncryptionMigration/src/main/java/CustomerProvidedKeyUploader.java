// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.CustomerProvidedKey;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public class CustomerProvidedKeyUploader implements EncryptionUploader {

    private final CustomerProvidedKey cpk;

    CustomerProvidedKeyUploader(CustomerProvidedKey cpk) {
        this.cpk = cpk;
    }

    @Override
    public void uploadBlobWithEncryption(BlobClient blobClient, InputStream data, String keyId, String keyWrapAlgorithm,
            BlobHttpHeaders headers, Map<String, String> metadata, Map<String, String> tags, AccessTier tier,
            BlobRequestConditions requestConditions, boolean computeMd5,
            ParallelTransferOptions parallelTransferOptions, Duration timeout, Context context) {
        blobClient.getCustomerProvidedKeyClient(cpk)
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