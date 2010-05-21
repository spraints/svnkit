/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCSchedule;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingCopyDB17;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCContext {

    private SVNWorkingCopyDB17 db;
    private boolean closeDb;

    public void close() throws SVNException {
        if (closeDb) {
            db.closeDB();
        }
    }

    public SVNWCContext() {
        this.db = new SVNWorkingCopyDB17();
        this.closeDb = true;
    }

    public SVNWCContext(SVNWorkingCopyDB17 db) {
        this.db = db;
        this.closeDb = false;
    }

    public SVNNodeKind getNodeKind(String absPath, boolean showHidden) throws SVNException {
        return null;
    }

    public boolean isNodeAdded(String dirAbsPath) {
        return false;
    }

    public boolean isNodeReplaced(String dirAbsPath) {
        return false;
    }

    public long getRevisionNumber(SVNRevision revision, SVNRepository repository, File path) {
        return 0;
    }

    public void checkCancelled() {
    }

    public List getChildNodes(String localAbsPath) {
        return null;
    }

    public SVNEntryInfo getEntry(String localAbsPath, boolean allow_unversioned, SVNNodeKind kind, boolean need_parent_stub) throws SVNException {
        return null;
    }

    public List readConfilctVictims(String localAbsPath) {
        return null;
    }

    public SVNTreeConflictDescription readTreeConflict(String localAbsPath) {
        return null;
    }

    public boolean isNodeHidden(String localAbsPath) {
        return false;
    }

    public List collectIgnorePatterns(String localAbsPath, Collection ignorePatterns) {
        return null;
    }

    public SVNStatus assembleStatus(File path, SVNEntryInfo entry, SVNEntryInfo parentEntry, SVNNodeKind pathKind, boolean pathSpecial, boolean getAll, boolean isIgnored,
            SVNLock repositoryLock, SVNURL repositoryRoot, SVNWCContext wCContext) throws SVNException {

        boolean locked_p = false;

        /* Defaults for two main variables. */
        SVNStatusType final_text_status = SVNStatusType.STATUS_NORMAL;
        SVNStatusType final_prop_status = SVNStatusType.STATUS_NONE;
        /* And some intermediate results */
        SVNStatusType pristine_text_status = SVNStatusType.STATUS_NONE;
        SVNStatusType pristine_prop_status = SVNStatusType.STATUS_NONE;

        assert(entry != null);

        /* Find out whether the path is a tree conflict victim.
         * This function will set tree_conflict to NULL if the path
         * is not a victim. */
        SVNTreeConflictDescription tree_conflict = db.readTreeConflict(path);
        SVNEntryInfo info = db.readInfo(path, true, true, true, false, false, true, true,
                false, true, true, true, false);

        SVNURL url = getNodeUrl(path);
        boolean file_external_p = isFileExternal(path);

        /* File externals are switched files, but they are not shown as
            such.  To be switched it must have both an URL and a parent with
            an URL, at the very least. */
        boolean switched_p = ! file_external_p ? isSwitched(path) : false;

        /* Examine whether our directory metadata is present, and compensate
           if it is missing.

           There are a several kinds of obstruction that we detect here:

           - versioned subdir is missing
           - the versioned subdir's admin area is missing
           - the versioned subdir has been replaced with a file/symlink

           Net result: the target is obstructed and the metadata is unavailable.

           Note: wc_db can also detect a versioned file that has been replaced
           with a versioned subdir (moved from somewhere). We don't look for
           that right away because the file's metadata is still present, so we
           can examine properties and conflicts and whatnot.

           ### note that most obstruction concepts disappear in single-db mode
        */
        if (info.getWCDBKind() == SVNWCDbKind.DIR)
          {
            if (info.getWCDBStatus() == SVNWCDbStatus.INCOMPLETE)
              {
                /* Highest precedence.  */
                final_text_status = SVNStatusType.STATUS_INCOMPLETE;
              }
            else if (info.getWCDBStatus() == SVNWCDbStatus.OBSTRUCTED_DELETE)
              {
                /* Deleted directories are never reported as missing.  */
                if (pathKind == SVNNodeKind.NONE)
                  final_text_status = SVNStatusType.STATUS_DELETED;
                else
                  final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
              }
            else if (info.getWCDBStatus() == SVNWCDbStatus.OBSTRUCTED
                     || info.getWCDBStatus() == SVNWCDbStatus.OBSTRUCTED_ADD)
              {
                /* A present or added directory should be on disk, so it is
                   reported missing or obstructed.  */
                if (pathKind == SVNNodeKind.NONE)
                  final_text_status = SVNStatusType.STATUS_MISSING;
                else
                  final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
              }
          }

        /* If FINAL_TEXT_STATUS is still normal, after the above checks, then
           we should proceed to refine the status.

           If it was changed, then the subdir is incomplete or missing/obstructed.
           It means that no further information is available, and we should skip
           all this work.  */
        SVNVersionedProperties pristineProperties = null;
        SVNVersionedProperties actualProperties =null;
        if (final_text_status == SVNStatusType.STATUS_NORMAL)
          {
            boolean has_props;
            boolean prop_modified_p = false;
            boolean text_modified_p = false;
            boolean wc_special;

            /* Implement predecence rules: */

            /* 1. Set the two main variables to "discovered" values first (M, C).
                  Together, these two stati are of lowest precedence, and C has
                  precedence over M. */

            /* Does the entry have props? */
            pristineProperties = db.readPristineProperties(path);
            actualProperties = db.readProperties(path);
            has_props = ((pristineProperties != null && !pristineProperties.isEmpty())
                            || (actualProperties != null && !actualProperties.isEmpty()));
            if (has_props) {
              final_prop_status = SVNStatusType.STATUS_NORMAL;
              /* If the entry has a property file, see if it has local changes. */
              prop_modified_p = isPropertiesDiff(pristineProperties,actualProperties);
            }

            /* Record actual property status */
            pristine_prop_status = prop_modified_p ? SVNStatusType.STATUS_MODIFIED
                                                   : SVNStatusType.STATUS_NORMAL;

            if (has_props) {
                wc_special = isSpecial(path);
            } else {
              wc_special = false;
            }

            /* If the entry is a file, check for textual modifications */
            if ((info.getWCDBKind() == SVNWCDbKind.FILE) && (wc_special == pathSpecial)){

                text_modified_p = isTextModified(path, false, true);
                /* Record actual text status */
                pristine_text_status = text_modified_p ? SVNStatusType.STATUS_MODIFIED
                        : SVNStatusType.STATUS_NORMAL;
            }

            if (text_modified_p)
              final_text_status = SVNStatusType.STATUS_MODIFIED;

            if (prop_modified_p)
              final_prop_status = SVNStatusType.STATUS_MODIFIED;

            if (entry.getPropertyRejectFilePath()!=null || entry.getConflictOld()!=null ||
                entry.getConflictNew()!=null || entry.getConflictWorking()!=null)
              {
                boolean[] text_conflict_p = {false};
                boolean[] prop_conflict_p = {false};

                /* The entry says there was a conflict, but the user might have
                marked it as resolved by deleting the artifact files, so check
                for that. */
                getIsConflicted(path, text_conflict_p, prop_conflict_p, null);

                if (text_conflict_p[0])
                  final_text_status = SVNStatusType.STATUS_CONFLICTED;
                if (prop_conflict_p[0])
                  final_prop_status = SVNStatusType.STATUS_CONFLICTED;
              }

            /* 2. Possibly overwrite the text_status variable with "scheduled"
                  states from the entry (A, D, R).  As a group, these states are
                  of medium precedence.  They also override any C or M that may
                  be in the prop_status field at this point, although they do not
                  override a C text status.*/

            /* ### db_status, base_shadowed, and fetching base_status can
               ### fully replace entry->schedule here.  */

            if (entry.getSchedule() == SVNWCSchedule.ADD
                && final_text_status != SVNStatusType.STATUS_CONFLICTED)
              {
                final_text_status = SVNStatusType.STATUS_ADDED;
                final_prop_status = SVNStatusType.STATUS_NONE;
              }

            else if (entry.getSchedule() == SVNWCSchedule.REPLACE
                     && final_text_status != SVNStatusType.STATUS_CONFLICTED)
              {
                final_text_status = SVNStatusType.STATUS_REPLACED;
                final_prop_status = SVNStatusType.STATUS_NONE;
              }

            else if (entry.getSchedule() == SVNWCSchedule.DELETE
                     && final_text_status != SVNStatusType.STATUS_CONFLICTED)
              {
                final_text_status = SVNStatusType.STATUS_DELETED;
                final_prop_status = SVNStatusType.STATUS_NONE;
              }


            /* 3. Highest precedence:

                  a. check to see if file or dir is just missing, or
                     incomplete.  This overrides every possible state
                     *except* deletion.  (If something is deleted or
                     scheduled for it, we don't care if the working file
                     exists.)

                  b. check to see if the file or dir is present in the
                     file system as the same kind it was versioned as.

               4. Check for locked directory (only for directories). */

            if (entry.isIncomplete()
                && (final_text_status != SVNStatusType.STATUS_DELETED)
                && (final_text_status != SVNStatusType.STATUS_ADDED))
              {
                final_text_status = SVNStatusType.STATUS_INCOMPLETE;
              }
            else if (pathKind == SVNNodeKind.NONE)
              {
                if (final_text_status != SVNStatusType.STATUS_DELETED)
                  final_text_status = SVNStatusType.STATUS_MISSING;
              }
            /* ### We can do this db_kind to node_kind translation since the cases
             * where db_kind would have been unknown are treated as unversioned
             * paths and thus have already returned. */
            else if (pathKind != (info.getWCDBKind() == SVNWCDbKind.DIR ?
                                              SVNNodeKind.DIR : SVNNodeKind.FILE)) {
              final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
            }

            else if ( wc_special != pathSpecial) {
              final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
            }

            if (pathKind == SVNNodeKind.DIR && info.getWCDBKind() == SVNWCDbKind.DIR) {
                locked_p = db.isWCLocked(path);
            }
          }

        /* 5. Easy out:  unless we're fetching -every- entry, don't bother
           to allocate a struct for an uninteresting entry. */

        if (! getAll)
          if (((final_text_status == SVNStatusType.STATUS_NONE)
               || (final_text_status == SVNStatusType.STATUS_NORMAL))
              && ((final_prop_status == SVNStatusType.STATUS_NONE)
                  || (final_prop_status == SVNStatusType.STATUS_NORMAL))
              && (! locked_p)
              && (! switched_p)
              && (! file_external_p)
              && ( info.getWCDBLock()==null)
              && ( repositoryLock==null)
              && ( info.getChangeList()==null)
              && ( tree_conflict==null))
            {
              return null;
            }


        /* 6. Build and return a status structure. */

        SVNLock lock = null;
        if(info.getWCDBLock()!=null) {
            final SVNWCDbLock wcdbLock = info.getWCDBLock();
            lock = new SVNLock(path.toString(), wcdbLock.getToken(), wcdbLock.getOwner(),
                    wcdbLock.getComment(), wcdbLock.getDate(), null);
        }

        SVNStatus status = new SVNStatus(url, path, info.getNodeKind(),
                SVNRevision.create(info.getRevision()), SVNRevision.create(info.getCommittedRevision()),
                info.getCommittedDate(), info.getCommittedAuthor(),
                final_text_status,  final_prop_status, SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                locked_p, entry.isCopied(), switched_p, file_external_p,
                new File(info.getConflictNew()), new File(info.getConflictOld()), new File(info.getConflictWorking()), new File(info.getPropertyRejectFilePath()),
                info.getCopyFromURL(), SVNRevision.create(info.getCopyFromRevision()),
                repositoryLock, lock, actualProperties.asMap().asMap(), info.getChangeList(), db.WC_FORMAT_17, tree_conflict);
        status.setEntry(new SVNEntry17(entry));

        return status;

    }

    private void getIsConflicted(File path, boolean[] textConflictP, boolean[] propConflictP, Object object) {
    }

    private boolean isSpecial(File path) {
        final String property = getProperty(path, SVNProperty.SPECIAL);
        return property != null;
    }

    private boolean isPropertiesDiff(SVNVersionedProperties pristine, SVNVersionedProperties actual) throws SVNException {
        if (pristine == null && actual == null) {
            return false;
        }
        if (pristine == null || actual == null) {
            return true;
        }
        final SVNVersionedProperties diff = actual.compareTo(pristine);
        return diff != null && !diff.isEmpty();
    }

    private boolean isTextModified(File path, boolean force_comparison, boolean compare_textbases) throws SVNException {
        return false;
    }

    private boolean isSwitched(File path) {
        // TODO
        return false;
    }

    private boolean isFileExternal(File path) {
        // TODO
        return false;
    }

    private SVNURL getNodeUrl(File path) {
        return null;
    }

    public boolean isAdminDirectory(String name) {
        return name != null && (SVNFileUtil.isWindows) ? SVNFileUtil.getAdminDirectoryName().equalsIgnoreCase(name) : SVNFileUtil.getAdminDirectoryName().equals(name);
    }

    public String getProperty(File path, String propertyName) {
        return null;
    }

    public SVNStatus assembleUnversioned(String localAbsPath, SVNNodeKind pathKind, boolean isIgnored) {
        return null;
    }

}
