package org.tmatesoft.svn.core.wc2;

import java.util.ArrayList;
import java.util.Collection;

public class SvnRemoteCopy extends AbstractSvnCommit {
    
    private boolean move;
    private boolean makeParents;
    private boolean failWhenDstExists;
    
    private Collection<SvnCopySource> sources;

    protected SvnRemoteCopy(SvnOperationFactory factory) {
        super(factory);
        sources = new ArrayList<SvnCopySource>();
    }

    public boolean isMove() {
        return move;
    }

    public void setMove(boolean move) {
        this.move = move;
    }

    public boolean isMakeParents() {
        return makeParents;
    }

    public void setMakeParents(boolean makeParents) {
        this.makeParents = makeParents;
    }

    public Collection<SvnCopySource> getSources() {
        return sources;
    }

    public void addCopySource(SvnCopySource source) {
        if (source != null) {
            this.sources.add(source);
        }
    }

    public boolean isFailWhenDstExists() {
        return failWhenDstExists;
    }

    public void setFailWhenDstExists(boolean failWhenDstExists) {
        this.failWhenDstExists = failWhenDstExists;
    }
}