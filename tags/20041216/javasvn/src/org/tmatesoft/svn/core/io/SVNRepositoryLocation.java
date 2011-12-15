/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

import org.tmatesoft.svn.util.PathUtil;

/**
 * @author Alexander Kitaev
 */
public class SVNRepositoryLocation {
    
    private String myPath;
    private final String myHost;
    private final int myPort;
    private final String myProtocol;
    private String myAsString;

    public static SVNRepositoryLocation parseURL(String location) throws SVNException {
        if (location == null) {
            return null;
        }
        int index = location.indexOf(':');
        if (index < 0) {
            throw new SVNException("malformed url: " + location);
        }
        String protocol = location.substring(0, index);
        location = "http" + location.substring(protocol.length());
        URL url = null;
        try {
            url = new URL(location); 
        } catch (MalformedURLException e) {
            throw new SVNException("malformed url " + location);
        }
        if (url != null) {
            String host = url.getHost();
            int port = url.getPort();
            if (port < 0) {
                if ("svn".equals(protocol)) {
                    port = 3690;
                } else if ("http".equals(protocol)) {
                    port = 80;
                } else if ("https".equals(protocol)) {
                    port = 443;
                } else if ("svn+ssh".equals(protocol)) {
                    port = 22;
                }
            }
            String path = url.getPath();
            if (!path.endsWith("/")) {
                path += "/";
            }
            return new SVNRepositoryLocation(protocol, host, port, path);
        }
        throw new SVNException("malformed url " + location);
    }

    
    public SVNRepositoryLocation(String protocol, String host, int port, String path) {
        myHost = host;
        myProtocol = protocol;
        myPort = port;
        myPath = path;        
        if (myPath.endsWith("/")) {
            myPath = myPath.substring(0, myPath.length() - "/".length());
        }
    }
    
    public String getProtocol() {
        return myProtocol;
    }
    public String getHost() {
        return myHost;
    }
    public String getPath() {
        return myPath;
    }
    public int getPort() {
        return myPort;
    }
    
    public String toString() {
        if (myAsString != null) {
            return myAsString;
        }
        StringBuffer sb = new StringBuffer();
        sb.append(myProtocol);
        sb.append("://");
        PathUtil.encode(myHost, sb);
        if (myPort != getDefaultPort(myProtocol)) {
            sb.append(':');
            sb.append(myPort);
        }
        for(StringTokenizer tokens = new StringTokenizer(myPath, "/"); tokens.hasMoreTokens();) {
            sb.append("/");
            PathUtil.encode(tokens.nextToken(), sb);
        }
        myAsString = sb.toString();
        return sb.toString();
    }
    
    private static int getDefaultPort(String protocol) {
        if ("http".equals(protocol)) {
            return 80;
        } else if ("svn".equals(protocol)) {
            return -1; // force port saving.
        } else if ("https".equals(protocol)) {
            return 443;
        } else if ("svn+ssh".equals(protocol)) {
            return 22;
        }
        return -1;
    }
}