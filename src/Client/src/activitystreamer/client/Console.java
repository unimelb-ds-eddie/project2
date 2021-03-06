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
		start();
	}

	public void run() {
		
		while (true) {
			System.out.println("Please input the command:");
			sc = new Scanner(System.in);

			String msg = sc.nextLine();

			System.out.println("sending:" + msg);
			JSONObject obj;
			try {
				obj = (JSONObject) parser.parse(msg);
				ClientSkeleton.getInstance().sendActivityObject(obj);
	
			} catch (ParseException e1) {
				System.out.println("invalid JSON object entered into input text field, data not sent");
			}

		}
	}

	public void sendMessage() {
	}

}
