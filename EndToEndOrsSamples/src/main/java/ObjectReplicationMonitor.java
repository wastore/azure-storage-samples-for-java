import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.batch.BlobBatchClient;
import com.azure.storage.blob.batch.BlobBatchClientBuilder;
import com.azure.storage.blob.batch.BlobBatchStorageException;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ObjectReplicationPolicy;
import com.azure.storage.blob.models.ObjectReplicationRule;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.*;
import java.lang.String;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class is to demonstrate what object replication does. please read the README in its entirety
 *     before running this program and make sure all prerequisites are met.
 */
public class ObjectReplicationMonitor {

    static Logger logger = LoggerFactory.getLogger(ObjectReplicationMonitor.class);

    public static void main(String[] args) throws IOException {

        // Get path to config file
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path pathToDir = Paths.get(currentPath.toString(),
                "src", "main", "java");
        String pathToConfig = Paths.get(pathToDir.toString(), "app.config").toString();

        // Get values from config files
        InputStream input = new FileInputStream(pathToConfig);
        Properties prop = new Properties();
        prop.load(input);

        String sourceContainerName = prop.getProperty("sourceContainerName");
        String sourceStorageAccountConnectionString = prop.getProperty("sourceStorageAccountConnectionString");
        String destinationContainerName = prop.getProperty("destinationContainerName");
        String destinationStorageAccountConnectionString = prop.getProperty("destinationStorageAccountConnectionString");
        String blobsToReplicatePrefix = prop.getProperty("blobsToReplicatePrefix");
        String archiveMethod = prop.getProperty("archiveMethod");
        String deleteBlobs = prop.getProperty("deleteBlobs");

        // Creating a sample list of blobs to upload and replicate
        String[] replicatedBlobList = new String[1000];
        for (int i = 0; i < replicatedBlobList.length; i++) {
            replicatedBlobList[i] = blobsToReplicatePrefix + i;
        }

        // Call method to setup blob in source container
        setupSource(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);

        // Call method to check the replication status of the blob in source container
        logger.info("\nOnce the replication process begins, the replication status will output...");
        List<String> completedEventsList = checkReplication(sourceStorageAccountConnectionString, sourceContainerName,
                replicatedBlobList);

        // Call method to check the contents of a replicated blob
        checkContents(sourceStorageAccountConnectionString, sourceContainerName, completedEventsList);
        checkContents(destinationStorageAccountConnectionString, destinationContainerName, completedEventsList);

        // Call method to archive replicated blobs
        if (archiveMethod.equals("batch")) {
            archiveReplicatedDataWithBatch(destinationStorageAccountConnectionString, destinationContainerName,
                    completedEventsList);
        } else if (archiveMethod.equals("individual")) {
            archiveReplicatedDataIndividually(destinationStorageAccountConnectionString, destinationContainerName,
                    completedEventsList);
        } else {
            logger.info("\nNo archive method selected.");
        }

        // Call method to delete blobs with batch
        if (deleteBlobs.equals("true")) {
            deleteBlobsWithBatch(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);
        }
    }


    /**
     * This method is to upload the blobs to the source container
     *
     * @param sourceConnectionString The connection string of the source account.
     * @param sourceContainer The name of the container in the source account.
     * @param blobList The list of blobs that were uploaded to the source container.
     */
    private static void setupSource(String sourceConnectionString, String sourceContainer, String[] blobList) {
        logger.info("\nUploading blobs to the source container...");

        // Creating blobserviceclient in order to make containerclient
        BlobServiceClient sourceBlobServiceClient = new BlobServiceClientBuilder().connectionString(sourceConnectionString)
                .buildClient();

        // Get the container client
        BlobContainerClient sourceContainerClient = sourceBlobServiceClient.getBlobContainerClient(sourceContainer);
        if (!sourceContainerClient.exists()) {
            sourceContainerClient.create();
        }

        // Iterate through list of blobs
        for (String blobName : blobList) {
            // Get blob client
            BlobClient sourceBlobClient = sourceContainerClient.getBlobClient(blobName);

            // Upload the blob
            String content = "Hello World!";
            ByteArrayInputStream dataStream = new ByteArrayInputStream(content.getBytes());
            sourceBlobClient.upload(dataStream, content.length(), true);
        }
    }


