/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition.TableTypes;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Static definitions of database objects.
 * This is a <strong>mostly</strong> complete representation of the application database.
 *
 * <strong>Note:</strong> Fields 'name' attribute must be in LOWER CASE.
 * <p>
 * TODO: Collated indexes need to be done manually. See {@link DBHelper} #createIndices
 */
@SuppressWarnings("WeakerAccess")
public final class DBDefinitions {

    /* ======================================================================================
     * Basic table definitions with type & alias set.
     * All these should be added to {@link #ALL_TABLES}.
     * ====================================================================================== */

    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF;
    /** Basic table definition. */
    public static final TableDefinition TBL_AUTHORS;
    /** Basic table definition. */
    public static final TableDefinition TBL_SERIES;
    /** Basic table definition. */
    public static final TableDefinition TBL_TOC_ENTRIES;
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKS;
    /** link table. */
    public static final TableDefinition TBL_BOOK_BOOKSHELF;
    /** link table. */
    public static final TableDefinition TBL_BOOK_AUTHOR;
    /** link table. */
    public static final TableDefinition TBL_BOOK_SERIES;
    /** link table. */
    public static final TableDefinition TBL_BOOK_LOANEE;
    /** link table. */
    public static final TableDefinition TBL_BOOK_TOC_ENTRIES;
    /** User defined styles. */
    public static final TableDefinition TBL_BOOKLIST_STYLES;
    /** Full text search; should NOT be added to {@link #ALL_TABLES}. */
    public static final TableDefinition TBL_BOOKS_FTS;
    /** Keeps track of nodes in the list across application restarts. */
    public static final TableDefinition TBL_BOOK_LIST_NODE_SETTINGS;

    /* ======================================================================================
     * Reminder: this is a {@link TableDefinition.TableTypes#Temporary}.
     * Should NOT be added to {@link #ALL_TABLES}.
     * ====================================================================================== */

    public static final TableDefinition TMP_TBL_BOOK_LIST;
    public static final TableDefinition TMP_TBL_ROW_NAVIGATOR;
    /**
     * This table should always be created without column constraints applied,
     * with the exception of the "_id" primary key autoincrement
     */
    public static final TableDefinition TMP_TBL_ROW_NAVIGATOR_FLATTENED;

    /* ======================================================================================
     * Primary and Foreign key domain definitions.
     * ====================================================================================== */
    /** Primary key. */
    public static final DomainDefinition DOM_PK_ID;
    /** FTS Primary key. */
    public static final DomainDefinition DOM_PK_DOCID;

    /** Foreign key. */
    public static final DomainDefinition DOM_FK_AUTHOR;
    /** Foreign key. */
    public static final DomainDefinition DOM_FK_BOOKSHELF;
    /** Foreign key. */
    public static final DomainDefinition DOM_FK_BOOK;
    /** Foreign key. */
    public static final DomainDefinition DOM_FK_SERIES;
    /** Foreign key. */
    public static final DomainDefinition DOM_FK_TOC_ENTRY;
    /**
     * Foreign key.
     * When a style is deleted, this key will be (re)set to
     * {@link BooklistStyle#DEFAULT_STYLE_ID}
     */
    public static final DomainDefinition DOM_FK_STYLE_ID;

    /**
     * Foreign key.
     * Links {@link #TMP_TBL_ROW_NAVIGATOR} and {@link #TMP_TBL_BOOK_LIST}.
     */
    public static final DomainDefinition DOM_FK_BOOK_LIST_TABLE_ROW_ID;

    /* ======================================================================================
     * Domain definitions.
     * ====================================================================================== */
    /** {@link #TBL_BOOKSHELF}. */
    public static final DomainDefinition DOM_BOOKSHELF;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ",', ')". */
    public static final DomainDefinition DOM_BOOKSHELF_CSV;
    /** {@link #TBL_AUTHORS}. */
    public static final DomainDefinition DOM_AUTHOR_IS_COMPLETE;
    /** "FamilyName, GivenName". */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED;
    /** "GivenName FamilyName". */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
    /** {@link #TBL_SERIES}. */
    public static final DomainDefinition DOM_SERIES_TITLE;
    public static final DomainDefinition DOM_SERIES_TITLE_OB;
    /** {@link #TBL_SERIES}. */
    public static final DomainDefinition DOM_SERIES_IS_COMPLETE;
    /** {@link #TBL_SERIES}. */
    public static final DomainDefinition DOM_SERIES_FORMATTED;
    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final DomainDefinition DOM_TITLE;
    /**
     * 'Order By' for the title. Lowercase, and stripped of spaces etc...
     * {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}.
     */
    public static final DomainDefinition DOM_TITLE_OB;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_ISBN;
    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final DomainDefinition DOM_DATE_FIRST_PUBLICATION;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PUBLISHER;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_DATE_PUBLISHED;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRINT_RUN;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRICE_LISTED;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRICE_LISTED_CURRENCY;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PAGES;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_FORMAT;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_LANGUAGE;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_DESCRIPTION;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_UUID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_EDITION_BITMASK;
    /** {@link #TBL_BOOKS}. See {@link Book#TOC_SINGLE_AUTHOR_SINGLE_WORK}. */
    public static final DomainDefinition DOM_BOOK_TOC_BITMASK;
    /**
     * {@link #TBL_BOOKS}.
     * String typed. We cannot rely on prices fetched from the internet to be 100% parsable.
     */
    public static final DomainDefinition DOM_BOOK_PRICE_PAID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRICE_PAID_CURRENCY;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_IS_OWNED;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_DATE_ACQUIRED;
    /** {@link #TBL_BOOKS} added to the collection. */
    public static final DomainDefinition DOM_BOOK_DATE_ADDED;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_DATE_LAST_UPDATED;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_GENRE;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_LOCATION;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_READ;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_READ_START;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_READ_END;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_SIGNED;
    /** {@link #TBL_BOOKS}. A rating goes from 0 to 5 stars, in 0.5 increments. */
    public static final DomainDefinition DOM_BOOK_RATING;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRIVATE_NOTES;
    /** {@link #TBL_BOOKS}. Book ID, not 'work' ID. */
    public static final DomainDefinition DOM_BOOK_GOODREADS_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_ISFDB_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_LIBRARY_THING_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_OPEN_LIBRARY_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_STRIP_INFO_BE_ID;
    /** {@link #TBL_BOOK_SERIES}. */
    public static final DomainDefinition DOM_BOOK_NUM_IN_SERIES;
    /** {@link #TBL_BOOK_LOANEE}. */
    public static final DomainDefinition DOM_LOANEE;
    /**
     * {@link #TBL_BOOK_LOANEE}.
     * Virtual: returns 0 for 'available' or 1 for 'lend out'
     */
    public static final DomainDefinition DOM_LOANEE_AS_BOOLEAN;


    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final DomainDefinition DOM_BOOK_AUTHOR_TYPE_BITMASK;
    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final DomainDefinition DOM_BOOK_AUTHOR_POSITION;

