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

        String source_container_name = prop.getProperty("source_container_name");
        String source_storage_account_connection_string = prop.getProperty("source_storage_account_connection_string");
        String destination_container_name = prop.getProperty("destination_container_name");
        String destination_storage_account_connection_string = prop.getProperty("destination_storage_account_connection_string");
        String blobs_to_replicate = prop.getProperty("blobs_to_replicate");

        String[] replicated_blob_list = blobs_to_replicate.split(", ");

        // sample of replication status
        System.out.println("\nThis is an example of using ORS...");

        // call method to setup blob in source container
        setupSource(source_storage_account_connection_string, source_container_name, replicated_blob_list);

        // call method to check the replication status of the blob in source container
        System.out.println("\nReplicating blobs to destination container...");
        System.out.println("Once the replication process begins, the replication status will output...");
        checkReplication(source_storage_account_connection_string, source_container_name, replicated_blob_list);

        // call method to check the contents of a replicated blob
        checkContents(source_storage_account_connection_string, source_container_name, destination_storage_account_connection_string, destination_container_name, replicated_blob_list);

        // call method to archive replicated blobs
        archiveReplicatedData(destination_storage_account_connection_string, destination_container_name, replicated_blob_list);

    }


    private static void setupSource(String connection_string, String container_name, String[] blob_list) {
        // this method is to upload a blob to the source container

        System.out.println("\nUploading blobs to the source container...");

        // creating blobserviceclient in order to make containerclient
        BlobServiceClient sourceBlobServiceClient = new BlobServiceClientBuilder().connectionString(connection_string).buildClient();

        // get the container client
        BlobContainerClient sourceContainerClient = sourceBlobServiceClient.getBlobContainerClient(container_name);
        if (!sourceContainerClient.exists()) {
            sourceContainerClient.create();
        }

        // iterate through list of blobs
        for (String b : blob_list) {

            // get blob client
            BlobClient sourceBlobClient = sourceContainerClient.getBlobClient(b);

            // upload the blob
            String content = "Hello World!";
            ByteArrayInputStream dataStream = new ByteArrayInputStream(content.getBytes());
            sourceBlobClient.upload(dataStream, content.length(), true);

        }

    }


    private static void checkReplication(String connection_string, String container_name, String[] blob_list) {
        // this method is to check the replication status and the contents of the blob

        // creating blobserviceclient to access containerclient
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connection_string).buildClient();

        // get the container client to access blob client
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(container_name);

        int completed = 0;
        double ratio = 0;
        int size = blob_list.length;
        ArrayList completed_events = new ArrayList();

        // while loop for continuous updating on replication status
        while (completed < size) {

            // iterate through list of blobs
            for (String b : blob_list) {

                // check if event has already been completed
                if (!completed_events.contains(b)) {
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
                                completed_events.add(b);

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


    private static void checkContents(String source_connection_string, String source_container, String destination_connection_string, String destination_container, String[] blob_list) throws FileNotFoundException {
        // this method is to check that the contents of a replicated blob transferred correctly

        String[] connection_string = {source_connection_string, destination_connection_string};
        String[] container_name = {source_container, destination_container};

        for (int s = 0; s < connection_string.length; s++) {
            // creating blobserviceclient to access containerclient
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connection_string[s]).buildClient();

            // get the container client to access blob client
            BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(container_name[s]);

            // print out contents of one blob to show successful replication
            BlobClient blobClient = blobContainerClient.getBlobClient(blob_list[0]);

            System.out.println("\nContent of " + blob_list[0] + " in the " + container_name[s] + " container:");

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


    private static void archiveReplicatedData(String destination_connection_string, String destination_container, String[] blob_list) throws IOException {
        // this method is to archive the replicated blobs

        System.out.println("\nTransferring replicated blobs in destination container to archive tier...");

        for(String b : blob_list) {

            Process p = null;

            // transferring blobs to archive
            p = new ProcessBuilder("cmd.exe", "/c", "az storage blob set-tier --connection-string " + destination_connection_string + " --container-name " + destination_container + " --name " + b + " --tier Archive").start();
            // Reading outputs from command line
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((r.readLine()) != null) {}
        }

        System.out.println("\nArchived replicated blobs!");
    }

}
