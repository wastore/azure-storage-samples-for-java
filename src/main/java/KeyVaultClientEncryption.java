import com.azure.core.credential.TokenCredential;
import com.azure.core.cryptography.AsyncKeyEncryptionKey;

import com.azure.core.cryptography.KeyEncryptionKey;
import com.azure.identity.DefaultAzureCredentialBuilder;

import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.KeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.function.BiFunction;

public class KeyVaultClientEncryption {

    public KeyVaultClientEncryption() {}

    /**
     * Creates an Async key for client-side encryption
     */
    private static AsyncKeyEncryptionKey createAsyncKey(KeyVaultKey key, TokenCredential cred) {
        KeyEncryptionKey akek = new KeyEncryptionKeyClientBuilder()
                .credential(cred)
                .buildKeyEncryptionKey(key.getId());

        String keyId = akek.getKeyId();
        BiFunction<String, byte[], byte[]> wrapKey = akek::wrapKey;
        BiFunction<String, byte[], byte[]> unWrapKey = akek::unwrapKey;

        return new AsyncKeyEncryptionKey() {
            @Override
            public Mono<String> getKeyId() {
                return Mono.just(keyId);
            }

            @Override
            public Mono<byte[]> wrapKey(String s, byte[] bytes) {
                return Mono.just(wrapKey.apply(s, bytes));
            }

            @Override
            public Mono<byte[]> unwrapKey(String s, byte[] bytes) {
                return Mono.defer(() ->Mono.just(unWrapKey.apply(s, bytes)));
            }
        };
    }

    private static void createEncryptionScope(String keyVaultUrl, String keyName, String encryptionScope,
                                              String storageAccount, String resourceGroup, String subscription) {
        // Creating key client that allows access of key vault
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        // Get key for decryption using keyClient
        KeyVaultKey key = keyClient.getKey(keyName);
        String keyId = key.getId();
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

    /**
     * Creates example container and blob, then uploads with client-side encryption with key vault
     **/
    public static EncryptedBlobClient setup(String storageAccount, String sharedKeyCred, String keyVaultUrl, String
            containerName, String blobName, String blobSuffix, String keyName) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";
        String fileName = blobName + blobSuffix;

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
        TokenCredential cred = new DefaultAzureCredentialBuilder().build();
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(cred)
                .buildClient();
        // Creating an example RSA key in key vault
        KeyVaultKey rsaKey = keyClient.createRsaKey(new CreateRsaKeyOptions(keyName)
                .setExpiresOn(OffsetDateTime.now().plusYears(1))
                .setKeySize(2048));

        // Creating a blob client
        BlobClient blobClient = blobContainerClient.getBlobClient(blobName + blobSuffix);

        // Setting encryptedKeyClient with key vault key
        EncryptedBlobClient encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(createAsyncKey(rsaKey, cred), KeyWrapAlgorithm.RSA_OAEP.toString())
                .blobClient(blobClient)
                .buildEncryptedBlobClient();

        // Uploading example blob with client-side encryption
        encryptedBlobClient.uploadFromFile(".\\src\\main\\java\\" + fileName, true);
        fileName = blobName + "Decrypted" + blobSuffix;
        encryptedBlobClient.downloadToFile(".\\src\\main\\java\\" + fileName);
        return encryptedBlobClient;
    }

    /**
     * Downloads client-side encrypted blob, decrypts with key vault, then reuploads with server-side encryption that
     * either uses Microsoft or customer-managed keys
     **/
    public static void decryptReupload(EncryptedBlobClient encryptedBlobClient, String storageAccount, String sharedKeyCred, String containerName,
                                String blobName, String blobSuffix, String encryptionScope) {
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Downloading encrypted blob, blob is decrypted upon download
        String fileName = blobName + "Decrypted" + blobSuffix;
        encryptedBlobClient.downloadToFile(".\\src\\main\\java\\" + fileName);

        // Creating blob client for reuploading
        BlobClientBuilder blobClientBuilder = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .blobName(fileName);

        // If using customer-managed keys, set the encryption scope
        if (encryptionScope != null) {
            String CPKfileName = blobName + "DecryptedCPK" + blobSuffix;
            blobClientBuilder = blobClientBuilder
                    .encryptionScope(encryptionScope)
                    .blobName(CPKfileName);
        }
        BlobClient blobClientDecrypted = blobClientBuilder.buildClient();

        // Uploading file to server
        blobClientDecrypted.uploadFromFile(".\\src\\main\\java\\" + fileName);

        // Cleaning up by deleting local save of encrypted blob
        File localFile = new File(".\\src\\main\\java\\" + fileName);
        localFile.delete();
    }


    /**
     * This sample will show client-side decryption using a key from key vault, and then upload two files with server-side
     * encryption. One will be encrypted with Microsoft-managed keys, and the other by customer-managed keys with
     * key vault through the creation of an encryption scope
     */
    public static void main(String[] args) {
        // Make sure that environmental variables are set to contain CLIENT_ID, CLIENT_SECRET, and TENANT_ID, and key
        // vault url obtained through creating service principal, as well as storage account, resource group, and
        // subscription names and that service principal has access to key vault.

        String sharedKeyCred = System.getenv("sharedKeyCred");
        String keyVaultUrl = System.getenv("keyVaultUrl");
        String storageAccount = System.getenv("storageAccount");
        String resourceGroup = System.getenv("resourceGroup");
        String subscription = System.getenv("subscription");


        // Setting names of container, blob, and key that will be created later in the code. Note that container
        // names are all lowercase and both containers and blobs cannot have underscores
        String containerName = "containername";
        String blobName = "blobExample";
        String blobSuffix = ".txt";
        String keyName = "keyName";
        String encryptionScope = "encryptionScopeName";

        // Setup where sample blob is client-side encrypted and uploaded to server
        EncryptedBlobClient blobClientEncrypted = setup(storageAccount, sharedKeyCred, keyVaultUrl, containerName, blobName, blobSuffix, keyName);

        // Decrypts sample blob then reuploads with server-side encryption using Microsoft-managed keys
        decryptReupload(blobClientEncrypted, storageAccount, sharedKeyCred, containerName, blobName, blobSuffix, null);

        // Create an example encryption scope that will allow for server-side encryption using customer-managed keys
        createEncryptionScope(keyVaultUrl, keyName, encryptionScope, storageAccount, resourceGroup, subscription);
        // Decrypts sample blob then reuploads with server-side encryption using customer-managed keys using
        // encryption scope
        decryptReupload(blobClientEncrypted, storageAccount, sharedKeyCred, containerName, blobName, blobSuffix, encryptionScope);

    }
}
