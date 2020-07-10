## Additional Setup for Client-Side Encryption with Local Key to Server-Side Encryption with Microsoft-Managed Keys
This sample will show how client-side encryption works with local keys and upload a blob into a newly 
generated container in the provided storage account linked to an Azure subscription. Then, the uploaded blob 
will be downloaded, decrypted, then reuploaded into the same container with server-side encryption
using Microsoft-managed keys.

#### General Setup
Requires installation of Java (at least JDK 8) and Maven. Must have an Azure subscription and 
create a storage account.

#### Code Sample Specific Setup
Requires modification of app.config file in setup folder. Must add values to variables listed below:
 * *sharedKeyCred* (for storage account)
 * *storageAccount*