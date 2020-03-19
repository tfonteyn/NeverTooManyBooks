/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.utils.xml;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

/**
 * Minimalist builder for XML tags and attributes.
 */
public final class XmlUtils {

    /** File header. */
    public static final String XML_VERSION_1_0_ENCODING_UTF_8 =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

    /** item, value stored in the item body. */
    public static final String TAG_STRING = "string";
    /** item; value is an attribute. */
    public static final String TAG_BOOLEAN = "boolean";
    /** item; value is an attribute. */
    public static final String TAG_INT = "int";
    /** item; value is an attribute. */
    public static final String TAG_LONG = "long";
    /** item; value is an attribute. */
    public static final String TAG_FLOAT = "float";
    /** item; value is an attribute. */
    public static final String TAG_DOUBLE = "double";
    /** item, value stored in the item body. */
    public static final String TAG_SET = "set";
    /** item, value stored in the item body. */
    public static final String TAG_LIST = "list";
    /** item, value stored in the item body. */
    public static final String TAG_SERIALIZABLE = "serializable";
    public static final String ATTR_VERSION = "version";
    /** Database row ID. */
    public static final String ATTR_ID = "id";
    /** the size of the list of elements. */
    @SuppressWarnings("WeakerAccess")
    public static final String ATTR_SIZE = "size";
    /** element name attribute; i.e. the "thing" we are reading/writing. */
    public static final String ATTR_NAME = "name";
    /** the value of the individual item of the "thing". */
    public static final String ATTR_VALUE = "value";

    private static final Pattern QUOT_PATTERN = Pattern.compile("&quot;", Pattern.LITERAL);
    private static final Pattern APOS_PATTERN = Pattern.compile("&apos;", Pattern.LITERAL);
    private static final Pattern LT_PATTERN = Pattern.compile("&lt;", Pattern.LITERAL);
    private static final Pattern GT_PATTERN = Pattern.compile("&gt;", Pattern.LITERAL);
    private static final Pattern AMP_PATTERN = Pattern.compile("&amp;", Pattern.LITERAL);

    /**
     * Generic tag with (optional) name and value attribute, empty body.
     * String values are automatically encoded.
     *
     * @return the tag, or an empty string if the value was empty.
     *
     * @throws IOException on failure
     */
    public static String tag(@NonNull final String tag,
                             @Nullable final String name,
                             @NonNull final Object value)
            throws IOException {
        if (value instanceof String) {
            String valueString = value.toString();
            if (!valueString.isEmpty()) {
                // strings are encoded
                return '<' + tag + nameAttr(name) + attr(ATTR_VALUE, String.valueOf(value))
                       + "/>\n";
            } else {
                return "";
            }
        } else {
            // non-strings as-is; for boolean this means: true,false
            return typedTag(name, value);
        }
    }

    /**
     * Generic tag with (optional) name attribute and content body.
     * No encoding of the value is done.
     *
     * @return the tag, or an empty string if the value was empty.
     */
    @SuppressWarnings("WeakerAccess")
    public static String tagWithBody(@NonNull final String tag,
                                     @Nullable final String name,
                                     @NonNull final Object value) {

        String valueString = value.toString();
        if (!valueString.isEmpty()) {
            return '<' + tag + nameAttr(name) + '>' + value + "</" + tag + ">\n";
        } else {
            return "";
        }
    }

    /**
     * Generic tag with (optional) name attribute and CDATA content body.
     *
     * @return the tag, or an empty string if the value was empty.
     */
    public static String tagWithCData(@NonNull final String tag,
                                      @Nullable final String name,
                                      @NonNull final String value) {
        if (!value.isEmpty()) {
            return '<' + tag + nameAttr(name) + ">\n"
                   + "<![CDATA[" + value + "]]>\n"
                   + "</" + tag + ">\n";
        } else {
            return "";
        }
    }

    /**
     * Encodes a single value with a tag equal to the value's type.
     * Strings are CDATA encoded; others use the 'value' attribute.
     *
     * <ul>Supported value types:
     *     <li>String</li>
     *     <li>boolean</li>
     *     <li>int</li>
     *     <li>long</li>
     *     <li>float</li>
     *     <li>Set</li>
     *     <li>List</li>
     *     <li>Serializable</li>
     * </ul>
     *
     * @param name  (optional) attribute for the tag
     * @param value to encode
     *
     * @return xml tag
     */
    public static String typedTag(@Nullable final String name,
                                  @NonNull final Object value)
            throws IOException {
        if (value instanceof String) {
            return tagWithCData(TAG_STRING, name, String.valueOf(value));

        } else if (value instanceof Boolean) {
            return tag(TAG_BOOLEAN, name, String.valueOf(value));
        } else if (value instanceof Integer) {
            return tag(TAG_INT, name, String.valueOf(value));
        } else if (value instanceof Long) {
            return tag(TAG_LONG, name, String.valueOf(value));
        } else if (value instanceof Float) {
            return tag(TAG_FLOAT, name, String.valueOf(value));
        } else if (value instanceof Double) {
            return tag(TAG_DOUBLE, name, String.valueOf(value));

        } else if (value instanceof Set) {
            return tagWithBody(TAG_SET, name, typedCollection((Collection) value));
        } else if (value instanceof List) {
            return tagWithBody(TAG_LIST, name, typedCollection((Collection) value));

        } else if (value instanceof Serializable) {
            return tagWithBody(TAG_SERIALIZABLE, name,
                               Base64.encodeToString(convertToBytes(value), Base64.DEFAULT));

        } else {
            throw new UnexpectedValueException(value.getClass().getCanonicalName());
        }
    }

