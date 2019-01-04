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

import java.io.BufferedReader;
import java.io.Reader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility functions for backup/restore code
 *
 * @author pjw
 */
public final class XmlUtils {

    public static final String UTF8 = "utf8";
    public static final int BUFFER_SIZE = 32768;

    /** root element, used to recognise 'our' files during import. FIXME: not used in all places yet due to backward compat */
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

    static final String XML_INFO_LIST = "info-list";
    static final String XML_INFO = "info";

    static final String XML_PREFERENCES_LIST = "preferences-list";
    static final String XML_PREFERENCES = "preferences";

    /** item, value stored in the item body */
    static final String XML_STRING = "string";
    /** item; value is an attribute */
    static final String XML_BOOLEAN = "boolean";
    /** item; value is an attribute */
    static final String XML_INT = "int";
    /** item; value is an attribute */
    static final String XML_LONG = "long";
    /** item; value is an attribute */
    static final String XML_FLOAT = "float";
    /** item; value is an attribute */
    static final String XML_DOUBLE = "double";
    /** item, value stored in the item body */
    static final String XML_SET = "set";
    /** item, value stored in the item body */
    static final String XML_SERIALIZABLE = "serializable";


    private XmlUtils() {
    }

    /**
     * escape reserved XML characters + all newlines/tab and the backslash
     *
     * quot	"	U+0022 (34)	XML 1.0	double quotation mark
     * amp	&	U+0026 (38)	XML 1.0	ampersand
     * apos	'	U+0027 (39)	XML 1.0	apostrophe (apostrophe-quote)
     * lt	<	U+003C (60)	XML 1.0	less-than sign
     * gt	>	U+003E (62)	XML 1.0	greater-than sign
     *
     * @param data to encode
     *
     * @return The encoded data
     */
    @NonNull
    public static String encode(@Nullable final String data) {
        try {
            if (data == null || "null".equalsIgnoreCase(data) || data.trim().isEmpty()) {
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
     *
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
     * The sax parser closes streams, which is not good on a Tar archive entry.
     *
     * @author pjw
     */
    public static class BufferedReaderNoClose
        extends BufferedReader {

        public BufferedReaderNoClose(@NonNull final Reader in,
                                     @SuppressWarnings("SameParameterValue") final int flags) {
            super(in, flags);
        }

        @Override
        public void close() {
            // ignore the close call from the SAX parser. We'll close it ourselves when appropriate.
        }
    }
}
