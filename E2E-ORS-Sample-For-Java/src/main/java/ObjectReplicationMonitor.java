import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.batch.BlobBatchClient;
import com.azure.storage.blob.batch.BlobBatchClientBuilder;
import com.azure.storage.blob.batch.BlobBatchStorageException;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.ObjectReplicationPolicy;
import com.azure.storage.blob.models.ObjectReplicationRule;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobStorageException;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;


public class ObjectReplicationMonitor {
    /*
    This class is to demonstrate what object replication does. please read the README in its entirety
    before running this program and make sure all prerequisites are met.
     */

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

        // Creating a sample list of blobs to upload and replicate
        String[] replicatedBlobList = new String[250];
        int num = 0;
        for (int i = 0; i < replicatedBlobList.length; i++) {

            replicatedBlobList[i] = blobsToReplicatePrefix + num;
            num++;

        }

        // Call method to setup blob in source container
        setupSource(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);

        // Call method to check the replication status of the blob in source container
        System.out.println("\nOnce the replication process begins, the replication status will output...");
        List<String> completedEventsList = checkReplication(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);

        // Call method to check the contents of a replicated blob
        checkContents(sourceStorageAccountConnectionString, sourceContainerName, destinationStorageAccountConnectionString, destinationContainerName, completedEventsList);

