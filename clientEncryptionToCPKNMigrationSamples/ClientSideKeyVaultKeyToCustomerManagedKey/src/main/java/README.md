## Additional Setup for Client-Side Encryption with Key Vault to Server-Side Encryption with Customer-Managed Keys
This sample will show how client-side encryption works with key vault keys and upload a blob into a newly 
generated container in the provided storage account linked to an Azure subscription. Then, the uploaded blob 
will be downloaded, decrypted, then reuploaded into the same container with server-side encryption
using customer-managed keys. Both encryptions will use the same key from key vault. 

#### General Setup
Requires installation of [Java](https://docs.microsoft.com/en-us/java/azure/jdk/?view=azure-java-stable) 
(at least JDK 8), [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli?view=azure-cli-latest), 
and Maven. Must have an [Azure subscription](https://azure.microsoft.com/en-us/free/) and 
[create a storage account](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal).

#### Code Sample Specific Setup
Must create a service principal and connect it to a key vault associated with Azure subscription. Set system
environmental variables to contain the following variables whose values are found in service principal:
 * *AZURE_CLIENT_ID*
 * *AZURE_CLIENT_SECRET*
 * *AZURE_TENANT_ID*

Requires modification of app.config file in setup folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 * *keyVaultUrl*
 * *resourceGroup*
 * *subscription*