    /**
     * Encode the given Collection to xml. The order is preserved during writing.
     *
     * @param values to encode
     *
     * @return partial xml
     */
    private static String typedCollection(@NonNull final Iterable values)
            throws IOException {
        StringBuilder sb = new StringBuilder("\n");
        for (Object value : values) {
            sb.append(typedTag(null, value));
        }
        return sb.toString();
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

    /**
     * The 'value' attribute should be encoded if it's a String.
     * If there is a String body, use {@link #tagWithCData}
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
                        break;
                }
                pos++;

            }
            return sb.toString();
        } catch (@NonNull final NullPointerException e) {
            return "\"\"";
        }
    }

    /**
     * counterpart of {@link #encode}
     * <p>
     * Only String 'value' tags need decoding.
     * <p>
     * decode the bare essentials only. To decode all possible entities we could add the Apache
     * 'lang' library I suppose.... maybe some day.
     */
    public static String decode(@Nullable final String data) {
        if (data == null || "null".equalsIgnoreCase(data) || data.trim().isEmpty()) {
            return "";
        }

        // must be last of the entities
        String result = data.trim();
        result = AMP_PATTERN.matcher(result).replaceAll("&");
        result = GT_PATTERN.matcher(result).replaceAll(">");
        result = LT_PATTERN.matcher(result).replaceAll("<");
        result = APOS_PATTERN.matcher(result).replaceAll("'");
        result = QUOT_PATTERN.matcher(result).replaceAll("\"");
        return result;
    }

    /**
     * Generic {@code double} attribute.
     * If the incoming value is 0, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a double
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    @SuppressWarnings("SameParameterValue")
    public static String attr(@NonNull final String attr,
                              final double value) {
        if (value != 0) {
            return ' ' + attr + "=\"" + value + '"';
        } else {
            return "";
        }
    }

    /**
     * Generic {@code long} attribute.
     * If the incoming value is 0, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a long
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    public static String attr(@NonNull final String attr,
                              final long value) {
        if (value != 0) {
            return ' ' + attr + "=\"" + value + '"';
        } else {
            return "";
        }
    }

    /**
     * Generic {@code boolean} attribute.
     * <p>
     * If the incoming value is {@code false}, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a boolean
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    public static String attr(@NonNull final String attr,
                              final boolean value) {
        if (value) {
            return ' ' + attr + "=\"true\"";
        } else {
            return "";
        }
    }

    /**
     * Generic {@code String} attribute. The String will be encoded.
     * <p>
     * If the incoming value is empty, an empty string is returned.
     *
     * @param attr  attribute name
     * @param value attribute value, a string
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    public static String attr(@NonNull final String attr,
                              @Nullable final String value) {
        if (value != null && !value.isEmpty()) {
            return ' ' + attr + "=\"" + encode(value) + '"';
        } else {
            return "";
        }
    }

    /**
     * "name" attribute; i.e. the "thing" we are reading/writing.
     * If the incoming value is empty, an empty string is returned.
     *
     * @param value the name
     *
     * @return string representation of the attribute, with leading space; or an empty string.
     */
    public static String nameAttr(@Nullable final String value) {
        if (value != null && !value.isEmpty()) {
            return ' ' + ATTR_NAME + "=\"" + value + '"';
        } else {
            return "";
        }
    }

    public static String versionAttr(final long version) {
        return attr(ATTR_VERSION, version);
    }

    /**
     * Database row ID.
     *
     * @param id of the item in its table
     *
     * @return string representation of the attribute, with leading space.
     */
    public static String idAttr(final long id) {
        return attr(ATTR_ID, id);
    }

    /**
     * "size" attribute; should be used when writing out lists.
     *
     * @param value the size
     *
     * @return string representation of the attribute, with leading space.
     */
    public static String sizeAttr(final long value) {
        return attr(ATTR_SIZE, value);
    }
}
