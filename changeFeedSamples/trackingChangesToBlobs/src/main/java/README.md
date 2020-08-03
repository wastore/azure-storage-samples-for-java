## Additional Setup and Instructions for Changefeed Example
This sample will show how to track events in your storage account, which include events such as blobs getting created and
deleted. Right now the sample is filtering for the creation and deletion of blobs whose names include 'exampleBlob.txt'.
These blobs are created in the ExampleEventCreator file, which will be run as part of this example. To change filters, 
the user must go into ChangeFeed.java file and change the predicates that are being used. The output for each event 
can also be modified to only display necessary information. 

#### Setup
Requires installation of [Java](https://docs.microsoft.com/en-us/java/azure/jdk/?view=azure-java-stable) 
(at least JDK 8) 
and Maven. Must have an [Azure subscription](https://azure.microsoft.com/en-us/free/) and 
[create a storage account](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal).
Sample is best run using IntelliJ.

#### Code Sample Specific Setup
Requires modification of app.config file in exampleEventCreator folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 
#### Step-by-Step Instructions to Run Program
1. Follow setup instructions above. Make sure all necessary installations are done and storage account is made
2. Navigate to directory exampleEventCreator
3. Open app.config and fill in values for all variables
4. Compile ExampleEventCreator.java and run ExampleEventCreator, then stop immediately.
5. Navigate up one directory
6. Compile ChangeFeed.java and run Changefeed, then stop immediately
7. In IntelliJ, click on Run menu -> Edit Configurations
8. In the right-hand pane, click "Allow parallel run" for both files that were just run. To navigate between files, 
click on desired file's name in the left-pane. 
9. Run ExampleEventCreator and Changefeed one after the other. They should run in parallel. Note that events in Changefeed
may not be updated right away, so it may be necessary to wait an hour or more for changefeed to update. 