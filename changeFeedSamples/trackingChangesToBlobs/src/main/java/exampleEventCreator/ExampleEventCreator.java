package exampleEventCreator;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ExampleEventCreator {
    /**
     * Sets up example events to test changefeed with
     */
    public static void main(String[] args) throws IOException {
        String sharedKeyCred = null;
        String storageAccount = null;

        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path pathToDir = Paths.get(currentPath.toString(), "changeFeedSamples",
                "trackingChangesToBlobs", "src", "main", "java", "exampleEventCreator");
        String configPath = Paths.get(pathToDir.toString(), "app.config").toString();

        // Extracting variables from config file
        InputStream input = new FileInputStream(configPath);
        Properties prop = new Properties();
        prop.load(input);
        sharedKeyCred = prop.getProperty("sharedKeyCred");
        storageAccount = prop.getProperty("storageAccount");

        setup(storageAccount, sharedKeyCred);
    }

    /**
     * Sets up events by creating several blobs, then deletes a few.
     */
    public static void setup(String storageAccount, String sharedKeyCred) throws IOException{
        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";
        String containerName = "test-changefeed-container";
        String blobName = "exampleBlob.txt";
        String blobData = "Lorem ipsum";

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

        // Creating a few blobs to generate events
        for (int i = 0; i < 5; i++) {
            BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(i + blobName).getBlockBlobClient();
            ByteArrayInputStream dataStream = new ByteArrayInputStream(blobData.getBytes());
            blockBlobClient.upload(dataStream, blobData.length(), true);
        }

        // Deleting a blob for an event
        blobContainerClient.getBlobClient(0 + blobName).delete();
        blobContainerClient.getBlobClient(1 + blobName).delete();
    }
}
