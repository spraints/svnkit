/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core;

/**
 * @author TMate Software Ltd.
 */
public class SVNWorkspaceAdapter implements ISVNWorkspaceListener {

    public void updated(String path, int contentsStatus, int propertiesStatus, long revision) {
    }

    public void committed(String path, int kind) {
    }

    public void modified(String path, int kind) {
    }
}