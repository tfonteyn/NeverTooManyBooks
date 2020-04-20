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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
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
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF;

    /* ======================================================================================
     * Basic table definitions with type & alias set.
     * All these should be added to {@link #ALL_TABLES}.
     * ====================================================================================== */
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
    public static final TableDefinition TBL_FTS_BOOKS;

    /** Keeps track of nodes in the list across application restarts. */
    public static final TableDefinition TBL_BOOK_LIST_NODE_STATE;

    /**
     * The temp booklist table. Constructed by {@link BooklistBuilder}.
     * {@link TableDefinition.TableType#Temporary). NOT added to {@link #ALL_TABLES}.
     */
    public static final TableDefinition TMP_TBL_BOOK_LIST;
    /**
     * The temp booklist row state table. Constructed by  {@link RowStateDAO}.
     * {@link TableDefinition.TableType#Temporary). NOT added to {@link #ALL_TABLES}.
     */
    public static final TableDefinition TMP_TBL_BOOK_LIST_ROW_STATE;
    /**
     * This table should always be created without column constraints applied,
     * with the exception of the "_id" primary key autoincrement.
     * {@link TableDefinition.TableType#Temporary). NOT added to {@link #ALL_TABLES}.
     */
    public static final TableDefinition TMP_TBL_BOOK_LIST_NAVIGATOR;


    /* ======================================================================================
     * Primary and Foreign key domain definitions.
     * ====================================================================================== */
    /** Primary key. */
    public static final Domain DOM_PK_ID;
    /** FTS Primary key. */
    public static final Domain DOM_FTS_BOOKS_PK;

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
    public static final Domain DOM_BOOKSHELF_NAME;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_BOOKSHELF.dot(KEY_BOOKSHELF) + ",', ')". */
    public static final Domain DOM_BOOKSHELF_NAME_CSV;

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
    public static final Domain DOM_DATE_FIRST_PUBLICATION;
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
    public static final Domain DOM_BOOK_DATE_READ_START;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_READ_END;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_SIGNED;
    /** {@link #TBL_BOOKS}. A rating goes from 0 to 5 stars, in 0.5 increments. */
    public static final Domain DOM_BOOK_RATING;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRIVATE_NOTES;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_CONDITION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_CONDITION_DUST_COVER;
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

    /** All native id keys supported for lookups. Also see {@link #NATIVE_ID_KEYS}. */
    public static final Collection<Domain> NATIVE_ID_DOMAINS = new ArrayList<>();

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final Domain DOM_LOANEE;
    /**
     * {@link #TBL_BOOK_LOANEE}.
     * Virtual: returns 0 for 'available' or 1 for 'lend out'
     */
    public static final Domain DOM_BL_LOANEE_AS_BOOL;


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
    /**
     * {@link #TBL_FTS_BOOKS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    public static final Domain DOM_FTS_AUTHOR_NAME;
    public static final Domain DOM_FTS_TOC_ENTRY_TITLE;
    /** For sorting in the {@link BooklistBuilder}. */
    public static final Domain DOM_BL_AUTHOR_SORT;

    /* ======================================================================================
     *  {@link BooklistBuilder} domains.
     * ====================================================================================== */
    /** For sorting in the {@link BooklistBuilder}. */
    public static final Domain DOM_BL_SERIES_SORT;
    /**
     * Series number, cast()'d for sorting purposes in {@link BooklistBuilder}
     * so we can sort it numerically regardless of content.
     */
    public static final Domain DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT;
    /** {@link #TMP_TBL_BOOK_LIST} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_PRIMARY_SERIES_COUNT;
    /**
     * {@link #TBL_BOOK_LIST_NODE_STATE}
     * {@link #TMP_TBL_BOOK_LIST_ROW_STATE}
     * {@link BooklistBuilder}.
     * <p>
     * Expression from the original tables that represent the hierarchical key for the node.
     * Stored in each row and used to determine the expand/collapse results.
     */
    public static final Domain DOM_BL_NODE_KEY;
    /** {@link #TBL_BOOK_LIST_NODE_STATE} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_NODE_GROUP;
    /** {@link #TMP_TBL_BOOK_LIST} {@link #TMP_TBL_BOOK_LIST_ROW_STATE} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_NODE_LEVEL;
    /**
     * {@link #TMP_TBL_BOOK_LIST_ROW_STATE} {@link BooklistBuilder}.
     * An expanded node, should always be visible!
     */
    public static final Domain DOM_BL_NODE_EXPANDED;
    /** {@link #TMP_TBL_BOOK_LIST_ROW_STATE} {@link BooklistBuilder}. */
    public static final Domain DOM_BL_NODE_VISIBLE;
    /** Suffix added to a column name to create a specific 'order by' copy of that column. */
    public static final String SUFFIX_KEY_ORDER_BY = "_ob";

    /* ======================================================================================
     *  Keys used as domain names / Bundle keys.
     * ====================================================================================== */
    /** Suffix added to a price column name to create a joined currency column. */
    public static final String SUFFIX_KEY_CURRENCY = "_currency";
    /** Primary key. */
    public static final String KEY_PK_ID = "_id";
    /** Primary key. */
    public static final String KEY_FTS_BOOKS_PK = "docid";
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
    /** External id. - String. */
    public static final String KEY_EID_WORLDCAT = "worldcat_oclc_book_id";

    //NEWTHINGS: add new site specific ID: add a KEY
    // ENHANCE: the search engines already use these when found, but not stored yet.
    /** External id. - String. */
    public static final String KEY_EID_LCCN = "lccn_book_id";
    /** All native id keys supported for lookups. Also see {@link #NATIVE_ID_DOMAINS}. */
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
    public static final String KEY_BOOKSHELF_NAME = "bookshelf";
    /** Alias. */
    public static final String KEY_BOOKSHELF_NAME_CSV = "bs_name_csv";
    /** Alias. */
    public static final String KEY_BOOKSHELF_ID_CSV = "bs_id_csv";

    /** {@link #TBL_AUTHORS} {@link #TBL_BOOK_AUTHOR} */
    public static final String KEY_AUTHOR_FAMILY_NAME = "family_name";
    public static final String KEY_AUTHOR_FAMILY_NAME_OB =
            KEY_AUTHOR_FAMILY_NAME + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_AUTHOR_GIVEN_NAMES = "given_names";
    public static final String KEY_AUTHOR_GIVEN_NAMES_OB =
            KEY_AUTHOR_GIVEN_NAMES + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_AUTHOR_IS_COMPLETE = "author_complete";
    public static final String KEY_AUTHOR_FORMATTED = "author_formatted";
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";
    public static final String KEY_BOOK_AUTHOR_TYPE_BITMASK = "author_type";
    public static final String KEY_BOOK_AUTHOR_POSITION = "author_position";
    /** {@link #TBL_SERIES} {@link #TBL_BOOK_SERIES} */
    public static final String KEY_SERIES_TITLE = "series_name";
    public static final String KEY_SERIES_TITLE_OB = KEY_SERIES_TITLE + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_SERIES_FORMATTED = "series_formatted";
    public static final String KEY_SERIES_IS_COMPLETE = "series_complete";
    public static final String KEY_BOOK_NUM_IN_SERIES = "series_num";
    public static final String KEY_BOOK_SERIES_POSITION = "series_position";
    /** {@link #TBL_TOC_ENTRIES}. */
    public static final String KEY_BOOK_TOC_ENTRY_POSITION = "toc_entry_position";
    /** {@link #TBL_BOOKS}. */
    public static final String KEY_DATE_ADDED = "date_added";
    public static final String KEY_DATE_LAST_UPDATED = "last_update_date";
    public static final String KEY_UUID = "uuid";
    public static final String KEY_TITLE = "title";
    public static final String KEY_TITLE_OB = KEY_TITLE + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_ISBN = "isbn";
    public static final String KEY_DATE_FIRST_PUBLICATION = "first_publication";
    public static final String KEY_PUBLISHER = "publisher";
    public static final String KEY_DATE_PUBLISHED = "date_published";
    public static final String KEY_PRINT_RUN = "print_run";
    public static final String KEY_PRICE_LISTED = "list_price";
    public static final String KEY_PRICE_LISTED_CURRENCY = KEY_PRICE_LISTED + SUFFIX_KEY_CURRENCY;
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
    public static final String KEY_PRICE_PAID_CURRENCY = KEY_PRICE_PAID + SUFFIX_KEY_CURRENCY;
    public static final String KEY_DATE_ACQUIRED = "date_acquired";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_READ = "read";
    public static final String KEY_READ_START = "read_start";
    public static final String KEY_READ_END = "read_end";
    public static final String KEY_SIGNED = "signed";
    public static final String KEY_RATING = "rating";
    public static final String KEY_PRIVATE_NOTES = "notes";
    public static final String KEY_BOOK_CONDITION = "cond_bk";
    public static final String KEY_BOOK_CONDITION_COVER = "cond_cvr";

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final String KEY_LOANEE = "loaned_to";
    public static final String KEY_LOANEE_AS_BOOLEAN = "loaned_flag";
    /** {@link #TBL_BOOKLIST_STYLES}. */
    public static final String KEY_STYLE_IS_BUILTIN = "builtin";
    /** {@link #TBL_FTS_BOOKS}. Semi-colon concatenated authors. */
    public static final String KEY_FTS_AUTHOR_NAME = "author_name";
    /** {@link #TBL_FTS_BOOKS}. Semi-colon concatenated titles. */
    public static final String KEY_FTS_TOC_ENTRY_TITLE = "toc_title";
    public static final String KEY_BOOK_COUNT = "book_count";
    /** BooklistBuilder. Virtual domains. */
    public static final String KEY_BL_AUTHOR_SORT = "bl_aut_sort";
    public static final String KEY_BL_SERIES_SORT = "bl_ser_sort";
    public static final String KEY_BL_SERIES_NUM_FLOAT = "bl_ser_num_float";
    public static final String KEY_BL_PRIMARY_SERIES_COUNT = "bl_prim_ser_cnt";
    /**
     * {@link #TBL_BOOK_LIST_NODE_STATE}.
     * The foreign key in the row-state table, pointing to the list table.
     */
    public static final String KEY_FK_BL_ROW_ID = "real_row_id";
    /** {@link #TBL_BOOK_LIST_NODE_STATE}. */
    public static final String KEY_BL_NODE_KEY = "node_key";
    /** {@link #TBL_BOOK_LIST_NODE_STATE} + {@link #TMP_TBL_BOOK_LIST_ROW_STATE}. */
    public static final String KEY_BL_NODE_LEVEL = "node_level";
    public static final String KEY_BL_NODE_GROUP = "node_group";
    public static final String KEY_BL_NODE_VISIBLE = "node_visible";
    public static final String KEY_BL_NODE_EXPANDED = "node_expanded";
    /**
     * All money keys.
     * Used with {@code MONEY_KEYS.contains(key)} to check if a key is about money.
     */
    public static final String MONEY_KEYS = KEY_PRICE_LISTED + ',' + KEY_PRICE_PAID;
    /**
     * Column alias.
     * <p>
     * BooklistBuilder: an alias for the {@link #TMP_TBL_BOOK_LIST_ROW_STATE} rowId
     * listViewRowPosition = KEY_BL_LIST_VIEW_ROW_ID - 1.
     * <p>
     * DOM_BL_LIST_VIEW_ROW_ID =
     * new Domain.Builder(KEY_BL_LIST_VIEW_ROW_ID, ColumnInfo.TYPE_INTEGER)
     * .notNull().build();
     */
    public static final String KEY_BL_LIST_VIEW_ROW_ID = "lv_row_id";

    /* ======================================================================================
     *  Keys used as column alias names / Bundle keys.
     * ====================================================================================== */
    /**
     * Column alias.
     * <p>
     * The type of a TOC entry. See {@link TocEntry.Type}
     * <p>
     * DOM_TOC_TYPE =
     * new Domain.Builder(KEY_TOC_TYPE, ColumnInfo.TYPE_TEXT).build();
     */
    public static final String KEY_TOC_TYPE = "type";
    /**
     * This key is for the file-based thumbnail field. It's not in the actual database.
     * But otherwise it's used just like other keys in this class.
     */
    public static final String KEY_THUMBNAIL = "thumbnail";
    /**
     * Users can select which fields they use / don't want to use.
     * Each field has an entry in the Preferences.
     * The key is suffixed with the name of the field.
     */
    private static final String PREFS_PREFIX_FIELD_VISIBILITY = "fields.visibility.";

    static {
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

        DOM_FK_BL_ROW_ID =
                new Domain.Builder(KEY_FK_BL_ROW_ID, ColumnInfo.TYPE_INTEGER).build();

        /* ======================================================================================
         *  Multi table domains
         * ====================================================================================== */

        DOM_TITLE =
                new Domain.Builder(KEY_TITLE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();
        DOM_TITLE_OB =
                new Domain.Builder(KEY_TITLE_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_DATE_PUBLISHED =
                new Domain.Builder(KEY_DATE_PUBLISHED, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();

        DOM_DATE_FIRST_PUBLICATION =
                new Domain.Builder(KEY_DATE_FIRST_PUBLICATION, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();

        DOM_DATE_LAST_UPDATED =
                new Domain.Builder(KEY_DATE_LAST_UPDATED, ColumnInfo.TYPE_DATETIME)
                        .notNull().withDefaultCurrentTimeStamp().build();

        /* ======================================================================================
         *  Bookshelf domains
         * ====================================================================================== */

        DOM_BOOKSHELF_NAME =
                new Domain.Builder(KEY_BOOKSHELF_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();
        DOM_BOOKSHELF_NAME_CSV =
                new Domain.Builder(KEY_BOOKSHELF_NAME_CSV, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  Author domains
         * ====================================================================================== */

        DOM_AUTHOR_FAMILY_NAME =
                new Domain.Builder(KEY_AUTHOR_FAMILY_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();

        DOM_AUTHOR_FAMILY_NAME_OB =
                new Domain.Builder(KEY_AUTHOR_FAMILY_NAME_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES =
                new Domain.Builder(KEY_AUTHOR_GIVEN_NAMES, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES_OB =
                new Domain.Builder(KEY_AUTHOR_GIVEN_NAMES_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_AUTHOR_IS_COMPLETE =
                new Domain.Builder(KEY_AUTHOR_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_AUTHOR_FORMATTED =
                new Domain.Builder(KEY_AUTHOR_FORMATTED, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        DOM_AUTHOR_FORMATTED_GIVEN_FIRST =
                new Domain.Builder(KEY_AUTHOR_FORMATTED_GIVEN_FIRST, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  Series domains
         * ====================================================================================== */

        DOM_SERIES_TITLE =
                new Domain.Builder(KEY_SERIES_TITLE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();
        DOM_SERIES_TITLE_OB =
                new Domain.Builder(KEY_SERIES_TITLE_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();
        DOM_SERIES_IS_COMPLETE =
                new Domain.Builder(KEY_SERIES_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(0)
                        .build();
        DOM_SERIES_FORMATTED =
                new Domain.Builder(KEY_SERIES_FORMATTED, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  Book domains
         * ====================================================================================== */

        DOM_BOOK_ISBN =
                new Domain.Builder(KEY_ISBN, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PUBLISHER =
                new Domain.Builder(KEY_PUBLISHER, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_PRINT_RUN =
                new Domain.Builder(KEY_PRINT_RUN, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PRICE_LISTED =
                new Domain.Builder(KEY_PRICE_LISTED, ColumnInfo.TYPE_REAL)
                        .notNull()
                        .withDefault(0d)
                        .build();

        DOM_BOOK_PRICE_LISTED_CURRENCY =
                new Domain.Builder(KEY_PRICE_LISTED_CURRENCY, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PAGES =
                new Domain.Builder(KEY_PAGES, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_FORMAT =
                new Domain.Builder(KEY_FORMAT, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_COLOR =
                new Domain.Builder(KEY_COLOR, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_LANGUAGE =
                new Domain.Builder(KEY_LANGUAGE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_GENRE =
                new Domain.Builder(KEY_GENRE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_DESCRIPTION =
                new Domain.Builder(KEY_DESCRIPTION, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_TOC_BITMASK =
                new Domain.Builder(KEY_TOC_BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(Book.TOC_SINGLE_AUTHOR_SINGLE_WORK)
                        .build();

        /* ======================================================================================
         *  Book personal data domains
         * ====================================================================================== */

        DOM_BOOK_UUID =
                new Domain.Builder(KEY_BOOK_UUID, ColumnInfo.TYPE_TEXT)
                        .notNull().withDefault("(lower(hex(randomblob(16))))").build();

        DOM_BOOK_EDITION_BITMASK =
                new Domain.Builder(KEY_EDITION_BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull().withDefault(0).build();

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
                        .notNull().withDefaultCurrentTimeStamp().build();

        DOM_BOOK_LOCATION =
                new Domain.Builder(KEY_LOCATION, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_READ =
                new Domain.Builder(KEY_READ, ColumnInfo.TYPE_BOOLEAN)
                        .notNull().withDefault(0).build();
        DOM_BOOK_DATE_READ_START =
                new Domain.Builder(KEY_READ_START, ColumnInfo.TYPE_DATE)
                        .notNull().withDefaultEmptyString().build();
        DOM_BOOK_DATE_READ_END =
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

        DOM_BOOK_CONDITION =
                new Domain.Builder(KEY_BOOK_CONDITION, ColumnInfo.TYPE_INTEGER)
                        .notNull().withDefault(0).build();
        DOM_BOOK_CONDITION_DUST_COVER =
                new Domain.Builder(KEY_BOOK_CONDITION_COVER, ColumnInfo.TYPE_INTEGER)
                        .notNull().withDefault(0).build();

        /* ======================================================================================
         *  Book external website id domains
         * ====================================================================================== */
        //NEWTHINGS: add new site specific ID: add a DOM and add it to NATIVE_ID_DOMAINS
        DOM_EID_GOODREADS_BOOK =
                new Domain.Builder(KEY_EID_GOODREADS_BOOK, ColumnInfo.TYPE_INTEGER).build();
        NATIVE_ID_DOMAINS.add(DOM_EID_GOODREADS_BOOK);

        DOM_BOOK_GOODREADS_LAST_SYNC_DATE =
                new Domain.Builder(KEY_BOOK_GOODREADS_LAST_SYNC_DATE, ColumnInfo.TYPE_DATE)
                        .notNull().withDefault("'0000-00-00'").build();

        DOM_EID_ISFDB =
                new Domain.Builder(KEY_EID_ISFDB, ColumnInfo.TYPE_INTEGER).build();
        NATIVE_ID_DOMAINS.add(DOM_EID_ISFDB);

        DOM_EID_LIBRARY_THING =
                new Domain.Builder(KEY_EID_LIBRARY_THING, ColumnInfo.TYPE_INTEGER).build();
        NATIVE_ID_DOMAINS.add(DOM_EID_LIBRARY_THING);

        DOM_EID_OPEN_LIBRARY =
                new Domain.Builder(KEY_EID_OPEN_LIBRARY, ColumnInfo.TYPE_TEXT).build();
        NATIVE_ID_DOMAINS.add(DOM_EID_OPEN_LIBRARY);

        DOM_EID_STRIP_INFO_BE =
                new Domain.Builder(KEY_EID_STRIP_INFO_BE, ColumnInfo.TYPE_INTEGER).build();
        NATIVE_ID_DOMAINS.add(DOM_EID_STRIP_INFO_BE);

        /* ======================================================================================
         *  Loanee domains
         * ====================================================================================== */

        DOM_LOANEE = new Domain.Builder(KEY_LOANEE, ColumnInfo.TYPE_TEXT)
                .notNull()
                .localized()
                .build();

        DOM_BL_LOANEE_AS_BOOL = new Domain.Builder(KEY_LOANEE_AS_BOOLEAN, ColumnInfo.TYPE_INTEGER)
                .notNull()
                .build();

        /* ======================================================================================
         *  Link table domains
         * ====================================================================================== */

        DOM_BOOK_AUTHOR_TYPE_BITMASK =
                new Domain.Builder(KEY_BOOK_AUTHOR_TYPE_BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull().withDefault(0).build();
        DOM_BOOK_AUTHOR_POSITION =
                new Domain.Builder(KEY_BOOK_AUTHOR_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull().build();

        DOM_BOOK_SERIES_POSITION =
                new Domain.Builder(KEY_BOOK_SERIES_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull().build();
        DOM_BOOK_NUM_IN_SERIES =
                new Domain.Builder(KEY_BOOK_NUM_IN_SERIES, ColumnInfo.TYPE_TEXT)
                        .localized()
                        .build();

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
         *  BooklistBuilder domains
         * ====================================================================================== */

        DOM_BL_AUTHOR_SORT =
                new Domain.Builder(KEY_BL_AUTHOR_SORT, ColumnInfo.TYPE_TEXT).build();

        DOM_BL_SERIES_SORT =
                new Domain.Builder(KEY_BL_SERIES_SORT, ColumnInfo.TYPE_TEXT).build();

        DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT =
                new Domain.Builder(KEY_BL_SERIES_NUM_FLOAT, ColumnInfo.TYPE_REAL).build();

        DOM_BL_PRIMARY_SERIES_COUNT =
                new Domain.Builder(KEY_BL_PRIMARY_SERIES_COUNT, ColumnInfo.TYPE_INTEGER).build();


        DOM_BL_NODE_KEY =
                new Domain.Builder(KEY_BL_NODE_KEY, ColumnInfo.TYPE_TEXT).build();
        DOM_BL_NODE_GROUP =
                new Domain.Builder(KEY_BL_NODE_GROUP, ColumnInfo.TYPE_INTEGER).notNull().build();
        DOM_BL_NODE_LEVEL =
                new Domain.Builder(KEY_BL_NODE_LEVEL, ColumnInfo.TYPE_INTEGER).notNull().build();
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
                             DOM_DATE_FIRST_PUBLICATION,
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
                             DOM_BOOK_DATE_READ_START,
                             DOM_BOOK_DATE_READ_END,

                             DOM_BOOK_EDITION_BITMASK,
                             DOM_BOOK_SIGNED,
                             DOM_BOOK_RATING,
                             DOM_BOOK_LOCATION,
                             DOM_BOOK_PRIVATE_NOTES,
                             DOM_BOOK_CONDITION,
                             DOM_BOOK_CONDITION_DUST_COVER,

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
                 .addIndex(KEY_TITLE, false, DOM_TITLE)
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
                                 DOM_BOOKSHELF_NAME)
                     .setPrimaryKey(DOM_PK_ID)
                     .addReference(TBL_BOOKLIST_STYLES, DOM_FK_STYLE)
                     .addIndex(KEY_BOOKSHELF_NAME, true, DOM_BOOKSHELF_NAME);
        ALL_TABLES.put(TBL_BOOKSHELF.getName(), TBL_BOOKSHELF);


        TBL_TOC_ENTRIES.addDomains(DOM_PK_ID,
                                   DOM_FK_AUTHOR,
                                   DOM_TITLE,
                                   DOM_TITLE_OB,
                                   DOM_DATE_FIRST_PUBLICATION)
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
        // this one is a standard table to preserve the state across app restarts
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

        // Stores the node state across application restarts.
        TBL_BOOK_LIST_NODE_STATE.addDomains(DOM_PK_ID,
                                            DOM_BL_NODE_KEY,
                                            DOM_BL_NODE_LEVEL,
                                            DOM_BL_NODE_GROUP,
                                            DOM_BL_NODE_EXPANDED,

                                            DOM_FK_BOOKSHELF,
                                            DOM_FK_STYLE
                                           )
                                .setPrimaryKey(DOM_PK_ID)
                                .addIndex("BOOKSHELF_STYLE", false,
                                          DOM_FK_BOOKSHELF, DOM_FK_STYLE);
        ALL_TABLES.put(TBL_BOOK_LIST_NODE_STATE.getName(), TBL_BOOK_LIST_NODE_STATE);

        /*
         * Temporary table used to store a flattened booklist tree structure.
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         *
         * Domains are added at runtime depending on how the list is build.
         */
        TMP_TBL_BOOK_LIST.addDomains(DOM_PK_ID,
                                     DOM_BL_NODE_KEY)
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
                            DOM_BL_NODE_KEY,
                            // Node data
                            DOM_BL_NODE_LEVEL,
                            DOM_BL_NODE_GROUP,
                            DOM_BL_NODE_VISIBLE,
                            DOM_BL_NODE_EXPANDED
                           )
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(KEY_FK_BL_ROW_ID, true, DOM_FK_BL_ROW_ID)
                .addIndex(KEY_BL_NODE_VISIBLE, false, DOM_BL_NODE_VISIBLE)
                .addIndex("NODE_DATA", false,
                          DOM_BL_NODE_KEY, DOM_BL_NODE_LEVEL, DOM_BL_NODE_EXPANDED);

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
         *  reminder: no need for a type nor constraints: https://sqlite.org/fts3.html
         * ====================================================================================== */
        TBL_FTS_BOOKS = new TableDefinition("books_fts").setType(TableType.FTS3);

        DOM_FTS_BOOKS_PK =
                new Domain.Builder(KEY_FTS_BOOKS_PK, ColumnInfo.TYPE_INTEGER).primaryKey().build();

        DOM_FTS_AUTHOR_NAME =
                new Domain.Builder(KEY_FTS_AUTHOR_NAME, ColumnInfo.TYPE_TEXT).build();

        DOM_FTS_TOC_ENTRY_TITLE =
                new Domain.Builder(KEY_FTS_TOC_ENTRY_TITLE, ColumnInfo.TYPE_TEXT).build();

        // should NOT be added to {@link #ALL_TABLES}.
        TBL_FTS_BOOKS.addDomains(DOM_TITLE,
                                 DOM_FTS_AUTHOR_NAME,
                                 DOM_SERIES_TITLE,

                                 DOM_BOOK_DESCRIPTION,
                                 DOM_BOOK_PRIVATE_NOTES,
                                 DOM_BOOK_PUBLISHER,
                                 DOM_BOOK_GENRE,
                                 DOM_BOOK_LOCATION,
                                 DOM_BOOK_ISBN,

                                 DOM_FTS_TOC_ENTRY_TITLE
                                );
    }

    private DBDefinitions() {
    }

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param context Current context
     * @param key     DBDefinitions.KEY_x to lookup
     *
     * @return {@code true} if the user wants to use this field.
     */
    public static boolean isUsed(@NonNull final Context context,
                                 @UserSelectedDomain @NonNull final String key) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PREFS_PREFIX_FIELD_VISIBILITY + key, true);
    }

    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param sharedPreferences prefs object
     * @param key               DBDefinitions.KEY_x to lookup
     *
     * @return {@code true} if the user wants to use this field.
     */
    public static boolean isUsed(@NonNull final SharedPreferences sharedPreferences,
                                 @UserSelectedDomain @NonNull final String key) {
        return sharedPreferences.getBoolean(PREFS_PREFIX_FIELD_VISIBILITY + key, true);
    }

    /**
     * NEWTHINGS: new fields visibility.
     * Same set as on xml/preferences_field_visibility.xml
     */
    @StringDef({KEY_ISBN,
                KEY_THUMBNAIL,
                KEY_BOOK_AUTHOR_TYPE_BITMASK,
                KEY_SERIES_TITLE,
                KEY_TOC_BITMASK,
                KEY_DESCRIPTION,
                KEY_PUBLISHER,
                KEY_DATE_PUBLISHED,
                KEY_DATE_FIRST_PUBLICATION,
                KEY_FORMAT,
                KEY_COLOR,
                KEY_GENRE,
                KEY_LANGUAGE,
                KEY_PAGES,
                KEY_PRICE_LISTED,
                KEY_LOANEE,
                KEY_PRIVATE_NOTES,
                KEY_BOOK_CONDITION,
                KEY_BOOK_CONDITION_COVER,
                KEY_LOCATION,
                KEY_PRICE_PAID,
                KEY_READ,
                KEY_READ_START,
                KEY_READ_END,
                KEY_EDITION_BITMASK,
                KEY_SIGNED,
                KEY_RATING})
    @Retention(RetentionPolicy.SOURCE)
    private @interface UserSelectedDomain {

    }
}
