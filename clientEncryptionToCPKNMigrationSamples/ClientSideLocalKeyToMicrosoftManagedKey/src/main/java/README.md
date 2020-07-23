## Additional Setup for Client-Side Encryption with Local Key to Server-Side Encryption with Microsoft-Managed Keys
This sample will show how client-side encryption works with local keys and upload an example blob (blobExample.txt found
in exampleCreation folder) into a newly generated container in the provided storage account linked to an Azure subscription. 
Then, the uploaded blob will be downloaded, decrypted, then reuploaded into the same container with server-side encryption
using Microsoft-managed keys.

#### General Setup
Requires installation of [Java](https://docs.microsoft.com/en-us/java/azure/jdk/?view=azure-java-stable) 
(at least JDK 8)
and Maven. Must have an [Azure subscription](https://azure.microsoft.com/en-us/free/) and 
[create a storage account](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal).

#### Code Sample Specific Setup
Requires modification of app.config file in exampleCreation folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 * *resourceGroup*
 * *subscription*
 
Requires the following variables in same app.config file, should not be changed if using given exampleCreation.
 * *clientSideLocalKeyFileName* (stores byte array that represents local key)
 * *containerName*
 * *blobName*
 * *blobNameAfterMigration* (name for reuploaded blob)
 * *encryptionScope* (name of encryption scope)

#### How to use files
First, ensure to follow setup as described above and that the app.config file is filled out completely. Variables that
have already been filled out are left as is if ExampleCreation.java will be run. Running the ExampleCreation is optional as 
long as if customer has a client-side encrypted blob ready to be migrated and an encryption scope created. After 
ExampleCreation is run, run Migration to perform migration to server-side encryption. 