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
package org.tmatesoft.svn.cli2.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNNotifyPrinter;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMoveCommand extends SVNCommand {

    public SVNMoveCommand() {
        super("move", new String[] {"mv", "rename", "ren"});
    }

    public boolean isCommitter() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.PARENTS);
        options = SVNOption.addLogMessageOptions(options);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = getEnvironment().combineTargets(null);
        if (targets.size() < 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        if (getEnvironment().getStartRevision() != SVNRevision.UNDEFINED && 
                getEnvironment().getStartRevision() != SVNRevision.HEAD) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Cannot specify revision (except HEAD) with move operation"));
        }
        SVNCommandTarget dst = new SVNCommandTarget((String) targets.remove(targets.size() - 1));
        if (!dst.isURL()) {
            if (getEnvironment().getMessage() != null || getEnvironment().getFileData() != null || getEnvironment().getRevisionProperties() != null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_UNNECESSARY_LOG_MESSAGE,
                "Local, non-commit operations do not take a log message or revision properties"));
            }
        }

        SVNCopyClient client = getEnvironment().getClientManager().getCopyClient();
        if (!getEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
        }
        client.setCommitHandler(getEnvironment());
        Collection sources = new ArrayList();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNCommandTarget source = new SVNCommandTarget(targetName);
            if (source.isURL()) {
                sources.add(new SVNCopySource(SVNRevision.HEAD, SVNRevision.UNDEFINED, source.getURL()));
            } else {
                sources.add(new SVNCopySource(getEnvironment().getStartRevision(), SVNRevision.UNDEFINED, source.getFile()));
            }
        }
        SVNCopySource[] copySources = (SVNCopySource[]) sources.toArray(new SVNCopySource[sources.size()]);
        try {
            if (dst.isURL()) {
                SVNCommitInfo info = client.doCopy(copySources, dst.getURL(), true, false, 
                        getEnvironment().isParents(), getEnvironment().getMessage(), getEnvironment().getRevisionProperties());
                if (!getEnvironment().isQuiet()) {
                    getEnvironment().printCommitInfo(info);
                }
            } else {
                client.doCopy(copySources, dst.getFile(), true, getEnvironment().isParents());
            }
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            SVNErrorCode code = err.getErrorCode();
            if (code == SVNErrorCode.UNVERSIONED_RESOURCE || code == SVNErrorCode.CLIENT_MODIFIED) {
                err = err.wrap("Use --force to override this restriction");
            }
            SVNErrorManager.error(err);
        }
    }

}
