/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.util.SVNMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVGetLocationsHandler extends DAVReportHandler implements ISVNLocationEntryHandler {

    private DAVGetLocationsRequest myDAVRequest;

    public DAVGetLocationsHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    protected DAVRequest getDAVRequest() {
        return getGetLocationsRequest();
    }

    private DAVGetLocationsRequest getGetLocationsRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVGetLocationsRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        setDAVResource(getRequestedDAVResource(false, false));

        writeXMLHeader();

        String path = SVNPathUtil.append(getDAVResource().getResourceURI().getPath(), getGetLocationsRequest().getPath());
        getDAVResource().getRepository().getLocations(path, getGetLocationsRequest().getPegRevision(), getGetLocationsRequest().getRevisions(), this);

        writeXMLFooter();
    }

    public void handleLocationEntry(SVNLocationEntry locationEntry) throws SVNException {
        Map attrs = new SVNMap();
        locationEntry.getPath();
        attrs.put(PATH_ATTR, locationEntry.getPath());
        attrs.put(REVISION_ATTR, String.valueOf(locationEntry.getRevision()));
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "location", SVNXMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }
}