import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.security.keyvault.keys.cryptography.LocalKeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyOperation;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.CustomerProvidedKey;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.util.Arrays;
import java.util.Random;


public class LocalKeyClientEncryption {

    public LocalKeyClientEncryption(){}

    /**
     * Creates an example random but not secure customer provided key to be used in server-side encryption
     */
    private static CustomerProvidedKey createCPK(){
        byte[] b = new byte[32];
        new Random().nextBytes(b);
        CustomerProvidedKey serverKey = new CustomerProvidedKey(b);
        return serverKey;
    }

    /**
     * Creates a random, not secure local key to be used in client-side encryption
     */
    private static AsyncKeyEncryptionKey createLocalKey(){
        byte[] byteKey = new byte[32];
        new Random().nextBytes(byteKey);
        JsonWebKey localKey = JsonWebKey.fromAes(new SecretKeySpec(byteKey, "AES"),
                Arrays.asList(KeyOperation.WRAP_KEY, KeyOperation.UNWRAP_KEY))
                .setId("my-id");
        AsyncKeyEncryptionKey akek = new LocalKeyEncryptionKeyClientBuilder()
                .buildAsyncKeyEncryptionKey(localKey).block();
        return akek;
    }

    /**
     * Sets local key client with in helper object to be used in decrpytion
     */
    private static EncryptedBlobClient createLocalKeyClient(AsyncKeyEncryptionKey key, BlobClient blobClient){
        return new EncryptedBlobClientBuilder()
                .key(key, KeyWrapAlgorithm.A256KW.toString())
                .blobClient(blobClient)
                .buildEncryptedBlobClient();
    }

    /**
     * Creates example container and blob, then uploads with client-side encryption with local keys
     */
    public static BlobClient setup(String storageAccount, String sharedKeyCred, String containerName, String blobName,
                                   String blobSuffix, AsyncKeyEncryptionKey key){
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
        EncryptedBlobClient encryptedBlobClient = createLocalKeyClient(key, blobClient);

        // Uploading example blob with client-side encryption
        String fileName = blobName + blobSuffix;
        encryptedBlobClient.uploadFromFile(".\\src\\main\\java\\" + fileName);
        return blobClient;
    }

    /**
     * Downloads client-side encrypted blob, decrypts with local key, then reuploads with server-side encryption
     */
    public static void decryptReupload(BlobClient blobClient, String storageAccount, String sharedKeyCred, String containerName, String blobName,
                                String blobSuffix, AsyncKeyEncryptionKey key, CustomerProvidedKey serverKey){
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Set blob encryption client
        EncryptedBlobClient encryptedBlobClient = createLocalKeyClient(key, blobClient);

        // Downloading encrypted blob, blob is decrypted upon download
        String fileName = blobName + "Decrypted" + blobSuffix;
        encryptedBlobClient.downloadToFile(".\\src\\main\\java\\" + fileName);

        // Creating blob client for decryption and reuploading
        BlobClientBuilder blobClientBuilder = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .blobName(fileName);

        // Checking to see if local key is provided to use customer-managed key for server encryption
        if (serverKey != null){
            String CPKfileName = blobName + "DecryptedCPK" + blobSuffix;
            blobClientBuilder = blobClientBuilder
                    .customerProvidedKey(serverKey)
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
     * This sample will show client-side decryption using a local key, and then upload two files with server-side
     * encryption. One will be encrypted with Microsoft-managed keys, and the other by customer-provided keys.
     */
    public static void main(String[] args){
        String sharedKeyCred = System.getenv("sharedKeyCred");
        String storageAccount = System.getenv("storageAccount");

        // Setting names of container and blob that will be created later in the code. Note that container
        // names are all lowercase and both containers and blobs cannot have underscores
        String containerName = "containername";
        String blobName = "blobExample";
        String blobSuffix = ".txt";

        AsyncKeyEncryptionKey key = createLocalKey();

        // Setup where sample blob is client-side encrypted and uploaded to server
        BlobClient blobClientEncrypted = setup(storageAccount, sharedKeyCred, containerName, blobName, blobSuffix, key);

        // Decrypts sample blob then reuploads with server-side encryption using Microsoft-managed keys
        decryptReupload(blobClientEncrypted, storageAccount, sharedKeyCred, containerName,
                blobName, blobSuffix, key, null);

        // Decrypts sample blob then reuploads with server-side encryption using customer-managed keys
        decryptReupload(blobClientEncrypted, storageAccount, sharedKeyCred, containerName,
                blobName, blobSuffix, key, createCPK());

    }
}
