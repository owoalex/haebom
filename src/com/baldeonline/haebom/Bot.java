package com.baldeonline.haebom;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

class WebSocketClientHandler extends WebSocketClient {
	
	public Bot BotReference;
	
	public WebSocketClientHandler(URI serverUri) {
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
				case 10:{ //HELLO
					int heartbeatInterval = data.getInt("heartbeat_interval") - 1250;
					System.out.println("Connection init");
					System.out.println("Heartbeat every " + String.valueOf(heartbeatInterval) + "ms");
					BotReference.webSocketEventScheduler.scheduleEvent("heartbeat", heartbeatInterval);
					
					//System.out.println("Attempting to resume connection");
					//JSONObject outData = new JSONObject();
					//outData.put("op", 6); //RESUME
					//JSONObject dataOut = new JSONObject();
					//dataOut.put("token", Bot.AUTHORIZATION);
					//dataOut.put("session_id", Bot.sessionId);
					//dataOut.put("seq", Bot.sessionSequence);
					//outData.put("d", dataOut);
					//this.send(outData.toString());
					
					System.out.println("Identifying...");
					JSONObject outData = new JSONObject();
					outData.put("op", 2);
					JSONObject dataOut = new JSONObject();
					dataOut.put("token", BotReference.AUTHORIZATION);
					JSONObject properties = new JSONObject();
					properties.put("$os", "Windows 7");
					properties.put("$browser", "HaebomBot");
					properties.put("$device", "IBM Compatible");
					dataOut.put("properties", properties);
					dataOut.put("compress", false);
					outData.put("d", dataOut);
					this.send(outData.toString());
				break;}
				case 1:{ //HEARTBEAT
					System.out.println("Heartbeat recieved, sending ACK");
					JSONObject outData = new JSONObject();
					outData.put("op", 11); //HEARTBEAT ACK
					this.send(outData.toString());
				break;}
				case 11:{ //HEARTBEAT ACK
					System.out.println("Heartbeat ACK");
				break;}
				case 9:{ //INVALID SESSION
					System.out.println("Invalid session!!!");
					//Bot.sessionId = data.getString("session_id");
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				break;}
				case 0:{
					String eventName = retdata.getString("t");
					BotReference.sessionSequence = retdata.getInt("s");
					//System.out.println("Dispatch");
					
					//System.out.println(retdata.toString(4));
					switch (eventName) {
						case "MESSAGE_CREATE": {
							//System.out.println(retdata.toString(4));
							
							for (int i=0; i<BotReference.serverHandlers.length; i++) {
								if (Long.decode(retdata.getJSONObject("d").getString("guild_id")) == BotReference.serverHandlers[i].serverSnowflake) {
									BotReference.serverHandlers[i].processMessage(retdata.getJSONObject("d"));
								}
							}
							
						break;}
						case "MESSAGE_REACTION_ADD": {
							System.out.println(retdata.getJSONObject("d").toString(4));
						break;}
						case "VOICE_SERVER_UPDATE": {
							System.err.println(retdata.getJSONObject("d").toString(4));
							for (int i=0; i<BotReference.serverHandlers.length; i++) {
								if (BotReference.serverHandlers[i].serverSnowflake == Long.valueOf(retdata.getJSONObject("d").getString("guild_id"))) {
									
									BotReference.serverHandlers[i].voiceEndpoint = retdata.getJSONObject("d").getString("endpoint");
									BotReference.serverHandlers[i].voiceToken = retdata.getJSONObject("d").getString("token");
									if (BotReference.serverHandlers[i].voiceSessionId != "") {
										BotReference.serverHandlers[i].continueVoiceConnectionInit();
									}
								}
							}
						break;}
						case "VOICE_STATE_UPDATE": { // CACHE THESE to find voice connections
							System.out.println(retdata.getJSONObject("d").toString(4)); // is a singular voice state
							JSONObject voiceState = retdata.getJSONObject("d");
							for (int i=0; i<BotReference.userIds.length; i++) {
								if (BotReference.userIds[i] == Long.valueOf(voiceState.getString("user_id"))) {
									BotReference.userIds[i] = -1;
									BotReference.userVoiceChannels[i] = -1;
								}
							}
							if (!voiceState.isNull("channel_id")) {
								BotReference.userIds = ArrayHelper.concatenate(BotReference.userIds, new long[] {Long.valueOf(voiceState.getString("user_id"))});
								BotReference.userVoiceChannels = ArrayHelper.concatenate(BotReference.userVoiceChannels, new long[] {Long.valueOf(voiceState.getString("channel_id"))});
								System.out.println("User joined a channel");
								if (BotReference.BOT_SNOWFLAKE == Long.valueOf(voiceState.getString("user_id"))) {
									for (int i=0; i<BotReference.serverHandlers.length; i++) {
										if (BotReference.serverHandlers[i].serverSnowflake == Long.valueOf(voiceState.getString("guild_id"))) {
											BotReference.serverHandlers[i].voiceSessionId = voiceState.getString("session_id");
											//System.err.println("NEW VOICE SESSION ID : " + BotReference.serverHandlers[i].voiceSessionId);
											if (BotReference.serverHandlers[i].voiceToken != "") {
												BotReference.serverHandlers[i].continueVoiceConnectionInit();
											}
										}
									}
								}
							} else {
								System.out.println("User left a channel");
							}
							System.err.println(Arrays.toString(BotReference.userIds));
							System.err.println(Arrays.toString(BotReference.userVoiceChannels));
						break;}
						case "GUILD_CREATE": { // CACHE THESE to find voice connections
							JSONArray voiceStateArray = retdata.getJSONObject("d").getJSONArray("voice_states"); // array of voice states
							for (int i=0; i<voiceStateArray.length(); i++) {
								JSONObject voiceState = voiceStateArray.getJSONObject(i);
								for (int j=0; j<BotReference.userIds.length; j++) {
									if (BotReference.userIds[j] == Long.valueOf(voiceState.getString("user_id"))) {
										BotReference.userIds[j] = -1;
										BotReference.userVoiceChannels[j] = -1;
									}
								}
								if (!voiceState.isNull("channel_id")) {
									BotReference.userIds = ArrayHelper.concatenate(BotReference.userIds, new long[] {Long.valueOf(voiceState.getString("user_id"))});
									BotReference.userVoiceChannels = ArrayHelper.concatenate(BotReference.userVoiceChannels, new long[] {Long.valueOf(voiceState.getString("channel_id"))});
								}
							}
							System.out.println(Arrays.toString(BotReference.userIds));
							System.out.println(Arrays.toString(BotReference.userVoiceChannels));
						break;}
						case "MESSAGE_UPDATE": {
						break;}
						default: {
							System.out.println("Unknown Event: " + eventName);
						}
					}
					//Bot.sessionId = data.getString("session_id");
					//Bot.sessionToken = data.getString("token");
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


public class Bot {
	public static String OWN_EXTERNAL_IP_ADDRESS = "81.154.190.100";
	public static int OWN_EXTERNAL_PORT = 4293;
	public static String botRootDirectory = "";
	public static TrustManager[] defaultTrustManagers;
	public static String BASE_URL = "https://discordapp.com/api";
	public static String USER_AGENT = "DiscordBot (baldeonline.com, 1.0.0) haebom-bot";
	public static long BOT_SNOWFLAKE = Long.valueOf("595623595791876146");
	public static String AUTHORIZATION = "NTk1NjIzNTk1NzkxODc2MTQ2.XRt2vw.Ca5vcGqUN5rbC5WV38th9pLpFgQ";
	public static String GATEWAY_URL = "";
	public static String sessionId = null;
	public static int sessionSequence = -1;
	public static ServerHandler[] serverHandlers = new ServerHandler[0];
	
	public static long[] userIds = new long[0];
	public static long[] userVoiceChannels = new long[0];
	
	public static WebSocketClientHandler webSocketClient;
	public static WebSocketEventScheduler webSocketEventScheduler;
	
	public Bot() {
		CertificateHandler certHandler = new CertificateHandler();
		defaultTrustManagers = certHandler.getTrustManagers();
		System.out.println("Hewwo");
		//HTTPSConnect.sendPOST(POST_URL, POST_PARAMS, trustManagers)
		JSONArray returnedGuilds = HTTPSConnect.getJSONArray(BASE_URL + "/users/@me/guilds", defaultTrustManagers);
		JSONObject returnedGateway = HTTPSConnect.getJSONObject(BASE_URL + "/gateway", defaultTrustManagers);
		GATEWAY_URL = returnedGateway.getString("url");
		///gateway
		
		initWebSocket();
		//System.out.println(returned.toString(4));
		for (int i=0; i<returnedGuilds.length(); i++) {
			//System.out.println(returned.getJSONObject(i).toString(4));
			ServerHandler svh = new ServerHandler(Long.valueOf(returnedGuilds.getJSONObject(i).getString("id")));
			svh.start();
			serverHandlers = ArrayHelper.push(serverHandlers, svh);
		}
	}
	
	public void initWebSocket() {
		try {
			String webSocketsUri = Bot.GATEWAY_URL + "/?v=6&encoding=json";
			System.out.println(webSocketsUri);
	
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2"); 
			sslContext.init(null, Bot.defaultTrustManagers, new SecureRandom());
			SSLSocketFactory factory = sslContext.getSocketFactory();
			
			webSocketEventScheduler = new WebSocketEventScheduler("wses");
			webSocketEventScheduler.botRoot = this;
			webSocketEventScheduler.start();
			
			webSocketClient = new WebSocketClientHandler(new URI(webSocketsUri));
			webSocketClient.setSocketFactory(factory);
			webSocketClient.connectBlocking();
			webSocketClient.BotReference = this;
			//BufferedReader reader = new BufferedReader( new InputStreamReader( System.in ) );
			//while ( true ) {
	//			String line = reader.readLine();
	//			if( line.equals( "close" ) ) {
	//				chatclient.closeBlocking();
	//			} else if ( line.equals( "open" ) ) {
	//				chatclient.reconnect();
	//			} else {
	//				chatclient.send( line );
	//			}
			//}
		} catch (Exception e) {
			System.err.println(e.getStackTrace());
		}
	}
	
	public static long getVoiceChannelFromUserId(long userId) {
		for (int i=0; i<userIds.length; i++) {
			if (userIds[i] == userId) {
				return userVoiceChannels[i];
			}
		}
		return -1;
	}
	
	public void eventHandler(String eventName) {
		switch (eventName) {
			case "heartbeat":{
				System.out.println("Heartbeat");
				JSONObject outData = new JSONObject();
				outData.put("op", 1);
				if (sessionSequence == -1) {
					outData.put("d", JSONObject.NULL);
				} else {
					outData.put("d", sessionSequence);
				}
				webSocketClient.send(outData.toString());
			break;}
			default: {
				if (eventName.startsWith("voiceheartbeat")) {
					long guildId = Long.valueOf(eventName.substring("voiceheartbeat".length()));
					for (int i=0; i<serverHandlers.length; i++) {
						if (serverHandlers[i].serverSnowflake == guildId) {
							serverHandlers[i].sendVoiceHeartbeat();
						}
					}
				}
			}
		}
	}
	
	public static void main(String[] args) {
		new Bot();
	}
}
