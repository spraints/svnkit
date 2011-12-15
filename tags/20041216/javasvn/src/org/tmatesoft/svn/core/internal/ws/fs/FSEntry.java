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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

/**
 * @author TMate Software Ltd.
 */
public abstract class FSEntry implements ISVNEntry {
    
    private String myPath;
    private String myName;
    private Map myProperties;
    private Map myWCProperties;
    private Map myBaseProperties;
    private Set myModifiedProperties;
    private FSAdminArea myAdminArea;
    private FSRootEntry myRootEntry;
    private boolean myIsManaged;
    private long myOldRevision; 
    
    public static final String WC_PREFIX = "svn:wc:"; 
    public static final String ENTRY_PREFIX = "svn:entry:";

    public FSEntry(FSAdminArea adminArea, FSRootEntry entry, String path) {
        setManaged(true);
        myAdminArea = adminArea;
        myPath = path;
        if (myPath.startsWith("/")) {
            myPath = myPath.substring(1);
        }
        myRootEntry = entry;
    }
    
    public boolean isManaged() {
        return myIsManaged;
    }
    
    protected void setManaged(boolean managed) {
        myIsManaged = managed;
    }
    
    public boolean isMissing() throws SVNException {
        return !getRootEntry().getWorkingCopyFile(this).exists() && !isScheduledForDeletion();
    }
    
    public String getPath() {
        return myPath;
    }
    
    public String getName() {
        if (myName == null) {
            myName = PathUtil.tail(getPath());
        }
        return myName;
    }

    public String getPropertyValue(String name) throws SVNException {
        if (name.startsWith(ENTRY_PREFIX)) {
            return (String) getEntry().get(name);            
        } else if (name.startsWith(WC_PREFIX)) {
            if (myWCProperties == null) {
                myWCProperties = getAdminArea().loadWCProperties(this);
            }
            return (String) myWCProperties.get(name);
        }
        if (myProperties == null) {
            myProperties = getAdminArea().loadProperties(this);
        }
        return (String) myProperties.get(name);
    }
    
    public Iterator propertyNames() throws SVNException {
        if (myProperties == null) {
            myProperties = getAdminArea().loadProperties(this);
        }
        HashSet names = new HashSet(myProperties.keySet());
        names.addAll(getEntry().keySet());
        return names.iterator();
    }

    public void setPropertyValue(String name, String value) throws SVNException {
        if (name.startsWith(ENTRY_PREFIX)) {
            // encode value?
            if (value == null) {
                getEntry().remove(name);
            } else {
                getEntry().put(name, value);
            }
        } else if (name.startsWith(WC_PREFIX)) {
            if (myWCProperties == null) {
                myWCProperties = getAdminArea().loadWCProperties(this);
            }
            if (value != null) {
                myWCProperties.put(name, value);
            } else {
                myWCProperties.remove(name);
            }
        } else {
            if (myProperties == null) {
                myProperties = getAdminArea().loadProperties(this);
            }
            if (value != null) {
                myProperties.put(name, value);
            } else {
                myProperties.remove(name);
            }
        }
    }
    
    public boolean isScheduledForAddition() throws SVNException {
        return SVNProperty.SCHEDULE_ADD.equals(getEntry().get(SVNProperty.SCHEDULE)) || 
            SVNProperty.SCHEDULE_REPLACE.equals(getEntry().get(SVNProperty.SCHEDULE));
    }
    
    public boolean isScheduledForDeletion() throws SVNException {
        return SVNProperty.SCHEDULE_DELETE.equals(getEntry().get(SVNProperty.SCHEDULE)) || 
        SVNProperty.SCHEDULE_REPLACE.equals(getEntry().get(SVNProperty.SCHEDULE));
    }
    
    public boolean isModified() throws SVNException {
        return isScheduledForAddition() || isScheduledForDeletion() || isPropertiesModified();
    }

