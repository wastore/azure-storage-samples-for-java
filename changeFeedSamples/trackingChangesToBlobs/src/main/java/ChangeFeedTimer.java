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
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Predicate;
import java.util.stream.Stream;

import exampleEventCreator.ExampleEventCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeFeedTimer {
    private static Logger logger = LoggerFactory.getLogger(ChangeFeedTimer.class);

    /**
     * Sets up a timer that checks for new events that are filtered based on preferences in TimerHelper. Saves cursor to
     * blob in given storage account for future use
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
        String cursorStorageContainer = prop.getProperty("cursorStorageContainer");
        String blobCursorName = prop.getProperty("blobCursorName");
        String intervalString = prop.getProperty("interval");
        int interval = Integer.parseInt(intervalString);

        String storageAccountUrl = "https://" + storageAccount + ".blob.core.windows.net";

        // Getting clients for accessing container and blob for storing cursor
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccountUrl)
                .credential(new StorageSharedKeyCredential(storageAccount, sharedKeyCred))
                .buildClient();

        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(cursorStorageContainer);
        BlobClient blobClient = blobContainerClient.getBlobClient(blobCursorName);

        // Creating changefeed client
        BlobChangefeedClient changefeedClient = new BlobChangefeedClientBuilder(blobServiceClient).buildClient();

        // Get previously used cursor
        String cursor = getCursor(blobContainerClient, blobClient);

        // Create a Timer
        Timer timer = new Timer();
        TimerTask task = new ChangeFeedHelper(blobServiceClient, blobContainerClient, blobClient, changefeedClient, cursor, logger);

        // Running on schedule
        timer.scheduleAtFixedRate(task, 0, interval);
    }

    /**
     * Retrieves cursor from container in case where cursor exists. If not, returns null
     */
    public static String getCursor(BlobContainerClient blobContainerClient, BlobClient blobClient) {
        // Checking if blob exists to see if there is an existing cursor
        if (blobContainerClient.exists() && blobClient.exists()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.download(outputStream);
            return new String(outputStream.toByteArray());
        }
        return null;
    }
}

class ChangeFeedHelper extends TimerTask {
    public String cursor;
    public BlobServiceClient serviceClient;
    public BlobContainerClient containerClient;
    public BlobClient blobClient;
    public BlobChangefeedClient changefeedClient;
    public Logger logger;

    // Filtering
    String trackedContainer = "containers/test-changefeed-container";
    String trackedBlob = "exampleBlob.txt";
    String eventType = "BlobCreated";
    Predicate<BlobChangefeedEvent> checkContainerName = (event) -> event.getSubject().contains(trackedContainer);
    Predicate<BlobChangefeedEvent> checkBlobName = (event) -> event.getSubject().contains(trackedBlob);
    Predicate<BlobChangefeedEvent> checkEventType = (event) -> event.getEventType().toString().equals(eventType);

    public ChangeFeedHelper(BlobServiceClient blobServiceClient, BlobContainerClient containerClient,
                            BlobClient blobClient, BlobChangefeedClient changefeedClient, Logger logger) {
        this(blobServiceClient, containerClient, blobClient, changefeedClient, null, logger);
    }

    public ChangeFeedHelper(BlobServiceClient blobServiceClient, BlobContainerClient containerClient,
                            BlobClient blobClient, BlobChangefeedClient changefeedClient, String cursor, Logger logger) {
        this.serviceClient = blobServiceClient;
        this.containerClient = containerClient;
        this.blobClient = blobClient;
        this.changefeedClient = changefeedClient;
        this.cursor = cursor;
        this.logger = logger;
    }

    /**
     * Every time the timer executes, check for new events starting from where cursor points at and filter events. Filter
     * depends on how the predicates are set up.
     */
    public void run() {
        BlobChangefeedPagedIterable iterable = null;

        // Starting from cursor position if there exists one. If there is no cursor, outputs of events start from
        // beginning of time
        if (this.cursor == null) {
            iterable = this.changefeedClient.getEvents();
        }
        else {
            iterable = this.changefeedClient.getEvents(this.cursor);
        }

        Stream<BlobChangefeedPagedResponse> pages = iterable.streamByPage();

        this.logger.info("Printing all events satisfying filters");

        // Checking by page every event and seeing if it satisfies filters. At the end, store the cursor in a blob
        pages.forEach(page -> {
                    page.getElements().stream()
                            .filter(event ->
                                    // TODO: Change predicate chain to desired chain to filter events
                                    checkBlobName.and(checkContainerName).test(event))
                            .forEach(event ->
                                    // TODO: Change output as needed to see necessary information from event
                                    this.logger.info("Time: {}, Subject: {}, ID: {}, Type: {}",
                                            event.getEventTime(), event.getSubject(), event.getId(), event.getEventType()));
                    this.cursor = page.getContinuationToken();
                }
        );

        this.logger.info("Printed all events satisfying filter since last check, storing cursor into storage account");
        // Stores cursor in storage account, in case if it needs to be used again later
        this.storeCursor();
        this.logger.info("Stored cursor");
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