    /**
     * This method is to check the replication status of the blobs.
     *
     * @param sourceConnectionString The connection string of the source account.
     * @param sourceContainer The name of the container in the source account.
     * @param blobList The list of blobs uploaded to the source container that will be checked for successful replication.
     * @return A list of blobs that successfully replicated.
     */
    private static List<String> checkReplication(String sourceConnectionString, String sourceContainer, String[] blobList) {
        // Creating blobserviceclient to access containerclient
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(sourceConnectionString)
                .buildClient();

        // Get the container client to access blob client
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(sourceContainer);

        // Variables to keep track of and represent how many blobs have replicated
        int completed = 0;
        int size = blobList.length;
        int percentage = 0;
        int percentTracker;
        ArrayList<String> completedEvents = new ArrayList<>();
        ArrayList<String> failedEvents = new ArrayList<>();

        // Continuously loop until all blobs have been checked for replication
        while (completed < size) {
            for (BlobItem blobItem : blobContainerClient.listBlobs()) {
                // Filter which blobs are to be checked as to not check the same blob more than once
                if (blobItem.getObjectReplicationSourcePolicies() != null && blobItem.getName().startsWith("blobExample")
                        && !completedEvents.contains(blobItem.getName()) && !failedEvents.contains(blobItem.getName())) {
                    // Get the replication status
                    for (ObjectReplicationPolicy policy : blobItem.getObjectReplicationSourcePolicies()) {
                        for (ObjectReplicationRule rule : policy.getRules()) {
                            String status = rule.getStatus().toString();
                            // Check if replication failed or was completed
                            if (status.equals("complete")) {
                                completed++;
                                completedEvents.add(blobItem.getName());
                            } else if (status.equals("failed")) {
                                logger.info("\nReplication of " + blobItem.getName() + " has failed. Check that "
                                        + blobItem.getName() + " in destination container are not archived...\nContinuing " +
                                        "to check replication status of blobs...");
                                failedEvents.add(blobItem.getName());
                                size--;
                            }
                        }
                    }
                }
                // Check if status has a new percentage value, to avoid repeat printing the same status
                percentTracker = (int) (((double) completed / (double) size) * 100);
                if (percentTracker != percentage) {
                    // Update percentage and output completion status
                    percentage = percentTracker;
                    logger.info("\nReplication completion is at " + percentage + "%...\n" + completed + " out of "
                            + size + " blobs have successfully replicated...");

                    // Check for completion of all blobs, this will also cause end of while loop
                    if (completed == size) {
                        logger.info("\nCompleted!");
                        break;
                    }
                }
            }
        }
        // Return only the events that had successful replication
        return completedEvents;
    }


    /**
     * This method is to check that the contents of a replicated blob transferred correctly
     *
     * @param connectionString The connection string of the account.
     * @param container The name of the container in the account.
     * @param blobList The list of blobs that were successfully replicated.
     */
    private static void checkContents(String connectionString, String container, List<String> blobList) {
            // Creating blobserviceclient to access containerclient
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString)
                    .buildClient();

            // Get the container client to access blob client
            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(container);

            // Print out contents of one blob to show successful replication
            BlobClient blobClient = blobContainerClient.getBlobClient(blobList.get(0));

