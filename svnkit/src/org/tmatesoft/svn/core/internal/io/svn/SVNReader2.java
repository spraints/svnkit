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
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNReader2 {

    private static final String DEAFAULT_ERROR_TEMPLATE = "nccn";
    private static final String DEFAULT_TEMPLATE = "wl";

    public static Date getDate(List items, int index) {
        String str = getString(items, index);
        return SVNTimeUtil.parseDate(str);
    }

    public static long getLong(List items, int index) {
        if (items == null || index >= items.size()) {
            return -1;
        }
        if (items.get(index) instanceof Long) {
            return ((Long) items.get(index)).longValue();
        } else if (items.get(index) instanceof Integer) {
            return ((Integer) items.get(index)).intValue();
        }
        return -1;
    }

    public static boolean getBoolean(List items, int index) {
        if (items == null || index >= items.size()) {
            return false;
        }
        if (items.get(index) instanceof Boolean) {
            return ((Boolean) items.get(index)).booleanValue();
        } else if (items.get(index) instanceof String) {
            return Boolean.valueOf((String) items.get(index)).booleanValue();
        }
        return false;

    }

//    public static Map getMap(List items, int index) {
//        if (items == null || index >= items.size()) {
//            return Collections.EMPTY_MAP;
//        }
//        if (items.get(index) instanceof Map) {
//            return (Map) items.get(index);
//        }
//        return Collections.EMPTY_MAP;
//    }

    public static List getList(List items, int index) {
        if (items == null || index >= items.size()) {
            return Collections.EMPTY_LIST;
        }
        if (items.get(index) instanceof List) {
            List list = (List) items.get(index);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof SVNItem) {
                    SVNItem item = (SVNItem) list.get(i);
                    if (item.getKind() == SVNItem.STRING) {
                        list.set(i, item.getLine());
                    } else if (item.getKind() == SVNItem.WORD) {
                        list.set(i, item.getWord());
                    } else if (item.getKind() == SVNItem.NUMBER) {
                        list.set(i, new Long(item.getNumber()));
                    } else if (item.getKind() == SVNItem.LIST) {
                        list.set(i, getList(list, i));
                    }
                }
            }
            return list;
        }
        return Collections.EMPTY_LIST;
    }

    public static Map getProperties(List items, int index, Map properties) throws SVNException {
        List props = getList(items, index);
        if (props == null) {
            return properties;
        }

        properties = properties == null ? new HashMap() : properties;
        for (Iterator prop = props.iterator(); prop.hasNext();) {
            SVNItem item = (SVNItem) prop.next();
            if (item.getKind() != SVNItem.LIST) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Proplist element not a list");
                SVNErrorManager.error(err);
            }
            List propItems = parseTuple("cs", item.getItems(), null);
            properties.put(getString(propItems, 0), getString(propItems, 1));
        }
        return properties;
    }

    public static String getString(List items, int index) {
        if (items == null || index >= items.size()) {
            return null;
        }
        if (items.get(index) instanceof byte[]) {
            try {
                return new String((byte[]) items.get(index), "UTF-8");
            } catch (IOException e) {
                return null;
            }
        } else if (items.get(index) instanceof String) {
            return (String) items.get(index);
        }
        return null;
    }

    public static boolean hasValue(List items, int index, boolean value) {
        return hasValue(items, index, Boolean.valueOf(value));
    }

    public static boolean hasValue(List items, int index, int value) {
        return hasValue(items, index, new Long(value));
    }

    public static boolean hasValue(List items, int index, Object value) {
        if (items == null || index >= items.size()) {
            return false;
        }
        if (items.get(index) instanceof List) {
            // look in list.
            for (Iterator iter = ((List) items.get(index)).iterator(); iter.hasNext();) {
                Object element = iter.next();
                if (element.equals(value)) {
                    return true;
                }
            }
        } else {
            if (items.get(index) == null) {
                return value == null;
            }
            if (items.get(index) instanceof byte[] && value instanceof String) {
                try {
                    items.set(index, new String((byte[]) items.get(index), "UTF-8"));
                } catch (IOException e) {
                    return false;
                }
            }
            return items.get(index).equals(value);
        }
        return false;
    }

    public static SVNItem readItem(InputStream is) throws SVNException {
        char ch = skipWhiteSpace(is);
        return readItem(is, null, ch);
    }

    public static List parse(InputStream is, String template, List values) throws SVNException {
        List readItems = readTuple(is, DEFAULT_TEMPLATE);
        String word = (String) readItems.get(0);
        List list = (List) readItems.get(1);

        if ("success".equals(word)) {
            return parseTuple(template, list, values);
        } else if ("failure".equals(word)) {
            handleFailureStatus(list);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return null;
    }

    private static void handleFailureStatus(List list) throws SVNException {
        if (list.size() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Empty error list");
            SVNErrorManager.error(err);
        }
        SVNErrorMessage topError = getErrorMessage((SVNItem) list.get(list.size() - 1));
        SVNErrorMessage parentError = topError;
        for (int i = list.size() - 2; i >= 0; i++) {
            SVNItem item = (SVNItem) list.get(i);
            SVNErrorMessage error = getErrorMessage(item);
            parentError.setChildErrorMessage(error);
            parentError = error;
        }
        SVNErrorManager.error(topError);
    }

    private static SVNErrorMessage getErrorMessage(SVNItem item) throws SVNException {
        if (item.getKind() != SVNItem.LIST) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed error list");
            SVNErrorManager.error(err);
        }
        List errorItems = parseTuple(DEAFAULT_ERROR_TEMPLATE, item.getItems(), null);
        int code = ((Long) errorItems.get(0)).intValue();
        SVNErrorCode errorCode = SVNErrorCode.getErrorCode(code);
        String errorMessage = (String) errorItems.get(1);
        errorMessage = errorMessage == null ? "" : errorMessage;
        //errorItems contains 2 items more (file and line) but native svn uses them only for debugging purposes.
        //May be we should use another error template.
        return SVNErrorMessage.create(errorCode, errorMessage);
    }

    private static List readTuple(InputStream is, String template) throws SVNException {
        char ch = readChar(is);
        SVNItem item = readItem(is, null, ch);
        System.out.println(item);
        if (item.getKind() != SVNItem.LIST) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return parseTuple(template, item.getItems(), null);
    }

    public static List parseTuple(String template, Collection items, List values) throws SVNException {
        values = values == null ? new ArrayList() : values;
        int index = 0;
        for (Iterator iterator = items.iterator(); iterator.hasNext() && index < template.length(); index++) {
            SVNItem item = (SVNItem) iterator.next();
            char ch = template.charAt(index);
            if (ch == '?') {
                index++;
                ch = template.charAt(index);
            }

            if ((ch == 'n' || ch == 'r') && item.getKind() == SVNItem.NUMBER) {
                values.add(new Long(item.getNumber()));
            } else if (ch == 's' && item.getKind() == SVNItem.STRING) {
                values.add(item.getLine());
            } else if (ch == 'c' && item.getKind() == SVNItem.STRING) {
                values.add(item.getLine().getBytes());
            } else if (ch == 'w' && item.getKind() == SVNItem.WORD) {
                values.add(item.getWord());
            } else if ((ch == 'b' || ch == 'B') && item.getKind() == SVNItem.WORD) {
                if (String.valueOf(true).equals(item.getWord())) {
                    values.add(Boolean.TRUE);
                } else if (String.valueOf(false).equals(item.getWord())) {
                    values.add(Boolean.FALSE);
                } else {
                    break;
                }
            } else if (ch == 'l' && item.getKind() == SVNItem.LIST) {
                values.add(item.getItems());
            } else if (ch == '(' && item.getKind() == SVNItem.LIST) {
                index++;
                values = parseTuple(template.substring(index), item.getItems(), values);
            } else if (ch == ')') {
                return values;
            } else {
                break;
            }
        }
        if (index < template.length() && template.charAt(index) == '?') {
            int nestingLevel = 0;
            while (index < template.length()) {
                switch (template.charAt(index)) {
                    case'?':
                        break;
                    case'r':
                        values.add(new Long(SVNRepository.INVALID_REVISION));
                        break;
                    case's':
                    case'c':
                    case'w':
                    case'l':
                        values.add(null);
                        break;
                    case'B':
                    case'n':
                    case'(':
                        nestingLevel++;
                        break;
                    case')':
                        nestingLevel--;
                        if (nestingLevel < 0) {
                            return values;
                        }
                        break;
                    default:
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                        SVNErrorManager.error(err);
                }
                index++;
            }
        }
        if (index == (template.length() - 1) && template.charAt(index) != ')') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return values;
    }

    private static SVNItem readItem(InputStream is, SVNItem item, char ch) throws SVNException {
        if (item == null) {
            item = new SVNItem();
        }
        if (Character.isDigit(ch)) {
            long value = Character.digit(ch, 10);
            long previousValue;
            while (true) {
                previousValue = value;
                ch = readChar(is);
                if (Character.isDigit(ch)) {
                    value = value * 10 + Character.digit(ch, 10);
                    if (previousValue != value / 10) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Number is larger than maximum");
                        SVNErrorManager.error(err);
                    }
                    continue;
                }
                break;
            }
            if (ch == ':') {
                // string.
                byte[] buffer = new byte[(int) value];
                try {
                    int toRead = (int) value;
                    while (toRead > 0) {
                        int r = is.read(buffer, buffer.length - toRead, toRead);
                        if (r <= 0) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                            SVNErrorManager.error(err);
                        }
                        toRead -= r;
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                    SVNErrorManager.error(err);
                }
                item.setKind(SVNItem.STRING);
                try {
                    item.setLine(new String(buffer, 0, buffer.length, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                    SVNErrorManager.error(err);
                }
                ch = readChar(is);
            } else {
                // number.
                item.setKind(SVNItem.NUMBER);
                item.setNumber(value);
            }
        } else if (Character.isLetter(ch)) {
            StringBuffer buffer = new StringBuffer();
            buffer.append(ch);
            while (true) {
                ch = readChar(is);
                if (Character.isLetterOrDigit(ch) && ch != '-') {
                    buffer.append(ch);
                    continue;
                }
                break;
            }
            item.setKind(SVNItem.WORD);
            item.setWord(buffer.toString());
        } else if (ch == '(') {
            item.setKind(SVNItem.LIST);
            item.setItems(new ArrayList());
            while (true) {
                ch = skipWhiteSpace(is);
                if (ch == ')') {
                    break;
                }
                SVNItem child = new SVNItem();
                item.getItems().add(child);
                readItem(is, child, ch);
            }
            ch = readChar(is);
        }
        if (!Character.isWhitespace(ch)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return item;
    }

    private static char readChar(InputStream is) throws SVNException {
        int r = 0;
        try {
            r = is.read();
            if (r < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                SVNErrorManager.error(err);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return (char) (r & 0xFF);
    }

    private static char skipWhiteSpace(InputStream is) throws SVNException {
        while (true) {
            char ch = readChar(is);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            return ch;
        }
    }
}
