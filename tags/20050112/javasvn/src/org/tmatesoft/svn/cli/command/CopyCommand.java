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

package org.tmatesoft.svn.cli.command;

import java.io.IOException;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author TMate Software Ltd.
 */
public class CopyCommand extends SVNCommand {

	public void run(PrintStream out, PrintStream err) throws SVNException {
		if (getCommandLine().hasURLs()) {
			runRemote(out);
		}
		else {
			runLocally(out);
		}
	}

	private void runRemote(PrintStream out) throws SVNException {
        String srcPath = getCommandLine().getURL(0);
        String destPath = getCommandLine().getURL(1);
		String message = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        
        String newPath = PathUtil.tail(destPath);
        newPath = PathUtil.removeLeadingSlash(newPath);
        newPath = PathUtil.decode(newPath);
        String root = PathUtil.removeTail(destPath);
        SVNRepository repository = createRepository(root);

        long revNumber = -1;
        String revStr = (String) getCommandLine().getArgumentValue(SVNArgument.REVISION);
        if (revStr != null) {
            try {
                revNumber = Long.parseLong(revStr);
            } catch (NumberFormatException e) {
                revNumber = -1;
            }
        }
        if (revNumber < 0) {
            revNumber = repository.getLatestRevision();
        }
        
        SVNRepositoryLocation srcLocation = SVNRepositoryLocation.parseURL(srcPath);
        srcPath = srcLocation.getPath();
        srcPath = PathUtil.decode(srcPath);
        if (repository.getRepositoryRoot() == null) {
            repository.testConnection();
        }
        srcPath = srcPath.substring(repository.getRepositoryRoot().length());
        if (!srcPath.startsWith("/")) {
            srcPath += "/";
        }

        ISVNEditor editor = repository.getCommitEditor(message, null);
        try {
            editor.openRoot(-1);
            editor.addDir(newPath, srcPath, revNumber);
            editor.closeDir();
            editor.closeDir();

            SVNCommitInfo info = editor.closeEdit();
            
            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
        } catch (SVNException e) {
            if (editor != null) {
                try {
                    editor.abortEdit();
                } catch (SVNException es) {}
            }
            throw e;
        }
	}

	private void runLocally(final PrintStream out) throws SVNException {
		if (getCommandLine().getPathCount() != 2) {
			throw new SVNException("Please enter SRC and DST path");
		}

		final String absolutePath = getCommandLine().getPathAt(0);
		final ISVNWorkspace workspace = createWorkspace(absolutePath);
		workspace.addWorkspaceListener(new SVNWorkspaceAdapter() {
			public void modified(String path, int kind) {
				try {
					path = convertPath(absolutePath, workspace, path);
				}
				catch (IOException e) {
				}

				final String kindString = (kind == SVNStatus.ADDED ? "A" : "D");

				DebugLog.log(kindString + "  " + path);
				out.println(kindString + "  " + path);
			}
		});

		final String srcPath = SVNUtil.getWorkspacePath(workspace, getCommandLine().getPathAt(0));
		final String dstTempPath = SVNUtil.getWorkspacePath(workspace, getCommandLine().getPathAt(1));
		final SVNStatus status = workspace.status(dstTempPath, false);
		final String dstPath = status != null && status.isDirectory() ? PathUtil.append(dstTempPath, PathUtil.tail(srcPath)) : dstTempPath;
		workspace.copy(srcPath, dstPath, false);
	}
}