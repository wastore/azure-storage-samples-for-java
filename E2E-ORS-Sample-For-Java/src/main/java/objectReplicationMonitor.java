import com.azure.storage.blob.*;
import com.azure.storage.blob.batch.BlobBatchClient;
import com.azure.storage.blob.batch.BlobBatchClientBuilder;
import com.azure.storage.blob.batch.BlobBatchStorageException;
import com.azure.storage.blob.models.*;

import java.util.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;


public class objectReplicationMonitor {
    /*
    this class is to demonstrate what object replication does. please read the README in its entirety
    before running this program and make sure all prerequisites are met.
     */

    public static void main(String[] args) throws IOException {

        // get path to config file
        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path pathToDir = Paths.get(currentPath.toString(),
                "src", "main", "java");
        String pathToConfig = Paths.get(pathToDir.toString(), "app.config").toString();

        // get values from config files
        InputStream input = new FileInputStream(pathToConfig);
        Properties prop = new Properties();
        prop.load(input);

        String sourceContainerName = prop.getProperty("sourceContainerName");
        String sourceStorageAccountConnectionString = prop.getProperty("sourceStorageAccountConnectionString");
        String destinationContainerName = prop.getProperty("destinationContainerName");
        String destinationStorageAccountConnectionString = prop.getProperty("destinationStorageAccountConnectionString");
        String blobsToReplicate = prop.getProperty("blobsToReplicate");

        // creating a sample list of blobs to upload and replicate
        String[] replicatedBlobList = new String[1000];
        int num = 1;
        for (int i = 0; i < 1000; i++) {

            replicatedBlobList[i] = blobsToReplicate + num;
            num++;

        }

        // call method to setup blob in source container
        setupSource(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);

        // call method to check the replication status of the blob in source container
        System.out.println("\nReplicating blobs to destination container...");
        System.out.println("Once the replication process begins, the replication status will output...");
        boolean status = checkReplication(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);

        // if checkReplication() completed replication, these methods will be called
        if (status) {
            // call method to check the contents of a replicated blob
            checkContents(sourceStorageAccountConnectionString, sourceContainerName, destinationStorageAccountConnectionString, destinationContainerName, replicatedBlobList);

            // call method to archive replicated blobs
            archiveReplicatedData(destinationStorageAccountConnectionString, destinationContainerName, replicatedBlobList);

        }

    }


    private static void setupSource(String connectionString, String containerName, String[] blobList) {
        /*
        this method is to upload the blobs to the source container
         */

        System.out.println("\nUploading blobs to the source container...");

        // creating blobserviceclient in order to make containerclient
        BlobServiceClient sourceBlobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // get the container client
        BlobContainerClient sourceContainerClient = sourceBlobServiceClient.getBlobContainerClient(containerName);
        if (!sourceContainerClient.exists()) {

            sourceContainerClient.create();

        }

        // iterate through list of blobs
        for (String b : blobList) {

            // get blob client
            BlobClient sourceBlobClient = sourceContainerClient.getBlobClient(b);

            // upload the blob
            String content = "Hello World!";
            ByteArrayInputStream dataStream = new ByteArrayInputStream(content.getBytes());
            sourceBlobClient.upload(dataStream, content.length(), true);

        }

    }


    private static boolean checkReplication(String connectionString, String containerName, String[] blobList) {
        /*
        this method is to check the replication status and the contents of the blob
        it returns a boolean to represent whether or not the replication was completed
        if it was not completed, false will be returned. if it is completed, true will return
         */

        // creating blobserviceclient to access containerclient
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // get the container client to access blob client
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);

        // variables to keep track of and represent how many blobs have replicated
        int completed = 0;
        double ratio = 0;
        int percentage = (int) (ratio * 100);
        int size = blobList.length;
        ArrayList completedEvents = new ArrayList();

        System.out.println("Please wait for replication to begin...");

