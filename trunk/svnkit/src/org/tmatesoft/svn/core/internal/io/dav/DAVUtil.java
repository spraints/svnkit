/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVPropertiesHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DAVUtil {
    
    public static int DEPTH_ZERO = 0;
    public static int DEPTH_ONE = 1;
    public static int DEPTH_INFINITE = -1;

    public static HTTPStatus getProperties(DAVConnection connection, String path, int depth, String label, DAVElement[] properties, Map result) throws SVNException {
        HTTPHeader header = new HTTPHeader();
        if (depth == DEPTH_ZERO) {
            header.setHeaderValue(HTTPHeader.DEPTH_HEADER, "0");
        } else if (depth == DEPTH_ONE) {
            header.setHeaderValue(HTTPHeader.DEPTH_HEADER, "1");
        } else if (depth == DEPTH_INFINITE) {
            header.setHeaderValue(HTTPHeader.DEPTH_HEADER, "infinity");
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Invalid PROPFIND depth value: '{0}'", new Integer(depth));
            SVNErrorManager.error(err);
        }
        if (label != null) {
            header.setHeaderValue(HTTPHeader.LABEL_HEADER, label);
        }
        StringBuffer body = DAVPropertiesHandler.generatePropertiesRequest(null, properties);
        DAVPropertiesHandler davHandler = new DAVPropertiesHandler();
        davHandler.setDAVProperties(result);        
        return connection.doPropfind(path, header, body, davHandler);
    }
    
    public static DAVProperties getResourceProperties(DAVConnection connection, String path, String label, DAVElement[] properties) throws SVNException {
        Map resultMap = new SVNHashMap();
        HTTPStatus status = getProperties(connection, path, DEPTH_ZERO, label, properties, resultMap);
        if (status.getError() != null) {
            SVNErrorManager.error(status.getError());
        }
        if (!resultMap.isEmpty()) {
            return (DAVProperties) resultMap.values().iterator().next();
        }
        label = label == null ? "NULL" : label;
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Failed to find label ''{0}'' for URL ''{1}''", new Object[] {label, path});
        SVNErrorManager.error(err);
        return null;
    }
    
    public static String getPropertyValue(DAVConnection connection, String path, String label, DAVElement property) throws SVNException {
        DAVProperties props = getResourceProperties(connection, path, label, new DAVElement[] {property});
        SVNPropertyValue value = props.getPropertyValue(property);
        if (value == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "''{0}'' was not present on the resource", property.toString());
            SVNErrorManager.error(err);
        }
        return value.getString();
    }
    
    public static DAVProperties getStartingProperties(DAVConnection connection, String path, String label) throws SVNException {
        return getResourceProperties(connection, path, label, DAVElement.STARTING_PROPERTIES);
    }

    public static DAVProperties findStartingProperties(DAVConnection connection, DAVRepository repos, String fullPath) throws SVNException {
        DAVProperties props = null;
        String originalPath = fullPath;
        String loppedPath = "";
        if ("".equals(fullPath)) {
            props = getStartingProperties(connection, fullPath, null);
            if (props != null) {
                if (props.getPropertyValue(DAVElement.REPOSITORY_UUID) != null && repos != null) {
                    repos.setRepositoryUUID(props.getPropertyValue(DAVElement.REPOSITORY_UUID).getString());
                }
                props.setLoppedPath(loppedPath);
            }
            return props;
        }
        
        while(!"".equals(fullPath)) {
            SVNErrorMessage err = null;
            SVNException nested=null;
            try {
                props = getStartingProperties(connection, fullPath, null);
            } catch (SVNException e) {
                if (e.getErrorMessage() == null) {
                    throw e;
                }
                err = e.getErrorMessage();
            }            
            if (err == null) {
                break;
            }
            if (err.getErrorCode() != SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                SVNErrorManager.error(err);
            }
            loppedPath = SVNPathUtil.append(SVNPathUtil.tail(fullPath), loppedPath);
            int length = fullPath.length();
            fullPath = "/".equals(fullPath) ? "" : SVNPathUtil.removeTail(fullPath);
            if (length == fullPath.length()) {
                SVNErrorMessage err2 = SVNErrorMessage.create(err.getErrorCode(), "The path was not part of repository");
                SVNErrorManager.error(err2, err, nested);
            }
        }        
        if ("".equals(fullPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "No part of path ''{0}'' was found in repository HEAD", originalPath);
            SVNErrorManager.error(err);
        }
        if (props != null) {
            if (props.getPropertyValue(DAVElement.REPOSITORY_UUID) != null && repos != null) {
                repos.setRepositoryUUID(props.getPropertyValue(DAVElement.REPOSITORY_UUID).getString());
            }
            if (props.getPropertyValue(DAVElement.BASELINE_RELATIVE_PATH) != null && repos != null) {
                String relativePath = props.getPropertyValue(DAVElement.BASELINE_RELATIVE_PATH).getString();
                relativePath = SVNEncodingUtil.uriEncode(relativePath);
                String rootPath = fullPath.substring(0, fullPath.length() - relativePath.length());
                repos.setRepositoryRoot(repos.getLocation().setPath(rootPath, true));
            }
            props.setLoppedPath(loppedPath);
        } 
        
        return props;
    }
    
    public static String getVCCPath(DAVConnection connection, DAVRepository repository, String path) throws SVNException {
        DAVProperties properties = findStartingProperties(connection, repository, path);
        SVNPropertyValue vcc = properties.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        if (vcc == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "The VCC property was not found on the resource");
            SVNErrorManager.error(err);
        }
        return vcc.getString();
    }

    public static DAVBaselineInfo getBaselineInfo(DAVConnection connection, DAVRepository repos, String path, long revision,
                                                  boolean includeType, boolean includeRevision, DAVBaselineInfo info) throws SVNException {
        DAVElement[] properties = includeRevision ? DAVElement.BASELINE_PROPERTIES : new DAVElement[] {DAVElement.BASELINE_COLLECTION};
        DAVProperties baselineProperties = getBaselineProperties(connection, repos, path, revision, properties);

        info = info == null ? new DAVBaselineInfo() : info;
        info.baselinePath = baselineProperties.getURL();
        SVNPropertyValue baseValue = baselineProperties.getPropertyValue(DAVElement.BASELINE_COLLECTION);
        info.baselineBase = baseValue == null ? null : baseValue.getString();
        info.baseline = baselineProperties.getOriginalURL();
        if (info.baselineBase == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "'DAV:baseline-collection' not present on the baseline resource");
            SVNErrorManager.error(err);
        }
