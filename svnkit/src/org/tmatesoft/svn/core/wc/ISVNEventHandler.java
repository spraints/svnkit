/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNException;

/**
 * The <b>ISVNEventHandler</b> interface should be implemented in
 * order to be further provided to an <b>SVN</b>*<b>Client</b>
 * object as a handler of a sequence of events generated by 
 * <b>SVN</b>*<b>Client</b>'s do*() methods.
 * 
 * <p>
 * This is a way how a custom event handler can be registered:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNOptions;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNWCUtil;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNClientManager;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNEventHandler;
 * ...
 * 
 * ISVNOptions options = SVNWCUtil.createDefaultOptions(<span class="javakeyword">true</span>);
 * String authName = <span class="javastring">"myName"</span>;
 * String authPassword = <span class="javastring">"myPassword"</span>;
 * SVNClientManager clientManager = SVNClientManager.newInstance(options, authName, authPassword);
 * clientManager.getCommitClient().setEventHandler(<span class="javakeyword">new</span> ISVNEventHandler(){
 *     <span class="javakeyword">public void</span> handleEvent(SVNEvent event, <span class="javakeyword">double</span> progress){
 *         <span class="javacomment">//handle event here</span>
 *     }
 *     
 *     <span class="javakeyword">public void</span> checkCancelled() <span class="javakeyword">throws</span> SVNCancelException {
 *         <span class="javacomment">//handle cancel of the operation - throw SVNCancelException</span>  
 *     }
 * });</pre>
 * <br />
 * or like this:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNCommitClient;
 * ...
 * 
 * ISVNOptions options = SVNWCUtil.createDefaultOptions(<span class="javakeyword">true</span>);
 * SVNCommitClient commitClient = new SVNCommitClient(null, options);
 * commitClient.setEventHandler(<span class="javakeyword">new</span> ISVNEventHandler(){
 * ...
 * });</pre>
 * </p>
 * <p>
 * All calls to <b>handleEvent()</b> and <b>checkCancelled()</b> methods 
 * are synchronous - that is the caller is blocked till a method 
 * finishes.
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @see     SVNEvent
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 *
 */
public interface ISVNEventHandler extends ISVNCanceller {
    /**
     * Constant that is currently the value of the <code>progress</code>
     * parameter (in {@link #handleEvent(SVNEvent, double) handleEvnt()}) 
     */
    public static final double UNKNOWN = -1;
    
    /**
     * Handles the current event. 
     * 
     * <p>
     * Generally all operations represented 
     * by do*() methods of <b>SVN</b>*<b>Client</b> objects are
     * followed by generating a sequence of events that are passed to the 
     * registered <b>ISVNEventHandler</b> object for custom processing.
     * For example, during an update operation each local item being modified
     * is signaled about by dispatching a specific for this item <b>SVNEvent</b>
     * object to this method where this event can be scrutinized and handled
     * in a desired way. 
     * 
     * @param event     the current event that keeps detailed information on
     *                  the type of action occured and other attributes like path,
     *                  status, etc. 
     *                   
     * @param progress  currently reserved for future use; now it's value
     *                  is always set to {@link #UNKNOWN}
     * @throws SVNException
     */
    public void handleEvent(SVNEvent event, double progress) throws SVNException;
    
}
