package activitystreamer.client;

import java.util.Scanner;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Console extends Thread {
	Scanner sc;
	private JSONParser parser;

	public Console() {

		parser = new JSONParser();

	}

	public void run() {
		
	}

	public void sendMessage() {
	}

}