        // while loop for continuous updating on replication status until all blobs have replicated
        while (completed < size) {

            // iterate through list of all blobs
            for (String b : blobList) {

                // check if event has already been completed, as to only check status of unreplicated blobs
                if (!completedEvents.contains(b)) {

                    // get blob client to access blob
                    BlobClient blobClient = blobContainerClient.getBlobClient(b);

                    // get properties of blob
                    BlobProperties properties = blobClient.getProperties();
                    List<ObjectReplicationPolicy> propertyList = properties.getObjectReplicationSourcePolicies();

                    // find replication policy status
                    for (ObjectReplicationPolicy policy : propertyList) {

                        for (ObjectReplicationRule rule : policy.getRules()) {

                            // track how many replications have completed
                            String status = rule.getStatus().toString();

                            // if the replication was completed, add 1 to variable completed and add that blob to the completedEvents list
                            if (status.equals("complete")) {

                                completed++;
                                completedEvents.add(b);

                            }

                            // if the replication was failed, return false
                            else if (status.equals("failed")) {

                                System.out.println("\nReplication has failed. Please check that blobs in destination container are not archived...");
                                return false;

                            }

                        }

                    }

                }

                // check if status has a new value and percentage, to avoid repeat printing the same status
                if((double)completed/(double)size != ratio && (int)(((double)completed/(double)size) * 100) != percentage) {

                    ratio = (double)completed/(double)size;
                    percentage = (int)(ratio * 100);

                    // output completion status as a percentage
                    System.out.println("\nReplication completion is at " + percentage + "%...");
                    System.out.println(completed + " out of " + size + " blobs have successfully replicated...");

                    // check for completion of all blobs, this will also cause end of while loop
                    if (ratio == 1) {
                        System.out.println("\nCompleted!");
                        break;

                    }

                }

            }

        }

        // if replication was completed for all blobs return true
        return true;

    }


    private static void checkContents(String sourceConnectionString, String sourceContainer, String destinationConnectionString, String destinationContainer, String[] blobList) throws FileNotFoundException {
        /*
        this method is to check that the contents of a replicated blob transferred correctly
         */

        String[] connectionString = {sourceConnectionString, destinationConnectionString};
        String[] containerName = {sourceContainer, destinationContainer};

        for (int s = 0; s < connectionString.length; s++) {

            // creating blobserviceclient to access containerclient
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString[s]).buildClient();

            // get the container client to access blob client
            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName[s]);

            // print out contents of one blob to show successful replication
            BlobClient blobClient = blobContainerClient.getBlobClient(blobList[0]);

            System.out.println("\nContent of " + blobList[0] + " in the " + containerName[s] + " container:");

            // create local file to store contents
            File localFile = new File("src/main/java/replicated_blob_content.txt");

            // download blob content to file
            blobClient.downloadToFile("src/main/java/replicated_blob_content.txt", true);

            // read content of file
            Scanner fileReader = new Scanner(localFile);
            while (fileReader.hasNextLine()) {

                String content = fileReader.nextLine();
                System.out.println(content);

            }

            fileReader.close();

            // delete content file-- clean up
            localFile.deleteOnExit();
        }

    }


    private static void archiveReplicatedData(String destinationConnectionString, String destinationContainer, String[] blobList) {
        /*
        this method is to archive the replicated blobs
         */

        System.out.println("\nTransferring replicated blobs in destination container to archive tier...");
        System.out.println("This may take a few minutes...");

        // get blobServiceClient to create batch client
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(destinationConnectionString).buildClient();

        // create a batch client that can be used to archive multiple blobs
        BlobBatchClient blobBatchClient = new BlobBatchClientBuilder(blobServiceClient).buildClient();

        // get all blobs in container's urls and add them to a list
        List<String> blobUrls = new ArrayList<>();

        for (String b : blobList) {

            String url = blobServiceClient.getBlobContainerClient(destinationContainer).getBlobClient(b).getBlobUrl();
            blobUrls.add(url);

        }

        // set the access tier of the batch to Archive with try/catch
        try {

            blobBatchClient.setBlobsAccessTier(blobUrls, AccessTier.ARCHIVE).forEach(response ->
                    System.out.printf("Setting blob access tier with URL %s completed with status code %d%n",
                            response.getRequest().getUrl(), response.getStatusCode()));

        }

        catch(BlobBatchStorageException ex) {

            for (BlobStorageException e : ex.getBatchExceptions()) {

                String message = e.getServiceMessage();
                System.out.println(message);

            }

        }

        System.out.println("\nArchived replicated blobs!");

    }

}