            logger.info("\nContent of " + blobList.get(0) + " in the " + container + " container:");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.download(outputStream);
            logger.info(outputStream.toString());
    }


    /**
     * This method is to archive the replicated blobs with batch
     *
     * @param destinationConnectionString The connection string of the destination account.
     * @param destinationContainer The name of the container in the destination account.
     * @param blobList The list of blobs that were successfully replicated
     */
    private static void archiveReplicatedDataWithBatch(String destinationConnectionString, String destinationContainer,
                                                       List<String> blobList) {
        logger.info("\nTransferring replicated blobs in destination container to archive tier...\nThis may take a few minutes...");

        // Get blobServiceClient to create batch client
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(destinationConnectionString)
                .buildClient();

        // Create a batch client that can be used to archive multiple blobs
        BlobBatchClient blobBatchClient = new BlobBatchClientBuilder(blobServiceClient).buildClient();

        // Get all blobs in container's urls and add them to a list
        List<String> blobUrls = new ArrayList<>();
        for (String blobName : blobList) {
            String url = blobServiceClient.getBlobContainerClient(destinationContainer).getBlobClient(blobName).getBlobUrl();
            blobUrls.add(url);
        }

        // Set the access tier of the batch to Archive with try/catch
        try {
            blobBatchClient.setBlobsAccessTier(blobUrls, AccessTier.ARCHIVE).forEach(response ->
                    logger.info(String.format("Setting blob access tier with URL %s completed with status code %d%n",
                            response.getRequest().getUrl(), response.getStatusCode())));

            logger.info("\nArchived replicated blobs!");
        } catch (BlobBatchStorageException ex) {
            logger.info("\nThis error may have to do with TLS. Please ensure that you are using 'http' in your " +
                    "connection string rather than 'https', and that 'Secure Transfer' is disabled in your storage account. " +
                    "See README.md for more help.");
            for (BlobStorageException e : ex.getBatchExceptions()) {
                String message = e.getServiceMessage();
                logger.error(message);
            }
        }
    }


    /**
     * This method is to archive the replicated blobs without batch
     *
     * @param destinationConnectionString The connection string of the destination account.
     * @param destinationContainer The name of the container in the destination account.
     * @param blobList The list of blobs that were successfully replicated
     */
    private static void archiveReplicatedDataIndividually(String destinationConnectionString, String destinationContainer,
                                                          List<String> blobList) {
        logger.info("\nTransferring replicated blobs in destination container to archive tier...\nThis may take a few minutes...");

        // Creating blobserviceclient in order to make containerclient
        BlobServiceClient destinationBlobServiceClient = new BlobServiceClientBuilder().connectionString(destinationConnectionString)
                .buildClient();

        // Get the container client
        BlobContainerClient destinationContainerClient = destinationBlobServiceClient.getBlobContainerClient(destinationContainer);
        if (!destinationContainerClient.exists()) {
            destinationContainerClient.create();
        }

        // Iterate through list of blobs
        for (String blobName : blobList) {
            // Get blob client and archive that blob
            BlobClient blobClient = destinationContainerClient.getBlobClient(blobName);
            blobClient.setAccessTier(AccessTier.ARCHIVE);
        }

        logger.info("\nArchived replicated blobs!");
    }

    /**
     * This method is to use batch to delete blobs from the source container that were uploaded in this sample
     *
     * @param sourceConnectionString The connection string of the source account.
     * @param sourceContainer The name of the source container.
     * @param blobList The list of blobs that were uploaded to the container
     */
    private static void deleteBlobsWithBatch(String sourceConnectionString, String sourceContainer,
                                                       String[] blobList) {
        logger.info("\nDeleting all blobs replicated in this sample...");

        // Get blobServiceClient to create batch client
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(sourceConnectionString)
                .buildClient();

        // Create a batch client that can be used to archive multiple blobs
        BlobBatchClient blobBatchClient = new BlobBatchClientBuilder(blobServiceClient).buildClient();

        // Get all blobs in container's urls and add them to a list
        List<String> blobUrls = new ArrayList<>();
        for (String blobName : blobList) {
            String url = blobServiceClient.getBlobContainerClient(sourceContainer).getBlobClient(blobName).getBlobUrl();
            blobUrls.add(url);
        }

        // Attempt to bulk delete blobs with batch
        try {
            blobBatchClient.deleteBlobs(blobUrls, DeleteSnapshotsOptionType.INCLUDE).forEach(response ->
                    logger.info(String.format("Deleting blob with URL %s completed with status code %d%n",
                            response.getRequest().getUrl(), response.getStatusCode())));

            logger.info("\nArchived replicated blobs!");
        } catch (BlobBatchStorageException ex) {
            logger.info("\nThis error may have to do with TLS. Please ensure that you are using 'http' in your " +
                    "connection string rather than 'https', and that 'Secure Transfer' is disabled in your storage account. " +
                    "See README.md for more help.");
            for (BlobStorageException e : ex.getBatchExceptions()) {
                String message = e.getServiceMessage();
                logger.error(message);
            }
        }
    }
}