//        info.baselineBase = SVNEncodingUtil.uriEncode(info.baselineBase);
        if (includeRevision) {
            SVNPropertyValue version = baselineProperties.getPropertyValue(DAVElement.VERSION_NAME);
            if (version == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "'DAV:version-name' not present on the baseline resource");
                SVNErrorManager.error(err);
            }
            info.revision = Long.parseLong(version.getString());
        }
        if (includeType) {
            Map propsMap = new SVNHashMap();
            path = SVNPathUtil.append(info.baselineBase, info.baselinePath);
            HTTPStatus status = getProperties(connection, path, 0, null, new DAVElement[] {DAVElement.RESOURCE_TYPE}, propsMap);
            if (status.getError() != null) {
                SVNErrorManager.error(status.getError());
            }
            if (!propsMap.isEmpty()) {
                DAVProperties props = (DAVProperties) propsMap.values().iterator().next();
                info.isDirectory = props != null && props.isCollection();
            }
        }
        return info;
    }

    public static DAVProperties getBaselineProperties(DAVConnection connection, DAVRepository repos, String path, long revision, DAVElement[] elements) throws SVNException {
        DAVProperties properties = null;
        String loppedPath = "";
        properties = findStartingProperties(connection, repos, path);
        SVNPropertyValue vccValue = properties.getPropertyValue(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        if (vccValue == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "The VCC property was not found on the resource");
            SVNErrorManager.error(err);
        }
        loppedPath = properties.getLoppedPath();
        SVNPropertyValue baselineRelativePathValue = properties.getPropertyValue(DAVElement.BASELINE_RELATIVE_PATH);
        if (baselineRelativePathValue == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "The relative-path property was not found on the resource");
            SVNErrorManager.error(err);
        }
        String baselineRelativePath = SVNEncodingUtil.uriEncode(baselineRelativePathValue.getString());
        baselineRelativePath = SVNPathUtil.append(baselineRelativePath, loppedPath);
        String label = null;
        String vcc = vccValue.getString();
        if (revision < 0) {
            vcc = getPropertyValue(connection, vcc, null, DAVElement.CHECKED_IN);
        } else {
            label = Long.toString(revision);
        }
        properties = getResourceProperties(connection, vcc, label, elements);
        properties.setURL(baselineRelativePath);
        return properties;
    }

    public static SVNProperties filterProperties(DAVProperties source, SVNProperties target) {
        target = target == null ? new SVNProperties() : target;
        for (Iterator props = source.getProperties().keySet().iterator(); props.hasNext();) {
            DAVElement property = (DAVElement) props.next();
            String namespace = property.getNamespace();
            if (namespace.equals(DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE)) {
                String name = property.getName();
                // hack!
                if (name.startsWith("svk_")) {
                    name = name.substring(0, "svk".length()) + ":" + name.substring("svk".length() + 1);
                }
                target.put(name, source.getPropertyValue(property));
            } else if (namespace.equals(DAVElement.SVN_SVN_PROPERTY_NAMESPACE)) {
                target.put("svn:" + property.getName(), source.getPropertyValue(property));
            } else if (property == DAVElement.CHECKED_IN) {
                target.put("svn:wc:ra_dav:version-url", source.getPropertyValue(property));
            }
        }
        return target;
    }

    public static void setSpecialWCProperties(SVNProperties props, DAVElement property, SVNPropertyValue propValue) {
        String propName = convertDAVElementToPropName(property);
        if (propName != null) {
            props.put(propName, propValue);
        }
    }

    public static void setSpecialWCProperties(ISVNEditor editor, boolean isDir, String path, DAVElement property, 
            SVNPropertyValue propValue) throws SVNException {
        String propName = convertDAVElementToPropName(property);
        if (propName != null) {
            if (isDir) {
                editor.changeDirProperty(propName, propValue);
            } else {
                editor.changeFileProperty(path, propName, propValue);
            }
        }
    }

    public static String getPathFromURL(String url) {
        String schemeEnd = "://";
        int ind = url.indexOf(schemeEnd);
        if (ind == -1) {
            return url;
        }
        
        url = url.substring(schemeEnd.length());
        for (int i = 0; i < url.length(); i++) {
            char currentChar = url.charAt(i);
            if (currentChar == '/' || currentChar == '?' || currentChar == '#') {
                return url.substring(i);
            }
        }
        return "/";
    }

    private static String convertDAVElementToPropName(DAVElement property) {
        String propName = null;
        if (property == DAVElement.VERSION_NAME) {
            propName = SVNProperty.COMMITTED_REVISION;    
        } else if (property == DAVElement.CREATOR_DISPLAY_NAME) {
            propName = SVNProperty.LAST_AUTHOR;
        } else if (property == DAVElement.CREATION_DATE) {
            propName = SVNProperty.COMMITTED_DATE;
        } else if (property == DAVElement.REPOSITORY_UUID) {
            propName = SVNProperty.UUID;
        } else if (property == DAVElement.MD5_CHECKSUM) {
            propName = SVNProperty.CHECKSUM;
        }
        return propName;
    }
}
