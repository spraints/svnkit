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
package org.tmatesoft.svn.examples.wc;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

/*
 * This class is an implementation of ISVNEventHandler intended for  processing   
 * events generated by do*() methods of an SVNCommitClient object. An  instance  
 * of this handler will be provided to  an  SVNCommitClient. When calling,  for 
 * example,  SVNCommitClient.doCommit(..)  on  a  WC  path,  this  method  will 
 * generate an event for each 'adding'/'deleting'/'sending'/.. action  it  will 
 * perform upon every path being committed. And this event is passed to 
 * 
 * ISVNEventHandler.handleEvent(SVNEvent event,  double progress) 
 * 
 * to notify the handler.  The  event  contains detailed  information about the 
 * path, action performed upon the path and some other. 
 */
public class CommitEventHandler implements ISVNEventHandler {
    /*
     * progress  is  currently  reserved  for future purposes and now is always
     * ISVNEventHandler.UNKNOWN  
     */
    public void handleEvent(SVNEvent event, double progress) {
        /*
         * Gets the current action. An action is represented by SVNEventAction.
         * In case of a commit  an  action  can  be  determined  via  comparing 
         * SVNEvent.getAction() with SVNEventAction.COMMIT_-like constants. 
         */
        SVNEventAction action = event.getAction();
        if (action == SVNEventAction.COMMIT_MODIFIED) {
            System.out.println("Sending   " + event.getFile());
        } else if (action == SVNEventAction.COMMIT_DELETED) {
            System.out.println("Deleting   " + event.getFile());
        } else if (action == SVNEventAction.COMMIT_REPLACED) {
            System.out.println("Replacing   " + event.getFile());
        } else if (action == SVNEventAction.COMMIT_DELTA_SENT) {
            System.out.println("Transmitting file data....");
        } else if (action == SVNEventAction.COMMIT_ADDED) {
            /*
             * Gets the MIME-type of the item.
             */
            String mimeType = event.getMimeType();
            if (SVNProperty.isBinaryMimeType(mimeType)) {
                /*
                 * If the item is a binary file
                 */
                System.out.println("Adding  (bin)  "
                        + event.getFile());
            } else {
                System.out.println("Adding         "
                        + event.getFile());
            }
        }

    }
    
    /*
     * Should be implemented to check if the current operation is cancelled. If 
     * it is, this method should throw an SVNCancelException. 
     */
    public void checkCancelled() throws SVNCancelException {
    }

}