    /** {@link #TBL_AUTHORS}. */
    public static final DomainDefinition DOM_AUTHOR_FAMILY_NAME;
    public static final DomainDefinition DOM_AUTHOR_FAMILY_NAME_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final DomainDefinition DOM_AUTHOR_GIVEN_NAMES;
    public static final DomainDefinition DOM_AUTHOR_GIVEN_NAMES_OB;

    /** {@link #TBL_BOOK_TOC_ENTRIES}. */
    public static final DomainDefinition DOM_BOOK_TOC_ENTRY_POSITION;
    public static final DomainDefinition DOM_STYLE_IS_BUILTIN;


    /**
     * {@link #TBL_BOOK_SERIES}.
     * The Series position is the order the Series show up in a book. Particularly important
     * for "primary series" and in lists where 'all' Series are shown.
     */
    public static final DomainDefinition DOM_BOOK_SERIES_POSITION;
    /** {@link #TBL_BOOKLIST_STYLES} java.util.UUID value stored as a string. */
    public static final DomainDefinition DOM_UUID;


    /** Virtual. The type of a TOC entry. See {@link TocEntry.Type} */
    public static final DomainDefinition DOM_TOC_TYPE;

    /**
     * {@link #TBL_BOOKS_FTS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    public static final DomainDefinition DOM_FTS_AUTHOR_NAME;

    /* ======================================================================================
     *  {@link BooklistGroup.RowKind} domains.
     * ====================================================================================== */
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_AUTHOR_SORT;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_SERIES_SORT;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_READ_STATUS;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_TITLE_LETTER;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_ADDED_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_ADDED_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_ADDED_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_UPDATE_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_UPDATE_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_LAST_UPDATE_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_READ_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_READ_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_READ_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_ACQUIRED_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_ACQUIRED_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_ACQUIRED_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_FIRST_PUBLICATION_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_FIRST_PUBLICATION_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_PUBLISHED_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final DomainDefinition DOM_RK_DATE_PUBLISHED_YEAR;

    /* ======================================================================================
     *  {@link BooklistBuilder} domains.
     * ====================================================================================== */
    /**
     * Series number, cast()'d for sorting purposes in {@link BooklistBuilder}
     * so we can sort it numerically regardless of content.
     */
    public static final DomainDefinition DOM_BL_SERIES_NUM_FLOAT;
    /** {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_ABSOLUTE_POSITION;
    /** {@link #TBL_BOOK_LIST_NODE_SETTINGS} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_NODE_ROW_KIND;
    /**
     * {@link #TBL_BOOK_LIST_NODE_SETTINGS}
     * {@link #TMP_TBL_ROW_NAVIGATOR}
     * {@link BooklistBuilder}.
     */
    public static final DomainDefinition DOM_BL_ROOT_KEY;
    /** {@link #TMP_TBL_BOOK_LIST} {@link #TMP_TBL_ROW_NAVIGATOR} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_NODE_LEVEL;
    /** {@link #TMP_TBL_BOOK_LIST} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_BOOK_COUNT;
    /** {@link #TMP_TBL_BOOK_LIST} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_PRIMARY_SERIES_COUNT;
    /**
     * {@link #TMP_TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node expanded.
     * An expanded node, should always be visible!
     */
    public static final DomainDefinition DOM_BL_NODE_EXPANDED;
    /** {@link #TMP_TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node visible. */
    public static final DomainDefinition DOM_BL_NODE_VISIBLE;
    /** {@link BooklistBuilder} the 'selected' book, i.e. the one to scroll back into view. */
    public static final DomainDefinition DOM_BL_NODE_SELECTED;

    /* ======================================================================================
     *  Keys used as domain names and as Bundle keys.
     * ====================================================================================== */

    /** Primary key. */
    public static final String KEY_PK_ID = "_id";
    /** Foreign key. */
    public static final String KEY_FK_BOOK = "book";
    public static final String KEY_FK_AUTHOR = "author";
    public static final String KEY_FK_SERIES = "series_id";
    public static final String KEY_FK_BOOKSHELF = "bookshelf";
    public static final String KEY_FK_TOC_ENTRY = "anthology";
    public static final String KEY_FK_STYLE = "style";
    /** External id. */
    public static final String KEY_EID_GOODREADS_BOOK = "goodreads_book_id";
    public static final String KEY_EID_GOODREADS_LAST_SYNC_DATE = "last_goodreads_sync_date";
    public static final String KEY_EID_ISFDB = "isfdb_book_id";
    public static final String KEY_EID_LIBRARY_THING = "lt_book_id";
    public static final String KEY_EID_OPEN_LIBRARY = "ol_book_id";
    public static final String KEY_EID_STRIP_INFO_BE = "si_book_id";
    //NEWTHINGS: add new site specific ID: add a KEY
    // ENHANCE: the search engines already use these when found, but not stored yet.
    public static final String KEY_EID_ASIN = "asin";
    public static final String KEY_EID_WORLDCAT = "worldcat_oclc_book_id";
    public static final String KEY_EID_LCCN = "lccn_book_id";

    /** {@link #TBL_BOOK_BOOKSHELF}. */
    public static final String KEY_BOOKSHELF = "bookshelf";
    public static final String KEY_BOOKSHELF_CSV = "bookshelves_csv";

    /** {@link #TBL_AUTHORS} {@link #TBL_BOOK_AUTHOR} */
    public static final String KEY_AUTHOR_FAMILY_NAME = "family_name";
    public static final String KEY_AUTHOR_GIVEN_NAMES = "given_names";
    public static final String KEY_AUTHOR_IS_COMPLETE = "author_complete";
    public static final String KEY_AUTHOR_FORMATTED = "author_formatted";
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";
    public static final String KEY_AUTHOR_TYPE = "author_type";
    public static final String KEY_AUTHOR_POSITION = "author_position";

    /** {@link #TBL_SERIES} {@link #TBL_BOOK_SERIES} */
    public static final String KEY_SERIES_TITLE = "series_name";
    public static final String KEY_SERIES_FORMATTED = "series_formatted";
    public static final String KEY_SERIES_IS_COMPLETE = "series_complete";
    public static final String KEY_BOOK_NUM_IN_SERIES = "series_num";

