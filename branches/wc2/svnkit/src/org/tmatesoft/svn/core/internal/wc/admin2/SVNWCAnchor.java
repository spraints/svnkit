/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin2;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNWCAnchor {
    
    private SVNWCAccess2 myAnchor;
    private SVNWCAccess2 myTarget;
    private String myTargetName;
    
    public SVNWCAnchor(SVNWCAccess2 anchor, SVNWCAccess2 target, String targetName) {
        myAnchor = anchor;
        myTarget = target;
        myTargetName = targetName; 
    }

    public SVNWCAccess2 getAnchor() {
        return myAnchor;
    }
    
    public SVNWCAccess2 getTarget() {
        return myTarget;
    }
    
    public String getTargetName() {
        return myTargetName;
    }
}