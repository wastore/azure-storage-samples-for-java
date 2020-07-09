package clientEncryptionToCPKNMigrationSamples.localKeyClientSideToCustomerManagedServerSide.setup;

import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.LocalKeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
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

public class setup {
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

    private static void createEncryptionScope(String keyVaultUrl, String keyName, String encryptionScope,
                                              String storageAccount, String resourceGroup, String subscription) {
        // Creating key client that allows access of key vault
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        // Create key for use in server-side encryption
        KeyVaultKey rsaKey = keyClient.createRsaKey(new CreateRsaKeyOptions(keyName)
                .setExpiresOn(OffsetDateTime.now().plusYears(1))
                .setKeySize(2048));
        String keyId = rsaKey.getId();
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
        String line = null;
        while (true) {
            try {
                line = r.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (line == null) {
                break;
            }
        }
    }

    public static void setup(String storageAccount, String sharedKeyCred, String containerName, String blobName,
                                            String blobSuffix, AsyncKeyEncryptionKey key) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Creating a BlobServiceClient that allows us to perform container and blob operations, given our storage
        // account URL and shared key credential
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .buildClient();

        // Creating client referencing to-be-created container, and then creating it
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
        blobContainerClient.create();

        // Creating a blob client
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName + blobSuffix);

        // Setting encryptedKeyClient
        EncryptedBlobClient encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(key, KeyWrapAlgorithm.A256KW.toString())
                .blobClient(blobClient)
                .buildEncryptedBlobClient();

        // Uploading example blob with client-side encryption
        String fileName = blobName + blobSuffix;
        encryptedBlobClient.uploadFromFile(".\\src\\main\\java\\" + fileName);
    }

    public static void main(String[] args) {
        String storageAccount = null;
        String sharedKeyCred = null;
        String keyVaultUrl = null;
        String resourceGroup = null;
        String subscription = null;

        String pathToDir = ".\\src\\main\\java\\clientEncryptionToCPKNMigrationSamples\\" +
                "localKeyClientSideToCustomerManagedServerSide\\setup\\";

        // Extracting variables from config file
        try (InputStream input = new FileInputStream(pathToDir + "app.config")) {
            Properties prop = new Properties();
            prop.load(input);
            storageAccount = prop.getProperty("storageAccount");
            sharedKeyCred = prop.getProperty("sharedKeyCred");
            keyVaultUrl = prop.getProperty("keyVaultUrl");
            resourceGroup = prop.getProperty("resourceGroup");
            subscription = prop.getProperty("subscription");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Setting names of container and blob that will be created later in the code. Note that container
        // names are all lowercase and both containers and blobs cannot have underscores
        String containerName = "containername";
        String blobName = "blobExample";
        String blobSuffix = ".txt";
        // Name for a key in key vault that will be generated and name for encryption scope to access key
        String keyName = "keyName";
        String encryptionScope = "encryptionScopeName";

        // Creating random local key and storing bytes into local file for later use in decrypting
        byte[] b = new byte[32];
        new Random().nextBytes(b);
        AsyncKeyEncryptionKey key = createLocalKey(b);
        try {
            OutputStream os = new FileOutputStream(pathToDir + "byteKeyInsecure.txt");
            os.write(b);
            os.close();
        } catch (Exception e) {
            System.out.println("Exception in saving key: " + e);
        }

        // Create an example encryption scope that will allow for server-side encryption using customer-managed keys
        createEncryptionScope(keyVaultUrl, keyName, encryptionScope, storageAccount, resourceGroup, subscription);

        // Setup where sample blob is client-side encrypted and uploaded to server
        setup(storageAccount, sharedKeyCred, containerName, blobName, blobSuffix, key);
    }
}