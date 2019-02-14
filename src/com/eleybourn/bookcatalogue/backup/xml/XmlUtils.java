/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup.xml;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.utils.RTE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Utility functions for backup/restore code.
 *
 * @author pjw
 */
public final class XmlUtils {

    public static final int BUFFER_SIZE = 32768;

    /**
     * root element, used to recognise 'our' files during import.
     * FIXME: not used in all places yet due to backward compat
     */
    static final String XML_ROOT = "bc";

    static final String ATTR_VERSION = "version";

    /** Database row-id. */
    static final String ATTR_ID = "id";

    /** the size of the list of elements. */
    static final String ATTR_SIZE = "size";

    /** element name attribute; i.e. the "thing" we are reading/writing. */
    static final String ATTR_NAME = "name";
    /** the value of the individual item of the "thing". */
    static final String ATTR_VALUE = "value";

    static final String XML_BOOKSHELF_LIST = "bookshelf-list";
    static final String XML_BOOKSHELF = "bookshelf";

    static final String XML_AUTHOR_LIST = "author-list";
    static final String XML_AUTHOR = "author";

    static final String XML_SERIES_LIST = "series-list";
    static final String XML_SERIES = "series";

    static final String XML_BOOK_LIST = "book-list";
    static final String XML_BOOK = "book";

    static final String XML_STYLE_LIST = "style-list";
    static final String XML_STYLE = "style";

    static final String XML_GROUP_LIST = "group-list";
    static final String XML_GROUP = "group";

    static final String XML_FILTER_LIST = "filter-list";
    static final String XML_FILTER = "filter";

    static final String XML_INFO_LIST = "info-list";
    static final String XML_INFO = "info";

    static final String XML_PREFERENCES_LIST = "preferences-list";
    static final String XML_PREFERENCES = "preferences";

    /** item, value stored in the item body. */
    static final String XML_STRING = "string";
    /** item; value is an attribute. */
    static final String XML_BOOLEAN = "boolean";
    /** item; value is an attribute. */
    static final String XML_INT = "int";
    /** item; value is an attribute. */
    static final String XML_LONG = "long";
    /** item; value is an attribute. */
    static final String XML_FLOAT = "float";
    /** item; value is an attribute. */
    static final String XML_DOUBLE = "double";
    /** item, value stored in the item body. */
    static final String XML_SET = "set";
    /** item, value stored in the item body. */
    static final String XML_LIST = "list";
    /** item, value stored in the item body. */
    static final String XML_SERIALIZABLE = "serializable";

    private XmlUtils() {
    }

    /**
     * The 'value' attribute should be encoded if it's a String.
     * If there is a String body, then use {@link #tagWithCData}
     * <p>
     * escape reserved XML characters + all newlines/tab and the backslash.
     * <p>
     * quot "   U+0022 (34) XML 1.0 double quotation mark
     * amp  &   U+0026 (38) XML 1.0 ampersand
     * apos '   U+0027 (39) XML 1.0 apostrophe (apostrophe-quote)
     * lt   <   U+003C (60) XML 1.0 less-than sign
     * gt   >   U+003E (62) XML 1.0 greater-than sign
     *
     * @param data to encode
     *
     * @return The encoded data
     */
    @NonNull
    private static String encode(@Nullable final String data) {
        try {
            if (data == null || data.trim().isEmpty() || "null".equalsIgnoreCase(data)) {
                return "";
            }

            final StringBuilder sb = new StringBuilder();
            int endPos = data.length() - 1;
            int pos = 0;
            while (pos <= endPos) {
                char c = data.charAt(pos);
                switch (c) {
                    case '"':
                        sb.append("&quot;");
                        break;
                    case '&':
                        sb.append("&amp;");
                        break;
                    case '\'':
                        sb.append("&apos;");
                        break;
                    case '<':
                        sb.append("&lt;");
                        break;
                    case '>':
                        sb.append("&gt;");
                        break;

                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        sb.append(c);
                }
                pos++;

            }
            return sb.toString();
        } catch (NullPointerException e) {
            return "\"\"";
        }
    }

    /**
     * counterpart of {@link #encode}
     * <p>
     * see {@link #encode(String)} : only String 'value' tags need decoding.
     * <p>
     * decode the bare essentials only. To decode all possible entities we could add the Apache
     * 'lang' library I suppose.... maybe some day.
     */
    public static String decode(@Nullable final String data) {
        if (data == null || "null".equalsIgnoreCase(data) || data.trim().isEmpty()) {
            return "";
        }

        return data.replace("&quot;", "\"")
                   .replace("&apos;", "'")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   // must be last of the entities
                   .replace("&amp;", "&");

    }

    /* ****************************************************************************************** */

