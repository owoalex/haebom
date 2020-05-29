package com.baldeonline.haebom;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.concentus.*;

import org.abstractj.kalium.NaCl;

import org.json.JSONObject;

public class FilePlayer implements Runnable {
	private Thread t;
	private String mediaResource;
	private String endpointServer; // ip addres
	private int endpointPort;
	public ServerHandler serverHandler;
    private DatagramSocket socket;
    private InetAddress address;
    
    private static final byte[] silenceBytes = new byte[] {(byte)0xF8, (byte)0xFF, (byte)0xFE};
	
	public FilePlayer(String mrl, String endpoint, int port) {
		try {
			System.out.println("PCM FilePlayer ready for " + mrl);
			mediaResource = mrl;
			endpointServer = endpoint;
			endpointPort = port;
			socket = new DatagramSocket();
	        address = InetAddress.getByName(endpointServer);
		} catch (SocketException | UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		System.err.println("Playing " + mediaResource + " to " + endpointServer + ":" + String.valueOf(endpointPort));
		try {
			long unixTime = System.currentTimeMillis() / 1000L;
			short seq = 0;
			OpusEncoder opusEncoder = new OpusEncoder(48000, 2, OpusApplication.OPUS_APPLICATION_VOIP);
			
			byte[] inputPCM = readBytesFromFile(mediaResource);
			//http://opus-codec.org/docs/opus_api-1.2/group__opus__encoder.html#gad2d6bf6a9ffb6674879d7605ed073e25
			int frameLength = 960; // at 48 kHz the permitted values are 120, 240, 480, 960, 1920, and 2880    FRAME LENGTH IS IN SAMPLES (Time)
			//An opus frame size of 960 at 48000hz represents 20 milliseconds of audio.
			//System.err.println("Bytes encoded: " + framesWritten);
			
			int outputBytesPerFrame = 256;
			
			int runningOffset = 0;
			
			byte NULL_BYTE = simulateUnsignedByte(0);
			

			byte[] header = new byte[24];
			byte[] secretKeyBytes = new byte[32];
			System.err.println(serverHandler.voiceSessionSecretKey.length());
			for (int i=0; i<serverHandler.voiceSessionSecretKey.length(); i++) {
				secretKeyBytes[i] = simulateUnsignedByte(serverHandler.voiceSessionSecretKey.getInt(i));
			}
			
			
			
			//Discord required xsalsa20_poly1305
			//package org.abstractj.kalium;

			for (int i=0; i<((inputPCM.length / 2)/frameLength); i++) { // /2 for stereo
				byte[] outputOpusFrame = new byte[outputBytesPerFrame];
				int bytesWritten = opusEncoder.encode(inputPCM, frameLength * i, frameLength, outputOpusFrame, 0, outputOpusFrame.length);
				int sampleTime = i * frameLength;
				runningOffset = runningOffset + bytesWritten;
				ByteBuffer bufferts = ByteBuffer.allocate(4);
			    bufferts.order(ByteOrder.BIG_ENDIAN);
			    bufferts.putInt(simulateUnsignedInt(sampleTime));
			    bufferts.flip();
				byte[] timestamp = bufferts.array();
				ByteBuffer bufferssrc = ByteBuffer.allocate(4);
			    bufferssrc.order(ByteOrder.BIG_ENDIAN);
			    bufferssrc.putInt(simulateUnsignedInt(serverHandler.voiceSsrc));
			    bufferssrc.flip();
				byte[] ssrc = bufferssrc.array();
				ByteBuffer bufferseq = ByteBuffer.allocate(2);
			    bufferseq.order(ByteOrder.BIG_ENDIAN);
			    bufferseq.putShort(simulateUnsignedShort(seq));
			    bufferseq.flip();
			    seq = (short) (seq + 1);
				byte[] sequence = bufferseq.array();
				byte[] outBuffer = new byte[] {simulateUnsignedByte(0x80),simulateUnsignedByte(0x78),sequence[0],sequence[1],timestamp[0],timestamp[1],timestamp[2],timestamp[3],ssrc[0],ssrc[1],ssrc[2],ssrc[3]};
				byte[] nonce = ArrayHelper.concatenate(outBuffer, new byte[] {NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE}); //12 null bytes
				
				byte[] oneFrame = Arrays.copyOfRange(outputOpusFrame, 0, bytesWritten);
				byte[] oneFramePadded = ArrayHelper.concatenate(new byte[] {NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE,NULL_BYTE},oneFrame); //16 null bytes
				byte[] cypherFrame = new byte[oneFramePadded.length];
				NaCl.Sodium sodiumInstance = NaCl.sodium();
				sodiumInstance.crypto_secretbox_xsalsa20poly1305(cypherFrame, oneFramePadded, oneFramePadded.length, nonce, secretKeyBytes);
				
				sendPacket(ArrayHelper.concatenate(outBuffer,cypherFrame));
				
				System.out.println(bytesToBinary(outBuffer));
				System.out.println(bytesToHex(outBuffer));
				System.err.println(bytesToHex(oneFramePadded));
				System.err.println(bytesToHex(cypherFrame));
				System.err.println(bytesToHex(ArrayHelper.concatenate(outBuffer,Arrays.copyOfRange(cypherFrame,16,cypherFrame.length))));
				Thread.sleep(8); //8
			}
		} catch (OpusException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public static byte simulateUnsignedByte(int input) {
		int output = input;
		if (input >= 128) {
			output = input -256;
		}
		return (byte) input;
	}
	
	public static short simulateUnsignedShort(int input) {
		int output = input;
		if (input >= 32768) {
			output = input -65536;
		}
		return (short) input;
	}
	
	public static int simulateUnsignedInt(int input) {
		long output = (long) input;
		if (input >= Long.parseLong("2147483648")) {
			output = input - Long.parseLong("4294967296");
		}
		return (int) input;
	}
	
	public static String bytesToBinary(byte[] bytes) {
	    StringBuilder sb = new StringBuilder();
	    for (byte b : bytes) {
	        ByteBuffer bb = ByteBuffer.allocate(2);
	        bb.put((byte) 0);
	        bb.put(b);
	        String s = Integer.toBinaryString(bb.getShort(0));
	        for (int i=0;i<(8-s.length());i++) {
	        	sb.append("0");
	        }
	        sb.append(s);
	    }
	    return sb.toString();
	}
	
	public void sendPacket(byte[] buffer) {
        try {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, endpointPort);
			socket.send(packet);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	public void endPlay() {
		socket.close();
	} 
	
	public void start() {
		if (t == null) {
			t = new Thread (this, String.valueOf(serverHandler.serverSnowflake) + "flp");
			t.start ();
		}
	}
	
	byte[] readBytesFromFile(String inputFileName){
		File file = new File(inputFileName);
		byte[] result = new byte[(int)file.length()];
		try {
			InputStream input = null;
			try {
				int totalBytesRead = 0;
				input = new BufferedInputStream(new FileInputStream(file));
				while(totalBytesRead < result.length){
					int bytesRemaining = result.length - totalBytesRead;
					//input.read() returns -1, 0, or more :
					int bytesRead = input.read(result, totalBytesRead, bytesRemaining); 
					if (bytesRead > 0){
						totalBytesRead = totalBytesRead + bytesRead;
					}
				}
			}
			finally {
				input.close();
			}
		} catch (FileNotFoundException ex) {
			System.err.println("File not found.");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		return result;
	}
}
