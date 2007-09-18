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

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVFileRevisionsHandler extends DAVReportHandler implements ISVNFileRevisionHandler {

    private DAVFileRevisionsRequest myDAVRequest;


    public DAVFileRevisionsHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) throws SVNException {
        super(repositoryManager, request, response);
    }


    protected DAVRequest getDAVRequest() {
        return getFileRevsionsRequest();
    }

    private DAVFileRevisionsRequest getFileRevsionsRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVFileRevisionsRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        setDAVResource(createDAVResource(false, false));

        writeXMLHeader();

        String path = SVNPathUtil.append(getDAVResource().getResourceURI().getPath(), getFileRevsionsRequest().getPath());
        getDAVResource().getRepository().getFileRevisions(path, getFileRevsionsRequest().getStartRevision(), getFileRevsionsRequest().getEndRevision(), this);

        writeXMLFooter();
    }

    public void openRevision(SVNFileRevision fileRevision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put("path", fileRevision.getPath());
        attrs.put("rev", String.valueOf(fileRevision.getRevision()));
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", XMLUtil.XML_STYLE_NORMAL, attrs, null);
        write(xmlBuffer);
        for (Iterator iterator = fileRevision.getRevisionProperties().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String propertyName = (String) entry.getKey();
            String propertyValue = (String) entry.getValue();
            writePropertyTag("rev-prop", propertyName, propertyValue);
        }
        for (Iterator iterator = fileRevision.getPropertiesDelta().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String propertyName = (String) entry.getKey();
            String propertyValue = (String) entry.getValue();
            if (propertyValue != null) {
                writePropertyTag("set-prop", propertyName, propertyValue);
            } else {
                xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "remove-prop", XMLUtil.XML_STYLE_SELF_CLOSING, "name", propertyName, null);
                write(xmlBuffer);
            }
        }
    }

    public void closeRevision(String token) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "file-rev", null);
        write(xmlBuffer);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        StringBuffer xmlBuffer = XMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", XMLUtil.XML_STYLE_NORMAL, null, null);
        write(xmlBuffer);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        writeTextDeltaChunk(diffWindow);
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        textDeltaChunkEnd();
        setWriteTextDeltaHeader(true);
        StringBuffer xmlBuffer = XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "txdelta", null);
        write(xmlBuffer);
    }
}
