package exampleDataCreator;

import com.azure.core.credential.TokenCredential;
import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.KeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.Random;

/**
 * Set up by creating client-side encrypted blob in a new container using key vault. Running the ExampleDataCreator is
 * optional as long as if customer has a client-side encrypted blob ready to be migrated and a local key created for
 * reupload
 */
public class ExampleDataCreator {
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
     * Creates example container and blob, then uploads with client-side encryption with key vault
     **/
    public static void setup(String clientSecret, String tenantId, String clientId,
                             String storageAccount, String sharedKeyCred, String keyVaultUrl, String
            containerName, String blobName, String keyName, String keyWrapAlgorithm) {
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

        // Creating key client that allows access of key vault
        TokenCredential cred = new ClientSecretCredentialBuilder()
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .clientId(clientId)
                .build();
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(cred)
                .buildClient();
        // Creating an example RSA key in key vault
        KeyVaultKey rsaKey = keyClient.createRsaKey(new CreateRsaKeyOptions(keyName)
                .setExpiresOn(OffsetDateTime.now().plusYears(1))
                .setKeySize(2048));

        // Creating a blob client
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName);

        // Setting encryptedKeyClient with key vault key
        AsyncKeyEncryptionKey key = createAsyncKey(rsaKey, cred);
        EncryptedBlobClient encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(key, keyWrapAlgorithm)
                .blobClient(blobClient)
                .buildEncryptedBlobClient();

        // Uploading example blob with client-side encryption
        encryptedBlobClient.uploadFromFile("clientEncryptionToCPKNMigrationSamples\\" +
                "ClientSideKeyVaultKeyToCustomerProvidedKey\\src\\main\\java\\exampleCreation\\" + blobName, true);
    }

    public static void main(String[] args) {
        String clientId = null;
        String clientSecret = null;
        String tenantId = null;
        String storageAccount = null;
        String sharedKeyCred = null;
        String keyVaultUrl = null;
        String containerName = null;
        String blobName = null;
        String clientSideEncryptionKeyName = null;
        String localKeyFileName = null;
        String keyWrapAlgorithm = null;

        String pathToDir = "clientEncryptionToCPKNMigrationSamples\\" +
                "ClientSideKeyVaultKeyToCustomerProvidedKey\\src\\main\\java\\exampleCreation\\";

        // Extracting variables from config file
        try (InputStream input = new FileInputStream(pathToDir + "app.config")) {
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
            clientSideEncryptionKeyName = prop.getProperty("clientSideEncryptionKeyName");
            localKeyFileName = prop.getProperty("localKeyFileName");
            keyWrapAlgorithm = prop.getProperty("keyWrapAlgorithm");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Creating random local key and storing bytes into local file for later use in decrypting
        byte[] b = new byte[32];
        new Random().nextBytes(b);
        try {
            OutputStream os = new FileOutputStream(pathToDir + localKeyFileName);
            os.write(b);
            os.close();
        } catch (Exception e) {
            System.out.println("Exception in saving key: " + e);
        }

        // Setup where sample blob is client-side encrypted and uploaded to server
        setup(clientSecret, tenantId, clientId,
                storageAccount, sharedKeyCred, keyVaultUrl, containerName, blobName, clientSideEncryptionKeyName, keyWrapAlgorithm);
    }
}