    /** {@link #TBL_TOC_ENTRIES}. */
    public static final String KEY_TOC_TYPE = "type";


    public static final String KEY_DATE_ADDED = "date_added";
    public static final String KEY_DATE_LAST_UPDATED = "last_update_date";
    public static final String KEY_UUID = "uuid";
    public static final String KEY_TITLE = "title";
    public static final String KEY_ISBN = "isbn";
    public static final String KEY_DATE_FIRST_PUBLICATION = "first_publication";
    public static final String KEY_PUBLISHER = "publisher";
    public static final String KEY_DATE_PUBLISHED = "date_published";
    public static final String KEY_PRINT_RUN = "print_run";
    public static final String KEY_PRICE_LISTED = "list_price";
    public static final String KEY_PRICE_LISTED_CURRENCY = "list_price_currency";
    public static final String KEY_PAGES = "pages";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_GENRE = "genre";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_BOOK_UUID = "book_uuid";
    public static final String KEY_EDITION_BITMASK = "edition_bm";
    public static final String KEY_TOC_BITMASK = "anthology";
    /** Personal data. */
    public static final String KEY_PRICE_PAID = "price_paid";
    public static final String KEY_PRICE_PAID_CURRENCY = "price_paid_currency";
    public static final String KEY_OWNED = "owned";
    public static final String KEY_DATE_ACQUIRED = "date_acquired";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_READ = "read";
    public static final String KEY_READ_START = "read_start";
    public static final String KEY_READ_END = "read_end";
    public static final String KEY_SIGNED = "signed";
    public static final String KEY_RATING = "rating";
    public static final String KEY_PRIVATE_NOTES = "notes";
    public static final String KEY_LOANEE = "loaned_to";
    public static final String KEY_LOANEE_AS_BOOLEAN = "loaned_flag";

    /** BooklistBuilder. */
    public static final String KEY_BL_SERIES_NUM_FLOAT = "ser_num_float";
    public static final String KEY_BL_ABSOLUTE_POSITION = "abs_pos";
    public static final String KEY_BL_NODE_ROW_KIND = "kind";
    public static final String KEY_BL_NODE_LEVEL = "level";
    public static final String KEY_BL_BOOK_COUNT = "book_count";
    public static final String KEY_BL_PRIMARY_SERIES_COUNT = "prim_ser_cnt";
    public static final String KEY_BL_ROOT_KEY = "root_key";
    public static final String KEY_BL_REAL_ROW_ID = "real_row_id";
    public static final String KEY_BL_NODE_VISIBLE = "visible";
    public static final String KEY_BL_NODE_EXPANDED = "expanded";
    public static final String KEY_BL_NODE_SELECTED = "selected";
    /**
     * A collection of all tables used to be able to rebuild indexes etc...,
     * added in order so interdependency's work out.
     * <p>
     * Only add standard tables. Do not add the TMP_* tables.
     */
    static final Map<String, TableDefinition> ALL_TABLES = new LinkedHashMap<>();

