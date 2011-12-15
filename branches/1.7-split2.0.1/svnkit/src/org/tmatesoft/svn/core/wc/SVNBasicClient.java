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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.core.internal.wc16.*;
import org.tmatesoft.svn.core.internal.wc17.*;


/**
 * The <b>SVNBasicClient</b> is the base class of all 
 * <b>SVN</b>*<b>Client</b> classes that provides a common interface
 * and realization.
 * 
 * <p>
 * All of <b>SVN</b>*<b>Client</b> classes use inherited methods of
 * <b>SVNBasicClient</b> to access Working Copies metadata, to create 
 * a driver object to access a repository if it's necessary, etc. In addition
 * <b>SVNBasicClient</b> provides some interface methods  - such as those
 * that allow you to set your {@link ISVNEventHandler event handler}, 
 * obtain run-time configuration options, and others. 
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNBasicClient {

    private SVNBasicDelegate delegate16;
    private SVNBasicDelegate delegate17;

    protected SVNBasicClient(SVNBasicDelegate delegate16, SVNBasicDelegate delegate17) {
        this.delegate16 = delegate16;
        this.delegate17 = delegate17;
        setPathListHandler(null);
        setDebugLog(null);
        setEventPathPrefix(null);
        setOptions(null);
        setEventHandler(null);
    }

    protected SVNBasicDelegate getDelegate17() {
        return this.delegate17;
    }

    protected SVNBasicDelegate getDelegate16() {
        return this.delegate16;
    }

    /**
     * Gets run-time configuration options used by this object.
     * 
     * @return the run-time options being in use
     */
    public ISVNOptions getOptions() {
        return getDelegate16().getOptions();
    }
    
    /**
     * Sets run-time global configuration options to this object.
     * 
     * @param options  the run-time configuration options 
     */
    public void setOptions(ISVNOptions options) {
        if (options == null) {
            options = SVNWCUtil.createDefaultOptions(true);
        }
        getDelegate16().setOptions(options);
        getDelegate17().setOptions(options);
    }
    
    /**
     * Sets externals definitions to be ignored or not during
     * operations.
     * 
     * <p>
     * For example, if external definitions are set to be ignored
     * then a checkout operation won't fetch them into a Working Copy.
     * 
     * @param ignore  <span class="javakeyword">true</span> to ignore
     *                externals definitions, <span class="javakeyword">false</span> - 
     *                not to
     * @see           #isIgnoreExternals()
     */
    public void setIgnoreExternals(boolean ignore) {
        getDelegate16().setIgnoreExternals(ignore);
        getDelegate17().setIgnoreExternals(ignore);
    }
    
    /**
     * Determines if externals definitions are ignored.
     * 
     * @return <span class="javakeyword">true</span> if ignored,
     *         otherwise <span class="javakeyword">false</span>
     * @see    #setIgnoreExternals(boolean)
     */
    public boolean isIgnoreExternals() {
        return getDelegate16().isIgnoreExternals();
    }
    /**
     * Sets (or unsets) all conflicted working files to be untouched
     * by update and merge operations.
     * 
     * <p>
     * By default when a file receives changes from the repository 
     * that are in conflict with local edits, an update operation places
     * two sections for each conflicting snatch into the working file 
     * one of which is a user's local edit and the second is the one just 
     * received from the repository. Like this:
     * <pre class="javacode">
     * <<<<<<< .mine
     * user's text
     * =======
     * received text
     * >>>>>>> .r2</pre><br /> 
     * Also the operation creates three temporary files that appear in the 
     * same directory as the working file. Now if you call this method with 
     * <code>leave</code> set to <span class="javakeyword">true</span>,
     * an update will still create temporary files but won't place those two
     * sections into your working file. And this behaviour also concerns
     * merge operations: any merging to a conflicted file will be prevented. 
     * In addition if there is any registered event
     * handler for an <b>SVNDiffClient</b> or <b>SVNUpdateClient</b> 
     * instance then the handler will be dispatched an event with 
     * the status type set to {@link SVNStatusType#CONFLICTED_UNRESOLVED}. 
     * 
     * <p>
     * The default value is <span class="javakeyword">false</span> until
     * a caller explicitly changes it calling this method. 
     * 
     * @param leave  <span class="javakeyword">true</span> to prevent 
     *               conflicted files from merging (all merging operations 
     *               will be skipped), otherwise <span class="javakeyword">false</span>
     * @see          #isLeaveConflictsUnresolved()              
     * @see          SVNUpdateClient
     * @see          SVNDiffClient
     * @see          ISVNEventHandler
     * @deprecated   this method should not be used anymore
     */
    public void setLeaveConflictsUnresolved(boolean leave) {
        getDelegate16().setLeaveConflictsUnresolved(leave);
        getDelegate17().setLeaveConflictsUnresolved(leave);
    }
    
    /**
     * Determines if conflicted files should be left unresolved
     * preventing from merging their contents during update and merge 
     * operations.
     *  
     * @return     <span class="javakeyword">true</span> if conflicted files
     *             are set to be prevented from merging, <span class="javakeyword">false</span>
     *             if there's no such restriction
     * @see        #setLeaveConflictsUnresolved(boolean)
     * @deprecated this method should not be used anymore
     */
    public boolean isLeaveConflictsUnresolved() {
        return getDelegate16().isLeaveConflictsUnresolved();
    }
    
    /**
     * Sets an event handler for this object. This event handler
     * will be dispatched {@link SVNEvent} objects to provide 
     * detailed information about actions and progress state 
     * of version control operations performed by <b>do</b>*<b>()</b>
     * methods of <b>SVN</b>*<b>Client</b> classes.
     * 
     * @param dispatcher an event handler
     */
    public void setEventHandler(ISVNEventHandler dispatcher) {
        getDelegate16().setEventHandler(dispatcher);
        getDelegate17().setEventHandler(dispatcher);
    }

    /**
     * Sets a path list handler implementation to this object.
     * @param handler  handler implementation
     * @since          1.2.0
     */
    public void setPathListHandler(ISVNPathListHandler handler) {
        getDelegate16().setPathListHandler(handler);
        getDelegate17().setPathListHandler(handler);
    }
    
    /**
     * Sets a logger to write debug log information to.
     * 
     * @param log a debug logger
     */
    public void setDebugLog(ISVNDebugLog log) {
        if (log == null) {
            log = SVNDebugLog.getDefaultLog();
        }
        getDelegate16().setDebugLog(log);
        getDelegate17().setDebugLog(log);
    }
    
    /**
     * Returns the debug logger currently in use.  
     * 
     * <p>
     * If no debug logger has been specified by the time this call occurs, 
     * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
     * will be created and used.
     * 
     * @return a debug logger
     */
    public ISVNDebugLog getDebugLog() {
        return getDelegate16().getDebugLog();
    }
    
    /**
     * Returns the root of the repository. 
     * 
     * <p/>
     * If <code>path</code> is not <span class="javakeyword">null</span> and <code>pegRevision</code> is 
     * either {@link SVNRevision#WORKING} or {@link SVNRevision#BASE}, then attempts to fetch the repository 
     * root from the working copy represented by <code>path</code>. If these conditions are not met or if the 
     * repository root is not recorded in the working copy, then a repository connection is established 
     * and the repository root is fetched from the session. 
     * 
     * <p/>
     * When fetching the repository root from the working copy and if <code>access</code> is 
     * <span class="javakeyword">null</span>, a new working copy access will be created and the working copy 
     * will be opened non-recursively for reading only. 
     * 
     * <p/>
     * All necessary cleanup (session or|and working copy close) will be performed automatically as the routine 
     * finishes. 
     * 
     * @param  path           working copy path
     * @param  url            repository url
     * @param  pegRevision    revision in which the target is valid
     * @param  adminArea      working copy administrative area object
     * @param  access         working copy access object
     * @return                repository root url
     * @throws SVNException 
     * @since                 1.2.0         
     */
    public SVNURL getReposRoot(File path, SVNURL url, SVNRevision pegRevision, SVNAdminArea adminArea, 
            SVNWCAccess access) throws SVNException {
        try {
            return getDelegate17().getReposRoot(path, url, pegRevision, adminArea, access);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                return getDelegate16().getReposRoot(path, url, pegRevision, adminArea, access);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Removes or adds a path prefix. This method is not intended for 
     * users (from an API point of view). 
     * 
     * @param prefix a path prefix
     */
    public void setEventPathPrefix(String prefix) {
        getDelegate16().setEventPathPrefix(prefix);
        getDelegate17().setEventPathPrefix(prefix);
    }
    
    /**
     * Handles a next working copy path with the {@link ISVNPathListHandler path list handler} 
     * if any was provided to this object through {@link #setPathListHandler(ISVNPathListHandler)}.
     * 
     * <p/>
     * Note: used by <code>SVNKit</code> internals.
     * 
     * @param  path            working copy path 
     * @throws SVNException 
     * @since                  1.2.0
     */
    public void handlePathListItem(File path) throws SVNException {
        try {
            getDelegate17().handlePathListItem(path);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getDelegate16().handlePathListItem(path);
            } else {
                throw e;
            }
        }
    }

}