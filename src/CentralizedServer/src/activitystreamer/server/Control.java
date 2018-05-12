package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.BufferedReader;

import activitystreamer.util.Settings;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static boolean term = false;
	private static Listener listener;
	// added attributes
	private static Hashtable<String, Integer> serverLoad;
	private static Hashtable<String, JSONObject> serverRedirect;
	private static Hashtable<String, Connection> pendingRegistration;
	private static Hashtable<String, JSONObject> lockRequestResponses;
	private static JSONObject lockRequestResponseCount;

	protected static Control control = null;

	public static Control getInstance() {
		if (control == null) {
			control = new Control();
		}
		return control;
	}

	public Control() {

		// Initialisation:

		// connections array
		connections = new ArrayList<Connection>();
		// server's client load for load balancing
		serverLoad = new Hashtable<String, Integer>();
		// server's hostname and port number for redirection
		serverRedirect = new Hashtable<String, JSONObject>();
		// hashtable to store pending user registrations while awaiting server replies
		pendingRegistration = new Hashtable<String, Connection>();
		// lock request responses - JSONObject to store number of responses and boolean
		// (to allow or deny)
		lockRequestResponses = new Hashtable<String, JSONObject>();
		lockRequestResponseCount = new JSONObject();
		lockRequestResponseCount.put("count", 0);
		lockRequestResponseCount.put("allow", true);
		// create new password local storage if not created
		createUserLocalStorage();

		// start a listener
		try {
			// one main thread listening for incoming connection requests
			listener = new Listener();

		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: " + e1);
			System.exit(-1);
		}
		start();
	}

	public void initiateConnection() {

		// make a connection to another server if remote hostname is supplied
		if (Settings.getRemoteHostname() != null) {
			try {

				// initiate connection with remote host server (outgoing)
				// synchronise thread to organise server authentication - due to the lack of
				// server id in JSON object
				// upon successful connection (i.e. socket accepted), begin server
				// authentication by calling serverAuthentication() method
				sendServerAuthentication(
						outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort())));

			} catch (IOException e) {
				log.error("failed to make connection to " + Settings.getRemoteHostname() + ":"
						+ Settings.getRemotePort() + " :" + e);
				System.exit(-1);
			}
		}
	}

	/*
	 * Processing incoming messages from the connection. Return true if the
	 * connection should close.
	 */

	@SuppressWarnings("unchecked")
	public synchronized boolean process(Connection con, String msg) {
		try {
			// parser to convert a string into a JSONObject
			JSONParser parser = new JSONParser();
			JSONObject message = (JSONObject) parser.parse(msg);

			if (message.containsKey("command")) {
				// retrieve command to process
				String command = (String) message.get("command");
				switch (command) {
				// AUTHENTICATE starts
				case "AUTHENTICATE":
					if (message.containsKey("secret")) {
						// check secret between 2 connecting servers
						String authenticateSecret = (String) message.get("secret");
						// if the server has already been authenticated, send invalid message and close
						// connection
						if (con.isServerAuthenticated() == true) {
							sendInvalidMessage(con, "server has already been authenticated");
							return true;
						}
						// if secret is incorrect, send AUTHENTICATION_FAIL and close connection
						else if (!Settings.getSecret().equals(authenticateSecret)) {
							log.info("authenticate failed with " + con.getSocket().getRemoteSocketAddress());
							sendAuthenticationFail(con, "the supplied secret is incorrect: " + authenticateSecret);
							return true;
						}
						// if the secret is correct and the server has not been authenticated
						// previously,
						// indicate that the server is now authenticated and keep the connection open
						else {
							log.info("authenticate successfully with " + con.getSocket().getRemoteSocketAddress());
							con.setServerAuthenticated();
						}
					} else {
						// send invalid message if secret is not found and close connection
						sendInvalidMessage(con, "the received message did not contain a secret");
						return true;
					}
					break;

				// AUTHENTICATE ends

				// INVALID_MESSAGE starts

				case "INVALID_MESSAGE":

					if (message.containsKey("info")) {
						// retrieve invalid message info, print it, and close connection
						String invalidMessageInfo = (String) message.get("info");
						log.info(invalidMessageInfo);
						return true;
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain a info");
						return true;
					}

					// INVALID_MESSAGE ends

					// AUTHENTICATION_FAIL starts

				case "AUTHENTICATION_FAIL":

					if (message.containsKey("info")) {
						// retrieve authentication fail info, print it, and close connection
						String authenticationFailInfo = (String) message.get("info");
						log.info(authenticationFailInfo);
						return true;
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain a info");
						return true;
					}

					// AUTHENTICATION_FAIL ends

					// LOGIN starts

				case "LOGIN":
					System.out.println("someone wants to login");
					break;
				// LOGIN ends

				// LOGOUT starts

				case "LOGOUT":
					// print client logged out message and close connection
					System.out.println(con.getClientUserName() + " logouts");
					log.info("client " + con.getSocket().getRemoteSocketAddress() + " has logged out");
					return true;

				// LOGOUT ends

				// ACTIVITY_MESSAGE starts

				case "ACTIVITY_MESSAGE":
					// check if client has logged in
					System.out.println("ACTIVITY_MESSAGE recieved.");
					break;
				// ACTIVITY_MESSAGE ends

				// SERVER_ANNOUNCE starts

				case "SERVER_ANNOUNCE":
					// Do we need this?
					System.out.println("SERVER_ANNOUNCE recieved.");
					break;
				// SERVER_ANNOUNCE ends

				// ACTIVITY_BROADCAST starts

				case "ACTIVITY_BROADCAST":
					System.out.println("ACTIVITY_BROADCAST recieved.");
					break;

				// ACTIVITY_BROADCAST ends

				// REGISTER starts

				case "REGISTER":
					System.out.println("Someone wants to register");
					break;
				// REGISTER ends

				// LOCK_REQUEST starts

				case "LOCK_REQUEST":
					System.out.println("LOCK_REQUEST recieved");
					break;
				// LOCK_REQUEST ends

				// LOCK_DENIED starts

				case "LOCK_DENIED":
					// It seems we dont need this
					System.out.println("LOCK_DENIED");
					break;

				// LOCK_DENIED ends

				// LOCK_ALLOWED starts

				case "LOCK_ALLOWED":
					// It seems we dont need this
					System.out.println("LOCK_ALLOWED");
					break;
				// LOCK_ALLOWED ends

				default:
					// if command is not valid send invalid message and close connection
					sendInvalidMessage(con, "the received message did not contain a valid command");
					return true;
				}

			} else {
				// send invalid message if command is not found and close connection
				sendInvalidMessage(con, "the received message did not contain a command");
				return true;
			}

		} catch (ParseException e) {
			log.error("invalid json format, unable to parse in json object" + e);
		} catch (Exception e) {
			log.error("an error has occurred when processing the message" + e);
		}
		return false;
	}

	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con) {
		if (!term)
			connections.remove(con);
	}

	/*
	 * A new incoming connection has been established, and a reference is returned
	 * to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException {
		log.debug("incoming connection: " + Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
	}

	/*
	 * A new outgoing connection has been established, and a reference is returned
	 * to it
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException {
		log.debug("outgoing connection: " + Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		// always trust that parent server is authenticated until authentication fails
		// to include in report, security issues - because the parent server does not
		// return authentication success until you send the next message, server unable
		// to know
		// ALTERNATIVE: another way is to check next incoming message is not invalid
		// message
		c.setServerAuthenticated();
		return c;
	}

	@Override
	public void run() {
		// establish thread for remote host server connection
		initiateConnection();
		log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");

		while (!term) {
			// do something with 5 second intervals in between
			// perform server announce every 5 seconds
			System.out.println("There are " + connections.size() + " servers connected.");
			for (Iterator<Connection> iterator = connections.iterator(); iterator.hasNext();) {
				Connection con = iterator.next();
				if (!con.isServerAuthenticated()) {
					con.writeMsg(
							"{\"command\" : \"ACTIVITY_BROADCAST\",\"expel\" : \"Sorry, clients cannot directly connect centralized server\"}");
				}
			}
			try {
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("received an interrupt, system is shutting down");
				break;
			}
			if (!term) {
				// log.debug("doing activity");
				term = doActivity();
			}

		}
		log.info("closing " + connections.size() + " connections");
		// clean up
		for (Connection connection : connections) {
			connection.closeCon();
		}
		listener.setTerm(true);
	}

	public boolean doActivity() {
		return false;
	}

	public final void setTerm(boolean t) {
		term = t;
	}

	public final ArrayList<Connection> getConnections() {
		return connections;
	}

	// added methods for project tasks

	// Invalid message

	@SuppressWarnings("unchecked")
	private void sendInvalidMessage(Connection c, String info) {
		log.info("ACTIVITY: port " + Settings.getLocalPort() + " sending INVALID_MESSAGE to "
				+ c.getSocket().getLocalSocketAddress());
		// Marshaling
		JSONObject invalidMessage = new JSONObject();
		invalidMessage.put("command", "INVALID_MESSAGE");
		invalidMessage.put("info", info);
		// send message
		if (c.writeMsg(invalidMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: INVALID_MESSAGE sent to "
					+ c.getSocket().getLocalSocketAddress());
		} else {
			log.info("[Port-" + Settings.getLocalPort() + "]: INVALID_MESSAGE sending to "
					+ c.getSocket().getLocalSocketAddress() + " failed");
		}
	}

	// Server authenticate

	@SuppressWarnings("unchecked")
	private boolean sendServerAuthentication(Connection c) {
		JSONObject authenticate = new JSONObject();
		authenticate.put("command", "AUTHENTICATE");
		authenticate.put("secret", Settings.getSecret());
		// write message
		if (c.writeMsg(authenticate.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATE sent to Port-" + Settings.getRemotePort());
			return true;
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATE sending to Port-" + Settings.getRemotePort()
					+ " failed");
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private boolean sendAuthenticationFail(Connection c, String info) {
		JSONObject failureMessage = new JSONObject();
		failureMessage.put("command", "AUTHENTICATION_FAIL");
		failureMessage.put("info", info);
		// write message
		if (c.writeMsg(failureMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATE_FAIL sent to "
					+ c.getSocket().getRemoteSocketAddress());
			return true;
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATE_FAIL sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
			return false;
		}
	}

	// Server announce

	@SuppressWarnings("unchecked")
	private void sendServerAnnounce(ArrayList<Connection> allConnections) {
		JSONObject serverAnnounceMessage = new JSONObject();
		serverAnnounceMessage.put("command", "SERVER_ANNOUNCE");
		serverAnnounceMessage.put("id", Settings.getServerId());
		serverAnnounceMessage.put("load", getClientLoad());
		serverAnnounceMessage.put("hostname", Settings.getLocalHostname());
		serverAnnounceMessage.put("port", Settings.getLocalPort());
		// write message
		for (Connection c : allConnections) {
			// send serve announce to authenticated server
			if (c.isServerAuthenticated()) {
				if (c.writeMsg(serverAnnounceMessage.toJSONString())) {
					// log.debug("[Port-" + Settings.getLocalPort() + "]: SERVER_ANNOUNCE sent to "
					// + c.getSocket().getRemoteSocketAddress());
				} else {
					// log.debug("[Port-" + Settings.getLocalPort() + "]: SERVER_ANNOUNCE sending to
					// " + c.getSocket().getRemoteSocketAddress() + " failed");
				}
			}
		}
	}

	private void forwardServerMessage(Connection origin, JSONObject serverMessage) {
		for (Connection c : connections) {
			if (!c.equals(origin) && c.isServerAuthenticated()) {
				c.writeMsg(serverMessage.toJSONString());
			}
		}
	}

	private void sendClientMessage(JSONObject clientMessage) {
		for (Connection c : connections) {
			if (!c.isServerAuthenticated()) {
				c.writeMsg(clientMessage.toJSONString());
			}
		}
	}

	private int getClientLoad() {
		int load = 0;

		for (Connection c : connections) {
			if (c.isClient()) {
				load++;
			}
		}
		return load;
	}

	// Redirect

	private boolean executeLoadBalance(Connection c) {
		for (String serverId : serverLoad.keySet()) {
			// redirect if server finds any server with at least 2 clients lesser than its
			// own
			if (getClientLoad() - serverLoad.get(serverId) >= 2) {
				// send destination server address
				redirectClient(c, serverRedirect.get(serverId));
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private void redirectClient(Connection c, JSONObject address) {
		// Marshaling
		JSONObject redirectMessage = new JSONObject();
		redirectMessage.put("command", "REDIRECT");
		redirectMessage.put("hostname", address.get("hostname"));
		redirectMessage.put("port", address.get("port"));
		// write message to remote server as JSON object for authentication
		if (c.writeMsg(redirectMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: REDIRECT sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: REDIRECT sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}

	// Login

	private boolean authenticateClient(String username, String secret) {
		boolean userAuthenticated = false;

		ArrayList<JSONObject> userLocalStorage = retrieveUserLocalStorage();

		if (userLocalStorage.size() != 0) {
			for (JSONObject userInfo : userLocalStorage) {
				String storedUsername = (String) userInfo.get("username");
				String storedSecret = (String) userInfo.get("secret");
				if (storedUsername.equals(username) && storedSecret.equals(secret)) {
					userAuthenticated = true;
					log.debug("username: " + username + " and secret: " + secret + " authenticated");
					break;
				}
			}
		}
		return userAuthenticated;
	}

	@SuppressWarnings("unchecked")
	private void sendLoginSuccess(Connection c, String username) {
		// increase number of clients logged in on server
		c.setLoggedInClient();
		JSONObject loginSuccessMessage = new JSONObject();
		loginSuccessMessage.put("command", "LOGIN_SUCCESS");
		loginSuccessMessage.put("info", "logged in as user " + username);
		// write message to remote server as JSON object for authentication
		if (c.writeMsg(loginSuccessMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: LOGIN_SUCCESS sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: LOGIN_SUCCESS sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}

	@SuppressWarnings("unchecked")
	private void sendLoginFailed(Connection c) {
		JSONObject loginFailedMessage = new JSONObject();
		loginFailedMessage.put("command", "LOGIN_FAILED");
		loginFailedMessage.put("info", "attempt to login with wrong secret");
		// write message to remote server as JSON object for authentication
		if (c.writeMsg(loginFailedMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: LOGIN_FAILED sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: LOGIN_FAILED sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}

	// Register

	private void createUserLocalStorage() {
		File passwordLocalStorage = new File("Port" + Settings.getLocalPort() + ".json");
		if (!(passwordLocalStorage.exists() && !passwordLocalStorage.isDirectory())) {
			try {
				passwordLocalStorage.createNewFile();
				log.debug("password local storage file created");
			} catch (Exception e) {
				log.debug("password local storage file could not be created " + e);
			}
		}
	}

	private ArrayList<JSONObject> retrieveUserLocalStorage() {
		ArrayList<JSONObject> allUserInfo = new ArrayList<JSONObject>();
		String filename = "Port" + Settings.getLocalPort() + ".json";
		try {
			// FileReader reads text file in the default encoding
			FileReader fileReader = new FileReader(filename);
			// wrap FileReader in BufferedReader
			BufferedReader bufferedReader = new BufferedReader(fileReader);

			String line = null;

			while ((line = bufferedReader.readLine()) != null) {
				JSONObject userInfo = (JSONObject) new JSONParser().parse(line);
				allUserInfo.add(userInfo);
			}
			bufferedReader.close();
		} catch (FileNotFoundException e) {
			log.error("file " + filename + " do not exist");
		} catch (IOException e) {
			log.error("error reading " + filename);
		} catch (ParseException e) {
			log.error(e);
		}
		return allUserInfo;
	}

	private boolean checkUsernameExist(String username) {
		boolean usernameExist = false;

		ArrayList<JSONObject> userLocalStorage = retrieveUserLocalStorage();

		String requestedUsername = username;

		if (userLocalStorage.size() != 0) {
			for (JSONObject userInfo : userLocalStorage) {
				String storedUsername = (String) userInfo.get("username");
				if (storedUsername.equals(requestedUsername)) {
					usernameExist = true;
					break;
				}
			}
		}
		return usernameExist;
	}

	private void removeMatchedUsernameAndSecret(String username, String secret) {
		ArrayList<JSONObject> userLocalStorage = retrieveUserLocalStorage();

		String requestedUsername = username;
		String requestedSecret = secret;

		if (userLocalStorage.size() != 0) {
			for (JSONObject userInfo : userLocalStorage) {
				String storedUsername = (String) userInfo.get("username");
				String storedSecret = (String) userInfo.get("secret");

				if (storedUsername.equals(requestedUsername) && storedSecret.equals(requestedSecret)) {
					userLocalStorage.remove(userInfo);
					log.debug("username: " + requestedUsername + " and secret: " + requestedSecret + " removed");
					break;
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void sendRegisterFailed(Connection c, String username) {
		JSONObject registerFailMessage = new JSONObject();
		registerFailMessage.put("command", "REGISTER_FAILED");
		registerFailMessage.put("info", username + " is already registered with the system");
		// reply to client
		if (c.writeMsg(registerFailMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: REGISTER_FAILED sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: REGISTER_FAILED sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}

	@SuppressWarnings("unchecked")
	private void sendRegisterSuccess(Connection c, String username) {
		JSONObject registerSuccessMessage = new JSONObject();
		registerSuccessMessage.put("command", "REGISTER_SUCCESS");
		registerSuccessMessage.put("info", "register success for " + username);
		// reply to client
		if (c.writeMsg(registerSuccessMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: REGISTER_SUCCESS sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: REGISTER_SUCCESS sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}

	@SuppressWarnings("unchecked")
	private void sendLockRequest(Connection c, String username, String secret) {
		JSONObject lockRequestMessage = new JSONObject();
		lockRequestMessage.put("command", "LOCK_REQUEST");
		lockRequestMessage.put("username", username);
		lockRequestMessage.put("secret", secret);
		// forward to other servers connected except the originated client
		forwardServerMessage(c, lockRequestMessage);
		log.debug("LOCK_REQUEST sent");
	}

	@SuppressWarnings("unchecked")
	private void sendLockAllowed(String username, String secret) {
		JSONObject lockAllowedMessage = new JSONObject();
		lockAllowedMessage.put("command", "LOCK_ALLOWED");
		lockAllowedMessage.put("username", username);
		lockAllowedMessage.put("secret", secret);

		for (Connection c : connections) {
			if (c.isServerAuthenticated()) {
				if (c.writeMsg(lockAllowedMessage.toJSONString())) {
					log.debug("[Port-" + Settings.getLocalPort() + "]: LOCK_ALLOWED sent to "
							+ c.getSocket().getRemoteSocketAddress());
				} else {
					log.debug("[Port-" + Settings.getLocalPort() + "]: LOCK_ALLOWED sending to "
							+ c.getSocket().getRemoteSocketAddress() + " failed");
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void sendLockDenied(String username, String secret) {
		JSONObject lockDeniedMessage = new JSONObject();
		lockDeniedMessage.put("command", "LOCK_DENIED");
		lockDeniedMessage.put("username", username);
		lockDeniedMessage.put("secret", secret);

		for (Connection c : connections) {
			if (c.isServerAuthenticated()) {
				if (c.writeMsg(lockDeniedMessage.toJSONString())) {
					log.debug("[Port-" + Settings.getLocalPort() + "]: LOCK_DENIED sent to "
							+ c.getSocket().getRemoteSocketAddress());
				} else {
					log.debug("[Port-" + Settings.getLocalPort() + "]: LOCK_DENIED sending to "
							+ c.getSocket().getRemoteSocketAddress() + " failed");
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void storeUsernameSecret(String username, String secret) {
		JSONObject newUser = new JSONObject();
		newUser.put("username", username);
		newUser.put("secret", secret);

		String filename = "Port" + Settings.getLocalPort() + ".json";

		try {
			FileWriter file = new FileWriter(filename, true);
			file.write(newUser.toJSONString());
			file.write(System.lineSeparator());
			file.flush();
			file.close();
			log.info("username: " + username + " and secret: " + secret + " stored");

		} catch (IOException e) {
			log.error("error storing username and secret " + e);
		}
	}

	// Activity

	@SuppressWarnings("unchecked")
	private void sendActivityBroadcast(JSONObject activity) {
		JSONObject activityBroadcastMessage = new JSONObject();
		activityBroadcastMessage.put("command", "ACTIVITY_BROADCAST");
		activityBroadcastMessage.put("activity", activity);
		// write message to all connections regardless of client or server
		for (Connection c : connections) {
			c.writeMsg(activityBroadcastMessage.toJSONString());
		}
	}
}
