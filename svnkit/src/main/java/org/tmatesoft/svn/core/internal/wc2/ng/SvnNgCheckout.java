package org.tmatesoft.svn.core.internal.wc2.ng;


import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnNgCheckout extends SvnNgAbstractUpdate<Long, SvnCheckout>{

    @Override
    protected Long run(SVNWCContext context) throws SVNException {
        SvnTarget source = getOperation().getSource();
        int targetWorkingCopyFormat = getOperation().getTargetWorkingCopyFormat();
        return checkout(source.getURL(), getFirstTarget(), source.getResolvedPegRevision(), getOperation().getRevision(), getOperation().getDepth(), getOperation().isIgnoreExternals(), getOperation().isAllowUnversionedObstructions(), getOperation().isSleepForTimestamp(), targetWorkingCopyFormat < 0 ? ISVNWCDb.WC_FORMAT_18 : targetWorkingCopyFormat);
    }

}
