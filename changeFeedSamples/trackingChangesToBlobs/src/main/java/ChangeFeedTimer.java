import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.changefeed.BlobChangefeedClient;
import com.azure.storage.blob.changefeed.BlobChangefeedClientBuilder;
import com.azure.storage.blob.changefeed.BlobChangefeedPagedIterable;
import com.azure.storage.blob.changefeed.BlobChangefeedPagedResponse;
import com.azure.storage.blob.changefeed.models.BlobChangefeedEvent;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ChangeFeedTimer {
    /**
     * Sets up a timer that checks for new events that are filtered based on preferences in TimerHelper. Saves cursor to
     * blob in given storage account for future use
     */
    public static void main(String[] args) throws IOException {
        String storingCursorContainer = "storingcursorcontainer";
        String blobCursorName = "storingcursorblob";

        // Delay between runs of timer. Currently set to an hour
        int interval = 3600000;

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

        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";


        // Getting clients for accessing container and blob for storing cursor
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .buildClient();

        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(storingCursorContainer);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobCursorName);

        // Creating changefeed client
        BlobChangefeedClient changefeedClient = new BlobChangefeedClientBuilder(blobServiceClient).buildClient();

        // Get previously used cursor
        String cursor = getCursor(blobClient);

        // Create a Timer
        Timer timer = new Timer();
        TimerTask task = new ChangeFeedHelper(blobContainerClient, blobClient, changefeedClient, cursor);

        // Running on schedule
        timer.scheduleAtFixedRate(task, 0, interval);
    }

    /**
     * Retrieves cursor from container in case where cursor exists. If not, returns null
     */
    public static String getCursor(BlobClient blobClient) {
        // Checking if blob exists to see if there is an existing cursor
        if (blobClient.exists()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.download(outputStream);
            return new String(outputStream.toByteArray());
        }
        return null;
    }
}

class ChangeFeedHelper extends TimerTask {
    public String cursor;
    public BlobContainerClient containerClient;
    public BlobClient blobClient;
    public BlobChangefeedClient changefeedClient;

    // Filtering
    String trackedContainer = "containers/test-changefeed-container";
    String trackedBlob = "exampleBlob.txt";
    String eventType = "BlobCreated";
    Predicate<BlobChangefeedEvent> checkContainerName = (event) -> event.getSubject().contains(trackedContainer);
    Predicate<BlobChangefeedEvent> checkBlobName = (event) -> event.getSubject().contains(trackedBlob);
    Predicate<BlobChangefeedEvent> checkEventType = (event) -> event.getEventType().toString().equals(eventType);

    public ChangeFeedHelper(BlobContainerClient containerClient, BlobClient blobClient, BlobChangefeedClient changefeedClient) {
        this(containerClient, blobClient, changefeedClient, null);
    }

    public ChangeFeedHelper(BlobContainerClient containerClient, BlobClient blobClient, BlobChangefeedClient changefeedClient, String cursor) {
        this.containerClient = containerClient;
        this.blobClient = blobClient;
        this.changefeedClient = changefeedClient;
        this.cursor = cursor;
    }

    /**
     * Every time the timer executes, check for new events starting from where cursor points at and filter events. Filter
     * depends on how the predicates are set up.
     */
    public void run() {
        BlobChangefeedPagedIterable iterable = null;

        // Starting from cursor position if there exists one
        if (this.cursor == null) {
            iterable = this.changefeedClient.getEvents();
        }
        else {
            iterable = this.changefeedClient.getEvents(this.cursor);
        }
        Iterable<BlobChangefeedPagedResponse> pages = iterable.iterableByPage(1);

        System.out.println("Printing all events satisfying filters");
        // Returning event info if event matches blob name
        for (BlobChangefeedPagedResponse page : pages) {
            List<BlobChangefeedEvent> iter = page.getValue();
            for (BlobChangefeedEvent event : iter) {
                // TODO: Change predicate chain to desired chain to filter events
                if (checkBlobName.and(checkContainerName).test(event)) {
                    // Prints out information about event
                    System.out.printf("Time: %s, Subject: %s, ID: %s, Type: %s%n", event.getEventTime(), event.getSubject(), event.getId(), event.getEventType());
                }
            }
            this.cursor = page.getContinuationToken();
        }

        System.out.println("Printed all events satisfying filter since last check, storing cursor into storage account");
        // Stores cursor in storage account, in case if it needs to be used again later
        this.storeCursor();
        System.out.println("Stored cursor");
    }

    /**
     * Stores cursor into blob in a container
     */
    private void storeCursor() {
        // Creates container if there is no container
        if (!this.containerClient.exists()){
            this.containerClient.create();
        }

        // Storing cursor in blob
        BlockBlobClient blockBlobClient = this.blobClient.getBlockBlobClient();
        ByteArrayInputStream dataStream = new ByteArrayInputStream(this.cursor.getBytes());
        blockBlobClient.upload(dataStream, this.cursor.length(), true);
    }
}


