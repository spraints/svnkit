/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceState;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVMakeCollectionHandler extends ServletDAVHandler {

    protected DAVMakeCollectionHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    public void execute() throws SVNException {
        int status = processMkColBody();
        if (status != HttpServletResponse.SC_OK) {
            setResponseStatus(status);
            return;
        }
        
        DAVResource resource = getRequestedDAVResource(false, false);
        if (resource.exists()) {
            setResponseStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        
        DAVResourceState resourceState = getResourceState(resource);
        validateRequest(resource, DAVDepth.DEPTH_ZERO, resourceState == DAVResourceState.NULL ? DAV_VALIDATE_PARENT : DAV_VALIDATE_RESOURCE, 
                null, null, null);
        
        
    }

    protected DAVRequest getDAVRequest() {
        return null;
    }

    private int processMkColBody() throws SVNException {
        String transferEncoding = getRequestHeader(HTTPHeader.TRANSFER_ENCODING_HEADER);
        String contentLength = getRequestHeader(HTTPHeader.CONTENT_LENGTH_HEADER);
        
        boolean readChunked = false;
        int remaining = 0;
        if (transferEncoding != null) {
            if (!transferEncoding.equalsIgnoreCase("chunked")) {
                //throw new DAVException("Unknown Transfer-Encoding ", new Object[] { transferEncoding }, HttpServletResponse.SC_NOT_IMPLEMENTED, 0);
                return HttpServletResponse.SC_NOT_IMPLEMENTED;
            }
            readChunked = true;
        } else if (contentLength != null) {
            
            try {
                remaining = Integer.parseInt(contentLength.trim());
            } catch (NumberFormatException nfe) {
                //throw new DAVException("Invalid Content-Length {0}", new Object[] { contentLength }, HttpServletResponse.SC_BAD_REQUEST, 0);
                return HttpServletResponse.SC_BAD_REQUEST;
            }
        }
        
        if (readChunked || remaining > 0) {
            //throw new DAVException(DAVServlet.getStatusLine(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE), HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, 0);
            return HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
        }
        
        readInput(true);
        return HttpServletResponse.SC_OK;
    }
}
