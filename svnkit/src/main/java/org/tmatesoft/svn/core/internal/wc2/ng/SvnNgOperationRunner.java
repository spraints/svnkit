package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc2.SvnLocalOperationRunner;
import org.tmatesoft.svn.core.wc2.SvnOperation;

public abstract class SvnNgOperationRunner<T extends SvnOperation> extends SvnLocalOperationRunner<T> {
    
    protected void run() throws SVNException {
        run(getWcContext());
    }
    
    protected boolean matchesChangelist(File target) {
        return getWcContext().matchesChangelist(target, getOperation().getApplicableChangelists());
    }
    
    protected abstract void run(SVNWCContext context) throws SVNException;

}