        // Call method to archive replicated blobs
        if (archiveMethod.equals("batch")) {
            archiveReplicatedDataWithBatch(destinationStorageAccountConnectionString, destinationContainerName, completedEventsList);
        }
        else if (archiveMethod.equals("individual")) {
            archiveReplicatedDataIndividually(destinationStorageAccountConnectionString, destinationContainerName, completedEventsList);
        }
        else {
            System.out.println("\nNo archive method selected");
        }
    }


    private static void setupSource(String connectionString, String containerName, String[] blobList) {
        /*
        This method is to upload the blobs to the source container
         */

        System.out.println("\nUploading blobs to the source container...");

        // Creating blobserviceclient in order to make containerclient
        BlobServiceClient sourceBlobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // Get the container client
        BlobContainerClient sourceContainerClient = sourceBlobServiceClient.getBlobContainerClient(containerName);
        if (!sourceContainerClient.exists()) {
            sourceContainerClient.create();
        }

        // Iterate through list of blobs
        for (String b : blobList) {
            // Get blob client
            BlobClient sourceBlobClient = sourceContainerClient.getBlobClient(b);

            // Upload the blob
            String content = "Hello World!";
            ByteArrayInputStream dataStream = new ByteArrayInputStream(content.getBytes());
            sourceBlobClient.upload(dataStream, content.length(), true);
        }
    }


    private static List<String> checkReplication(String connectionString, String containerName, String[] blobList) {
        /*
        This method is to check the replication status of the blobs.
         */

        // Creating blobserviceclient to access containerclient
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // Get the container client to access blob client
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);

        // Variables to keep track of and represent how many blobs have replicated
        int completed = 0;
        double ratio = 0;
        int percentage = (int) (ratio * 100);
        int size = blobList.length;
        ArrayList<String> completedEvents = new ArrayList<>();
        ArrayList<String> failedEvents = new ArrayList<>();

        // While loop for continuous updating on replication status until all blobs have replicated
        while (completed < size) {

            // Iterate through list of all blobs
            for (String b : blobList) {

                // Check if event has already been completed, as to only check status of unreplicated blobs
                if (!completedEvents.contains(b) && !failedEvents.contains(b)) {

                    // Get blob client to access blob
                    BlobClient blobClient = blobContainerClient.getBlobClient(b);

                    // Get properties of blob
                    BlobProperties properties = blobClient.getProperties();
                    List<ObjectReplicationPolicy> propertyList = properties.getObjectReplicationSourcePolicies();

                    // Find replication policy status
                    for (ObjectReplicationPolicy policy : propertyList) {
                        for (ObjectReplicationRule rule : policy.getRules()) {

                            // Track how many replications have completed
                            String status = rule.getStatus().toString();

                            // If the replication was completed, add 1 to variable completed and add that blob to the failedEvents list
                            if (status.equals("complete")) {
                                completed++;
                                completedEvents.add(b);
                            }

                            // If the replication was failed on a specific blob, alert user and continue checking replication of other blobs
                            else if (status.equals("failed")) {
                                System.out.println("\nReplication of " + b + " has failed. Please check that " + b + " in destination container are not archived...");
                                failedEvents.add(b);
                                size--;
                            }
                        }
                    }
                }

                // Check if status has a new value and percentage, to avoid repeat printing the same status
                if ((double) completed / (double) size != ratio && (int) (((double) completed / (double) size) * 100) != percentage) {
                    ratio = (double) completed / (double) size;
                    percentage = (int) (ratio * 100);

                    // Output completion status as a percentage
                    System.out.println("\nReplication completion is at " + percentage + "%...");
                    System.out.println(completed + " out of " + size + " blobs have successfully replicated...");

                    // Check for completion of all blobs, this will also cause end of while loop
                    if (ratio == 1) {
                        System.out.println("\nCompleted!");
                        break;
                    }
                }
            }
        }
        return completedEvents;
    }


    private static void checkContents(String sourceConnectionString, String sourceContainer, String destinationConnectionString, String destinationContainer, List<String> blobList) throws FileNotFoundException {
        /*
        This method is to check that the contents of a replicated blob transferred correctly
         */

        String[] connectionString = {sourceConnectionString, destinationConnectionString};
        String[] containerName = {sourceContainer, destinationContainer};
        for (int s = 0; s < connectionString.length; s++) {

            // Creating blobserviceclient to access containerclient
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString[s]).buildClient();

            // Get the container client to access blob client
            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName[s]);

            // Print out contents of one blob to show successful replication
            BlobClient blobClient = blobContainerClient.getBlobClient(blobList.get(0));

            System.out.println("\nContent of " + blobList.get(0) + " in the " + containerName[s] + " container:");

            // Create local file to store contents
            File localFile = new File("src/main/java/replicated_blob_content.txt");

            // Download blob content to file
            blobClient.downloadToFile("src/main/java/replicated_blob_content.txt", true);

            // Read content of file
            Scanner fileReader = new Scanner(localFile);
            while (fileReader.hasNextLine()) {
                String content = fileReader.nextLine();
                System.out.println(content);
            }
            fileReader.close();

            // Delete content file-- clean up
            localFile.deleteOnExit();
        }
    }


    private static void archiveReplicatedDataWithBatch(String destinationConnectionString, String destinationContainer, List<String> blobList) {
        /*
        This method is to archive the replicated blobs with batch
         */

        System.out.println("\nTransferring replicated blobs in destination container to archive tier...");
        System.out.println("This may take a few minutes...");

        // Get blobServiceClient to create batch client
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(destinationConnectionString).buildClient();

        // Create a batch client that can be used to archive multiple blobs
        BlobBatchClient blobBatchClient = new BlobBatchClientBuilder(blobServiceClient).buildClient();

        // Get all blobs in container's urls and add them to a list
        List<String> blobUrls = new ArrayList<>();
        for (String b : blobList) {
            String url = blobServiceClient.getBlobContainerClient(destinationContainer).getBlobClient(b).getBlobUrl();
            blobUrls.add(url);
        }

        // Set the access tier of the batch to Archive with try/catch
        try {
            blobBatchClient.setBlobsAccessTier(blobUrls, AccessTier.ARCHIVE).forEach(response ->
                    System.out.printf("Setting blob access tier with URL %s completed with status code %d%n",
                            response.getRequest().getUrl(), response.getStatusCode()));

            System.out.println("\nArchived replicated blobs!");
        }
        catch (BlobBatchStorageException ex) {
            System.out.println("\nThis error may have to do with TLS. Please ensure that you are using 'https' in your connection string rather than 'https', and that 'Secure Transfer' is disabled in your storage account. See README.md for more help.");
            for (BlobStorageException e : ex.getBatchExceptions()) {
                String message = e.getServiceMessage();
                System.out.println(message);
            }
        }
    }


    private static void archiveReplicatedDataIndividually(String destinationConnectionString, String destinationContainer, List<String> blobList) {
        /*
        This is to archive the data without batch
         */

        System.out.println("\nTransferring replicated blobs in destination container to archive tier...");
        System.out.println("This may take a few minutes...");

        // Creating blobserviceclient in order to make containerclient
        BlobServiceClient sourceBlobServiceClient = new BlobServiceClientBuilder().connectionString(destinationConnectionString).buildClient();

        // Get the container client
        BlobContainerClient sourceContainerClient = sourceBlobServiceClient.getBlobContainerClient(destinationContainer);
        if (!sourceContainerClient.exists()) {
            sourceContainerClient.create();
        }

        // Iterate through list of blobs
        for (String b : blobList) {
            // Get blob client and archive that blob
            BlobClient blobClient = sourceContainerClient.getBlobClient(b);
            blobClient.setAccessTier(AccessTier.ARCHIVE);
        }
    }
}
