import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.ObjectReplicationPolicy;
import com.azure.storage.blob.models.ObjectReplicationRule;
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

        String[] replicatedBlobList = blobsToReplicate.split(", ");

        // sample of replication status
        System.out.println("\nThis is an example of using ORS...");

        // call method to setup blob in source container
        setupSource(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);

        // call method to check the replication status of the blob in source container
        System.out.println("\nReplicating blobs to destination container...");
        System.out.println("Once the replication process begins, the replication status will output...");
        checkReplication(sourceStorageAccountConnectionString, sourceContainerName, replicatedBlobList);

        // call method to check the contents of a replicated blob
        checkContents(sourceStorageAccountConnectionString, sourceContainerName, destinationStorageAccountConnectionString, destinationContainerName, replicatedBlobList);

        // call method to archive replicated blobs
        archiveReplicatedData(destinationStorageAccountConnectionString, destinationContainerName, replicatedBlobList);

    }


    private static void setupSource(String connectionString, String containerName, String[] blobList) {
        // this method is to upload a blob to the source container

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


    private static void checkReplication(String connectionString, String containerName, String[] blobList) {
        // this method is to check the replication status and the contents of the blob

        // creating blobserviceclient to access containerclient
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // get the container client to access blob client
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);

        int completed = 0;
        double ratio = 0;
        int size = blobList.length;
        ArrayList completedEvents = new ArrayList();

        // while loop for continuous updating on replication status
        while (completed < size) {

            // iterate through list of blobs
            for (String b : blobList) {

                // check if event has already been completed
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
                            if (status.equals("complete")) {
                                completed++;
                                completedEvents.add(b);

                            }

                        }

                    }

                }

                // if status has new value
                if((double)completed/(double)size != ratio) {
                    ratio = (double)completed/(double)size;
                    // output completion status as a percentage
                    System.out.println("\nReplication completion is at " + ((int)(ratio * 100)) + "%...");
                    System.out.println(completed + " out of " + size + " blobs have successfully replicated...");

                    // check for completion
                    if (ratio == 1) {
                        System.out.println("\nCompleted!");
                        break;

                    }

                }

            }

        }

    }


    private static void checkContents(String sourceConnectionString, String sourceContainer, String destinationConnectionString, String destinationContainer, String[] blobList) throws FileNotFoundException {
        // this method is to check that the contents of a replicated blob transferred correctly

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

            // delete content file
            localFile.delete();
        }

    }


    private static void archiveReplicatedData(String destinationConnectionString, String destinationContainer, String[] blobList) throws IOException {
        // this method is to archive the replicated blobs

        System.out.println("\nTransferring replicated blobs in destination container to archive tier...");

        for(String b : blobList) {

            Process p = null;

            // transferring blobs to archive
            p = new ProcessBuilder("cmd.exe", "/c", "az storage blob set-tier --connection-string " + destinationConnectionString + " --container-name " + destinationContainer + " --name " + b + " --tier Archive").start();
            // Reading outputs from command line
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((r.readLine()) != null) {}
        }

        System.out.println("\nArchived replicated blobs!");
    }

}