    static {

        // Suffix added to a column name to create a specific 'order by' copy of that column.
        final String COLUMN_SUFFIX_ORDER_BY = "_ob";

        /* ======================================================================================
         *  Table definitions
         * ====================================================================================== */

        // never change the "authors" "a" alias. It's hardcoded elsewhere.
        TBL_AUTHORS =
                new TableDefinition("authors").setAlias("a");
        // never change the "books" "b" alias. It's hardcoded elsewhere.
        TBL_BOOKS =
                new TableDefinition("books").setAlias("b");
        // never change the "series" "s" alias. It's hardcoded elsewhere.
        TBL_SERIES =
                new TableDefinition("series").setAlias("s");

        TBL_BOOKSHELF =
                new TableDefinition("bookshelf").setAlias("bsh");
        TBL_TOC_ENTRIES =
                new TableDefinition("anthology").setAlias("an");

        TBL_BOOK_BOOKSHELF =
                new TableDefinition("book_bookshelf").setAlias("bbsh");
        TBL_BOOK_AUTHOR =
                new TableDefinition("book_author").setAlias("ba");
        TBL_BOOK_SERIES =
                new TableDefinition("book_series").setAlias("bs");
        TBL_BOOK_LOANEE =
                new TableDefinition("loan").setAlias("l");
        TBL_BOOK_TOC_ENTRIES =
                new TableDefinition("book_anthology").setAlias("bat");

        TBL_BOOKLIST_STYLES =
                new TableDefinition("book_list_styles").setAlias("bls");

        /* ======================================================================================
         *  Primary and Foreign Key definitions
         * ====================================================================================== */

        DOM_PK_ID = new DomainDefinition(KEY_PK_ID);

        DOM_FK_AUTHOR =
                new DomainDefinition(KEY_FK_AUTHOR, ColumnInfo.TYPE_INTEGER, true)
                        .references(TBL_AUTHORS, "ON DELETE CASCADE ON UPDATE CASCADE");
        DOM_FK_BOOKSHELF =
                new DomainDefinition(KEY_FK_BOOKSHELF, ColumnInfo.TYPE_INTEGER, true)
                        .references(TBL_BOOKSHELF, "ON DELETE CASCADE ON UPDATE CASCADE");
        DOM_FK_BOOK =
                new DomainDefinition(KEY_FK_BOOK, ColumnInfo.TYPE_INTEGER, true)
                        .references(TBL_BOOKS, "ON DELETE CASCADE ON UPDATE CASCADE");
        DOM_FK_SERIES =
                new DomainDefinition(KEY_FK_SERIES, ColumnInfo.TYPE_INTEGER, true)
                        .references(TBL_SERIES, "ON DELETE CASCADE ON UPDATE CASCADE");
        DOM_FK_TOC_ENTRY =
                new DomainDefinition(KEY_FK_TOC_ENTRY, ColumnInfo.TYPE_INTEGER, true)
                        .references(TBL_TOC_ENTRIES, "ON DELETE CASCADE ON UPDATE CASCADE");
        DOM_FK_STYLE_ID =
                new DomainDefinition(KEY_FK_STYLE, ColumnInfo.TYPE_INTEGER, true)
                        .setDefault(BooklistStyle.DEFAULT_STYLE_ID)
                        .references(TBL_BOOKLIST_STYLES, "ON DELETE SET DEFAULT ON UPDATE CASCADE");

        /* ======================================================================================
         *  Multi table domains
         * ====================================================================================== */

        DOM_TITLE =
                new DomainDefinition(KEY_TITLE, ColumnInfo.TYPE_TEXT, true);
        DOM_TITLE_OB =
                new DomainDefinition(KEY_TITLE + COLUMN_SUFFIX_ORDER_BY, ColumnInfo.TYPE_TEXT, true)
                        .setPrePreparedOrderBy(true)
                        .setDefaultEmptyString();

        DOM_DATE_FIRST_PUBLICATION =
                new DomainDefinition(KEY_DATE_FIRST_PUBLICATION, ColumnInfo.TYPE_DATE, true)
                        .setDefaultEmptyString();

        DOM_DATE_LAST_UPDATED =
                new DomainDefinition(KEY_DATE_LAST_UPDATED, ColumnInfo.TYPE_DATETIME, true)
                        .setDefault("current_timestamp");

        /* ======================================================================================
         *  Bookshelf domains
         * ====================================================================================== */

        DOM_BOOKSHELF =
                new DomainDefinition(KEY_BOOKSHELF, ColumnInfo.TYPE_TEXT, true);
        DOM_BOOKSHELF_CSV =
                new DomainDefinition(KEY_BOOKSHELF_CSV, ColumnInfo.TYPE_TEXT, true);

        /* ======================================================================================
         *  Author domains
         * ====================================================================================== */

        DOM_AUTHOR_FAMILY_NAME =
                new DomainDefinition(KEY_AUTHOR_FAMILY_NAME, ColumnInfo.TYPE_TEXT, true);

        DOM_AUTHOR_FAMILY_NAME_OB =
                new DomainDefinition(KEY_AUTHOR_FAMILY_NAME + COLUMN_SUFFIX_ORDER_BY,
                                     ColumnInfo.TYPE_TEXT, true)
                        .setPrePreparedOrderBy(true)
                        .setDefaultEmptyString();

        DOM_AUTHOR_GIVEN_NAMES =
                new DomainDefinition(KEY_AUTHOR_GIVEN_NAMES, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();

        DOM_AUTHOR_GIVEN_NAMES_OB =
                new DomainDefinition(KEY_AUTHOR_GIVEN_NAMES + COLUMN_SUFFIX_ORDER_BY,
                                     ColumnInfo.TYPE_TEXT, true)
                        .setPrePreparedOrderBy(true)
                        .setDefaultEmptyString();

        DOM_AUTHOR_IS_COMPLETE =
                new DomainDefinition(KEY_AUTHOR_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);

        DOM_AUTHOR_FORMATTED =
                new DomainDefinition(KEY_AUTHOR_FORMATTED, ColumnInfo.TYPE_TEXT, true);

        DOM_AUTHOR_FORMATTED_GIVEN_FIRST =
                new DomainDefinition(KEY_AUTHOR_FORMATTED_GIVEN_FIRST, ColumnInfo.TYPE_TEXT, true);

        /* ======================================================================================
         *  Series domains
         * ====================================================================================== */

        DOM_SERIES_TITLE =
                new DomainDefinition(KEY_SERIES_TITLE, ColumnInfo.TYPE_TEXT, true);
        DOM_SERIES_TITLE_OB =
                new DomainDefinition(KEY_SERIES_TITLE + COLUMN_SUFFIX_ORDER_BY,
                                     ColumnInfo.TYPE_TEXT, true)
                        .setPrePreparedOrderBy(true)
                        .setDefaultEmptyString();
        DOM_SERIES_IS_COMPLETE =
                new DomainDefinition(KEY_SERIES_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);
        DOM_SERIES_FORMATTED =
                new DomainDefinition(KEY_SERIES_FORMATTED, ColumnInfo.TYPE_TEXT, true);

        /* ======================================================================================
         *  Book domains
         * ====================================================================================== */

        DOM_BOOK_ISBN =
                new DomainDefinition(KEY_ISBN, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();

        DOM_BOOK_PUBLISHER =
                new DomainDefinition(KEY_PUBLISHER, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_DATE_PUBLISHED =
                new DomainDefinition(KEY_DATE_PUBLISHED, ColumnInfo.TYPE_DATE, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PRINT_RUN =
                new DomainDefinition(KEY_PRINT_RUN, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PRICE_LISTED =
                new DomainDefinition(KEY_PRICE_LISTED, ColumnInfo.TYPE_REAL, true)
                        .setDefault(0d);
        DOM_BOOK_PRICE_LISTED_CURRENCY =
                new DomainDefinition(KEY_PRICE_LISTED_CURRENCY, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PAGES =
                new DomainDefinition(KEY_PAGES, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_FORMAT =
                new DomainDefinition(KEY_FORMAT, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_LANGUAGE =
                new DomainDefinition(KEY_LANGUAGE, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_GENRE =
                new DomainDefinition(KEY_GENRE, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_DESCRIPTION =
                new DomainDefinition(KEY_DESCRIPTION, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();

        DOM_BOOK_TOC_BITMASK =
                new DomainDefinition(KEY_TOC_BITMASK, ColumnInfo.TYPE_INTEGER, true)
                        .setDefault(Book.TOC_SINGLE_AUTHOR_SINGLE_WORK);

        /* ======================================================================================
         *  Book personal data domains
         * ====================================================================================== */

        DOM_BOOK_UUID =
                new DomainDefinition(KEY_BOOK_UUID, ColumnInfo.TYPE_TEXT, true)
                        .setDefault("(lower(hex(randomblob(16))))");

        DOM_BOOK_EDITION_BITMASK =
                new DomainDefinition(KEY_EDITION_BITMASK, ColumnInfo.TYPE_INTEGER, true)
                        .setDefault(0);

        DOM_BOOK_IS_OWNED =
                new DomainDefinition(KEY_OWNED, ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);
        DOM_BOOK_DATE_ACQUIRED =
                new DomainDefinition(KEY_DATE_ACQUIRED, ColumnInfo.TYPE_DATE, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PRICE_PAID =
                new DomainDefinition(KEY_PRICE_PAID, ColumnInfo.TYPE_REAL, true)
                        .setDefault(0d);
        DOM_BOOK_PRICE_PAID_CURRENCY =
                new DomainDefinition(KEY_PRICE_PAID_CURRENCY, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();

        DOM_BOOK_DATE_ADDED =
                new DomainDefinition(KEY_DATE_ADDED, ColumnInfo.TYPE_DATETIME, true)
                        .setDefault("current_timestamp");

        DOM_BOOK_LOCATION =
                new DomainDefinition(KEY_LOCATION, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_READ =
                new DomainDefinition(KEY_READ, ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);
        DOM_BOOK_READ_START =
                new DomainDefinition(KEY_READ_START, ColumnInfo.TYPE_DATE, true)
                        .setDefaultEmptyString();
        DOM_BOOK_READ_END =
                new DomainDefinition(KEY_READ_END, ColumnInfo.TYPE_DATE, true)
                        .setDefaultEmptyString();
        DOM_BOOK_SIGNED =
                new DomainDefinition(KEY_SIGNED, ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);
        DOM_BOOK_RATING =
                new DomainDefinition(KEY_RATING, ColumnInfo.TYPE_REAL, true)
                        .setDefault(0);
        DOM_BOOK_PRIVATE_NOTES =
                new DomainDefinition(KEY_PRIVATE_NOTES, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();

        /* ======================================================================================
         *  Book external website id domains
         * ====================================================================================== */
        //NEWTHINGS: add new site specific ID: add a DOM
        DOM_BOOK_GOODREADS_ID =
                new DomainDefinition(KEY_EID_GOODREADS_BOOK, ColumnInfo.TYPE_INTEGER);
        DOM_BOOK_GOODREADS_LAST_SYNC_DATE =
                new DomainDefinition(KEY_EID_GOODREADS_LAST_SYNC_DATE, ColumnInfo.TYPE_DATE)
                        .setDefault("'0000-00-00'");

        DOM_BOOK_ISFDB_ID =
                new DomainDefinition(KEY_EID_ISFDB, ColumnInfo.TYPE_INTEGER);
        DOM_BOOK_LIBRARY_THING_ID =
                new DomainDefinition(KEY_EID_LIBRARY_THING, ColumnInfo.TYPE_INTEGER);
        DOM_BOOK_OPEN_LIBRARY_ID =
                new DomainDefinition(KEY_EID_OPEN_LIBRARY, ColumnInfo.TYPE_TEXT);
        DOM_BOOK_STRIP_INFO_BE_ID =
                new DomainDefinition(KEY_EID_STRIP_INFO_BE, ColumnInfo.TYPE_INTEGER);


        /* ======================================================================================
         *  Loanee domains
         * ====================================================================================== */

        DOM_LOANEE =
                new DomainDefinition(KEY_LOANEE, ColumnInfo.TYPE_TEXT, true);
        DOM_LOANEE_AS_BOOLEAN =
                new DomainDefinition(KEY_LOANEE_AS_BOOLEAN, ColumnInfo.TYPE_INTEGER, true);

        /* ======================================================================================
         *  TOC domains
         * ====================================================================================== */

        DOM_TOC_TYPE =
                new DomainDefinition(KEY_TOC_TYPE, ColumnInfo.TYPE_TEXT);

        /* ======================================================================================
         *  Link table domains
         * ====================================================================================== */

        DOM_BOOK_AUTHOR_TYPE_BITMASK =
                new DomainDefinition(KEY_AUTHOR_TYPE, ColumnInfo.TYPE_INTEGER, true)
                        .setDefault(0);
        DOM_BOOK_AUTHOR_POSITION =
                new DomainDefinition(KEY_AUTHOR_POSITION, ColumnInfo.TYPE_INTEGER, true);

        DOM_BOOK_SERIES_POSITION =
                new DomainDefinition("series_position", ColumnInfo.TYPE_INTEGER, true);
        DOM_BOOK_NUM_IN_SERIES =
                new DomainDefinition(KEY_BOOK_NUM_IN_SERIES, ColumnInfo.TYPE_TEXT);

        DOM_BOOK_TOC_ENTRY_POSITION =
                new DomainDefinition("toc_entry_position", ColumnInfo.TYPE_INTEGER, true);

        /* ======================================================================================
         *  Style domains
         * ====================================================================================== */

        DOM_UUID =
                new DomainDefinition(KEY_UUID, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();

        DOM_STYLE_IS_BUILTIN =
                new DomainDefinition("builtin", ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);
        /* ======================================================================================
         *  RowKind domains
         * ====================================================================================== */
        DOM_RK_AUTHOR_SORT =
                new DomainDefinition("dd_author_sort", ColumnInfo.TYPE_TEXT, true);
        DOM_RK_SERIES_SORT =
                new DomainDefinition("dd_series_sort", ColumnInfo.TYPE_TEXT, true);
        DOM_RK_READ_STATUS =
                new DomainDefinition("dd_read_status", ColumnInfo.TYPE_TEXT, true);
        DOM_RK_TITLE_LETTER =
                new DomainDefinition("dd_title_letter", ColumnInfo.TYPE_TEXT);

        DOM_RK_DATE_ADDED_DAY =
                new DomainDefinition("dd_added_day", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_ADDED_MONTH =
                new DomainDefinition("dd_added_month", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_ADDED_YEAR =
                new DomainDefinition("dd_added_year", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_UPDATE_DAY =
                new DomainDefinition("dd_upd_day", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_UPDATE_MONTH =
                new DomainDefinition("dd_upd_month", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_LAST_UPDATE_YEAR =
                new DomainDefinition("dd_upd_year", ColumnInfo.TYPE_INTEGER);

        DOM_RK_DATE_READ_DAY =
                new DomainDefinition("dd_read_day", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_READ_MONTH =
                new DomainDefinition("dd_read_month", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_READ_YEAR =
                new DomainDefinition("dd_read_year", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_ACQUIRED_DAY =
                new DomainDefinition("dd_acq_day", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_ACQUIRED_MONTH =
                new DomainDefinition("dd_acq_month", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_ACQUIRED_YEAR =
                new DomainDefinition("dd_acq_year", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_FIRST_PUBLICATION_MONTH =
                new DomainDefinition("dd_first_pub_month", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_FIRST_PUBLICATION_YEAR =
                new DomainDefinition("dd_first_pub_year", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_PUBLISHED_MONTH =
                new DomainDefinition("dd_pub_month", ColumnInfo.TYPE_INTEGER);
        DOM_RK_DATE_PUBLISHED_YEAR =
                new DomainDefinition("dd_pub_year", ColumnInfo.TYPE_INTEGER);

        /* ======================================================================================
         *  BooklistBuilder domains
         * ====================================================================================== */

        DOM_BL_SERIES_NUM_FLOAT =
                new DomainDefinition(KEY_BL_SERIES_NUM_FLOAT, ColumnInfo.TYPE_REAL);
        DOM_BL_ABSOLUTE_POSITION =
                new DomainDefinition(KEY_BL_ABSOLUTE_POSITION, ColumnInfo.TYPE_INTEGER, true);
        DOM_BL_NODE_ROW_KIND =
                new DomainDefinition(KEY_BL_NODE_ROW_KIND, ColumnInfo.TYPE_INTEGER, true);
        DOM_BL_ROOT_KEY =
                new DomainDefinition(KEY_BL_ROOT_KEY, ColumnInfo.TYPE_TEXT);
        DOM_BL_NODE_LEVEL =
                new DomainDefinition(KEY_BL_NODE_LEVEL, ColumnInfo.TYPE_INTEGER, true);
        DOM_BL_BOOK_COUNT =
                new DomainDefinition(KEY_BL_BOOK_COUNT, ColumnInfo.TYPE_INTEGER);
        DOM_BL_PRIMARY_SERIES_COUNT =
                new DomainDefinition(KEY_BL_PRIMARY_SERIES_COUNT, ColumnInfo.TYPE_INTEGER);
        DOM_FK_BOOK_LIST_TABLE_ROW_ID =
                new DomainDefinition(KEY_BL_REAL_ROW_ID, ColumnInfo.TYPE_INTEGER);
        DOM_BL_NODE_VISIBLE =
                new DomainDefinition(KEY_BL_NODE_VISIBLE, ColumnInfo.TYPE_INTEGER)
                        .setDefault(0);
        DOM_BL_NODE_EXPANDED =
                new DomainDefinition(KEY_BL_NODE_EXPANDED, ColumnInfo.TYPE_INTEGER)
                        .setDefault(0);
        DOM_BL_NODE_SELECTED =
                new DomainDefinition(KEY_BL_NODE_SELECTED, ColumnInfo.TYPE_BOOLEAN)
                        .setDefault(0);

        /* ======================================================================================
         *  Book tables.
         * ====================================================================================== */

        TBL_AUTHORS.addDomains(DOM_PK_ID,
                               DOM_AUTHOR_FAMILY_NAME,
                               DOM_AUTHOR_FAMILY_NAME_OB,
                               DOM_AUTHOR_GIVEN_NAMES,
                               DOM_AUTHOR_GIVEN_NAMES_OB,
                               DOM_AUTHOR_IS_COMPLETE)
                   .setPrimaryKey(DOM_PK_ID)
                   .addIndex(DOM_AUTHOR_FAMILY_NAME_OB, false, DOM_AUTHOR_FAMILY_NAME_OB)
                   .addIndex(DOM_AUTHOR_FAMILY_NAME, false, DOM_AUTHOR_FAMILY_NAME)
                   .addIndex(DOM_AUTHOR_GIVEN_NAMES_OB, false, DOM_AUTHOR_GIVEN_NAMES_OB)
                   .addIndex(DOM_AUTHOR_GIVEN_NAMES, false, DOM_AUTHOR_GIVEN_NAMES);
        ALL_TABLES.put(TBL_AUTHORS.getName(), TBL_AUTHORS);

        TBL_SERIES.addDomains(DOM_PK_ID,
                              DOM_SERIES_TITLE,
                              DOM_SERIES_TITLE_OB,
                              DOM_SERIES_IS_COMPLETE)
                  .setPrimaryKey(DOM_PK_ID)
                  .addIndex("id", true, DOM_PK_ID)
                  .addIndex(DOM_SERIES_TITLE_OB, false, DOM_SERIES_TITLE_OB)
                  .addIndex(DOM_SERIES_TITLE, false, DOM_SERIES_TITLE);
        ALL_TABLES.put(TBL_SERIES.getName(), TBL_SERIES);

        TBL_BOOKS.addDomains(DOM_PK_ID,
                             // book data
                             DOM_TITLE,
                             DOM_TITLE_OB,
                             DOM_BOOK_ISBN,
                             DOM_BOOK_PUBLISHER,
                             DOM_BOOK_DATE_PUBLISHED,
                             DOM_DATE_FIRST_PUBLICATION,
                             DOM_BOOK_PRINT_RUN,

                             DOM_BOOK_PRICE_LISTED,
                             DOM_BOOK_PRICE_LISTED_CURRENCY,

                             DOM_BOOK_TOC_BITMASK,
                             DOM_BOOK_FORMAT,
                             DOM_BOOK_GENRE,
                             DOM_BOOK_LANGUAGE,
                             DOM_BOOK_PAGES,

                             DOM_BOOK_DESCRIPTION,

                             // personal data
                             //DOM_BOOK_IS_OWNED,
                             DOM_BOOK_PRICE_PAID,
                             DOM_BOOK_PRICE_PAID_CURRENCY,
                             DOM_BOOK_DATE_ACQUIRED,

                             DOM_BOOK_READ,
                             DOM_BOOK_READ_START,
                             DOM_BOOK_READ_END,

                             DOM_BOOK_EDITION_BITMASK,
                             DOM_BOOK_SIGNED,
                             DOM_BOOK_RATING,
                             DOM_BOOK_LOCATION,
                             DOM_BOOK_PRIVATE_NOTES,

                             // external id/data
                             //NEWTHINGS: add new site specific ID: add DOM to table
                             DOM_BOOK_ISFDB_ID,
                             DOM_BOOK_LIBRARY_THING_ID,
                             DOM_BOOK_OPEN_LIBRARY_ID,
                             DOM_BOOK_STRIP_INFO_BE_ID,
                             DOM_BOOK_GOODREADS_ID,
                             DOM_BOOK_GOODREADS_LAST_SYNC_DATE,

                             // internal data
                             DOM_BOOK_UUID,
                             DOM_BOOK_DATE_ADDED,
                             DOM_DATE_LAST_UPDATED)

                 .setPrimaryKey(DOM_PK_ID)
                 .addIndex(DOM_TITLE_OB, false, DOM_TITLE_OB)
                 .addIndex(DOM_BOOK_ISBN, false, DOM_BOOK_ISBN)
                 .addIndex(DOM_BOOK_PUBLISHER, false, DOM_BOOK_PUBLISHER)
                 .addIndex(DOM_BOOK_UUID, true, DOM_BOOK_UUID)
                 //NEWTHINGS: add new site specific ID: add index as needed.
                 .addIndex(DOM_BOOK_GOODREADS_ID, false, DOM_BOOK_GOODREADS_ID)
                 .addIndex(DOM_BOOK_OPEN_LIBRARY_ID, false, DOM_BOOK_OPEN_LIBRARY_ID)
                 .addIndex(DOM_BOOK_STRIP_INFO_BE_ID, false, DOM_BOOK_STRIP_INFO_BE_ID)
                 .addIndex(DOM_BOOK_ISFDB_ID, false, DOM_BOOK_ISFDB_ID);
        ALL_TABLES.put(TBL_BOOKS.getName(), TBL_BOOKS);


        TBL_BOOKSHELF.addDomains(DOM_PK_ID,
                                 DOM_FK_STYLE_ID,
                                 DOM_BOOKSHELF)
                     .setPrimaryKey(DOM_PK_ID)
                     .addReference(TBL_BOOKLIST_STYLES, DOM_FK_STYLE_ID)
                     .addIndex(DOM_BOOKSHELF, true, DOM_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOKSHELF.getName(), TBL_BOOKSHELF);


        TBL_TOC_ENTRIES.addDomains(DOM_PK_ID,
                                   DOM_FK_AUTHOR,
                                   DOM_TITLE,
                                   DOM_TITLE_OB,
                                   DOM_DATE_FIRST_PUBLICATION)
                       .setPrimaryKey(DOM_PK_ID)
                       .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                       .addIndex(DOM_FK_AUTHOR, false, DOM_FK_AUTHOR)
                       .addIndex(DOM_TITLE_OB, false, DOM_TITLE_OB)
                       .addIndex("pk", true, DOM_FK_AUTHOR, DOM_TITLE_OB);
        ALL_TABLES.put(TBL_TOC_ENTRIES.getName(), TBL_TOC_ENTRIES);


        TBL_BOOK_AUTHOR.addDomains(DOM_FK_BOOK,
                                   DOM_FK_AUTHOR,
                                   DOM_BOOK_AUTHOR_POSITION,
                                   DOM_BOOK_AUTHOR_TYPE_BITMASK)
                       .setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_AUTHOR_POSITION)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK)
                       .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                       .addIndex(DOM_FK_AUTHOR, true,
                                 DOM_FK_AUTHOR,
                                 DOM_FK_BOOK)
                       .addIndex(DOM_FK_BOOK, true,
                                 DOM_FK_BOOK,
                                 DOM_FK_AUTHOR);
        ALL_TABLES.put(TBL_BOOK_AUTHOR.getName(), TBL_BOOK_AUTHOR);


        TBL_BOOK_SERIES.addDomains(DOM_FK_BOOK,
                                   DOM_FK_SERIES,
                                   DOM_BOOK_NUM_IN_SERIES,
                                   DOM_BOOK_SERIES_POSITION)
                       .setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_SERIES_POSITION)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK)
                       .addReference(TBL_SERIES, DOM_FK_SERIES)
                       .addIndex(DOM_FK_SERIES, true,
                                 DOM_FK_SERIES,
                                 DOM_FK_BOOK,
                                 DOM_BOOK_NUM_IN_SERIES)
                       .addIndex(DOM_FK_BOOK, true,
                                 DOM_FK_BOOK,
                                 DOM_FK_SERIES,
                                 DOM_BOOK_NUM_IN_SERIES);
        ALL_TABLES.put(TBL_BOOK_SERIES.getName(), TBL_BOOK_SERIES);


        TBL_BOOK_LOANEE.addDomains(DOM_PK_ID,
                                   DOM_FK_BOOK,
                                   DOM_LOANEE)
                       .setPrimaryKey(DOM_PK_ID)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK)
                       .addIndex(DOM_FK_BOOK, true, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_LOANEE.getName(), TBL_BOOK_LOANEE);


        TBL_BOOK_BOOKSHELF.addDomains(DOM_FK_BOOK,
                                      DOM_FK_BOOKSHELF)
                          .setPrimaryKey(DOM_FK_BOOK, DOM_FK_BOOKSHELF)
                          .addReference(TBL_BOOKS, DOM_FK_BOOK)
                          .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                          .addIndex(DOM_FK_BOOK, false, DOM_FK_BOOK)
                          .addIndex(DOM_FK_BOOKSHELF, false, DOM_FK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOK_BOOKSHELF.getName(), TBL_BOOK_BOOKSHELF);


        TBL_BOOK_TOC_ENTRIES.addDomains(DOM_FK_BOOK,
                                        DOM_FK_TOC_ENTRY,
                                        DOM_BOOK_TOC_ENTRY_POSITION)
                            .setPrimaryKey(DOM_FK_BOOK, DOM_FK_TOC_ENTRY)
                            .addReference(TBL_BOOKS, DOM_FK_BOOK)
                            .addReference(TBL_TOC_ENTRIES, DOM_FK_TOC_ENTRY)
                            .addIndex(DOM_FK_TOC_ENTRY, false, DOM_FK_TOC_ENTRY)
                            .addIndex(DOM_FK_BOOK, false, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_TOC_ENTRIES.getName(), TBL_BOOK_TOC_ENTRIES);

        /* ======================================================================================
         * Configuration tables.
         * ====================================================================================== */

        TBL_BOOKLIST_STYLES.addDomains(DOM_PK_ID,
                                       DOM_STYLE_IS_BUILTIN,
                                       DOM_UUID)
                           .setPrimaryKey(DOM_PK_ID)
                           .addIndex(DOM_UUID, true, DOM_UUID);
        ALL_TABLES.put(TBL_BOOKLIST_STYLES.getName(), TBL_BOOKLIST_STYLES);

        /* ======================================================================================
         *  {@link BooklistBuilder} tables keeping track of the actual list with visibility
         *  and expansion, and the flat list for the book details screen.
         * ====================================================================================== */

        // Base Name of BOOK_LIST-related tables.
        final String DB_TN_BOOK_LIST_NAME = "book_list";

        // this one is a standard table!
        TBL_BOOK_LIST_NODE_SETTINGS =
                new TableDefinition(DB_TN_BOOK_LIST_NAME + "_node_settings")
                        .setAlias("blns");

        TMP_TBL_BOOK_LIST =
                new TableDefinition(DB_TN_BOOK_LIST_NAME + "_tmp")
                        .setAlias("bl")
                        .setType(TableTypes.Temporary);

        TMP_TBL_ROW_NAVIGATOR =
                new TableDefinition(DB_TN_BOOK_LIST_NAME + "_tmp_row_pos")
                        .setAlias("blrp")
                        .setType(TableTypes.Temporary);

        TMP_TBL_ROW_NAVIGATOR_FLATTENED =
                new TableDefinition(DB_TN_BOOK_LIST_NAME + "_tmp_row_pos_flattened")
                        .setAlias("blrpf")
                        .setType(TableTypes.Temporary);

        // Allow debug mode to use standard tables so we can export and inspect the content.
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLES) {
            TMP_TBL_BOOK_LIST.setType(TableTypes.Standard);
            TMP_TBL_ROW_NAVIGATOR.setType(TableTypes.Standard);
            TMP_TBL_ROW_NAVIGATOR_FLATTENED.setType(TableTypes.Standard);
        }

        /*
         * {@link BooklistBuilder}
         */
        //URGENT: preserveNodes
        TBL_BOOK_LIST_NODE_SETTINGS.addDomains(DOM_PK_ID,
                                               DOM_FK_BOOKSHELF,
                                               DOM_FK_STYLE_ID,

                                               DOM_BL_NODE_EXPANDED,

                                               DOM_BL_NODE_ROW_KIND,
                                               DOM_BL_ROOT_KEY
                                              )
                                   .setPrimaryKey(DOM_PK_ID)
                                   .addIndex(DOM_FK_BOOKSHELF, false, DOM_FK_BOOKSHELF)
                                   .addIndex(DOM_FK_STYLE_ID, false, DOM_FK_STYLE_ID)
        ;
        ALL_TABLES.put(TBL_BOOK_LIST_NODE_SETTINGS.getName(), TBL_BOOK_LIST_NODE_SETTINGS);

        /*
         * Temporary table used to store a flattened booklist tree structure.
         *
         * This is an EXAMPLE only. The structure will depend on {@link BooklistBuilder}.
         *
         * _id  level   kind book_count root_key        book  book_uuid
         * 1    1       13              fmt/Hardcover         1d872e0edbda2c86758d8fc889351716
         * 2    2       0    1          fmt/Hardcover   1617  fc78e8ee6918e54547c2561986ccbec2
         * 3    2       0    1          fmt/Hardcover   1564  47f6f13fd98594afcdca1ce71df9f23c
         * ... snip...
         * 21   2       0    1          fmt/Hardcover   1531  3ac5caf178d9c61cbf8810d6233d0a2c
         * 22   2       0    1          fmt/Hardcover   1334  9a37fe8a709e2d9ba8c8d0301c4c0903
         * 23   2       0    1          fmt/Hardcover   1616  61891a4d06bf821a7f270769671c13d9
         * 24   2       0    1          fmt/Hardcover   1332  ab291216c5d0d6612b8e5e6d4484ffd5
         * 25   1       13              fmt/Hardcover         ae199f646b992c321f3b8ff04d0387ce
         * 26   2       0    1      	fmt/Hardcover   1505  228bff45e6d9cb025c2f73911ef6e4c4
         * 27   2       0    1      	fmt/Hardcover   1561  e6db432cb841ea0cb82901e6b3e7c0b3
         * 28   1       13              fmt/Paperback         a5df7be3d84070e0152494bbbfe99eb6
         * ...snip...
         * 32   2       0    1      	fmt/Paperback   1589  649dfb4b92a05e15e66821e2933f1930
         * 33   1       13              fmt/Paperback         07b91229ccb1de81de8f0fe7cdbefb83
         * 34   2       0    1      	fmt/Paperback   1251  74034e297d73785aee87c26d3050a8cb
         *
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         *
         * PARTIAL list of domains... this is a temp table created at runtime.
         * Domains are added (or not) depending on how the list is build.
         */
        //URGENT: preserveNodes
        TMP_TBL_BOOK_LIST.addDomains(DOM_PK_ID,
                                     DOM_BL_NODE_LEVEL,

                                     DOM_BL_NODE_ROW_KIND,

                                     DOM_BL_BOOK_COUNT,
                                     DOM_BL_PRIMARY_SERIES_COUNT,
                                     DOM_BL_ROOT_KEY,
                                     DOM_FK_BOOK)
                         .setPrimaryKey(DOM_PK_ID);

        /*
         * FULL representation of ROW_NAVIGATOR temp table.
         *
         * Keep track of level/expand/visible for each row in TMP_TBL_BOOK_LIST.
         *
         * {@link BooklistBuilder}
         */
        TMP_TBL_ROW_NAVIGATOR.addDomains(DOM_PK_ID,
                                         DOM_FK_BOOK_LIST_TABLE_ROW_ID,
                                         DOM_BL_NODE_LEVEL,
                                         DOM_BL_NODE_EXPANDED,
                                         DOM_BL_NODE_VISIBLE,
                                         //URGENT: preserveNodes
                                         DOM_BL_ROOT_KEY
                                        )
                             .setPrimaryKey(DOM_PK_ID);
        // do ***NOT*** add the reference here. It will be added *after* cloning in BooklistBuilder.
        // as the TMP_TBL_BOOK_LIST name will have an instance specific suffix.
        //.addReference(TMP_TBL_BOOK_LIST, DOM_FK_BOOK_LIST_TABLE_ROW_ID);

        /*
         * FULL representation of TMP_TBL_ROW_NAVIGATOR_FLATTENED temp table.
         *
         * Get's populated after a new TMP_TBL_BOOK_LIST is created and populated.
         * It provides the linear (flat) list of book ids to move back and forth when
         * the user swipes left and right on the book details screen.
         *
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         */
        TMP_TBL_ROW_NAVIGATOR_FLATTENED.addDomains(DOM_PK_ID,
                                                   DOM_FK_BOOK)
                                       .setPrimaryKey(DOM_PK_ID);


        /* ======================================================================================
         *  FTS definitions
         * ====================================================================================== */
        TBL_BOOKS_FTS = new TableDefinition("books_fts").setType(TableTypes.FTS3);

        DOM_PK_DOCID = new DomainDefinition("docid");

        DOM_FTS_AUTHOR_NAME =
                new DomainDefinition("author_name", ColumnInfo.TYPE_TEXT, true);

        /*
         * reminder: FTS columns don't need a type nor constraints.
         * https://sqlite.org/fts3.html
         *
         * should NOT be added to {@link #ALL_TABLES}
         */
        TBL_BOOKS_FTS.addDomains(DOM_FTS_AUTHOR_NAME,
                                 DOM_TITLE,
                                 DOM_SERIES_TITLE,
                                 DOM_BOOK_ISBN,

                                 DOM_BOOK_DESCRIPTION,
                                 DOM_BOOK_PRIVATE_NOTES,

                                 DOM_BOOK_PUBLISHER,
                                 DOM_BOOK_GENRE,
                                 DOM_BOOK_LOCATION
                                );

        /* ======================================================================================
         * Developer sanity checks.
         * ====================================================================================== */
        if (BuildConfig.DEBUG /* always */) {
            Set<String> tNames = new HashSet<>();
            Set<String> tAliases = new HashSet<>();
            for (TableDefinition table : ALL_TABLES.values()) {
                if (!tNames.add(table.getName())) {
                    throw new IllegalStateException("Duplicate table name: " + table.getName());
                }
                if (!tAliases.add(table.getAlias())) {
                    throw new IllegalStateException("Duplicate table alias: " + table.getAlias());
                }
            }
        }
    }

    private DBDefinitions() {
    }
}
