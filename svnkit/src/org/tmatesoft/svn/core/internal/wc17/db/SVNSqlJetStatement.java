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
package org.tmatesoft.svn.core.internal.wc17.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.internal.table.SqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNSqlJetStatement {

    protected SVNSqlJetDb sDb;
    protected ISqlJetCursor cursor;
    protected List binds = new ArrayList();

    protected ISqlJetCursor openCursor() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public long insert(Object... data) {
        throw new UnsupportedOperationException();
    }

    public SVNSqlJetStatement(SVNSqlJetDb sDb) {
        this.sDb = sDb;
        cursor = null;
    }

    public List getBinds() {
        return binds;
    }
    
    public boolean isNeedsReset() {
        return cursor != null;
    }

    public void reset() throws SVNException {
        binds.clear();
        if (isNeedsReset()) {
            try {
                cursor.close();
            } catch (SqlJetException e) {
                SVNSqlJetDb.createSqlJetError(e);
            } finally {
                try {
                    sDb.getDb().commit();
                } catch (SqlJetException e) {
                    SVNSqlJetDb.createSqlJetError(e);
                }
            }
        }
    }

    public boolean next() throws SVNException {
        try {
            if (cursor == null) {
                sDb.getDb().beginTransaction(SqlJetTransactionMode.READ_ONLY);
                cursor = openCursor();
                return !cursor.eof();
            }
            return cursor.next();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public void bindf(String format, Object... data) {
        // TODO
        binds.addAll(Arrays.asList(data));
    }

    public void bindLong(int i, long v) {
        binds.set(i, v);
    }

    public void bindString(int i, String string) {
        binds.set(i, string);
    }

    public void bindProperties(int i, SVNProperties props) throws SVNException {
        SVNSkel.createPropList(props.asMap()).getData();
        binds.set(i, SVNSkel.createPropList(props.asMap()).getData());
    }

    public void bindChecksumm(int i, SVNChecksum checksum) {
        binds.set(i, checksum.getDigest());
    }

    public void bindBlob(int i, byte[] serialized) {
        binds.set(i, serialized);
    }

    public long getColumnLong(int i) throws SVNException {
        try {
            return cursor.getInteger(i);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return 0;
        }
    }

    public long getColumnLong(String f) throws SVNException {
        try {
            return cursor.getInteger(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return 0;
        }
    }

    public String getColumnString(int i) throws SVNException {
        try {
            return cursor.getString(i);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public String getColumnString(String f) throws SVNException {
        try {
            return cursor.getString(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public boolean isColumnNull(int i) throws SVNException {
        try {
            return cursor.isNull(i);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public boolean isColumnNull(String f) throws SVNException {
        try {
            return cursor.isNull(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

}
