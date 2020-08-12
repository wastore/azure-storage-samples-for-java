#README
###Understanding this sample
The purpose of this sample is to demonstrate what object replication does.

###Getting started
Currently, creating object replication is unavailable in command line. If you have not yet set up object replication,
please follow the instructions in the links below, using Azure portal when possible.

To register for preview:
https://docs.microsoft.com/en-us/azure/storage/blobs/object-replication-overview?tabs=azure-cli

To configure and create object replication:
https://docs.microsoft.com/en-us/azure/storage/blobs/object-replication-configure?tabs=portal

###Prerequisites
Object replication configured and set up (see links above for instructions)

Azure account with an active subscription

Java 8

Apache Maven

Compatible IDE

###To Use
Follow the instructions in the links under "Getting Started" if you do not already have ORS set up

Go Through the app.config file and make any necessary changes

Run the file ObjectReplicationMonitor.java to get a demonstration. This demo will upload blobs to the source container. 
Then, the program may pause for a few minutes to allow for the replication to process. Finally, the program will locate 
the blobs that have been replicated into the destination container and output those blobs' properties that check for 
successful replication. This will output a percentage status to track how many blobs have successfully replicated.
Then one blob pair from the source and destination container's contents will be printed to ensure that the blobs 
correctly replicated from the source container to the destination container.

###Issues with Archiving
This program has two different implementations of archiving replicated blobs: using batch or archiving individually.
To choose which implementation you would like to use, set archiveMethod in app.config to "batch" or "individual".
#####Using Batch to Archive
Currently, there are issues with using batch to archive. In order to navigate these issues, follow these steps:
1. Access the storage account that contains the blobs you want to archive
2. Navigate to Configuration
3. Disable "Secure Transfer Required"
4. When adding this storage account's connection string to the config.app, change "https" to "http" in the connection string
5. Set archiveMethod in app.config to "batch"
[here is the reported issue](https://github.com/Azure/azure-sdk-for-net/issues/13524)
#####Archiving Blobs Individually
If you do not want to use batch, the other option is to archive each blob individually. All that is required is to set
archiveMethod in app.config to "individual".