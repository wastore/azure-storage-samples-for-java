## Additional Setup for Client-side to CPK-N Code Samples

#### General Setup
Requires installation of Java (at least JDK 11) and Maven. Must have an Azure subscription and create a storage account.

#### KeyVaultClientEncryption 
This sample will show client-side decryption using a key from key vault, and then upload two files with server-side
encryption. One will be encrypted with Microsoft-managed keys, and the other by customer-managed keys with
key vault through the creation of an encryption scope. \
In addition to the general setup, create a service principal with access to key vault. From service principal, 
find client id, client secret (password), and tenant id. \
Requires setting and naming the following environmental variables exactly as listed below 
(must be in **system** environmental variables):
  * *CLIENT_ID*
  * *CLIENT_SECRET*
  * *TENANT_ID*
  * *sharedKeyCred* (shared key credential for accessing storage account)
  * *keyVaultUrl* (obtained through creating service principal)
  * *storageAccount*
  * *resourceGroup* (of storage account)
  * *subscription* (of resource group)
             
#### LocalKeyClientEncryption
Requires setting and naming the following environmental variables exactly as listed below (must be in 
**system** environmental variables):
 * *sharedKeyCred* (for storage account)
 * *storageAccount*
 
This sample will show client-side decryption using a local key, and then upload two files with server-side
encryption. One will be encrypted with Microsoft-managed keys, and the other by customer-provided keys.