    public int applyChangedProperties(Map changedProperties) throws SVNException {
        if (isScheduledForAddition() || isScheduledForDeletion()) {
            return SVNStatus.OBSTRUCTED;
        }
        setOldRevision(SVNProperty.longValue(getPropertyValue(SVNProperty.COMMITTED_REVISION)));
        if (changedProperties.isEmpty()) {
            return SVNStatus.NOT_MODIFIED;
        }
        if (myBaseProperties == null) {
            myBaseProperties = getAdminArea().loadBaseProperties(this);
        }
        if (myProperties == null) {
            myProperties = getAdminArea().loadProperties(this);
        }
        if (myModifiedProperties == null) {
            myModifiedProperties = new HashSet();
        }
        Map latestProperties = new HashMap(myBaseProperties); 
        for(Iterator entries = changedProperties.entrySet().iterator(); entries.hasNext();) {
            Map.Entry entry = (Map.Entry) entries.next();
            String name = (String) entry.getKey();
            if (name.startsWith(ENTRY_PREFIX) || name.startsWith(WC_PREFIX)) {
                setPropertyValue(name, (String) entry.getValue());
                continue;
            }
            latestProperties.put(name, entry.getValue());
            myModifiedProperties.add(name);
        }
        getAdminArea().saveTemporaryProperties(this, latestProperties);
        if (isPropertiesModified()) {
            // just compare propkeys.
            // if latest (remote) has the same keys as local (that differs from base), then it is a conflict.
            for(Iterator localEntries = myProperties.entrySet().iterator(); localEntries.hasNext();) {
                Map.Entry localEntry = (Map.Entry) localEntries.next();
                if (myBaseProperties.entrySet().contains(localEntry)) {
                    // not changed
                    continue;
                }
                if (latestProperties.containsKey(localEntry.getKey())) {
                    Object newValue = latestProperties.get(localEntry.getKey());
                    Object localValue = localEntry.getValue();
                    if (newValue == null && localValue == newValue) {
                        // mergeable
                        continue;
                    } else if (newValue != null && newValue.equals(localValue)) {
                        // mergeable
                        continue;
                    }
                    return SVNStatus.CONFLICTED;
                }
            }
        }
        return SVNStatus.UPDATED;
    }

    public boolean sendChangedProperties(ISVNEditor editor) throws SVNException {
        if (myBaseProperties == null) {
            myBaseProperties = getAdminArea().loadBaseProperties(this);            
        }
        if (myProperties == null) {
            myProperties = getAdminArea().loadProperties(this);
        }
        if (myModifiedProperties == null) {
            myModifiedProperties = new HashSet();
        }
        // send properties that were added or changed
        for(Iterator keys = myProperties.keySet().iterator(); keys.hasNext();) {
            String key = (String) keys.next();
            Object newValue = myProperties.get(key);
            Object oldValue = myBaseProperties.get(key);
            if (oldValue == null || !oldValue.equals(newValue)) {
                myModifiedProperties.add(key);
                if (isDirectory()) {
                    editor.changeDirProperty(key, (String) newValue);
                } else {
                    editor.changeFileProperty(key, (String) newValue);
                }
            }
        }
        for(Iterator keys = myBaseProperties.keySet().iterator(); keys.hasNext();) {
            String key = (String) keys.next();
            if (!myProperties.containsKey(key)) {
                if (isDirectory()) {
                    editor.changeDirProperty(key, null);
                } else {
                    editor.changeFileProperty(key, null);
                }
                myModifiedProperties.add(key);
            }
        }
        return !myModifiedProperties.isEmpty();
    }
    
    public void save() throws SVNException {
        save(true);
    }
    
    public void save(boolean recursive) throws SVNException {
        getAdminArea().saveProperties(this, myProperties);
        getAdminArea().saveBaseProperties(this, myBaseProperties);
        getAdminArea().saveWCProperties(this, myWCProperties);
    }
    
