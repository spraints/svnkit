/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNFileUtil {


    public final static boolean isWindows;
    public final static OutputStream DUMMY_OUT = new OutputStream() {
        public void write(int b) throws IOException {
        }
    };
    public final static InputStream DUMMY_IN = new InputStream() {
        public int read() throws IOException {
            return -1;
        }
    };
    
    private static String nativeEOLMarker;
    private static String ourGroupID;
    private static String ourUserID;
    private static File ourAppDataPath;
    private static final String BINARY_MIME_TYPE = "application/octet-stream";

    static {
        String osName = System.getProperty("os.name");
        isWindows = osName != null
                && osName.toLowerCase().indexOf("windows") >= 0;
    }

    public static String getBasePath(File file) {
        File base = file.getParentFile();
        while (base != null) {
            if (base.isDirectory()) {
                File adminDir = new File(base, ".svn");
                if (adminDir.exists() && adminDir.isDirectory()) {
                    break;
                }
            }
            base = base.getParentFile();
        }
        String path = file.getAbsolutePath();
        if (base != null) {
            path = path.substring(base.getAbsolutePath().length());
        }
        path = path.replace(File.separatorChar, '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }
    
    public static boolean createEmptyFile(File file) throws SVNException {
        boolean created;
        try {
            created = file.createNewFile();
        } catch (IOException e) {
            created = false;
        }
        if (!created) {
            SVNErrorManager.error("svn: Cannot create new file '" + file + "'");            
        }
        return created;
    }

    public static File createUniqueFile(File parent, String name, String suffix) {
        File file = new File(parent, name + suffix);
        for (int i = 1; i < 99999; i++) {
            if (!file.exists()) {
                return file;
            }
            file = new File(parent, name + "." + i + suffix);
        }
        return file;
    }

    public static void rename(File src, File dst) throws SVNException {
        boolean renamed = src.renameTo(dst);
        if (renamed) {
            return;
        }
        if (!src.exists() && !isSymlink(src)) {
            SVNErrorManager.error("svn: Cannot rename file '"
                    + src.getAbsolutePath() + "' : file doesn't exist");
        }
        if (dst.isDirectory()) {
            SVNErrorManager.error("svn: Cannot overwrite file '"
                    + dst.getAbsolutePath() + "' : it is a directory");
        }
        if (!renamed) {
            if (dst.exists()) {
                boolean deleted = dst.delete();
                if (!deleted && dst.exists()) {
                    SVNErrorManager.error("svn: Cannot overwrite file '"
                            + dst.getAbsolutePath() + "'");
                }
            }
            if (!src.renameTo(dst)) {
                SVNErrorManager.error("svn: Cannot rename file '"
                        + src.getAbsolutePath() + "'");
            }
        }
    }

    public static boolean setReadonly(File file, boolean readonly)
            throws SVNException {
        if (!file.exists()) {
            SVNErrorManager.error("svn: Cannot change file RO state '"
                    + file.getAbsolutePath() + "' : file doesn't exist");
        }
        if (readonly) {
            return file.setReadOnly();
        }
        if (file.canWrite()) {
            return true;
        }
        File tmpFile = createUniqueFile(file.getParentFile(), file.getName(), ".tmp");
        copyFile(file, tmpFile, false);
        rename(tmpFile, file);
        return true;
    }

    public static void setExecutable(File file, boolean executable) {
        if (isWindows || file == null || !file.exists()) {
            return;
        }
        try {
            execCommand(new String[] { "chmod", executable ? "ugo+x" : "ugo-x",
                    file.getAbsolutePath() });
        } catch (Throwable th) {
            SVNDebugLog.logInfo(th);
        }
    }
    
    public static File resolveSymlinkToFile(File file){
        File targetFile = file;
        while(isSymlink(targetFile)){
            String symlinkName = getSymlinkName(targetFile); 
            if(symlinkName == null){
                return null;
            }
            if(symlinkName.startsWith("/")){
                targetFile = new File(symlinkName);
            }else{
                targetFile = new File(targetFile.getParentFile(), symlinkName);
            }
        }
        if(targetFile == null && !targetFile.isFile()){
            return null;
        }
        return targetFile;
    }
    
    public static boolean isSymlink(File file) {
        if (isWindows || file == null) {
            return false;
        }
        String line = execCommand(new String[] { "ls", "-ld",
                file.getAbsolutePath() });
        return line != null && line.startsWith("l");
    }

    public static void copy(File src, File dst, boolean safe, boolean copyAdminDirectories) throws SVNException {
        SVNFileType srcType = SVNFileType.getType(src);
        if (srcType == SVNFileType.FILE) {
            copyFile(src, dst, safe);
        } else if (srcType == SVNFileType.DIRECTORY) {
            copyDirectory(src, dst, copyAdminDirectories, null);
        } else if (srcType == SVNFileType.SYMLINK) {
            String name = SVNFileUtil.getSymlinkName(src);
            if (name != null) {
                SVNFileUtil.createSymlink(dst, name);
            }
        }
    }

    public static void copyFile(File src, File dst, boolean safe)
            throws SVNException {
        if (src == null || dst == null) {
            return;
        }
        if (src.equals(dst)) {
            return;
        }
        if (!src.exists()) {
            dst.delete();
            return;
        }
        File tmpDst = dst;
        if (dst.exists()) {
            if (safe) {
                tmpDst = createUniqueFile(dst.getParentFile(), dst.getName(), ".tmp");
            } else {
                dst.delete();
            }
        }
        FileChannel srcChannel = null;
        FileChannel dstChannel = null;
        dst.getParentFile().mkdirs();
        try {
            srcChannel = new FileInputStream(src).getChannel();
            dstChannel = new FileOutputStream(tmpDst).getChannel();
            long count = srcChannel.size();
            while (count > 0) {
                count -= dstChannel.transferFrom(srcChannel, 0, srcChannel
                        .size());
            }
        } catch (IOException e) {
            SVNErrorManager.error("svn: Cannot copy file '" + src + "' to '" + dst + "'");
        } finally {
            if (dstChannel != null) {
                try {
                    dstChannel.close();
                } catch (IOException e) {
                    //
                }
            }
            if (srcChannel != null) {
                try {
                    srcChannel.close();
                } catch (IOException e) {
                    //
                }
            }
        }
        if (safe && tmpDst != dst) {
            rename(tmpDst, dst);
        }
        dst.setLastModified(src.lastModified());
    }

    public static boolean createSymlink(File link, File linkName)
            throws SVNException {
        if (isWindows) {
            return false;
        }
        if (link.exists() || isSymlink(link)) {
            SVNErrorManager.error("svn: Cannot create symlink '"
                    + link.getAbsolutePath() + "' : file already exists.");
        }
        String linkTarget = "";
        try {
            linkTarget = readSingleLine(linkName);
        } catch (IOException e) {
            SVNErrorManager.error("svn: " + e.getMessage());
        }
        if (linkTarget.startsWith("link")) {
            linkTarget = linkTarget.substring("link".length()).trim();
        }
        return createSymlink(link, linkTarget);
    }

    public static boolean createSymlink(File link, String linkName) {
        execCommand(new String[] { "ln", "-s", linkName, link.getAbsolutePath() });
        return isSymlink(link);
    }

    public static boolean detranslateSymlink(File src, File linkFile)
            throws SVNException {
        if (isWindows) {
            return false;
        }
        if (!isSymlink(src)) {
            SVNErrorManager.error("svn: Cannot detranslate symlink '"
                    + src.getAbsolutePath()
                    + "' : file doesn't exists or not a symlink.");
        }
        String linkPath = getSymlinkName(src);
        OutputStream os = openFileForWriting(linkFile);
        try {
            os.write("link ".getBytes("UTF-8"));
            os.write(linkPath.getBytes("UTF-8"));
        } catch (IOException e) {
            SVNErrorManager.error("svn: " + e.getMessage());
        } finally {
            SVNFileUtil.closeFile(os);
        }
        return true;
    }

    public static String getSymlinkName(File link) {
        if (isWindows || link == null) {
            return null;
        }
        String ls = execCommand(new String[] { "ls", "-ld", link.getAbsolutePath() });
        if (ls == null || ls.lastIndexOf(" -> ") < 0) {
            return null;
        }
//        return ls.substring(ls.lastIndexOf(" -> ") + " -> ".length()).trim();
        String[] attributes = ls.split(" ");
        return attributes[attributes.length - 1];
    }

    public static String computeChecksum(String line) {
        if (line == null) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        if (digest == null) {
            return null;
        }
        digest.update(line.getBytes());
        return toHexDigest(digest);

    }

    public static String computeChecksum(File file) throws SVNException {
        if (file == null || file.isDirectory() || !file.exists()) {
            return null;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            SVNErrorManager.error("svn: MD5 algorithm implementation not found");
            return null;
        }
        InputStream is = openFileForReading(file);
        byte[] buffer = new byte[1024*16];
        try {
            while (true) {
                int l = is.read(buffer);
                if (l <= 0) {
                    break;
                }
                digest.update(buffer, 0, l);
            }
        } catch (IOException e) {
            SVNErrorManager.error("svn: I/O error while computing checksum for '" + file + "'");
        } finally {
            closeFile(is);
        }
        return toHexDigest(digest);
    }

    public static boolean compareFiles(File f1, File f2, MessageDigest digest)
            throws SVNException {
        if (f1 == null || f2 == null) {
            SVNErrorManager
                    .error("svn: Cannot accept 'null' values in compareFiles method");
            return false;
        }
        if (f1.equals(f2)) {
            return true;
        }
        boolean equals = true;
        if (f1.length() != f2.length()) {
            if (digest == null) {
                return false;
            }
            equals = false;
        }
        InputStream is1 = openFileForReading(f1);
        InputStream is2 = openFileForReading(f2);
        try {
            while (true) {
                int b1 = is1.read();
                int b2 = is2.read();
                if (b1 != b2) {
                    if (digest == null) {
                        return false;
                    }
                    equals = false;
                }
                if (b1 < 0) {
                    break;
                }
                if (digest != null) {
                    digest.update((byte) (b1 & 0xFF));
                }
            }
        } catch (IOException e) {
            SVNErrorManager.error("svn: I/O error while comparing files '" + f1 + "' and '" + f2 + "'");
        } finally {
            closeFile(is1);
            closeFile(is2);
        }
        return equals;
    }

    public static void setHidden(File file, boolean hidden) {
        if (!isWindows || file == null || !file.exists() || file.isHidden()) {
            return;
        }
        try {
            Runtime.getRuntime().exec(
                    "attrib " + (hidden ? "+" : "-") + "H \""
                            + file.getAbsolutePath() + "\"");
        } catch (Throwable th) {
            //
        }
    }

    public static void deleteAll(File dir, ISVNEventHandler cancelBaton) throws SVNException {
        deleteAll(dir, true, cancelBaton);
    }

    public static void deleteAll(File dir, boolean deleteDirs) {
      try {
        deleteAll(dir, deleteDirs, null);
      }
      catch (SVNException e) {
        // should never happen as cancell handler is null.
      }

    }

    public static void deleteAll(File dir, boolean deleteDirs, ISVNEventHandler cancelBaton) throws SVNException {
        if (dir == null) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            if (cancelBaton != null) {
                cancelBaton.checkCancelled();
            }
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                deleteAll(child, deleteDirs, cancelBaton);
            }
            if (cancelBaton != null) {
                cancelBaton.checkCancelled();
            }
        }
        if (dir.isDirectory() && !deleteDirs) {
            return;
        }
        dir.delete();
    }

    private static String readSingleLine(File file) throws IOException {
        if (!file.isFile() || !file.canRead()) {
            throw new IOException("can't open file '" + file.getAbsolutePath()
                    + "'");
        }
        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            line = reader.readLine();
        } finally {
            closeFile(reader);
        }
        return line;
    }

    public static String toHexDigest(MessageDigest digest) {
        if (digest == null) {
            return null;
        }
        byte[] result = digest.digest();
        String hexDigest = "";
        for (int i = 0; i < result.length; i++) {
            byte b = result[i];
            int lo = b & 0xf;
            int hi = (b >> 4) & 0xf;
            hexDigest += Integer.toHexString(hi) + Integer.toHexString(lo);
        }
        return hexDigest;
    }
    
    public static String toHexDigest(byte[] digest) {
        if (digest == null) {
            return null;
        }

        String hexDigest = "";
        for (int i = 0; i < digest.length; i++) {
            byte b = digest[i];
            int lo = b & 0xf;
            int hi = (b >> 4) & 0xf;
            hexDigest += Integer.toHexString(hi) + Integer.toHexString(lo);
        }
        return hexDigest;
    }

    public static byte[] fromHexDigest(String hexDigest){
        if(hexDigest == null || hexDigest.length()==0){
            return null;
        }
        
        String hexMD5Digest = hexDigest.toLowerCase();
        
        int digestLength = hexMD5Digest.length()/2;
        
        if(digestLength==0 || 2*digestLength != hexMD5Digest.length()){
            return null;
        }
        
        byte[] digest = new byte[digestLength];
        for(int i = 0; i < hexMD5Digest.length()/2; i++){
            if(!isHex(hexMD5Digest.charAt(2*i)) || !isHex(hexMD5Digest.charAt(2*i + 1))){
                return null;
            }
            
            int hi = Character.digit(hexMD5Digest.charAt(2*i), 16)<<4;

            int lo =  Character.digit(hexMD5Digest.charAt(2*i + 1), 16);
            Integer ib = new Integer(hi | lo);
            byte b = ib.byteValue();
            
            digest[i] = b;
        }
        
        return digest; 
    }
    
    private static boolean isHex(char ch){
        if(Character.isDigit(ch) || (ch - 'a' >= 0 && ch - 'a' < 6)){
            return true;
        }
        return false;
    }
    
    public static String getNativeEOLMarker(){
        if(nativeEOLMarker == null){
            nativeEOLMarker = new String(SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE));
        }
        return nativeEOLMarker;
    }
    
    public static long roundTimeStamp(long tstamp) {
        return (tstamp / 1000) * 1000;
    }

    public static void sleepForTimestamp() {
        long time = System.currentTimeMillis();
        time = 1010 - (time - (time / 1000) * 1000);
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            //
        }
    }
    
    public static String detectMimeType(InputStream is) throws IOException {
        byte[] buffer = new byte[1024];
        int read = 0;
        read = is.read(buffer);
        int binaryCount = 0;
        for (int i = 0; i < read; i++) {
            byte b = buffer[i];
            if (b == 0) {
                return BINARY_MIME_TYPE;
            }
            if (b < 0x07 || (b > 0x0d && b < 0x20) || b > 0x7F) {
                binaryCount++;
            }
        }
        if (read > 0 && binaryCount * 1000 / read > 850) {
            return BINARY_MIME_TYPE;
        }
        return null;
    }

    public static String detectMimeType(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        InputStream is = null;
        try {
            is = openFileForReading(file);
            return detectMimeType(is);
        } catch (IOException e) {
            return null;
        } catch (SVNException e) {
            return null;
        } finally {
            closeFile(is);
        }
    }

    public static boolean isExecutable(File file) {
        if (isWindows) {
            return false;
        }
        String[] commandLine = new String[] { "ls", "-ln",
                file.getAbsolutePath() };
        String line = execCommand(commandLine);
        if (line == null || line.indexOf(' ') < 0) {
            return false;
        }
        int index = 0;

        String mod = null;
        String fuid = null;
        String fgid = null;
        for (StringTokenizer tokens = new StringTokenizer(line, " \t"); tokens
                .hasMoreTokens();) {
            String token = tokens.nextToken();
            if (index == 0) {
                mod = token;
            } else if (index == 2) {
                fuid = token;
            } else if (index == 3) {
                fgid = token;
            } else if (index > 3) {
                break;
            }
            index++;
        }
        if (mod == null) {
            return false;
        }
        if (getCurrentUser().equals(fuid)) {
            return mod.toLowerCase().indexOf('x') >= 0
                    && mod.toLowerCase().indexOf('x') < 4;
        } else if (getCurrentGroup().equals(fgid)) {
            return mod.toLowerCase().indexOf('x', 4) >= 4
                    && mod.toLowerCase().indexOf('x', 4) < 7;
        } else {
            return mod.toLowerCase().indexOf('x', 7) >= 7;
        }
    }

    public static void copyDirectory(File srcDir, File dstDir, boolean copyAdminDir, ISVNEventHandler cancel) throws SVNException {
        if (!dstDir.exists()) {
            dstDir.mkdirs();
            dstDir.setLastModified(srcDir.lastModified());
        }
        File[] files = srcDir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            File file = files[i];
            if (file.getName().equals("..") || file.getName().equals(".")
                    || file.equals(dstDir)) {
                continue;
            }
            if (cancel != null) {
                cancel.checkCancelled();
            }
            if (!copyAdminDir && file.getName().equals(".svn")) {
                continue;
            }
            SVNFileType fileType = SVNFileType.getType(file);
            File dst = new File(dstDir, file.getName());

            if (fileType == SVNFileType.FILE) {
                boolean executable = isExecutable(file);
                copyFile(file, dst, false);
                if (executable) {
                    setExecutable(dst, executable);
                }
            } else if (fileType == SVNFileType.DIRECTORY) {
                copyDirectory(file, dst, copyAdminDir, cancel);
                if (file.isHidden() || ".svn".equals(file.getName())) {
                    setHidden(dst, true);
                }
            } else if (fileType == SVNFileType.SYMLINK) {
                String name = getSymlinkName(file);
                createSymlink(dst, name);
            }
        }
    }

    public static OutputStream openFileForWriting(File file)
            throws SVNException {
        return openFileForWriting(file, false);
    }

    public static OutputStream openFileForWriting(File file, boolean append)
            throws SVNException {
        if (file == null) {
            return null;
        }
        /*
        if (file.exists() && (!file.isFile() || !file.canWrite())) {
            SVNErrorManager.error("svn: Cannot write to '" + file
                    + "': path refers to directory or write access denied");
        }*/
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            return new BufferedOutputStream(new FileOutputStream(file, append));
        } catch (FileNotFoundException e) {
            SVNErrorManager.error("svn: Cannot write to '" + file + "': "
                    + e.getMessage());
        }
        return null;
    }

    public static InputStream openFileForReading(File file) throws SVNException {
        if (file == null) {
            return null;
        }
        if (!file.isFile() || !file.canRead()) {
            SVNErrorManager.error("svn: Cannot read from '" + file + "': path refers to directory or read access denied");
        }
        if (!file.exists()) {
            return DUMMY_IN;
        }
        try {
            return new BufferedInputStream(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            SVNErrorManager.error("svn: Cannot read from '" + file + "': " + e.getMessage());
        }
        return null;
    }

    public static void closeFile(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            is.close();
        } catch (IOException e) {
            //
        }
    }

    public static void closeFile(OutputStream os) {
        if (os == null) {
            return;
        }
        try {
            os.close();
        } catch (IOException e) {
            //
        }
    }
    
    private static String execCommand(String[] commandLine) {
        return execCommand(commandLine, false);
    }

    private static String execCommand(String[] commandLine, boolean waitAfterRead) {
        InputStream is = null;
        StringBuffer result = new StringBuffer();
        try {
            Process process = Runtime.getRuntime().exec(commandLine);
            is = process.getInputStream();
            if (!waitAfterRead) {
                int rc = process.waitFor();
                if (rc != 0) {
                    return null;
                }
            }
            int r;            
            while ((r = is.read()) >= 0) {
                result.append((char) (r & 0xFF));
            }
            if (waitAfterRead) {
                int rc = process.waitFor();
                if (rc != 0) {
                    return null;
                }
            }
            return result.toString().trim();
        } catch (IOException e) {
            SVNDebugLog.logInfo(e);
        } catch (InterruptedException e) {
            SVNDebugLog.logInfo(e);
        } finally {
            closeFile(is);
        }
        return null;
    }

    private static String getCurrentUser() {
        if (isWindows) {
            return System.getProperty("user.name");
        }
        if (ourUserID == null) {
            ourUserID = execCommand(new String[] { "id", "-u" });
            if (ourUserID == null) {
                ourUserID = "0";
            }
        }
        return ourUserID;
    }

    private static String getCurrentGroup() {
        if (isWindows) {
            return System.getProperty("user.name");
        }
        if (ourGroupID == null) {
            ourGroupID = execCommand(new String[] { "id", "-g" });
            if (ourGroupID == null) {
                ourGroupID = "0";
            }
        }
        return ourGroupID;
    }

    public static void closeFile(Writer os) {
        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
                //
            }
        }
    }

    public static void closeFile(Reader is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                //
            }
        }
    }
    
    public static File getApplicationDataPath() {
        if (ourAppDataPath != null) {
            return ourAppDataPath;
        }
        Method getEnvMethod = null;
        try {
            getEnvMethod = System.class.getMethod("getenv", new Class[] {String.class});
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        }
        if (getEnvMethod != null) {
            try {
                String path = (String) getEnvMethod.invoke(null, new Object[] {"APPDATA"});
                if (path != null) {
                    ourAppDataPath = new File(path);
                    return new File(path);
                }
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        if (ourAppDataPath == null) {
            String[] cmd = new String[] {"cmd.exe", "/D", "/C", "set", "APPDATA"};
            String result = execCommand(cmd, true);
            if (result != null && result.startsWith("APPDATA=")) {
                result = result.substring("APPDATA=".length());
                if (result.indexOf("\r") >= 0) {
                    result = result.substring(0, result.indexOf("\r"));
                }
                ourAppDataPath = new File(result.trim());
            } else {
                ourAppDataPath = new File(new File(System.getProperty("user.home")), "Application Data");
            }
        }
        return ourAppDataPath;
    }
}
