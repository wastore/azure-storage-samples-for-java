package com.blobs.quickstart;

import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import org.apache.commons.lang3.time.StopWatch;

import java.io.*;
import java.util.ArrayList;

public class PerformanceTesting
{
    public static void main( String[] args ) throws IOException
    {
        String connectStr = "<CONNECTION_STRING>";
        String containerName = "<CONTAINER_NAME>";
        // File to upload
        String fileName = "64.txt";

        // Create a BlobServiceClient object which will be used to create a container client
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectStr).buildClient();
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

        //Parameters to test
        long[] sizeArray = {(1*1024*1024), (16*1024*1024), (32*1024*1024), (48*1024*1024), (64*1024*1024)};
        long[] blockArray = {(1*1024*1024), (16*1024*1024), (32*1024*1024), (48*1024*1024), (64*1024*1024)};
        int[] concurrency = {1, 2, 3, 4, 5, 10, 15, 20};

        int total_count = -1;
        ArrayList<Double> arrayList = new ArrayList<>();

        //Loop through all settings and upload 50 blobs for each setting
        for(long maxSize : sizeArray){
            for(long blockSize : blockArray){
                for(int max_con : concurrency){
                    long milliseconds = 0;
                    int count = 0;
                    ParallelTransferOptions parallelTransferOptions = new ParallelTransferOptions().setMaxConcurrency(max_con).setBlockSizeLong(blockSize).setMaxSingleUploadSizeLong(maxSize);
                    for(int j = 0; j<50; j++){
                        BlobClient blobClient = containerClient.getBlobClient(fileName + String.valueOf(j));

                        // Record time for each upload
                        StopWatch watch = new StopWatch();
                        watch.start();
                        blobClient.uploadFromFile(fileName, parallelTransferOptions, null, null, null, null, null);
                        watch.stop();
                        milliseconds += watch.getTime();
                        count++;


                        blobClient.delete();
                    }

                    // Calculate Average
                    total_count++;
                    double average = milliseconds/count;
                    arrayList.add(average);
                    System.out.println("Setting "+ String.valueOf(total_count) +": maxSize = " + String.valueOf(maxSize/(1024*1024)) + " / blockSize = " + String.valueOf(blockSize/(1024*1024)) + " / max_concurrency = " + String.valueOf(max_con) + ": " + average);
                }
                System.out.println();
            }
        }

        // Create copy of ArrayList
        ArrayList<Double> copy = new ArrayList<Double>(arrayList.size());
        for(Double i : arrayList){
            copy.add(new Double(i));
        }

        // Print slowest times
        System.out.println("Printing Slowest Time: ");
        for(int i = 0; i<5; i++){
            int ind = 0;
            double max = copy.get(0);
            for(int j = 1; j<copy.size();j++){
                if(copy.get(j)>max){
                    max = copy.get(j);
                    ind = j;
                }
            }
            copy.set(ind, 0.0);
            System.out.println(String.valueOf(i+1) + ": Setting #" + String.valueOf(ind) + " (" + String.valueOf(max) + ")");
        }
        System.out.println();

        // Print Fastest Times
        System.out.println("Printing Fastest Times: ");
        for(int i = 0; i<5; i++){
            int index = 0;
            double min = arrayList.get(0);
            for(int j = 1; j<arrayList.size();j++){
                if(arrayList.get(j)<min){
                    min = arrayList.get(j);
                    index = j;
                }
            }
            arrayList.set(index, (double)(1024*1024));
            System.out.println(String.valueOf(i+1) + ": Setting #" + String.valueOf(index) + " (" + String.valueOf(min) + ")");
        }
    }
}