    /**
     * Database row-id.
     *
     * @param id of the item in its table
     *
     * @return string representation of the attribute, with leading space.
     */
    static String id(final long id) {
        return ' ' + ATTR_ID + "=\"" + id + '"';
    }

    static String version(final long version) {
        return ' ' + ATTR_VERSION + "=\"" + version + '"';
    }

    /** element name attribute; i.e. the "thing" we are reading/writing. */
    static String name(@NonNull final String name) {
        return ' ' + ATTR_NAME + "=\"" + name + '"';
    }

    /** the value of the individual item of the "thing". */
    static String value(@NonNull final String value) {
        return ' ' + ATTR_VALUE + "=\"" + encode(value) + '"';
    }

    static String size(final long size) {
        return ' ' + ATTR_SIZE + "=\"" + size + '"';
    }

    static String attr(@NonNull final String attr,
                       final long value) {
        return ' ' + attr + "=\"" + value + '"';
    }

    static String attr(@NonNull final String attr,
                       final double value) {
        return ' ' + attr + "=\"" + value + '"';
    }

    /**
     * Generic String value attribute. The String will be encoded.
     *
     * @param attr  attribute name
     * @param value attribute value, a string
     *
     * @return string representation of the attribute, with leading space.
     */
    static String attr(@NonNull final String attr,
                       @NonNull final String value) {
        return ' ' + attr + "=\"" + encode(value) + '"';
    }

    /* ****************************************************************************************** */

    /**
     * String values are automatically encoded.
     *
     * @return tag with (optional) name and value attribute, empty body
     */
    static String tag(@NonNull final String tag,
                      @Nullable final String name,
                      @NonNull final Object value)
            throws IOException {
        if (value instanceof String) {
            // strings are encoded
            return '<' + tag + (name != null ? name(name) : "")
                    + value(encode(String.valueOf(value))) + "/>\n";
        } else {
            // non-strings as-is; for boolean this means: true,false
            return typedTag(name, value);
        }
    }

    /**
     * No encoding of the value is done here.
     *
     * @return tag with (optional) name attribute and content body
     */
    private static String tagWithBody(@NonNull final String tag,
                                      @Nullable final String name,
                                      @NonNull final Object value) {

        return '<' + tag + (name != null ? name(name) : "") + '>'
                + value
                + "</" + tag + ">\n";
    }

    /**
     * @return tag with (optional) name attribute and CDATA content body
     */
    static String tagWithCData(@NonNull final String tag,
                               @Nullable final String name,
                               @NonNull final String value) {
        return '<' + tag + (name != null ? name(name) : "") + ">\n"
                + "<![CDATA[" + value + "]]>\n"
                + "</" + tag + ">\n";
    }

    /**
     * Encode the given Collection to xml.
     *
     * @param values to encode
     *
     * @return partial xml
     */
    private static String typedCollection(@NonNull final Collection values)
            throws IOException {
        StringBuilder sb = new StringBuilder("\n");
        for (Object value : values) {
            sb.append(typedTag(null, value));
        }
        return sb.toString();
    }

    /**
     * Encodes a single value with a tag equal to the value's type.
     * Strings are CDATA encoded; others use the 'value' attribute.
     *
     * @param name  (optional) attribute for the tag
     * @param value to encode
     *
     * @return xml tag
     */
    static String typedTag(@Nullable final String name,
                           @NonNull final Object value)
            throws IOException {
        if (value instanceof String) {
            return tagWithCData(XML_STRING, name, String.valueOf(value));

        } else if (value instanceof Boolean) {
            return tag(XML_BOOLEAN, name, String.valueOf(value));
        } else if (value instanceof Integer) {
            return tag(XML_INT, name, String.valueOf(value));
        } else if (value instanceof Long) {
            return tag(XML_LONG, name, String.valueOf(value));
        } else if (value instanceof Float) {
            return tag(XML_FLOAT, name, String.valueOf(value));
        } else if (value instanceof Double) {
            return tag(XML_DOUBLE, name, String.valueOf(value));

        } else if (value instanceof Set) {
            return tagWithBody(XML_SET, name, typedCollection((Collection) value));
        } else if (value instanceof List) {
            return tagWithBody(XML_LIST, name, typedCollection((Collection) value));

        } else if (value instanceof Serializable) {
            return tagWithBody(XmlUtils.XML_SERIALIZABLE, name,
                               Base64.encodeToString(convertToBytes(value), Base64.DEFAULT));

        } else {
            //noinspection ConstantConditions
            throw new RTE.IllegalTypeException(value.getClass().getCanonicalName());
        }
    }

    /**
     * Helper for encoding an object to Base64. The android lib wants a byte[]
     *
     * @param object to transform
     *
     * @return the array
     *
     * @throws IOException on failure
     */
    private static byte[] convertToBytes(@NonNull final Object object)
            throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        }
    }
}
