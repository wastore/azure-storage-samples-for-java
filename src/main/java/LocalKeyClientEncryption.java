import com.azure.core.cryptography.AsyncKeyEncryptionKey;
import com.azure.security.keyvault.keys.cryptography.LocalKeyEncryptionKeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyOperation;
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.CustomerProvidedKey;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClient;
import com.azure.storage.blob.specialized.cryptography.EncryptedBlobClientBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.util.Arrays;
import java.util.Random;


public class LocalKeyClientEncryption {
    public EncryptedBlobClient encryptedBlobClient;
    public AsyncKeyEncryptionKey key;

    public LocalKeyClientEncryption(){}

    /**
     * Creates an example random but not secure customer provided key to be used in server-side encryption
     */
    private CustomerProvidedKey createCPK(){
        byte[] b = new byte[32];
        new Random().nextBytes(b);
        CustomerProvidedKey serverKey = new CustomerProvidedKey(b);
        return serverKey;
    }

    /**
     * Creates a random, not secure local key to be used in client-side encryption
     */
    private AsyncKeyEncryptionKey createLocalKey(){
        byte[] byteKey = new byte[32];
        new Random().nextBytes(byteKey);
        JsonWebKey localKey = JsonWebKey.fromAes(new SecretKeySpec(byteKey, "AES"),
                Arrays.asList(KeyOperation.WRAP_KEY, KeyOperation.UNWRAP_KEY))
                .setId("my-id");
        AsyncKeyEncryptionKey akek = new LocalKeyEncryptionKeyClientBuilder()
                .buildAsyncKeyEncryptionKey(localKey).block();
        this.key = akek;
        return akek;
    }

    /**
     * Sets local key client with in helper object to be used in decrpytion
     */
    private void setLocalKeyClient(AsyncKeyEncryptionKey key, BlobClient blobClient){
        this.encryptedBlobClient = new EncryptedBlobClientBuilder()
                .key(key, KeyWrapAlgorithm.A256KW.toString())
                .blobClient(blobClient)
                .buildEncryptedBlobClient();
    }

    /**
     * Creates example container and blob, then uploads with client-side encryption with local keys
     */
    public void setup(String storageAccountUrl, String sasToken){
        // Setting names of container and blob that will be created later in the code. Note that container
        // names are all lowercase and both containers and blobs cannot have underscores
        String exampleContainerName = "containername";
        String exampleBlobName = "blobExample";
        String exampleBlobSuffix = ".txt";

        // Creating a BlobServiceClient that allows us to perform container and blob operations, given our storage
        // account URL and generated SAS token
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccountUrl)
                .sasToken(sasToken)
                .buildClient();

        // Creating client referencing to-be-created container, and then creating it
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(exampleContainerName);
        blobContainerClient.create();

        // Creating a blob client
        BlobClient blobClient = blobContainerClient.getBlobClient(exampleBlobName + exampleBlobSuffix);
        // Creating a local key and setting encryptedKeyClient
        this.createLocalKey();
        this.setLocalKeyClient(this.key, blobClient);

        // Uploading example blob with client-side encryption
        String fileName = exampleBlobName + exampleBlobSuffix;
        this.encryptedBlobClient.uploadFromFile(".\\src\\main\\java\\" + fileName);
    }

    /**
     * Downloads client-side encrypted blob, decrypts with local key, then reuploads with server-side encryption
     */
    public void decryptReupload(String storageAccountUrl, String sasToken, String containerName, String blobName,
                                String blobSuffix, AsyncKeyEncryptionKey key, CustomerProvidedKey serverKey){
        // Creating blob client to download blob
        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .sasToken(sasToken)
                .containerName(containerName)
                .blobName(blobName + blobSuffix)
                .buildClient();

        // Set blob encryption client
        if (key == null) {
            key = this.key;
        }
        this.setLocalKeyClient(key, blobClient);

        // Downloading encrypted blob, blob is decrypted upon download
        String fileName = blobName + "Decrypted" + blobSuffix;
        this.encryptedBlobClient.downloadToFile(".\\src\\main\\java\\" + fileName);

        // Creating blob client for decryption and reuploading
        BlobClientBuilder blobClientBuilder = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .sasToken(sasToken)
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
     * Requires setting and naming the following environmental variables:
     * sasToken (for storage account)
     * storageAccount
     * This sample will show client-side decryption using a local key, and then upload two files with server-side
     * encryption. One will be encrypted with Microsoft-managed keys, and the other is by customer-provided keys.
     */
    public static void main(String[] args){
        String sasToken = System.getenv("sasToken");
        String storageAccount = System.getenv("storageAccount");

        // Setting names of container and blob so that they match ones from setup. Can be changed in case where
        // set up is not used
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";
        String containerName = "containername";
        String blobName = "blobExample";
        String blobSuffix = ".txt";

        // Creating LocalKeyClientEncryption helper
        LocalKeyClientEncryption helper = new LocalKeyClientEncryption();
        // Setup where sample blob is client-side encrypted and uploaded to server
        helper.setup(storageAccountUrl, sasToken);

        // Add additional AsyncKeyEncryptionKey in decryption if example key was not used
        // Decrypts sample blob then reuploads with server-side encryption using Microsoft-managed keys
        helper.decryptReupload(storageAccountUrl, sasToken, containerName,
                blobName, blobSuffix, null, null);

        // Decrypts sample blob then reuploads with server-side encryption using customer-managed keys
        helper.decryptReupload(storageAccountUrl, sasToken, containerName,
                blobName, blobSuffix, null, helper.createCPK());

    }
}
