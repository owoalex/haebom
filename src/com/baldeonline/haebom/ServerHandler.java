package com.baldeonline.haebom;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Random;
import org.concentus.OpusEncoder;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

class VoiceChannelWebSocketClientHandler extends WebSocketClient {
	public ServerHandler serverRoot;
	public VoiceChannelWebSocketClientHandler(URI serverUri) {
		super(serverUri);
	}
	@Override
	public void onOpen(ServerHandshake handshakedata) {
		System.out.println("Connected to " + this.uri);

	}
	@Override
	public void onMessage(String message) {
		try {
			//System.out.println("got: " + message);
			JSONObject retdata = new JSONObject(message);
			int opCode = retdata.getInt("op");
			JSONObject data;
			try {
				data = retdata.getJSONObject("d");
			} catch (Exception e2) {
				data = null;
			}
			switch (opCode) {
				case 8:{ //HELLO
					int heartbeatInterval = (int) ((data.getInt("heartbeat_interval") - 1250) * 0.75f);
					System.out.println("Got voice hello");
					System.out.println("Voice heartbeat every " + String.valueOf(heartbeatInterval) + "ms");
					Bot.webSocketEventScheduler.scheduleEvent("voiceheartbeat" + String.valueOf(serverRoot.serverSnowflake), heartbeatInterval);
				break;}
				case 2:{ //VOICE READY
					System.out.println("Got voice ready");
					System.err.println(data.toString(4));
					serverRoot.voiceSsrc = data.getInt("ssrc");
					serverRoot.voiceIp = data.getString("ip");
					serverRoot.voicePort = data.getInt("port");
					serverRoot.voiceModes = data.getJSONArray("modes");
					
					JSONObject outObject = new JSONObject();
					JSONObject outData = new JSONObject();
					outObject.put("op", 1);
					outData.put("protocol", "udp");
					JSONObject dataObj = new JSONObject();
					dataObj.put("address", Bot.OWN_EXTERNAL_IP_ADDRESS);
					dataObj.put("port", Bot.OWN_EXTERNAL_PORT);
					dataObj.put("mode", "xsalsa20_poly1305");
					outData.put("data", dataObj);
					outObject.put("d", outData);
					
					this.send(outObject.toString());
				break;}
				case 4:{ //Session Description Payload
					serverRoot.voiceSessionSecretKey = data.getJSONArray("secret_key");
					System.out.println("Got session description");
					System.err.println(serverRoot.voiceSessionSecretKey.toString(4));
					
				break;}
				case 3:{ //HEARTBEAT
					System.out.println("Heartbeat recieved, sending ACK");
					JSONObject outData = new JSONObject();
					outData.put("op", 6); //HEARTBEAT ACK
					this.send(outData.toString());
				break;}
				case 6:{ //HEARTBEAT ACK
					System.out.println("Heartbeat ACK");
				break;}
			}
		} catch (Exception e) {
			System.err.println("Something went wrong at the gateway");
			e.printStackTrace();
		}
	}
	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println("Disconnected from " + this.uri);

	}
	@Override
	public void onError(Exception ex) {
		ex.printStackTrace();

	}
}


public class ServerHandler implements Runnable {
	private Thread t;
	public long serverSnowflake = -1;
	public long voiceChannelSnowflake = -1;
	public String voiceSessionId;
	public String voiceEndpoint;
	public String voiceToken;
	
	public String voiceIp;
	public int voiceSsrc;
	public int voicePort;
	public JSONArray voiceModes;
	public JSONArray voiceSessionSecretKey;
	
	public VoiceChannelWebSocketClientHandler voiceWebSocketClient;
	
	private JSONObject serverInfo;
	private JSONArray channels;
	private final String[] CHANNEL_TYPE_ENUM = new String[] {"Text Channel",null,"Voice Channel",null,"Channel Group"};
	public static String triggerPrefix = "haebom";
	public static String triggerSuffix = "!";
	private long lastTextChannel;
	
	public static int BYTE_SIGNED_OFFSET = 128;
	
	public ServerHandler(long serverSnowflakeIn) {
		serverSnowflake = serverSnowflakeIn;
	}
	
