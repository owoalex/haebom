package com.baldeonline.haebom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class CertificateHandler {
	public String thisJar = "";

	public CertificateHandler() {
		try {
			thisJar = getJarFile().toString();
			thisJar = thisJar.substring(6).replace("%20", " ");
		} catch (IOException e) {
			thisJar = "truststore.zip";
			e.printStackTrace();
		}
		//System.out.println(thisJar);
	}
	
	public static TrustManagerFactory buildTrustManagerFactory() {
		try {
			TrustManagerFactory trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			try {
				KeyStore ks;
				ks = KeyStore.getInstance(KeyStore.getDefaultType());
				ks.load(null); // You don't need the KeyStore instance to come from a file.		
				
				File dir = new File(Bot.botRootDirectory + "truststore");
				File[] directoryListing = dir.listFiles();
				if (directoryListing != null) {
					for (File child : directoryListing) {
						try {	
							InputStream is = new FileInputStream(child);
							System.out.println("Trusting Certificate: "+child.getName().substring(0, child.getName().length() - 4));
							CertificateFactory cf = CertificateFactory.getInstance("X.509");
							X509Certificate caCert = (X509Certificate)cf.generateCertificate(is);
							ks.setCertificateEntry(child.getName().substring(0, child.getName().length() - 4), caCert);
						} catch (FileNotFoundException e2) {
							e2.printStackTrace();
						}
					}
				}
				
				
				//J:\Libraries\Documents\Projects\Minecraft Launchpad\truststore\DST Root CA X3.crt
//				
//				try {	
//					InputStream is = new FileInputStream("J:\\Libraries\\Documents\\Projects\\Minecraft Launchpad\\truststore\\DST Root CA X3.crt");
//					CertificateFactory cf = CertificateFactory.getInstance("X.509");
//					X509Certificate caCert = (X509Certificate)cf.generateCertificate(is);
//					ks.setCertificateEntry("caCert", caCert);
//				} catch (FileNotFoundException e2) {
//					e2.printStackTrace();
//				}
				
				trustManager.init(ks);
			} catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e1) {
				e1.printStackTrace();
			}
			
			return trustManager;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static TrustManager[] getTrustManagers() {
		TrustManagerFactory trustManager = buildTrustManagerFactory();
		return trustManager.getTrustManagers();
	}
	
	public void loadCertificates() {
		try {
			UnzipLib.unzipFolder(thisJar, "truststore", Bot.botRootDirectory + "truststore");
			System.out.println("Extracted truststore to "+ Bot.botRootDirectory);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void extractFile(Path zipFile, String fileName, Path outputFile) throws IOException {
	    // Wrap the file system in a try-with-resources statement
	    // to auto-close it when finished and prevent a memory leak
	    try (FileSystem fileSystem = FileSystems.newFileSystem(zipFile, null)) {
	        Path fileToExtract = fileSystem.getPath(fileName);
	        Files.copy(fileToExtract, outputFile);
	    }
	}
	
	private File getJarFile() throws FileNotFoundException {
	    String path = Bot.class.getResource(Bot.class.getSimpleName() + ".class").getFile();
	    if(path.startsWith("/")) {
	        throw new FileNotFoundException("This is not a jar file: \n" + path);
	    }
	    path = ClassLoader.getSystemClassLoader().getResource(path).getFile();

	    return new File(path.substring(0, path.lastIndexOf('!')));
	}
}
