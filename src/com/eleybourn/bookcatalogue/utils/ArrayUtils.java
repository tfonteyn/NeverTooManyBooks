package com.eleybourn.bookcatalogue.utils;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.Series;

import java.util.ArrayList;
import java.util.Iterator;

public class ArrayUtils<T> {

    public interface Factory<T> {
        T get(String source);
    }

    private final Factory<T> mFactory;

    ArrayUtils(Factory<T> factory) {
        mFactory = factory;
    }

    private T get(String source) {
        return mFactory.get(source);
    }

    private static ArrayUtils<Author> mAuthorUtils = null;
    private static ArrayUtils<Series> mSeriesUtils = null;

    static public ArrayUtils<Author> getAuthorUtils() {
        if (mAuthorUtils == null) {
            mAuthorUtils = new ArrayUtils<>(new ArrayUtils.Factory<Author>() {
                @Override
                public Author get(String source) {
                    return new Author(source);
                }
            });
        }
        return mAuthorUtils;
    }

    static public ArrayUtils<Series> getSeriesUtils() {
        if (mSeriesUtils == null) {
            mSeriesUtils = new ArrayUtils<>(new ArrayUtils.Factory<Series>() {
                @Override
                public Series get(String source) {
                    return new Series(source);
                }
            });
        }
        return mSeriesUtils;
    }

    /**
     * Encode a list of strings by 'escaping' all instances of: delim, '\', \r, \n. The
     * escape char is '\'.
     *
     * This is used to build text lists separated by 'delim'.
     *
     * @param sa	String to convert
     * @return		Converted string
     */
    public String encodeList(ArrayList<T> sa, char delim) {
        Iterator<T> si = sa.iterator();
        return encodeList(si, delim);
    }

    private String encodeList(Iterator<T> si, char delim) {
        StringBuilder ns = new StringBuilder();
        if (si.hasNext()) {
            ns.append(Utils.encodeListItem(si.next().toString(), delim));
            while (si.hasNext()) {
                ns.append(delim);
                ns.append(Utils.encodeListItem(si.next().toString(), delim));
            }
        }
        return ns.toString();
    }

    /**
     * Decode a text list separated by '|' and encoded by encodeListItem.
     *
     * @param s		String representing the list
     * @return		Array of strings resulting from list
     */
    public ArrayList<T> decodeList(String s, char delim, boolean allowBlank) {
        StringBuilder ns = new StringBuilder();
        ArrayList<T> list = new ArrayList<>();
        if (s == null)
            return list;

        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if (inEsc) {
                switch(c) {
                case '\\':
                    ns.append(c);
                    break;
                case 'r':
                    ns.append('\r');
                    break;
                case 't':
                    ns.append('\t');
                    break;
                case 'n':
                    ns.append('\n');
                    break;
                default:
                    ns.append(c);
                    break;
                }
                inEsc = false;
            } else {
                switch (c) {
                case '\\':
                    inEsc = true;
                    break;
                default:
                    if (c == delim) {
                        String source = ns.toString();
                        if (allowBlank || !source.isEmpty())
                            list.add(get(source));
                        ns.setLength(0);
                        break;
                    } else {
                        ns.append(c);
                        break;
                    }
                }
            }
        }
        // It's important to send back even an empty item.
        String source = ns.toString();
        if (allowBlank || !source.isEmpty())
            list.add(get(source));
        return list;
    }
}
