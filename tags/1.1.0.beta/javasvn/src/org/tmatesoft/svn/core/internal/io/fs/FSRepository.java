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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSRepository extends SVNRepository implements ISVNReporter {

    private File myReposRootDir;
    private FSReporterContext myReporterContext;
    private FSFS myFSFS;
    private SVNDeltaGenerator myDeltaGenerator;
    private SVNDeltaCombiner myDeltaCombiner;
    //invalid revision number, suppose it to be -1
    public static final int SVN_INVALID_REVNUM = -1;

    protected FSRepository(SVNURL location, ISVNSession options) {
        super(location, options);
    }

    public void testConnection() throws SVNException {
        // try to open and close a repository
        try {
            openRepository();
        } finally {
            closeRepository();
        }
    }

    private void openRepository() throws SVNException {
        try {
            openRepositoryRoot();
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED, "Unable to open repository ''{0}''", getLocation().toDecodedString());
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err.wrap("Unable to open an ra_local session to URL"));
        }
    }

    private void openRepositoryRoot() throws SVNException {
        lock();
        
        if (!"".equals(getLocation().getHost()) && !getLocation().getHost().equalsIgnoreCase("localhost")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Local URL ''{0}'' contains unsupported hostname", getLocation().toDecodedString());
            SVNErrorManager.error(err);
        }
        
        myReposRootDir = FSFS.findRepositoryRoot(new File(getLocation().getPath()));

        if (myReposRootDir == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED, "Unable to open repository ''{0}''", getLocation().toDecodedString());
            SVNErrorManager.error(err);
        }
        
        myFSFS = new FSFS(myReposRootDir);
        myFSFS.open();
        
        String rootDir = myReposRootDir.getPath();
        rootDir = rootDir.replace(File.separatorChar, '/');
        if (!rootDir.startsWith("/")) {
            rootDir = "/" + rootDir;
        }
        setRepositoryCredentials(myFSFS.getUUID(), getLocation().setPath(rootDir, false));
    }

    void closeRepository() {
        unlock();
    }

    public File getRepositoryRootDir() {
        return myReposRootDir;
    }

    File getReposRootDir() {
        return myReposRootDir;
    }

    public long getLatestRevision() throws SVNException {
        try {
            openRepository();
            return myFSFS.getYoungestRevision();
        } finally {
            closeRepository();
        }
    }

    public long getDatedRevision(Date date) throws SVNException {
        if (date == null) {
            return getLatestRevision();
        }
        try {
            openRepository();
            long latest = myFSFS.getYoungestRevision();
            long top = latest;
            long bottom = 0;
            long middle;
            Date currentTime = null;

            while (bottom <= top) {
                middle = (top + bottom) / 2;
                currentTime = getRevisionTime(middle);
                if (currentTime.compareTo(date) > 0) {
                    if ((middle - 1) < 0) {
                        return 0;
                    }
                    Date prevTime = getRevisionTime(middle - 1);
                    if (prevTime.compareTo(date) < 0) {
                        return middle - 1;
                    }
                    top = middle - 1;
                } else if (currentTime.compareTo(date) < 0) {
                    if ((middle + 1) > latest) {
                        return latest;
                    }
                    Date nextTime = getRevisionTime(middle + 1);
                    if (nextTime.compareTo(date) > 0) {
                        return middle + 1;
                    }
                    bottom = middle + 1;
                } else {
                    return middle;
                }
            }
            return 0;
        } finally {
            closeRepository();
        }
    }

    private Date getRevisionTime(long revision) throws SVNException {
        Map revisionProperties = myFSFS.getRevisionProperties(revision);
        String timeString = (String) revisionProperties.get(SVNRevisionProperty.DATE);
        if (timeString == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Failed to find time on revision {0,number,integer}", new Long(revision));
            SVNErrorManager.error(err);
        }
        return SVNTimeUtil.parseDateString(timeString);
    }

    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        assertValidRevision(revision);
        try {
            openRepository();
            properties = properties == null ? new HashMap() : properties; 
            properties.putAll(myFSFS.getRevisionProperties(revision));
        } finally {
            closeRepository();
        }
        return properties;
    }

    public void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException {
        assertValidRevision(revision);
        try {
            openRepository();
            if (!SVNProperty.isRegularProperty(propertyName)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS,
                        "Storage of non-regular property ''{0}'' is disallowed through the repository interface, and could indicate a bug in your client", propertyName);
                SVNErrorManager.error(err);
            }
            String userName = getUserName();
            Map revProps = myFSFS.getRevisionProperties(revision);
            String oldValue = (String)revProps.get(propertyName);
            String action = null;
            if (propertyValue == null) {
                action = FSHooks.REVPROP_DELETE;
            } else if (oldValue == null) {
                action = FSHooks.REVPROP_ADD;
            } else {
                action = FSHooks.REVPROP_MODIFY;
            }
            myFSFS.setRevisionProperty(revision, propertyName, propertyValue, oldValue, userName, action);
        } finally {
            closeRepository();
        }
    }

    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        assertValidRevision(revision);
        if (propertyName == null) {
            return null;
        }
        try {
            openRepository();
            return (String) myFSFS.getRevisionProperties(revision).get(propertyName);
        } finally {
            closeRepository();
        }
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = myFSFS.getYoungestRevision();
            }
            String repositoryPath = getRepositoryPath(path);
            FSRevisionRoot root = myFSFS.createRevisionRoot(revision);
            return root.checkNodeKind(repositoryPath); 
        } finally {
            closeRepository();
        }
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = myFSFS.getYoungestRevision();
            }
            
            String repositoryPath = getRepositoryPath(path);
            FSRevisionRoot root = myFSFS.createRevisionRoot(revision);
            
            if (contents != null) {
                InputStream fileStream = null;
                try {
                    fileStream = root.getFileStreamForPath(new SVNDeltaCombiner(), repositoryPath);
                    byte[] buffer = new byte[102400];
                    while (true) {
                        int length = fileStream.read(buffer);
                        if (length > 0) {
                            contents.write(buffer, 0, length);
                        }
                        if (length != 102400) {
                            break;
                        }
                    }
                } catch (IOException ioe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                    SVNErrorManager.error(err, ioe);
                } finally {
                    SVNFileUtil.closeFile(fileStream);
                }
            }
            if (properties != null) {
                FSRevisionNode revNode = root.getRevisionNode(repositoryPath);
                properties.putAll(collectProperties(revNode));
            }
            return revision;
        } finally {
            closeRepository();
        }
    }

    private Collection getDirEntries(FSRevisionNode parent, SVNURL parentURL, boolean includeLogs) throws SVNException {
        Map entries = parent.getDirEntries(myFSFS);
        Set keys = entries.keySet();
        Iterator dirEntries = keys.iterator();
        Collection dirEntriesList = new LinkedList();
        while (dirEntries.hasNext()) {
            String name = (String) dirEntries.next();
            FSEntry repEntry = (FSEntry) entries.get(name);
            if (repEntry != null) {
                dirEntriesList.add(buildDirEntry(repEntry, parentURL, null, includeLogs));
            }
        }
        return dirEntriesList;
    }

    private Map collectProperties(FSRevisionNode revNode) throws SVNException {
        Map properties = new HashMap();
        Map versionedProps = revNode.getProperties(myFSFS);
        if (versionedProps != null && versionedProps.size() > 0) {
            properties.putAll(versionedProps);
        }
        Map metaprops = null;
        try {
            metaprops = getMetaProps(revNode.getId().getRevision());
        } catch (SVNException svne) {
            //
        }
        if (metaprops != null && metaprops.size() > 0) {
            properties.putAll(metaprops);
        }
        return properties;
    }

    public Map getMetaProps(long revision) throws SVNException {
        Map metaProps = new HashMap();
        Map revProps = myFSFS.getRevisionProperties(revision);
        String author = (String) revProps.get(SVNRevisionProperty.AUTHOR);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        String uuid = getRepositoryUUID(true);
        String rev = String.valueOf(revision);

        metaProps.put(SVNProperty.LAST_AUTHOR, author);
        metaProps.put(SVNProperty.COMMITTED_DATE, date);
        metaProps.put(SVNProperty.COMMITTED_REVISION, rev);
        metaProps.put(SVNProperty.UUID, uuid);
        return metaProps;
    }

    private SVNDirEntry buildDirEntry(FSEntry repEntry, SVNURL parentURL, FSRevisionNode entryNode, boolean includeLogs) throws SVNException {
        entryNode = entryNode == null ? myFSFS.getRevisionNode(repEntry.getId()) : entryNode;
        long size = 0;
        if (entryNode.getType() == SVNNodeKind.FILE) {
            size = getFileLength(entryNode);
        }
        Map props = null;
        props = entryNode.getProperties(myFSFS);
        boolean hasProps = (props == null || props.size() == 0) ? false : true;
        Map revProps = null;
        revProps = myFSFS.getRevisionProperties(repEntry.getId().getRevision());
        String lastAuthor = null;
        String log = null;
        Date lastCommitDate = null;
        if (revProps != null && revProps.size() > 0) {
            lastAuthor = (String) revProps.get(SVNRevisionProperty.AUTHOR);
            log = (String) revProps.get(SVNRevisionProperty.LOG);
            String timeString = (String) revProps.get(SVNRevisionProperty.DATE);
            lastCommitDate = timeString != null ? SVNTimeUtil.parseDateString(timeString) : null;
        }
        SVNURL entryURL = parentURL.appendPath(repEntry.getName(), false);
        SVNDirEntry dirEntry = new SVNDirEntry(entryURL, repEntry.getName(), repEntry.getType(), size, hasProps, repEntry.getId().getRevision(), lastCommitDate, lastAuthor, includeLogs ? log : null);
        dirEntry.setRelativePath(repEntry.getName());
        return dirEntry;
    }

    public long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = myFSFS.getYoungestRevision();
            }
            String repositoryPath = getRepositoryPath(path);
            FSRevisionRoot root = myFSFS.createRevisionRoot(revision);
            
            FSRevisionNode parent = root.getRevisionNode(repositoryPath);
            if (handler != null) {
                SVNURL parentURL = getLocation().appendPath(path, false);
                Collection entriesCollection = getDirEntries(parent, parentURL, false);
                Iterator entries = entriesCollection.iterator();
                while (entries.hasNext()) {
                    SVNDirEntry entry = (SVNDirEntry) entries.next();
                    handler.handleDirEntry(entry);
                }
            }
            if (properties != null) {
                properties.putAll(collectProperties(parent));
            }
            return revision;
        } finally {
            closeRepository();
        }
    }

    public SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = myFSFS.getYoungestRevision();
            }

            String repositoryPath = getRepositoryPath(path);
            SVNURL parentURL = getLocation().appendPath(path, false);
            
            FSRevisionRoot root = myFSFS.createRevisionRoot(revision);
            
            FSRevisionNode parent = root.getRevisionNode(repositoryPath);
            if (entries != null) {
                entries.addAll(getDirEntries(parent, parentURL, includeCommitMessages));
            }
            SVNDirEntry parentDirEntry = buildDirEntry(new FSEntry(parent.getId(), parent.getType(), ""), parentURL, parent, false);
            return parentDirEntry;
        } finally {
            closeRepository();
        }
    }

    public int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException {
        try {
            openRepository();
            path = getRepositoryPath(path);
            startRevision = isInvalidRevision(startRevision) ? myFSFS.getYoungestRevision() : startRevision;
            endRevision = isInvalidRevision(endRevision) ? myFSFS.getYoungestRevision() : endRevision;
            FSRevisionRoot root = myFSFS.createRevisionRoot(endRevision);

            if (root.checkNodeKind(path) != SVNNodeKind.FILE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "''{0}'' is not a file", path);
                SVNErrorManager.error(err);
            }
            LinkedList locationEntries = new LinkedList();

            FSNodeHistory history = FSNodeHistory.getNodeHistory(root, path);

            while (true) {
                history = history.fsHistoryPrev(true, myFSFS);
                if (history == null) {
                    break;
                }
                long histRev = history.getHistoryEntry().getRevision();
                String histPath = history.getHistoryEntry().getPath();
                locationEntries.addFirst(new SVNLocationEntry(histRev, histPath));
                if (histRev <= startRevision) {
                    break;
                }
            }

            if (locationEntries.size() == 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error: there're no file revisions to get");
                SVNErrorManager.error(err);
            }
            
            FSRoot lastRoot = null;
            String lastPath = null;
            Map lastProps = new HashMap();
            for (ListIterator locations = locationEntries.listIterator(); locations.hasNext();) {
                SVNLocationEntry location = (SVNLocationEntry) locations.next();
                long rev = location.getRevision();
                String revPath = location.getPath();
                Map revProps = myFSFS.getRevisionProperties(rev);

                root = myFSFS.createRevisionRoot(rev);

                FSRevisionNode fileNode = root.getRevisionNode(revPath);
                Map props = fileNode.getProperties(myFSFS);
                Map propDiffs = getPropsDiffs(props, lastProps);
                boolean contentsChanged = false;
                if (lastRoot != null) {
                    contentsChanged = areFileContentsChanged(lastRoot, lastPath, root, revPath);
                } else {
                    contentsChanged = true;
                }

                if (handler != null) {
                    handler.openRevision(new SVNFileRevision(revPath, rev, revProps, propDiffs));
                    if (contentsChanged) {
                        SVNDeltaCombiner sourceCombiner = new SVNDeltaCombiner();
                        SVNDeltaCombiner targetCombiner = new SVNDeltaCombiner();
                        handler.applyTextDelta(path, null);
                        InputStream sourceStream = null;
                        InputStream targetStream = null;
                        try {
                            if (lastRoot != null && lastPath != null) {
                                sourceStream = lastRoot.getFileStreamForPath(sourceCombiner, lastPath);
                            } else {
                                sourceStream = FSInputStream.createDeltaStream(sourceCombiner, (FSRevisionNode) null, myFSFS);
                            }
                            targetStream = root.getFileStreamForPath(targetCombiner, revPath);
                            SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
                            deltaGenerator.sendDelta(path, sourceStream, 0, targetStream, handler, false);
                        } finally {
                            SVNFileUtil.closeFile(sourceStream);
                            SVNFileUtil.closeFile(targetStream);
                        }
                        handler.closeRevision(path);
                    } else {
                        handler.closeRevision(path);
                    }
                }
                lastRoot = root;
                lastPath = revPath;
                lastProps = props;
            }
            return locationEntries.size();
        } finally {
            closeRepository();
        }
    }

    public long log(String[] targetPaths, long startRevision, long endRevision, boolean discoverChangedPaths, boolean strictNode, long limit, ISVNLogEntryHandler handler) throws SVNException {
        try {
            openRepository();
            String[] absPaths = null;
            if (targetPaths != null) {
                absPaths = new String[targetPaths.length];
                for (int i = 0; i < targetPaths.length; i++) {
                    absPaths[i] = getRepositoryPath(targetPaths[i]);
                }
            }
            long histStart = startRevision;
            long histEnd = endRevision;
            long youngestRev = myFSFS.getYoungestRevision();
            
            if (isInvalidRevision(startRevision)) {
                startRevision = youngestRev;
            }
            
            if (isInvalidRevision(endRevision)) {
                endRevision = youngestRev;
            }

            if (startRevision > youngestRev) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(startRevision));
                SVNErrorManager.error(err);
            }
            if (endRevision > youngestRev) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision {0,number,integer}", new Long(endRevision));
                SVNErrorManager.error(err);
            }

            if (startRevision > endRevision) {
                histStart = endRevision;
                histEnd = startRevision;
            }

            long sendCount = 0;
            if (absPaths == null || absPaths.length == 0 || (absPaths.length == 1 && "/".equals(absPaths[0]))) {
                sendCount = histEnd - histStart + 1;
                if (limit != 0 && sendCount > limit) {
                    sendCount = limit;
                }
                for (int i = 0; i < sendCount; i++) {
                    long rev = histStart + i;
                    if (startRevision > endRevision) {
                        rev = histEnd - i;
                    }
                    if (handler != null) {
                        sendChanges(rev, discoverChangedPaths, handler);
                    }
                }
                return sendCount;
            }

            LinkedList histories = new LinkedList();
            FSRevisionRoot root = myFSFS.createRevisionRoot(histEnd);
            for (int i = 0; i < absPaths.length; i++) {
                String path = absPaths[i];
                FSNodeHistory hist = FSNodeHistory.getNodeHistory(root, path);
                LogPathInfo info = new LogPathInfo(hist);
                info.pickUpNextHistory(strictNode, histStart);
                histories.addLast(info);
            }

            LinkedList revisions = null;
            boolean anyHistoriesLeft = true;
            for (long currentRev = histEnd; currentRev >= histStart && anyHistoriesLeft; currentRev = getNextHistoryRevision(histories)) {
                boolean changed = false;
                anyHistoriesLeft = false;
                for (ListIterator infoes = histories.listIterator(); infoes.hasNext();) {
                    LogPathInfo info = (LogPathInfo) infoes.next();

                    if (info.checkHistory(currentRev, strictNode, histStart)) {
                        changed = true;
                    }
                    
                    if (info.getHistory() != null) {
                        anyHistoriesLeft = true;
                    }
                }

                if (changed) {
                    if (startRevision > endRevision) {
                        if (handler != null) {
                            sendChanges(currentRev, discoverChangedPaths, handler);
                        }
                        if (limit != 0 && ++sendCount >= limit) {
                            break;
                        }
                    } else {
                        if (revisions == null) {
                            revisions = new LinkedList();
                        }
                        revisions.addFirst(new Long(currentRev));
                    }
                }
            }

            if (revisions != null) {
                int i = 0;
                for (ListIterator revs = revisions.listIterator(); revs.hasNext();) {
                    sendChanges(((Long) revs.next()).longValue(), discoverChangedPaths, handler);
                    if (limit != 0 && ++i >= limit) {
                        break;
                    }
                }
                return i;
            }
            return sendCount;
        } finally {
            closeRepository();
        }
    }

    private long getNextHistoryRevision(LinkedList histories) {
        long nextRevision = FSRepository.SVN_INVALID_REVNUM;
        for (ListIterator infoes = histories.listIterator(); infoes.hasNext();) {
            LogPathInfo info = (LogPathInfo) infoes.next();
            if (info.getHistory() == null) {
                continue;
            }
            long historyRevision = info.getHistoryRevision();
            if (historyRevision > nextRevision) {
                nextRevision = historyRevision;
            }
        }
        return nextRevision;
    }

    private class LogPathInfo {

        private FSNodeHistory myHistory;

        private LogPathInfo(FSNodeHistory hist) {
            myHistory = hist;
        }

        public FSNodeHistory getHistory() {
            return myHistory;
        }

        public long getHistoryRevision() {
            return myHistory == null ? FSRepository.SVN_INVALID_REVNUM : myHistory.getHistoryEntry().getRevision();
        }

        public void pickUpNextHistory(boolean strict, long start) throws SVNException {
            if (myHistory == null) {
                return;
            }
            FSNodeHistory tempHist = myHistory.fsHistoryPrev(strict ? false : true, myFSFS);
            if (tempHist == null) {
                myHistory = null;
                return;
            }
            
            myHistory = tempHist;

            if (myHistory.getHistoryEntry().getRevision() < start) {
                myHistory = null;
                return;
            }
        }

        public boolean checkHistory(long currentRev, boolean strict, long start) throws SVNException {
            if (myHistory == null) {
                return false;
            }

            if (getHistoryRevision() < currentRev) {
                return false;
            }

            pickUpNextHistory(strict, start);
            return true;
        }
    }

    public int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        assertValidRevision(pegRevision);
        for (int i = 0; i < revisions.length; i++) {
            assertValidRevision(revisions[i]);
        }
        try {
            openRepository();
            path = getRepositoryPath(path);
            ArrayList locationEntries = new ArrayList(0);
            long[] locationRevs = new long[revisions.length];
            long revision;
            Arrays.sort(revisions);

            for (int i = 0; i < revisions.length; ++i) {
                locationRevs[i] = revisions[revisions.length - (i + 1)];
            }

            int count = 0;
            boolean isAncestor = false;
            
            for (count = 0; count < locationRevs.length && locationRevs[count] > pegRevision; ++count) {
                isAncestor = FSNodeHistory.checkAncestryOfPegPath(path, pegRevision, locationRevs[count], myFSFS);
                if (isAncestor) {
                    break;
                }
            }
            
            if (count >= locationRevs.length) {
                return 0;
            }
            revision = isAncestor ? locationRevs[count] : pegRevision;

            FSRevisionRoot root = null;
            while (count < revisions.length) {
                root = myFSFS.createRevisionRoot(revision);
                FSClosestCopy tempClCopy = closestCopy(root, path);
                if (tempClCopy == null) {
                    break;
                }
                FSRevisionRoot croot = tempClCopy.getRevisionRoot();
                if (croot == null) {
                    break;
                }
                String cpath = tempClCopy.getPath();

                long crev = croot.getRevision();
                while ((count < revisions.length) && (locationRevs[count] >= crev)) {
                    locationEntries.add(new SVNLocationEntry(locationRevs[count], path));
                    ++count;
                }

                FSRevisionNode copyfromNode = croot.getRevisionNode(cpath);
                String copyfromPath = copyfromNode.getCopyFromPath();
                long copyfromRevision = copyfromNode.getCopyFromRevision();

                while ((count < revisions.length) && locationRevs[count] > copyfromRevision) {
                    ++count;
                }
                
                String remainder = path.equals(cpath) ? "" : SVNPathUtil.pathIsChild(cpath, path);
                path = SVNPathUtil.concatToAbs(copyfromPath, remainder);
                revision = copyfromRevision;
            }
            
            root = myFSFS.createRevisionRoot(revision);
            FSRevisionNode curNode = root.getRevisionNode(path);

            while (count < revisions.length) {
                root = myFSFS.createRevisionRoot(locationRevs[count]);
                if (root.checkNodeKind(path) == SVNNodeKind.NONE) {
                    break;
                }
                FSRevisionNode currentNode = root.getRevisionNode(path);
                if (!curNode.getId().isRelated(currentNode.getId())) {
                    break;
                }
                locationEntries.add(new SVNLocationEntry(locationRevs[count], path));
                ++count;
            }
            
            for (count = 0; count < locationEntries.size(); count++) {
                if (handler != null) {
                    handler.handleLocationEntry((SVNLocationEntry) locationEntries.get(count));
                }
            }
            return count;
        } finally {
            closeRepository();
        }
    }

    public void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            makeReporterContext(revision, target, url, recursive, ignoreAncestry, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            makeReporterContext(targetRevision, target, url, recursive, ignoreAncestry, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            makeReporterContext(revision, target, null, recursive, false, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            makeReporterContext(revision, target, null, recursive, false, false, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    private void makeReporterContext(long targetRevision, String target, SVNURL switchURL, boolean recursive, boolean ignoreAncestry, boolean textDeltas, ISVNEditor editor)
            throws SVNException {
        target = target == null ? "" : target;

        if (!isValidRevision(targetRevision)) {
            targetRevision = myFSFS.getYoungestRevision();
        }
        
        String switchPath = null;
        
        if (switchURL != null) {
            SVNURL reposRootURL = getRepositoryRoot(false);

            if (switchURL.toDecodedString().indexOf(reposRootURL.toDecodedString()) == -1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "''{0}''\nis not the same repository as\n''{1}''", new Object[] {
                        switchURL, getRepositoryRoot(false)
                });
                SVNErrorManager.error(err);
            }
            
            switchPath = switchURL.toDecodedString().substring(reposRootURL.toDecodedString().length());
            
            if ("".equals(switchPath)) {
                switchPath = "/";
            }
        }
        
        String anchor = getRepositoryPath("");
        String fullTargetPath = switchPath != null ? switchPath : SVNPathUtil.concatToAbs(anchor, target);
        myReporterContext = new FSReporterContext(targetRevision, SVNFileUtil.createTempFile("report", ".tmp"), target, fullTargetPath, switchURL == null ? false : true, recursive, ignoreAncestry, textDeltas, editor);
    }

    public void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            makeReporterContext(revision, target, url, recursive, true, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public SVNDirEntry info(String path, long revision) throws SVNException {
        try {
            openRepository();
            path = getRepositoryPath(path);
            if (FSRepository.isInvalidRevision(revision)) {
                revision = myFSFS.getYoungestRevision();
            }
            FSRevisionRoot root = myFSFS.createRevisionRoot(revision);

            if (root.checkNodeKind(path) == SVNNodeKind.NONE) {
                return null;
            }
            
            FSRevisionNode revNode = root.getRevisionNode(path);
            String fullPath = getFullPath(path);
            String parentFullPath = "/".equals(path) ? fullPath : SVNPathUtil.removeTail(fullPath);
            SVNURL url = getLocation().setPath(parentFullPath, false);
            String name = SVNPathUtil.tail(path);
            FSEntry fsEntry = new FSEntry(revNode.getId(), revNode.getType(), name);
            SVNDirEntry entry = buildDirEntry(fsEntry, url, revNode, false);
            return entry;
        } finally {
            closeRepository();
        }
    }

    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, ISVNWorkspaceMediator mediator) throws SVNException {
        try {
            openRepository();
        } catch (SVNException svne) {
            closeRepository();
            throw svne;
        }
        FSCommitEditor commitEditor = new FSCommitEditor(getRepositoryPath(""), logMessage, getUserName(), locks, keepLocks, null, myFSFS, this);
        return commitEditor;
    }

    public SVNLock getLock(String path) throws SVNException {
        try {
            openRepository();
            path = getRepositoryPath(path);
            SVNLock lock = myFSFS.getLockHelper(path, false);
            return lock;
        } finally {
            closeRepository();
        }
    }

    public SVNLock[] getLocks(String path) throws SVNException {
        try {
            openRepository();
            path = getRepositoryPath(path);

            File digestFile = myFSFS.getDigestFileFromRepositoryPath(path);
            final ArrayList locks = new ArrayList();
            ISVNLockHandler handler = new ISVNLockHandler() {

                public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                    locks.add(lock);
                }

                public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                }
            };
            
            myFSFS.walkDigestFiles(digestFile, handler, false);
            
            return (SVNLock[]) locks.toArray(new SVNLock[locks.size()]);
        } finally {
            closeRepository();
        }
    }

    public void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
        try {
            openRepository();
            for (Iterator paths = pathsToRevisions.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                Long revision = (Long) pathsToRevisions.get(path);
                String reposPath = getRepositoryPath(path);
                long curRevision = (revision == null || isInvalidRevision(revision.longValue())) ? myFSFS.getYoungestRevision() : revision.longValue();
                SVNLock lock = null;
                SVNErrorMessage error = null;
                try {
                    lock = myFSFS.lockPath(reposPath, null, getUserName(), comment, null, curRevision, force);
                } catch (SVNException svne) {
                    error = svne.getErrorMessage();
                    if (!FSErrors.isLockError(error)) {
                        throw svne;
                    }
                }
                if (handler != null) {
                    handler.handleLock(reposPath, lock, error);
                }
            }
        } finally {
            closeRepository();
        }
    }

    public void unlock(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException {
        try {
            openRepository();
            for (Iterator paths = pathToTokens.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                String token = (String) pathToTokens.get(path);
                String reposPath = getRepositoryPath(path);
                SVNErrorMessage error = null;
                try {
                    myFSFS.unlockPath(reposPath, token, getUserName(), force);
                } catch (SVNException svne) {
                    error = svne.getErrorMessage();
                    if (!FSErrors.isUnlockError(error)) {
                        throw svne;
                    }
                }
                if (handler != null) {
                    handler.handleUnlock(reposPath, new SVNLock(reposPath, token, null, null, null, null), error);
                }
            }
        } finally {
            closeRepository();
        }
    }

    private void sendChanges(long revNum, boolean discoverChangedPaths, ISVNLogEntryHandler handler) throws SVNException {
        Map revisionProps = myFSFS.getRevisionProperties(revNum);
        Map changedPaths = null;
        String author = null;
        Date date = null;
        String message = null;
        
        if (revisionProps != null) {
            author = (String) revisionProps.get(SVNRevisionProperty.AUTHOR);
            String datestamp = (String) revisionProps.get(SVNRevisionProperty.DATE);
            message = (String) revisionProps.get(SVNRevisionProperty.LOG);
            date = datestamp != null ? SVNTimeUtil.parseDateString(datestamp) : null;
        }

        if (revNum > 0 && discoverChangedPaths) {
            FSRevisionRoot root = myFSFS.createRevisionRoot(revNum);
            changedPaths = root.detectChanged();
        }
        changedPaths = changedPaths == null ? new HashMap() : changedPaths;
        handler.handleLogEntry(new SVNLogEntry(changedPaths, revNum, author, date, message));
    }

    public void closeSession() throws SVNException {
    }

    public void setLocation(SVNURL url, boolean forceReconnect) throws SVNException {
        super.setLocation(url, forceReconnect);
    }

    public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        try {
            myFSFS.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, lockToken, revision, startEmpty);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }

    public void deletePath(String path) throws SVNException {
        try {
            myFSFS.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, null, FSRepository.SVN_INVALID_REVNUM, false);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        SVNURL reposRootURL = getRepositoryRoot(false);
        if (url.toDecodedString().indexOf(reposRootURL.toDecodedString()) == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "''{0}''\nis not the same repository as\n''{1}''", new Object[] {
                    url, reposRootURL
            });
            SVNErrorManager.error(err);
        }
        String reposLinkPath = url.toDecodedString().substring(reposRootURL.toDecodedString().length());
        if ("".equals(reposLinkPath)) {
            reposLinkPath = "/";
        }
        try {
            myFSFS.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, reposLinkPath, lockToken, revision, startEmpty);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }

    public void finishReport() throws SVNException {
        OutputStream tmpFile = myReporterContext.getReportFileForWriting();
        try {
            tmpFile.write('-');
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        SVNFileUtil.closeFile(myReporterContext.getReportFileForWriting());

        PathInfo info = null;
        try {
            info = myReporterContext.getFirstPathInfo();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        if (info == null || !info.getPath().equals(myReporterContext.getReportTarget()) || info.getLinkPath() != null || isInvalidRevision(info.getRevision())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_REVISION_REPORT, "Invalid report for top level of working copy");
            SVNErrorManager.error(err);
        }

        long sourceRevision = info.getRevision();
        PathInfo lookahead = null;
        
        try {
            lookahead = myReporterContext.getNextPathInfo();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        if (lookahead != null && lookahead.getPath().equals(myReporterContext.getReportTarget())) {
            if ("".equals(myReporterContext.getReportTarget())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_REVISION_REPORT, "Two top-level reports with no target");
                SVNErrorManager.error(err);
            }
            info = lookahead;
            try {
                myReporterContext.getNextPathInfo();
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            }
        }

        myReporterContext.getEditor().targetRevision(myReporterContext.getTargetRevision());

        String fullTargetPath = myReporterContext.getReportTargetPath();
        String fullSourcePath = SVNPathUtil.concatToAbs(getRepositoryPath(""), myReporterContext.getReportTarget());
        FSEntry targetEntry = fakeDirEntry(fullTargetPath, myReporterContext.getTargetRoot());
        FSRevisionRoot srcRoot = myReporterContext.getSourceRoot(sourceRevision);
        FSEntry sourceEntry = fakeDirEntry(fullSourcePath, srcRoot);

        if (isValidRevision(info.getRevision()) && info.getLinkPath() == null && sourceEntry == null) {
            fullSourcePath = null;
        }

        if ("".equals(myReporterContext.getReportTarget()) && (sourceEntry == null || sourceEntry.getType() != SVNNodeKind.DIR || targetEntry == null || targetEntry.getType() != SVNNodeKind.DIR)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "Cannot replace a directory from within");
            SVNErrorManager.error(err);
        }
        myDeltaGenerator = new SVNDeltaGenerator();
        myDeltaCombiner = new SVNDeltaCombiner();
        try {
            myReporterContext.getEditor().openRoot(sourceRevision);
    
            if ("".equals(myReporterContext.getReportTarget())) {
                diffDirs(sourceRevision, fullSourcePath, fullTargetPath, "", info.isStartEmpty());
            } else {
                // update entry
                updateEntry(sourceRevision, fullSourcePath, sourceEntry, fullTargetPath, targetEntry, myReporterContext.getReportTarget(), info, true);
            }
    
            myReporterContext.getEditor().closeDir();
            myReporterContext.getEditor().closeEdit();
        } finally {
            myDeltaCombiner = null;
            myDeltaGenerator = null;
            disposeReporterContext();
        }
    }

    public void abortReport() throws SVNException {
        disposeReporterContext();
    }

    public boolean areContentsEqual(FSRevisionNode revNode1, FSRevisionNode revNode2) {
        return areRepresentationsEqual(revNode1, revNode2, false);
    }

    public boolean arePropertiesEqual(FSRevisionNode revNode1, FSRevisionNode revNode2) {
        return areRepresentationsEqual(revNode1, revNode2, true);
    }

    private boolean areRepresentationsEqual(FSRevisionNode revNode1, FSRevisionNode revNode2, boolean forProperties) {
        if(revNode1 == revNode2){
            return true;
        }else if(revNode1 == null || revNode2 == null){
            return false;
        }
        return FSRepresentation.compareRepresentations(forProperties ? revNode1.getPropsRepresentation() : revNode1.getTextRepresentation(), forProperties ? revNode2.getPropsRepresentation() : revNode2.getTextRepresentation());
    }

    private void diffDirs(long sourceRevision, String sourcePath, String targetPath, String editPath, boolean startEmpty) throws SVNException {
        diffProplists(sourceRevision, startEmpty == true ? null : sourcePath, editPath, targetPath, null, true);
        Map sourceEntries = null;
        if (sourcePath != null && !startEmpty) {
            FSRevisionRoot sourceRoot = myReporterContext.getSourceRoot(sourceRevision);
            FSRevisionNode sourceNode = sourceRoot.getRevisionNode(sourcePath);
            sourceEntries = sourceNode.getDirEntries(myFSFS);
        }
        FSRevisionNode targetNode = myReporterContext.getTargetRoot().getRevisionNode(targetPath);
        
        Map targetEntries = targetNode.getDirEntries(myFSFS);

        while (true) {
            Object[] nextInfo = fetchPathInfo(editPath);
            String entryName = (String) nextInfo[0];
            if (entryName == null) {
                break;
            }
            PathInfo pathInfo = (PathInfo) nextInfo[1];
            if (pathInfo != null && isInvalidRevision(pathInfo.getRevision())) {
                if (sourceEntries != null) {
                    sourceEntries.remove(entryName);
                }
                continue;
            }

            String entryEditPath = SVNPathUtil.append(editPath, entryName);
            String entryTargetPath = SVNPathUtil.concatToAbs(targetPath, entryName);
            FSEntry targetEntry = (FSEntry) targetEntries.get(entryName);
            String entrySourcePath = sourcePath != null ? SVNPathUtil.concatToAbs(sourcePath, entryName) : null;
            FSEntry sourceEntry = sourceEntries != null ? (FSEntry) sourceEntries.get(entryName) : null;
            updateEntry(sourceRevision, entrySourcePath, sourceEntry, entryTargetPath, targetEntry, entryEditPath, pathInfo, myReporterContext.isRecursive());
            targetEntries.remove(entryName);

            if (sourceEntries != null) {
                sourceEntries.remove(entryName);
            }
        }

        if (sourceEntries != null) {
            Object[] names = sourceEntries.keySet().toArray();
            for (int i = 0; i < names.length; i++) {
                FSEntry srcEntry = (FSEntry) sourceEntries.get(names[i]);
                if (targetEntries.get(srcEntry.getName()) == null) {
                    String entryEditPath = SVNPathUtil.append(editPath, srcEntry.getName());
                    if (myReporterContext.isRecursive() || srcEntry.getType() != SVNNodeKind.DIR) {
                        myReporterContext.getEditor().deleteEntry(entryEditPath, FSRepository.SVN_INVALID_REVNUM);
                    }
                }
            }
        }

        Object[] names = targetEntries.keySet().toArray();
        for (int i = 0; i < names.length; i++) {
            FSEntry tgtEntry = (FSEntry) targetEntries.get(names[i]);
            String entryEditPath = SVNPathUtil.append(editPath, tgtEntry.getName());
            String entryTargetPath = SVNPathUtil.concatToAbs(targetPath, tgtEntry.getName());
            FSEntry srcEntry = sourceEntries != null ? (FSEntry) sourceEntries.get(tgtEntry.getName()) : null;
            String entrySourcePath = srcEntry != null ? SVNPathUtil.concatToAbs(sourcePath, tgtEntry.getName()) : null;
            updateEntry(sourceRevision, entrySourcePath, srcEntry, entryTargetPath, tgtEntry, entryEditPath, null, myReporterContext.isRecursive());
        }
    }

    private void diffFiles(long sourceRevision, String sourcePath, String targetPath, String editPath, String lockToken) throws SVNException {
        diffProplists(sourceRevision, sourcePath, editPath, targetPath, lockToken, false);
        String sourceHexDigest = null;
        FSRevisionRoot sourceRoot = null;
        if (sourcePath != null) {
            sourceRoot = myReporterContext.getSourceRoot(sourceRevision);

            boolean changed = false;
            if (myReporterContext.isIgnoreAncestry()) {
                changed = checkFilesDifferent(sourceRoot, sourcePath, myReporterContext.getTargetRoot(), targetPath);
            } else {
                changed = areFileContentsChanged(sourceRoot, sourcePath, myReporterContext.getTargetRoot(), targetPath);
            }
            if (!changed) {
                return;
            }
            FSRevisionNode sourceNode = sourceRoot.getRevisionNode(sourcePath);
            sourceHexDigest = sourceNode.getFileChecksum();
        }

        myReporterContext.getEditor().applyTextDelta(editPath, sourceHexDigest);

        if (myReporterContext.isSendTextDeltas()) {
            InputStream sourceStream = null;
            InputStream targetStream = null;
            try {
                if (sourceRoot != null && sourcePath != null) {
                    sourceStream = sourceRoot.getFileStreamForPath(myDeltaCombiner, sourcePath);
                } else {
                    sourceStream = FSInputStream.createDeltaStream(myDeltaCombiner, (FSRevisionNode) null, myFSFS);
                }
                targetStream = myReporterContext.getTargetRoot().getFileStreamForPath(new SVNDeltaCombiner(), targetPath);
                myDeltaGenerator.sendDelta(editPath, sourceStream, 0, targetStream, myReporterContext.getEditor(), false);
            } finally {
                SVNFileUtil.closeFile(sourceStream);
                SVNFileUtil.closeFile(targetStream);
            }
        } else {
            myReporterContext.getEditor().textDeltaEnd(editPath);
        }
    }

    private boolean checkFilesDifferent(FSRoot root1, String path1, FSRoot root2, String path2) throws SVNException {
        boolean changed = areFileContentsChanged(root1, path1, root2, path2);
        if (!changed) {
            return false;
        }

        FSRevisionNode revNode1 = root1.getRevisionNode(path1);
        FSRevisionNode revNode2 = root2.getRevisionNode(path2);
        if (getFileLength(revNode1) != getFileLength(revNode2)) {
            return true;
        }

        if (!revNode1.getFileChecksum().equals(revNode2.getFileChecksum())) {
            return true;
        }

        InputStream file1IS = null;
        InputStream file2IS = null;
        try {
            file1IS = root1.getFileStreamForPath(myDeltaCombiner, path1);
            file2IS = root2.getFileStreamForPath(myDeltaCombiner, path2);

            int r1 = -1;
            int r2 = -1;
            while (true) {
                r1 = file1IS.read();
                r2 = file2IS.read();
                if (r1 != r2) {
                    return true;
                }
                if (r1 == -1) {// we've finished - files do not differ
                    break;
                }
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(file1IS);
            SVNFileUtil.closeFile(file2IS);
        }
        return false;
    }

    private long getFileLength(FSRevisionNode revNode) throws SVNException {
        if (revNode.getType() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to get length of a *non*-file node");
            SVNErrorManager.error(err);
        }
        return revNode.getTextRepresentation() != null ? revNode.getTextRepresentation().getExpandedSize() : 0;
    }

    private boolean areFileContentsChanged(FSRoot root1, String path1, FSRoot root2, String path2) throws SVNException {
        if (root1.checkNodeKind(path1) != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "''{0}'' is not a file", path1);
            SVNErrorManager.error(err);
        }
        if (root2.checkNodeKind(path2) != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "''{0}'' is not a file", path2);
            SVNErrorManager.error(err);
        }
        FSRevisionNode revNode1 = root1.getRevisionNode(path1);
        FSRevisionNode revNode2 = root2.getRevisionNode(path2);
        return !areContentsEqual(revNode1, revNode2);
    }

    private void updateEntry(long sourceRevision, String sourcePath, FSEntry sourceEntry, String targetPath, FSEntry targetEntry, String editPath, PathInfo pathInfo, boolean recursive)
            throws SVNException {
        if (pathInfo != null && pathInfo.getLinkPath() != null && !myReporterContext.isSwitch()) {
            targetPath = pathInfo.getLinkPath();
            targetEntry = fakeDirEntry(targetPath, myReporterContext.getTargetRoot());
        }
        
        if (pathInfo != null && isInvalidRevision(pathInfo.getRevision())) {
            sourcePath = null;
            sourceEntry = null;
        } else if (pathInfo != null && sourcePath != null) {
            sourcePath = pathInfo.getLinkPath() != null ? pathInfo.getLinkPath() : sourcePath;
            sourceRevision = pathInfo.getRevision();
            FSRevisionRoot srcRoot = myReporterContext.getSourceRoot(sourceRevision);
            sourceEntry = fakeDirEntry(sourcePath, srcRoot);
        }

        if (sourcePath != null && sourceEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Working copy path ''{0}'' does not exist in repository", editPath);
            SVNErrorManager.error(err);
        }
        
        if (!recursive && ((sourceEntry != null && sourceEntry.getType() == SVNNodeKind.DIR) || (targetEntry != null && targetEntry.getType() == SVNNodeKind.DIR))) {
            skipPathInfo(editPath);
            return;
        }
        boolean related = false;

        if (sourceEntry != null && targetEntry != null && sourceEntry.getType() == targetEntry.getType()) {
            int distance = sourceEntry.getId().compareTo(targetEntry.getId());
            if (distance == 0 && !PathInfo.isRelevant(myReporterContext.getCurrentPathInfo(), editPath) && (pathInfo == null || (!pathInfo.isStartEmpty() && pathInfo.getLockToken() == null))) {
                return;
            } else if (distance != -1 || myReporterContext.isIgnoreAncestry()) {
                related = true;
            }
        }

        if (sourceEntry != null && !related) {
            myReporterContext.getEditor().deleteEntry(editPath, FSRepository.SVN_INVALID_REVNUM);
            sourcePath = null;
        }

        if (targetEntry == null) {
            skipPathInfo(editPath);
            return;
        }
        
        if (targetEntry.getType() == SVNNodeKind.DIR) {
            if (related) {
                myReporterContext.getEditor().openDir(editPath, sourceRevision);
            } else {
                myReporterContext.getEditor().addDir(editPath, null, FSRepository.SVN_INVALID_REVNUM);
            }
            diffDirs(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.isStartEmpty() : false);
            myReporterContext.getEditor().closeDir();
        } else {
            if (related) {
                myReporterContext.getEditor().openFile(editPath, sourceRevision);
            } else {
                myReporterContext.getEditor().addFile(editPath, null, FSRepository.SVN_INVALID_REVNUM);
            }
            diffFiles(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.getLockToken() : null);
            FSRevisionNode targetNode = myReporterContext.getTargetRoot().getRevisionNode(targetPath);
            String targetHexDigest = targetNode.getFileChecksum();
            myReporterContext.getEditor().closeFile(editPath, targetHexDigest);
        }
    }

    private FSEntry fakeDirEntry(String reposPath, FSRevisionRoot root) throws SVNException {
        if (root.checkNodeKind(reposPath) == SVNNodeKind.NONE) {
            return null;
        }
        FSRevisionNode node = root.getRevisionNode(reposPath);
        FSEntry dirEntry = new FSEntry(node.getId(), node.getType(), SVNPathUtil.tail(node.getCreatedPath()));
        return dirEntry;
    }

    private void skipPathInfo(String prefix) throws SVNException {
        while (PathInfo.isRelevant(myReporterContext.getCurrentPathInfo(), prefix)) {
            try {
                myReporterContext.getNextPathInfo();
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err);
            }
        }

    }

    private Object[] fetchPathInfo(String prefix) throws SVNException {
        Object[] result = new Object[2];
        PathInfo pathInfo = myReporterContext.getCurrentPathInfo();
        if (!PathInfo.isRelevant(pathInfo, prefix)) {
            result[0] = null;
            result[1] = null;
        } else {
            String relPath = "".equals(prefix) ? pathInfo.getPath() : pathInfo.getPath().substring(prefix.length() + 1);
            if (relPath.indexOf('/') != -1) {
                result[0] = relPath.substring(0, relPath.indexOf('/'));
                result[1] = null;
            } else {
                result[0] = relPath;
                result[1] = pathInfo;
                try {
                    myReporterContext.getNextPathInfo();
                } catch (IOException ioe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                    SVNErrorManager.error(err);
                }
            }
        }
        return result;
    }

    private void diffProplists(long sourceRevision, String sourcePath, String editPath, String targetPath, String lockToken, boolean isDir) throws SVNException {
        FSRevisionNode targetNode = myReporterContext.getTargetRoot().getRevisionNode(targetPath);
        long createdRevision = targetNode.getId().getRevision();

        if (isValidRevision(createdRevision)) {
            Map entryProps = getMetaProps(createdRevision);
            changeProperty(editPath, SVNProperty.COMMITTED_REVISION, (String) entryProps.get(SVNProperty.COMMITTED_REVISION), isDir);
            String committedDate = (String) entryProps.get(SVNProperty.COMMITTED_DATE);

            if (committedDate != null || sourcePath != null) {
                changeProperty(editPath, SVNProperty.COMMITTED_DATE, committedDate, isDir);
            }

            String lastAuthor = (String) entryProps.get(SVNProperty.LAST_AUTHOR);
            
            if (lastAuthor != null || sourcePath != null) {
                changeProperty(editPath, SVNProperty.LAST_AUTHOR, lastAuthor, isDir);
            }

            String uuid = (String) entryProps.get(SVNProperty.UUID);
            
            if (uuid != null || sourcePath != null) {
                changeProperty(editPath, SVNProperty.UUID, uuid, isDir);
            }
        }

        if (lockToken != null) {
            SVNLock lock = myFSFS.getLockHelper(targetPath, false);
            if (lock == null || !lockToken.equals(lock.getID())) {
                changeProperty(editPath, SVNProperty.LOCK_TOKEN, null, isDir);
            }
        }

        Map sourceProps = null;
        if (sourcePath != null) {
            FSRevisionRoot sourceRoot = myReporterContext.getSourceRoot(sourceRevision);
            FSRevisionNode sourceNode = sourceRoot.getRevisionNode(sourcePath);
            boolean propsChanged = !arePropertiesEqual(sourceNode, targetNode);
            if (!propsChanged) {
                return;
            }
            sourceProps = sourceNode.getProperties(myFSFS);
        } else {
            sourceProps = new HashMap();
        }

        Map targetProps = targetNode.getProperties(myFSFS);
        Map propsDiffs = getPropsDiffs(sourceProps, targetProps);
        Object[] names = propsDiffs.keySet().toArray();
        for (int i = 0; i < names.length; i++) {
            String propName = (String) names[i];
            changeProperty(editPath, propName, (String) propsDiffs.get(propName), isDir);
        }
    }

    public Map getPropsDiffs(Map sourceProps, Map targetProps){
        Map result = new HashMap();
        
        if(sourceProps == null){
            sourceProps = Collections.EMPTY_MAP;
        }
        
        if(targetProps == null){
            targetProps = Collections.EMPTY_MAP;
        }

        for(Iterator names = sourceProps.keySet().iterator(); names.hasNext();){
            String propName = (String)names.next();
            String srcPropVal = (String)sourceProps.get(propName);
            String targetPropVal = (String)targetProps.get(propName);

            if(targetPropVal == null){
                result.put(propName, null);
            }else if(!targetPropVal.equals(srcPropVal)){
                result.put(propName, targetPropVal);
            }
        }

        for(Iterator names = targetProps.keySet().iterator(); names.hasNext();){
            String propName = (String)names.next();
            String targetPropVal = (String)targetProps.get(propName);
            if(sourceProps.get(propName) == null){
                result.put(propName, targetPropVal);
            }
        }        

        return result;
    }

    private void changeProperty(String path, String name, String value, boolean isDir) throws SVNException {
        if (isDir) {
            myReporterContext.getEditor().changeDirProperty(name, value);
        } else {
            myReporterContext.getEditor().changeFileProperty(path, name, value);
        }
    }

    private void disposeReporterContext() {
        if (myReporterContext != null) {
            myReporterContext.disposeContext();
            myReporterContext = null;
        }
    }

    public static boolean isInvalidRevision(long revision) {
        return SVNRepository.isInvalidRevision(revision);
    }

    public static boolean isValidRevision(long revision) {
        return SVNRepository.isValidRevision(revision);
    }

    private class FSReporterContext {

        private File myReportFile;
        private String myTarget;
        private OutputStream myReportOS;
        private FSFile myReportIS;
        private ISVNEditor myEditor;
        private long myTargetRevision;
        private boolean isRecursive;
        private PathInfo myCurrentPathInfo;
        private boolean ignoreAncestry;
        private boolean sendTextDeltas;
        private String myTargetPath;
        private boolean isSwitch;
        private FSRevisionRoot myTargetRoot;
        private LinkedList myRootsCache;

        public FSReporterContext(long revision, File tmpFile, String target, String targetPath, boolean isSwitch, boolean recursive, boolean ignoreAncestry, boolean textDeltas, ISVNEditor editor) {
            myTargetRevision = revision;
            myReportFile = tmpFile;
            myTarget = target;
            myEditor = editor;
            isRecursive = recursive;
            this.ignoreAncestry = ignoreAncestry;
            sendTextDeltas = textDeltas;
            myTargetPath = targetPath;
            this.isSwitch = isSwitch;
        }

        public OutputStream getReportFileForWriting() throws SVNException {
            if (myReportOS == null) {
                myReportOS = SVNFileUtil.openFileForWriting(myReportFile);
            }
            return myReportOS;
        }

        public boolean isIgnoreAncestry() {
            return ignoreAncestry;
        }

        public boolean isSwitch() {
            return isSwitch;
        }

        public boolean isSendTextDeltas() {
            return sendTextDeltas;
        }

        public String getReportTarget() {
            return myTarget;
        }

        public String getReportTargetPath() {
            return myTargetPath;
        }

        public void disposeContext() {
            SVNFileUtil.closeFile(myReportOS);
            myReportIS.close();
            SVNFileUtil.deleteFile(myReportFile);
        }

        public ISVNEditor getEditor() {
            return myEditor;
        }

        public boolean isRecursive() {
            return isRecursive;
        }

        public long getTargetRevision() {
            return myTargetRevision;
        }

        public PathInfo getFirstPathInfo() throws IOException{
            SVNFileUtil.closeFile(myReportOS);
            myReportIS = new FSFile(myReportFile);
            myCurrentPathInfo = myReportIS.readPathInfoFromReportFile();
            return myCurrentPathInfo;
        }

        public PathInfo getNextPathInfo() throws IOException {
            myCurrentPathInfo = myReportIS.readPathInfoFromReportFile();
            return myCurrentPathInfo;
        }

        public PathInfo getCurrentPathInfo() {
            return myCurrentPathInfo;
        }

        public FSRevisionRoot getTargetRoot(){
            if (myTargetRoot == null) {
                myTargetRoot = myFSFS.createRevisionRoot(myTargetRevision);
            }
            return myTargetRoot;
        }
        
        private LinkedList getRootsCache(){
            if(myRootsCache == null){
                myRootsCache = new LinkedList();
            }
            return myRootsCache;
        }
        
        public FSRevisionRoot getSourceRoot(long revision){
            LinkedList cache = getRootsCache();
            FSRevisionRoot root = null;     
            int i = 0;
            
            for(;i < cache.size() && i < 10; i++){
                root = (FSRevisionRoot)myRootsCache.get(i);
                if(root.getRevision() == revision){
                    if(i != 0){
                        myRootsCache.remove(i);
                        myRootsCache.addFirst(root);
                    }
                    break;
                }
                root = null;
            }
            
            if(root == null){
                if(i == 10){
                    myRootsCache.removeLast();
                }
                root = myFSFS.createRevisionRoot(revision);
                myRootsCache.addFirst(root);
            }
            
            return root;
        }
    }

    private FSClosestCopy closestCopy(FSRevisionRoot root, String path) throws SVNException {
        FSParentPath parentPath = root.openPath(path, true, true);

        SVNLocationEntry copyDstEntry = FSNodeHistory.findYoungestCopyroot(myReposRootDir, parentPath);
        
        if (copyDstEntry == null || copyDstEntry.getRevision() == 0) {
            return null;
        }

        FSRevisionRoot copyDstRoot = myFSFS.createRevisionRoot(copyDstEntry.getRevision());
        if (copyDstRoot.checkNodeKind(path) == SVNNodeKind.NONE) {
            return null;
        }
        
        FSRevisionNode curRev = copyDstRoot.getRevisionNode(path);
        if (!parentPath.getRevNode().getId().isRelated(curRev.getId())) {
            return null;
        }

        long createdRev = parentPath.getRevNode().getId().getRevision();
        if (createdRev == copyDstEntry.getRevision()) {
            if (parentPath.getRevNode().getPredecessorId() == null) {
                return null;
            }
        }

        return new FSClosestCopy(copyDstRoot, copyDstEntry.getPath());
    }

    private String getUserName() throws SVNException {
        if (getLocation().getUserInfo() != null && getLocation().getUserInfo().trim().length() > 0) {
            return getLocation().getUserInfo();
        }
        if (getAuthenticationManager() != null) {
            try {
                String realm = getRepositoryUUID(true);
                ISVNAuthenticationManager authManager = getAuthenticationManager();
                SVNAuthentication auth = authManager.getFirstAuthentication(ISVNAuthenticationManager.USERNAME, realm, getLocation());

                while (auth != null) {
                    if (auth.getUserName() != null && !"".equals(auth.getUserName().trim())) {
                        authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.USERNAME, realm, null, auth);
                        return auth.getUserName();
                    }
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Empty user name is not allowed");
                    authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.USERNAME, realm, err, auth);
                    auth = authManager.getNextAuthentication(ISVNAuthenticationManager.USERNAME, realm, getLocation());
                }
                // auth manager returned null - that is cancellation.
                SVNErrorManager.cancel("Authentication cancelled");
            } catch (SVNCancelException e) {
                throw e;
            } catch (SVNAuthenticationException e) {
                // no more credentials, use system user name.
            } catch (SVNException e) {
                // generic error.
                throw e;
            }
        }
        return System.getProperty("user.name");
    }
}