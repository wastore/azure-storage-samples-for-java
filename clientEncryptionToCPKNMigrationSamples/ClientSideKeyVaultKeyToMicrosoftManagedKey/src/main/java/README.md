## Additional Setup for Client-Side Encryption with Key Vault to Server-Side Encryption with Microsoft-Managed Keys
This sample will show how client-side encryption works with key vault keys and upload an example blob (blobExample.txt 
found in exampleDataCreator folder) into a newly generated container in the provided storage account linked to an Azure subscription.
Then, the uploaded blob will be downloaded, decrypted, then reuploaded into the same container with server-side encryption
using Microsoft-managed keys.

#### General Setup
Requires installation of [Java](https://docs.microsoft.com/en-us/java/azure/jdk/?view=azure-java-stable) 
(at least JDK 8)
and Maven. Must have an [Azure subscription](https://azure.microsoft.com/en-us/free/) and 
[create a storage account](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal).

#### Code Sample Specific Setup
Must create a service principal and connect it to a key vault associated with Azure subscription. Modify app.config 
to contain the following variables whose values are found in service principal:
 * *clientId*
 * *clientSecret*
 * *tenantId*

Requires modification of app.config file in exampleDataCreator folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 * *keyVaultUrl*
 * *resourceGroup*
 * *subscription*
 
Requires the following variables in same app.config file, should not be changed if using given exampleDataCreator.
  * *containerName*
  * *blobName*
  * *blobNameAfterMigration* (name for reuploaded blob)
  * *clientSideEncryptionKeyName*
  * *encryptionScope* (name of encryption scope)
  * *keyWrapAlgorithm*
  
#### Step-by-Step Instructions to Run Program
1. Follow setup instructions above. Make sure all necessary installations are done, service principal is created, and
storage account is made
2. Navigate to directory exampleDataCreator
3. Open app.config and fill in values for all variables
4. Compile ExampleDataCreator.java and run ExampleDataCreator
5. Navigate up one directory
6. Compile Migration.java and run Migration
