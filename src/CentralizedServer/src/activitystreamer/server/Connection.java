package activitystreamer.server;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;


public class Connection extends Thread {
	private static final Logger log = LogManager.getLogger();
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;
	private boolean open = false;
	private Socket socket;
	private boolean term=false;
	// added variable(s)
	private boolean serverAuthenticated = false; // required for serverAnnounce to check if connecting servers have been authenticated before reading server announce message
	private boolean loggedInClient = false; // to indicate that client has logged in; clients logged in are included in load count
	private String clientUserName; 
	private String clientSecret;
	private boolean backupCentralisedServer = false;
	private boolean mainCentralisedServer = false;
	private String serverId;

	Connection(Socket socket) throws IOException{
		in = new DataInputStream(socket.getInputStream());
	    out = new DataOutputStream(socket.getOutputStream());
	    inreader = new BufferedReader( new InputStreamReader(in));
	    outwriter = new PrintWriter(out, true);
	    this.socket = socket;
	    
	    open = true;
	    start();
	}
	
	/*
	 * returns true if the message was written, otherwise false
	 */
	public boolean writeMsg(String msg) {
		if(open){
			outwriter.println(msg);
			outwriter.flush();
			return true;	
		}
		return false;
	}
	
	public void closeCon(){
		if(open){
			log.info("closing connection "+Settings.socketAddress(socket));
			try {
				term=true;
				inreader.close();
				out.close();
			} catch (IOException e) {
				// already closed?
				log.error("received exception closing the connection "+Settings.socketAddress(socket)+": "+e);
			}
		}
	}
	
	public void run(){
		try {
			String data;
			while(!term && (data = inreader.readLine())!=null){
				term=Control.getInstance().process(this,data);
			}
			log.debug("connection closed to "+Settings.socketAddress(socket));
			// check if it's a backup centralized server; remove from the right connection array
			if(backupCentralisedServer) {
				Control.getInstance().backupServerConnectionClosed(this);
			} else if (mainCentralisedServer) {
				Control.getInstance().mainServerConnectionClosed(this);
			} else {
				Control.getInstance().connectionClosed(this);
			}
			in.close();
			
		} catch (IOException e) {
			log.error("connection "+Settings.socketAddress(socket)+" closed with exception: "+e);
			// check if it's a backup centralised server; remove from the right connection array
			if(backupCentralisedServer) {
				Control.getInstance().backupServerConnectionClosed(this);
			} else if (mainCentralisedServer) {
				Control.getInstance().mainServerConnectionClosed(this);
			} else {
				Control.getInstance().connectionClosed(this);
			}
		}
		open=false;
	}
	
	public Socket getSocket() {
		return socket;
	}
	
	public boolean isOpen() {
		return open;
	}
	
	// added method(s)
	
	public boolean isServerAuthenticated() {
		return serverAuthenticated;
	}
	
	public void setServerAuthenticated() {
		this.serverAuthenticated = true;
	}
	
	public boolean isClient() {
		return loggedInClient;
	}
	
	public void setLoggedInClient() {
		this.loggedInClient = true;
	}
	
	public String getClientUserName() {
		return clientUserName;
	}

	public void setClientUserName(String clientUserName) {
		this.clientUserName = clientUserName;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public boolean isBackupCentralisedServer() {
		return backupCentralisedServer;
	}

	public void setBackupCentralisedServer() {
		this.backupCentralisedServer = true;
	}
	
	public void setServerID(String serverId) {
		this.serverId = serverId;
	}

	public String getServerId() {
		return serverId;
	}
	
	public boolean isMainCentralisedServer() {
		return mainCentralisedServer;
	}

	public void setMainCentralisedServer() {
		this.mainCentralisedServer = true;
	}
}
