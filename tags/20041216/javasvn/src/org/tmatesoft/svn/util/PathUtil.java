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

package org.tmatesoft.svn.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.StringTokenizer;

/**
 * @author Alexander Kitaev
 */
public class PathUtil {
    
    public static final boolean isEmpty(String path) {
        return path == null || "".equals(path.trim()) || "/".equals(path.trim());
    }
    
    public static final String removeTail(String path) {
        if (isEmpty(path)) {
            return null;
        }
        path = removeTrailingSlash(path);
        int index = path.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return path.substring(0, index);
    }

    public static final String append(String path, String segment) {
        path = isEmpty(path) ? "" : removeTrailingSlash(path);
        segment = isEmpty(segment) ? "" : removeLeadingSlash(segment);
        return path + '/' + segment;
    }
    
    public static String tail(String path) {
        path = removeTrailingSlash(path);
        int index = path.lastIndexOf('/');
        if (index >= 0) {
            return path.substring(index + 1);
        }
        return path;
    }

    public static String head(String path) {
        path = removeLeadingSlash(path);
        int index = path.indexOf('/');
        if (index >= 0) {
            return path.substring(0, index);
        }
        return path;
    }

    public static String removeLeadingSlash(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return path;
    }

    public static final String removeTrailingSlash(String path) {
        if (isEmpty(path)) {
            return path;
        }
        if (path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
    
    public static void encode(String urlPart, StringBuffer dst) {
        for(int i = 0; i < urlPart.length(); i++) {
            char ch = urlPart.charAt(i);
            if (ch == ' ') {
                dst.append("%20");                
            } else {
                dst.append(ch);
            }
        }
    }
    
    public static String encode(String source) {
        StringBuffer sb = new StringBuffer();
        encode(source, sb);
        return sb.toString();
    }

    public static String decode(String source) {
        try {
            return URLDecoder.decode(source, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        return source;
    }
    
    public static String getCommonRoot(String[] paths) {
        if (paths.length == 1) {
            return PathUtil.removeLeadingSlash(PathUtil.removeTail(paths[0]));
        } 
        String[] p = new String[paths.length];
        for(int i = 0; i < p.length; i++) {
            p[i] = paths[i].replace(File.separatorChar, '/');
        }
        paths = p;
        // find common root
        String root = "";
        for(StringTokenizer tokens = new StringTokenizer(paths[0], "/", true); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (!"/".equals(token)) {
                String testRoot = root + token + "/";
                for(int i = 0; i < paths.length; i++) {
                    if (!paths[i].startsWith(testRoot)) {
                        root = PathUtil.removeLeadingSlash(root);
                        root = PathUtil.removeTrailingSlash(root);
                        return root;
                    }
                }
            }
            root += token;
        }
        return root;
    }

    public static boolean isURL(String pathOrUrl) {
        return pathOrUrl != null && 
            (pathOrUrl.startsWith("http://") || 
                    pathOrUrl.startsWith("https://") || 
                    pathOrUrl.startsWith("svn://") ||
                    pathOrUrl.startsWith("svn+ssh://"));
    }
}