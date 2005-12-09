/*
 * Created on 13.11.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.core.*;

/**
 * @author Tim
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FSParentPath
{	
	//A node along the path.  This could be the final node, one of its
    //parents, or the root.  Every parent path ends with an element for
    //the root directory
	FSRevisionNode revNode;
	
	//The name NODE has in its parent directory.  This is zero for the
    //root directory, which (obviously) has no name in its parent
	String nameEntry;
	
	//The parent of NODE, or zero if NODE is the root directory
	FSParentPath parent;
	
	//The copy ID inheritence style
	int copyStyle;
	
	//If copy ID inheritence style is copy_id_inherit_new, this is the
    //path which should be implicitly copied; otherwise, this is NULL
	String copySrcPath;

	//constructors
	public FSParentPath(){		
	}
	
    public FSParentPath(FSRevisionNode newRevNode, String newNameEntry, FSParentPath newParent, int newCopyStyle, String newCopySrcPath){
		revNode = newRevNode;
		nameEntry = newNameEntry;
		parent = newParent;
		copyStyle = newCopyStyle;
		copySrcPath = newCopySrcPath;
	}
	public FSParentPath(FSParentPath newParentPath){
		revNode = newParentPath.getRevNode();
		nameEntry = newParentPath.getNameEntry();
		parent = newParentPath.getParentPath();
		copyStyle = newParentPath.getCopyStyle();
		copySrcPath = newParentPath.getCopySrcPath();		
	}
	public FSParentPath(FSRevisionNode newRevNode, String newEntry, FSParentPath newParentPath){
		revNode = newRevNode;
		nameEntry = newEntry;
		parent = newParentPath;
		copyStyle = FSParentPath.COPY_ID_INHERIT_UNKNOWN;
		copySrcPath = null;
	}
	
	//methods-accessors
	public FSRevisionNode getRevNode(){
		return revNode;
	}
	public void setRevNode(FSRevisionNode newRevNode){
		revNode = newRevNode;
	}
	public String getNameEntry(){
		return nameEntry;
	}
	public void setNameEntry(String newNameEntry){
		nameEntry = newNameEntry;
	}
	public FSParentPath getParentPath(){
		return parent;
	}
	public void setParent(FSParentPath newParent){
		parent = newParent;
	}
	public int getCopyStyle(){
		return copyStyle;
	}
	public void setCopyStyle(int newCopyStyle){
		copyStyle = newCopyStyle;
	}
	public String getCopySrcPath(){
		return copySrcPath;
	}
	public void setCopySrcPath(String newCopyPath){
		copySrcPath = newCopyPath;
	}
	public void setParentPath(FSRevisionNode newRevNode, String newEntry, FSParentPath newParentPath){
		revNode = newRevNode;
		nameEntry = newEntry;
		parent = newParentPath;
		copyStyle = FSParentPath.COPY_ID_INHERIT_UNKNOWN;
		copySrcPath = null;
	}

	//methods
	public String constructParentPath(){
		String pathSoFar = "/";
		
		if(this.getParentPath() != null){
			pathSoFar = this.getParentPath().constructParentPath();
		}
		
		return this.getNameEntry() != null ? SVNPathUtil.append(pathSoFar, this.getNameEntry()) : pathSoFar;
	}

	//Return value consist of :
	//1:	SVNLocationEntry.revision
	//		copy inheritance style
	//2:	SVNLocationEntry.path
	//		copy src path
	public static SVNLocationEntry getCopyInheritance(File reposRootDir, FSParentPath child, String txnID)throws SVNException{
		if(child == null){
			SVNErrorManager.error("argument FSParentPath have to be valid");			
		}
		if(child.getParentPath() == null){
			SVNErrorManager.error("argument FSParentPath have to be not root");
			return null;
		}
		if(txnID == null){
			SVNErrorManager.error("argument String txnID = null");
		}
		FSID childID = child.getRevNode().getId();
		FSID parentID = child.getParentPath().getRevNode().getId();
		FSID copyrootID = new FSID();
		String childCopyID = childID.getCopyID();
		String parentCopyID = parentID.getCopyID();
		FSRevisionNode copyrootRoot = null;
		FSRevisionNode copyrootNode = null;				
		
		//If this child is already mutable, we have nothing to do
		if(childID.getTxnID() != null){
			return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_SELF, null);
		}
		//From this point on, we'll assume that the child will just take
	    //its copy ID from its parent
		SVNLocationEntry constrEntry = new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_PARENT, null);
		
		//Special case: if the child's copy ID is '0', use the parent's
	    //copy ID
		if(childCopyID.compareTo("0") == 0){
			return constrEntry;
		}
		
		//Compare the copy IDs of the child and its parent.  If they are
	    //the same, then the child is already on the same branch as the
	    //parent, and should use the same mutability copy ID that the
	    //parent will use
		if(childCopyID.compareTo(parentCopyID) == 0){
			return constrEntry;
		}
		
	    //If the child is on the same branch that the parent is on, the
	    //child should just use the same copy ID that the parent would use.
	    //Else, the child needs to generate a new copy ID to use should it
	    //need to be made mutable.  We will claim that child is on the same
	    //branch as its parent if the child itself is not a branch point,
	    //or if it is a branch point that we are accessing via its original
	    //copy destination path
		SVNLocationEntry copyrootEntry = new SVNLocationEntry(child.getRevNode().getCopyRootRevision(), child.getRevNode().getCopyRootPath());
		try{
			copyrootRoot = FSReader.getRootRevNode(reposRootDir, copyrootEntry.getRevision());
			copyrootNode = FSReader.getRevisionNode(reposRootDir, copyrootEntry.getPath(), copyrootRoot, 0);
			copyrootID = copyrootNode.getId();
		}catch(SVNException ex){
			SVNErrorManager.error("");
		}
		if(FSID.compareIds(copyrootID, childID) == -1){
			return copyrootEntry;
		}
		
	    //Determine if we are looking at the child via its original path or
	    //as a subtree item of a copied tree
		if(child.getRevNode().getCreatedPath().compareTo(child.constructParentPath()) == 0){
			return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_SELF, null);
		}
		return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_NEW, child.getRevNode().getCreatedPath());
	}
	
	//the function returns 2 strings 
	//1 string is:
	//			a null-terminated copy of the first component of PATH.
	//		    If path is empty, or consists entirely of
	//			slashes, return the empty string
	//2 string is:
	//		    If the component is followed by one or more slashes, we set 2 string
	//		    to be after the slashes. If the component ends PATH, we set
	//			2 string to zero.  This means:
	//			   - If 2 string is zero, then the component ends the PATH, and there
	//				 are no trailing slashes in the path.
	//			   - If 2 string points at PATH's terminating null character, then
	//				 the component returned was the last, and PATH ends with one or more
	//				 slash characters.
	//			   - Otherwise, 2 string points to the beginning of the next component
	//				 of PATH.  You can pass this value to nextEntryName to extract
	//				 the next component.
	public static String[] nextEntryName(String path){
		String[] retVal = new String[2];
		if(path == null){
			retVal[0] = null;
			retVal[1] = null;
		}		
		int slashOccurence = path.indexOf('/');		
		if(slashOccurence == -1){
			retVal[0] = path;
			retVal[1] = null;
			return retVal;
		}
        int slashCount = slashOccurence + 1;
        while(path.charAt(slashCount) == '/'){
            slashCount++;
            if(slashCount == path.length()){					
                break;
            }				
        }
        retVal[0] = path.substring(0, slashOccurence);
        retVal[1] = path.substring(slashCount, path.length());
        return retVal;
	}
	
	//Copy id inheritance style 
	public static final int COPY_ID_INHERIT_UNKNOWN = 0;
	public static final int COPY_ID_INHERIT_SELF = 1;
	public static final int COPY_ID_INHERIT_PARENT = 2;
	public static final int COPY_ID_INHERIT_NEW = 3;
	
}
