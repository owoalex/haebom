package com.baldeonline.haebom;

import java.util.concurrent.CountDownLatch;

import org.json.JSONArray;
import org.json.JSONObject;

public class WebSocketEventScheduler implements Runnable {
	private Thread t;
	public String threadName;
	private int[] eventsTime = new int[0];
	private int[] eventsTimeElapsed = new int[0];
	public Bot botRoot;
	public String[] eventsCallback = new String[0];
	
	public WebSocketEventScheduler(String tni) {
		threadName = tni;
	}
	
	@Override
	public void run() {
		while (true) {
			try {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				for (int i=0; i<eventsTimeElapsed.length; i++) {
					eventsTimeElapsed[i] = eventsTimeElapsed[i] + 50;
					if (eventsTime[i] < eventsTimeElapsed[i]) {
						eventsTimeElapsed[i] = 0;
						botRoot.eventHandler(eventsCallback[i]);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void scheduleEvent(String name, int timeMs) {
		eventsTime = ArrayHelper.concatenate(eventsTime, new int[] {timeMs});
		eventsTimeElapsed = ArrayHelper.concatenate(eventsTimeElapsed, new int[] {0});
		eventsCallback = ArrayHelper.concatenate(eventsCallback, new String[] {name});
		System.out.println("Event sheduled every " + String.valueOf(timeMs));
	}
	
	public void start() {
		if (t == null) {
			t = new Thread (this, threadName);
			t.start();
		}
	}
}
