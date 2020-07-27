import com.azure.core.credential.TokenCredential;
import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.KeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.CustomerProvidedKey;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Downloads and decrypts blob using key vault, creates an example local key, and reuploads blob with customer-provided
 * key
 */
public class Migration {
    public static void main(String[] args) throws IOException {
        String clientId = null;
        String clientSecret = null;
        String tenantId = null;
        String storageAccount = null;
        String sharedKeyCred = null;
        String keyVaultUrl = null;
        String containerName = null;
        String blobName = null;
        String blobNameAfterMigration = null;
        String clientSideEncryptionKeyName = null;
        String localKeyFileName = null;
        String keyWrapAlgorithm = null;

        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path pathToDir = Paths.get(currentPath.toString(), "clientEncryptionToCPKNMigrationSamples",
                "ClientSideKeyVaultKeyToCustomerProvidedKey", "src", "main", "java", "exampleDataCreator");
        String configPath = Paths.get(pathToDir.toString(), "app.config").toString();

        // Extracting variables from config file
        InputStream input = new FileInputStream(configPath);
        Properties prop = new Properties();
        prop.load(input);
        clientSecret = prop.getProperty("clientSecret");
        clientId = prop.getProperty("clientId");
        tenantId = prop.getProperty("tenantId");
        storageAccount = prop.getProperty("storageAccount");
        sharedKeyCred = prop.getProperty("sharedKeyCred");
        keyVaultUrl = prop.getProperty("keyVaultUrl");
        containerName = prop.getProperty("containerName");
        blobName = prop.getProperty("blobName");
        blobNameAfterMigration = prop.getProperty("blobNameAfterMigration");
        clientSideEncryptionKeyName = prop.getProperty("clientSideEncryptionKeyName");
        localKeyFileName = prop.getProperty("localKeyFileName");
        keyWrapAlgorithm = prop.getProperty("keyWrapAlgorithm");

        String blobAfterMigrationPath = Paths.get(pathToDir.toString(), blobNameAfterMigration).toString();

        // File containing key
        String serverLocalKeyPath = Paths.get(pathToDir.toString(), localKeyFileName).toString();

        // Extracting key from file
        byte[] b = extractLocalKey(serverLocalKeyPath);

        // Decrypts sample blob then reuploads with server-side encryption using Microsoft-managed keys
        try {
            decryptClientSideKeyVaultKey(clientSecret, tenantId, clientId,
                    storageAccount, sharedKeyCred, containerName, blobName, keyVaultUrl,
                    clientSideEncryptionKeyName, keyWrapAlgorithm, blobAfterMigrationPath);
            encryptCustomerProvidedKey(storageAccount, sharedKeyCred, containerName, blobNameAfterMigration,
                    new CustomerProvidedKey(b), blobAfterMigrationPath);
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
     * Downloads client-side encrypted blob, decrypts with key vault, then stores in local file temporarily
     */
    private static void decryptClientSideKeyVaultKey(String clientSecret, String tenantId, String clientId,
                                                     String storageAccount, String sharedKeyCred, String containerName,
                                                     String blobName, String keyVaultUrl,
                                                     String keyname, String keyWrapAlgorithm, String path){
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Setting encryptedKeyClient with key vault key
        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .blobName(blobName)
                .buildClient();
        TokenCredential cred = new ClientSecretCredentialBuilder()
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .clientId(clientId)
                .build();
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(cred)
                .buildClient();
        KeyVaultKey rsaKey = keyClient.getKey(keyname);
        EncryptedBlobClient encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(createAsyncKey(rsaKey, cred), keyWrapAlgorithm)
                .blobClient(blobClient)
                .buildEncryptedBlobClient();

        // Downloading encrypted blob, blob is decrypted upon download
        encryptedBlobClient.downloadToFile(path);
    }

    /**
     * Creates an Async key for client-side encryption
     */
    private static AsyncKeyEncryptionKey createAsyncKey(KeyVaultKey key, TokenCredential cred) {
        AsyncKeyEncryptionKey akek = new KeyEncryptionKeyClientBuilder()
                .credential(cred)
                .buildAsyncKeyEncryptionKey(key.getId())
                .block();
        return akek;
    }

    /**
     * Reuploads blob with server-side encryption using a customer-provided key
     */
    private static void encryptCustomerProvidedKey(String storageAccount, String sharedKeyCred, String containerName,
                                                   String blobDecryptName, CustomerProvidedKey localKey, String path) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";
        // Creating blob client for reuploading
        BlobClientBuilder blobClientBuilder = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .customerProvidedKey(localKey)
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