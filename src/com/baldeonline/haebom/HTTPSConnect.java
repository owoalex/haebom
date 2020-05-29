package com.baldeonline.haebom;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONArray;
import org.json.JSONObject;

public class HTTPSConnect {
	static String sendPOST(String POST_URL, String POST_PARAMS, TrustManager[] trustManagers) {
		try {
			URL obj = new URL(POST_URL);
			HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
			//System.setProperty("javax.net.ssl.trustStore", path_to_your_jks_file);
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2"); 
			//sslContext.init(null, tmf.getTrustManagers(), null);
			//sslContext.init(null, null, new SecureRandom());
			sslContext.init(null, trustManagers, new SecureRandom());
			con.setSSLSocketFactory(sslContext.getSocketFactory());
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json; utf-8");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("User-Agent", Bot.USER_AGENT);
			con.setRequestProperty("Authorization", "Bot " + Bot.AUTHORIZATION);
	
			// For POST only - START
			con.setDoOutput(true);
			OutputStream os = con.getOutputStream();
			os.write(POST_PARAMS.getBytes());
			os.flush();
			os.close();
			// For POST only - END
			int responseCode = 0;
				
			try {
				//sendPOST("http://localhost", postParams);
				responseCode = con.getResponseCode();
			} catch (IOException e) {
			}
			System.out.println("POST Response Code : " + responseCode);

			if (responseCode >= 400) {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						con.getErrorStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				return response.toString();
			} else {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						con.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				return response.toString();
			}

		} catch (KeyManagementException | NoSuchAlgorithmException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return "";
	}
	
	static void sendJSONObject(String urlPointer, JSONObject objectIn, TrustManager[] trustManagers) {
		String returnedData = sendPOST(urlPointer, objectIn.toString(), trustManagers);
		System.out.println(returnedData);
	}
	
	static JSONObject getJSONObject(String urlPointer, TrustManager[] trustManagers) {
		String returnedData = getData(urlPointer, trustManagers);
		try {
			return new JSONObject(returnedData);
		} catch (Exception e) {
			System.err.println(returnedData);
			return null;
		}
	}
	
	static JSONArray getJSONArray(String urlPointer, TrustManager[] trustManagers) {
		String returnedData = getData(urlPointer, trustManagers);
		try {
			return new JSONArray(returnedData);
		} catch (Exception e) {
			System.err.println(returnedData);
			return null;
		}
	}
	
	static String getData(String urlPointer, TrustManager[] trustManagers) {
		try {
			URL obj = new URL(urlPointer);
			HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
			//System.setProperty("javax.net.ssl.trustStore", path_to_your_jks_file);
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2"); 
			//sslContext.init(null, tmf.getTrustManagers(), null);
			//sslContext.init(null, null, new SecureRandom());
			sslContext.init(null, trustManagers, new SecureRandom());
			con.setSSLSocketFactory(sslContext.getSocketFactory());
			con.setRequestMethod("GET");
			con.setRequestProperty("Content-Type", "application/json; utf-8");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("User-Agent", Bot.USER_AGENT);
			con.setRequestProperty("Authorization", "Bot " + Bot.AUTHORIZATION);
			int responseCode = 0;
			try {
				responseCode = con.getResponseCode();
			} catch (IOException e) {
			}
			//System.out.println("POST Response Code : " + responseCode);

			if (responseCode >= 400) {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						con.getErrorStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				return response.toString();
			} else {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						con.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
				return response.toString();
			}

		} catch (IOException e) {
			e.printStackTrace();
			return "";
		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return "";
		} catch (KeyManagementException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return "";
		}
		
	}
}
