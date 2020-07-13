import com.azure.core.credential.TokenCredential;
import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.KeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Downloads and decrypts blob using key vault, then reuploads using customer-managed keys through encryption scopes
 */
public class Migration {
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
     * Downloads client-side encrypted blob, decrypts with key vault, then stores in local file temporarily
     */
    private static void decryptClientSideKeyVaultKey(String storageAccount, String sharedKeyCred, String containerName,
                                                  String blobName, String blobSuffix, String keyVaultUrl, String keyname,
                                                  String path) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Setting encryptedKeyClient with key vault key
        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .blobName(blobName + blobSuffix)
                .buildClient();
        TokenCredential cred = new DefaultAzureCredentialBuilder().build();
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(cred)
                .buildClient();
        KeyVaultKey rsaKey = keyClient.getKey(keyname);
        EncryptedBlobClient encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(createAsyncKey(rsaKey, cred), KeyWrapAlgorithm.RSA_OAEP.toString())
                .blobClient(blobClient)
                .buildEncryptedBlobClient();

        // Downloading encrypted blob, blob is decrypted upon download
        String fileName = blobName + "Decrypted" + blobSuffix;
        encryptedBlobClient.downloadToFile(path + fileName);
    }

    /**
     * Reuploads blob with server-side encryption using a customer-managed key
     */
    private static void encryptCustomerManaged(String storageAccount, String sharedKeyCred, String containerName,
                                               String blobName, String blobSuffix,
                                               String encryptionScope, String path) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";
        String fileName = blobName + "Decrypted" + blobSuffix;
        // Creating blob client for reuploading
        BlobClientBuilder blobClientBuilder = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .encryptionScope(encryptionScope)
                .blobName(fileName);
        BlobClient blobClientDecrypted = blobClientBuilder.buildClient();

        // Uploading file to server
        blobClientDecrypted.uploadFromFile(path + fileName, true);
    }

    /**
     * Cleans up temp files created during decryption
     */
    private static void cleanup(String blobName, String blobSuffix, String path) {
        String fileName = blobName + "Decrypted" + blobSuffix;
        // Cleaning up by deleting local save of encrypted blob
        File localFile = new File(path + fileName);
        localFile.delete();
    }

    public static void main(String[] args) {
        String storageAccount = null;
        String sharedKeyCred = null;
        String keyVaultUrl = null;

        String pathToDir = "clientEncryptionToCPKNMigrationSamples\\ClientSideKeyVaultKeyToCustomerManagedKey\\" +
                "src\\main\\java\\setup\\";

        // Extracting variables from config file
        try (InputStream input = new FileInputStream(pathToDir + "app.config")) {
            Properties prop = new Properties();
            prop.load(input);
            storageAccount = prop.getProperty("storageAccount");
            sharedKeyCred = prop.getProperty("sharedKeyCred");
            keyVaultUrl = prop.getProperty("keyVaultUrl");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Setting names of container and blob that were created in setup
        String containerName = "containername";
        String blobName = "blobExample";
        String blobSuffix = ".txt";
        // Name for a key in key vault used for client-side encryption and name for an encryption scope
        String keyName = "keyName";
        String encryptionScope = "encryptionScopeName";

        // Decrypts sample blob then reuploads with server-side encryption using customer-managed keys
        decryptClientSideKeyVaultKey(storageAccount, sharedKeyCred, containerName, blobName, blobSuffix, keyVaultUrl, keyName, pathToDir);
        encryptCustomerManaged(storageAccount, sharedKeyCred, containerName, blobName, blobSuffix, encryptionScope, pathToDir);
        cleanup(blobName, blobSuffix, pathToDir);
    }
}
