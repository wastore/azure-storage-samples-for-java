import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ParallelTransferOptions;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public interface EncryptionUploader {
    void uploadBlobWithEncryption(BlobClient blobClient, InputStream data, String keyId, String keyWrapAlgorithm,
            BlobHttpHeaders headers, Map<String, String> metadata, Map<String, String> tags, AccessTier tier,
            BlobRequestConditions requestConditions, boolean computeMd5,
            ParallelTransferOptions parallelTransferOptions, Duration timeout, Context context);
}
