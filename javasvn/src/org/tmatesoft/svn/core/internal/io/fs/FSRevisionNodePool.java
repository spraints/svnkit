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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class FSRevisionNodePool {

    public abstract void setRootsCacheSize(int numberOfRoots); 
    
    public abstract void setRevisionNodesCacheSize(int numberOfNodes);
    
    public abstract void setRevisionsCacheSize(int numberOfRevs);
    
    public abstract int getRootsCacheSize(); 
    
    public abstract int getRevisionNodesCacheSize();
    
    public abstract int getRevisionsCacheSize();

    //first tries to find a necessary root node in the cache
    //if not found, the root node is read from the repository
    public FSRevisionNode getRootRevisionNode(long revision, File reposRootDir) throws SVNException{
        if(reposRootDir == null || FSRepository.isInvalidRevision(revision)){
            return null;
        }
        FSRevisionNode root = fetchRootRevisionNode(revision);
        if(root == null){
            FSRevisionNode rootRevNode = FSReader.getRootRevNode(reposRootDir, revision);
            if(rootRevNode != null){
                cacheRootRevisionNode(revision, root);
            }
        }
        return root;
    }
    
    protected abstract FSRevisionNode fetchRootRevisionNode(long revision);
    
    protected abstract void cacheRootRevisionNode(long revision, FSRevisionNode root);

    private FSRevisionNode getRootNode(FSRoot root, File reposRootDir) throws SVNException{
        if(!root.isTxnRoot()){
            if(root.getRootRevisionNode() == null){
                FSRevisionNode rootRevNode = getRootRevisionNode(root.getRevision(), reposRootDir); 
                root.setRootRevisionNode(rootRevNode);
                return rootRevNode;
            }
            return root.getRootRevisionNode();
        }
        FSTransaction txn = FSReader.getTxn(root.getTxnId(), reposRootDir);
        FSRevisionNode txnRootRevNode = FSReader.getRevNodeFromID(reposRootDir, txn.getRootId()); 
//        root.setRootRevisionNode(rootRevNode);??
        return txnRootRevNode;
    }

    private FSParentPath openPath(FSRoot root, String path, boolean isLastComponentOptional, String txnId, File reposRootDir)throws SVNException{     
    	String canonPath = SVNPathUtil.canonicalizeAbsPath(path);
        FSRevisionNode here = getRootNode(root, reposRootDir);;
        String pathSoFar = "/";
        
        //Make a parentPath item for the root node, using its own current copy-id
        FSParentPath parentPath = new FSParentPath(here, null, null);
        parentPath.setCopyStyle(FSParentPath.COPY_ID_INHERIT_SELF);
        
        String rest = canonPath.substring(1);// skip the leading '/'
        
        /* Whenever we are at the top of this loop:
         - HERE is our current directory,
         - REST is the path we're going to find in HERE, and 
         - PARENT_PATH includes HERE and all its parents.  */
        while(true){
            String entry = SVNPathUtil.head(rest);
            String next = SVNPathUtil.removeHead(rest);
            pathSoFar = SVNPathUtil.concatToAbs(pathSoFar, entry);
            FSRevisionNode child = null;
            if(entry == null || "".equals(entry)){
                child = here;
            }else{
                FSRevisionNode cachedRevNode = fetchRevisionNode(root, pathSoFar, reposRootDir);//getRevisionNode(root.getRootRevisionNode(), pathSoFar, reposRootDir); 
                if(cachedRevNode != null){
                    child = cachedRevNode;
                }else{
                    child = FSReader.getChildDirNode(entry, here, reposRootDir);
                }
                if(child == null){
                    /* If this was the last path component, and the caller
                     * said it was optional, then don't return an error;
                     * just put a null node pointer in the path.  
                     */                
                    if(isLastComponentOptional && (next == null || "".equals(next)) ){
                        return new FSParentPath(null, entry, parentPath);
                    }
                    return null;
                }   
                parentPath.setParentPath(child, entry, new FSParentPath(parentPath));
                SVNLocationEntry copyInherEntry = null;
                if(txnId != null){
                    copyInherEntry = FSParentPath.getCopyInheritance(reposRootDir, parentPath, txnId);
                    parentPath.setCopyStyle((int)copyInherEntry.getRevision());
                    parentPath.setCopySrcPath(copyInherEntry.getPath());
                }
                /* Cache the node we found (if it wasn't already cached). */
                if(cachedRevNode == null){
                    cacheRevisionNode(root, pathSoFar, child);
                }
            }       
            if(next == null || next.compareTo("") == 0){
                break;
            }
            //The path isn't finished yet; we'd better be in a directory
            if(child.getType() != SVNNodeKind.DIR){
                SVNErrorManager.error("'" + pathSoFar + "' is not a directory in filesystem");
            }
            rest = next;
            here = child;
        }       
        return parentPath;
    }
    
    //first tries to find a necessary rev node in the cache
    //if not found, the rev node is read from the repository
    public FSRevisionNode getRevisionNode(long revision, String path, File reposRootDir) throws SVNException{
        if(reposRootDir == null || path == null || "".equals(path) || FSRepository.isInvalidRevision(revision)){
            return null;
        }
        FSRevisionNode revNode = fetchRevisionNode(revision, path);
        if(revNode == null){
            FSRevisionNode root = fetchRootRevisionNode(revision); 
            //get it from a rev-file
            revNode = FSReader.getRevisionNode(reposRootDir, path, root, revision);
            if(revNode != null){
                cacheRevisionNode(revision, path, revNode);
            }
        }
        return revNode;
    }

    protected abstract void cacheRevisionNode(long revision, String path, FSRevisionNode revNode);

    protected abstract FSRevisionNode fetchRevisionNode(long revision, String path);

    /* Nothing to do for non-txn roots cause the rev-node should have been
     * already cached. However txn roots hold the rev-nodes object themselves,
     * we need to cache them
     */
    protected void cacheRevisionNode(FSRoot root, String path, FSRevisionNode revNode) throws SVNException {
        if(root.isTxnRoot()){
            root.putRevNodeToCache(path, revNode);
        }
    }

    /* for txn roots rev-nodes are stored in the root object itself
     * for non-txn roots rev-nodes are cached within this pool object
     */
    protected FSRevisionNode fetchRevisionNode(FSRoot root, String path, File reposRootDir) throws SVNException {
        if(!root.isTxnRoot()){
            FSRevisionNode rootNode = getRootNode(root, reposRootDir);
            return getRevisionNode(rootNode, path, reposRootDir);
        }
        return root.fetchRevNodeFromCache(path);
    }

    //first tries to find a necessary rev node in the cache
    //if not found, the rev node is read from the repository
    public FSRevisionNode getRevisionNode(FSRevisionNode root, String path, File reposRootDir) throws SVNException{
        if(reposRootDir == null || path == null || "".equals(path) || root == null){
            return null;
        }
        return getRevisionNode(root.getId().getRevision(), path, reposRootDir);
    }
    
    public void removeRevisionNode(FSRoot root, String path) throws SVNException {
        if(root.isTxnRoot()){
            root.removeRevNodeFromCache(path);
        }
    }
    
    //first tries to find a necessary rev node in the cache
    //if not found, the rev node is read from the repository
    public FSRevisionNode getRevisionNode(FSRoot root, String path, File reposRootDir) throws SVNException{
        if(reposRootDir == null || path == null || "".equals(path) || root == null){
            return null;
        }
        FSParentPath parentPath = getParentPath(root, path, true, reposRootDir);
        FSRevisionNode revNode = parentPath != null ? parentPath.getRevNode() : null;
        return revNode;
    }
    
    /*
     * Almost the same as getRevisionNode(), but returns a parent path
     * object containing the node-rev itself plus the chain of parent nodes 
     * up to the root.   
     */
    public FSParentPath getParentPath(FSRoot root, String path, boolean entryMustExist, File reposRootDir) throws SVNException{
        if(reposRootDir == null || path == null || "".equals(path) || root == null){
            return null;
        }
        return openPath(root, path, !entryMustExist, root.getTxnId(), reposRootDir);
    }
    
    public abstract void clearRootsCache();
    
    public abstract void clearRevisionsCache();
    
    public void clearAllCaches(){
        clearRootsCache();
        clearRevisionsCache();
    }
}
