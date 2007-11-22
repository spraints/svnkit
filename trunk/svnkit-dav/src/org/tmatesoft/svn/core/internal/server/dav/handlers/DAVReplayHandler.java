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
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVReplayHandler extends DAVReportHandler implements ISVNEditor {

    private DAVReplayRequest myDAVRequest;

    public DAVReplayHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }


    protected DAVRequest getDAVRequest() {
        return getReplayRequest();
    }

    private DAVReplayRequest getReplayRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVReplayRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        setDAVResource(getRequestedDAVResource(false, false));

        writeXMLHeader();

        getRequestedRepository().replay(getReplayRequest().getLowRevision(),
                getReplayRequest().getRevision(),
                getReplayRequest().isSendDeltas(),
                this);

        writeXMLFooter();
    }

    private SVNRepository getRequestedRepository() throws SVNException {
        if (getDAVResource().getResourceURI().getPath() == null || getDAVResource().getResourceURI().getPath().length() == 0) {
            return getDAVResource().getRepository();
        }
        SVNURL resourceURL = getDAVResource().getRepository().getLocation();
        SVNURL resultURL = resourceURL.appendPath(getDAVResource().getResourceURI().getPath(), true);
        return SVNRepositoryFactory.create(resultURL);
    }

    public void targetRevision(long revision) throws SVNException {
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "target-revision", SVNXMLUtil.XML_STYLE_SELF_CLOSING, "rev", String.valueOf(revision), null);
        write(xmlBuffer);
    }

    public void openRoot(long revision) throws SVNException {
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "open-root", SVNXMLUtil.XML_STYLE_SELF_CLOSING, REVISION_ATTR, String.valueOf(revision), null);
        write(xmlBuffer);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put(NAME_ATTR, path);
        attrs.put(REVISION_ATTR, String.valueOf(revision));
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "delete-entry", SVNXMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        addEntry("add-directory", path, copyFromPath, copyFromRevision);
    }

    public void openDir(String path, long revision) throws SVNException {
        openEntry("open-directory", path, revision);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        changeEntryProperty("change-directory-prop", name, value);
    }

    public void closeDir() throws SVNException {
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "close-directory", SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, null);
        write(xmlBuffer);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        addEntry("add-file", path, copyFromPath, copyFromRevision);
    }

    public void openFile(String path, long revision) throws SVNException {
        openEntry("open-file", path, revision);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        changeEntryProperty("change-file-prop", name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        Map attrs = new HashMap();
        if (textChecksum != null) {
            attrs.put(CHECKSUM_ATTR, textChecksum);
        }
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "close-file", SVNXMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        Map attrs = new HashMap();
        if (baseChecksum != null) {
            attrs.put(CHECKSUM_ATTR, baseChecksum);
        }
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "apply-textdelta", SVNXMLUtil.XML_STYLE_PROTECT_PCDATA, attrs, null);
        write(xmlBuffer);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        writeTextDeltaChunk(diffWindow);
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
        textDeltaChunkEnd();
        setWriteTextDeltaHeader(true);
        StringBuffer xmlBuffer = SVNXMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, "apply-textdelta", null);
        write(xmlBuffer);
    }

    public void abortEdit() throws SVNException {
    }

    private void addEntry(String tagName, String path, String copyfromPath, long copyfromRevision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put(NAME_ATTR, path);
        if (copyfromPath != null) {
            attrs.put(COPYFROM_PATH_ATTR, copyfromPath);
            attrs.put(COPYFROM_REVISION_ATTR, String.valueOf(copyfromRevision));
        }
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, SVNXMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    private void openEntry(String tagName, String path, long revision) throws SVNException {
        Map attrs = new HashMap();
        attrs.put(NAME_ATTR, path);
        attrs.put(REVISION_ATTR, String.valueOf(revision));
        StringBuffer xmlBuffer = SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, SVNXMLUtil.XML_STYLE_SELF_CLOSING, attrs, null);
        write(xmlBuffer);
    }

    private void changeEntryProperty(String tagName, String propertyName, String propertyValue) throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        if (propertyValue != null) {
            try {
                propertyValue = SVNBase64.byteArrayToBase64(propertyValue.getBytes(UTF8_ENCODING));
                SVNXMLUtil.openXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, SVNXMLUtil.XML_STYLE_PROTECT_PCDATA, NAME_ATTR, propertyName, xmlBuffer);
                xmlBuffer.append(propertyValue);
                SVNXMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, xmlBuffer);
            } catch (UnsupportedEncodingException e) {
            }
        } else {
            Map attrs = new HashMap();
            attrs.put(NAME_ATTR, propertyName);
            attrs.put(DELETE_ATTR, Boolean.TRUE.toString());
            SVNXMLUtil.openCDataTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, tagName, propertyValue, attrs, xmlBuffer);
        }
        write(xmlBuffer);
    }
}
