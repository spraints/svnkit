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
package org.tmatesoft.svn.core.wc;

/**
 * The <b>SVNEventAction</b> class is used to describe an action 
 * which generated an <b>SVNEvent</b> object. 
 * <p>
 * Each operation invoked by 
 * a do*() method of an <b>SVN</b>*<b>Client</b> class consists of 
 * several actions that can be considered as operation steps. For example,      
 * an update operation receives changes for files, adds new ones, deletes
 * another ones and so on. And for every such action (for every file
 * updated, deleted, added, etc.) the 
 * {@link SVNUpdateClient#doUpdate(File, SVNRevision, boolean) doUpdate()}
 * method generates an <b>SVNEvent</b> objects which contains information
 * on the type of this action that can be retrieved simply calling
 * the <b>SVNEvent</b>'s {@link SVNEvent#getAction() getAction()} method:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNEvent;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNEventAction;
 * ...
 *   
 *   SVNEventAction action = event.getAction();
 *   <span class="javacomment">//parse the action according to the type of</span> 
 *   <span class="javacomment">//operation and your needs</span>
 *   <span class="javakeyword">if</span> (action == SVNEventAction.UPDATE_UPDATE){
 *       ...
 *   }
 *   ...</pre>
 * <p>
 * <b>SVNEventAction</b> is just a set of predefined constant fields of
 * the same type. Each constant is applicable only to a certain type
 * of operation - for example those constants that names start with the 
 * <i>UPDATE_</i> prefix are relevant only for update related operations
 * (update, checkout, switch, etc.). 
 *  
 * @version 1.1
 * @author  TMate Software Ltd.
 * @see     SVNEvent
 * @see     ISVNEventHandler
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
 */
public class SVNEventAction {

    private int myID;

    private SVNEventAction(int id) {
        myID = id;
    }

    /**
     * Returns this object's identifier.
     * Each constant field of the <b>SVNEventAction</b> class is also an 
     * <b>SVNEventAction</b> object with its own id. 
     * 
     * @return id of this object 
     */
    public int getID() {
        return myID;
    }

    /**
     * Returns a string representation of this object. 
     * As a matter of fact this is a string representation of this 
     * object's id.
     * 
     * @return a string representing this object
     */
    public String toString() {
        return Integer.toString(myID);
    }
    
    /**
     * Reserved for future purposes.
     */
    public static final SVNEventAction PROGRESS = new SVNEventAction(-1);
    
    /**
     * Denotes that a new item is scheduled for addition. Generated
     * by the {@link SVNWCClient#doAdd(File, boolean, boolean, boolean, boolean)
     * doAdd()} method. 
     */
    public static final SVNEventAction ADD = new SVNEventAction(0);

    /**
     * Denotes that the item is copied with history. 
     * 
     * @see SVNCopyClient
     */
    public static final SVNEventAction COPY = new SVNEventAction(1);

    /**
     * Denotes that the item is scheduled for deletion. Generated
     * by the {@link SVNWCClient#doDelete(File, boolean, boolean) doDelete()} 
     * method. 
     */
    public static final SVNEventAction DELETE = new SVNEventAction(2);
    
    /**
     * Denotes that the deleted item is restored (prior to be updated).
     */
    public static final SVNEventAction RESTORE = new SVNEventAction(3);
    
    /**
     * Denotes that all local changes to the item were reverted. Generated by 
     * the {@link SVNWCClient#doRevert(File, boolean)} method.
     */
    public static final SVNEventAction REVERT = new SVNEventAction(4);
    
    /**
     * Denotes that a revert operation failed. Generated by the
     * {@link SVNWCClient#doRevert(File, boolean)} method.
     */
    public static final SVNEventAction FAILED_REVERT = new SVNEventAction(5);
    
    /**
     * Denotes that the conflict on the item is resolved (the item is
     * marked resolved). Such an event is generated by the
     * {@link SVNWCClient#doResolve(File, boolean) doResolve()} method.
     */
    public static final SVNEventAction RESOLVED = new SVNEventAction(6);
    
    /**
     * Denotes that the operation is skipped due to errors (inability to 
     * be performed, etc.).
     */
    public static final SVNEventAction SKIP = new SVNEventAction(7);
    
