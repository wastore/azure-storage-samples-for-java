package exampleDataCreator;

import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.LocalKeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyOperation;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

/**
 * Set up by creating client-side encrypted blob in a new container using an example local key. Saves local key in local
 * file for decrpytion. Creates key vault key and generates encryption scope for reupload during decryption. Running the
 * ExampleDataCreator is optional as long as if customer has a client-side encrypted blob ready to be migrated and
 * encryption scope created
 */
public class ExampleDataCreator {
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
     * Creating encryption scope that allows access to key from key vault
     */
    private static void createKeyVaultEncryptionScope(String clientSecret, String tenantId, String clientId,
                                                      String keyVaultUrl, String keyName, String encryptionScope,
                                                      String storageAccount, String resourceGroup, String subscription) {
        // Creating key client that allows access of key vault
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new ClientSecretCredentialBuilder()
                        .clientSecret(clientSecret)
                        .tenantId(tenantId)
                        .clientId(clientId)
                        .build())
                .buildClient();

        // Create key for use in server-side encryption
        KeyVaultKey rsaKey = keyClient.createRsaKey(new CreateRsaKeyOptions(keyName)
                .setExpiresOn(OffsetDateTime.now().plusYears(1))
                .setKeySize(2048));
        String keyId = rsaKey.getId();

        // Template for command:
        // az storage account encryption-scope create --name <encryptionScopeName> -s Microsoft.KeyVault -u <keyID>
        //      --account-name <storageAccountName> -g <resourceGroupName> --subscription <subscriptionName>
        String command = "az storage account encryption-scope create --name " + encryptionScope + " -s " +
                "Microsoft.KeyVault -u " + keyId +
                " --account-name " + storageAccount + " -g " + resourceGroup + " --subscription " + subscription;

        // Running command line command to create encryption scope for given key
        Process p = null;
        try {
            p = new ProcessBuilder("cmd.exe", "/c", command).start();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        // Reading outputs from command line
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        try {
            while ((r.readLine()) != null) {}
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypts sample blob using local key provided and uploads to server
     */
    public static void setup(String storageAccount, String sharedKeyCred, String containerName, String blobName,
                                            AsyncKeyEncryptionKey key, String keyWrapAlgorithm) {
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
        encryptedBlobClient.uploadFromFile("clientEncryptionToCPKNMigrationSamples\\" +
                "ClientSideLocalKeyToCustomerManagedKey\\src\\main\\java\\exampleDataCreator\\" + blobName, true);
    }

    public static void main(String[] args) {
        String clientId = null;
        String clientSecret = null;
        String tenantId = null;
        String storageAccount = null;
        String sharedKeyCred = null;
        String keyVaultUrl = null;
        String resourceGroup = null;
        String subscription = null;
        String clientSideLocalKeyFileName = null;
        String containerName = null;
        String blobName = null;
        String serverSideEncryptionKeyName = null;
        String encryptionScope = null;
        String keyWrapAlgorithm = null;

        String pathToDir = "clientEncryptionToCPKNMigrationSamples\\" +
                "ClientSideLocalKeyToCustomerManagedKey\\src\\main\\java\\exampleDataCreator\\";

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
            resourceGroup = prop.getProperty("resourceGroup");
            subscription = prop.getProperty("subscription");
            clientSideLocalKeyFileName = prop.getProperty("clientSideLocalKeyFileName");
            containerName = prop.getProperty("containerName");
            blobName = prop.getProperty("blobName");
            serverSideEncryptionKeyName = prop.getProperty("serverSideEncryptionKeyName");
            encryptionScope = prop.getProperty("encryptionScope");
            keyWrapAlgorithm = prop.getProperty("keyWrapAlgorithm");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Creating random local key and storing bytes into local file for later use in decrypting
        byte[] b = new byte[32];
        new Random().nextBytes(b);
        AsyncKeyEncryptionKey key = createLocalKey(b);
        try {
            OutputStream os = new FileOutputStream(pathToDir + clientSideLocalKeyFileName);
            os.write(b);
            os.close();
        } catch (Exception e) {
            System.out.println("Exception in saving key: " + e);
        }

        // Create an example encryption scope to access given key that will allow for server-side encryption using
        // customer-managed keys
        createKeyVaultEncryptionScope(clientSecret, tenantId, clientId,
                keyVaultUrl, serverSideEncryptionKeyName, encryptionScope, storageAccount, resourceGroup, subscription);

        // Setup where sample blob is client-side encrypted and uploaded to server
        setup(storageAccount, sharedKeyCred, containerName, blobName, key, keyWrapAlgorithm);
    }
}
