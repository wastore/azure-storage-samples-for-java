#README
###Understanding this sample
The purpose of this sample is to demonstrate what object replication does.

###Getting started
Currently, creating object Replication is unavailable in command line. If you have not yet set up object replication,
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

Run the file objectReplicationMonitor.java to get a demonstration. This demo will upload a blob
to the source container and output the properties and content of that blob. Then, the program will pause for 
5 minutes to allow for the replication to process. Finally, the program will locate the blob that has been
replicated into the destination container and output that blob's properties and content to show that the
blob correctly replicated from the source container to the destination container.