    public void merge() throws SVNException {
        getAdminArea().saveWCProperties(this, myWCProperties);
        if (!getAdminArea().getTemporaryPropertiesFile(this).exists()) {
            return;
        }
        Map latestProperties = getAdminArea().loadTemporaryProperties(this);
        getAdminArea().saveBaseProperties(this, latestProperties);
        
        if (!isPropertiesModified()) {
            getAdminArea().saveProperties(this, latestProperties);
            long lastModified = getAdminArea().propertiesLastModified(this);
            if (lastModified != 0) {
                getEntry().put(SVNProperty.PROP_TIME, TimeUtil.formatDate(new Date(lastModified)));
            } else {
                getEntry().remove(SVNProperty.PROP_TIME);
            }
            if (myProperties != null) {
                myProperties = latestProperties;
            }
            if (myBaseProperties != null) {
                myBaseProperties = latestProperties;
            }
        } else {
            if (myProperties == null) {
                myProperties = getAdminArea().loadProperties(this);
            }
            if (myBaseProperties == null) {
                myBaseProperties = getAdminArea().loadBaseProperties(this);
            }
            Set conflict = new HashSet();
            for(Iterator entries = latestProperties.entrySet().iterator(); entries.hasNext();) {
                Map.Entry entry = (Map.Entry) entries.next();
                String localValue = (String) myProperties.get(entry.getKey());
                String newValue = (String) entry.getValue();
                if (localValue == null) {
                    myProperties.put(entry.getKey(), entry.getValue());
                } else if ((newValue == null && localValue != null) || (newValue != null && !newValue.equals(localValue)) ||
                        (localValue != null && !localValue.equals(newValue))) {
                    if (newValue != null) {
                        conflict.add("Property '" + entry.getKey() + "' locally changed to '" + myProperties.get(entry.getKey()) + "', but update sets it to '" +
                                entry.getValue() + "'\n");
                    } else {
                        conflict.add("Property '" + entry.getKey() + "' locally changed to '" + myProperties.get(entry.getKey()) + "', but update deletes it\n");
                    }
                } else {
                    myProperties.put(entry.getKey(), entry.getValue());
                }
            }
            if (!conflict.isEmpty()) {
                File parent = getRootEntry().getWorkingCopyFile(this);
                if (!parent.isDirectory()) {
                    parent = parent.getParentFile();
                }
                File prej = new File(parent, isDirectory() ? "dir_conflicts.prej" : getName() + ".prej");
                OutputStream os = null;
                try {
                    os = new FileOutputStream(prej, true);
                    for(Iterator lines = conflict.iterator(); lines.hasNext();) {
                        String line = (String) lines.next();
                        os.write(line.getBytes());
                    }
                } catch (IOException e) {
                    throw new SVNException(e);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e1) {
                        }
                    }
                }
                setPropertyValue(SVNProperty.PROP_REJECT_FILE, prej.getName());
            }
            getAdminArea().saveProperties(this, myProperties);
        }
        getAdminArea().deleteTemporaryProperties(this);
        myModifiedProperties = null;
    }
    
    public void commit() throws SVNException {
        if (myProperties == null) {
            myProperties = getAdminArea().loadProperties(this);
        }
        getAdminArea().saveProperties(this, myProperties);
        getAdminArea().saveBaseProperties(this, myProperties);
        
        long lastModified = getAdminArea().propertiesLastModified(this); 
        if (lastModified != 0) {
            getEntry().put(SVNProperty.PROP_TIME, TimeUtil.formatDate(new Date(lastModified)));
        } else {
            getEntry().remove(SVNProperty.PROP_TIME);
        }
        getAdminArea().saveWCProperties(this, myWCProperties);
    }
    
    public boolean isConflict() throws SVNException {
        return getPropertyValue(SVNProperty.PROP_REJECT_FILE) != null;
    }
    
    public void markResolved() throws SVNException {
        // remove .prej file and corresponding property.
        String fileName = getPropertyValue(SVNProperty.PROP_REJECT_FILE);
        if (fileName == null) {
            return;
        }
        File file = getRootEntry().getWorkingCopyFile(this);
        if (file.isDirectory()) {
            new File(file, fileName).delete();
        } else {
            new File(file.getParentFile(), fileName).delete();
        }
        setPropertyValue(SVNProperty.PROP_REJECT_FILE, null);
    }
    
    protected void revertProperties() throws SVNException {
        // 1. replace props with base props.
        long lm = TimeUtil.parseDate(getPropertyValue(SVNProperty.PROP_TIME)).getTime();
        File base = getAdminArea().getBasePropertiesFile(this);
        File local = getAdminArea().getPropertiesFile(this);
        if (base.exists()) {
            FSUtil.copy(base, local, null, null, null);
            if (lm == 0) {
                lm = local.lastModified();
                setPropertyValue(SVNProperty.PROP_TIME, TimeUtil.formatDate(new Date(lm)));
            } else {
                local.setLastModified(lm);
            }
        } else {
            local.delete();
        }
        if (myProperties != null) {
            myProperties = getAdminArea().loadProperties(this);
        }
    }
    
    public void dispose() throws SVNException {
        myModifiedProperties = null;
        myProperties = null;
        myWCProperties = null;
        myBaseProperties = null;
    }
    
    protected boolean isPropertyModified(String name) {
        return myModifiedProperties != null && myModifiedProperties.contains(name);
    }
    
    public boolean isPropertiesModified() throws SVNException {
        // if equals => compare file stmap with prop-time.
        if (myProperties != null) {
            // props were read or modified.
            // compare base props map and props map,
            if (myBaseProperties == null) {
                myBaseProperties = getAdminArea().loadBaseProperties(this);
            }
            // if props are modified, tstamps doesn't matter.
            return !myProperties.equals(myBaseProperties);
        }
        // may be props were saved after modification.
        long timeStamp = getAdminArea().propertiesLastModified(this); 
        String savedTimeStr = (String) getEntry().get(SVNProperty.PROP_TIME);
        long savedTime = TimeUtil.parseDate(savedTimeStr).getTime();
        if (timeStamp != savedTime) {
            Map bProps = getAdminArea().loadBaseProperties(this);
            Map props = getAdminArea().loadProperties(this);
            return !bProps.equals(props);
        }
        return false;
    }
    
    protected FSAdminArea getAdminArea() {
        return myAdminArea;
    }
    
    protected FSRootEntry getRootEntry() {
        return myRootEntry;
    }
    protected void setOldRevision(long rev) {
        myOldRevision = rev;
    }
    protected long getOldRevision() {
        return myOldRevision;
    }

    protected abstract Map getEntry() throws SVNException;
}