    /**
     * In an update operation denotes that the item is deleted from
     * the Working Copy (as it was deleted in the repository).
     */
    public static final SVNEventAction UPDATE_DELETE = new SVNEventAction(8);
    
    /**
     * In an update operation denotes that the item is added to
     * the Working Copy (as it was added in the repository).
     */
    public static final SVNEventAction UPDATE_ADD = new SVNEventAction(9);
    
    /**
     * In an update operation denotes that the item is modified (there 
     * are changes received from the repository).
     * 
     */
    public static final SVNEventAction UPDATE_UPDATE = new SVNEventAction(10);
    
    /**
     * In an update operation denotes that the item is not modified, but its children are.
     * 
     */
    public static final SVNEventAction UPDATE_NONE = new SVNEventAction(10);

    /**
     * In an update operation denotes that the operation itself is completed
     * (for instance, in a console client can be used to print out the
     * revision updated to).
     */
    public static final SVNEventAction UPDATE_COMPLETED = new SVNEventAction(11);
    
    /**
     * In an update operation denotes that the item being updated is 
     * external.
     */
    public static final SVNEventAction UPDATE_EXTERNAL = new SVNEventAction(12);
    
    /**
     * In a remote status operation denotes that the operation itself is completed - 
     * used to get the latest repository revision against which the status was
     * invoked.  
     */
    public static final SVNEventAction STATUS_COMPLETED = new SVNEventAction(13);
    
    /**
     * In a status operation denotes that the status is performed on an 
     * external item. To find out the item's current status use 
     * {@link SVNEvent#getContentsStatus() getContentsStatus()}, 
     * {@link SVNEvent#getPropertiesStatus() getPropertiesStatus()}.
     * The {@link SVNStatusType#STATUS_EXTERNAL} constant says only that the 
     * item belongs to externals definitions. 
     * 
     */
    public static final SVNEventAction STATUS_EXTERNAL = new SVNEventAction(14);
    
    /**
     * In a commit operation denotes sending the item's modifications to the
     * repository.
     */
    public static final SVNEventAction COMMIT_MODIFIED = new SVNEventAction(15);
    
    /**
     * In a commit operation denotes adding a new item to the repository.
     */
    public static final SVNEventAction COMMIT_ADDED = new SVNEventAction(16);
    
    /**
     * In a commit operation denotes deleting the item from the
     * repository.
     */
    public static final SVNEventAction COMMIT_DELETED = new SVNEventAction(17);
    
    /**
     * In a commit operation denotes replacing (one item was deleted while 
     * another one with the same name was added) the item in the repository. 
     */
    public static final SVNEventAction COMMIT_REPLACED = new SVNEventAction(18);

    /**
     * In a commit operation denotes the final stage of the operation - 
     * sending all file data and finalizing the commit.
     */
    public static final SVNEventAction COMMIT_DELTA_SENT = new SVNEventAction(19);

    /**
     * In a commit operation denotes that the operation itself is completed
     * (for instance, in a console client can be used to print out the
     * commited revsion).
     */
    public static final SVNEventAction COMMIT_COMPLETED = new SVNEventAction(32);

    /**
     * Denotes that file blaming is started.
     */
    public static final SVNEventAction ANNOTATE = new SVNEventAction(20);
    
    /**
     * Denotes that the file item is locked as a result of a locking 
     * operation. Generated by a <b>doLock()</b> method of {@link SVNWCClient}.
     */
    public static final SVNEventAction LOCKED = new SVNEventAction(21);

    /**
     * Denotes that the file item is unlocked as a result of an unlocking 
     * operation. Generated by a <b>doUnlock()</b> method of {@link SVNWCClient}.
     */
    public static final SVNEventAction UNLOCKED = new SVNEventAction(22);

    /**
     * Denotes that locking a file item failed. Generated by a <b>doLock()</b> 
     * method of {@link SVNWCClient}.
     */
    public static final SVNEventAction LOCK_FAILED = new SVNEventAction(23);

    /**
     * Denotes that unlocking a file item failed. Generated by a <b>doUnlock()</b> 
     * method of {@link SVNWCClient}.
     */
    public static final SVNEventAction UNLOCK_FAILED = new SVNEventAction(24);
}
