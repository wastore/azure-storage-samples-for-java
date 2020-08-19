## Additional Setup for Client-Side Encryption with Local Key to Server-Side Encryption with Customer-Provided Keys
This sample will show how client-side encryption works with local keys and upload an example blob (blobExample.txt found
in exampleDataCreator folder) into a newly generated container in the provided storage account linked to an Azure subscription. Then,
the uploaded blob will be downloaded, decrypted, then reuploaded into the same container with server-side encryption
using customer-provided keys. Both encryptions will use the same, insecure local key. 

#### General Setup
Requires installation of [Java](https://docs.microsoft.com/en-us/java/azure/jdk/?view=azure-java-stable) 
(at least JDK 8)
and Maven. Must have an [Azure subscription](https://azure.microsoft.com/en-us/free/) and 
[create a storage account](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal).

#### Code Sample Specific Setup
Requires modification of app.config file in exampleDataCreator folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 
Requires the following variables in same app.config file, should not be changed if using given exampleDataCreator.
  * *clientSideLocalKeyFileName* (stores byte array that represents local key)
  * *serverSideLocalKeyFileName* (stores byte array that represents local key)
  * *containerName*
  * *blobName*
  * *blobNameAfterMigration* (name for reuploaded blob)
  
#### Step-by-Step Instructions to Run Program
1. Follow setup instructions above. Make sure all necessary installations are done, service principal is created, and
storage account is made
2. Navigate to directory exampleDataCreator
3. Open app.config and fill in values for all variables
4. Compile ExampleDataCreator.java and run ExampleDataCreator
5. Navigate up one directory
6. Compile Migration.java and run Migration
