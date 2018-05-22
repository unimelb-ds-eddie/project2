package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.gson.JsonObject;

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
	public static ArrayList<Connection> connections;
	private static boolean term = false;
	private static Listener listener;
	// added variables
	private static Connection centralisedServerConnection;

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

				// ONLY initiate connection to the main centralised host server
				// that would be the ONLY outgoing connection for the regular server
				sendServerAuthentication(
						outgoingConnection(new Socket(Settings.getCentralisedRemoteHostname(), Settings.getCentralisedRemotePort())));

			} catch (IOException ex) {
				
				try {
					sendServerAuthentication(
							outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort())));
				} catch (IOException e) {
					log.error("failed to make connection to " + Settings.getRemoteHostname() + ":"
							+ Settings.getRemotePort() + " :" + e);
					System.exit(-1);
				}
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

				// ***** AUTHENTICATION_SUCCESS (START) *****

				case "AUTHENTICATION_SUCCESS":

					if (message.containsKey("info")) {
						// if message was valid, indicate that server is authenticated
						// and indicated that server is a centraliser server
						con.setServerAuthenticated();
						con.setCentralisedServer();
						// print success message to console
						String authenticationSuccessInfo = (String) message.get("info");
						log.info(authenticationSuccessInfo);
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain a info");
						return true;
					}
					break;

				// ***** AUTHENTICATION_SUCCESS (END) *****
					
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

				// LOGOUT starts [NOTE] should consider passing the request to centralised server for better architecture

				case "LOGOUT":
					// print client logged out message and close connection
					log.info("client " + con.getSocket().getRemoteSocketAddress() + " has logged out");
					// inform centralized server to decrease 1 load
					JSONObject deload = new JSONObject();
					deload.put("command", "DE_LOAD");
					deload.put("id",Settings.getServerId());
					System.out.println(Settings.getServerId());
					forwardServerMessage(con, deload);
					return true;

				// LOGOUT ends

				// ACTIVITY_MESSAGE starts

				case "ACTIVITY_MESSAGE":
					if(con.isClient()) {
						// check if activity message contains username
						boolean hasUsername = message.containsKey("username");
						boolean hasSecret = message.containsKey("secret");
						boolean hasActivity = message.containsKey("activity");

						if(hasUsername) {
							String activityMessageUsername = (String) message.get("username");

							if(activityMessageUsername.equals("anonymous")) {
								if(hasActivity) {
									// process broadcast
									JSONObject activityMessageActivity = (JSONObject) message.get("activity");
									activityMessageActivity.put("authenticated_user", activityMessageUsername);
									sendActivityBroadcast(activityMessageActivity);
								} else {
									// respond with invalid message if message did not contain a activity and close the connection
									sendInvalidMessage(con, "the received message did not contain an activity object");
									return true;
								}
							} else if(!activityMessageUsername.equals("anonymous") && hasSecret) {
								String activityMessageSecret = (String) message.get("secret");
								if(activityMessageUsername.equals(con.getClientUserName()) && activityMessageSecret.equals(con.getClientSecret())) {
									if(hasActivity) {
										// process broadcast
										JSONObject activityMessageActivity = (JSONObject) message.get("activity");
										activityMessageActivity.put("authenticated_user", activityMessageUsername);
										sendActivityBroadcast(activityMessageActivity);
									} else {
										// respond with invalid message if message did not contain a activity and close the connection
										sendInvalidMessage(con, "the received message did not contain an activity object");
										return true;
									}
								} else {
									// send authentication fail if username or secret did not match
									sendAuthenticationFail(con, "username and secret do not match the logged in the user");
									return true;
								}
							} else {
								// respond with invalid message if message did not contain a secret and close the connection
								sendInvalidMessage(con, "the received message has a non-anonymous username but did not contain a secret");
								return true;
							}
						} else {
							// respond with invalid message if message did not contain a username and close the connection
							sendInvalidMessage(con, "the received message did not contain a username");
							return true;
						}
					} else {
						sendAuthenticationFail(con, "client not login");
					}
		
					break;

				// ACTIVITY_MESSAGE ends

				// ACTIVITY_BROADCAST starts

				case "ACTIVITY_BROADCAST":
					System.out.println("i received from central server:"+message );
					if(con.isServerAuthenticated()) {
						forwardActivityBroadcastToClients(message);				
					}else {
						sendInvalidMessage(con, "Sorry, only server can send ACTIVITY_BROADCAST");
						return true;
					}
					break;

				// ACTIVITY_BROADCAST ends
					
				//testing
				case "LOGIN":
					System.out.println(msg);				
					con.setClientUserName(message.get("username").toString());
					con.setClientSecret(message.get("secret").toString());
					con.setLoggedInClient();
					System.out.println("This server is serving for:");
					System.out.println(con.getClientUserName());
					System.out.println(con.getClientSecret());
					return false;
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
	
	public synchronized void centralisedServerConnectionClosed(Connection con) {
		if (!term) {
			centralisedServerConnection.closeCon();
			try {
				// establish connection to default centralised server first, if not backup server
				sendServerAuthentication(
						outgoingConnection(new Socket(Settings.getBackupRemoteHostname(), Settings.getBackupRemotePort())));

			} catch (IOException e) {
				log.error("failed to make connection to " + Settings.getBackupRemoteHostname() + ":"
						+ Settings.getBackupRemotePort() + " :" + e);
				System.exit(-1);
			}
		}
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
		centralisedServerConnection = new Connection(s);
		connections.add(centralisedServerConnection);
		return centralisedServerConnection;
	}

	@Override
	public void run() {
		// establish thread for remote host server connection
		initiateConnection();
		log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");

		while (!term) {
			// do something with 5 second intervals in between
			// perform server announce every 5 seconds
			// sendServerAnnounce(connections);
			//sendServerAnnounce(connections);
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
		// [NOTE] new information added for centralised server memory
		authenticate.put("id", Settings.getServerId());
		authenticate.put("hostname", Settings.getLocalHostname());
		authenticate.put("port", Settings.getLocalPort());
		
		// when facing invalid msg, deload one
		JSONObject deload = new JSONObject();
		deload.put("command", "DE_LOAD");
		deload.put("id",Settings.getServerId());
		System.out.println(Settings.getServerId());
		forwardServerMessage(c, deload);
		
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

	public void forwardServerMessage(Connection origin, JSONObject serverMessage) {
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

	// Activity

	@SuppressWarnings("unchecked")
	private boolean sendAuthenticationFail(Connection c, String info) {
		JSONObject failureMessage = new JSONObject();
		failureMessage.put("command", "AUTHENTICATION_FAIL");
		failureMessage.put("info", info);
		
		// when facing fail, deload one
		JSONObject deload = new JSONObject();
		deload.put("command", "DE_LOAD");
		deload.put("id",Settings.getServerId());
		System.out.println(Settings.getServerId());
		forwardServerMessage(c, deload);
		
		// write message
		if (c.writeMsg(failureMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATE_FAIL sent to " + c.getSocket().getRemoteSocketAddress());
			return true;
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATE_FAIL sending to " + c.getSocket().getRemoteSocketAddress() + " failed");
			return false;
		}
	}
	
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
	
	@SuppressWarnings("unchecked")
	private void forwardActivityBroadcastToClients(JSONObject activity) {

		// write message to all connections regardless of client or server
		for (Connection c : connections) {
			if(c.isClient())
			c.writeMsg(activity.toJSONString());
		}
	}
}
