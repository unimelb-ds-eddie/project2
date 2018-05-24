package activitystreamer.util;

import java.math.BigInteger;
import java.net.Socket;
import java.security.SecureRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Settings {
	private static final Logger log = LogManager.getLogger();
	private static SecureRandom random = new SecureRandom();
	private static int localPort = 3780;
	private static String localHostname = "localhost";
	private static String remoteHostname = null;
	private static int remotePort = 3780;
	private static int activityInterval = 8000; // milliseconds
	private static String secret = nextSecret();
	private static String username = "anonymous";
	// added global variable(s)
	private static String serverId = nextSecret();
	
//	private static String backupRemoteHostname = "localhost"; // hardcoded
//	private static int backupRemotePort = 3790; // hardcoded

	public static int getLocalPort() {
		return localPort;
	}

	public static void setLocalPort(int localPort) {
		if (localPort < 0 || localPort > 65535) {
			log.error("supplied port " + localPort + " is out of range, using " + getLocalPort());
		} else {
			Settings.localPort = localPort;
		}
	}

	public static int getRemotePort() {
		return remotePort;
	}

	public static void setRemotePort(int remotePort) {
		if (remotePort < 0 || remotePort > 65535) {
			log.error("supplied port " + remotePort + " is out of range, using " + getRemotePort());
		} else {
			Settings.remotePort = remotePort;
		}
	}

	public static String getRemoteHostname() {
		return remoteHostname;
	}

	public static void setRemoteHostname(String remoteHostname) {
		Settings.remoteHostname = remoteHostname;
	}

	public static int getActivityInterval() {
		return activityInterval;
	}

	public static void setActivityInterval(int activityInterval) {
		Settings.activityInterval = activityInterval;
	}

	public static String getSecret() {
		return secret;
	}

	public static void setSecret(String s) {
		secret = s;
	}

	public static String getUsername() {
		return username;
	}

	public static void setUsername(String username) {
		Settings.username = username;
	}

	public static String getLocalHostname() {
		return localHostname;
	}

	public static void setLocalHostname(String localHostname) {
		Settings.localHostname = localHostname;
	}

	// added global setter(s) and getter(s)

	public static String getServerId() {
		return serverId;
	}

	public static void setServerId(String serverId) {
		Settings.serverId = serverId;
	}

	/*
	 * some general helper functions
	 */

	public static String socketAddress(Socket socket) {
		return socket.getInetAddress() + ":" + socket.getPort();
	}

	public static String nextSecret() {
		return new BigInteger(130, random).toString(32);
	}
	
	// added methods
	
//	public static String getBackupRemoteHostname() {
//		return backupRemoteHostname;
//	}
//	
//	public static int getBackupRemotePort() {
//		return backupRemotePort;
//	}
//
//	public static void setBackupRemoteHostname(String backupRemoteHostname) {
//		Settings.backupRemoteHostname = backupRemoteHostname;
//	}
//
//	public static void setBackupRemotePort(int backupRemotePort) {
//		Settings.backupRemotePort = backupRemotePort;
//	}
	
	

}
