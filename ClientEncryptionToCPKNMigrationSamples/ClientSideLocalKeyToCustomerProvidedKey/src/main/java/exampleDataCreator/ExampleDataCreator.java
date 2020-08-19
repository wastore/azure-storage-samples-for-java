package exampleDataCreator;

import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.security.keyvault.keys.cryptography.LocalKeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyOperation;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

/**
 * Set up by creating client-side encrypted blob in a new container using an example local key. Saves local key in local
 * file for decryption. Running the ExampleDataCreator is optional as long as if customer has a client-side encrypted blob
 * ready to be migrated and local key created for reupload
 */
public class ExampleDataCreator {
    public static void main(String[] args) throws IOException {
        String clientSideLocalKeyFileName = null;
        String storageAccount = null;
        String sharedKeyCred = null;
        String containerName = null;
        String blobName = null;
        String keyWrapAlgorithm = null;

        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path pathToDir = Paths.get(currentPath.toString(), "clientEncryptionToCPKNMigrationSamples",
                "ClientSideLocalKeyToCustomerProvidedKey", "src", "main", "java", "exampleDataCreator");
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
        keyWrapAlgorithm = prop.getProperty("keyWrapAlgorithm");

        String blobPath = Paths.get(pathToDir.toString(), blobName).toString();
        String localKeyPath = Paths.get(pathToDir.toString(), clientSideLocalKeyFileName).toString();

        // Creating random local key and insecurely storing bytes into local file for later use in decrypting
        byte[] b = new byte[32];
        new Random().nextBytes(b);
        AsyncKeyEncryptionKey key = createLocalKey(b);
        OutputStream os = new FileOutputStream(localKeyPath);
        os.write(b);
        os.close();

        // Setup where sample blob is client-side encrypted and uploaded to server
        setup(storageAccount, sharedKeyCred, containerName, blobName, key, keyWrapAlgorithm, blobPath);
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
     * Encrypts sample blob using local key provided and uploads to server
     */
    public static void setup(String storageAccount, String sharedKeyCred, String containerName, String blobName,
                             AsyncKeyEncryptionKey key, String keyWrapAlgorithm, String blobPath) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Creating a BlobServiceClient that allows us to perform container and blob operations, given our storage
        // account URL and shared key credential
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .buildClient();

        // Creating client referencing to-be-created container, and then creating it
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!blobContainerClient.exists()) {
            blobContainerClient.create();
        }

        // Creating a blob client
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

        // Setting encryptedKeyClient
        EncryptedBlobClient encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(key, keyWrapAlgorithm)
                .blobClient(blobClient)
                .buildEncryptedBlobClient();

        // Uploading example blob with client-side encryption
        encryptedBlobClient.uploadFromFile(blobPath, true);
    }
}
