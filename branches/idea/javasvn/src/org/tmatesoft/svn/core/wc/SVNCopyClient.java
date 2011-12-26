/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNCommitMediator;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCommitter;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNLog;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * This class provides methods to perform any kinds of copying and moving that <b>SVN</b>
 * supports - operating on both Working Copies (WC) and URLs.
 * 
 * <p>
 * Copy operations allow a user to copy versioned files and directories with all their 
 * previous history in several ways. 
 * 
 * <p>
 * Supported copy operations are:
 * <ul>
 * <li> Working Copy to Working Copy (WC-to-WC) copying - this operation copies the source
 * Working Copy item to the destination one and schedules the source copy for addition with history.
 * <li> Working Copy to URL (WC-to-URL) copying - this operation commits to the repository (exactly
 * to that repository location that is specified by URL) a copy of the Working Copy item.
 * <li> URL to Working Copy (URL-to-WC) copying - this operation will copy the source item from
 * the repository to the Working Copy item and schedule the source copy for addition with history.
 * <li> URL to URL (URL-to-URL) copying - this is a fully repository-side operation, it commits 
 * a copy of the source item to a specified repository location (within the same repository, of
 * course). 
 * </ul>
 * 
 * <p> 
 * Besides just copying <b>SVNCopyClient</b> also is able to move a versioned item - that is
 * first making a copy of the source item and second scheduling the source item for deletion 
 * when operating on a Working Copy or right committing the deletion of the source item when 
 * operating immediately on the repository.
 * 
 * <p>
 * Supported move operations are:
 * <ul>
 * <li> Working Copy to Working Copy (WC-to-WC) moving - this operation copies the source
 * Working Copy item to the destination one and schedules the source item for deletion.
 * <li> URL to URL (URL-to-URL) moving - this is a fully repository-side operation, it commits 
 * a copy of the source item to a specified repository location (within the same repository, of
 * course) and deletes the source item. 
 * </ul>
 * 
 * <p>
 * Overloaded <code>doCopy(..)</code> methods of <b>SVNCopyClient</b> are similar to
 * 'svn copy' and 'svn move' commands of the <b>SVN</b> command line client. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * 
 */
public class SVNCopyClient extends SVNBasicClient {

    private ISVNCommitHandler myCommitHandler;

