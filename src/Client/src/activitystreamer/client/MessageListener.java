package activitystreamer.client;

// code is from tutorial 7
import java.io.BufferedReader;
import java.net.SocketAddress;
import java.net.SocketException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import activitystreamer.util.Settings;

public class MessageListener extends Thread {
	private static final Logger log = LogManager.getLogger();
	private boolean term = false;

	private BufferedReader reader;
	ClientSkeleton client;

	public MessageListener(BufferedReader reader, ClientSkeleton client) {
		this.reader = reader;
		this.client = client;
	}

	@Override
	public void run() {

		try {
			String msg = null;
			// Read messages from the server while the end of the stream is not reached
			while (!term && (msg = reader.readLine()) != null) {
				// Print the messages to the console
				JSONParser parser = new JSONParser();
				JSONObject newMessage = (JSONObject) parser.parse(msg);
				ClientSkeleton.getInstance().getTextFrame().setOutputText(newMessage);
				System.out.println(newMessage);
				term = ClientSkeleton.getInstance().process(this, newMessage);

			}
			SocketAddress remoteSocketAddress = ClientSkeleton.getInstance().getSocket().getRemoteSocketAddress();
			ClientSkeleton.getInstance().getSocket().close();
			log.info("connection closed to " + remoteSocketAddress);
		} catch (SocketException e) {
			log.info("connected server crashed, you can try to connect to backup server.");
			JSONObject errorMsg = new JSONObject();
			errorMsg.put("info1", "the connection to the server failed, you can try to reconnect or go to backup server.");
			errorMsg.put("info2", "info1 is not important, I just want to know that I have handled the failure model.");
			ClientSkeleton.getInstance().getTextFrame().setOutputText(errorMsg);
			// try to connect the centralized server

			// Settings.setRemotePort(Settings.getRemoteBackupPort());
			// Settings.setRemoteHostname(Settings.getRemoteBackupHostname());
			// client.executeRedirect();
			// JSONObject relogin = new JSONObject();
			// relogin.put("command", "LOGIN");
			// relogin.put("username", client.getTempUsername());
			// relogin.put("secret", client.getTempSecret());
			// client.sendActivityObject(relogin);

		} catch (Exception e) {
			// e.printStackTrace();
		}

	}
}