	public void run() {
		serverInfo = HTTPSConnect.getJSONObject(Bot.BASE_URL + "/guilds/" + String.valueOf(serverSnowflake), Bot.defaultTrustManagers);
		//System.out.println(serverInfo.toString(4));
		System.out.println("Server Name  : " + serverInfo.getString("name"));
		System.out.println("Snowflake    : " + serverInfo.getString("id"));
		System.out.println("Region       : " + serverInfo.getString("region"));
		channels = HTTPSConnect.getJSONArray(Bot.BASE_URL + "/guilds/" + String.valueOf(serverSnowflake) + "/channels", Bot.defaultTrustManagers);
		System.out.print("Channels     : ");
		for (int i=0; i<channels.length(); i++) {
			JSONObject channelInfo = channels.getJSONObject(i);
			int type = channelInfo.getInt("type");
			System.out.print(CHANNEL_TYPE_ENUM[type] + " - " + channelInfo.getString("name") + "\n               ");
		}
	}
		
	public void start() {
		if (t == null) {
			t = new Thread (this, String.valueOf(serverSnowflake));
			t.start ();
		}
	}

	public void sendMessage(long channel, String content) {
		JSONObject obj = new JSONObject();
		obj.put("content", content);
		obj.put("tts", false);
		HTTPSConnect.sendJSONObject(Bot.BASE_URL + "/channels/" + String.valueOf(channel) + "/messages", obj, Bot.defaultTrustManagers);
	}
	
	public void processMessage(JSONObject fullMessage) {
		//System.out.println("Baka " + fullMessage.getString("content"));
		String content = fullMessage.getString("content");
		if (content.startsWith(triggerPrefix) || content.endsWith(triggerSuffix)) {
			System.out.println(fullMessage.toString(4));
			if (content.startsWith(triggerPrefix)) {
				content = content.substring(triggerPrefix.length(), content.length());
			}
			if (content.endsWith(triggerSuffix)) {
				content = content.substring(0, content.length() - triggerSuffix.length());
			}
			System.out.println(content);
			long channel = Long.parseLong(fullMessage.getString("channel_id"));
			//sendMessage(channel, "owo " + content + " uwu");
			long authorId = Long.valueOf(fullMessage.getJSONObject("author").getString("id"));
			lastTextChannel = channel;
			String[] commandParts = new String[0];
			int bufferStart = 0;
			char separator = " ".charAt(0);
			String buffer = "";
			boolean strict = false;
			for (int i=0; i<content.length(); i++) {
				if (content.charAt(i) == separator) {
					if ((strict == false) && (buffer.length() == 0)) {
						//ignore too many spaces
					} else {
						commandParts = ArrayHelper.concatenate(commandParts, new String[] {buffer});
					}
					buffer = "";
					if (content.charAt(i) == "\"".charAt(0)) {
						i++; //skip the next space
						separator = " ".charAt(0);
						strict = false;
					}
				} else {
					if (content.charAt(i) == "\"".charAt(0)) {
						separator = content.charAt(i);
						strict = true;
					} else {
						buffer = buffer + content.charAt(i);
					}
				}
			}
			commandParts = ArrayHelper.concatenate(commandParts, new String[] {buffer});
			for (int i=0; i<commandParts.length; i++) {
				System.out.println("[" + commandParts[i] + "]");
			}
			if (commandParts.length > 0) {
				switch (commandParts[0].toLowerCase()) {
					case "uwu": {
						if (fullMessage.getJSONObject("member").has("nick")) {
							sendMessage(channel, "owo " + fullMessage.getJSONObject("member").getString("nick"));
						} else {
							sendMessage(channel, "owo " + fullMessage.getJSONObject("author").getString("username"));
						}
					break;}
					case "annyeong": {
						sendMessage(channel, "annyeong oppa!");
					break;}
					case "join": {
						joinChannel(authorId);
					break;}
					case "test": {
						System.out.println("");
						
						ByteBuffer bufferts = ByteBuffer.allocate(1);
					    bufferts.order(ByteOrder.BIG_ENDIAN);
					    //bufferts.putInt((int) unixTime);
					    bufferts.put(simulateUnsignedInt(0));
					    bufferts.flip();
						byte[] timestamp = bufferts.array();
						System.err.println(printBytes(timestamp));
						
						bufferts = ByteBuffer.allocate(1);
					    bufferts.order(ByteOrder.BIG_ENDIAN);
					    //bufferts.putInt((int) unixTime);
					    bufferts.put(simulateUnsignedInt(128));
					    bufferts.flip();
						timestamp = bufferts.array();
						System.err.println(printBytes(timestamp));
						
						bufferts = ByteBuffer.allocate(1);
					    bufferts.order(ByteOrder.BIG_ENDIAN);
					    //bufferts.putInt((int) unixTime);
					    bufferts.put(simulateUnsignedInt(255));
					    bufferts.flip();
						timestamp = bufferts.array();
						System.err.println(printBytes(timestamp));
						
					break;}
					case "microwave": {
						playPCMFile("microwave.pcm");
					break;}
					default: {
						sendMessage(channel, "Oppa... tha... that's not a valid command ;-;");
					}
				}
			}
		}
	}
	
