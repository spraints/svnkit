/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNGanymedConnector implements ISVNConnector {

    private static final String SVNSERVE_COMMAND = "svnserve -t";
    private static final String SVNSERVE_COMMAND_WITH_USER_NAME = "svnserve -t --tunnel-user ";
    private static final String ourCustomUserName = System.getProperty("javasvn.ssh.author", "");

    private Session mySession;
    private InputStream myInputStream;
    private OutputStream myOutputStream;
    private Connection myConnection;

    public void open(SVNRepositoryImpl repository) throws SVNException {
        ISVNAuthenticationManager authManager = repository.getAuthenticationManager();

        String realm = repository.getLocation().getProtocol() + "://" + repository.getLocation().getHost();
        if (repository.getLocation().hasPort()) {
            realm += ":" + repository.getLocation().getPort();
        }        

        int reconnect = 1;
        while(true) {
            SVNSSHAuthentication authentication = (SVNSSHAuthentication) authManager.getFirstAuthentication(ISVNAuthenticationManager.SSH, realm, repository.getLocation());
            Connection connection = null;
            
            while (authentication != null) {
                try {
                    connection = SVNGanymedSession.getConnection(repository.getLocation(), authentication);
                    if (connection == null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Cannot connect to ''{0}''", repository.getLocation().setPath("", false));
                        SVNErrorManager.error(err);
                    }
                    authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.SSH, realm, null, authentication);
                    repository.setExternalUserName(ourCustomUserName);
                    break;
                } catch (SVNAuthenticationException e) {
                    authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.SSH, realm, e.getErrorMessage(), authentication);
                    authentication = (SVNSSHAuthentication) authManager.getNextAuthentication(ISVNAuthenticationManager.SSH, realm, repository.getLocation());
                    connection = null;
                }
            }
            if (authentication == null) {
                SVNErrorManager.cancel("authentication cancelled");
            } else if (connection == null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Can not establish connection with to ''{0}''", realm));
            }
            try {
                mySession = connection.openSession();
                if ("".equals(repository.getExternalUserName())) {
                    mySession.execCommand(SVNSERVE_COMMAND);
                } else {
                    mySession.execCommand(SVNSERVE_COMMAND_WITH_USER_NAME + repository.getExternalUserName());
                }
    
                myOutputStream = mySession.getStdin();
                myInputStream = mySession.getStdout();
                new StreamGobbler(mySession.getStderr());
                if (!SVNGanymedSession.isUsePersistentConnection()) {
                    myConnection = connection;
                } 
                return;
            } catch (IOException e) {
                reconnect--;
                if (reconnect >= 0) {
                    // try again, but close session first.
                    SVNGanymedSession.closeConnection(connection);
                    connection = null;
                    continue;
                }
                SVNDebugLog.logInfo(e);
                close();
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED, "Cannot connect to ''{0}'': {1}", new Object[] {repository.getLocation().setPath("", false), e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            }
        }
    }

    public void close() throws SVNException {
        SVNFileUtil.closeFile(myOutputStream);
        SVNFileUtil.closeFile(myInputStream);
        if (mySession != null) {
            mySession.close();
        }
        if (!SVNGanymedSession.isUsePersistentConnection() && myConnection != null) {
            SVNGanymedSession.closeConnection(myConnection);
            myConnection = null;
        }
        mySession = null;
        myOutputStream = null;
        myInputStream = null;
    }

    public InputStream getInputStream() throws IOException {
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        return myOutputStream;
    }

    public boolean isConnected(SVNRepositoryImpl repos) throws SVNException {
        return true;
    }
}