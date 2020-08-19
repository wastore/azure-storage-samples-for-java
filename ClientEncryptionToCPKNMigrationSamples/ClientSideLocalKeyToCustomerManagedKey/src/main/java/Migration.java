import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.security.keyvault.keys.cryptography.LocalKeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyOperation;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

/**
 * Downloads and decrypts blob using local key found in local file, then reuploads using customer-managed keys
 * through encryption scopes
 */
public class Migration {
    public static void main(String[] args) throws IOException {
        String clientSideLocalKeyFileName = null;
        String storageAccount = null;
        String sharedKeyCred = null;
        String containerName = null;
        String blobName = null;
        String blobNameAfterMigration = null;
        String encryptionScope = null;
        String keyWrapAlgorithm = null;

        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path pathToDir = Paths.get(currentPath.toString(), "clientEncryptionToCPKNMigrationSamples",
                "ClientSideLocalKeyToCustomerManagedKey", "src", "main", "java", "exampleDataCreator");
        String configPath = Paths.get(pathToDir.toString(), "app.config").toString();

        // Extracting variables from config file
        InputStream input = new FileInputStream(configPath);
        Properties prop = new Properties();
        prop.load(input);
        clientSideLocalKeyFileName = prop.getProperty("clientSideLocalKeyFileName");
        storageAccount = prop.getProperty("storageAccount");
        sharedKeyCred = prop.getProperty("sharedKeyCred");
        containerName = prop.getProperty("containerName");
        blobName = prop.getProperty("blobName");
        blobNameAfterMigration = prop.getProperty("blobNameAfterMigration");
        encryptionScope = prop.getProperty("encryptionScope");
        keyWrapAlgorithm = prop.getProperty("keyWrapAlgorithm");

        String blobAfterMigrationPath = Paths.get(pathToDir.toString(), blobNameAfterMigration).toString();
        String localKeyPath = Paths.get(pathToDir.toString(), clientSideLocalKeyFileName).toString();

        // Extracting key from file
        byte[] b = extractLocalKey(localKeyPath);
        AsyncKeyEncryptionKey key = createLocalKey(b);
        // Decrypts sample blob then reuploads with server-side encryption using customer-managed keys
        try {
            decryptClientSideLocalKey(storageAccount, sharedKeyCred, containerName, blobName, key,
                    keyWrapAlgorithm, blobAfterMigrationPath);
            encryptCustomerManagedKey(storageAccount, sharedKeyCred, containerName, blobNameAfterMigration, encryptionScope,
                    blobAfterMigrationPath);
        } finally {
            cleanup(blobAfterMigrationPath);
        }
    }

    /**
     * Extracts local key by accessing file that stores bytes. Returns the key in the form of a byte array.
     * This method should be modified because it is very insecure.
     */
    private static byte[] extractLocalKey(String filename) throws IOException {
        return Files.readAllBytes(Paths.get(filename));
    }

    /**
     * Creates a random, not secure local key to be used in client-side encryption
     */
    private static AsyncKeyEncryptionKey createLocalKey(byte[] byteKey) {
        JsonWebKey localKey = JsonWebKey.fromAes(new SecretKeySpec(byteKey, "AES"),
                Arrays.asList(KeyOperation.WRAP_KEY, KeyOperation.UNWRAP_KEY))
                .setId("my-id");
        AsyncKeyEncryptionKey akek = new LocalKeyEncryptionKeyClientBuilder()
                .buildAsyncKeyEncryptionKey(localKey).block();
        return akek;
    }

    /**
     * Downloads client-side encrypted blob, decrypts with a local key, then stores in local file temporarily
     */
    private static void decryptClientSideLocalKey(String storageAccount, String sharedKeyCred, String containerName,
                                                  String blobName, AsyncKeyEncryptionKey key,
                                                  String keyWrapAlgorithm, String path) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";
        // Creating encrypted blob client to download blob
        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .blobName(blobName)
                .buildClient();
        EncryptedBlobClient encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(key, keyWrapAlgorithm)
                .blobClient(blobClient)
                .buildEncryptedBlobClient();

        // Downloading encrypted blob, blob is decrypted upon download
        encryptedBlobClient.downloadToFile(path);
    }

    /**
     * Reuploads blob with server-side encryption using a customer-managed key
     */
    private static void encryptCustomerManagedKey(String storageAccount, String sharedKeyCred, String containerName,
                                                  String blobDecryptName, String encryptionScope, String path) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";
        // Creating blob client for reuploading
        BlobClientBuilder blobClientBuilder = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .encryptionScope(encryptionScope)
                .blobName(blobDecryptName);

        BlobClient blobClientDecrypted = blobClientBuilder.buildClient();

        // Uploading file to server
        blobClientDecrypted.uploadFromFile(path, true);
    }

    /**
     * Cleans up temp files created during decryption
     */
    private static void cleanup(String path) {
        // Cleaning up by deleting local save of encrypted blob
        File localFile = new File(path);
        localFile.delete();
    }
}
