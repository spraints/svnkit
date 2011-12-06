package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNCredentials;
import org.tmatesoft.svn.core.io.ISVNSSHCredentials;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UserInfo;

public class SVNJSchSession {

	private static final int TIMEOUT = 10 * 1000;
	private static Map ourSessionsPool = new Hashtable();

	static Session getSession(SVNRepositoryLocation location, ISVNCredentials credentials) throws SVNAuthenticationException {
		if ("".equals(credentials.getName()) || credentials.getName() == null) {
			throw new SVNAuthenticationException("User name is required to establish svn+shh connection");
		}
		String key = credentials.getName() + ":" + location.getHost() + ":" + location.getPort();
		Session session = (Session) ourSessionsPool.get(key);
		if (session != null && !session.isConnected()) {
			ourSessionsPool.remove(key);
			DebugLog.log("SESSION " + key + " disposed");
			session = null;
		}
		try {
			if (session == null) {
				JSch jsch = new JSch();
				String privateKey = null;
				String passphrase = null;
				if (credentials instanceof ISVNSSHCredentials) {
					ISVNSSHCredentials sshCredentials = (ISVNSSHCredentials) credentials;
					privateKey = sshCredentials.getPrivateKeyID();
					passphrase = sshCredentials.getPassphrase();
					if (privateKey != null && passphrase != null) {
						jsch.addIdentity(privateKey, passphrase);
					} else if (privateKey != null) {
						jsch.addIdentity(privateKey);
					}
				}
				session = jsch.getSession(credentials.getName(), location.getHost(), location.getPort());
				
				UserInfo userInfo = new EmptyUserInfo(credentials.getPassword(), passphrase);
				session.setUserInfo(userInfo);				
				session.setSocketFactory(new SimpleSocketFactory());
				DebugLog.log("SESSION " + key + " created");
				session.setTimeout(TIMEOUT);
				session.connect();
				session.setTimeout(0);
				ourSessionsPool.put(key, session);
				DebugLog.log("SESSION " + key + " connected");
			} else {
				DebugLog.log("SESSION " + key + " reused");
			}
			return session;
		} catch (JSchException e) {
			DebugLog.error(e);
			if (session != null && session.isConnected()) {
				session.disconnect();
        		DebugLog.log("DISCONNECTING: " + session);
			}
			DebugLog.log("SESSION " + key + " diconnected");
			ourSessionsPool.remove(key);
			DebugLog.log("SESSION " + key + " disposed");
			throw new SVNAuthenticationException(e);
		}
	}

	public static void shutdown() {
		if (ourSessionsPool.size() > 0) {
			for (Iterator e = ourSessionsPool.values().iterator(); e.hasNext(); ) {
				Session session = (Session) (e.next());
				try {
					session.disconnect();
            		DebugLog.log("DISCONNECTING: " + session);
				} catch (Exception ee) {
				}
			}
			ourSessionsPool.clear();
		}
	}

	private static class SimpleSocketFactory implements SocketFactory {
		private InputStream myInputStream = null;
		private OutputStream myOutputStream = null;
		
		public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
			Socket socket = null;
			socket = new Socket(host, port);
			socket.setKeepAlive(true);
			socket.setReuseAddress(true);
			return socket;
		}
		public InputStream getInputStream(Socket socket) throws IOException {
			if (myInputStream == null)
				myInputStream = socket.getInputStream();
			return myInputStream;
		}
		public OutputStream getOutputStream(Socket socket) throws IOException {
			if (myOutputStream == null)
				myOutputStream = socket.getOutputStream();
			return myOutputStream;
		}
	}
    private static class EmptyUserInfo implements UserInfo {

        private String myPassword;
        private String myPassphrase;

        public EmptyUserInfo(String password, String passphrase) {
            myPassword = password;
            myPassphrase = passphrase;
        }

        public String getPassphrase() {
            return myPassphrase;
        }

        public String getPassword() {
            return myPassword;
        }

        public boolean promptPassword(String arg0) {
            return myPassword != null;
        }

        public boolean promptPassphrase(String arg0) {
            return myPassphrase != null;
        }

        public boolean promptYesNo(String arg0) {
            return true;
        }

        public void showMessage(String arg0) {
        	DebugLog.log("jsch message: " + arg0);
        }
    }}