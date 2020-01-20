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
package com.hardbacknutter.nevertoomanybooks.database;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition.TableType;
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

    /**
     * A collection of all tables used to be able to rebuild indexes etc...,
     * added in order so interdependency's work out.
     * <p>
     * Only add standard tables. Do not add the TMP_* tables.
     */
    public static final Map<String, TableDefinition> ALL_TABLES = new LinkedHashMap<>();

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
    public static final TableDefinition TBL_BOOK_LIST_NODE_STATE;

    /* ======================================================================================
     * Reminder: {@link TableDefinition.TableType#Temporary).
     * Should NOT be added to {@link #ALL_TABLES}.
     * ====================================================================================== */

    public static final TableDefinition TMP_TBL_BOOK_LIST;
    public static final TableDefinition TMP_TBL_BOOK_LIST_ROW_STATE;
    /**
     * This table should always be created without column constraints applied,
     * with the exception of the "_id" primary key autoincrement.
     */
    public static final TableDefinition TMP_TBL_BOOK_LIST_NAVIGATOR;

    /* ======================================================================================
     * Primary and Foreign key domain definitions.
     * ====================================================================================== */
    /** Primary key. */
    public static final Domain DOM_PK_ID;
    /** FTS Primary key. */
    public static final Domain DOM_PK_DOCID;

    /** Foreign key. */
    public static final Domain DOM_FK_AUTHOR;
    /** Foreign key. */
    public static final Domain DOM_FK_BOOKSHELF;
    /** Foreign key. */
    public static final Domain DOM_FK_BOOK;
    /** Foreign key. */
    public static final Domain DOM_FK_SERIES;
    /** Foreign key. */
    public static final Domain DOM_FK_TOC_ENTRY;
    /**
     * Foreign key.
     * When a style is deleted, this key will be (re)set to
     * {@link BooklistStyle#DEFAULT_STYLE_ID}
     */
    public static final Domain DOM_FK_STYLE;

    /**
     * Foreign key.
     * Links {@link #TMP_TBL_BOOK_LIST_ROW_STATE} and {@link #TMP_TBL_BOOK_LIST}.
     */
    public static final Domain DOM_FK_BL_ROW_ID;

    /* ======================================================================================
     * Domain definitions.
     * ====================================================================================== */
    /** {@link #TBL_BOOKSHELF}. */
    public static final Domain DOM_BOOKSHELF;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_BOOKSHELF.dot(KEY_BOOKSHELF) + ",', ')". */
    public static final Domain DOM_BOOKSHELF_CSV;

    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_FAMILY_NAME;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_FAMILY_NAME_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_GIVEN_NAMES;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_GIVEN_NAMES_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_IS_COMPLETE;

    /** Virtual: "FamilyName, GivenName". */
    public static final Domain DOM_AUTHOR_FORMATTED;
    /** Virtual: "GivenName FamilyName". */
    public static final Domain DOM_AUTHOR_FORMATTED_GIVEN_FIRST;

    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_TITLE;
    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_TITLE_OB;
    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_IS_COMPLETE;
    /** Virtual: Series (nr). */
    public static final Domain DOM_SERIES_FORMATTED;

    /** {@link #TBL_BOOKS} + {@link #TBL_TOC_ENTRIES}. */
    public static final Domain DOM_TITLE;
    /**
     * 'Order By' for the title. Lowercase, and stripped of spaces etc...
     * {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}.
     */
    public static final Domain DOM_TITLE_OB;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_ISBN;
    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final Domain DOM_DATE_FIRST_PUB;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PUBLISHER;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_DATE_PUBLISHED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRINT_RUN;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_LISTED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_LISTED_CURRENCY;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PAGES;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_FORMAT;
    /** {@link #TBL_BOOKS}. Meant for comics or illustrated books. */
    public static final Domain DOM_BOOK_COLOR;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_LANGUAGE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DESCRIPTION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_UUID;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_EDITION_BITMASK;
    /** {@link #TBL_BOOKS}. See {@link Book#TOC_SINGLE_AUTHOR_SINGLE_WORK}. */
    public static final Domain DOM_BOOK_TOC_BITMASK;
    /**
     * {@link #TBL_BOOKS}.
     * String typed. We cannot rely on prices fetched from the internet to be 100% parsable.
     */
    public static final Domain DOM_BOOK_PRICE_PAID;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_PAID_CURRENCY;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_ACQUIRED;
    /** {@link #TBL_BOOKS} added to the collection. */
    public static final Domain DOM_BOOK_DATE_ADDED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_DATE_LAST_UPDATED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_GENRE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_LOCATION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_READ;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_READ_START;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_READ_END;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_SIGNED;
    /** {@link #TBL_BOOKS}. A rating goes from 0 to 5 stars, in 0.5 increments. */
    public static final Domain DOM_BOOK_RATING;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRIVATE_NOTES;


    /** {@link #TBL_BOOKS}. Book ID, not 'work' ID. */
    public static final Domain DOM_EID_GOODREADS_BOOK;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_EID_ISFDB;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_EID_LIBRARY_THING;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_EID_OPEN_LIBRARY;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_EID_STRIP_INFO_BE;
    /** {@link #TBL_BOOK_SERIES}. */
    public static final Domain DOM_BOOK_NUM_IN_SERIES;


    /** {@link #TBL_BOOK_LOANEE}. */
    public static final Domain DOM_LOANEE;
    /**
     * {@link #TBL_BOOK_LOANEE}.
     * Virtual: returns 0 for 'available' or 1 for 'lend out'
     */
    public static final Domain DOM_LOANEE_AS_BOOLEAN;


    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final Domain DOM_BOOK_AUTHOR_TYPE_BITMASK;
    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final Domain DOM_BOOK_AUTHOR_POSITION;


    /** {@link #TBL_BOOK_TOC_ENTRIES}. */
    public static final Domain DOM_BOOK_TOC_ENTRY_POSITION;

    /** {@link #TBL_BOOKLIST_STYLES}. */
    public static final Domain DOM_STYLE_IS_BUILTIN;

    /**
     * {@link #TBL_BOOK_SERIES}.
     * The Series position is the order the Series show up in a book. Particularly important
     * for "primary series" and in lists where 'all' Series are shown.
     */
    public static final Domain DOM_BOOK_SERIES_POSITION;
    /** {@link #TBL_BOOKLIST_STYLES} java.util.UUID value stored as a string. */
    public static final Domain DOM_UUID;

    /** Virtual. The type of a TOC entry. See {@link TocEntry.Type} */
    public static final Domain DOM_TOC_TYPE;

    /**
     * {@link #TBL_BOOKS_FTS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    public static final Domain DOM_FTS_AUTHOR_NAME;

    /* ======================================================================================
     *  {@link BooklistGroup.RowKind} domains.
     * ====================================================================================== */
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_AUTHOR_SORT;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_SERIES_SORT;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_READ_STATUS;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_TITLE_LETTER;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_SERIES_TITLE_LETTER;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_ADDED_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_ADDED_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_ADDED_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_LAST_UPDATED_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_LAST_UPDATED_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_LAST_UPDATED_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_READ_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_READ_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_READ_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_ACQUIRED_DAY;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_ACQUIRED_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_ACQUIRED_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_FIRST_PUBLICATION_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_FIRST_PUBLICATION_YEAR;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_PUBLISHED_MONTH;
    /** Display-domain in {@link BooklistGroup.RowKind}. */
    public static final Domain DOM_RK_DATE_PUBLISHED_YEAR;

    /* ======================================================================================
     *  {@link BooklistBuilder} domains.
     * ====================================================================================== */
    /**
     * Series number, cast()'d for sorting purposes in {@link BooklistBuilder}
     * so we can sort it numerically regardless of content.
     */
    public static final Domain DOM_BL_SERIES_NUM_FLOAT;
    /** {@link BooklistBuilder}. */
    public static final Domain DOM_BL_LIST_VIEW_ROW_ID;
    /** {@link #TBL_BOOK_LIST_NODE_STATE} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_NODE_KIND;
    /**
     * {@link #TBL_BOOK_LIST_NODE_STATE}
     * {@link #TMP_TBL_BOOK_LIST_ROW_STATE}
     * {@link BooklistBuilder}.
     * <p>
     * Expression from the original tables that represent the key for the root level group.
     * Stored in each row and used to determine the expand/collapse results.
     */
    public static final Domain DOM_BL_ROOT_KEY;

    /** {@link #TMP_TBL_BOOK_LIST} {@link #TMP_TBL_BOOK_LIST_ROW_STATE} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_NODE_LEVEL;
    /** {@link #TMP_TBL_BOOK_LIST} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_BOOK_COUNT;
    /** {@link #TMP_TBL_BOOK_LIST} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_PRIMARY_SERIES_COUNT;
    /**
     * {@link #TMP_TBL_BOOK_LIST_ROW_STATE} {@link BooklistBuilder} is node expanded.
     * An expanded node, should always be visible!
     */
    public static final Domain DOM_BL_NODE_EXPANDED;
    /** {@link #TMP_TBL_BOOK_LIST_ROW_STATE} {@link BooklistBuilder} is node visible. */
    public static final Domain DOM_BL_NODE_VISIBLE;

    /* ======================================================================================
     *  Keys used as domain names and as Bundle keys.
     * ====================================================================================== */
    /** Primary key. */
    public static final String KEY_PK_ID = "_id";
    /** Primary key. */
    public static final String KEY_PK_DOCID = "docid";

    /** Foreign key. */
    public static final String KEY_FK_BOOK = "book";
    /** Foreign key. */
    public static final String KEY_FK_AUTHOR = "author";
    /** Foreign key. */
    public static final String KEY_FK_SERIES = "series_id";
    /** Foreign key. */
    public static final String KEY_FK_BOOKSHELF = "bookshelf";
    /** Foreign key. */
    public static final String KEY_FK_TOC_ENTRY = "anthology";
    /** Foreign key. */
    public static final String KEY_FK_STYLE = "style";

    /** External id. - Long. */
    public static final String KEY_EID_GOODREADS_BOOK = "goodreads_book_id";
    public static final String KEY_BOOK_GOODREADS_LAST_SYNC_DATE = "last_goodreads_sync_date";
    /** External id. - Long. */
    public static final String KEY_EID_ISFDB = "isfdb_book_id";
    /** External id. - Long. */
    public static final String KEY_EID_LIBRARY_THING = "lt_book_id";
    /** External id. - String. */
    public static final String KEY_EID_OPEN_LIBRARY = "ol_book_id";
    /** External id. - Long. */
    public static final String KEY_EID_STRIP_INFO_BE = "si_book_id";
    /** External id. - String. */
    public static final String KEY_EID_ASIN = "asin";

    //NEWTHINGS: add new site specific ID: add a KEY
    // ENHANCE: the search engines already use these when found, but not stored yet.
    /** External id. - String. */
    public static final String KEY_EID_WORLDCAT = "worldcat_oclc_book_id";
    /** External id. - String. */
    public static final String KEY_EID_LCCN = "lccn_book_id";
    /** All native id keys supported for lookups. */
    public static final String[] NATIVE_ID_KEYS = {
            DBDefinitions.KEY_EID_GOODREADS_BOOK,
            DBDefinitions.KEY_EID_ISFDB,
            DBDefinitions.KEY_EID_LIBRARY_THING,
            DBDefinitions.KEY_EID_OPEN_LIBRARY,
            DBDefinitions.KEY_EID_STRIP_INFO_BE,
//                DBDefinitions.KEY_EID_ASIN,
//                DBDefinitions.KEY_EID_WORLDCAT,
//                DBDefinitions.KEY_EID_LCCN
    };

    /** {@link #TBL_BOOKSHELF}. */
    public static final String KEY_BOOKSHELF = "bookshelf";
    public static final String KEY_BOOKSHELF_CSV = "bookshelves_csv";

    /** {@link #TBL_AUTHORS} {@link #TBL_BOOK_AUTHOR} */
    public static final String KEY_AUTHOR_FAMILY_NAME = "family_name";
    public static final String KEY_AUTHOR_FAMILY_NAME_OB;
    public static final String KEY_AUTHOR_GIVEN_NAMES = "given_names";
    public static final String KEY_AUTHOR_GIVEN_NAMES_OB;
    public static final String KEY_AUTHOR_IS_COMPLETE = "author_complete";
    public static final String KEY_AUTHOR_FORMATTED = "author_formatted";
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";
    public static final String KEY_AUTHOR_TYPE_BITMASK = "author_type";
    public static final String KEY_BOOK_AUTHOR_POSITION = "author_position";

    /** {@link #TBL_SERIES} {@link #TBL_BOOK_SERIES} */
    public static final String KEY_SERIES_TITLE = "series_name";
    public static final String KEY_SERIES_TITLE_OB;
    public static final String KEY_SERIES_FORMATTED = "series_formatted";
    public static final String KEY_SERIES_IS_COMPLETE = "series_complete";
    public static final String KEY_BOOK_NUM_IN_SERIES = "series_num";
    public static final String KEY_BOOK_SERIES_POSITION = "series_position";

    /** {@link #TBL_TOC_ENTRIES}.  Virtual. The type of a TOC entry. See {@link TocEntry.Type} */
    public static final String KEY_TOC_TYPE = "type";
    /** {@link #TBL_TOC_ENTRIES}. */
    public static final String KEY_BOOK_TOC_ENTRY_POSITION = "toc_entry_position";
    /** {@link #TBL_BOOKS}. */
    public static final String KEY_DATE_ADDED = "date_added";
    public static final String KEY_DATE_LAST_UPDATED = "last_update_date";
    public static final String KEY_UUID = "uuid";
    public static final String KEY_TITLE = "title";
    public static final String KEY_TITLE_OB;
    public static final String KEY_ISBN = "isbn";
    public static final String KEY_DATE_FIRST_PUBLICATION = "first_publication";
    public static final String KEY_PUBLISHER = "publisher";
    public static final String KEY_DATE_PUBLISHED = "date_published";
    public static final String KEY_PRINT_RUN = "print_run";
    public static final String KEY_PRICE_LISTED = "list_price";
    public static final String KEY_PRICE_LISTED_CURRENCY = "list_price_currency";
    public static final String KEY_PAGES = "pages";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_COLOR = "color";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_GENRE = "genre";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_BOOK_UUID = "book_uuid";
    public static final String KEY_EDITION_BITMASK = "edition_bm";
    public static final String KEY_TOC_BITMASK = "anthology";

    /** {@link #TBL_BOOKS} Personal data. */
    public static final String KEY_PRICE_PAID = "price_paid";
    public static final String KEY_PRICE_PAID_CURRENCY = "price_paid_currency";
    public static final String KEY_DATE_ACQUIRED = "date_acquired";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_READ = "read";
    public static final String KEY_READ_START = "read_start";
    public static final String KEY_READ_END = "read_end";
    public static final String KEY_SIGNED = "signed";
    public static final String KEY_RATING = "rating";
    public static final String KEY_PRIVATE_NOTES = "notes";

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final String KEY_LOANEE = "loaned_to";
    public static final String KEY_LOANEE_AS_BOOLEAN = "loaned_flag";

    /** {@link #TBL_BOOKLIST_STYLES}. */
    public static final String KEY_STYLE_IS_BUILTIN = "builtin";
    /** {@link #TBL_BOOKS_FTS}. */
    public static final String KEY_FTS_AUTHOR_NAME = "author_name";

    /** BooklistBuilder. */
    public static final String KEY_BL_SERIES_NUM_FLOAT = "ser_num_float";
    public static final String KEY_BL_BOOK_COUNT = "book_count";
    public static final String KEY_BL_PRIMARY_SERIES_COUNT = "prim_ser_cnt";
    /**
     * BooklistBuilder: an alias for the RowState table rowId
     * listViewRowPosition = rowId -1.
     */
    public static final String KEY_BL_LIST_VIEW_ROW_ID = "lv_row_id";
    public static final String KEY_BL_ROOT_KEY = "root_key";
    /** The foreign key in the row-state table, pointing to the list table. */
    public static final String KEY_FK_BL_ROW_ID = "real_row_id";

    /** {@link #TBL_BOOK_LIST_NODE_STATE} + {@link #TMP_TBL_BOOK_LIST_ROW_STATE}. */
    public static final String KEY_BL_NODE_LEVEL = "level";
    public static final String KEY_BL_NODE_KIND = "kind";
    public static final String KEY_BL_NODE_VISIBLE = "visible";
    public static final String KEY_BL_NODE_EXPANDED = "expanded";

    static {
        // Suffix added to a column name to create a specific 'order by' copy of that column.
        final String COLUMN_SUFFIX_ORDER_BY = "_ob";

        KEY_AUTHOR_FAMILY_NAME_OB = KEY_AUTHOR_FAMILY_NAME + COLUMN_SUFFIX_ORDER_BY;
        KEY_AUTHOR_GIVEN_NAMES_OB = KEY_AUTHOR_GIVEN_NAMES + COLUMN_SUFFIX_ORDER_BY;
        KEY_TITLE_OB = KEY_TITLE + COLUMN_SUFFIX_ORDER_BY;
        KEY_SERIES_TITLE_OB = KEY_SERIES_TITLE + COLUMN_SUFFIX_ORDER_BY;

        /* ======================================================================================
         *  Table definitions
         * ====================================================================================== */

        // never change the "authors" "a" alias. It's hardcoded elsewhere.
        TBL_AUTHORS = new TableDefinition("authors").setAlias("a");
        // never change the "books" "b" alias. It's hardcoded elsewhere.
        TBL_BOOKS = new TableDefinition("books").setAlias("b");
        // never change the "series" "s" alias. It's hardcoded elsewhere.
        TBL_SERIES = new TableDefinition("series").setAlias("s");

        TBL_BOOKSHELF = new TableDefinition("bookshelf").setAlias("bsh");
        TBL_TOC_ENTRIES = new TableDefinition("anthology").setAlias("an");

        TBL_BOOK_BOOKSHELF = new TableDefinition("book_bookshelf").setAlias("bbsh");
        TBL_BOOK_AUTHOR = new TableDefinition("book_author").setAlias("ba");
        TBL_BOOK_SERIES = new TableDefinition("book_series").setAlias("bs");
        TBL_BOOK_LOANEE = new TableDefinition("loan").setAlias("l");
        TBL_BOOK_TOC_ENTRIES = new TableDefinition("book_anthology").setAlias("bat");

        TBL_BOOKLIST_STYLES = new TableDefinition("book_list_styles").setAlias("bls");

        /* ======================================================================================
         *  Primary and Foreign Key definitions
         * ====================================================================================== */

        DOM_PK_ID = new Domain.Builder(KEY_PK_ID, ColumnInfo.TYPE_INTEGER).primaryKey().build();

        DOM_FK_AUTHOR =
                new Domain.Builder(KEY_FK_AUTHOR, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_AUTHORS, "ON DELETE CASCADE ON UPDATE CASCADE")
                        .build();
        DOM_FK_BOOKSHELF =
                new Domain.Builder(KEY_FK_BOOKSHELF, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_BOOKSHELF, "ON DELETE CASCADE ON UPDATE CASCADE")
                        .build();
        DOM_FK_BOOK =
                new Domain.Builder(KEY_FK_BOOK, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_BOOKS, "ON DELETE CASCADE ON UPDATE CASCADE")
                        .build();
        DOM_FK_SERIES =
                new Domain.Builder(KEY_FK_SERIES, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_SERIES, "ON DELETE CASCADE ON UPDATE CASCADE")
                        .build();
        DOM_FK_TOC_ENTRY =
                new Domain.Builder(KEY_FK_TOC_ENTRY, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_TOC_ENTRIES, "ON DELETE CASCADE ON UPDATE CASCADE")
                        .build();
        DOM_FK_STYLE =
                new Domain.Builder(KEY_FK_STYLE, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(BooklistStyle.DEFAULT_STYLE_ID)
                        .references(TBL_BOOKLIST_STYLES, "ON DELETE SET DEFAULT ON UPDATE CASCADE")
                        .build();

        /* ======================================================================================
         *  Multi table domains
         * ====================================================================================== */

        DOM_TITLE =
                new Domain.Builder(KEY_TITLE, ColumnInfo.TYPE_TEXT).notNull().build();
        DOM_TITLE_OB =
                new Domain.Builder(KEY_TITLE_OB, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().prePreparedOrderBy().build();

        DOM_DATE_FIRST_PUB =
                new Domain.Builder(KEY_DATE_FIRST_PUBLICATION, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();

        DOM_DATE_LAST_UPDATED =
                new Domain.Builder(KEY_DATE_LAST_UPDATED, ColumnInfo.TYPE_DATETIME)
                        .notNull().withDefault("current_timestamp").build();

        /* ======================================================================================
         *  Bookshelf domains
         * ====================================================================================== */

        DOM_BOOKSHELF =
                new Domain.Builder(KEY_BOOKSHELF, ColumnInfo.TYPE_TEXT).notNull().build();
        DOM_BOOKSHELF_CSV =
                new Domain.Builder(KEY_BOOKSHELF_CSV, ColumnInfo.TYPE_TEXT).notNull().build();

        /* ======================================================================================
         *  Author domains
         * ====================================================================================== */

        DOM_AUTHOR_FAMILY_NAME =
                new Domain.Builder(KEY_AUTHOR_FAMILY_NAME, ColumnInfo.TYPE_TEXT).notNull().build();

        DOM_AUTHOR_FAMILY_NAME_OB =
                new Domain.Builder(KEY_AUTHOR_FAMILY_NAME_OB, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().prePreparedOrderBy().build();

        DOM_AUTHOR_GIVEN_NAMES =
                new Domain.Builder(KEY_AUTHOR_GIVEN_NAMES, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();

        DOM_AUTHOR_GIVEN_NAMES_OB =
                new Domain.Builder(KEY_AUTHOR_GIVEN_NAMES_OB, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().prePreparedOrderBy().build();

        DOM_AUTHOR_IS_COMPLETE =
                new Domain.Builder(KEY_AUTHOR_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN)
                        .notNull().withDefault(0).build();

        DOM_AUTHOR_FORMATTED =
                new Domain.Builder(KEY_AUTHOR_FORMATTED, ColumnInfo.TYPE_TEXT).notNull().build();

        DOM_AUTHOR_FORMATTED_GIVEN_FIRST =
                new Domain.Builder(KEY_AUTHOR_FORMATTED_GIVEN_FIRST, ColumnInfo.TYPE_TEXT)
                        .notNull().build();

        /* ======================================================================================
         *  Series domains
         * ====================================================================================== */

        DOM_SERIES_TITLE =
                new Domain.Builder(KEY_SERIES_TITLE, ColumnInfo.TYPE_TEXT).notNull().build();
        DOM_SERIES_TITLE_OB =
                new Domain.Builder(KEY_SERIES_TITLE_OB, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().prePreparedOrderBy().build();
        DOM_SERIES_IS_COMPLETE =
                new Domain.Builder(KEY_SERIES_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN)
                        .notNull().withDefault(0).build();
        DOM_SERIES_FORMATTED =
                new Domain.Builder(KEY_SERIES_FORMATTED, ColumnInfo.TYPE_TEXT).notNull().build();

        /* ======================================================================================
         *  Book domains
         * ====================================================================================== */

        DOM_BOOK_ISBN =
                new Domain.Builder(KEY_ISBN, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();

        DOM_BOOK_PUBLISHER =
                new Domain.Builder(KEY_PUBLISHER, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_DATE_PUBLISHED =
                new Domain.Builder(KEY_DATE_PUBLISHED, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_PRINT_RUN =
                new Domain.Builder(KEY_PRINT_RUN, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();

        DOM_BOOK_PRICE_LISTED =
                new Domain.Builder(KEY_PRICE_LISTED, ColumnInfo.TYPE_REAL)
                        .notNull().withDefault(0d).build();

        DOM_BOOK_PRICE_LISTED_CURRENCY =
                new Domain.Builder(KEY_PRICE_LISTED_CURRENCY, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_PAGES =
                new Domain.Builder(KEY_PAGES, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_FORMAT =
                new Domain.Builder(KEY_FORMAT, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_COLOR =
                new Domain.Builder(KEY_COLOR, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_LANGUAGE =
                new Domain.Builder(KEY_LANGUAGE, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_GENRE =
                new Domain.Builder(KEY_GENRE, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_DESCRIPTION =
                new Domain.Builder(KEY_DESCRIPTION, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();

        DOM_BOOK_TOC_BITMASK =
                new Domain.Builder(KEY_TOC_BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull().withDefault(Book.TOC_SINGLE_AUTHOR_SINGLE_WORK).build();

        /* ======================================================================================
         *  Book personal data domains
         * ====================================================================================== */

        DOM_BOOK_UUID =
                new Domain.Builder(KEY_BOOK_UUID, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefault("(lower(hex(randomblob(16))))").build();

        DOM_BOOK_EDITION_BITMASK =
                new Domain.Builder(KEY_EDITION_BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull().withDefault(0).build();

//        DOM_BOOK_IS_OWNED =
//                new Domain.Builder(KEY_OWNED, ColumnInfo.TYPE_BOOLEAN)
//                        .notNull().withDefault(0).build();
        DOM_BOOK_DATE_ACQUIRED =
                new Domain.Builder(KEY_DATE_ACQUIRED, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();

        DOM_BOOK_PRICE_PAID =
                new Domain.Builder(KEY_PRICE_PAID, ColumnInfo.TYPE_REAL)
                        .notNull().withDefault(0d).build();

        DOM_BOOK_PRICE_PAID_CURRENCY =
                new Domain.Builder(KEY_PRICE_PAID_CURRENCY, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();

        DOM_BOOK_DATE_ADDED =
                new Domain.Builder(KEY_DATE_ADDED, ColumnInfo.TYPE_DATETIME)
                        .notNull().withDefault("current_timestamp").build();

        DOM_BOOK_LOCATION =
                new Domain.Builder(KEY_LOCATION, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_READ =
                new Domain.Builder(KEY_READ, ColumnInfo.TYPE_BOOLEAN)
                        .notNull().withDefault(0).build();
        DOM_BOOK_READ_START =
                new Domain.Builder(KEY_READ_START, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_READ_END =
                new Domain.Builder(KEY_READ_END, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_SIGNED =
                new Domain.Builder(KEY_SIGNED, ColumnInfo.TYPE_BOOLEAN)
                        .notNull().withDefault(0).build();
        DOM_BOOK_RATING =
                new Domain.Builder(KEY_RATING, ColumnInfo.TYPE_REAL)
                        .notNull().withDefault(0).build();
        DOM_BOOK_PRIVATE_NOTES =
                new Domain.Builder(KEY_PRIVATE_NOTES, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();

        /* ======================================================================================
         *  Book external website id domains
         * ====================================================================================== */
        //NEWTHINGS: add new site specific ID: add a DOM
        DOM_EID_GOODREADS_BOOK =
                new Domain.Builder(KEY_EID_GOODREADS_BOOK, ColumnInfo.TYPE_INTEGER).build();
        DOM_BOOK_GOODREADS_LAST_SYNC_DATE =
                new Domain.Builder(KEY_BOOK_GOODREADS_LAST_SYNC_DATE, ColumnInfo.TYPE_DATE)
                        .withDefault("'0000-00-00'").build();

        DOM_EID_ISFDB =
                new Domain.Builder(KEY_EID_ISFDB, ColumnInfo.TYPE_INTEGER).build();
        DOM_EID_LIBRARY_THING =
                new Domain.Builder(KEY_EID_LIBRARY_THING, ColumnInfo.TYPE_INTEGER).build();
        DOM_EID_OPEN_LIBRARY =
                new Domain.Builder(KEY_EID_OPEN_LIBRARY, ColumnInfo.TYPE_TEXT).build();
        DOM_EID_STRIP_INFO_BE =
                new Domain.Builder(KEY_EID_STRIP_INFO_BE, ColumnInfo.TYPE_INTEGER).build();

        /* ======================================================================================
         *  Loanee domains
         * ====================================================================================== */

        DOM_LOANEE = new Domain.Builder(KEY_LOANEE, ColumnInfo.TYPE_TEXT).notNull().build();
        DOM_LOANEE_AS_BOOLEAN = new Domain.Builder(KEY_LOANEE_AS_BOOLEAN, ColumnInfo.TYPE_INTEGER)
                .notNull().build();

        /* ======================================================================================
         *  TOC domains
         * ====================================================================================== */

        DOM_TOC_TYPE = new Domain.Builder(KEY_TOC_TYPE, ColumnInfo.TYPE_TEXT).build();

        /* ======================================================================================
         *  Link table domains
         * ====================================================================================== */

        DOM_BOOK_AUTHOR_TYPE_BITMASK =
                new Domain.Builder(KEY_AUTHOR_TYPE_BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull().withDefault(0).build();
        DOM_BOOK_AUTHOR_POSITION =
                new Domain.Builder(KEY_BOOK_AUTHOR_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull().build();

        DOM_BOOK_SERIES_POSITION =
                new Domain.Builder(KEY_BOOK_SERIES_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull().build();
        DOM_BOOK_NUM_IN_SERIES =
                new Domain.Builder(KEY_BOOK_NUM_IN_SERIES, ColumnInfo.TYPE_TEXT).build();

        DOM_BOOK_TOC_ENTRY_POSITION =
                new Domain.Builder(KEY_BOOK_TOC_ENTRY_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull().build();

        /* ======================================================================================
         *  Style domains
         * ====================================================================================== */

        DOM_UUID =
                new Domain.Builder(KEY_UUID, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefaultEmptyString().build();
        DOM_STYLE_IS_BUILTIN =
                new Domain.Builder(KEY_STYLE_IS_BUILTIN, ColumnInfo.TYPE_BOOLEAN)
                        .notNull().withDefault(0).build();
        /* ======================================================================================
         *  RowKind display-domains
         * ====================================================================================== */
        DOM_RK_AUTHOR_SORT =
                new Domain.Builder("dd_aut_sort", ColumnInfo.TYPE_TEXT).notNull().build();

        DOM_RK_TITLE_LETTER =
                new Domain.Builder("dd_tit_let", ColumnInfo.TYPE_TEXT).notNull().build();
        DOM_RK_SERIES_TITLE_LETTER =
                new Domain.Builder("dd_ser_tit_let", ColumnInfo.TYPE_TEXT).notNull().build();

        DOM_RK_READ_STATUS =
                new Domain.Builder("dd_rd_sts", ColumnInfo.TYPE_TEXT).notNull().build();

        DOM_RK_SERIES_SORT =
                new Domain.Builder("dd_ser_sort", ColumnInfo.TYPE_TEXT).build();

        DOM_RK_DATE_ADDED_DAY =
                new Domain.Builder("dd_add_d", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_ADDED_MONTH =
                new Domain.Builder("dd_add_m", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_ADDED_YEAR =
                new Domain.Builder("dd_add_y", ColumnInfo.TYPE_INTEGER).build();

        DOM_RK_DATE_LAST_UPDATED_DAY =
                new Domain.Builder("dd_upd_d", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_LAST_UPDATED_MONTH =
                new Domain.Builder("dd_upd_m", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_LAST_UPDATED_YEAR =
                new Domain.Builder("dd_upd_y", ColumnInfo.TYPE_INTEGER).build();

        DOM_RK_DATE_READ_DAY =
                new Domain.Builder("dd_rd_d", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_READ_MONTH =
                new Domain.Builder("dd_rd_m", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_READ_YEAR =
                new Domain.Builder("dd_rd_y", ColumnInfo.TYPE_INTEGER).build();

        DOM_RK_DATE_ACQUIRED_DAY =
                new Domain.Builder("dd_acq_d", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_ACQUIRED_MONTH =
                new Domain.Builder("dd_acq_m", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_ACQUIRED_YEAR =
                new Domain.Builder("dd_acq_y", ColumnInfo.TYPE_INTEGER).build();

        DOM_RK_DATE_FIRST_PUBLICATION_MONTH =
                new Domain.Builder("dd_1pub_m", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_FIRST_PUBLICATION_YEAR =
                new Domain.Builder("dd_1pub_y", ColumnInfo.TYPE_INTEGER).build();

        DOM_RK_DATE_PUBLISHED_MONTH =
                new Domain.Builder("dd_pub_m", ColumnInfo.TYPE_INTEGER).build();
        DOM_RK_DATE_PUBLISHED_YEAR =
                new Domain.Builder("dd_pub_y", ColumnInfo.TYPE_INTEGER).build();

        /* ======================================================================================
         *  BooklistBuilder domains
         * ====================================================================================== */

        DOM_BL_SERIES_NUM_FLOAT =
                new Domain.Builder(KEY_BL_SERIES_NUM_FLOAT, ColumnInfo.TYPE_REAL).build();
        DOM_BL_LIST_VIEW_ROW_ID =
                new Domain.Builder(KEY_BL_LIST_VIEW_ROW_ID, ColumnInfo.TYPE_INTEGER)
                        .notNull().build();
        DOM_BL_NODE_KIND =
                new Domain.Builder(KEY_BL_NODE_KIND, ColumnInfo.TYPE_INTEGER).notNull().build();
        DOM_BL_ROOT_KEY =
                new Domain.Builder(KEY_BL_ROOT_KEY, ColumnInfo.TYPE_TEXT).build();
        DOM_BL_NODE_LEVEL =
                new Domain.Builder(KEY_BL_NODE_LEVEL, ColumnInfo.TYPE_INTEGER).notNull().build();
        DOM_BL_BOOK_COUNT =
                new Domain.Builder(KEY_BL_BOOK_COUNT, ColumnInfo.TYPE_INTEGER).build();
        DOM_BL_PRIMARY_SERIES_COUNT =
                new Domain.Builder(KEY_BL_PRIMARY_SERIES_COUNT, ColumnInfo.TYPE_INTEGER).build();
        DOM_FK_BL_ROW_ID =
                new Domain.Builder(KEY_FK_BL_ROW_ID, ColumnInfo.TYPE_INTEGER).build();
        DOM_BL_NODE_VISIBLE =
                new Domain.Builder(KEY_BL_NODE_VISIBLE, ColumnInfo.TYPE_INTEGER)
                        .withDefault(0).build();
        DOM_BL_NODE_EXPANDED =
                new Domain.Builder(KEY_BL_NODE_EXPANDED, ColumnInfo.TYPE_INTEGER)
                        .withDefault(0).build();

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
                   .addIndex(KEY_AUTHOR_FAMILY_NAME_OB, false, DOM_AUTHOR_FAMILY_NAME_OB)
                   .addIndex(KEY_AUTHOR_FAMILY_NAME, false, DOM_AUTHOR_FAMILY_NAME)
                   .addIndex(KEY_AUTHOR_GIVEN_NAMES_OB, false, DOM_AUTHOR_GIVEN_NAMES_OB)
                   .addIndex(KEY_AUTHOR_GIVEN_NAMES, false, DOM_AUTHOR_GIVEN_NAMES);
        ALL_TABLES.put(TBL_AUTHORS.getName(), TBL_AUTHORS);

        TBL_SERIES.addDomains(DOM_PK_ID,
                              DOM_SERIES_TITLE,
                              DOM_SERIES_TITLE_OB,
                              DOM_SERIES_IS_COMPLETE)
                  .setPrimaryKey(DOM_PK_ID)
                  .addIndex("id", true, DOM_PK_ID)
                  .addIndex(KEY_SERIES_TITLE_OB, false, DOM_SERIES_TITLE_OB)
                  .addIndex(KEY_SERIES_TITLE, false, DOM_SERIES_TITLE);
        ALL_TABLES.put(TBL_SERIES.getName(), TBL_SERIES);

        TBL_BOOKS.addDomains(DOM_PK_ID,
                             // book data
                             DOM_TITLE,
                             DOM_TITLE_OB,
                             DOM_BOOK_ISBN,
                             DOM_BOOK_PUBLISHER,
                             DOM_DATE_PUBLISHED,
                             DOM_DATE_FIRST_PUB,
                             DOM_BOOK_PRINT_RUN,

                             DOM_BOOK_PRICE_LISTED,
                             DOM_BOOK_PRICE_LISTED_CURRENCY,

                             DOM_BOOK_TOC_BITMASK,
                             DOM_BOOK_FORMAT,
                             DOM_BOOK_COLOR,
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
                             DOM_EID_ISFDB,
                             DOM_EID_LIBRARY_THING,
                             DOM_EID_OPEN_LIBRARY,
                             DOM_EID_STRIP_INFO_BE,
                             DOM_EID_GOODREADS_BOOK,
                             DOM_BOOK_GOODREADS_LAST_SYNC_DATE,

                             // internal data
                             DOM_BOOK_UUID,
                             DOM_BOOK_DATE_ADDED,
                             DOM_DATE_LAST_UPDATED)

                 .setPrimaryKey(DOM_PK_ID)
                 .addIndex(KEY_TITLE_OB, false, DOM_TITLE_OB)
                 .addIndex(KEY_ISBN, false, DOM_BOOK_ISBN)
                 .addIndex(KEY_PUBLISHER, false, DOM_BOOK_PUBLISHER)
                 .addIndex(KEY_BOOK_UUID, true, DOM_BOOK_UUID)
                 //NEWTHINGS: add new site specific ID: add index as needed.
                 .addIndex(KEY_EID_GOODREADS_BOOK, false, DOM_EID_GOODREADS_BOOK)
                 .addIndex(KEY_EID_OPEN_LIBRARY, false, DOM_EID_OPEN_LIBRARY)
                 .addIndex(KEY_EID_STRIP_INFO_BE, false, DOM_EID_STRIP_INFO_BE)
                 .addIndex(KEY_EID_ISFDB, false, DOM_EID_ISFDB);
        ALL_TABLES.put(TBL_BOOKS.getName(), TBL_BOOKS);


        TBL_BOOKSHELF.addDomains(DOM_PK_ID,
                                 DOM_FK_STYLE,
                                 DOM_BOOKSHELF)
                     .setPrimaryKey(DOM_PK_ID)
                     .addReference(TBL_BOOKLIST_STYLES, DOM_FK_STYLE)
                     .addIndex(KEY_BOOKSHELF, true, DOM_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOKSHELF.getName(), TBL_BOOKSHELF);


        TBL_TOC_ENTRIES.addDomains(DOM_PK_ID,
                                   DOM_FK_AUTHOR,
                                   DOM_TITLE,
                                   DOM_TITLE_OB,
                                   DOM_DATE_FIRST_PUB)
                       .setPrimaryKey(DOM_PK_ID)
                       .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                       .addIndex(KEY_FK_AUTHOR, false, DOM_FK_AUTHOR)
                       .addIndex(KEY_TITLE_OB, false, DOM_TITLE_OB)
                       .addIndex("pk", true, DOM_FK_AUTHOR, DOM_TITLE_OB);
        ALL_TABLES.put(TBL_TOC_ENTRIES.getName(), TBL_TOC_ENTRIES);


        TBL_BOOK_AUTHOR.addDomains(DOM_FK_BOOK,
                                   DOM_FK_AUTHOR,
                                   DOM_BOOK_AUTHOR_POSITION,
                                   DOM_BOOK_AUTHOR_TYPE_BITMASK)
                       // enforce: only one author on a particular position for a book.
                       // allow: multiple copies of that author and multiple types.
                       // The latter has some restrictions handled in code.
                       //FIXME: should add DOM_FK_AUTHOR to the primary key
                       .setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_AUTHOR_POSITION)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK)
                       .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                       .addIndex(KEY_FK_AUTHOR, true,
                                 DOM_FK_AUTHOR,
                                 DOM_FK_BOOK)
                       .addIndex(KEY_FK_BOOK, true,
                                 DOM_FK_BOOK,
                                 DOM_FK_AUTHOR);
        ALL_TABLES.put(TBL_BOOK_AUTHOR.getName(), TBL_BOOK_AUTHOR);


        TBL_BOOK_SERIES.addDomains(DOM_FK_BOOK,
                                   DOM_FK_SERIES,
                                   DOM_BOOK_NUM_IN_SERIES,
                                   DOM_BOOK_SERIES_POSITION)
                       // enforce: only one series on a particular position for a book.
                       // allow: multiple copies of that series and multiple numbers.
                       // The latter has some restrictions handled in code.
                       // in contract to TBL_BOOK_AUTHOR we don't want to add the DOM_FK_SERIES
                       // to the primary key, as want want to allow a single book to be
                       // present in a series multiple times (each time with a different number).
                       .setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_SERIES_POSITION)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK)
                       .addReference(TBL_SERIES, DOM_FK_SERIES)
                       .addIndex(KEY_FK_SERIES, true,
                                 DOM_FK_SERIES,
                                 DOM_FK_BOOK,
                                 DOM_BOOK_NUM_IN_SERIES)
                       .addIndex(KEY_FK_BOOK, true,
                                 DOM_FK_BOOK,
                                 DOM_FK_SERIES,
                                 DOM_BOOK_NUM_IN_SERIES);
        ALL_TABLES.put(TBL_BOOK_SERIES.getName(), TBL_BOOK_SERIES);


        TBL_BOOK_LOANEE.addDomains(DOM_PK_ID,
                                   DOM_FK_BOOK,
                                   DOM_LOANEE)
                       .setPrimaryKey(DOM_PK_ID)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK)
                       .addIndex(KEY_FK_BOOK, true, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_LOANEE.getName(), TBL_BOOK_LOANEE);


        TBL_BOOK_BOOKSHELF.addDomains(DOM_FK_BOOK,
                                      DOM_FK_BOOKSHELF)
                          .setPrimaryKey(DOM_FK_BOOK, DOM_FK_BOOKSHELF)
                          .addReference(TBL_BOOKS, DOM_FK_BOOK)
                          .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                          .addIndex(KEY_FK_BOOK, false, DOM_FK_BOOK)
                          .addIndex(KEY_FK_BOOKSHELF, false, DOM_FK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOK_BOOKSHELF.getName(), TBL_BOOK_BOOKSHELF);


        TBL_BOOK_TOC_ENTRIES.addDomains(DOM_FK_BOOK,
                                        DOM_FK_TOC_ENTRY,
                                        DOM_BOOK_TOC_ENTRY_POSITION)
                            .setPrimaryKey(DOM_FK_BOOK, DOM_FK_TOC_ENTRY)
                            .addReference(TBL_BOOKS, DOM_FK_BOOK)
                            .addReference(TBL_TOC_ENTRIES, DOM_FK_TOC_ENTRY)
                            .addIndex(KEY_FK_TOC_ENTRY, false, DOM_FK_TOC_ENTRY)
                            .addIndex(KEY_FK_BOOK, false, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_TOC_ENTRIES.getName(), TBL_BOOK_TOC_ENTRIES);

        /* ======================================================================================
         * Configuration tables.
         * ====================================================================================== */

        TBL_BOOKLIST_STYLES.addDomains(DOM_PK_ID,
                                       DOM_STYLE_IS_BUILTIN,
                                       DOM_UUID)
                           .setPrimaryKey(DOM_PK_ID)
                           .addIndex(KEY_UUID, true, DOM_UUID);
        ALL_TABLES.put(TBL_BOOKLIST_STYLES.getName(), TBL_BOOKLIST_STYLES);

        /* ======================================================================================
         *  {@link BooklistBuilder} tables keeping track of the actual list with visibility
         *  and expansion, and the flat list for the book details screen.
         * ====================================================================================== */

        // Prefix name of BOOK_LIST-related tables.
        final String DB_TN_BOOK_LIST_PREFIX = "book_list";
        // this one is a standard table!
        TBL_BOOK_LIST_NODE_STATE =
                new TableDefinition(DB_TN_BOOK_LIST_PREFIX + "_node_settings")
                        .setAlias("bl_ns");

        TMP_TBL_BOOK_LIST =
                new TableDefinition(DB_TN_BOOK_LIST_PREFIX + "_tmp_")
                        .setAlias("bl")
                        .setType(TableDefinition.TableType.Temporary);

        TMP_TBL_BOOK_LIST_ROW_STATE =
                new TableDefinition(DB_TN_BOOK_LIST_PREFIX + "_row_state_tmp_")
                        .setAlias("bl_rs")
                        .setType(TableDefinition.TableType.Temporary);

        TMP_TBL_BOOK_LIST_NAVIGATOR =
                new TableDefinition(DB_TN_BOOK_LIST_PREFIX + "_navigator_tmp_")
                        .setAlias("bl_n")
                        .setType(TableDefinition.TableType.Temporary);

        // Allow debug mode to use standard tables so we can export and inspect the content.
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLES) {
            TMP_TBL_BOOK_LIST.setType(TableDefinition.TableType.Standard);
            TMP_TBL_BOOK_LIST_ROW_STATE.setType(TableType.Standard);
            TMP_TBL_BOOK_LIST_NAVIGATOR.setType(TableType.Standard);
        }

        /*
         * Stores the node state across application restarts.
         */
        TBL_BOOK_LIST_NODE_STATE.addDomains(DOM_PK_ID,
                                            DOM_FK_BOOKSHELF,
                                            DOM_FK_STYLE,

                                            DOM_BL_ROOT_KEY,
                                            DOM_BL_NODE_LEVEL,
                                            DOM_BL_NODE_KIND,
                                            DOM_BL_NODE_EXPANDED
                                           )
                                .setPrimaryKey(DOM_PK_ID)
                                .addIndex(KEY_FK_BOOKSHELF, false, DOM_FK_BOOKSHELF)
                                .addIndex(KEY_FK_STYLE, false, DOM_FK_STYLE);
        ALL_TABLES.put(TBL_BOOK_LIST_NODE_STATE.getName(), TBL_BOOK_LIST_NODE_STATE);

        /*
         * Temporary table used to store a flattened booklist tree structure.
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         *
         * Domains are added at runtime depending on how the list is build.
         */
        TMP_TBL_BOOK_LIST.addDomains(DOM_PK_ID,
                                     DOM_BL_ROOT_KEY)
                         .setPrimaryKey(DOM_PK_ID);
        //TODO: figure out indexes

        /*
         * Keep track of level/expand/visible for each row in TMP_TBL_BOOK_LIST.
         *
         * {@link BooklistBuilder}
         */
        TMP_TBL_BOOK_LIST_ROW_STATE
                .addDomains(DOM_PK_ID,
                            // FK to TMP_TBL_BOOK_LIST
                            DOM_FK_BL_ROW_ID,
                            DOM_BL_ROOT_KEY,
                            // Node data
                            DOM_BL_NODE_LEVEL,
                            DOM_BL_NODE_KIND,
                            DOM_BL_NODE_VISIBLE,
                            DOM_BL_NODE_EXPANDED
                           )
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(KEY_FK_BL_ROW_ID, true, DOM_FK_BL_ROW_ID)
                .addIndex(KEY_BL_NODE_VISIBLE, false, DOM_BL_NODE_VISIBLE)
                .addIndex("NODE_DATA", false,
                          DOM_BL_ROOT_KEY, DOM_BL_NODE_LEVEL, DOM_BL_NODE_EXPANDED);

        // do ***NOT*** add the reference here. It will be added *after* cloning in BooklistBuilder.
        // as the TMP_TBL_BOOK_LIST name will have an instance specific suffix.
        //.addReference(TMP_TBL_BOOK_LIST, DOM_FK_BL_ROW_ID);

        /*
         * FULL representation of TMP_TBL_BOOK_LIST_NAVIGATOR temp table.
         *
         * Get's populated after a new TMP_TBL_BOOK_LIST is created and populated.
         * It provides the linear (flat) list of book ids to move back and forth when
         * the user swipes left and right on the book details screen.
         *
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         */
        TMP_TBL_BOOK_LIST_NAVIGATOR.addDomains(DOM_PK_ID,
                                               DOM_FK_BOOK)
                                   .setPrimaryKey(DOM_PK_ID);
        // no index needed, the PK is enough

        /* ======================================================================================
         *  FTS definitions
         * ====================================================================================== */
        TBL_BOOKS_FTS = new TableDefinition("books_fts").setType(TableType.FTS3);

        DOM_PK_DOCID =
                new Domain.Builder(KEY_PK_DOCID, ColumnInfo.TYPE_INTEGER).primaryKey().build();

        DOM_FTS_AUTHOR_NAME =
                new Domain.Builder(KEY_FTS_AUTHOR_NAME, ColumnInfo.TYPE_TEXT).notNull().build();

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
            Collection<String> tNames = new HashSet<>();
            Collection<String> tAliases = new HashSet<>();
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
