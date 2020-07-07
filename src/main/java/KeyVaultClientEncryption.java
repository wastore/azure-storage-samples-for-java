import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.DecryptResult;
import com.azure.security.keyvault.keys.cryptography.models.EncryptResult;
import com.azure.security.keyvault.keys.cryptography.models.EncryptionAlgorithm;
import com.azure.security.keyvault.keys.models.CreateRsaKeyOptions;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;

public class KeyVaultClientEncryption {


    public KeyVaultClientEncryption(){}

    private static void makeEncryptionScope(String keyVaultUrl, String keyName, String encryptionScope,
                                     String storageAccount, String resourceGroup, String subscription){

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

        Process p = null;
        try{
            p = new ProcessBuilder("cmd.exe", "/c", command).start();
        }
        catch (IOException ex){
            ex.printStackTrace();
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = null;
        while (true) {
            try {
                line = r.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (line == null) { break; }
//            System.out.println(line);
    }

    }

    /**
     * Creates example container and blob, then uploads with client-side encryption with key vault
     **/
    public static BlobClient setup(String storageAccount, String sharedKeyCred, String keyVaultUrl, String containerName, String blobName, String blobSuffix, String keyName){
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

        // Creating key client that allows access of key vault
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        // Creating an example RSA key in key vault
        KeyVaultKey rsaKey = keyClient.createRsaKey(new CreateRsaKeyOptions(keyName)
                .setExpiresOn(OffsetDateTime.now().plusYears(1))
                .setKeySize(2048));
        // Creating cryptography client that allows cryptographic operations using the key that was just created
        CryptographyClient cryptoClient = new CryptographyClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .keyIdentifier(rsaKey.getId())
                .buildClient();


        // Converting sample string (which will act as sample blob data) to a byte array
        byte[] blobBytes = "Hello World".getBytes();
        // Encrypting data using cryptoclient and the RSA algorithm
        EncryptResult encryptResult = cryptoClient.encrypt(EncryptionAlgorithm.RSA_OAEP, blobBytes);

        // Creating BlockBlobClient that will be used to upload to server
        BlobClient blobClient = blobContainerClient
                .getBlobClient(blobName + blobSuffix);
        BlockBlobClient blockBlobClient = blobClient
                .getBlockBlobClient();
        // Uploading ciphertext from encryptResult using block client
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(encryptResult.getCipherText())){
            blockBlobClient.upload(inputStream, encryptResult.getCipherText().length);
        }
        catch (IOException ex){
            ex.printStackTrace();
        }
        return blobClient;

    }

    /**
     * Downloads client-side encrypted blob, decrypts with key vault, then reuploads with server-side encryption that
     * either uses Microsoft or customer-managed keys
     **/
    public static void decryptReupload(String storageAccount, String sharedKeyCred, String keyVaultUrl, String containerName,
                                String blobName, String blobSuffix, String keyName, BlobClient blobClient, String encryptionScope){
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Creating key client that allows access of key vault
        KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(keyVaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        // Get key for decryption using keyClient
        KeyVaultKey key = keyClient.getKey(keyName);
        // Creating cryptography client that allows cryptographic operations using the key that was just created
        CryptographyClient cryptoClient = new CryptographyClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .keyIdentifier(key.getId())
                .buildClient();

        // Download blob to output stream
        ByteArrayOutputStream stream = null;
        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
            blobClient.download(outputStream);
            stream = outputStream;
        }
        catch (IOException exception){
            exception.printStackTrace();
        }
        // Decrypt byte array from output stream
        DecryptResult decryptionResult = cryptoClient.decrypt(EncryptionAlgorithm.RSA_OAEP, stream.toByteArray());

        // Create blob client for reuploading
        BlobClientBuilder blobClientBuilder = new BlobClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .containerName(containerName)
                .blobName(blobName + "Decrypted" + blobSuffix);

        // If using customer-managed keys, set the encryption scope
        if (encryptionScope != null){
            String CPKfileName = blobName + "DecryptedCPK" + blobSuffix;
            blobClientBuilder = blobClientBuilder
                    .encryptionScope(encryptionScope)
                    .blobName(CPKfileName);
        }
        BlobClient blobClientDecrypted = blobClientBuilder.buildClient();

        // Get BlockBlobClient from BlobClient
        BlockBlobClient decryptedBlockBlobClient = blobClientDecrypted.getBlockBlobClient();

        // Upload decrypted blob
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decryptionResult.getPlainText())){
            decryptedBlockBlobClient.upload(inputStream, decryptionResult.getPlainText().length);
        }
        catch (IOException ex){
            ex.printStackTrace();
        }

    }


    /**
     * This sample will show client-side decryption using a key from key vault, and then upload two files with server-side
     * encryption. One will be encrypted with Microsoft-managed keys, and the other by customer-managed keys with
     * key vault through the creation of an encryption scope
     */
    public static void main(String[] args){
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
        BlobClient blobClientEncrypted = setup(storageAccount, sharedKeyCred, keyVaultUrl, containerName, blobName, blobSuffix, keyName);

        // Decrypts sample blob then reuploads with server-side encryption using Microsoft-managed keys
        decryptReupload(storageAccount, sharedKeyCred, keyVaultUrl, containerName, blobName, blobSuffix, keyName, blobClientEncrypted, null);

        // Create an example encryption scope that will allow for server-side encryption using customer-managed keys
        makeEncryptionScope(keyVaultUrl, keyName, encryptionScope, storageAccount, resourceGroup, subscription);
        // Decrypts sample blob then reuploads with server-side encryption using customer-managed keys using
        // encryption scope
        decryptReupload(storageAccount, sharedKeyCred, keyVaultUrl, containerName, blobName, blobSuffix, keyName, blobClientEncrypted, encryptionScope);

    }
}
