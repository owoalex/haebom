package com.baldeonline.haebom;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnzipLib {
	private static final int BUFFER_SIZE = 4096;
	
	public static void unzipFolder(String zipFilePath, String sourceDirectory, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + entry.getName();
            
            //System.out.println(entry.getName());
            if (entry.getName().startsWith(sourceDirectory)) {
	            if (!entry.isDirectory()) {
	                // if the entry is a file, extracts it
	            	filePath = destDirectory + entry.getName().substring(sourceDirectory.length());
					File fileTBC = new File(filePath);
					String dirAddr = fileTBC.getParent();
					File directory = new File(dirAddr);
					//System.out.println("Making Directory "+dirAddr);
					if (directory.exists()){
						//System.out.println("Directory already exists "+directory);
					} else {
						boolean success = directory.mkdirs();
						if (success) {
							//System.out.println("Created directory "+directory);
						} else {
							System.err.println("Failed to create directory "+directory);
						}
					}
	            	
	            	
	                extractFile(zipIn, filePath, new int[1], 0);
	            } else {
	                // if the entry is a directory, make the directory
	            	filePath = destDirectory + entry.getName().substring(sourceDirectory.length());
	                File dir = new File(filePath);
	                dir.mkdirs();
	            }
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }
	
	public static void unzip(String zipFilePath, String destDirectory) throws IOException {
		unzipProgress(zipFilePath, destDirectory, new int[1], 0);
	}
	
    public static void unzipProgress(String zipFilePath, String destDirectory, int[] elementsProgress, int elementId) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
            	
				File fileTBC = new File(filePath);
				String dirAddr = fileTBC.getParent();
				File directory = new File(dirAddr + File.separator);
				if (directory.exists()){
					//System.out.println("Directory already exists "+directory);
				} else {
					boolean success = directory.mkdirs();
					if (success) {
						//System.out.println("Created directory "+directory);
					} else {
						System.err.println("Failed to create directory "+directory);
					}
				}
            	
                extractFile(zipIn, filePath, elementsProgress, elementId);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath + File.separator);
                dir.mkdirs();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }
    
    /**
     * Extracts a zip entry (file entry)
     * @param zipIn
     * @param filePath
     * @throws IOException
     */
    private static void extractFile(ZipInputStream zipIn, String filePath, int[] elementsProgress, int elementId) throws IOException {
    	//System.out.println(filePath);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
            elementsProgress[elementId] = elementsProgress[elementId] + read;
        }
        bos.close();
    }
}
