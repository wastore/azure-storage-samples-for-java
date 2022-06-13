// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.ParallelTransferOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;

public class DataMigrator {
    private ClientSideEncryptionDownloader downloader;
    private EncryptionUploader uploader;
    private BlobRequestConditions uploadRequestConditions;
    private boolean uploadComputeMd5;
    private ParallelTransferOptions uploadParallelTransferOptions;
    private Duration uploadTimeout;
    private Context uploadContext;

    public DataMigrator(ClientSideEncryptionDownloader downloader, EncryptionUploader uploader,
            BlobRequestConditions uploadRequestConditions, boolean uploadComputeMd5,
            ParallelTransferOptions uploadParallelTransferOptions, Duration uploadTimeout, Context uploadContext) {
        this.downloader = downloader;
        this.uploader = uploader;
        this.uploadRequestConditions = uploadRequestConditions;
        this.uploadComputeMd5 = uploadComputeMd5;
        this.uploadParallelTransferOptions = uploadParallelTransferOptions;
        this.uploadTimeout = uploadTimeout;
        this.uploadContext = uploadContext;
    }

    public boolean migrateBlobIfV1ClientSideEncrypted(BlobContainerClient containerClient, BlobItem blobItem) {
        ByteArrayOutputStream plaintextHolder = new ByteArrayOutputStream();
        BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());
        boolean downloaded = this.downloader.downloadV1ClientSideEncryptedBlobOrDefault(blobClient, plaintextHolder);
        if (!downloaded) {
            return false;
        }

        blobItem.getMetadata().remove("encryptiondata");

        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setCacheControl(blobItem.getProperties().getCacheControl())
                .setContentDisposition(blobItem.getProperties().getContentDisposition())
                .setContentEncoding(blobItem.getProperties().getContentEncoding())
                .setContentLanguage(blobItem.getProperties().getContentLanguage())
                .setContentType(blobItem.getProperties().getContentType())
                .setContentMd5(blobItem.getProperties().getContentMd5());

        this.uploader.uploadBlobWithEncryption(blobClient, new ByteArrayInputStream(plaintextHolder.toByteArray()),
                this.downloader.getKeyId(), this.downloader.getKeyWrapAlgorithm(), headers, blobItem.getMetadata(),
                blobItem.getTags(), blobItem.getProperties().getAccessTier(), this.uploadRequestConditions,
                this.uploadComputeMd5, this.uploadParallelTransferOptions, this.uploadTimeout, this.uploadContext);

        return true;
    }

    public void migrateBlobPrefix(BlobContainerClient containerClient, String prefix, String delimiter,
            boolean recursive) {
        long blobsMigrated = 0;
        long blobsIgnored = 0;

        for (BlobItem blobItem : containerClient.listBlobsByHierarchy(delimiter, new ListBlobsOptions()
                .setPrefix(prefix).setDetails(new BlobListDetails().setRetrieveMetadata(true).setRetrieveTags(true)),
                null)) {
            if (!blobItem.isPrefix()) {
                if (migrateBlobIfV1ClientSideEncrypted(containerClient, blobItem)) {
                    blobsMigrated++;
                } else {
                    blobsIgnored ++;
                }
            } else if (recursive) {
                migrateBlobPrefix(containerClient, blobItem.getName(), delimiter, recursive);
                // Can't really return two items like in .net. Alternatives?
            }
        }
    }

    public void migrateBlobContainer(BlobContainerClient containerClient) {
        long blobsMigrated = 0;
        long blobsIgnored = 0;

        for(BlobItem item : containerClient.listBlobs(new ListBlobsOptions().setDetails(new BlobListDetails()
                .setRetrieveTags(true).setRetrieveMetadata(true)), null)) {
            if (migrateBlobIfV1ClientSideEncrypted(containerClient, item)) {
                blobsMigrated++;
            } else {
                blobsIgnored ++;
            }
        }
    }
}
