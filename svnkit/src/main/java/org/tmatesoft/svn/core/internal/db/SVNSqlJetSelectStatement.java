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
package org.tmatesoft.svn.core.internal.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetValueType;
import org.tmatesoft.sqljet.core.internal.ISqlJetMemoryPointer;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnDef;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNSqlJetSelectStatement extends SVNSqlJetTableStatement {

    private String indexName;
    private Map<String, Object> rowValues;

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, Enum<?> fromTable) throws SVNException {
        this(sDb, fromTable.toString());
    }

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, Enum<?> fromTable, Enum<?> indexName) throws SVNException {
        this(sDb, fromTable.toString(), indexName != null ? indexName.toString() : null);
    }

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, String fromTable) throws SVNException {
        super(sDb, fromTable);
    }

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, String fromTable, String indexName) throws SVNException {
        this(sDb, fromTable);
        this.indexName = indexName;
    }

    protected ISqlJetCursor openCursor() throws SVNException {
        try {
            return getTable().lookup(getIndexName(), getWhere());
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    protected String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    protected Object[] getWhere() throws SVNException {
        if (binds.size() == 0) {
            return null;
        }
        return binds.toArray();
    }

    public boolean next() throws SVNException {
        boolean next = super.next();
        loadRowValues(next);
        while (next && !isFilterPassed()) {
            next = super.next();
            loadRowValues(next);
        }
        return next;
    }

    protected boolean isFilterPassed() throws SVNException {
        return true;
    }

    public boolean eof() throws SVNException {
        boolean eof = super.eof();
        loadRowValues(!eof);
        while (!eof && !isFilterPassed()) {
            eof = !super.next();
            loadRowValues(!eof);
        }
        return eof;
    }

    private void loadRowValues(boolean has) throws SVNException {
        if (has) {
            rowValues = getRowValues2(rowValues);
        } else if (rowValues != null) {
            rowValues.clear();
        }
    }

    public Map<String, Object> getRowValues2(Map<String, Object> v) throws SVNException {
        v = v == null ? new HashMap<String, Object>() : v;
        try {
            Object[] values = getCursor().getRowValues();
            List<ISqlJetColumnDef> columns = getTable().getDefinition().getColumns();
            for (int i = 0; i < values.length; i++) {
                String colName = columns.get(i).getName();
                v.put(colName, values[i]);
            }
            return v;
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public Map<String, Object> getRowValues() throws SVNException {
        HashMap<String, Object> v = new HashMap<String, Object>();
        try {
            List<ISqlJetColumnDef> columns = getTable().getDefinition().getColumns();
            for (ISqlJetColumnDef column : columns) {
                String colName = column.getName();
                SqlJetValueType fieldType = getCursor().getFieldType(colName);
                if (fieldType == SqlJetValueType.NULL) {
                    v.put(colName, null);
                } else if (fieldType == SqlJetValueType.BLOB) {
                    v.put(colName, getCursor().getBlobAsArray(colName));
                } else {
                    v.put(colName, getCursor().getValue(colName));
                }
            }
            return v;
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    @Override
    protected Object getColumn(String f) throws SVNException {
        return rowValues != null ? rowValues.get(f) : null;
    }
    @Override
    protected long getColumnLong(String f) throws SVNException {
        if (rowValues == null) {
            return 0;
        }
        if (rowValues.get(f) == null) {
            return 0;
        }
        return (Long) rowValues.get(f);
    }

    @Override
    protected String getColumnString(String f) throws SVNException {
        if (rowValues == null) {
            return null;
        }
        return (String) rowValues.get(f);
    }

    @Override
    protected boolean isColumnNull(String f) throws SVNException {
        if (rowValues == null) {
            return true;
        }
        return rowValues.get(f) == null;
    }

    @Override
    protected byte[] getColumnBlob(String f) throws SVNException {
        if (rowValues == null) {
            return null;
        }
        ISqlJetMemoryPointer buffer = (ISqlJetMemoryPointer) rowValues.get(f);
        return buffer != null ? SqlJetUtility.readByteBuffer(buffer) : null;
    }
}
