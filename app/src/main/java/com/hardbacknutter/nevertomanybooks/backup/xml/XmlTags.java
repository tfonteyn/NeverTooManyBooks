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
package com.hardbacknutter.nevertomanybooks.backup.xml;

/**
 * Tag names for xml import and export.
 */
final class XmlTags {

    /**
     * root element, used to recognise 'our' files during import.
     */
    static final String XML_ROOT = "NeverToManyBooks";

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

    private XmlTags() {
    }
}
