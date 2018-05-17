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
		while (true) {
			System.out.println("CMD starts");
			sc = new Scanner(System.in);
			String msg = sc.nextLine().trim().replaceAll("\r", "").replaceAll("\n", "").replaceAll("\t", "");
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
