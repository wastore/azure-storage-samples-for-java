## Additional Setup for Client-Side Encryption with Key Vault to Server-Side Encryption with Customer-Provided Keys
This sample will show how client-side encryption works with key vault keys and upload an example blob (blobExample.txt 
found in setup folder) into a newly generated container in the provided storage account linked to an Azure subscription.
Then, the uploaded blob will be downloaded, decrypted, then reuploaded into the same container with server-side encryption
using customer-provided keys. 

#### General Setup
Requires installation of [Java](https://docs.microsoft.com/en-us/java/azure/jdk/?view=azure-java-stable) 
(at least JDK 8)
and Maven. Must have an [Azure subscription](https://azure.microsoft.com/en-us/free/) and 
[create a storage account](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal).

#### Code Sample Specific Setup
Must create a service principal and connect it to a key vault associated with Azure subscription. Set system
environmental variables to contain the following variables whose values are found in service principal (does not have to
 be in app.config):
 * *AZURE_CLIENT_ID*
 * *AZURE_CLIENT_SECRET*
 * *AZURE_TENANT_ID*

Requires modification of app.config file in setup folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 * *keyVaultUrl*
 
  Requires the following variables in same app.config file, should not be changed if using given setup.
  * *containerName*
  * *blobName*
  * *blobDecryptedName* (name for reuploaded blob)
  * *keyName*
