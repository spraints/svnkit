package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ConflictInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.MergeInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.MergePropertiesInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCNodeReposInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.LocationsInfo;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgMergeCallback implements ISvnDiffCallback {
    
    private boolean dryRun;
    private File targetAbsPath;
    private SVNWCContext context;
    
    private Collection<File> dryRunDeletions;
    private Collection<File> dryRunAdditions;
    
    private Collection<File> pathsWithDeletedMergeInfo;
    private Collection<File> pathsWithAddedMergeInfo;

    private boolean recordOnly;
    private boolean sourcesAncestral;
    
    private long mergeSource2Rev;
    private SVNURL mergeSource2Url;
    private long mergeSource1Rev;
    private SVNURL mergeSource1Url;
    
    private boolean sameRepos;
    private boolean ignoreAncestry;
    private boolean mergeinfoCapable;
    private boolean reintegrateMerge;
    
    private SVNRepository repos1;
    private SVNRepository repos2;
    private SVNDiffOptions diffOptions;
    private Collection<File> conflictedPaths;
    private File addedPath;
    private SVNURL reposRootUrl;
    private boolean force;
    
    public Collection<File> getConflictedPaths() {
        return conflictedPaths;
    }

    public void fileOpened(SvnDiffCallbackResult result, File path, long revision) throws SVNException {
        // do nothing
    }

    public void fileChanged(SvnDiffCallbackResult result, File path,
            File tmpFile1, File tmpFile2, long rev1, long rev2,
            String mimetype1, String mimeType2, SVNProperties propChanges,
            SVNProperties originalProperties) throws SVNException {
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            result.contentState = os.obstructionState;
            if (os.obstructionState == SVNStatusType.MISSING) {
                result.propState = SVNStatusType.MISSING;
            }
            return;
        }
        SVNNodeKind wcKind = os.kind;
        boolean isDeleted = os.deleted;
        
        if (wcKind != SVNNodeKind.FILE  || isDeleted) {
            if (wcKind == SVNNodeKind.NONE) {
                SVNDepth parentDepth = context.getNodeDepth(SVNFileUtil.getParentFile(path));
                if (parentDepth != SVNDepth.UNKNOWN && parentDepth.compareTo(SVNDepth.FILES) < 0) {
                    result.contentState = SVNStatusType.MISSING;
                    result.propState = SVNStatusType.MISSING;
                    return;
                }
            }

            treeConflict(path, SVNNodeKind.FILE, SVNConflictAction.EDIT, SVNConflictReason.MISSING);
            
            result.treeConflicted = true;
            result.contentState = SVNStatusType.MISSING;
            result.propState = SVNStatusType.MISSING;
            return;
        }
        
        if (!propChanges.isEmpty()) {
            MergePropertiesInfo mergeOutcome = mergePropChanges(path, propChanges, originalProperties);
            result.propState = mergeOutcome.mergeOutcome;
            if (mergeOutcome.treeConflicted) {
                result.treeConflicted = true;
                return;
            }
        } else {
            result.propState = SVNStatusType.UNCHANGED;
        }
        
        if (recordOnly) {
            result.contentState = SVNStatusType.UNCHANGED;
            return;
        }
        
        if (tmpFile1 != null) {
            boolean hasLocalMods = context.isTextModified(path, false);
            String targetLabel = ".working";
            String leftLabel = ".merge-left.r" + rev1;
            String rightLabel = ".merge-right.r" + rev2;
            SVNConflictVersion[] cvs = makeConflictVersions(path, SVNNodeKind.FILE);
            
            MergeInfo mergeOutcome = context.mergeText(tmpFile1, tmpFile2, path, leftLabel, rightLabel, targetLabel, cvs[0], cvs[1], dryRun, diffOptions, propChanges);
            if (mergeOutcome.mergeOutcome == SVNStatusType.CONFLICTED) {
                result.contentState = SVNStatusType.CONFLICTED;
            } else if (hasLocalMods && mergeOutcome.mergeOutcome != SVNStatusType.UNCHANGED) {
                result.contentState = SVNStatusType.MERGED;
            } else if (mergeOutcome.mergeOutcome == SVNStatusType.MERGED) {
                result.contentState = SVNStatusType.CHANGED;
            } else if (mergeOutcome.mergeOutcome == SVNStatusType.NO_MERGE) {
                result.contentState = SVNStatusType.MISSING;
            } else {
                result.contentState = SVNStatusType.UNCHANGED;
            }
        }
    }

    public void fileAdded(SvnDiffCallbackResult result, File path,
            File tmpFile1, File tmpFile2, long rev1, long rev2,
            String mimetype1, String mimeType2, File copyFromPath,
            long copyFromRevision, SVNProperties propChanges,
            SVNProperties originalProperties) throws SVNException {
        if (recordOnly) {
            result.contentState = SVNStatusType.UNCHANGED;
            result.propState = SVNStatusType.UNCHANGED;
            return;
        }
        result.propState = SVNStatusType.UNKNOWN;
        SVNProperties fileProps = new SVNProperties(originalProperties);
        for (String propName : propChanges.nameSet()) {
            if (SVNProperty.isWorkingCopyProperty(propName)) {
                continue;
            }
            if (!sameRepos && !SVNProperty.isRegularProperty(propName)) {
                continue;
            }
            if (!sameRepos && SVNProperty.MERGE_INFO.equals(propName)) {
                continue;
            }
            if (propChanges.getSVNPropertyValue(propName) != null) {
                fileProps.put(propName, propChanges.getSVNPropertyValue(propName));
            } else {
                fileProps.remove(propName);
            }
        }
        
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            if (dryRun && addedPath != null && SVNWCUtils.isChild(addedPath, path)) {
                result.contentState = SVNStatusType.CHANGED;
                if (!fileProps.isEmpty()) {
                    result.propState = SVNStatusType.CHANGED;
                }
            } else {
                result.contentState = os.obstructionState;
            }
            return;
        }
        
        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
        
        if (kind == SVNNodeKind.NONE) {
            if (!dryRun) {
                SVNURL copyFromUrl = null;
                long copyFromRev = -1;
                InputStream newBaseContents = null;
                InputStream newContents = null;
                SVNProperties newBaseProps, newProps;
                try {
                    if (sameRepos) {
                        String child = SVNWCUtils.getPathAsChild(targetAbsPath, path);
                        if (child != null) {
                            copyFromUrl = mergeSource2Url.appendPath(child, false);
                        } else {
                            copyFromUrl = mergeSource2Url;
                        }
                        copyFromRev = rev2;
                        checkReposMatch(path, copyFromUrl);
                        newBaseContents = SVNFileUtil.openFileForReading(tmpFile2);
                        newContents = null;
                        newBaseProps = fileProps;
                        newProps = null;
                    } else {
                        newBaseProps = new SVNProperties();
                        newProps = fileProps;
                        newBaseContents = SVNFileUtil.DUMMY_IN;
                        newContents = SVNFileUtil.openFileForReading(tmpFile2);
                    }
                    SVNTreeConflictDescription tc = context.getTreeConflict(path);
                    if (tc != null) {
                        treeConflictOnAdd(path, SVNNodeKind.FILE, SVNConflictAction.ADD, SVNConflictReason.ADDED);
                        result.treeConflicted = true;
                    } else {
                        SvnNgReposToWcCopy.addFileToWc(context, path, newBaseContents, newContents, newBaseProps, newProps, copyFromUrl, copyFromRev);
                    }
                } finally {
                    SVNFileUtil.closeFile(newBaseContents);
                    SVNFileUtil.closeFile(newContents);
                }
            }
            result.contentState = SVNStatusType.CHANGED;
            if (!fileProps.isEmpty()) {
                result.propState = SVNStatusType.CHANGED;
            }
        } else if (kind == SVNNodeKind.DIR) {
            treeConflictOnAdd(path, SVNNodeKind.FILE, SVNConflictAction.ADD, SVNConflictReason.OBSTRUCTED);
            result.treeConflicted = true;
            SVNNodeKind wcKind = context.readKind(path, false);
            if (wcKind != SVNNodeKind.NONE && isDryRunDeletion(path)) {
                result.contentState = SVNStatusType.CHANGED;
            } else {
                result.contentState = SVNStatusType.OBSTRUCTED;
            }
        } else if (kind == SVNNodeKind.FILE) {
            if (isDryRunDeletion(path)) {
                result.contentState = SVNStatusType.CHANGED;
            } else {
                treeConflictOnAdd(path, SVNNodeKind.FILE, SVNConflictAction.ADD, SVNConflictReason.ADDED);
                result.treeConflicted = true;
            }            
        } else {
            result.contentState = SVNStatusType.UNKNOWN;
        }
    }

    public void fileDeleted(SvnDiffCallbackResult result, File path,
            File tmpFile1, File tmpFile2, String mimetype1, String mimeType2,
            SVNProperties originalProperties) throws SVNException {
        
        if (dryRun) {
            dryRunDeletions.add(path);
        }
        if (recordOnly) {
            result.contentState = SVNStatusType.UNCHANGED;
            return;
        }
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            result.contentState = os.obstructionState;
            return;
        }
        
        SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
        if (kind == SVNNodeKind.FILE) {
            boolean same = compareFiles(tmpFile1, originalProperties, path);
            if (same || force || recordOnly) {
                if (!dryRun) {
                    SvnNgRemove.delete(context, path, false, true, null);
                }
                result.contentState = SVNStatusType.CHANGED;
            } else {
                treeConflict(path, SVNNodeKind.FILE, SVNConflictAction.DELETE, SVNConflictReason.EDITED);
                result.treeConflicted = true;
                result.contentState = SVNStatusType.OBSTRUCTED;
            }
        } else if (kind == SVNNodeKind.DIR) {
            treeConflict(path, SVNNodeKind.FILE, SVNConflictAction.DELETE, SVNConflictReason.OBSTRUCTED);
            result.treeConflicted = true;
            result.contentState = SVNStatusType.OBSTRUCTED;
        } else if (kind == SVNNodeKind.NONE) {
            treeConflict(path, SVNNodeKind.FILE, SVNConflictAction.DELETE, SVNConflictReason.DELETED);
            result.treeConflicted = true;
            result.contentState = SVNStatusType.MISSING;
        } else {
            result.contentState = SVNStatusType.UNKNOWN;
        }
    }

    public void dirDeleted(SvnDiffCallbackResult result, File path) throws SVNException {
        if (recordOnly) {
            result.contentState = SVNStatusType.UNCHANGED;
            return;
        }
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        boolean isVersioned = os.kind == SVNNodeKind.DIR || os.kind == SVNNodeKind.FILE;
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            result.contentState = os.obstructionState;
            return;
        }
        if (os.deleted) {
            os.kind = SVNNodeKind.NONE;
        }
        if (dryRun) {
            if (dryRunDeletions == null) {
                dryRunDeletions = new HashSet<File>();
            }
            dryRunDeletions.add(path);
        }
        if (os.kind == SVNNodeKind.DIR) {
            if (isVersioned && !os.deleted) {
                try {
                    if (!force) {
                        // TODO check it could be deleted.
                    }
                    if (!dryRun) {
                        SvnNgRemove.delete(context, path, false, false, null);
                    }
                    result.contentState = SVNStatusType.CHANGED;
                } catch (SVNException e) {
                    treeConflict(path, SVNNodeKind.DIR, SVNConflictAction.DELETE, SVNConflictReason.EDITED);
                    result.treeConflicted = true;
                    result.contentState = SVNStatusType.CONFLICTED;
                }
            } else {
                treeConflict(path, SVNNodeKind.DIR, SVNConflictAction.DELETE, SVNConflictReason.DELETED);
                result.treeConflicted = true;                
            }
        } else if (os.kind == SVNNodeKind.FILE) {
            result.contentState = SVNStatusType.OBSTRUCTED;
        } else if (os.kind == SVNNodeKind.NONE) {
            treeConflict(path, SVNNodeKind.DIR, SVNConflictAction.DELETE, SVNConflictReason.DELETED);
            result.treeConflicted = true;
            result.contentState = SVNStatusType.MISSING;
        } else {
            result.contentState = SVNStatusType.UNKNOWN;
        }
    }

    public void dirOpened(SvnDiffCallbackResult result, File path, long revision) throws SVNException {
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            result.skipChildren = true;
            return;
        }
        if (os.kind != SVNNodeKind.DIR || os.deleted) {
            if (os.kind == SVNNodeKind.NONE) {
                SVNDepth parentDepth = context.getNodeDepth(SVNFileUtil.getParentFile(path));
                if (parentDepth != SVNDepth.UNKNOWN && parentDepth.compareTo(SVNDepth.IMMEDIATES) < 0) {
                    result.skipChildren = true;
                    return;
                }
            }
            if (os.kind == SVNNodeKind.FILE) {
                treeConflict(path, SVNNodeKind.DIR, SVNConflictAction.EDIT, SVNConflictReason.REPLACED);
                result.treeConflicted = true;
            } else if (os.deleted || os.kind == SVNNodeKind.NONE) {
                treeConflict(path, SVNNodeKind.DIR, SVNConflictAction.EDIT, SVNConflictReason.DELETED);
                result.treeConflicted = true;
            }
        }
    }

    public void dirAdded(SvnDiffCallbackResult result, File path, long revision, String copyFromPath, long copyFromRevision) throws SVNException {
        if (recordOnly) {
            result.contentState = SVNStatusType.UNCHANGED;
            return;
        }
        File parentPath = SVNFileUtil.getParentFile(path);
        String child = SVNWCUtils.getPathAsChild(targetAbsPath, path);
        SVNURL copyFromUrl = null;
        long copyFromRev = -1;
        if (sameRepos) {
            copyFromUrl = mergeSource2Url.appendPath(child, false);
            copyFromRev = revision;
            checkReposMatch(parentPath, copyFromUrl);
        }
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        boolean isVersioned = os.kind == SVNNodeKind.DIR || os.kind == SVNNodeKind.FILE;
        if (os.obstructionState == SVNStatusType.OBSTRUCTED && (os.deleted || os.kind == SVNNodeKind.NONE)) {
            SVNFileType diskKind = SVNFileType.getType(path);
            if (diskKind == SVNFileType.DIRECTORY) {
                os.obstructionState = SVNStatusType.INAPPLICABLE;
                os.kind = SVNNodeKind.DIR;
            }
        }
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            if (dryRun && addedPath != null && SVNWCUtils.isChild(addedPath, path)) {
                result.contentState = SVNStatusType.CHANGED;
            } else {
                result.contentState = os.obstructionState;
            }
            return;
        }
        if (os.deleted) {
            os.kind = SVNNodeKind.NONE;
        }
        
        if (os.kind == SVNNodeKind.NONE) {
            if (dryRun) {
                if (dryRunAdditions == null) {
                    dryRunAdditions = new HashSet<File>();
                }
                dryRunAdditions.add(path);
            } else {
                path.mkdir();
                if (copyFromUrl != null) {
                    SVNWCNodeReposInfo reposInfo = context.getNodeReposInfo(parentPath);
                    File reposRelPath = new File(SVNURLUtil.getRelativeURL(reposInfo.reposRootUrl, copyFromUrl));
                    context.getDb().opCopyDir(path, new SVNProperties(), 
                            copyFromRev, new SVNDate(0, 0), null, 
                            reposRelPath, 
                            reposInfo.reposRootUrl, 
                            reposInfo.reposUuid, 
                            copyFromRev, 
                            null, 
                            SVNDepth.INFINITY, 
                            null, 
                            null);
                } else {
                    context.getDb().opAddDirectory(path, null);
                }
            }
            result.contentState = SVNStatusType.CHANGED;
        } else if (os.kind == SVNNodeKind.DIR) {
            if (!isVersioned || os.deleted) {
                if (!dryRun) {
                    if (copyFromUrl != null) {
                        SVNWCNodeReposInfo reposInfo = context.getNodeReposInfo(parentPath);
                        File reposRelPath = new File(SVNURLUtil.getRelativeURL(reposInfo.reposRootUrl, copyFromUrl));
                        context.getDb().opCopyDir(path, new SVNProperties(), 
                                copyFromRev, new SVNDate(0, 0), null, 
                                reposRelPath, 
                                reposInfo.reposRootUrl, 
                                reposInfo.reposUuid, 
                                copyFromRev, 
                                null, 
                                SVNDepth.INFINITY, 
                                null, 
                                null);
                    } else {
                        context.getDb().opAddDirectory(path, null);
                    }
                } else {
                    addedPath = path;
                }
                result.contentState = SVNStatusType.CHANGED;
            } else {
                if (isDryRunDeletion(path)) {
                    result.contentState = SVNStatusType.CHANGED;
                } else {
                    treeConflictOnAdd(path, SVNNodeKind.DIR, SVNConflictAction.ADD, SVNConflictReason.ADDED);
                    result.treeConflicted = true;
                    result.contentState = SVNStatusType.OBSTRUCTED;
                }
            }
        } else if (os.kind == SVNNodeKind.FILE) {
            if (dryRun) {
                addedPath = null;
            }
            if (isVersioned && isDryRunDeletion(path)) {
                result.contentState = SVNStatusType.CHANGED;
            } else {
                treeConflictOnAdd(path, SVNNodeKind.DIR, SVNConflictAction.ADD, SVNConflictReason.OBSTRUCTED);
                result.treeConflicted = true;
                result.contentState = SVNStatusType.OBSTRUCTED;
            }
        } else {
            if (dryRun) {
                addedPath = null;
            }
            result.contentState = SVNStatusType.UNKNOWN;
        }
    }

    public void dirPropsChanged(SvnDiffCallbackResult result, File path, boolean isAdded, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException {
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            result.contentState = os.obstructionState;
            return;
        }
        if (isAdded && dryRun && isDryRunAddition(path)) {
            return;
        }
        MergePropertiesInfo info = mergePropChanges(path, propChanges, originalProperties);
        result.treeConflicted = info.treeConflicted;
        result.contentState = info.mergeOutcome;
    }

    public void dirClosed(SvnDiffCallbackResult result, File path, boolean isAdded) throws SVNException {
        if (dryRun && dryRunDeletions != null) {
            dryRunDeletions.clear();
        }
    }
    
    private boolean isDryRunAddition(File path) {
        return dryRun && dryRunAdditions != null && dryRunAdditions.contains(path);
    }
    
    private boolean isDryRunDeletion(File path) {
        return dryRun && dryRunDeletions != null && dryRunDeletions.contains(path);
    }
    
    private void checkReposMatch(File path, SVNURL url) throws SVNException {
        if (!SVNURLUtil.isAncestor(reposRootUrl, url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Url ''{0}'' of ''{1}'' is not in repository ''{2}''", url, path, reposRootUrl);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    private MergePropertiesInfo mergePropChanges(File localAbsPath, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException {
        SVNProperties props = new SVNProperties();
        SvnNgPropertiesManager.categorizeProperties(propChanges, props, null, null);
        
        if (recordOnly && !props.isEmpty()) {
            SVNProperties mergeinfoProps = new SVNProperties();
            if (props.containsName(SVNProperty.MERGE_INFO)) {
                mergeinfoProps.put(SVNProperty.MERGE_INFO, props.getStringValue(SVNProperty.MERGE_INFO));
            }
            props = mergeinfoProps;
        }
        
        MergePropertiesInfo mergeOutcome = null;
        if (!props.isEmpty()) {
            if (mergeSource1Rev < mergeSource2Rev || !sourcesAncestral) {
                props = filterSelfReferentialMergeInfo(props, localAbsPath, isHonorMergeInfo(), sameRepos, reintegrateMerge, repos2);
            }
            SVNException err = null;
            try {
                mergeOutcome = context.mergeProperties(localAbsPath, null, null, originalProperties, propChanges, dryRun);
            } catch (SVNException e) {
                err = e;
            }
            
            if (!dryRun) {
                for (String propName : props.nameSet()) {
                    if (!SVNProperty.MERGE_INFO.equals(propName)) {
                        continue;
                    }
                    SVNProperties pristineProps = context.getPristineProps(localAbsPath);
                    boolean hasPristineMergeInfo = false;
                    if (pristineProps != null && pristineProps.containsName(SVNProperty.MERGE_INFO)) {
                        hasPristineMergeInfo = true;
                    }
                    if (!hasPristineMergeInfo && props.getSVNPropertyValue(propName) != null) {
                        if (pathsWithAddedMergeInfo == null) {
                            pathsWithAddedMergeInfo = new HashSet<File>();
                        }
                        pathsWithAddedMergeInfo.add(localAbsPath);
                    } else if (hasPristineMergeInfo && props.getSVNPropertyValue(propName) == null) {
                        if (pathsWithDeletedMergeInfo == null) {
                            pathsWithDeletedMergeInfo = new HashSet<File>();
                        }
                        pathsWithDeletedMergeInfo.add(localAbsPath);
                    }
                }
            }
            
            if (err != null && err.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND ||
                    err.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_UNEXPECTED_STATUS) {
                if (mergeOutcome != null) {
                    mergeOutcome.mergeOutcome = SVNStatusType.MISSING;
                    mergeOutcome.treeConflicted = true;
                }
            } else if (err != null) {
                throw err;
            }
        }
        return mergeOutcome;
    }
    
    private SVNProperties filterSelfReferentialMergeInfo(SVNProperties props, File localAbsPath, boolean honorMergeInfo, boolean sameRepos,
            boolean reintegrateMerge, SVNRepository repos) throws SVNException {
        if (!sameRepos) {
            return omitMergeInfoChanges(props);
        }
        if (!honorMergeInfo && !reintegrateMerge) {
            return props;
        }
        
        boolean isAdded = context.isNodeAdded(localAbsPath);
        if (isAdded) {
            return props;
        }
        long baseRevision = context.getNodeBaseRev(localAbsPath);        
        SVNProperties adjustedProps = new SVNProperties();
        
        for (String propName : props.nameSet()) {
            if (!SVNProperty.MERGE_INFO.equals(propName) || props.getSVNPropertyValue(propName) == null || "".equals(props.getSVNPropertyValue(propName))) {
                adjustedProps.put(propName, props.getSVNPropertyValue(propName));
                continue;
            }
            SVNURL targetUrl = context.getUrlFromPath(localAbsPath);
            SVNURL oldUrl = repos.getLocation();
            repos.setLocation(targetUrl, false);
            String mi = props.getStringValue(propName);
            
            Map<String, SVNMergeRangeList> mergeinfo = null;
            Map<String, SVNMergeRangeList> filteredYoungerMergeinfo = null;
            Map<String, SVNMergeRangeList> filteredMergeinfo = null;
            
            try {
                mergeinfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mi), null);
            } catch (SVNException e) {
                adjustedProps.put(propName, props.getSVNPropertyValue(propName));
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.MERGE_INFO_PARSE_ERROR) {
                    repos.setLocation(oldUrl, false);
                    continue;
                }
                throw e;
            }
            Map<String, SVNMergeRangeList>[] splitted = splitMergeInfoOnRevision(mergeinfo, baseRevision);
            Map<String, SVNMergeRangeList> youngerMergeInfo = splitted[0];
            mergeinfo = splitted[1];
            
            if (youngerMergeInfo != null) {
                SVNURL mergeSourceRootUrl = repos.getRepositoryRoot(true);
                
                for (Iterator<String> youngerMergeInfoIter = youngerMergeInfo.keySet().iterator(); youngerMergeInfoIter.hasNext();) {
                    String sourcePath = youngerMergeInfoIter.next();
                    SVNMergeRangeList rangeList = (SVNMergeRangeList) youngerMergeInfo.get(sourcePath);
                    SVNMergeRange ranges[] = rangeList.getRanges();
                    List<SVNMergeRange> adjustedRanges = new ArrayList<SVNMergeRange>();
                    
                    SVNURL mergeSourceURL = mergeSourceRootUrl.appendPath(sourcePath, false);
                    for (int i = 0; i < ranges.length; i++) {
                        SVNMergeRange range = ranges[i];
                        Structure<LocationsInfo> locations = null;
                        try {
                            locations = new SvnNgRepositoryAccess(null, context).getLocations(
                                    repos, 
                                    SvnTarget.fromURL(targetUrl), 
                                    SVNRevision.create(baseRevision), 
                                    SVNRevision.create(range.getStartRevision() + 1), 
                                    SVNRevision.UNDEFINED);
                            SVNURL startURL = locations.get(LocationsInfo.startUrl);
                            if (!mergeSourceURL.equals(startURL)) {
                                adjustedRanges.add(range);
                            }
                            locations.release();
                        } catch (SVNException svne) {
                            SVNErrorCode code = svne.getErrorMessage().getErrorCode();
                            if (code == SVNErrorCode.CLIENT_UNRELATED_RESOURCES || 
                                    code == SVNErrorCode.RA_DAV_PATH_NOT_FOUND ||
                                    code == SVNErrorCode.FS_NOT_FOUND ||
                                    code == SVNErrorCode.FS_NO_SUCH_REVISION) {
                                adjustedRanges.add(range);
                            } else {
                                throw svne;
                            }
                        }
                    }

                    if (!adjustedRanges.isEmpty()) {
                        if (filteredYoungerMergeinfo == null) {
                            filteredYoungerMergeinfo = new TreeMap<String, SVNMergeRangeList>();
                        }
                        SVNMergeRangeList adjustedRangeList = SVNMergeRangeList.fromCollection(adjustedRanges); 
                        filteredYoungerMergeinfo.put(sourcePath, adjustedRangeList);
                    }
                }
            }
            if (mergeinfo != null && !mergeinfo.isEmpty()) {
                
                Map<String, SVNMergeRangeList> implicitMergeInfo = 
                        new SvnNgRepositoryAccess(null, context).getHistoryAsMergeInfo(repos2, SvnTarget.fromFile(localAbsPath),  
                                baseRevision, -1);                         
                filteredMergeinfo = SVNMergeInfoUtil.removeMergeInfo(implicitMergeInfo, mergeinfo, true);
            }
            
            if (oldUrl != null) {
                repos.setLocation(oldUrl, false);
            }
            
            if (filteredMergeinfo != null && filteredYoungerMergeinfo != null) {
                filteredMergeinfo = SVNMergeInfoUtil.mergeMergeInfos(filteredMergeinfo, filteredYoungerMergeinfo);
            } else if (filteredYoungerMergeinfo != null) {
                filteredMergeinfo = filteredYoungerMergeinfo;
            }

            if (filteredMergeinfo != null && !filteredMergeinfo.isEmpty()) {
                String filteredMergeInfoStr = SVNMergeInfoUtil.formatMergeInfoToString(filteredMergeinfo, null);
                adjustedProps.put(SVNProperty.MERGE_INFO, filteredMergeInfoStr);
            }
        }
        
        return adjustedProps;
    }

    private Map<String, SVNMergeRangeList>[] splitMergeInfoOnRevision(Map<String, SVNMergeRangeList> mergeinfo, long revision) {
        Map<String, SVNMergeRangeList> youngerMergeinfo = null;
        for (String path : new HashSet<String>(mergeinfo.keySet())) {
            SVNMergeRangeList rl = mergeinfo.get(path);
            for (int i = 0; i < rl.getSize(); i++) {
                SVNMergeRange r = rl.getRanges()[i];
                if (r.getEndRevision() <= revision) {
                    continue;
                } else {
                    SVNMergeRangeList youngerRl = new SVNMergeRangeList(new SVNMergeRange[0]);
                    for (int j = 0; j < rl.getSize(); j++) {
                        SVNMergeRange r2 = rl.getRanges()[j];
                        SVNMergeRange youngerRange = r2.dup();
                        if (i == j && r.getStartRevision() + 1 <= revision) {
                            youngerRange.setStartRevision(revision);
                            r.setEndRevision(revision);
                        }
                        youngerRl.pushRange(youngerRange.getStartRevision(), youngerRange.getEndRevision(), youngerRange.isInheritable());
                    }
                    
                    if (youngerMergeinfo == null) {
                        youngerMergeinfo = new TreeMap<String, SVNMergeRangeList>();
                    }
                    youngerMergeinfo.put(path, youngerRl);
                    mergeinfo = SVNMergeInfoUtil.removeMergeInfo(youngerMergeinfo, mergeinfo, true);
                    break;
                }
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, SVNMergeRangeList>[] result = new Map[2]; 
        result[0] = youngerMergeinfo;
        result[1] = mergeinfo;
        return result;
    }

    private SVNProperties omitMergeInfoChanges(SVNProperties props) {
        SVNProperties result = new SVNProperties();
        for (String name : props.nameSet()) {
            if (SVNProperty.MERGE_INFO.equals(name)) {
                continue;
            }
            SVNPropertyValue pv = props.getSVNPropertyValue(name);
            result.put(name, pv);
        }
        return result;
    }

    private boolean isHonorMergeInfo() {
        return sourcesAncestral && sameRepos && !ignoreAncestry && mergeinfoCapable;
    }
    
    private ObstructionState performObstructionCheck(File localAbsPath, SVNNodeKind expectedKind) throws SVNException {
        ObstructionState result = new ObstructionState();
        result.obstructionState = SVNStatusType.INAPPLICABLE;
        result.kind = SVNNodeKind.NONE;

        if (dryRun) {
            if (isDryRunDeletion(localAbsPath)) {
                result.deleted = true;
                if (expectedKind != SVNNodeKind.UNKNOWN &&
                        expectedKind != SVNNodeKind.NONE) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;
                }
                return result;
            } else if (isDryRunAddition(localAbsPath)) {
                result.added = true;
                result.kind = SVNNodeKind.DIR;
                return result;
            }
        }
        
        boolean checkRoot = !localAbsPath.equals(targetAbsPath);
        checkWcForObstruction(result, localAbsPath, checkRoot);
        if (result.obstructionState == SVNStatusType.INAPPLICABLE &&
                expectedKind != SVNNodeKind.UNKNOWN &&
                result.kind != expectedKind) {
            result.obstructionState = SVNStatusType.OBSTRUCTED;
        }
        return result;
    }
    
    private void checkWcForObstruction(ObstructionState result, File localAbsPath, boolean noWcRootCheck) throws SVNException {
        result.kind = SVNNodeKind.NONE;
        result.obstructionState = SVNStatusType.INAPPLICABLE;
        SVNFileType diskKind = SVNFileType.getType(localAbsPath);
        SVNWCDbStatus status = null;
        SVNWCDbKind dbKind = null;
        boolean conflicted = false;
        try {
            Structure<NodeInfo> info = context.getDb().readInfo(localAbsPath, NodeInfo.status, NodeInfo.kind, NodeInfo.conflicted);
            status = info.get(NodeInfo.status);
            dbKind = info.get(NodeInfo.kind);
            conflicted = info.is(NodeInfo.conflicted);
            
            info.release();
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            if (diskKind != SVNFileType.NONE) {
                result.obstructionState = SVNStatusType.OBSTRUCTED;
                return;
            }
            
            try {
                Structure<NodeInfo> parentInfo = context.getDb().readInfo(SVNFileUtil.getParentFile(localAbsPath), NodeInfo.status, NodeInfo.kind);
                ISVNWCDb.SVNWCDbStatus parentStatus = parentInfo.get(NodeInfo.status);
                ISVNWCDb.SVNWCDbKind parentDbKind = parentInfo.get(NodeInfo.kind);
                if (parentDbKind != SVNWCDbKind.Dir ||
                        (parentStatus != SVNWCDbStatus.Normal &&
                        parentStatus != SVNWCDbStatus.Added)) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;
                }
                parentInfo.release();
            } catch (SVNException e2) {
                if (e2.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;
                    return;
                }
                throw e;
            }
            return;
        }
        if (!noWcRootCheck && dbKind == SVNWCDbKind.Dir && status == SVNWCDbStatus.Normal) {
            boolean isRoot = context.getDb().isWCRoot(localAbsPath);
            if (isRoot) {
                result.obstructionState = SVNStatusType.OBSTRUCTED;
                return;
            }
        }
        result.kind = dbKind.toNodeKind();
        switch (status) {
        case Deleted:
            result.deleted = true;
        case NotPresent:
            if (diskKind != SVNFileType.NONE) {
                result.obstructionState = SVNStatusType.OBSTRUCTED;
            }
            break;
        case Excluded:
        case ServerExcluded:
        case Incomplete:
            result.obstructionState = SVNStatusType.MISSING;            
            break;
        case Added:
            result.added = true;
        case Normal:
            if (diskKind == SVNFileType.NONE) {
                result.obstructionState = SVNStatusType.MISSING;
            } else {
                SVNNodeKind expectedKind = dbKind.toNodeKind();
                if (SVNFileType.getNodeKind(diskKind) != expectedKind) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;            
                }
            }
        }
        
        if (conflicted) {
            ConflictInfo ci = context.getConflicted(localAbsPath, true, true, true);
            result.conflicted = ci != null && (ci.propConflicted || ci.textConflicted || ci.treeConflicted);
        }
    }
    
    private SVNConflictVersion[] makeConflictVersions(File target, SVNNodeKind kind) throws SVNException {
        SVNURL srcReposUrl = repos1.getRepositoryRoot(true);
        String child = SVNWCUtils.getPathAsChild(targetAbsPath, target);
        SVNURL leftUrl;
        SVNURL rightUrl;
        if (child != null) {
            leftUrl = mergeSource1Url.appendPath(child, false);
            rightUrl = mergeSource2Url.appendPath(child, false);
        } else {
            leftUrl = mergeSource1Url;
            rightUrl = mergeSource2Url;
        }
        String leftPath = SVNWCUtils.isChild(srcReposUrl, leftUrl);
        String rightPath = SVNWCUtils.isChild(srcReposUrl, rightUrl);
        SVNConflictVersion lv = new SVNConflictVersion(srcReposUrl, leftPath, mergeSource1Rev, kind);
        SVNConflictVersion rv = new SVNConflictVersion(srcReposUrl, rightPath, mergeSource2Rev, kind);
        
        return new SVNConflictVersion[] {lv, rv};
    }
    
    private void treeConflictOnAdd(File path, SVNNodeKind kind, SVNConflictAction action, SVNConflictReason reason) throws SVNException {
        if (recordOnly || dryRun) {
            return;
        }
        SVNTreeConflictDescription tc = makeTreeConflict(path, kind, action, reason);
        SVNTreeConflictDescription existingTc = context.getTreeConflict(path);
        
        if (existingTc == null) {
            context.getDb().opSetTreeConflict(path, tc);
            if (conflictedPaths == null) {
                conflictedPaths = new HashSet<File>();
            }
            conflictedPaths.add(path);
        } else if (existingTc.getConflictAction() == SVNConflictAction.DELETE && tc.getConflictAction() == SVNConflictAction.ADD) {
            existingTc.setConflictAction(SVNConflictAction.REPLACE);
            context.getDb().opSetTreeConflict(path, existingTc);
        }
    }
    
    private SVNTreeConflictDescription makeTreeConflict(File path, SVNNodeKind kind, SVNConflictAction action, SVNConflictReason reason) throws SVNException {
        final SVNConflictVersion[] cvs = makeConflictVersions(path, kind);
        final SVNTreeConflictDescription tc = new SVNTreeConflictDescription(path, kind, action, reason, null, cvs[0], cvs[1]);
        return tc;
    }
    
    private void treeConflict(File path, SVNNodeKind kind, SVNConflictAction action, SVNConflictReason reason) throws SVNException {
        if (recordOnly || dryRun) {
            return;
        }
        SVNTreeConflictDescription tc = context.getTreeConflict(path);
        if (tc == null) {
            tc = makeTreeConflict(path, kind, action, reason);
            context.getDb().opSetTreeConflict(path, tc);
            
            if (conflictedPaths == null) {
                conflictedPaths = new HashSet<File>();
            }
            conflictedPaths.add(path);
        }
    }

    private boolean compareProps(SVNProperties p1, SVNProperties p2) throws SVNException {
        if (p1 == null || p2 == null) {
            return p1 == p2;
        }
        SVNProperties diff = p1.compareTo(p2);
        for (String propName : diff.nameSet()) {
            if (SVNProperty.isRegularProperty(propName) && !SVNProperty.MERGE_INFO.equals(propName)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean compareFiles(File oldPath, SVNProperties oldProps, File minePath) throws SVNException {
        boolean same = compareProps(oldProps, context.getActualProps(minePath));
        if (same) {
            InputStream is = null;
            InputStream old = null;
            try {
                is = context.getTranslatedStream(minePath, minePath, true, false);
                old = SVNFileUtil.openFileForReading(oldPath);
                same = SVNFileUtil.compare(is, old);
            } finally {
                SVNFileUtil.closeFile(is);
                SVNFileUtil.closeFile(old);
            }
        }
        return same;
    }

    
    private static class ObstructionState {
        SVNStatusType obstructionState;
        boolean added;
        boolean deleted;
        boolean conflicted;
        SVNNodeKind kind;
    }
    
}
