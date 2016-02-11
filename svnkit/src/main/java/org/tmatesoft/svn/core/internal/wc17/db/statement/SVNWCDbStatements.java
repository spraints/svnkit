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
package org.tmatesoft.svn.core.internal.wc17.db.statement;

import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSRevPropCreateSchema;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSRevPropGet;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSRevPropSet;
import org.tmatesoft.svn.core.internal.wc17.db.statement17.*;

/**
 * @author TMate Software Ltd.
 */
public enum SVNWCDbStatements {

    DELETE_ACTUAL_EMPTY(SVNWCDbDeleteActualEmpty.class),
    DELETE_ACTUAL_NODE(SVNWCDbDeleteActualNode.class),
    DELETE_BASE_NODE(SVNWCDbDeleteBaseNode.class),
    DELETE_WC_LOCK_ORPHAN_RECURSIVE(SVNWCDbDeleteLockOrphanRecursive.class),
    DELETE_NODES(SVNWCDbDeleteNodes.class),
    DELETE_PRISTINE(SVNWCDbDeletePristine.class),
    DELETE_WC_LOCK(SVNWCDbDeleteWCLock.class),
    DELETE_WORK_ITEM(SVNWCDbDeleteWorkItem.class),
    DELETE_WORKING_NODE(SVNWCDbDeleteWorkingNode.class),
    DELETE_NODES_RECURSIVE(SVNWCDbDeleteNodesRecursive.class),
    DELETE_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE(SVNWCDbDeleteActualNodeLeavingChangelistRecursive.class),
    DELETE_ACTUAL_EMPTIES(SVNWCDbDeleteActualEmpties.class),
    DELETE_PRISTINE_IF_UNREFERENCED(SVNWCDbDeletePristineIfUnreferenced.class),
    FIND_WC_LOCK(SVNWCDbFindWCLock.class),
    FSFS_GET_REVPROP(SVNFSFSRevPropGet.class),
    FSFS_SET_REVPROP(SVNFSFSRevPropSet.class),
    INSERT_ACTUAL_CONFLICT_DATA(SVNWCDbInsertActualConflictData.class),
    INSERT_ACTUAL_PROPERTY_CONFLICTS(SVNWCDbInsertActualPropertiesConflicts.class),
    INSERT_ACTUAL_PROPS(SVNWCDbInsertActualProps.class),
    INSERT_ACTUAL_NODE(SVNWCDbInsertActualNode.class),
    INSERT_ACTUAL_EMPTIES(SVNWCDbInsertActualEmpties.class),
    INSERT_ACTUAL_TEXT_CONFLICTS(SVNWCDbInsertActualTextConflicts.class),
    INSERT_ACTUAL_TREE_CONFLICTS(SVNWCDbInsertActualTreeConflicts.class),
    INSERT_EXTERNAL(SVNWCDbInsertExternal.class),
    INSERT_EXTERNAL_UPGRADE(SVNWCDbInsertExternalUpgrade.class),
    INSERT_LOCK(SVNWCDbInsertLock.class),
    INSERT_NODE(SVNWCDbInsertNode.class),
    INSERT_PRISTINE(SVNWCDbInsertPristine.class),
    INSERT_OR_IGNORE_PRISTINE(SVNWCDbInsertOrIgnorePristine.class),
    INSERT_REPOSITORY(SVNWCDbInsertRepository.class),
    INSERT_WC_LOCK(SVNWCDbInsertWCLock.class),
    INSERT_WCROOT(SVNWCDbInsertWCRoot.class),
    INSERT_WORK_ITEM(SVNWCDbInsertWorkItem.class),
    INSERT_WORKING_NODE_FROM_BASE(SVNWCDbInsertWorkingNodeFromBase.class),
    INSERT_WORKING_NODE_NORMAL_FROM_BASE(SVNWCDbInsertWorkingNodeNormalFromBase.class),
    INSERT_WORKING_NODE_NOT_PRESENT_FROM_BASE(SVNWCDbInsertWorkingNodeNotPresentFromBase.class),
    INSERT_DELETE_FROM_NODE_RECURSIVE(SVNWCDbInsertDeleteFromNodeRecursive.class),
    INSERT_DELETE_FROM_BASE(SVNWCDbInsertDeleteFromBase.class),
    INSTALL_WORKING_NODE_FOR_DELETE(SVNWCDbInstallWorkingNodeForDelete.class),
    INSERT_TARGET(SVNWCDbInsertTarget.class),
    INSERT_TARGET2(SVNWCDbInsertTarget2.class),
    INSERT_TARGET_DEPTH_FILES(SVNWCDbInsertTargetDepthFiles.class),
    INSERT_TARGET_DEPTH_IMMEDIATES(SVNWCDbInsertTargetDepthImmediates.class),
    INSERT_TARGET_DEPTH_INFINITY(SVNWCDbInsertTargetDepthInfinity.class),
    INSERT_TARGET_WITH_CHANGELIST(SVNWCDbInsertTargetWithChangelist.class),
    INSERT_TARGET_DEPTH_FILES_WITH_CHANGELIST(SVNWCDbInsertTargetDepthFilesWithChangelist.class),
    INSERT_TARGET_DEPTH_IMMEDIATES_WITH_CHANGELIST(SVNWCDbInsertTargetDepthImmediatesWithChangelist.class),
    INSERT_TARGET_DEPTH_INFINITY_WITH_CHANGELIST(SVNWCDbInsertTargetDepthInfinityWithChangelist.class),
    LOOK_FOR_WORK(SVNWCDbLookForWork.class),
    REVPROP_CREATE_SCHEMA(SVNFSFSRevPropCreateSchema.class),
    SELECT_ALL_FILES(SVNWCDbSelectAllFiles.class),
    SELECT_ACTUAL_CONFLICT_VICTIMS(SVNWCDbSelectActualConflictVictims.class),
    SELECT_ACTUAL_NODE(SVNWCDbSelectActualNode.class),
    SELECT_ACTUAL_CHILDREN_INFO(SVNWCDbSelectActualChildrenInfo.class),
    SELECT_ACTUAL_PROPS(SVNWCDbSelectActualProperties.class),
    SELECT_ACTUAL_TREE_CONFLICT(SVNWCDbSelectActualTreeConflict.class),
    SELECT_ANY_PRISTINE_REFERENCE(SVNWCDbSelectAnyPristineReference.class),
    SELECT_BASE_DAV_CACHE(SVNWCDbSelectBaseDavCache.class),
    SELECT_BASE_NODE(SVNWCDbSelectBaseNode.class),
    SELECT_BASE_NODE_CHILDREN(SVNWCDbSelectBaseNodeChildren.class),
    SELECT_LOCK(SVNWCDbSelectLock.class),
    SELECT_BASE_NODE_WITH_LOCK(SVNWCDbSelectBaseNodeWithLock.class),
    SELECT_BASE_PROPS(SVNWCDbSelectBaseProperties.class),
    SELECT_CONFLICT_DETAILS(SVNWCDbSelectConflictDetails.class),
    SELECT_DELETION_INFO(SVNWCDbSelectDeletionInfo.class),
    SELECT_FILE_EXTERNAL(SVNWCDBSelectFileExternal.class),
    SELECT_NODE_PROPS(SVNWCDbSelectNodeProps.class),
    SELECT_NODE_CHILDREN_INFO(SVNWCDbSelectNodeChildrenInfo.class),
    SELECT_NODE_CHILDREN_WALKER_INFO(SVNWCDbSelectNodeChildrenWalkerInfo.class),
    SELECT_NOT_PRESENT_DESCENDANTS(SVNWCDbSelectNotPresent.class),
    SELECT_PRISTINE_MD5_CHECKSUM(SVNWCDbSelectPristineMD5Checksum.class),
    SELECT_PRISTINE_SHA1_CHECKSUM(SVNWCDbSelectSHA1Checksum.class),
    SELECT_UNREFERENCED_PRISTINES(SVNWCDbSelectUnreferencedPristines.class),
    SELECT_REPOSITORY(SVNWCDbSelectRepository.class),
    SELECT_REPOSITORY_BY_ID(SVNWCDbSelectRepositoryById.class),
    SELECT_WC_LOCK(SVNWCDbSelectWCLock.class),
    SELECT_ANCESTORS_WC_LOCKS(SVNWCDbSelectAncestorWCLocks.class),
    SELECT_WCROOT_NULL(SVNWCDbSelectWCRootNull.class),
    SELECT_WORK_ITEM(SVNWCDbSelectWorkItem.class),
    SELECT_NODE_INFO(SVNWCDbSelectNodeInfo.class),
    SELECT_NODE_INFO_WITH_LOCK(SVNWCDbSelectNodeInfoWithLock.class),
    SELECT_WORKING_NODE(SVNWCDbSelectWorkingNode.class),
    SELECT_WORKING_NODE_CHILDREN(SVNWCDbSelectWorkingNodeChildren.class),
    SELECT_CHANGELIST_LIST(SVNWCDbSelectChangelist.class),
    SELECT_TARGETS_LIST(SVNWCDbSelectTargetslist.class),
    UPDATE_ACTUAL_CONFLICT_DATA(SVNWCDbUpdateActualConflictData.class),
    UPDATE_ACTUAL_PROPERTY_CONFLICTS(SVNWCDbUpdateActualPropertyConflicts.class),
    UPDATE_ACTUAL_PROPS(SVNWCDbUpdateActualProps.class),
    UPDATE_ACTUAL_TEXT_CONFLICTS(SVNWCDbUpdateActualTextConflicts.class),
    UPDATE_ACTUAL_TREE_CONFLICTS(SVNWCDbUpdateActualTreeConflicts.class),
    UPDATE_ACTUAL_CHANGELISTS(SVNWCDbUpdateActualChangelists.class),
    UPDATE_BASE_NODE_DAV_CACHE(SVNWCDbUpdateBaseNodeDavCache.class),
    UPDATE_BASE_NODE_PRESENCE_REVNUM_AND_REPOS_PATH(SVNUpdateBaseNodePresenceRevnumAndReposPath.class),
    UPDATE_BASE_REVISION(SVNWCDbUpdateBaseRevision.class),
    UPDATE_COPYFROM(SVNWCDbUpdateCopyfrom.class),
    UPDATE_NODE_BASE_DEPTH(SVNWCDbUpdateNodeBaseDepth.class),
    UPDATE_NODE_BASE_PRESENCE(SVNWCDbUpdateNodeBasePresence.class),
    UPDATE_NODE_PROPS(SVNWCDbUpdateNodeProperties.class),
    UPDATE_NODE_WORKING_DEPTH(SVNWCDbUpdateNodeWorkingDepth.class),
    UPDATE_NODE_FILEINFO(SVNWCDbUpdateNodeFileinfo.class),
    SELECT_LOWEST_WORKING_NODE(SVNWCDbSelectLowestWorkingNode.class),
    CLEAR_TEXT_CONFLICT(SVNWCDbClearTextConflict.class),
    CLEAR_PROPS_CONFLICT(SVNWCDbClearPropsConflict.class),
    CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST_RECURSIVE(SVNWCDbClearActualNodeLeavingChangelistRecursive.class),
    CLEAR_BASE_NODE_RECURSIVE_DAV_CACHE(SVNWCDbClearDavCacheRecursive.class),
    DELETE_LOWEST_WORKING_NODE(SVNWCDbDeleteLowestWorkingNode.class),
    DELETE_ACTUAL_NODE_WITHOUT_CONFLICT(SVNWCDbDeleteActualNodeWithoutConflict.class),
    SELECT_ACTUAL_CHILDREN_CONFLICT(SVNWCDbSelectActualChildrenConflict.class),
    CLEAR_ACTUAL_NODE_LEAVING_CONFLICT(SVNWCDbClearActualNodeLeavingConflict.class),
    INSERT_WORKING_NODE_FROM_BASE_COPY_PRESENCE(SVNWCDbInsertWorkingNodeFromBaseCopyPresence.class),
    INSERT_WORKING_NODE_FROM_BASE_COPY(SVNWCDbInsertWorkingNodeFromBaseCopy.class),
    SELECT_OP_DEPTH_CHILDREN(SVNWCDbSelectOpDepthChildren.class),
    SELECT_GE_OP_DEPTH_CHILDREN(SVNWCDbSelectGeOpDepthChildren.class),
    APPLY_CHANGES_TO_BASE_NODE(SVNWCDbApplyChangesToBaseNode.class),
    DELETE_ALL_WORKING_NODES(SVNWCDbDeleteAllWorkingNodes.class),
    RESET_ACTUAL_WITH_CHANGELIST(SVNWCDbResetActualWithChangelist.class), 
    SELECT_EXTERNALS_DEFINED(SVNWCDBSelectExternalsDefined.class),
    SELECT_EXTERNAL_PROPERTIES(SVNWCDBSelectExternalProperties.class), 
    DOES_NODE_EXIST(SVNWCDBDoesNodeExists.class), 
    UPDATE_ACTUAL_CLEAR_CHANGELIST(SVNWCDDBUpdateActualClearChangelist.class),
    INSERT_DELETE_LIST(SVNWCDbInsertDeleteList.class),
    HAS_SERVER_EXCLUDED_NODES(SVNWCDbHasServerExcludedNodes.class),
    SELECT_WORKING_CHILDREN(SVNWCDbSelectWorkingChildren.class), 
    DELETE_LOCK(SVNWCDbDeleteLock.class),
    DELETE_ALL_LAYERS(SVNWCDbDeleteAllLayers.class), 
    DELETE_SHADOWED_RECURSIVE(SVNWCDbDeleteShadowedRecursive.class), 
    DELETE_ACTUAL_NODE_RECURSIVE(SVNWCDbDeleteActualNodeRecursive.class),
    COMMIT_DESCENDANT_TO_BASE(SVNWCDbCommitDescendantToBase.class), 
    UPDATE_OP_DEPTH_INCREASE_RECURSIVE(SVNWCDbUpdateOpDepthIncreaseRecursive.class), 
    DELETE_WC_LOCK_ORPHAN(SVNWCDbDeleteWCLockOrphan.class), 
    DELETE_ACTUAL_NODE_LEAVING_CHANGELIST(SVNWCDbDeleteActualNodeLeavingChangelist.class), 
    CLEAR_ACTUAL_NODE_LEAVING_CHANGELIST(SVNWCDbClearActualNodeLeavingChangelist.class), 
    SELECT_REVERT_LIST_COPIED_CHILDREN(SVNWCDbSelectRevertListCopiedChildren.class), 
    MARK_SKIPPED_CHANGELIST_DIRS(SVNWCDbMarkSkippedChangelistDirs.class), 
    SELECT_ALL_SERVER_EXCLUDED_NODES(SVNWCDbSelectAllServerExcludedNodes.class), 
    SELECT_MOVED_FROM_RELPATH(SVNWCDbSelectMovedFromRelPath.class),
    SELECT_WCROOT_NODES(SVNWCDbSelectWCRootNodes.class),
    UPDATE_IPROPS(SVNWCDbUpdateIProps.class),
    SELECT_IPROPS_NODE(SVNWCDbSelectIPropsNode.class),
    DELETE_BASE_RECURSIVE(SVNWCDbDeleteBaseRecursive.class),
    DELETE_WORKING_RECURSIVE(SVNWCDbDeleteWorkingRecursive.class),
    DELETE_WORKING_BASE_DELETE(SVNWCDbDeleteWorkingBaseDelete.class),
    DELETE_ACTUAL_FOR_BASE_RECURSIVE(SVNWCDbDeleteActualForBaseRecursive.class),
    SELECT_MOVED_OUTSIDE(SVNWCDbSelectMovedOutside.class),
    SELECT_MOVED_TO(SVNWCDbSelectMovedTo.class),
    CLEAR_MOVED_HERE_RECURSIVE(SVNWCDbClearMovedHereRecursive.class),
    HAS_SERVER_EXCLUDED_DESCENDANTS(SVNWCDbHasServerExcludedDescendants.class),
    SELECT_BASE_PRESENT(SVNWCDbSelectBasePresent.class),
    CLEAR_MOVE_TO_RELPATH(SVNWCDbClearMovedToRelPath.class),
    UPDATE_OP_DEPTH_RECURSIVE(SVNWCDbUpdateOpDepthRecursive.class),
    SELECT_OP_DEPTH_MOVED_PAIR(SVNWCDbSelectOpDepthMovedPair.class),
    SELECT_UPDATE_MOVE_LIST(SVNWCDbSelectUpdateMoveList.class),
    INSERT_UPDATE_MOVE_LIST(SVNWCDbInsertUpdateMoveList.class),
    SELECT_REPOS_PATH_REVISION(SVNWCDbSelectReposPathRevision.class),
    DELETE_WORKING_OP_DEPTH_ABOVE(SVNWCDbDeleteWorkingOpDepthAbove.class),
    SELECT_CHILDREN_OP_DEPTH(SVNWCDbSelectChildrenOpDepth.class),
    SELECT_HIGHEST_WORKING_NODE(SVNWCDbSelectHighestWorkingNode.class),
    DELETE_NO_LOWER_LAYER(SVNWCDbDeleteNoLowerLayer.class),
    REPLACE_WITH_BASE_DELETED(SVNWCDbReplaceWithBaseDeleted.class),
    DELETE_WORKING_OP_DEPTH(SVNWCDbDeleteWorkingOpDepth.class),
    SELECT_LOCAL_RELPATH_OP_DEPTH(SVNWCDbSelectLocalRelPathOpDepth.class),
    COPY_MOVE_NODE(SVNWCDbCopyMoveNode.class),
    UPDATE_ACTUAL_CONFLICT(SVNWCDbUpdateActualConflict.class),
    SELECT_CONFLICT_VICTIMS(SVNWCDbSelectConflictVictims.class),
    DELETE_MOVED_BACK(SVNWCDbDeleteMovedBack.class),
    SELECT_MOVED_BACK(SVNWCDbSelectMovedBack.class),
    SELECT_MIN_MAX_REVISIONS(SVNWCDbSelectMinMaxRevisions.class),
    SELECT_COPY_PRISTINES(SVNWCDbSelectCopyPristines.class),
    UPDATE_MOVED_TO_RELPATH(SVNWCDbUpdateMovedToRelPath.class),
    DELETE_FILE_EXTERNALS(SVNWCDbDeleteFileExternals.class),
    DELETE_EXTERNAL_REGISTRATIONS(SVNWCDbDeleteExternalRegistrations.class),
    DELETE_FILE_EXTERNAL_REGISTRATIONS(SVNWCDbDeleteFileExternalRegistrations.class),
    SELECT_MOVED_FOR_DELETE(SVNWCDbSelectMovedForDelete.class),
    UPDATE_MOVED_TO_DESCENDANTS(SVNWCDbUpdateMovedToDescendants.class),
    CLEAR_MOVED_TO_DESCENDANTS(SVNWCDbClearMovedToDescendants.class),
    DELETE_NODES_ABOVE_DEPTH_RECURSIVE(SVNWCDbDeleteNodesAboveDepthRecursive.class),
    SELECT_MOVED_HERE_CHILDREN(SVNWCDbSelectMovedHereChildren.class),
    SELECT_DELETION_INFO_SCAN(SVNWCDbSelectDeletionInfoScan.class),
    SELECT_BASE_NODE_LOCK_TOKENS_RECURSIVE(SVNWCDbSelectBaseNodeLockTokensRecursive.class),
    SELECT_COMMITTABLE_EXTERNALS_IMMEDIATELY_BELOW(SVNWCDbSelectCommittableExternalsImmediatelyBelow.class),
    SELECT_COMMITTABLE_EXTERNALS_BELOW(SVNWCDbSelectCommittableExternalsBelow.class),
    HAS_LAYER_BETWEEN(SVNWCDbHasLayerBetween.class),
    SELECT_MOVED_PAIR3(SVNWCDbSelectMovedPair3.class),
    SELECT_HAS_NON_FILE_CHILDREN(SVNWCDbSelectHasNonFileChildren.class),
    SELECT_HAS_GRANDCHILDREN(SVNWCDbSelectHasGrandChildren.class),
    SELECT_DEPTH_NODE(SVNWCDbSelectDepthNode.class),
    SELECT_BASE_CHILDREN_INFO(SVNWCDbSelectBaseChildrenInfo.class),
    DELETE_LOCK_RECURSIVELY(SVNWCDbDeleteLockRecursively.class),
    INSERT_WORKING_NODE_COPY_FROM(SVNWCDbInsertWorkingNodeCopyFrom.class),
    SELECT_MOVED_FROM_FOR_DELETE(SVNWCDbSelectMovedFromForDelete.class),
    CLEAR_MOVED_TO_FROM_DEST(SVNWCDbClearMovedToFromDest.class),
    SELECT_MOVED_DESCENDANTS_SRC(SVNWCDbSelectMovedDescendantsSrc.class),
    FIND_CONFLICT_DESCENDANT(SVNWCDbFindConflictDescendant.class),
    SELECT_MOVED_TO_NODE(SVNWCDbSelectMovedToNode.class),
    //1.7 versions of the requests
    SELECT_ACTUAL_CHILDREN_CONFLICT_17(SVNWCDbSelectActualChildrenConflict17.class),
    SELECT_ACTUAL_CHILDREN_INFO_17(SVNWCDbSelectActualChildrenInfo17.class),
    SELECT_ACTUAL_CONFLICT_VICTIMS_17(SVNWCDbSelectActualConflictVictims17.class),
    SELECT_CONFLICT_VICTIMS_17(SVNWCDbSelectConflictVictims17.class),
    UPDATE_ACTUAL_CONFLICT_DATA_17(SVNWCDbUpdateActualConflictData17.class),
    INSERT_ACTUAL_CONFLICT_DATA_17(SVNWCDbInsertActualConflictData17.class),
    SELECT_MOVED_FROM_RELPATH_17(SVNWCDbSelectMovedFromRelPath17.class),
    INSERT_ACTUAL_NODE_17(SVNWCDbInsertActualNode17.class)
    ;
    

    private Class<? extends SVNSqlJetStatement> statementClass;

    private SVNWCDbStatements() {
    }

    private SVNWCDbStatements(Class<? extends SVNSqlJetStatement> statementClass) {
        this.statementClass = statementClass;
    }

    public Class<? extends SVNSqlJetStatement> getStatementClass() {
        return statementClass;
    }

}
