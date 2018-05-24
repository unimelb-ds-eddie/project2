package activitystreamer.server;

import java.util.Scanner;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Console extends Thread {
	Scanner sc;
	private JSONParser parser;
	Control cs;
	public Console(Control cs) {
		this.cs = cs;
		parser = new JSONParser();
		start();
	}

	public void run() {
		
		while (true) {
			System.out.println("Please input the command:");
			sc = new Scanner(System.in);
			
			String msg = sc.nextLine();
			if(msg.equals("cr")) {
				System.out.println("close the connection of regular server");
				cs.connections.get(0).closeCon();
			}else if(msg.equals("cb")) {
				System.out.println("close the connection of backup server");
				cs.backupServerConnections.get(0).closeCon();
			}
		}
	}

	public void sendMessage() {
	}

}