	public static byte simulateUnsignedInt(int input) {
		int output = input;
		if (input >= 128) {
			output = input -256;
		}
		return (byte) input;
	}
	
	public static String printBytes(byte[] bytes) {
		String bl = "";
		for (byte b : bytes) {
		    bl = bl + Integer.toBinaryString(b & 255 | 256).substring(1);
		}
		return bl;
	}
	
	public void joinChannel(long authorId) {
		JSONObject guildinfo = HTTPSConnect.getJSONObject(Bot.BASE_URL + "/guilds/" + String.valueOf(serverSnowflake), Bot.defaultTrustManagers);
		
		long inChannel = Bot.getVoiceChannelFromUserId(authorId);
		
		//System.err.println(guildinfo.toString(4));
		
		if (inChannel == -1) {
			sendMessage(lastTextChannel, "B... but... you're not here :(");
		} else {
			voiceChannelSnowflake = inChannel;
			JSONObject outObject = new JSONObject();
			JSONObject outData = new JSONObject();
			outObject.put("op", 4);
			
			outData.put("guild_id", String.valueOf(serverSnowflake));
			outData.put("channel_id", String.valueOf(voiceChannelSnowflake));
			outData.put("self_mute", false);
			outData.put("self_deaf", false);
			
			outObject.put("d", outData);
			System.err.println(outObject.toString(4));
			
			voiceToken = "";
			voiceSessionId = "";
			
			Bot.webSocketClient.send(outObject.toString());
			
			sendMessage(lastTextChannel, "Attempting to join the channel...");
		}
	}
	
	public void continueVoiceConnectionInit() {
		try {
			sendMessage(lastTextChannel, "Joining the channel...");
			
			String rawServerName = voiceEndpoint.split(":")[0];
			String webSocketsUri = "wss://" + rawServerName + "?v=3";
			System.out.println(webSocketsUri);
	
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2"); 
			sslContext.init(null, Bot.defaultTrustManagers, new SecureRandom());
			SSLSocketFactory factory = sslContext.getSocketFactory();
			
			voiceWebSocketClient = new VoiceChannelWebSocketClientHandler(new URI(webSocketsUri));
			voiceWebSocketClient.setSocketFactory(factory);
			voiceWebSocketClient.serverRoot = this;
			voiceWebSocketClient.connectBlocking();
			
			System.out.println("Identifying...");
			JSONObject outData = new JSONObject();
			outData.put("op", 0);
			JSONObject dataOut = new JSONObject();
			dataOut.put("server_id", serverSnowflake);
			dataOut.put("user_id", Bot.BOT_SNOWFLAKE);
			dataOut.put("session_id", voiceSessionId);
			dataOut.put("token", voiceToken);
			
			outData.put("d", dataOut);
			voiceWebSocketClient.send(outData.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void sendVoiceHeartbeat() {
		System.out.println("Voice Heartbeat");
		JSONObject outData = new JSONObject();
		outData.put("op", 3);
		Random r = new Random();
		outData.put("d", r.nextInt(((int) Math.pow(2, 32))));
		voiceWebSocketClient.send(outData.toString());
	}
	
	public void playPCMFile(String mrl) {
		System.out.println("Playing PCM File " + mrl);
		JSONObject outData = new JSONObject();
		outData.put("op", 5);
		JSONObject dataOut = new JSONObject();
		dataOut.put("speaking", true);
		dataOut.put("delay", 0);
		dataOut.put("ssrc", voiceSsrc);
		
		outData.put("d", dataOut);
		voiceWebSocketClient.send(outData.toString());
		
		FilePlayer fp1 = new FilePlayer(mrl,voiceIp,voicePort);
		fp1.serverHandler = this;
		fp1.start();
	}
}
