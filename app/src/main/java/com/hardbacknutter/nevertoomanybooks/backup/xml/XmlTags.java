/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

/**
 * Tag names for xml import and export.
 */
final class XmlTags {

    /** root element, used to recognise 'our' files during import. */
    static final String XML_ROOT = "NeverTooManyBooks";

    static final String TAG_BOOKSHELF_LIST = "bookshelf-list";
    static final String TAG_BOOKSHELF = "bookshelf";

    static final String TAG_AUTHOR_LIST = "author-list";
    static final String TAG_AUTHOR = "author";

    static final String TAG_SERIES_LIST = "series-list";
    static final String TAG_SERIES = "series";

    static final String TAG_PUBLISHER_LIST = "publisher-list";
    static final String TAG_PUBLISHER = "publisher";

    static final String TAG_TOC_ENTRY_LIST = "tocentry-list";
    static final String TAG_TOC_ENTRY = "tocentry";

    static final String TAG_BOOK_LIST = "book-list";
    static final String TAG_BOOK = "book";

    static final String TAG_STYLE_LIST = "style-list";
    static final String TAG_STYLE = "style";

    static final String TAG_GROUP_LIST = "group-list";
    static final String TAG_GROUP = "group";

    static final String TAG_FILTER_LIST = "filter-list";
    static final String TAG_FILTER = "filter";

    static final String TAG_INFO_LIST = "info-list";
    static final String TAG_INFO = "info";

    static final String TAG_PREFERENCES_LIST = "preferences-list";
    static final String TAG_PREFERENCES = "preferences";

    private XmlTags() {
    }

}
