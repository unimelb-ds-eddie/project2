package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

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
	public static ArrayList<Connection> connections;
	private static boolean term = false;
	private static Listener listener;
	// modified / added attributes
	private static ArrayList<Connection> backupServerConnections;
	private static Hashtable<String, Integer> serverClientLoad;
	private static Hashtable<String, JSONObject> serverAddresses;
	// [DELETE] for testing
//	private static Hashtable<String, Integer> testserverClientLoad;
	private static Hashtable<String, JSONObject> testserverAddresses;
	
	// [NOTE] - things to take note
	// [ADD] - methods to be written
	// [DELETE] - items to be deleted later

	protected static Control control = null;

	public static Control getInstance() {
		if (control == null) {
			control = new Control();
		}
		return control;
	}

	public Control() {

		// ***** STARTUP INITIALISATION (START) *****

		// Connections:

		// connection array
		connections = new ArrayList<Connection>();
		// backup server connections for redundancies to the centralised server
		backupServerConnections = new ArrayList<Connection>();

		// Centralised Server Memory:

		// server's client load for load balancing
		serverClientLoad = new Hashtable<String, Integer>();
		// server's address (hostname and port number) for load balancing
		serverAddresses = new Hashtable<String, JSONObject>();
				
		// Local Storage:
				
		// user store - username and secret
		createUserLocalStorage();

		// ***** STARTUP INITIALISATION (END) *****

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

		// [for backup centralised server]: make a connection to main server -> if remote hostname was supplied
		if (Settings.getRemoteHostname() != null) {
			try {
				// initiate connection with centralised host server (outgoing)
				// authenticate with centralised host server with secret
				// the ONLY outgoing connection expected for a centralised server is to connect to the main centralised server
				sendAuthenticationBackup(
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
				
				// ***** BACKUP_AUTHENTICATE (START) *****
				
				case "BACKUP_AUTHENTICATE":
					if (message.containsKey("secret")) {
						// check secret between 2 connecting servers
						String authenticateSecret = (String) message.get("secret");
						// if the server has already been authenticated, send invalid message and close
						// connection
						if (con.isServerAuthenticated() == true) {
							sendInvalidMessage(con, "server has already been authenticated");
							return true;
						}
						// if secret is incorrect, send BACKUP_AUTHENTICATION_FAIL and close connection
						else if (!Settings.getSecret().equals(authenticateSecret)) {
							log.info("authenticate failed with " + con.getSocket().getRemoteSocketAddress());
							sendBackupAuthenticationFail(con, "the supplied secret is incorrect: " + authenticateSecret);
							return true;
						}
						// if the secret is correct and the server has not been authenticated previously,
						// indicate that the server is now authenticated and reply BACKUP_AUTHENTICATE_SUCCESS
						else {
							log.info("authenticate successfully with " + con.getSocket().getRemoteSocketAddress());
							// indicate that server has been authenticated
							// indicate that server is a backup server and update incoming connection array to backup connection array
							// send BACKUP_AUTHENTICATION_SUCCESS
							con.setServerAuthenticated();
							con.setBackupCentralisedServer();
							updateBackupConnection(con);
							sendBackupAuthenticationSuccess(con, "authenticated successfully with main centralised server");
							// initiate server synchronisation
							synchroniseCentralisedServers(con);
						}
					} else {
						// send invalid message if secret is not found and close connection
						sendInvalidMessage(con, "the received message did not contain a secret");
						return true;
					}
					break;
					
				// ***** BACKUP_AUTHENTICATE (END) *****
					
				// ***** BACKUP_AUTHENTICATION_FAIL (START) *****

				case "BACKUP_AUTHENTICATION_FAIL":

					if (message.containsKey("info")) {
						// retrieve authentication fail info, print it, and close connection
						String backupAuthenticationFailInfo = (String) message.get("info");
						log.info(backupAuthenticationFailInfo);
						return true;
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain a info");
						return true;
					}

				// ***** BACKUP_AUTHENTICATION_FAIL (END) ******
					
				// ***** BACKUP_AUTHENTICATION_SUCCESS (START) *****
					
				case "BACKUP_AUTHENTICATION_SUCCESS":
					// send invalid message if message was corrupted
					if (message.containsKey("info")) {
						// retrieve authentication success info, print it, and set connection as authenticated
						con.setServerAuthenticated();
						String backupAuthenticationSuccessInfo = (String) message.get("info");
						log.info(backupAuthenticationSuccessInfo);
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain a info");
						return true;
					}
					break;

				// ***** BACKUP_AUTHENTICATION_SUCCESS (END) *****
				
				// ***** AUTHENTICATE (START) *****

				case "AUTHENTICATE":
					
					// if the server has already been authenticated, send invalid message and close
					// connection
					if (con.isServerAuthenticated() == true) {
						sendInvalidMessage(con, "server has already been authenticated");
						return true;
					} else {
						boolean hasSecret = message.containsKey("secret");
						boolean hasID = message.containsKey("id");
						boolean hasHostname = message.containsKey("hostname");
						boolean hasPort = message.containsKey("port");
						
						if (hasSecret && hasID && hasHostname && hasPort) {
							// check secret between 2 connecting servers
							String authenticateSecret = (String) message.get("secret");
							
							// if secret is incorrect, send AUTHENTICATION_FAIL and close connection
							if (!Settings.getSecret().equals(authenticateSecret)) {
								log.info("authenticate failed with " + con.getSocket().getRemoteSocketAddress());
								sendAuthenticationFail(con, "the supplied secret is incorrect: " + authenticateSecret);
								return true;
							}
							// if the secret is correct and the server has not been authenticated
							// previously,
							// indicate that the server is now authenticated and reply AUTHENTICATE_SUCCESS
							else {
								log.info("authenticate successfully with " + con.getSocket().getRemoteSocketAddress());
								// indicate connection as an authenticated server				
								con.setServerAuthenticated();
								
								// retrieve server id, hostname, and port for load balance
								String id = (String) message.get("id");
								String hostname = (String) message.get("hostname");
								int port = (int)(long) message.get("port");
								
								// initialise the server into memory
								// initialise client load to 0
								serverClientLoad.put(id, 0);
								// update server address information
								JSONObject address = new JSONObject();
								address.put("hostname", hostname);
								address.put("port", port);
								serverAddresses.put(id, address);
								
								// [ADD] synchronise the update with backup server
								synchroniseNewServer(backupServerConnections, id, address);
								
								// send AUTHENTICATE_SUCCESS 
								sendAuthenticateSuccess(con, "authenticated successfully with main centralised server");
							}
						} else {
							// respond with invalid message if any of the attributes were missing and close the connection
							if(!hasID) {
								sendInvalidMessage(con, "the received message did not contain a server id");
							} else if(!hasSecret) {
								sendInvalidMessage(con, "the received message did not contain a secret");
							} else if(!hasHostname) {
								sendInvalidMessage(con, "the received message did not contain server's hostname");
							} else if(!hasPort) {
								sendInvalidMessage(con, "the received message did not contain server's port number");
							}
							return true;
						}
					}
					break;

				// ***** AUTHENTICATE (END) *****

				// ***** AUTHENTICATION_FAIL (START) *****

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

				// ***** AUTHENTICATION_FAIL (END) ******
					
				// ***** SYNCHRONISE_SERVER_LOAD (START) *****
					
				case "SYNCHRONISE_SERVER_LOAD":
					// new protocol to synchronise server memory on existing server load
					// "command": "SYNCHRONISE_SERVER_LOAD"
					// "activity": {"serverID1": "clientLoad1", "serverID2": "clientLoad2"} // Array of JSON Objects

					// send invalid message if message was corrupted
					// if message was valid, allow synchronisation of the 2 centralised servers
					if (message.containsKey("activity")) {
						// load it into serverClientLoad memory
						updateServerClientLoad((JSONObject) message.get("activity"));
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain activity object");
						return true;
					}
					break;
					
				// ***** SYNCHRONISE_SERVER_LOAD (END) *****
					
				// ***** SYNCHRONISE_SERVER_ADDRESSES (START) *****
					
				case "SYNCHRONISE_SERVER_ADDRESSES":
					// new protocol to synchronise server memory on connected servers' addresses
					// "command": "SYNCHRONISE_SERVER_ADDRESSES"
					// "activity": {"serverID1": {"hostname": "hostname1", "port": "port1"}, "serverID2": {"hostname": "hostname2", "port": "port2"}} // Array of JSON Objects

					// send invalid message if message was corrupted
					// if message was valid, allow synchronisation of the 2 centralised servers
					if (message.containsKey("activity")) {
						updateServerAddresses((JSONObject) message.get("activity"));
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain activity object");
						return true;
					}
					break;
					
				// ***** SYNCHRONISE_SERVER_ADDRESSES (END) *****
				
				// ***** SYNCHRONISE_USER_STORE (START) *****
					
				case "SYNCHRONISE_USER_STORE":
					// new protocol to synchronise server memory on connected servers' addresses
					// "command": "SYNCHRONISE_USER_STORE"
					// "activity": {"serverID1": {"hostname": "hostname1", "port": "port1"}} // Array of JSON Objects

					// send invalid message if message was corrupted
					// if message was valid, allow synchronisation of the 2 centralised servers
					if (message.containsKey("activity")) {
						// do something
						ArrayList<JSONObject> tempUserStore = (ArrayList<JSONObject>) message.get("activity");
						updateUserStore(tempUserStore);
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain activity object");
						return true;
					}
					break;
					
				// ***** SYNCHRONISE_USER_STORE (END) *****
					
				// ***** SYNCHRONISE_NEW_SERVER (START) *****
					
				case "SYNCHRONISE_NEW_SERVER":
					// new protocol to synchronise server memory on connected servers' addresses
					// "command": "SYNCHRONISE_NEW_SERVER"
					// "id": "<serverID>"
					// "address": {"hostname": "hostname1", "port": "port1"} // Array of JSON Objects

					// send invalid message if message was corrupted
					
					if (message.containsKey("address")) {
						// if message was valid, allow synchronisation of the 2 centralised servers
						// update serverLoad and serverAddress
						// retrieve server id, hostname, and port for load balance
						String id = (String) message.get("id");
						JSONObject address = (JSONObject) message.get("address");
						
						// initialise the server into memory
						// initialise client load to 0
						serverClientLoad.put(id, 0);
						// update server address information
						serverAddresses.put(id, address);
					} else {
						// send invalid message if info is not found and close connection
						sendInvalidMessage(con, "the received message did not contain activity object");
						return true;
					}
					break;
					
				// ***** SYNCHRONISE_NEW_SERVER (END) *****

				// ***** LOGIN (START) *****

				case "LOGIN":
					System.out.println("someone wants to login");

					// extract client's details from JSON
					String username_client = (String) message.get("username");
					String secret_client = (String) message.get("secret");

					// authenticate the client's details
					if (authenticateClient(username_client, secret_client)) {
						// send login success
						System.out.println("Login success!");
						sendLoginSuccess(con, username_client);

						// redirect if the client is connected to central server to login
						// by calling executeLoadBalance
						// do something below to reflect this
						executeLoadBalance(con);

					} else {
						System.out.println("Login failed!");
						// send login failed
						sendLoginFailed(con);
						// close connection
						System.out.println("Closing connection...");
						return true;
					}
					break;

				// ***** LOGIN (END) *****
					
				// DELOAD
				case "DE_LOAD":
					// decrease one load when a client logouts
					String decease_id = (String) message.get("id");
					System.out.println(decease_id + " logouts");
					serverClientLoad.put(decease_id, serverClientLoad.get(decease_id)-1);
				break;
									
				// ***** LOGOUT (START) *****

				case "LOGOUT":
					// print client logged out message and close connection
					System.out.println(con.getClientUserName() + " logouts");
					log.info("client " + con.getSocket().getRemoteSocketAddress() + " has logged out");
					return true;

				// ***** LOGOUT (END) *****

				// ***** ACTIVITY_BROADCAST (START) ******

				case "ACTIVITY_BROADCAST":
					System.out.println("ACTIVITY_BROADCAST recieved.");
					break;

				// ***** ACTIVITY_BROADCAST (END) ******

				// ***** REGISTER (START) *****

				case "REGISTER":
					System.out.println("Someone wants to register");

					// retrieve client's details from JSON object 'message'
					String username = (String) message.get("username");
					String secret = (String) message.get("secret");

					// check if username exists. If yes, invoke register fail
					if (!checkUsernameExist(username)) {
						// this is the case where it doesn't exist
						// write to DB and send register success
						System.out.println("Username doesn't exist, registering now...");
						storeUsernameSecret(username, secret);
						sendRegisterSuccess(con, username);

					} else {
						// this is the case where username already exists
						// send register fail
						System.out.println("Username exists, failing to register!");
						sendRegisterFailed(con, username);

						// close connection
						return true;
					}

					break;

				// ***** REGISTER (END) *****

				// ***** INVALID_MESSAGE (START) *****

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

					// ***** INVALID_MESSAGE (END) *****

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
	
	public synchronized void backupServerConnectionClosed(Connection con) {
		if (!term)
			backupServerConnections.remove(con);
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
	
	private void updateBackupConnection(Connection c) {
		log.debug("updating connection as backup centralised server connection");
		backupServerConnections.add(c);
		connections.remove(c);
	}

	/*
	 * A new outgoing connection has been established, and a reference is returned
	 * to it
	 */
	
	// ONLY to be used for establishing connection with main centralised server (and nothing else)
	public synchronized Connection outgoingConnection(Socket s) throws IOException {
		log.debug("outgoing connection: " + Settings.socketAddress(s));
		Connection c = new Connection(s);
		// add to connection first, wait for authenticate success
		backupServerConnections.add(c);
		return c;
	}

	@Override
	public void run() {
		// establish thread for remote host server connection
		initiateConnection();
		log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");

		while (!term) {
			// do something with 5 second intervals in between
//			System.out.println("There are " + connections.size() + " servers connected.");
//			System.out.println("There are " + backupServerConnections.size() + " backup servers connected.");
			System.out.println("ServerLoad status:");

			for (Map.Entry<String, Integer> entry : serverClientLoad.entrySet()) {
				System.out.println("Server:" + entry.getKey());
				System.out.println("Load:" + entry.getValue());
			}
			
			for (Map.Entry<String, JSONObject> entry : serverAddresses.entrySet()) {
				System.out.println("Server:" + entry.getKey());
				System.out.println("Load:" + entry.getValue());
			}

			// kick clients off
			// commented out for testing purposes
			/*
			 * for (Iterator<Connection> iterator = connections.iterator();
			 * iterator.hasNext();) { Connection con = iterator.next(); if
			 * (!con.isServerAuthenticated()) { con.writeMsg(
			 * "{\"command\" : \"ACTIVITY_BROADCAST\",\"expel\" : \"Sorry, clients cannot directly connect centralized server\"}"
			 * ); } }
			 */

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

	// Backup centralised server and regular server authenticate

	@SuppressWarnings("unchecked")
	private boolean sendAuthenticationBackup(Connection c) {
		JSONObject authenticate = new JSONObject();
		authenticate.put("command", "BACKUP_AUTHENTICATE");
		authenticate.put("secret", Settings.getSecret());
		// write message
		if (c.writeMsg(authenticate.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: BACKUP_AUTHENTICATE sent to Port-" + Settings.getRemotePort());
			return true;
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: BACKUP_AUTHENTICATE sending to Port-" + Settings.getRemotePort()
					+ " failed");
			return false;
		}
	}
	
	@SuppressWarnings("unchecked")
	private boolean sendBackupAuthenticationFail(Connection c, String info) {
		JSONObject failureMessage = new JSONObject();
		failureMessage.put("command", "BACKUP_AUTHENTICATION_FAIL");
		failureMessage.put("info", info);
		// write message
		if (c.writeMsg(failureMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: BACKUP_AUTHENTICATE_FAIL sent to "
					+ c.getSocket().getRemoteSocketAddress());
			return true;
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: BACKUP_AUTHENTICATE_FAIL sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
			return false;
		}
	}
	
	@SuppressWarnings("unchecked")
	private boolean sendBackupAuthenticationSuccess(Connection c, String info) {
		JSONObject successMessage = new JSONObject();
		successMessage.put("command", "BACKUP_AUTHENTICATION_SUCCESS");
		successMessage.put("info", info);
		// write message
		if (c.writeMsg(successMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: BACKUP_AUTHENTICATION_SUCCESS sent to "
					+ c.getSocket().getRemoteSocketAddress());
			return true;
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: BACKUP_AUTHENTICATION_SUCCESS sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
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
	
	@SuppressWarnings("unchecked")
	private boolean sendAuthenticateSuccess(Connection c, String info) {
		JSONObject successMessage = new JSONObject();
		successMessage.put("command", "AUTHENTICATION_SUCCESS");
		successMessage.put("info", info);
		// write message
		if (c.writeMsg(successMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATION_SUCCESS sent to "
					+ c.getSocket().getRemoteSocketAddress());
			return true;
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: AUTHENTICATION_SUCCESS sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
			return false;
		}
	}
	
	// Synchronise centralised servers
	
	private void synchroniseCentralisedServers(Connection c) {
		// synchronising memory and user store
		log.debug("initiate synchronisation of centralised servers");
		// synchronise servers' client load memory
		synchroniseServerLoad(c);
		// synchronise server addresses
		synchroniseServerAddresses(c);
		// synchornise user store
		synchroniseUserStore(c);
		// [NOTE] what if the synchronisation fail? server crashes mid-way?
		// close connection and restart?
	}
	
	@SuppressWarnings("unchecked")
	private void synchroniseServerLoad(Connection c) {
		log.debug("synchronising servers' client load");
		// create JSON object for new protocol
		JSONObject synchroniseServerLoadMessage = new JSONObject();
		synchroniseServerLoadMessage.put("command", "SYNCHRONISE_SERVER_LOAD");
		synchroniseServerLoadMessage.put("activity", serverClientLoad);
		// write message to backup server as JSON object
		if (c.writeMsg(synchroniseServerLoadMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_SERVER_LOAD sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_SERVER_LOAD sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}
	
	@SuppressWarnings("unchecked")
	private void synchroniseServerAddresses(Connection c) {
		log.debug("synchronising server addresses");
		// create JSON object for new protocol
		JSONObject synchroniseServerAddressesMessage = new JSONObject();
		synchroniseServerAddressesMessage.put("command", "SYNCHRONISE_SERVER_ADDRESSES");
		synchroniseServerAddressesMessage.put("activity", serverAddresses);
		// write message to backup server as JSON object
		if (c.writeMsg(synchroniseServerAddressesMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_SERVER_ADDRESSES sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_SERVER_ADDRESSES sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}
	
	@SuppressWarnings("unchecked")
	private void synchroniseUserStore(Connection c) {
		log.debug("synchronising user store");
		// create JSON object for new protocol
		JSONObject synchroniseUserStoreMessage = new JSONObject();
		synchroniseUserStoreMessage.put("command", "SYNCHRONISE_USER_STORE");
//		synchroniseUserStoreMessage.put("activity", retrieveUserLocalStorage());
		// [DELETE] for testing
		synchroniseUserStoreMessage.put("activity", testretrieveUserLocalStorage());
		// write message to backup server as JSON object
		if (c.writeMsg(synchroniseUserStoreMessage.toJSONString())) {
			log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_USER_STORE sent to "
					+ c.getSocket().getRemoteSocketAddress());
		} else {
			log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_USER_STORE sending to "
					+ c.getSocket().getRemoteSocketAddress() + " failed");
		}
	}
	
	@SuppressWarnings("unchecked")
	private void synchroniseNewServer(ArrayList<Connection> c, String serverId, JSONObject address) {
		log.debug("synchronising new server to backup");
		// create JSON object for new protocol
		JSONObject synchroniseNewServerMessage = new JSONObject();
		synchroniseNewServerMessage.put("command", "SYNCHRONISE_NEW_SERVER");
		synchroniseNewServerMessage.put("id", serverId);
		synchroniseNewServerMessage.put("address", address);
		for (Connection backupCon : c) {
			// write message to backup server as JSON object
			if (backupCon.writeMsg(synchroniseNewServerMessage.toJSONString())) {
				log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_NEW_SERVER sent to "
						+ backupCon.getSocket().getRemoteSocketAddress());
			} else {
				log.debug("[Port-" + Settings.getLocalPort() + "]: SYNCHRONISE_NEW_SERVER sending to "
						+ backupCon.getSocket().getRemoteSocketAddress() + " failed");
			}
		}
	}
	
	private void updateServerClientLoad(JSONObject activity) {
		for(Object key : activity.keySet()) {
			String serverId = (String) key;
			Integer clientLoad = (int)(long) activity.get(serverId);
			serverClientLoad.put(serverId, clientLoad);	
		}
	}
	
	private void updateServerAddresses(JSONObject activity) {
		for(Object key : activity.keySet()) {
			String serverId = (String) key;
			JSONObject address = (JSONObject) activity.get(serverId);
			serverAddresses.put(serverId, address);	
		}
	}
	
	private void updateUserStore(ArrayList<JSONObject> activity) {
		// [NOTE] assumption is there will always be 1 centralised server up, 
		// and each time 1 disconnect and reconnect again, the user store will keep appending
		// (i.e. should not have a case of server A have X and server B have Y when both are running)
		
		for(JSONObject user : activity) {
			// [NOTE] what if this message is corrupted? but we handle it like invalid message?
			String username = (String) user.get("username");
			if (!checkUsernameExist(username)) {
				// store username and secret if the username was not found
				String secret = (String) user.get("secret");
				storeUsernameSecret(username, secret);
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

	private void executeLoadBalance(Connection c) {
		System.out.println("executeLoadBalance in effect now...");
		String leastId = "";
		int leastLoad = 99999999;
		for (String serverId : serverClientLoad.keySet()) {
			if (serverClientLoad.get(serverId) <= leastLoad) {
				leastId = serverId;
				leastLoad = serverClientLoad.get(serverId);
			}
		}
		redirectClient(c, serverAddresses.get(leastId));
		serverClientLoad.put(leastId, leastLoad + 1);

		// for (String serverId : serverClientLoad.keySet()) {
		// // redirect to least-loaded server
		// // own
		// if (getClientLoad() - serverClientLoad.get(serverId) >= 2) {
		// // send destination server address
		// redirectClient(c, serverAddresses.get(serverId));
		// return true;
		// }
		// }
		// return false;
	}

	@SuppressWarnings("unchecked")
	private void redirectClient(Connection c, JSONObject address) {
		System.out.println("Redirect in effect now...");
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
	
	// [DELETE] for testing
		private ArrayList<JSONObject> testretrieveUserLocalStorage() {
			ArrayList<JSONObject> userstore = new ArrayList<JSONObject>();
			
			// fake username and secret
			JSONObject newUser1 = new JSONObject();
			newUser1.put("username", "eddie");
			newUser1.put("secret", "xyz");
			
			JSONObject newUser2 = new JSONObject();
			newUser2.put("username", "edward");
			newUser2.put("secret", "abc");
			
			JSONObject newUser3 = new JSONObject();
			newUser3.put("username", "rahmat");
			newUser3.put("secret", "123");
			
			JSONObject newUser4 = new JSONObject();
			newUser4.put("username", "yj");
			newUser4.put("secret", "890");
			
			userstore.add(newUser1);
			userstore.add(newUser2);
			userstore.add(newUser3);
			userstore.add(newUser4);
			
			return userstore;
		}
}