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
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;

/**
 * The <b>SVNEvent</b> class is used to provide detailed information on 
 * an operation progress to the <b>ISVNEventHandler</b> (if any) registered 
 * for an <b>SVN</b>*<b>Client</b> object. Such events are generated by
 * an operation invoked by do*() method of an <b>SVN</b>*<b>Client</b> object
 * and passed to a developer's event handler for notification. Retrieving 
 * information out of an <b>SVNEvent</b> the developer can decide how it 
 * should be interpreted.
 * 
 * <p>
 * This is an example:<br />
 * implementing <b>ISVNEventHandler</b>
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNEventHandler;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNCancelException;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNEvent;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNEventAction;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatusType;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNNodeKind;
 * <span class="javakeyword">import</span> java.io.File;
 * ...
 * 
 * <span class="javakeyword">public class</span> MyCustomUpdateEventHandler <span class="javakeyword">implements</span> ISVNEventHandler {
 *     <span class="javakeyword">public void</span> handleEvent(SVNEvent event, <span class="javakeyword">double</span> progress) {
 *         <span class="javacomment">//get the action type</span>
 *         <span class="javakeyword">if</span>(event.getAction() == SVNEventAction.UPDATE_UPDATE){
 *             <span class="javacomment">//get the item's node kind</span>
 *             SVNNodeKind kind = even.getNodeKind();
 *             <span class="javacomment">//get the item's contents status</span>
 *             <span class="javakeyword">if</span>(event.getContentsStatus() == SVNStatusType.CHANGED &&
 *                kind == SVNNodeKind.FILE){
 *                 ...
 *             }
 *             ...
 *             <span class="javacomment">//get the item's properties status</span>
 *             <span class="javakeyword">if</span>(event.getPropertiesStatus() == SVNStatusType.MERGED){
 *                 ...
 *             }
 *             <span class="javacomment">//get the item's lock status</span>
 *             <span class="javakeyword">if</span>(event.getLockStatus() == SVNStatusType.LOCK_UNLOCKED){
 *                 ...
 *             }
 *             <span class="javacomment">//get the item's relative path</span>
 *             String path = event.getPath();
 *             <span class="javacomment">//or in a java.io.File representation</span>
 *             File fsEntry = event.getFile(); 
 *             
 *             <span class="javacomment">//get update revision</span>
 *             long revision = event.getRevision(); 
 *             ...
 *         }
 *         ...
 *     }
 *     
 *     <span class="javakeyword">public void</span> checkCancelled() <span class="javakeyword">throws</span> SVNCancelException{
 *         <span class="javakeyword">throw new</span> SVNCancelException(<span class="javastring">"cancelled!"</span>);
 *     }
 * }</pre><br />
 * then registering a handler:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNUpdateClient;
 * ...
 * 
 * SVNUpdateClient updateClient;
 * ...
 * updateClient.setEventHandler(<span class="javakeyword">new</span> MyCustomUpdateEventHandler());
 * ...</pre><br />
 * now when invoking an update operation:
 * <pre class="javacode">
 * updateClient.doUpdate(...);</pre><br />
 * the registered instance of the <b>ISVNEventHandler</b> implementation
 * will be dispatched progress events. 
 * </p>
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @see     ISVNEventHandler
 * @see     SVNStatusType
 * @see     SVNEventAction
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNEvent {

    private String myMimeType;
    private SVNErrorMessage myErrorMessage;
    private SVNEventAction myAction;
    private SVNNodeKind myNodeKind;
    private long myRevision;
    private SVNStatusType myContentsStatus;
    private SVNStatusType myPropertiesStatus;
    private SVNStatusType myLockStatus;
    private SVNLock myLock;
    private SVNAdminAreaInfo myAdminAreaInfo;
    private String myName;
    private String myPath;
    private File myRoot;
    private File myRootFile;
    private SVNEventAction myExpectedAction;
    
    /**
     * Constructs an <b>SVNEvent</b> object given
     * an error message for a filed operation. 
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNEvent</b> object. It's not intended for users (from an API point of view).
     * 
     * @param errorMessage the message describing the operation fault
     */
    public SVNEvent(SVNErrorMessage errorMessage) {
        myErrorMessage = errorMessage;
    }
    

    /**
     * Constructs an <b>SVNEvent</b> object.
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNEvent</b> object. It's not intended for users (from an API point of view).
     * 
     * @param info           admin info
     * @param adminArea      admin area the item belongs to
     * @param name           the item's name
     * @param action         the type of action the item is exposed to
     * @param expectedAction the action type that was expected 
     * @param kind           the item's node kind
     * @param revision       a revision number
     * @param mimetype       the item's MIME type
     * @param cstatus        the item's contents status
     * @param pstatus        the item's properties status
     * @param lstatus        the item's lock status
     * @param lock           the item's lock
     * @param error          an error message
     */
    public SVNEvent(SVNAdminAreaInfo info, SVNAdminArea adminArea, String name,
            SVNEventAction action, SVNEventAction expectedAction, SVNNodeKind kind, 
            long revision, String mimetype, SVNStatusType cstatus, SVNStatusType pstatus,
            SVNStatusType lstatus, SVNLock lock, SVNErrorMessage error) {
        myMimeType = mimetype;
        myErrorMessage = error;
        myExpectedAction = expectedAction != null ? expectedAction : action;
        myAction = action;
        myNodeKind = kind == null ? SVNNodeKind.UNKNOWN : kind;
        myRevision = revision;
        myContentsStatus = cstatus == null ? SVNStatusType.INAPPLICABLE : cstatus;
        myPropertiesStatus = pstatus == null ? SVNStatusType.INAPPLICABLE : pstatus;
        myLockStatus = lstatus == null ? SVNStatusType.INAPPLICABLE : lstatus;
        myLock = lock;
        myAdminAreaInfo = info;
        myRoot = adminArea != null ? adminArea.getRoot() : null;
        myName = name;
    }

    /**
     * Constructs an <b>SVNEvent</b> object.
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNEvent</b> object. It's not intended for users (from an API point of view).
     * 
     * @param info       admin info
     * @param adminArea  admin area the item belongs to
     * @param name       the item's name
     * @param action     the type of action the item is exposed to
     * @param kind       the item's node kind
     * @param revision   a revision number
     * @param mimetype   the item's MIME type
     * @param cstatus    the item's contents status
     * @param pstatus    the item's properties status
     * @param lstatus    the item's lock status
     * @param lock       the item's lock
     * @param error      an error message
     */
    public SVNEvent(SVNAdminAreaInfo info, SVNAdminArea adminArea, String name,
            SVNEventAction action, SVNNodeKind kind, long revision,
            String mimetype, SVNStatusType cstatus, SVNStatusType pstatus,
            SVNStatusType lstatus, SVNLock lock, SVNErrorMessage error) {
        this(info, adminArea, name, action, null, kind, revision, mimetype, cstatus, pstatus, lstatus, lock, error);
    }
    
    /**
     * Constructs an <b>SVNEvent</b> object filling it with informational 
     * details most of that would be retrieved and analized by an 
     * <b>ISVNEventHandler</b> implementation. 
     * 
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNEvent</b> object. It's not intended for users (from an API point of view).
     *
     * <p>
     * If <code>action</code> is {@link SVNEventAction#SKIP} (i.e. operation is skipped) 
     * then the expected action (that would have occurred if the operation hadn't been skipped) 
     * is provided in <code>expected</code>. 
     * 
     * @param rootFile   the item's root directory
     * @param file       the item's path itself
     * @param action     the type of action the item is exposed to
     * @param expected   the action that is expected to happen, but may
     *                   be skipped in real for some reason
     * @param kind       the item's node kind
     * @param revision   a revision number
     * @param mimetype   the item's MIME type
     * @param cstatus    the item's contents status
     * @param pstatus    the item's properties status
     * @param lstatus    the item's lock status
     * @param lock       the item's lock
     * @param error      an error message
     */
    public SVNEvent(File rootFile, File file, SVNEventAction action, SVNEventAction expected,
            SVNNodeKind kind, long revision, String mimetype,
            SVNStatusType cstatus, SVNStatusType pstatus,
            SVNStatusType lstatus, SVNLock lock, SVNErrorMessage error) {
        myMimeType = mimetype;
        myExpectedAction = expected != null ? expected : action;
        myErrorMessage = error;
        myAction = action;
        myNodeKind = kind == null ? SVNNodeKind.UNKNOWN : kind;
        myRevision = revision;
        myContentsStatus = cstatus == null ? SVNStatusType.INAPPLICABLE
                : cstatus;
        myPropertiesStatus = pstatus == null ? SVNStatusType.INAPPLICABLE
                : pstatus;
        myLockStatus = lstatus == null ? SVNStatusType.INAPPLICABLE : lstatus;
        myLock = lock;

        myRoot = file != null ? file.getParentFile() : null;
        myRootFile = rootFile;
        myName = file != null ? file.getName() : "";
    }
    
    /**
     * Constructs an <b>SVNEvent</b> object filling it with informational 
     * details most of that would be retrieved and analized by an 
     * <b>ISVNEventHandler</b> implementation. 
     * 
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNEvent</b> object. It's not intended for users (from an API point of view).
     *
     * @param rootFile   the item's root directory
     * @param file       the item's path itself
     * @param action     the type of action the item is exposed to
     * @param kind       the item's node kind
     * @param revision   a revision number
     * @param mimetype   the item's MIME type
     * @param cstatus    the item's contents status
     * @param pstatus    the item's properties status
     * @param lstatus    the item's lock status
     * @param lock       the item's lock
     * @param error      an error message
     */
    public SVNEvent(File rootFile, File file, SVNEventAction action,            
            SVNNodeKind kind, long revision, String mimetype,
            SVNStatusType cstatus, SVNStatusType pstatus,
            SVNStatusType lstatus, SVNLock lock, SVNErrorMessage error) {
        this(rootFile, file, action, null, kind, revision, mimetype, cstatus, pstatus, lstatus, lock, error);
    }
    
    /**
     * Gets the item's path relative to the Working Copy root directory.
     * 
     * @return a string representation of the item's path
     */
    public String getPath() {
        if (myPath != null) {
            return myPath;
        }
        if (myAdminAreaInfo == null && myRootFile == null) {
            return myName;
        }
        File file = getFile();
        File root = myAdminAreaInfo != null ? myAdminAreaInfo.getAnchor().getRoot() : myRootFile;
        String rootPath = root.getAbsolutePath().replace(File.separatorChar, '/');
        String filePath = file.getAbsolutePath().replace(File.separatorChar, '/');
        myPath = filePath.substring(rootPath.length());
        if (myPath.startsWith("/")) {
            myPath = myPath.substring(1);
        }
        return myPath;
    }
    
    /**
     * Gets a java.io.File representation of the item's path.
     * 
     * @return the item's path
     */
    public File getFile() {
        if (myRoot != null) {
            return ("".equals(myName) || ".".equals(myName)) ? myRoot : new File(myRoot, myName);
        } else if (myAdminAreaInfo != null && getPath() != null) {
            return new File(myAdminAreaInfo.getAnchor().getRoot(), getPath());
        }
        return null;
    }
    
    /**
     * Gets the type of an action performed upon the item. An action is 
     * one of predefined <b>SVNEventAction</b> constants that are specific for
     * each kind of operation, such as update actions, commit actions, etc. 
     * 
     * @return the current action 
     */
    public SVNEventAction getAction() {
        return myAction;
    }
    
    /**
     * Returns the expected action. It is always the same as
     * the action returned by {@link #getAction()} except those cases 
     * when {@link #getAction()} returns {@link SVNEventAction#SKIP} (i.e. 
     * when the expected operation is skipped).
     *  
     * @return the expected action
     */
    public SVNEventAction getExpectedAction() {
        return myExpectedAction;
    }
    
    /**
     * Gets the status type of either file or directory contents.
     * Use predefined <b>SVNStatusType</b> constants to examine the
     * item's status. For a directory contents are its entries.
     * 
     * @return the item's status type
     */
    public SVNStatusType getContentsStatus() {
        return myContentsStatus;
    }
    
    /**
     * Gets the error message that (if it's an error situation and 
     * therefore the string is not <span class="javakeyword">null</span>) 
     * points to some fault.  
     * 
     * @return  an error message (in case of an error occured) or 
     *          <span class="javakeyword">null</span> if everything
     *          is OK
     */
    public SVNErrorMessage getErrorMessage() {
        return myErrorMessage;
    }
    
    /**
     * Gets the file item's lock information (if any) represented by an 
     * <b>SVNLock</b> object.
     * 
     * @return the file item's lock info if the file is locked; otherwise 
     *         <span class="javakeyword">null</span> 
     */
    public SVNLock getLock() {
        return myLock;
    }
    
    /**
     * Gets the file item's lock status. The value of 
     * <b>SVNStatusType.<i>LOCK_INAPPLICABLE</i></b> means
     * the lock status is irrelevant during the current event action.
     *  
     * @return the lock status of the file item
     */
    public SVNStatusType getLockStatus() {
        return myLockStatus;
    }

    /**
     * Gets the MIME type of the item relying upon the special 
     * SVN's <i>'svn:mime-type'</i> property.
     * 
     * <p>
     * You can use {@link org.tmatesoft.svn.core.SVNProperty}'s metods to 
     * find out whether it's a text MIME type or a binary:
     * <pre class="javacode">
     * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNProperty;
     * ...
     * 
     * String mimeType = event.getMimeType();
     * <span class="javakeyword">if</span>(SVNProperty.isBinaryMimeType(mimeType)){
     *     <span class="javacomment">//your processing</span>
     * }</pre>
     * 
     * @return the item's MIME type as a string or 
     *         <span class="javakeyword">null</span> if the item has no
     *         <i>'svn:mime-type'</i> property set
     */
    public String getMimeType() {
        return myMimeType;
    }
    
    /**
     * Gets the node kind of the item characterizing it as an entry - 
     * whether it's a directory, file, etc. The value of 
     * <b>SVNNodeKind.<i>NONE</i></b> may mean the node kind is 
     * inapplicable diring the current event action. The value of 
     * <b>SVNNodeKind.<i>UNKNOWN</i></b> may mean deleted entries.
     *  
     * @return the item's node kind
     */
    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }
    
    /**
     * Gets the status type of the item's properties.
     * The value of <b>SVNStatusType.<i>INAPPLICABLE</i></b> may mean
     * the item has no versioned properties or that properties status is
     * irrelevant during the current event action.
     * 
     * @return the status type of the item's properties
     */
    public SVNStatusType getPropertiesStatus() {
        return myPropertiesStatus;
    }
    
    /**
     * Gets the revision number specific for the action context.
     * It may be whether an update revision or a committed one or
     * an inapplicable value when a revision number is irrelevant during
     * the event action.
     *  
     * @return a revision number
     */
    public long getRevision() {
        return myRevision;
    }
    
    /**
     * Sets the item's path relative to the Working Copy root.
     * 
     * @param path  the item's relative path
     */
    public void setPath(String path) {
        myPath = path;
    }
}
