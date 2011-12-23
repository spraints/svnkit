package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.SVNCommitter17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeOriginInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgCommitUtil.ISvnUrlKindCallback;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCommitItem;
import org.tmatesoft.svn.core.wc2.SvnCommitPacket;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.hooks.ISvnCommitHandler;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgWcToReposCopy extends SvnNgOperationRunner<SVNCommitInfo, SvnRemoteCopy> implements ISvnUrlKindCallback {

    @Override
    public boolean isApplicable(SvnRemoteCopy operation, SvnWcGeneration wcGeneration) throws SVNException {
        return areAllSourcesLocal(operation) && !operation.getFirstTarget().isLocal();
    }
    
    private boolean areAllSourcesLocal(SvnRemoteCopy operation) {
        // need all sources to be wc files at WORKING.
        // BASE revision meas repos_to_repos copy
        for(SvnCopySource source : operation.getSources()) {
            if (source.getSource().isFile() && 
                    (source.getRevision() == SVNRevision.WORKING || source.getRevision() == SVNRevision.UNDEFINED)) {
                continue;
            }
            return false;
        }
        return true;
    }
    
    @Override
    protected SVNCommitInfo run(SVNWCContext context) throws SVNException {
        SVNCommitInfo info = doRun(context);
        if (info != null) {
            getOperation().receive(getOperation().getFirstTarget(), info);
        }
        return info;
    }
    
    protected SVNCommitInfo doRun(SVNWCContext context) throws SVNException {
        if (getOperation().isMove()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                    "Moves between the working copy and the repository are not supported");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        Collection<SvnCopySource> sources = getOperation().getSources();
        Collection<SvnCopyPair> copyPairs = new ArrayList<SvnNgWcToReposCopy.SvnCopyPair>();

        if (sources.size() > 1) {
            for (SvnCopySource copySource : sources) {
                SvnCopyPair copyPair = new SvnCopyPair();
                String baseName;
                copyPair.source = copySource.getSource().getFile();
                baseName = copyPair.source.getName();
                copyPair.dst = getOperation().getFirstTarget().getURL();
                copyPair.dst = copyPair.dst.appendPath(baseName, false);
                copyPairs.add(copyPair);
            }
        } else if (sources.size() == 1) {
            SvnCopyPair copyPair = new SvnCopyPair();
            SvnCopySource source = sources.iterator().next(); 
            copyPair.source= source.getSource().getFile();
            copyPair.dst = getOperation().getFirstTarget().getURL();            
            copyPairs.add(copyPair);
        }

        return copy(copyPairs, getOperation().isMakeParents(), getOperation().getRevisionProperties(), getOperation().getCommitMessage(), 
                getOperation().getCommitHandler());
    }

    private SVNCommitInfo copy(Collection<SvnCopyPair> copyPairs, boolean makeParents, SVNProperties revisionProperties, String commitMessage, ISvnCommitHandler commitHandler) throws SVNException {
        
        for (SvnCopyPair pair : copyPairs) {
            pair.srcRevNum = getWcContext().getNodeBaseRev(pair.source);
        }
        SvnCopyPair firstPair = copyPairs.iterator().next();
        SVNURL topDstUrl = firstPair.dst.removePathTail();
        for (SvnCopyPair pair : copyPairs) {
            topDstUrl = SVNURLUtil.getCommonURLAncestor(topDstUrl, pair.dst);
        }
        File topSrcPath = getCommonCopyAncestor(copyPairs);
        SVNRepository repository = getRepositoryAccess().createRepository(topDstUrl, topSrcPath);
        topDstUrl = repository.getLocation();
        
        Collection<SVNURL> parents = null;
        if (makeParents) {
            parents = findMissingParents(topDstUrl, repository);
        }
        for (SvnCopyPair pair : copyPairs) {
            String path = SVNURLUtil.getRelativeURL(repository.getLocation(), pair.dst);
            path = SVNEncodingUtil.uriDecode(path);
            if (repository.checkPath(path, -1) != SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS,
                        "Path ''{0}'' already exists", pair.dst);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        SvnCommitItem[] items = new SvnCommitItem[(parents != null ? parents.size() : 0) + copyPairs.size()];
        int index = 0;
        if (makeParents && parents != null) {
            for (SVNURL parent : parents) {
                SvnCommitItem parentItem = new SvnCommitItem();
                parentItem.setUrl(parent);
                parentItem.setFlags(SvnCommitItem.ADD);
                parentItem.setKind(SVNNodeKind.DIR);
                items[index++] = parentItem;
            }
        }
        for (SvnCopyPair svnCopyPair : copyPairs) {
            SvnCommitItem item = new SvnCommitItem();
            item.setUrl(svnCopyPair.dst);
            item.setPath(svnCopyPair.source);
            item.setFlags(SvnCommitItem.ADD);
            item.setKind(SVNNodeKind.DIR);
            items[index++] = item;
        }
        commitMessage = getOperation().getCommitHandler().getCommitMessage(commitMessage, items);
        if (commitMessage == null) {
            return SVNCommitInfo.NULL;
        }
        revisionProperties = getOperation().getCommitHandler().getRevisionProperties(commitMessage, items, revisionProperties);
        if (revisionProperties == null) {
            return SVNCommitInfo.NULL;
        }
        SvnCommitPacket packet = new SvnCommitPacket();
        SVNURL repositoryRoot = repository.getRepositoryRoot(true);
        if (parents != null) {
            for (SVNURL parent : parents) {
                String parentPath = SVNURLUtil.getRelativeURL(repositoryRoot, parent);
                packet.addItem(null, SVNNodeKind.DIR, repositoryRoot, parentPath, -1, null, -1, SvnCommitItem.ADD);
            }
        }
        for (SvnCopyPair svnCopyPair : copyPairs) {
            SvnNgCommitUtil.harvestCopyCommitables(getWcContext(), svnCopyPair.source, svnCopyPair.dst, packet, this);
        }

        for (SvnCopyPair svnCopyPair : copyPairs) {
            SvnCommitItem item = packet.getItem(svnCopyPair.source);
            if (item == null) {
                continue;
            }
            Map<String, SVNMergeRangeList> mergeInfo = calculateTargetMergeInfo(svnCopyPair.source, -1, repository);
            String mergeInfoProperty = getWcContext().getProperty(svnCopyPair.source, SVNProperty.MERGE_INFO);
            Map<String, SVNMergeRangeList> wcMergeInfo = 
                    mergeInfoProperty != null ? SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoProperty), null) : null;
            if (wcMergeInfo != null && mergeInfo != null) {
                mergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeInfo, wcMergeInfo);
            } else if (mergeInfo == null) {
                mergeInfo = wcMergeInfo;
            }
            String extendedMergeInfoValue = null;
            if (wcMergeInfo != null) {
                extendedMergeInfoValue = SVNMergeInfoUtil.formatMergeInfoToString(wcMergeInfo, null);
                item.addOutgoingProperty(SVNProperty.MERGE_INFO, SVNPropertyValue.create(extendedMergeInfoValue));
            }
        }
        Map<String, SvnCommitItem> committables = new TreeMap<String, SvnCommitItem>();
        SVNURL url = SvnNgCommitUtil.translateCommitables(packet.getItems(packet.getRepositoryRoots().iterator().next()), committables);
        repository.setLocation(url, false);
        ISVNEditor commitEditor = repository.getCommitEditor(commitMessage, null, false, revisionProperties, null);
        return SVNCommitter17.commit(getWcContext(), null, committables, repositoryRoot, commitEditor, null, null);
    }
    
    private Collection<SVNURL> findMissingParents(SVNURL targetURL, SVNRepository repository) throws SVNException {
        SVNNodeKind kind = repository.checkPath("", -1);
        Collection<SVNURL> parents = new ArrayList<SVNURL>();
        while (kind == SVNNodeKind.NONE) {
            parents.add(targetURL);
            targetURL = targetURL.removePathTail();
            repository.setLocation(targetURL, false);
            kind = repository.checkPath("", -1);
        }
        if (kind != SVNNodeKind.DIR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS,
                    "Path ''{0}'' already exists, but it is not a directory", targetURL);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return parents;
    }

    private File getCommonCopyAncestor(Collection<SvnCopyPair> copyPairs) {
        File ancestor = null;
        for (SvnCopyPair svnCopyPair : copyPairs) {
            if (ancestor == null) {
                ancestor = svnCopyPair.source;
                continue;
            }
            String ancestorPath = ancestor.getAbsolutePath().replace(File.separatorChar, '/');
            String sourcePath = svnCopyPair.source.getAbsolutePath().replace(File.separatorChar, '/');
            ancestorPath = SVNPathUtil.getCommonPathAncestor(ancestorPath, sourcePath);
            ancestor = new File(ancestorPath);
        }
        return ancestor;
    }

    private Map<String, SVNMergeRangeList> calculateTargetMergeInfo(File srcFile, long srcRevision, SVNRepository repository) throws SVNException {
        SVNURL url = null;
        SVNURL oldLocation = null;
        
        Structure<NodeOriginInfo> nodeOrigin = getWcContext().getNodeOrigin(srcFile, false, NodeOriginInfo.revision, NodeOriginInfo.reposRelpath, NodeOriginInfo.reposRootUrl);
        if (nodeOrigin != null && nodeOrigin.get(NodeOriginInfo.reposRelpath) != null) {
            url = nodeOrigin.get(NodeOriginInfo.reposRootUrl);
            url = SVNWCUtils.join(url, nodeOrigin.<File>get(NodeOriginInfo.reposRelpath));
            srcRevision = nodeOrigin.lng(NodeOriginInfo.revision);
        }
        if (url != null) {
            Map<String, SVNMergeRangeList> targetMergeInfo = null;
            String mergeInfoPath;
            SVNRepository repos = repository;

            try {
                mergeInfoPath = getRepositoryAccess().getPathRelativeToSession(url, null, repos);
                if (mergeInfoPath == null) {
                    oldLocation = repos.getLocation();
                    repos.setLocation(url, false);
                    mergeInfoPath = "";
                }
                targetMergeInfo = getRepositoryAccess().getReposMergeInfo(repos, mergeInfoPath, srcRevision, SVNMergeInfoInheritance.INHERITED, true);
            } finally {
                if (repository == null) {
                    repos.closeSession();
                } else if (oldLocation != null) {
                    repos.setLocation(oldLocation, false);
                }
            }
            return targetMergeInfo;
        }
        return null;
    }

    private static class SvnCopyPair {
        long srcRevNum;
        
        File source;
        SVNURL dst;
    }

    public SVNNodeKind getUrlKind(SVNURL url, long revision) throws SVNException {
        return getRepositoryAccess().createRepository(url, null).checkPath("", revision);
    }
}