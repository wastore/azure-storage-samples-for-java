## Additional Setup and Instructions for Changefeed Example
This sample will show how to track events in your storage account, which include events such as blobs getting created and
deleted. Right now the sample is filtering for the creation and deletion of blobs whose names include 'exampleBlob.txt'.
These blobs are created in the ExampleEventCreator file, which will be run as part of this example. To change filters, 
the user must go into ChangeFeed.java file and change the predicates that are being used. 

#### Setup
Requires installation of [Java](https://docs.microsoft.com/en-us/java/azure/jdk/?view=azure-java-stable) 
(at least JDK 8) 
and Maven. Must have an [Azure subscription](https://azure.microsoft.com/en-us/free/) and 
[create a storage account](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal).

#### Code Sample Specific Setup
Requires modification of app.config file in exampleEventCreator folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 
#### Step-by-Step Instructions to Run Program
1. Follow setup instructions above. Make sure all necessary installations are done and storage account is made
2. Navigate to directory exampleEventCreator
3. Open app.config and fill in values for all variables
4. Compile ExampleEventCreator.java and run ExampleEventCreator
5. Navigate up one directory
6. Compile ChangeFeed.java and run Changefeed. Note that events may not be updated right away, so it may be necessary
to wait an hour or more for changefeed to update. 