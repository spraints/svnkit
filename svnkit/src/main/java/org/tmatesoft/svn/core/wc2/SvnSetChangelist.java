package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnSetChangelist extends SvnOperation<Long> {

    private String changelistName;
    private boolean remove;
    private SVNDepth depth;
    private String[] changelists;

    protected SvnSetChangelist(SvnOperationFactory factory) {
        super(factory);
    }
    
    public String getChangelistName() {
        return changelistName;
    }

    public void setChangelistName(String changelistName) {
        this.changelistName = changelistName;
    }
    
    public SVNDepth getDepth() {
        return depth;
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }
    
    public String[] getChangelists() {
        return changelists;
    }

    public void setChangelists(String[] changelists) {
        this.changelists = changelists;
    }

    public boolean isRemove() {
        return remove;
    }

    public void setRemove(boolean remove) {
        this.remove = remove;
    }
    
    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        
        if ("".equals(getChangelistName())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "Target changelist name must not be empty");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (hasRemoteTargets()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' is not a local path", getFirstTarget().getURL());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }

}