    public SVNCopyClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNCopyClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }

    /**
     * Sets an implementation of <span class="style0">ISVNCommitHandler</span> to 
     * the commit handler that will be used during commit operations to handle 
     * commit log messages. The handler will receive a clien's log message and items 
     * (represented as <span class="style0">SVNCommitItem</span> objects) that will be 
     * committed. Depending on implementor's aims the initial log message can
     * be modified (or something else) and returned back. 
     * 
     * <p>
     * If using <span class="style0">SVNCopyClient</span> without specifying any
     * commit handler then a default one will be used - {@link DefaultSVNCommitHandler}.
     * 
     * @param handler				an implementor's handler that will be used to handle 
     * 								commit log messages
     * @see	  #getCommitHandler()
     * @see	  ISVNCommitHandler
     */
    public void setCommitHandler(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    /**
     * Returns the specified commit handler (if set) being in use or a default one 
     * (<span class="style0">DefaultSVNCommitHandler</span>) if no special 
     * implementations of <span class="style0">ISVNCommitHandler</span> were 
     * previousely provided.
     *   
     * @return	the commit handler being in use or a default one
     * @see	    #setCommitHander(ISVNCommitHandler)
     * @see		ISVNCommitHabdler
     * @see		DefaultSVNCommitHandler 
     */
    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }

    // path, path (this could be anything).
    // dstRevision: WORKING or HEAD only
    /**
     * Copies (or moves) a source path/URL into a destination path/URL and in the cases of WC-to-WC or
     * URL-to-WC copying schedules the source copy for addition with history. 
     * 
     * <p>
     * 'Moving' is first copying
     * a source to a destination and then scheduling the source for deletion. 
     * 
     * <p>
     * <b>How this method works:</b>
     * <ul>
     * <li>If <code>srcRevision</code> = <code>dstRevision</code> = <b>SVNRevision.<i>WORKING</i></b> 
     * then WC-to-WC copying (moving) with scheduling a copy of <code>srcPath</code> for addition takes place. In this 
     * case both <code>srcPegRevision</code> and <code>dstPegRevision</code> are ignored.  
     * <li>If <code>srcRevision</code> = <b>SVNRevision.<i>WORKING</i></b> and <code>dstRevision</code> = <b>SVNRevision.<i>HEAD</i></b> 
     * then WC-to-URL copying takes place. If <code>srcPath</code> is a directory then the repository location where <code>srcPath</code> 
     * will be copied to is made up of the value of the 'svn:entries:url' property for <code>srcPath</code> and the name of the entry 
     * to be copied. For example, 'X:\WC\dir1' from 'svn://host/repos/dir1' will be copied into 'svn://host/repos/dir1/dir1'.  
     * <code>srcPath</code> must be a directory since a file can not be copied in this way.   
     * <li>If <code>dstRevision</code> = <b>SVNRevision.<i>WORKING</i></b> and <code>srcRevision</code> is something different from
     * <b>SVNRevision.<i>WORKING</i></b> then URL-to-WC copying with scheduling a copy of the source URL takes place. The source
     * URL corresponds to the repository location of <code>srcPath</code> at <code>srcRevision</code>.
     * <li>If <code>srcRevision</code> is different from <b>SVNRevision.<i>WORKING</i></b> and <code>dstRevision</code> = 
     * <b>SVNRevision.<i>HEAD</i></b> then URL-to-URL (moving) copying takes place. The source URL (that is the repository location of <code>srcPath</code>
     * at <code>srcRevision</code>) is copied (or moved) to the destination one (that is the repository location 
     * of <code>dstPath</code>).
     * </ul>
     * 
     * <p>
     * <b>Note:</b> if either <code>srcPegRevision</code> or <code>dstPegRevision</code> is 
     * <span class="javakeyword">null</span> or somehow invalid it's assigned to the value of
     * 'svn:entry:revision' property of the <code>srcPath</code>/<code>dstPath</code> entry.
     * 
     * @param  srcPath				the source WC item's path  					
     * @param  srcPegRevision		the revision at which the source item is seen in the  
     * 								repository  
     * @param  srcRevision			the desired revision of the source item; if this parameter is 
     * 								<span class="javakeyword">null</span> or somehow invalid then the current
     * 								item's WORKING revision is used
     * @param  dstPath				the destination WC item's path; 
     * @param  dstPegRevision		the revision at which the destination item is seen in the  
     * 								repository
     * @param  dstRevision			the desired revision of the destination item; can be only either <b>SVNRevision.<i>WORKING</i></b>
     * 								or <b>SVNRevision.<i>HEAD</i></b>
     * @param  force				<span class="javakeyword">true</span> to force the moving operation even if
     * 								the <code>srcPath</code> has local modifications; this flag is relevant
     * 								only for WC-to-WC moving 
     * @param  move					<span class="javakeyword">true</span> to move either <code>srcPath</code> item's repository
     * 								location to <code>dstPath</code> item's repository location or <code>srcPath</code> itself
     * 								to <code>dstItem</code>
     * @param  commitMessage		a string to be a commit log message
     * @return 						<b>SVNCommitInfo.<i>NULL</i></b> for URL-to-WC copying and WC-to-WC copying/moving; 
     * 								information on a new revision as the result of the commit for WC-to-URL copying and URL-to-URL copying/moving
     * @throws SVNException			1) If <code>srcPath</code> doesn't exist at <code>srcRevision</code>;
     * 								2) for URL-to-URL: source and destination are not located in the same 
     * 								repository;
     * 								3) If <code>dstURL</code> is a file that already exists 
     * 								4) if <code>dstRevision</code> is neither 
     * 								<b>SVNRevision.<i>HEAD</i></b> nor 
     * 								<b>SVNRevision.<i>WORKING</i></b> (only these are valid)
     * 								5) moving (<code>move</code> = <span class="javakeyword">true</span>) 
     * 								is supported only either for WC-to-WC or URL-to-URL schema
     * 								     
     */
    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcPegRevision,
            SVNRevision srcRevision, File dstPath, SVNRevision dstPegRevision,
            SVNRevision dstRevision, boolean force, boolean move,
            String commitMessage) throws SVNException {
        if (srcRevision == null || !srcRevision.isValid()) {
            srcRevision = SVNRevision.WORKING;
        }
        if (dstRevision == null || !dstRevision.isValid()) {
            dstRevision = SVNRevision.WORKING;
        }
        if (dstRevision != SVNRevision.HEAD
                && dstRevision != SVNRevision.WORKING) {
            SVNErrorManager
                    .error("svn: Only HEAD or WORKING revision could be specified as copy or move target revision");
        }
        if (srcRevision == SVNRevision.WORKING
                && dstRevision == SVNRevision.WORKING) {
            // wc->wc
            wc2wcCopy(srcPath, dstPath, force, move);
        } else if (srcRevision == SVNRevision.WORKING) {
            // wc->url
            if (move) {
                SVNErrorManager
                        .error("svn: Only WC to WC or URL to URL move is supported");
            }
            SVNWCAccess dstAccess = createWCAccess(srcPath);
            String dstURL = dstAccess.getTargetEntryProperty(SVNProperty.URL);
            if (dstPegRevision == null || !dstPegRevision.isValid()) {
                dstPegRevision = SVNRevision.parse(dstAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            }
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            return wc2urlCopy(srcPath, dstURL, commitMessage);
        } else if (dstRevision == SVNRevision.WORKING) {
            // url->wc
            if (move) {
                SVNErrorManager
                        .error("svn: Only WC to WC or URL to URL move is supported");
            }
            SVNWCAccess srcAccess = createWCAccess(srcPath);
            String srcURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
            if (srcPegRevision == null || !srcPegRevision.isValid()) {
                srcPegRevision = SVNRevision.parse(srcAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            }
            long srcRevNumber = getRevisionNumber(srcPath, srcRevision);
            srcURL = getURL(srcURL, srcPegRevision, SVNRevision
                    .create(srcRevNumber));
            url2wcCopy(srcURL, srcRevNumber, dstPath);
        } else {
            // url->url
            SVNWCAccess srcAccess = createWCAccess(srcPath);
            SVNWCAccess dstAccess = createWCAccess(srcPath);
    
            String srcURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
            String dstURL = dstAccess.getTargetEntryProperty(SVNProperty.URL);
            if (srcPegRevision == null || !srcPegRevision.isValid()) {
                srcPegRevision = SVNRevision.parse(srcAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            }
            if (dstPegRevision == null || !dstPegRevision.isValid()) {
                dstPegRevision = SVNRevision.parse(dstAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            }
            long srcRevNumber = getRevisionNumber(srcPath, srcRevision);
            srcURL = getURL(srcURL, srcPegRevision, SVNRevision
                    .create(srcRevNumber));
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            return url2urlCopy(srcURL, srcRevNumber, dstURL, commitMessage,
                    move);
        }
        return SVNCommitInfo.NULL;
    }

    // path, url (url->url or wc->url)
    /**
     * Copies (or moves) a source path/URL into a destination URL. 
     * 
     * <p>
     * 'Moving' is first copying a source to a destination and then scheduling 
     * the source for deletion. 
     * 
     * <p>
     * <b>How this method works:</b>
     * <ul>
     * <li>If <code>srcRevision</code> is different from <b>SVNRevision.<i>WORKING</i></b>
     * then URL-to-URL copying (or moving) takes place.
     * <li>Otherwise (<code>srcRevision</code> = <b>SVNRevision.<i>WORKING</i></b>) it's
     * WC-to-URL copying. In this case <code>srcPegRevision</code> is ignored.
     * </ul>
     * 
     * <p>
     * <b>Note:</b> if <code>srcPegRevision</code>  is <span class="javakeyword">null</span> or 
     * somehow invalid it's assigned (if needed) to the value of
     * 'svn:entry:revision' property of the <code>srcPath</code> entry.
     * 
     * 
     * @param  srcPath				the source WC item's path  					
     * @param  srcPegRevision		the revision at which the source item is seen in the  
     * 								repository  
     * @param  srcRevision			the revision of the source item; if this parameter is 
     * 								<span class="javakeyword">null</span> or somehow invalid then the current
     * 								item's WORKING revision is used
     * @param  dstURL				the repository location where the source item will be copied to 
     * @param  dstPegRevision		the revision at which the destination item (<code>dstURL</code>) is seen in the  
     * 								repository
     * @param  move					<span class="javakeyword">true</span> to move <code>srcPath</code> item's repository
     * 								location to <code>dstURL</code>
     * @param  commitMessage		a string to be a commit log message
     * @return 						information on a new revision as the result
     * 								of the commit
     * @throws SVNException			1) If <code>srcPath</code> doesn't exist at <code>srcRevision</code>;
     * 								2) If <code>dstURL</code> is a file that already exists 
     * 								3) for URL-to-URL: source and destination are not located in the same 
     * 								repository;
     * 								4) moving (<code>move</code> = <span class="javakeyword">true</span>) 
     * 								is supported only either for WC-to-WC or URL-to-URL schema
     */
    public SVNCommitInfo doCopy(File srcPath, SVNRevision srcPegRevision,
            SVNRevision srcRevision, String dstURL, SVNRevision dstPegRevision,
            boolean move, String commitMessage) throws SVNException {
        if (srcRevision == null || !srcRevision.isValid()) {
            srcRevision = SVNRevision.WORKING;
        }
        if (srcRevision != SVNRevision.WORKING) {
            // url->url
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            SVNWCAccess wcAccess = createWCAccess(srcPath);
            String srcURL = wcAccess.getTargetEntryProperty(SVNProperty.URL);
            if (srcPegRevision == null || !srcPegRevision.isValid()) {
                srcPegRevision = SVNRevision.parse(wcAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            }
            long srcRevisionNumber = getRevisionNumber(srcPath, srcRevision);
            srcURL = getURL(srcURL, srcPegRevision, SVNRevision
                    .create(srcRevisionNumber));
            return url2urlCopy(srcURL, srcRevisionNumber, dstURL,
                    commitMessage, move);
        } else {
            // wc->url
            if (move) {
                SVNErrorManager
                        .error("svn: Only WC to WC or URL to URL move is supported");
            }
            dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);
            return wc2urlCopy(srcPath, dstURL, commitMessage);
        }
    }
    
    /**
     * Copies (or moves) a source URL into a destination URL/path and in the case of
     * URL-to-WC copying schedules the source copy for addition with history. 
     * 
     * <p>
     * 'Moving' is first copying a source to a destination and then scheduling 
     * the source for deletion. 
     * 
     * <p>
     * <b>How this method works:</b>
     * <ul>
     * <li>If <code>dstRevision</code> = <b>SVNRevision.<i>WORKING</i></b>
     * then URL-to-WC copying takes place. In this case <code>dstPegRevision</code> is ignored.
     * <li>Otherwise (<code>dstRevision</code> = <b>SVNRevision.<i>HEAD</i></b>) it's URL-to-URL 
     * copying. 
     * </ul>
     * 
     * <p>
     * <b>Note:</b> if <code>dstPegRevision</code>  is <span class="javakeyword">null</span> or 
     * somehow invalid it's assigned to the value of
     * 'svn:entry:revision' property of the <code>srcPath</code> entry.
     * 
     * @param  srcURL				the repository location of the source item  					
     * @param  srcPegRevision		the revision at which the source item is seen in the  
     * 								repository  
     * @param  srcRevision			the desired revision of the source item; 
     * @param  dstPath				the destination WC item's path where the source item will be copied to 
     * @param  dstPegRevision		the revision at which the destination item is seen in the  
     * 								repository
     * @param  dstRevision			the desired revision of the destination item; can be only either 
     * 								<b>SVNRevision.<i>WORKING</i></b> or <b>SVNRevision.<i>HEAD</i></b>
     * @param  move					<span class="javakeyword">true</span> to move <code>srcURL</code> to 
     * 								<code>dstPath</code> item's repository location
     * @param  commitMessage		a string to be a commit log message
     * @return 						<b>SVNCommitInfo.<i>NULL</i></b> for URL-to-WC copying; information on a 
     * 								new revision as the result of the commit for URL-to-URL copying/moving
     * @throws SVNException			1) If <code>srcURL</code> doesn't exist at <code>srcRevision</code>;
     * 								2) If <code>dstPath</code> is a file that already exists 
     * 								3) for URL-to-URL: source and destination are not located in the same 
     * 								repository;
     * 								4) if <code>dstRevision</code> is neither 
     * 								<b>SVNRevision.<i>HEAD</i></b> nor 
     * 								<b>SVNRevision.<i>WORKING</i></b> (only these are valid)
     * 								5) moving (<code>move</code> = <span class="javakeyword">true</span>) 
     * 								is supported only either for WC-to-WC or URL-to-URL schema
     */
    // url, path (url->url or url->wc)
    // dstRevision: WORKING or HEAD only
    public SVNCommitInfo doCopy(String srcURL, SVNRevision srcPegRevision,
            SVNRevision srcRevision, File dstPath, SVNRevision dstPegRevision,
            SVNRevision dstRevision, boolean move, String commitMessage)
            throws SVNException {
        if (dstRevision == null || !dstRevision.isValid()) {
            dstRevision = SVNRevision.WORKING;
        }
        if (dstRevision != SVNRevision.HEAD
                && dstRevision != SVNRevision.WORKING) {
            SVNErrorManager
                    .error("svn: Only HEAD or WORKING revision could be specified as copy or move target revision");
        }
        srcURL = getURL(srcURL, srcPegRevision, srcRevision);
        long srcRevisionNumber = getRevisionNumber(srcURL, srcRevision);
        if (dstRevision == SVNRevision.WORKING) {
            // url->wc
            if (move) {
                SVNErrorManager
                        .error("svn: Only WC to WC or URL to URL move is supported");
            }
            url2wcCopy(srcURL, srcRevisionNumber, dstPath);
        } else {
            // url->url
            SVNWCAccess wcAccess = createWCAccess(dstPath);
            String dstURL = wcAccess.getTargetEntryProperty(SVNProperty.NAME);
            if (dstPegRevision == null || !dstPegRevision.isValid()) {
                dstPegRevision = SVNRevision.parse(wcAccess
                        .getTargetEntryProperty(SVNProperty.REVISION));
            }
            dstURL = getURL(dstURL, dstPegRevision, dstRevision);
            return url2urlCopy(srcURL, srcRevisionNumber, dstURL,
                    commitMessage, move);
        }
        return SVNCommitInfo.NULL;
    }

    // url, url (url->url only)
    /**
     * Copies (or moves) a source URL into a destination URL. 
     * 
     * <p>
     * 'Moving' is first copying a source to a destination and then scheduling 
     * the source for deletion. 
     * 
     * @param  srcURL				the repository location of the source item to be copied  					
     * @param  srcPegRevision		the revision at which the source entry is seen in the  
     * 								repository; if this parameter is <span class="javakeyword">null</span> 
     * 								then it's assigned to <b>SVNRevision.<i>UNDEFINED</i></b>  
     * @param  srcRevision			the desired revision of the source item; if this parameter is 
     * 								<span class="javakeyword">null</span> then it's assigned to
     * 								<b>SVNRevision.<i>HEAD</i></b>
     * @param  dstURL				the repository location of the destination item where the source will be copied to 
     * @param  dstPegRevision		the revision at which the destination entry (<code>dstURL</code>) is seen in the  
     * 								repository; if this parameter is <span class="javakeyword">null</span> 
     * 								then it's assigned to <b>SVNRevision.<i>UNDEFINED</i></b>
     * @param  move					<span class="javakeyword">true</span> to move <code>srcURL</code> to <code>dstURL</code>
     * @param  commitMessage		a string to be a commit log message
     * @return 						information on a new revision as the result
     * 								of the commit
     * @throws SVNException			Possible cases are: 
     * 								1) If <code>srcURL</code> doesn't exist at <code>srcRevision</code>;
     * 								2) If <code>dstURL</code> is a file that already exists 
     * 								3) source and destination are not located in the same 
     * 								repository;
     */
    public SVNCommitInfo doCopy(String srcURL, SVNRevision srcPegRevision,
            SVNRevision srcRevision, String dstURL, SVNRevision dstPegRevision,
            boolean move, String commitMessage) throws SVNException {
        if (srcRevision == null) {
            srcRevision = SVNRevision.HEAD;
        }
        if (srcPegRevision == null) {
            srcPegRevision = SVNRevision.UNDEFINED;
        }
        if (dstPegRevision == null) {
            dstPegRevision = SVNRevision.UNDEFINED;
        }
        srcURL = validateURL(srcURL);
        dstURL = validateURL(dstURL);
        long srcRevNumber = getRevisionNumber(srcURL, srcRevision);

        srcURL = getURL(srcURL, srcPegRevision, SVNRevision
                .create(srcRevNumber));
        dstURL = getURL(dstURL, dstPegRevision, SVNRevision.HEAD);

        return url2urlCopy(srcURL, srcRevNumber, dstURL, commitMessage, move);
    }

    private SVNCommitInfo wc2urlCopy(File srcPath, String dstURL,
            String commitMessage) throws SVNException {
        dstURL = validateURL(dstURL);
        SVNRepository repos = createRepository(dstURL);
        SVNNodeKind dstKind = repos.checkPath("", -1);
        if (dstKind == SVNNodeKind.DIR) {
            dstURL = SVNPathUtil
                    .append(dstURL, SVNEncodingUtil.uriEncode(srcPath.getName()));
        } else if (dstKind == SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: File '" + dstURL + "' already exists");
        }
        SVNCommitItem[] items = new SVNCommitItem[] { new SVNCommitItem(null,
                dstURL, null, SVNNodeKind.NONE, SVNRevision.UNDEFINED, true,
                false, false, false, true, false) };
        commitMessage = getCommitHandler().getCommitMessage(commitMessage,
                items);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }
        // collect commitables.
        Collection tmpFiles = null;
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;
        SVNWCAccess wcAccess = createWCAccess(srcPath);
        try {
            wcAccess.open(true, true);

            Map commitables = new TreeMap();
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(
                    wcAccess.getTargetName(), false);
            if (entry == null) {
                SVNErrorManager.error("svn: '" + srcPath
                        + "' is not under version control");
                return SVNCommitInfo.NULL;
            }
            SVNCommitUtil.harvestCommitables(commitables, wcAccess.getAnchor(),
                    srcPath, null, entry, dstURL, entry.getURL(), true, false,
                    false, null, true);

            items = (SVNCommitItem[]) commitables.values().toArray(
                    new SVNCommitItem[commitables.values().size()]);
            commitables = new TreeMap();
            dstURL = SVNCommitUtil.translateCommitables(items, commitables);

            SVNRepository repository = createRepository(dstURL);
            SVNCommitMediator mediator = new SVNCommitMediator(wcAccess,
                    commitables);
            tmpFiles = mediator.getTmpFiles();

            commitEditor = repository.getCommitEditor(commitMessage, null,
                    false, mediator);
            info = SVNCommitter.commit(wcAccess, tmpFiles, commitables,
                    repository.getRepositoryRoot(true).getPath(), commitEditor);
            commitEditor = null;
        } finally {
            if (tmpFiles != null) {
                for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                    File file = (File) files.next();
                    file.delete();
                }
            }
            if (commitEditor != null && info == null) {
                commitEditor.abortEdit();
            }
            if (wcAccess != null) {
                wcAccess.close(true);
            }
        }

        return info != null ? info : SVNCommitInfo.NULL;
    }

    private void wc2wcCopy(File srcPath, File dstPath, boolean force,
            boolean move) throws SVNException {
        // 1. can't copy src to its own child
        if (SVNPathUtil.isChildOf(srcPath, dstPath)) {
            SVNErrorManager.error("svn: Cannot copy '" + srcPath
                    + "' into its own child '" + dstPath + "'");
        }
        // 2. can't move path into itself
        if (move && srcPath.equals(dstPath)) {
            SVNErrorManager.error("svn: Cannot move '" + srcPath
                    + "' into itself");
        }
        // 3. src should exist
        SVNFileType srcType = SVNFileType.getType(srcPath);
        if (srcType == SVNFileType.NONE) {
            SVNErrorManager.error("svn: Path '" + srcPath + "' does not exist");
        }
        // 4. if dst exists - use its child
        SVNFileType dstType = SVNFileType.getType(dstPath);
        if (dstType == SVNFileType.DIRECTORY) {
            dstPath = new File(dstPath, srcPath.getName());
            dstType = SVNFileType.getType(dstPath);
            if (dstType != SVNFileType.NONE) {
                SVNErrorManager.error("svn: '" + dstPath
                        + "' already exist and is in a way");
            }
        } else if (dstType != SVNFileType.NONE) {
            SVNErrorManager.error("svn: File '" + dstPath + "' already exist");
        }
        // 5. if move -> check if dst could be deleted later.
        SVNWCAccess srcAccess = createWCAccess(srcPath);
        SVNWCAccess dstAccess = createWCAccess(dstPath);
        try {
            if (move) {
                if (srcAccess.getAnchor().getRoot().equals(
                        dstAccess.getAnchor().getRoot())) {
                    dstAccess = srcAccess;
                }
                srcAccess.open(true, srcType == SVNFileType.DIRECTORY);
                if (!force) {
                    srcAccess.getAnchor().canScheduleForDeletion(
                            dstAccess.getTargetName());
                }
            }
            if (srcAccess != dstAccess) {
                dstAccess.open(true, srcType == SVNFileType.DIRECTORY);
            }
            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries()
                    .getEntry("", true);
            if (dstParentEntry.isScheduledForDeletion()) {
                SVNErrorManager.error("svn: Cannot copy to '" + dstPath
                        + "' as it is scheduled for deletion");
            }
            if (srcType == SVNFileType.DIRECTORY) {
                // copy directory.
                copyDirectory(dstAccess, srcAccess, dstPath.getName());
            } else {
                // copy single file.
                copyFile(dstAccess, srcAccess, dstPath.getName());
            }

            if (move) {
                srcAccess.getAnchor().scheduleForDeletion(srcPath.getName());
            }
        } finally {
            dstAccess.close(true);
            if (move && srcAccess != dstAccess) {
                srcAccess.close(true);
            }
        }
    }

    private void url2wcCopy(String srcURL, long srcRevision, File dstPath)
            throws SVNException {
        SVNRepository repos = createRepository(srcURL);
        SVNNodeKind srcKind = repos.checkPath("", srcRevision);
        if (srcKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: URL '" + srcURL + "' does not exist");
        }
        String srcUUID = repos.getRepositoryUUID();
        if (dstPath.isDirectory()) {
            dstPath = new File(dstPath, SVNEncodingUtil.uriDecode(SVNPathUtil.tail(srcURL)));
            if (dstPath.exists()) {
                SVNErrorManager.error("svn: Path '" + dstPath + "' already exists");
            }
        } else if (dstPath.exists()) {
            SVNErrorManager.error("svn: Path '" + dstPath + "' already exists");
        }

        boolean sameRepositories;
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        try {
            wcAccess.open(true, false);
            // check for missing entry.
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName(), false);
            if (entry != null && !entry.isDirectory()) {
                SVNErrorManager.error("svn: '" + dstPath + "' is already under version control");
            }
            String uuid = wcAccess.getTargetEntryProperty(SVNProperty.UUID);
            sameRepositories = uuid.equals(srcUUID);
            if (srcKind == SVNNodeKind.DIR) {
                String dstURL = wcAccess.getAnchor().getEntries()
                        .getPropertyValue("", SVNProperty.URL);
                dstURL = SVNPathUtil.append(dstURL, SVNEncodingUtil.uriEncode(dstPath
                        .getName()));
                SVNDirectory targetDir = createVersionedDirectory(dstPath,
                        dstURL, uuid, srcRevision);
                SVNWCAccess wcAccess2 = new SVNWCAccess(targetDir, targetDir,
                        "");
                wcAccess2.open(true, true);
                setDoNotSleepForTimeStamp(true);
                try {
                    SVNReporter reporter = new SVNReporter(wcAccess2, true,
                            true);
                    SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess2,
                            null, true, isLeaveConflictsUnresolved());

                    repos.update(srcRevision, null, true, reporter, editor);
                    dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(
                            wcAccess, editor.getTargetRevision()));
                    if (sameRepositories) {
                        addDir(wcAccess.getAnchor(), dstPath.getName(), srcURL,
                                editor.getTargetRevision());
                        addDir(wcAccess2.getAnchor(), "", srcURL, editor
                                .getTargetRevision());
                        dispatchEvent(SVNEventFactory.createAddedEvent(
                                wcAccess, wcAccess.getAnchor(), wcAccess
                                        .getAnchor().getEntries().getEntry(
                                                dstPath.getName(), true)));
                    } else {
                        SVNErrorManager.error("svn: '" + dstPath.getParentFile() + "' belongs to different repository then '" + srcURL + "'");
                    }
                } finally {
                    setDoNotSleepForTimeStamp(false);
                    wcAccess2.close(true);
                }
            } else {
                Map properties = new HashMap();
                File tmpFile = null;
                File baseTmpFile = wcAccess.getAnchor().getBaseFile(dstPath.getName(), true);
                tmpFile = SVNFileUtil.createUniqueFile(baseTmpFile.getParentFile(), dstPath.getName(), ".tmp");
                OutputStream os = SVNFileUtil.openFileForWriting(tmpFile);

                try {
                    repos.getFile("", srcRevision, properties, os);
                } finally {
                    SVNFileUtil.closeFile(os);
                }
                SVNFileUtil.rename(tmpFile, baseTmpFile);
                if (tmpFile != null) {
                    tmpFile.delete();
                }
                addFile(wcAccess.getAnchor(), dstPath.getName(), properties,
                        sameRepositories ? srcURL : null, srcRevision);
                wcAccess.getAnchor().runLogs();
                dispatchEvent(SVNEventFactory.createAddedEvent(wcAccess,
                        wcAccess.getAnchor(), wcAccess.getAnchor().getEntries()
                                .getEntry(dstPath.getName(), true)));
            }
        } finally {
            if (wcAccess != null) {
                wcAccess.close(true);
            }
            if (!isDoNotSleepForTimeStamp()) {
                SVNFileUtil.sleepForTimestamp();
            }
        }

    }

    private SVNCommitInfo url2urlCopy(String srcURL, long srcRevision, String dstURL, String commitMessage, final boolean move) throws SVNException {
        String commonURL = SVNPathUtil.getCommonURLAncestor(srcURL, dstURL);
        if (commonURL.length() == 0 || commonURL.indexOf("://") < 0) {
            SVNErrorManager.error("svn: Source and dest appear not to be in the same repository (src: '" + srcURL + "'; dst: '" + dstURL + "'");
        }
        boolean resurrect = false;
        if (srcURL.equals(dstURL)) {
            commonURL = SVNPathUtil.removeTail(commonURL);
            resurrect = true;
        }
        String srcRelative = SVNEncodingUtil.uriDecode(srcURL.substring(commonURL.length()));
        String dstRelative = SVNEncodingUtil.uriDecode(dstURL.substring(commonURL.length()));
        if (srcRelative.length() == 0 && move) {
            SVNErrorManager.error("svn: Cannot move '" + srcURL  + "' into itself");
        }
        if (srcRelative.startsWith("/")) {
            srcRelative = srcRelative.substring(1);
        }
        if (dstRelative.startsWith("/")) {
            dstRelative = dstRelative.substring(1);
        }
        SVNRepository repos = createRepository(commonURL);
        long lastRevision = repos.getLatestRevision();
        if (srcRevision < 0) {
            srcRevision = lastRevision;
        }
        final SVNNodeKind srcKind = repos.checkPath(srcRelative, srcRevision);
        if (srcKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: Path '" + srcURL + "' does not exist in revision " + srcRevision);
        }
        SVNNodeKind dstKind = repos.checkPath(dstRelative, lastRevision);
        if (dstKind == SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: Path '" + dstURL + "' already exists");
        } else if (dstKind == SVNNodeKind.DIR) {
            String newDstPath = dstRelative.length() == 0 ? 
                    SVNEncodingUtil.uriDecode(SVNPathUtil.tail(srcURL)) : 
                    SVNPathUtil.append(dstRelative, SVNEncodingUtil.uriDecode(SVNPathUtil.tail(srcURL)));
            dstKind = repos.checkPath(newDstPath, lastRevision);
            if (dstKind != SVNNodeKind.NONE) {
                SVNErrorManager.error("svn: Path '" + newDstPath + "' already exists");
            }
            dstRelative = newDstPath;
        }

        Collection commitItems = new ArrayList(2);
        commitItems.add(new SVNCommitItem(null, dstURL, srcURL, srcKind,
                SVNRevision.create(srcRevision), true, false, false, false,
                true, false));
        if (move) {
            commitItems.add(new SVNCommitItem(null, srcURL, null, srcKind,
                    SVNRevision.create(srcRevision), false, true, false, false,
                    false, false));
        }
        commitMessage = getCommitHandler().getCommitMessage(commitMessage, (SVNCommitItem[]) commitItems.toArray(new SVNCommitItem[commitItems.size()]));
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }

        ISVNEditor commitEditor = repos.getCommitEditor(commitMessage, null, false, null);
        final String srcPath = srcRelative;
        final String dstPath = dstRelative;
        final long srcRevNumber = srcRevision;
        final boolean isRessurect = resurrect;
        ISVNCommitPathHandler committer = new ISVNCommitPathHandler() {
            public boolean handleCommitPath(String commitPath,
                    ISVNEditor commitEditor) throws SVNException {
                boolean doAdd = false;
                boolean doDelete = false;
                if (isRessurect) {
                    if (!move) {
                        doAdd = true;
                    }
                } else {
                    if (move) {
                        if (commitPath.equals(srcPath)) {
                            doDelete = true;
                        } else {
                            doAdd = true;
                        }
                    } else {
                        doAdd = true;
                    }
                }
                if (doDelete) {
                    commitEditor.deleteEntry(srcPath, -1);
                }
                boolean closeDir = false;
                if (doAdd) {
                    if (srcKind == SVNNodeKind.DIR) {
                        commitEditor.addDir(dstPath, srcPath, srcRevNumber);
                        closeDir = true;
                    } else {
                        commitEditor.addFile(dstPath, srcPath, srcRevNumber);
                        commitEditor.closeFile(dstPath, null);
                    }
                }
                return closeDir;
            }
        };
        Collection paths = move ? Arrays.asList(new String[] { srcRelative, dstRelative }) : Collections.singletonList(dstRelative);

        SVNCommitInfo result = null;
        try {
            SVNCommitUtil.driveCommitEditor(committer, paths, commitEditor, -1);
            result = commitEditor.closeEdit();
        } catch (SVNException e) {
            try {
                commitEditor.abortEdit();
            } catch (SVNException inner) {
                //
            }
            SVNDebugLog.log(e);
            SVNErrorManager.error("svn: " + e.getMessage());
        }
        return result != null ? result : SVNCommitInfo.NULL;
    }

    private void addDir(SVNDirectory dir, String name, String copyFromURL,
            long copyFromRev) throws SVNException {
        SVNEntry entry = dir.getEntries().getEntry(name, true);
        if (entry == null) {
            entry = dir.getEntries().addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        if (copyFromURL != null) {
            entry.setCopyFromRevision(copyFromRev);
            entry.setCopyFromURL(copyFromURL);
            entry.setCopied(true);
        }
        entry.scheduleForAddition();
        if ("".equals(name) && copyFromURL != null) {
            updateCopiedDirectory(dir, name, null, null, -1);
        }
        dir.getEntries().save(true);
    }

    static void updateCopiedDirectory(SVNDirectory dir, String name,
            String newURL, String copyFromURL, long copyFromRevision)
            throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry != null) {
            entry.setCopied(true);
            if (newURL != null) {
                entry.setURL(newURL);
            }
            if (entry.isFile()) {
                dir.getWCProperties(name).delete();
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
            }
            boolean deleted = false;
            if (entry.isDeleted() && newURL != null) {
                // convert to scheduled for deletion.
                deleted = true;
                entry.setDeleted(false);
                entry.scheduleForDeletion();
                if (entry.isDirectory()) {
                    entry.setKind(SVNNodeKind.FILE);
                }
            }
            if (entry.getLockToken() != null && newURL != null) {
                entry.setLockToken(null);
                entry.setLockOwner(null);
                entry.setLockComment(null);
                entry.setLockCreationDate(null);
            }
            if (!"".equals(name) && entry.isDirectory() && !deleted) {
                SVNDirectory childDir = dir.getChildDirectory(name);
                if (childDir != null) {
                    String childCopyFromURL = copyFromURL == null ? null
                            : SVNPathUtil.append(copyFromURL, SVNEncodingUtil.uriEncode(entry.getName()));
                    updateCopiedDirectory(childDir, "", newURL,
                            childCopyFromURL, copyFromRevision);
                }
            } else if ("".equals(name)) {
                dir.getWCProperties("").delete();
                if (copyFromURL != null) {
                    entry.setCopyFromURL(copyFromURL);
                    entry.setCopyFromRevision(copyFromRevision);
                }
                for (Iterator ents = entries.entries(true); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    String childCopyFromURL = copyFromURL == null ? null
                            : SVNPathUtil.append(copyFromURL, SVNEncodingUtil.uriEncode(childEntry.getName()));
                    String newChildURL = newURL == null ? null : SVNPathUtil.append(newURL, SVNEncodingUtil.uriEncode(childEntry.getName()));
                    updateCopiedDirectory(dir, childEntry.getName(),
                            newChildURL, childCopyFromURL, copyFromRevision);
                }
                entries.save(true);
            }
        }
    }

    private void addFile(SVNDirectory dir, String fileName, Map properties,
            String copyFromURL, long copyFromRev) throws SVNException {
        SVNLog log = dir.getLog(0);
        Map regularProps = new HashMap();
        Map entryProps = new HashMap();
        Map wcProps = new HashMap();
        for (Iterator names = properties.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            String propValue = (String) properties.get(propName);
            if (propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                entryProps.put(SVNProperty.shortPropertyName(propName),
                        propValue);
            } else if (propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                wcProps.put(propName, propValue);
            } else {
                regularProps.put(propName, propValue);
            }
        }
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.KIND),
                SVNProperty.KIND_FILE);
        entryProps
                .put(SVNProperty.shortPropertyName(SVNProperty.REVISION), "0");
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE),
                SVNProperty.SCHEDULE_ADD);
        if (copyFromURL != null) {
            entryProps.put(SVNProperty
                    .shortPropertyName(SVNProperty.COPYFROM_REVISION), Long
                    .toString(copyFromRev));
            entryProps.put(SVNProperty
                    .shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL);
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPIED),
                    Boolean.TRUE.toString());
        }

        log.logChangedEntryProperties(fileName, entryProps);
        log.logChangedWCProperties(fileName, wcProps);
        dir.mergeProperties(fileName, regularProps, null, true, log);

        Map command = new HashMap();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(
                fileName, true)));
        command.put(SVNLog.DEST_ATTR, fileName);
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(
                fileName, true)));
        command.put(SVNLog.DEST_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(
                fileName, false)));
        log.addCommand(SVNLog.MOVE, command, false);
        log.save();
    }

    private void copyFile(SVNWCAccess dstAccess, SVNWCAccess srcAccess,
            String dstName) throws SVNException {
        SVNEntry dstEntry = dstAccess.getAnchor().getEntries().getEntry(
                dstName, false);
        File dstPath = new File(dstAccess.getAnchor().getRoot(), dstName);
        File srcPath = new File(srcAccess.getAnchor().getRoot(), srcAccess
                .getTargetName());
        if (dstEntry != null && dstEntry.isFile()) {
            if (dstEntry.isScheduledForDeletion()) {
                SVNErrorManager
                        .error("svn: '"
                                + dstPath
                                + "' is scheduled for deletion; it must be committed before being overwritten");
            } else {
                SVNErrorManager.error("svn: There is already versioned item '"
                        + dstPath + "'");
            }
        }
        SVNEntry srcEntry = srcAccess.getAnchor().getEntries().getEntry(
                srcAccess.getTargetName(), true);
        if (srcEntry == null) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcPath
                    + "': it's not under version control");
        } else if (srcEntry.isScheduledForAddition()
                || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorManager.error("svn: Cannot copy or move '" + srcPath
                    + "': it's not in repository yet; try committing first");
        }
        SVNFileType srcType = SVNFileType.getType(srcPath);
        if (srcType == SVNFileType.SYMLINK) {
            String name = SVNFileUtil.getSymlinkName(srcPath);
            if (name != null) {
                SVNFileUtil.createSymlink(dstPath, name);
            }
        } else {
            SVNFileUtil.copyFile(srcPath, dstPath, false);
        }
        // copy props, props base and text-base
        File srcTextBase = srcAccess.getAnchor().getBaseFile(
                srcAccess.getTargetName(), false);
        SVNProperties srcProps = srcAccess.getAnchor().getProperties(
                srcAccess.getTargetName(), false);
        boolean executable = srcProps.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        SVNProperties srcBaseProps = srcAccess.getAnchor().getBaseProperties(
                srcAccess.getTargetName(), false);

        File dstTextBase = dstAccess.getAnchor().getBaseFile(dstName, false);
        SVNProperties dstProps = dstAccess.getAnchor().getProperties(dstName,
                false);
        SVNProperties dstBaseProps = srcAccess.getAnchor().getBaseProperties(
                dstName, false);
        if (srcTextBase.exists()) {
            SVNFileUtil.copyFile(srcTextBase, dstTextBase, false);
        }
        if (srcProps.getFile().exists()) {
            srcProps.copyTo(dstProps);
        }
        if (srcBaseProps.getFile().exists()) {
            srcBaseProps.copyTo(dstBaseProps);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        // and finally -> add.
        String copyFromURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
        long copyFromRevision = SVNRevision.parse(
                srcAccess.getTargetEntryProperty(SVNProperty.REVISION))
                .getNumber();

        SVNEntry entry = dstAccess.getAnchor().add(dstName, false, false);
        entry.setCopied(true);
        entry.setCopyFromRevision(copyFromRevision);
        entry.setCopyFromURL(copyFromURL);
        entry.setRevision(copyFromRevision);
        entry.scheduleForAddition();
        dstAccess.getAnchor().getEntries().save(true);
    }

    private void copyDirectory(SVNWCAccess dstAccess, SVNWCAccess srcAccess,
            String dstName) throws SVNException {
        SVNEntry srcEntry = dstAccess.getTarget().getEntries().getEntry("",
                false);
        if (srcEntry == null) {
            SVNErrorManager.error("svn: '" + srcAccess.getTarget().getRoot()
                    + "' is not under version control");
        } else if (srcEntry.isScheduledForAddition()
                || srcEntry.getURL() == null || srcEntry.isCopied()) {
            SVNErrorManager.error("svn: Cannot copy or move '"
                    + srcAccess.getTarget().getRoot()
                    + "': it is not in repository yet; try committing first");
        }
        String copyFromURL = srcAccess.getTargetEntryProperty(SVNProperty.URL);
        long copyFromRev = SVNRevision.parse(
                srcAccess.getTargetEntryProperty(SVNProperty.REVISION))
                .getNumber();

        String newURL = dstAccess.getAnchor().getEntries().getEntry("", true)
                .getURL();
        newURL = SVNPathUtil.append(newURL, SVNEncodingUtil.uriEncode(dstName));

        File dstPath = new File(dstAccess.getAnchor().getRoot(), dstName);

        SVNFileUtil.copyDirectory(srcAccess.getTarget().getRoot(), dstPath,
                true);

        SVNDirectory newDir = dstAccess.addDirectory(dstName, dstPath, true,
                true);

        SVNEntry entry = dstAccess.getAnchor().getEntries().addEntry(dstName);
        entry.setCopyFromRevision(copyFromRev);
        entry.setKind(SVNNodeKind.DIR);
        entry.scheduleForAddition();
        entry.setCopyFromURL(copyFromURL);
        entry.setCopied(true);

        SVNEvent event = SVNEventFactory.createAddedEvent(dstAccess, dstAccess
                .getAnchor(), entry);
        dispatchEvent(event);
        dstAccess.getTarget().getEntries().save(true);

        updateCopiedDirectory(newDir, "", newURL, null, -1);
        SVNEntry newRoot = newDir.getEntries().getEntry("", true);
        newRoot.scheduleForAddition();
        newRoot.setCopyFromRevision(copyFromRev);
        newRoot.setCopyFromURL(copyFromURL);
        newDir.getEntries().save(true);
        // fire added event.
    }
}