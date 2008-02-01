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
package org.tmatesoft.svn.cli2.svnadmin;

import java.io.File;
import java.util.List;

import org.tmatesoft.svn.cli2.AbstractSVNCommand;
import org.tmatesoft.svn.cli2.AbstractSVNOption;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminCommand extends AbstractSVNCommand {

    protected SVNAdminCommand(String name, String[] aliases) {
        super(name, aliases);
    }
    
    protected SVNAdminCommandEnvironment getSVNAdminEnvironment() {
        return (SVNAdminCommandEnvironment) getEnvironment();
    }
    
    protected File getLocalRepository() throws SVNException {
        List targets = getEnvironment().combineTargets(null);
        if (targets.isEmpty()) {
            targets.add("");
        }
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Repository argument required"));
        }
        SVNPath target = new SVNPath((String) targets.get(0));
        if (target.isURL()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "'" + target.getTarget() + "' is an URL when it should be a path"));
        }
        return target.getFile();
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli2.svnadmin.commands";
    }

    protected long getRevisionNumber(SVNRevision rev, long latestRevision, SVNRepository repos) throws SVNException {
        long result = -1;
        if (rev.getNumber() >= 0) {
            result = rev.getNumber();
        } else if (rev == SVNRevision.HEAD) {
            result = latestRevision;
        } else if (rev.getDate() != null) {
            result = repos.getDatedRevision(rev.getDate());
        } else if (rev != SVNRevision.UNDEFINED) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Invalid revision specifier"));
        }
        if (result > latestRevision) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "Revisions must not be greater than the youngest revision ("  + latestRevision + ")"));
        }
        return result;
    }
    

    public boolean isOptionSupported(AbstractSVNOption option) {
        boolean supported = super.isOptionSupported(option);
        if (!supported) {
            return option == SVNAdminOption.HELP || option == SVNAdminOption.QUESTION;
        }
        return true;
    }

}
