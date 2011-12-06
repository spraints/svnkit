/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tigris.subversion.javahl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdmin {

    protected long cppAddr;
    private SVNClientImpl myDelegate;
    private SVNAdminClient mySVNAdminClient;

    /**
     * Filesystem in a Berkeley DB
     */
    public static final String BDB = "bdb";
    /**
     * Filesystem in the filesystem
     */
    public static final String FSFS = "fsfs";
    
    public SVNAdmin() {
        myDelegate = SVNClientImpl.newInstance();
    }

    
    public void dispose() {
        myDelegate.dispose();
        mySVNAdminClient = null;
    }

    /**
     * @return Version information about the underlying native libraries.
     */
    public Version getVersion()
    {
        return myDelegate.getVersion();
    }

    /**
     * create a subversion repository.
     * @param path                  the path where the repository will been 
     *                              created.
     * @param disableFsyncCommit    disable to fsync at the commit (BDB).
     * @param keepLog               keep the log files (BDB).
     * @param configPath            optional path for user configuration files.
     * @param fstype                the type of the filesystem (BDB or FSFS)
     * @throws ClientException  throw in case of problem
     */
    public void create(String path, boolean disableFsyncCommit, 
                              boolean keepLog, String configPath,
                              String fstype) throws ClientException {
        if (BDB.equalsIgnoreCase(fstype)) {
            notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
        }
        try {
            SVNRepositoryFactory.createLocalRepository(new File(path), false, false);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
        
    }

    /**
     * deltify the revisions in the repository
     * @param path              the path to the repository
     * @param start             start revision
     * @param end               end revision
     * @throws ClientException  throw in case of problem
     */
    public void deltify(String path, Revision start, Revision end) throws ClientException {        
        notImplementedYet();
    }
    
    /**
     * dump the data in a repository
     * @param path              the path to the repository
     * @param dataOut           the data will be outputed here
     * @param errorOut          the messages will be outputed here
     * @param start             the first revision to be dumped
     * @param end               the last revision to be dumped
     * @param incremental       the dump will be incremantal
     * @throws ClientException  throw in case of problem
     */
    public void dump(String path, final OutputInterface dataOut, OutputInterface errorOut, Revision start, Revision end, boolean incremental) throws ClientException {
        OutputStream os = createOutputStream(dataOut);
        try {
            getAdminClient().doDump(new File(path).getAbsoluteFile(), os, JavaHLObjectFactory.getSVNRevision(start), JavaHLObjectFactory.getSVNRevision(end), incremental, false);
        } catch (SVNException e) {
            try {
                errorOut.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
            } catch (IOException e1) {
                //
            }
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    /**
     * make a hot copy of the repository
     * @param path              the path to the source repository
     * @param targetPath        the path to the target repository
     * @param cleanLogs         clean the unused log files in the source
     *                          repository
     * @throws ClientException  throw in case of problem
     */
    public void hotcopy(String path, String targetPath, boolean cleanLogs) throws ClientException {
        notImplementedYet();
    }

    /**
     * list all logfiles (BDB) in use or not)
     * @param path              the path to the repository
     * @param receiver          interface to receive the logfile names
     * @throws ClientException  throw in case of problem
     */
    public void listDBLogs(String path, MessageReceiver receiver) throws ClientException {
        notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
    }

    /**
     * list unused logfiles
     * @param path              the path to the repository
     * @param receiver          interface to receive the logfile names
     * @throws ClientException  throw in case of problem
     */
    public void listUnusedDBLogs(String path, MessageReceiver receiver) throws ClientException {
        notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
    }

    /**
     * interface to receive the messages
     */
    public static interface MessageReceiver
    {
        /**
         * receive one message line
         * @param message   one line of message
         */
        public void receiveMessageLine(String message);
    }

    /**
     * load the data of a dump into a repository,
     * @param path              the path to the repository
     * @param dataInput         the data input source
     * @param messageOutput     the target for processing messages
     * @param ignoreUUID        ignore any UUID found in the input stream
     * @param forceUUID         set the repository UUID to any found in the
     *                          stream
     * @param relativePath      the directory in the repository, where the data
     *                          in put optional.
     * @throws ClientException  throw in case of problem
     */
    public void load(String path, InputInterface dataInput, OutputInterface messageOutput, boolean ignoreUUID, boolean forceUUID, String relativePath) throws ClientException {
        InputStream is = createInputStream(dataInput);
        try {
            SVNUUIDAction uuidAction = SVNUUIDAction.DEFAULT;
            if (ignoreUUID) {
                uuidAction = SVNUUIDAction.IGNORE_UUID;
            } else if (forceUUID) {
                uuidAction = SVNUUIDAction.FORCE_UUID;
            }
            getAdminClient().doLoad(new File(path).getAbsoluteFile(), is, false, false, uuidAction, relativePath);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    /**
     * list all open transactions in a repository
     * @param path              the path to the repository
     * @param receiver          receives one transaction name per call
     * @throws ClientException  throw in case of problem
     */
    public void lstxns(String path, final MessageReceiver receiver) throws ClientException {
        getAdminClient().setEventHandler(new ISVNAdminEventHandler() {
            public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                if (receiver != null && event.getTxnName() != null) {
                    receiver.receiveMessageLine(event.getTxnName());
                }
            }
            public void checkCancelled() throws SVNCancelException {
            }
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }
        });
        try {
            getAdminClient().doListTransactions(new File(path).getAbsoluteFile());
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }
    
    /**
     * recover the berkeley db of a repository, returns youngest revision
     * @param path              the path to the repository
     * @throws ClientException  throw in case of problem
     */
    public long recover(String path) throws ClientException {
        notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
        return -1;
    }

    /**
     * remove open transaction in a repository
     * @param path              the path to the repository
     * @param transactions      the transactions to be removed
     * @throws ClientException  throw in case of problem
     */
    public void rmtxns(String path, String [] transactions) throws ClientException {
        try {
            getAdminClient().doRemoveTransactions(new File(path).getAbsoluteFile(), transactions);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    /**
     * set the log message of a revision
     * @param path              the path to the repository
     * @param rev               the revision to be changed
     * @param message           the message to be set
     * @param bypassHooks       if to bypass all repository hooks
     * @throws ClientException  throw in case of problem
     */
    public void setLog(String path, Revision rev, String message, boolean bypassHooks) throws ClientException {
        try {
            SVNRepository repository = SVNRepositoryFactory.create(SVNURL.fromFile(new File(path).getAbsoluteFile()));
            ((FSRepository) repository).setRevisionPropertyValue(JavaHLObjectFactory.getSVNRevision(rev).getNumber(), SVNRevisionProperty.LOG, message, bypassHooks);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        } 
    }
    
    /**
     * verify the repository
     * @param path              the path to the repository
     * @param messageOut        the receiver of all messages
     * @param start             the first revision
     * @param end               the last revision
     * @throws ClientException  throw in case of problem
     */
    public void verify(String path,  OutputInterface messageOut,  Revision start, Revision end) throws ClientException {
        try {
            getAdminClient().doVerify(new File(path).getAbsoluteFile(), JavaHLObjectFactory.getSVNRevision(start), JavaHLObjectFactory.getSVNRevision(end));
        } catch (SVNException e) {
            try {
                messageOut.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
            } catch (IOException e1) {
                //
            }
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    /**
     * list all locks in the repository
     * @param path              the path to the repository
     * @throws ClientException  throw in case of problem
     * @since 1.2
     */ 
    public Lock[] lslocks(String path) throws ClientException {
        notImplementedYet();
        return new Lock[0];
    }

    /**
     * remove multiple locks from the repository
     * @param path              the path to the repository
     * @param locks             the name of the locked items
     * @throws ClientException  throw in case of problem
     * @since 1.2
     */
    public void rmlocks(String path, String [] locks) throws ClientException {
        notImplementedYet();
    }
    
    private void notImplementedYet() throws ClientException {
        notImplementedYet(null);
    }

    private void notImplementedYet(String message) throws ClientException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                message == null ? "Requested SVNAdmin functionality is not yet implemented" : message);
        JavaHLObjectFactory.throwException(new SVNException(err), myDelegate);
    }
    
    protected SVNAdminClient getAdminClient() {
        if (mySVNAdminClient == null) {
            mySVNAdminClient = new SVNAdminClient(SVNWCUtil.createDefaultAuthenticationManager(), SVNWCUtil.createDefaultOptions(true));
        }
        return mySVNAdminClient;
    }

    private static OutputStream createOutputStream(final OutputInterface dataOut) {
        if (dataOut == null) {
            return SVNFileUtil.DUMMY_OUT;
        }
        return new OutputStream() {
            public void write(int b) throws IOException {
                dataOut.write(new byte[] {(byte) (b & 0xFF)});
            }
            public void write(byte[] b) throws IOException {
                dataOut.write(b);
            }
            public void close() throws IOException {
                dataOut.close();
            }
            public void write(byte[] b, int off, int len) throws IOException {                
                byte[] copy = new byte[len];
                System.arraycopy(b, off, copy, 0, len);
                dataOut.write(copy);
            }
        };
    }

    private static InputStream createInputStream(final InputInterface dataIn) {
        if (dataIn == null) {
            return SVNFileUtil.DUMMY_IN;
        }
        return new InputStream() {

            public int read() throws IOException {
                byte[] b = new byte[1];
                int r = dataIn.read(b);
                if (r <= 0) {
                    return -1;
                }
                return b[0];
            }

            public void close() throws IOException {
                dataIn.close();
            }

            public int read(byte[] b, int off, int len) throws IOException {
                byte[] copy = new byte[len];
                int realLen = dataIn.read(copy);
                if (realLen <= 0) {
                    return realLen;
                }
                System.arraycopy(copy, 0, b, off, realLen);
                return realLen;
            }

            public int read(byte[] b) throws IOException {
                return dataIn.read(b);
            }
        };
    }
    
}