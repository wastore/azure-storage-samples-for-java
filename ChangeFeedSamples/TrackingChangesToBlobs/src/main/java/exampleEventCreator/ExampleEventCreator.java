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
import java.util.Timer;
import java.util.TimerTask;

public class ExampleEventCreator {
    /**
     * Sets up example events to test changefeed with
     */
    public static void main(String[] args) throws IOException {
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path pathToDir = Paths.get(currentPath.toString(), "changeFeedSamples",
                "trackingChangesToBlobs", "src", "main", "java", "exampleEventCreator");
        String configPath = Paths.get(pathToDir.toString(), "app.config").toString();

        // Extracting variables from config file
        InputStream input = new FileInputStream(configPath);
        Properties prop = new Properties();
        prop.load(input);
        String sharedKeyCred = prop.getProperty("sharedKeyCred");
        String storageAccount = prop.getProperty("storageAccount");
        String intervalString = prop.getProperty("interval");
        int interval = Integer.parseInt(intervalString);
        
        // Create a Timer for creating events on a consistent interval
        Timer timer = new Timer();
        TimerTask task = new ExampleEventCreatorHelper(storageAccount, sharedKeyCred);

        // Running on schedule
        timer.scheduleAtFixedRate(task, 0, interval);
    }
}

class ExampleEventCreatorHelper extends TimerTask {
    String storageAccount;
    String sharedKeyCred;
    int blobCount = 0;

    public ExampleEventCreatorHelper(String storageAccount, String sharedKeyCred) {
        this.storageAccount = storageAccount;
        this.sharedKeyCred = sharedKeyCred;
    }

    // Creates a new blob every time timer is triggered with file name "#exampleBlob.txt" where # is an int.
    public void run() {
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

        // Generate blob as event
        BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(this.blobCount + blobName).getBlockBlobClient();
        ByteArrayInputStream dataStream = new ByteArrayInputStream(blobData.getBytes());
        blockBlobClient.upload(dataStream, blobData.length(), true);
        System.out.println("Generated blob called " + this.blobCount + blobName);
        this.blobCount++;
    }
}
