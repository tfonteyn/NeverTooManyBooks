/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BookDetailsFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BookLevelFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Static definitions of database objects.
 * This is a <strong>mostly</strong> complete representation of the application database.
 * <p>
 * <strong>Note:</strong> Fields 'name' attribute must be in LOWER CASE.
 * <p>
 * TODO: Collated indexes need to be done manually. See {@link DBHelper#recreateIndices()}
 * <p>
 * Currently (2022-05-14) UTC datetime is used with::
 * <ul>Main database:
 *  <li>{@link #DOM_ADDED__UTC}</li>
 *  <li>{@link #DOM_LAST_UPDATED__UTC}</li>
 *  <li>{@link #DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC}</li>
 *  <li>{@link #DOM_STRIP_INFO_BE_LAST_SYNC__UTC}</li>
 * </ul>
 * <ul>Covers cache database:
 *   <li>{@link CacheDbHelper}#IMAGE_LAST_UPDATED__UTC}</li>
 * </ul>
 * <p>
 * All others, are considered USER local time zone.
 * <p>
 * Rationale: given this is an app running on a device in the users pocket,
 * using UTC for those columns would only be useful to users who travel to other timezones,
 * reset their devices to the new timezone, and actively edit (user) date columns while
 * travelling.
 */
@SuppressWarnings("WeakerAccess")
public final class DBDefinitions {

    /**
     * A collection of all tables used to be able to rebuild indexes etc...,
     * added in order so interdependency's work out.
     * <p>
     * Only add standard tables. Do not add temporary/FTS tables.
     * app tables
     * {@link #TBL_BOOKLIST_STYLES},
     * <p>
     * basic user data tables
     * {@link #TBL_BOOKSHELF},
     * {@link #TBL_BOOKSHELF_FILTERS}
     * {@link #TBL_AUTHORS},
     * {@link #TBL_SERIES},
     * {@link #TBL_PUBLISHERS},
     * {@link #TBL_BOOKS},
     * {@link #TBL_TOC_ENTRIES},
     * {@link #TBL_DELETED_BOOKS},
     * <p>
     * link tables
     * {@link #TBL_PSEUDONYM_AUTHOR},
     * <p>
     * {@link #TBL_BOOK_BOOKSHELF},
     * {@link #TBL_BOOK_AUTHOR},
     * {@link #TBL_BOOK_SERIES},
     * {@link #TBL_BOOK_PUBLISHER},
     * {@link #TBL_BOOK_TOC_ENTRIES},
     * {@link #TBL_BOOK_LOANEE},
     * <p>
     * {@link #TBL_CALIBRE_BOOKS},
     * {@link #TBL_CALIBRE_LIBRARIES},
     * <p>
     * permanent booklist management tables
     * {@link #TBL_BOOK_LIST_NODE_STATE}: storage of the expanded/collapsed status
     * of the book list tree.
     * <p>
     * {@link #TBL_STRIPINFO_COLLECTION}: stores external id's for new books to import
     * from this site. Used as a means to split the relatively fast process of getting
     * the collection data (fast) and as a next step importing new books (slow).
     */
    public static final Map<String, TableDefinition> ALL_TABLES = new LinkedHashMap<>();

    /* ======================================================================================
     * Basic table definitions with type & alias set.
     * All these should be added to {@link #ALL_TABLES}.
     * ====================================================================================== */

    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF;
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF_FILTERS;
    /** Basic table definition. */
    public static final TableDefinition TBL_AUTHORS;
    /** Basic table definition. */
    public static final TableDefinition TBL_SERIES;
    /** Basic table definition. */
    public static final TableDefinition TBL_PUBLISHERS;
    /** Basic table definition. */
    public static final TableDefinition TBL_TOC_ENTRIES;
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKS;
    /** Basic table definition. Track UUID's of deleted books for full sync functionality. */
    public static final TableDefinition TBL_DELETED_BOOKS;
    /** link table. */
    public static final TableDefinition TBL_BOOK_BOOKSHELF;
    /** link table. */
    public static final TableDefinition TBL_BOOK_AUTHOR;
    /** link table. */
    public static final TableDefinition TBL_BOOK_SERIES;
    /** link table. */
    public static final TableDefinition TBL_BOOK_PUBLISHER;
    /** link table. */
    public static final TableDefinition TBL_BOOK_LOANEE;
    /** link table. */
    public static final TableDefinition TBL_BOOK_TOC_ENTRIES;

    /** Map alternative names for Authors. */
    public static final TableDefinition TBL_PSEUDONYM_AUTHOR;

    /** User defined styles. */
    public static final TableDefinition TBL_BOOKLIST_STYLES;
    /** Keeps track of nodes in the list across application restarts. */
    public static final TableDefinition TBL_BOOK_LIST_NODE_STATE;

    /** A bridge to a Calibre database. Partially imported data. */
    public static final TableDefinition TBL_CALIBRE_BOOKS;
    /** The custom fields in Calibre, mapped to our local fields. */
    public static final TableDefinition TBL_CALIBRE_CUSTOM_FIELDS;
    /** The Calibre library(ies) to which we have/are connected. **/
    public static final TableDefinition TBL_CALIBRE_LIBRARIES;
    /** The mapping of a Calibre Library/Virtual Library to a Bookshelf. */
    public static final TableDefinition TBL_CALIBRE_VIRTUAL_LIBRARIES;

    /** A bridge to the stripinfo.be site. Site specific imported data. */
    public static final TableDefinition TBL_STRIPINFO_COLLECTION;

    /* ======================================================================================
     * Primary and Foreign key domain definitions.
     * ====================================================================================== */
    /** Primary key. */
    public static final Domain DOM_PK_ID;

    /** Foreign key. */
    public static final Domain DOM_FK_AUTHOR;
    /** Foreign key. */
    public static final Domain DOM_FK_BOOKSHELF;
    /** Foreign key. */
    public static final Domain DOM_FK_BOOK;
    /** Foreign key. */
    public static final Domain DOM_FK_SERIES;
    /** Foreign key. */
    public static final Domain DOM_FK_PUBLISHER;
    /** Foreign key. */
    public static final Domain DOM_FK_TOC_ENTRY;
    /** Foreign key. */
    public static final Domain DOM_FK_CALIBRE_LIBRARY;
    /**
     * Foreign key.
     * When a style is deleted, this key will be (re)set to
     * {@link BuiltinStyle#HARD_DEFAULT_ID}
     */
    public static final Domain DOM_FK_STYLE;

    /* ======================================================================================
     * Domain definitions.
     * ====================================================================================== */
    /** {@link #TBL_BOOKSHELF}. */
    public static final Domain DOM_BOOKSHELF_NAME;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_BOOKSHELF.dot(KEY_BOOKSHELF) + ",', ')". */
    public static final Domain DOM_BOOKSHELF_NAME_CSV;
    /** Saved booklist adapter position of current top row. */
    public static final Domain DOM_BOOKSHELF_BL_TOP_POS;
    /** Saved booklist adapter top row offset from view top. */
    public static final Domain DOM_BOOKSHELF_BL_TOP_OFFSET;

    /** {@link #TBL_BOOKSHELF_FILTERS}. */
    public static final Domain DOM_BOOKSHELF_FILTER_NAME;
    public static final Domain DOM_BOOKSHELF_FILTER_VALUE;

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
    /**
     * {@link #TBL_PSEUDONYM_AUTHOR}.
     * <p>
     * This is the Author of a book; i.e. the author name as printed on the book cover.
     * If it's a pseudonym, it will have a matching entry in {@link #TBL_PSEUDONYM_AUTHOR}.
     * That entry, has the id for the real name of author {@link #DOM_AUTHOR_REAL_AUTHOR}.
     * <p>
     * e.g.
     * "Paul French" is an author with id==123;
     * Table {@link #TBL_PSEUDONYM_AUTHOR} contains a row with
     * DOM_AUTHOR_PSEUDONYM==123;
     * DOM_AUTHOR_REAL_AUTHOR==456;
     * where 456 is "Isaac Asimov"
     */
    public static final Domain DOM_AUTHOR_PSEUDONYM;
    /**
     * {@link #TBL_PSEUDONYM_AUTHOR}.
     * <p>
     * Link column back to the {@link #TBL_AUTHORS}.
     */
    public static final Domain DOM_AUTHOR_REAL_AUTHOR;

    /** Virtual: "FamilyName, GivenName". */
    public static final Domain DOM_AUTHOR_FORMATTED_FAMILY_FIRST;

    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_TITLE;
    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_TITLE_OB;
    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_IS_COMPLETE;

    /** {@link #TBL_PUBLISHERS}. */
    public static final Domain DOM_PUBLISHER_NAME;
    /** {@link #TBL_PUBLISHERS}. */
    public static final Domain DOM_PUBLISHER_NAME_OB;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME) + ",', ')". */
    public static final Domain DOM_PUBLISHER_NAME_CSV;


    /**
     * The actual title of the book as it appears on the cover.
     * For translated book this will be the translated title.
     * (or the title of a story/entry in the table of content).
     *
     * @see #TBL_BOOKS
     * @see #TBL_TOC_ENTRIES
     * @see #DOM_TITLE_OB
     */
    public static final Domain DOM_TITLE;
    /**
     * 'Order By' for the {@link #DOM_TITLE}. Lowercase, and stripped of spaces etc...
     *
     * @see #TBL_BOOKS
     * @see #TBL_TOC_ENTRIES
     * @see #DOM_TITLE
     */
    public static final Domain DOM_TITLE_OB;

    /**
     * For translated books, the title in the original language.
     * ENHANCE: Currently there is no extra field to indicate the original language.
     */
    public static final Domain DOM_TITLE_ORIGINAL_LANG;

    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_ISBN;
    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final Domain DOM_DATE_FIRST_PUBLICATION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_PUBLISHED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRINT_RUN;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_LISTED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_LISTED_CURRENCY;
    /**
     * {@link #TBL_BOOKS}.
     * <p>
     * Note this is a <strong>TEXT</strong> field. See {@link DBKey#PAGE_COUNT}.
     */
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
    public static final Domain DOM_BOOK_EDITION;
    /** {@link #TBL_BOOKS}. See {@link Book.ContentType}. */
    public static final Domain DOM_BOOK_CONTENT_TYPE;
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
    public static final Domain DOM_ADDED__UTC;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_LAST_UPDATED__UTC;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_GENRE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_LOCATION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_READ;
    /**
     * {@link #TBL_BOOKS}. This is a string with different encodings.
     *
     * @see Book#getReadingProgress()
     */
    public static final Domain DOM_BOOK_READ_PROGRESS;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_READ_START;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_READ_END;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_SIGNED;
    /** {@link #TBL_BOOKS}. A rating goes from 1 to 5 stars, in 0.5 increments; 0 == not set. */
    public static final Domain DOM_BOOK_RATING;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRIVATE_NOTES;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_CONDITION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_CONDITION_DUST_COVER;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_AUTO_UPDATE;

    /** {@link #TBL_BOOKS}. Book ID, not 'work' ID. */
    public static final Domain DOM_ESID_GOODREADS_BOOK;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_ISFDB;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_LIBRARY_THING;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_OPEN_LIBRARY;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_STRIP_INFO_BE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_LAST_DODO_NL;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_BEDETHEQUE;

    /**
     * {@link #TBL_CALIBRE_LIBRARIES}.
     * The physical Calibre library ID as needed in ajax calls.
     */
    public static final Domain DOM_CALIBRE_LIBRARY_STRING_ID;

    /** {@link #TBL_CALIBRE_LIBRARIES}. */
    public static final Domain DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC;

    /** {@link #TBL_CALIBRE_LIBRARIES}. */
    public static final Domain DOM_CALIBRE_LIBRARY_UUID;
    /** {@link #TBL_CALIBRE_LIBRARIES} {@link #TBL_CALIBRE_VIRTUAL_LIBRARIES}. Display name. */
    public static final Domain DOM_CALIBRE_LIBRARY_NAME;

    /** {@link #TBL_CALIBRE_VIRTUAL_LIBRARIES}. Expression or {@code null} for the physical lib. */
    public static final Domain DOM_CALIBRE_VIRT_LIB_EXPR;

    /** {@link #TBL_CALIBRE_CUSTOM_FIELDS}. */
    public static final Domain DOM_CALIBRE_CUSTOM_FIELD_NAME;
    /** {@link #TBL_CALIBRE_CUSTOM_FIELDS}. */
    public static final Domain DOM_CALIBRE_CUSTOM_FIELD_TYPE;
    /** {@link #TBL_CALIBRE_CUSTOM_FIELDS}. */
    public static final Domain DOM_CALIBRE_CUSTOM_FIELD_MAPPING;

    /** {@link #TBL_CALIBRE_BOOKS}. */
    public static final Domain DOM_CALIBRE_BOOK_ID;
    /** {@link #TBL_CALIBRE_BOOKS}. */
    public static final Domain DOM_CALIBRE_BOOK_UUID;
    /** {@link #TBL_CALIBRE_BOOKS}. */
    public static final Domain DOM_CALIBRE_BOOK_MAIN_FORMAT;

    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_COLLECTION_ID;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_OWNED;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_DIGITAL;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_WANTED;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_AMOUNT;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_LAST_SYNC__UTC;

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final Domain DOM_LOANEE;

    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final Domain DOM_BOOK_AUTHOR_TYPE_BITMASK;
    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final Domain DOM_BOOK_AUTHOR_POSITION;


    /** {@link #TBL_BOOK_TOC_ENTRIES}. */
    public static final Domain DOM_BOOK_TOC_ENTRY_POSITION;

    /** {@link #TBL_BOOKLIST_STYLES}. */
    public static final Domain DOM_STYLE_NAME;
    public static final Domain DOM_STYLE_TYPE;
    public static final Domain DOM_STYLE_IS_PREFERRED;
    public static final Domain DOM_STYLE_MENU_POSITION;
    public static final Domain DOM_STYLE_LAYOUT;
    public static final Domain DOM_STYLE_COVER_CLICK_ACTION;
    public static final Domain DOM_STYLE_COVER_LONG_CLICK_ACTION;

    public static final Domain DOM_STYLE_EXP_LEVEL;
    public static final Domain DOM_STYLE_ROW_USES_PREF_HEIGHT;
    public static final Domain DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME;
    public static final Domain DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME;
    public static final Domain DOM_STYLE_TITLE_SHOW_REORDERED;
    public static final Domain DOM_STYLE_READ_STATUS_WITH_PROGRESS;
    public static final Domain DOM_STYLE_TEXT_SCALE;
    public static final Domain DOM_STYLE_COVER_SCALE;
    public static final Domain DOM_STYLE_LIST_HEADER;
    public static final Domain DOM_STYLE_BOOK_DETAIL_FIELDS_VISIBILITY;
    public static final Domain DOM_STYLE_BOOK_LEVEL_FIELDS_VISIBILITY;
    public static final Domain DOM_STYLE_BOOK_LEVEL_FIELDS_ORDER_BY;
    public static final Domain DOM_STYLE_GROUPS;
    public static final Domain DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE;
    public static final Domain DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH;


    /** {@link #TBL_BOOK_SERIES}. */
    public static final Domain DOM_BOOK_NUM_IN_SERIES;
    /**
     * {@link #TBL_BOOK_SERIES}.
     * The Series position is the order the Series show up in a book.
     * Particularly important for "primary series"
     * and in lists where 'all' Series are shown.
     */
    public static final Domain DOM_BOOK_SERIES_POSITION;

    /**
     * {@link #TBL_BOOK_PUBLISHER}.
     * The Publisher position is the order the Publishers show up in a book.
     * Particularly important for "primary Publisher"
     * and in lists where 'all' Publisher are shown.
     */
    public static final Domain DOM_BOOK_PUBLISHER_POSITION;

    /** {@link #TBL_BOOKLIST_STYLES} java.util.UUID value stored as a string. */
    public static final Domain DOM_STYLE_UUID;


    /**
     * Expression for the domain {@link DBDefinitions#DOM_BOOKSHELF_NAME_CSV}.
     * <p>
     * The order of the returned names will be arbitrary.
     * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
     */
    public static final String EXP_BOOKSHELF_NAME_CSV;

    /**
     * Expression for the domain {@link DBDefinitions#DOM_PUBLISHER_NAME_CSV}.
     * <p>
     * The order of the returned names will be arbitrary.
     * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
     */
    public static final String EXP_PUBLISHER_NAME_CSV;

    /* ======================================================================================
     *  {@link BooklistNodeDao} domains.
     * ====================================================================================== */

    /**
     * {@link #TBL_BOOK_LIST_NODE_STATE} {@link Booklist}.
     * <p>
     * Expression from the original tables that represent the hierarchical key for the node.
     * Stored in each row and used to determine the expand/collapse results.
     */
    public static final Domain DOM_BL_NODE_KEY;
    /** {@link #TBL_BOOK_LIST_NODE_STATE} {@link Booklist}. */
    public static final Domain DOM_BL_NODE_GROUP;
    /** {@link #TBL_BOOK_LIST_NODE_STATE} {@link Booklist}. */
    public static final Domain DOM_BL_NODE_LEVEL;

    /** {@link #TBL_BOOK_LIST_NODE_STATE} {@link Booklist}. Should always be visible! */
    public static final Domain DOM_BL_NODE_EXPANDED;
    /** {@link #TBL_BOOK_LIST_NODE_STATE} {@link Booklist}. */
    public static final Domain DOM_BL_NODE_VISIBLE;

    /* ======================================================================================
     *  {@link TBL_FTS_BOOKS}.
     * ====================================================================================== */

    /**
     * reminder: no need for a type nor constraints.
     * <a href="https://sqlite.org/fts3.html">SqLite FTS3</a>
     */
    public static final TableDefinition TBL_FTS_BOOKS;

    public static final String ON_DELETE_CASCADE_ON_UPDATE_CASCADE =
            "ON DELETE CASCADE ON UPDATE CASCADE";

    /**
     * {@link #TBL_FTS_BOOKS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    static final Domain DOM_FTS_AUTHOR_NAME;
    static final Domain DOM_FTS_TOC_ENTRY_TITLE;
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";

    static {
        /* ======================================================================================
         *  Table definitions
         * ====================================================================================== */

        // never change the "authors" "a" alias. It's hardcoded elsewhere.
        TBL_AUTHORS = new TableDefinition("authors", "a");
        // never change the "books" "b" alias. It's hardcoded elsewhere.
        TBL_BOOKS = new TableDefinition("books", "b");
        TBL_DELETED_BOOKS = new TableDefinition("deleted_books", "delb");
        // never change the "series" "s" alias. It's hardcoded elsewhere.
        TBL_SERIES = new TableDefinition("series", "s");
        // never change the "publishers" "p" alias. It's hardcoded elsewhere.
        TBL_PUBLISHERS = new TableDefinition("publishers", "p");

        TBL_BOOKSHELF = new TableDefinition("bookshelf", "bsh");
        TBL_BOOKSHELF_FILTERS = new TableDefinition("bookshelf_filters", "bshf");

        TBL_TOC_ENTRIES = new TableDefinition("anthology", "an");

        TBL_PSEUDONYM_AUTHOR = new TableDefinition("pseudonym_author", "ap");

        TBL_BOOK_BOOKSHELF = new TableDefinition("book_bookshelf", "bbsh");
        TBL_BOOK_AUTHOR = new TableDefinition("book_author", "ba");
        TBL_BOOK_SERIES = new TableDefinition("book_series", "bs");
        TBL_BOOK_PUBLISHER = new TableDefinition("book_publisher", "bp");
        TBL_BOOK_LOANEE = new TableDefinition("loan", "l");
        TBL_BOOK_TOC_ENTRIES = new TableDefinition("book_anthology", "bat");

        TBL_CALIBRE_LIBRARIES = new TableDefinition("calibre_lib", "clb_l");
        TBL_CALIBRE_VIRTUAL_LIBRARIES = new TableDefinition("calibre_vlib", "clb_vl");
        TBL_CALIBRE_CUSTOM_FIELDS = new TableDefinition("calibre_custom_fields", "clb_cf");
        TBL_CALIBRE_BOOKS = new TableDefinition("calibre_books", "clb_b");

        TBL_BOOKLIST_STYLES = new TableDefinition("book_list_styles", "bls");

        TBL_STRIPINFO_COLLECTION = new TableDefinition("stripinfo_collection", "si_c");

        /* ======================================================================================
         *  Primary and Foreign Key definitions
         * ====================================================================================== */

        DOM_PK_ID = new Domain.Builder(DBKey.PK_ID, SqLiteDataType.Integer)
                .primaryKey()
                .build();

        DOM_FK_AUTHOR =
                new Domain.Builder(DBKey.FK_AUTHOR, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_AUTHORS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_BOOKSHELF =
                new Domain.Builder(DBKey.FK_BOOKSHELF, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_BOOKSHELF, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_BOOK =
                new Domain.Builder(DBKey.FK_BOOK, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_BOOKS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_SERIES =
                new Domain.Builder(DBKey.FK_SERIES, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_SERIES, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_PUBLISHER =
                new Domain.Builder(DBKey.FK_PUBLISHER, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_PUBLISHERS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_TOC_ENTRY =
                new Domain.Builder(DBKey.FK_TOC_ENTRY, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_TOC_ENTRIES, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_CALIBRE_LIBRARY =
                new Domain.Builder(DBKey.FK_CALIBRE_LIBRARY, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_CALIBRE_LIBRARIES, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_STYLE =
                new Domain.Builder(DBKey.FK_STYLE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(BuiltinStyle.HARD_DEFAULT_ID)
                        .references(TBL_BOOKLIST_STYLES, "ON DELETE SET DEFAULT ON UPDATE CASCADE")
                        .build();

        /* ======================================================================================
         *  Multi table domains
         * ====================================================================================== */

        DOM_TITLE =
                new Domain.Builder(DBKey.TITLE, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_TITLE_OB =
                new Domain.Builder(DBKey.TITLE_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_TITLE_ORIGINAL_LANG =
                new Domain.Builder(DBKey.TITLE_ORIGINAL_LANG, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_DATE_FIRST_PUBLICATION =
                new Domain.Builder(DBKey.FIRST_PUBLICATION__DATE, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_LAST_UPDATED__UTC =
                new Domain.Builder(DBKey.DATE_LAST_UPDATED__UTC, SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultCurrentTimeStamp()
                        .build();

        /* ======================================================================================
         *  Bookshelf domains
         * ====================================================================================== */

        DOM_BOOKSHELF_NAME =
                new Domain.Builder(DBKey.BOOKSHELF_NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        // Virtual, display only, unsorted
        DOM_BOOKSHELF_NAME_CSV =
                new Domain.Builder(DBKey.BOOKSHELF_NAME_CSV, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_BOOKSHELF_BL_TOP_POS =
                new Domain.Builder(DBKey.BOOKSHELF_BL_TOP_POS, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(RecyclerView.NO_POSITION)
                        .build();

        DOM_BOOKSHELF_BL_TOP_OFFSET =
                new Domain.Builder(DBKey.BOOKSHELF_BL_TOP_OFFSET, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_BOOKSHELF_FILTER_NAME =
                new Domain.Builder(DBKey.FILTER_DBKEY, SqLiteDataType.Text)
                        .notNull()
                        .build();
        DOM_BOOKSHELF_FILTER_VALUE =
                new Domain.Builder(DBKey.FILTER_VALUE, SqLiteDataType.Text)
                        .build();

        /* ======================================================================================
         *  Author domains
         * ====================================================================================== */

        DOM_AUTHOR_FAMILY_NAME =
                new Domain.Builder(DBKey.AUTHOR_FAMILY_NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_AUTHOR_FAMILY_NAME_OB =
                new Domain.Builder(DBKey.AUTHOR_FAMILY_NAME_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES =
                new Domain.Builder(DBKey.AUTHOR_GIVEN_NAMES, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES_OB =
                new Domain.Builder(DBKey.AUTHOR_GIVEN_NAMES_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_IS_COMPLETE =
                new Domain.Builder(DBKey.AUTHOR_IS_COMPLETE, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_AUTHOR_FORMATTED_FAMILY_FIRST =
                new Domain.Builder(DBKey.AUTHOR_FORMATTED, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_AUTHOR_PSEUDONYM =
                new Domain.Builder(DBKey.AUTHOR_PSEUDONYM, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_AUTHORS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();

        DOM_AUTHOR_REAL_AUTHOR =
                new Domain.Builder(DBKey.AUTHOR_REAL_AUTHOR, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_AUTHORS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();

        /* ======================================================================================
         *  Series domains
         * ====================================================================================== */

        DOM_SERIES_TITLE =
                new Domain.Builder(DBKey.SERIES_TITLE, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_SERIES_TITLE_OB =
                new Domain.Builder(DBKey.SERIES_TITLE_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_SERIES_IS_COMPLETE =
                new Domain.Builder(DBKey.SERIES_IS_COMPLETE, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        /* ======================================================================================
         *  Publisher domains
         * ====================================================================================== */
        DOM_PUBLISHER_NAME =
                new Domain.Builder(DBKey.PUBLISHER_NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_PUBLISHER_NAME_OB =
                new Domain.Builder(DBKey.PUBLISHER_NAME_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_PUBLISHER_NAME_CSV =
                new Domain.Builder(DBKey.PUBLISHER_NAME_CSV, SqLiteDataType.Text)
                        .notNull()
                        .build();
        /* ======================================================================================
         *  Book domains
         * ====================================================================================== */

        DOM_BOOK_ISBN =
                new Domain.Builder(DBKey.BOOK_ISBN, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_DATE_PUBLISHED =
                new Domain.Builder(DBKey.BOOK_PUBLICATION__DATE, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PRINT_RUN =
                new Domain.Builder(DBKey.PRINT_RUN, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PRICE_LISTED =
                new Domain.Builder(DBKey.PRICE_LISTED, SqLiteDataType.Real)
                        .notNull()
                        .withDefault(0d)
                        .build();

        DOM_BOOK_PRICE_LISTED_CURRENCY =
                new Domain.Builder(DBKey.PRICE_LISTED_CURRENCY, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PAGES =
                new Domain.Builder(DBKey.PAGE_COUNT, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_FORMAT =
                new Domain.Builder(DBKey.FORMAT, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_COLOR =
                new Domain.Builder(DBKey.COLOR, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_LANGUAGE =
                new Domain.Builder(DBKey.LANGUAGE, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_GENRE =
                new Domain.Builder(DBKey.GENRE, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_DESCRIPTION =
                new Domain.Builder(DBKey.DESCRIPTION, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_CONTENT_TYPE =
                new Domain.Builder(DBKey.BOOK_CONTENT_TYPE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Book.ContentType.Book.getId())
                        .build();

        /* ======================================================================================
         *  Book personal data domains
         * ====================================================================================== */

        DOM_BOOK_UUID =
                new Domain.Builder(DBKey.BOOK_UUID, SqLiteDataType.Text)
                        .notNull()
                        .withDefault("(lower(hex(randomblob(16))))")
                        .build();

        DOM_BOOK_EDITION =
                new Domain.Builder(DBKey.EDITION__BITMASK, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Book.Edition.UNKNOWN)
                        .build();

        DOM_BOOK_DATE_ACQUIRED =
                new Domain.Builder(DBKey.DATE_ACQUIRED, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PRICE_PAID =
                new Domain.Builder(DBKey.PRICE_PAID, SqLiteDataType.Real)
                        .notNull()
                        .withDefault(0d)
                        .build();

        DOM_BOOK_PRICE_PAID_CURRENCY =
                new Domain.Builder(DBKey.PRICE_PAID_CURRENCY, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_ADDED__UTC =
                new Domain.Builder(DBKey.DATE_ADDED__UTC, SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultCurrentTimeStamp()
                        .build();

        DOM_BOOK_LOCATION =
                new Domain.Builder(DBKey.LOCATION, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_READ =
                new Domain.Builder(DBKey.READ__BOOL, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();
        DOM_BOOK_READ_PROGRESS =
                new Domain.Builder(DBKey.READ_PROGRESS, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();
        DOM_BOOK_DATE_READ_START =
                new Domain.Builder(DBKey.READ_START__DATE, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();
        DOM_BOOK_DATE_READ_END =
                new Domain.Builder(DBKey.READ_END__DATE, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();
        DOM_BOOK_SIGNED =
                new Domain.Builder(DBKey.SIGNED__BOOL, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();
        DOM_BOOK_RATING =
                new Domain.Builder(DBKey.RATING, SqLiteDataType.Real)
                        .notNull()
                        .withDefault(0)
                        .build();
        DOM_BOOK_PRIVATE_NOTES =
                new Domain.Builder(DBKey.PERSONAL_NOTES, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_CONDITION =
                new Domain.Builder(DBKey.BOOK_CONDITION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(0)
                        .build();
        DOM_BOOK_CONDITION_DUST_COVER =
                new Domain.Builder(DBKey.BOOK_CONDITION_COVER, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_AUTO_UPDATE =
                new Domain.Builder(DBKey.AUTO_UPDATE, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(true)
                        .build();

        /* ======================================================================================
         *  Book external website id domains
         * ====================================================================================== */
        //NEWTHINGS: adding a new search engine: optional: add external id domain
        DOM_ESID_GOODREADS_BOOK =
                new Domain.Builder(DBKey.SID_GOODREADS_BOOK, SqLiteDataType.Integer)
                        .build();

        DOM_ESID_ISFDB =
                new Domain.Builder(DBKey.SID_ISFDB, SqLiteDataType.Integer)
                        .build();

        DOM_ESID_LIBRARY_THING =
                new Domain.Builder(DBKey.SID_LIBRARY_THING, SqLiteDataType.Integer)
                        .build();

        DOM_ESID_OPEN_LIBRARY =
                new Domain.Builder(DBKey.SID_OPEN_LIBRARY, SqLiteDataType.Text)
                        .build();

        DOM_ESID_STRIP_INFO_BE =
                new Domain.Builder(DBKey.SID_STRIP_INFO, SqLiteDataType.Integer)
                        .build();

        DOM_ESID_LAST_DODO_NL =
                new Domain.Builder(DBKey.SID_LAST_DODO_NL, SqLiteDataType.Integer)
                        .build();

        DOM_ESID_BEDETHEQUE =
                new Domain.Builder(DBKey.SID_BEDETHEQUE, SqLiteDataType.Integer)
                        .build();

        //NEWTHINGS: adding a new search engine: optional: add specific/extra domains.

        /* ======================================================================================
         *  StripInfo.be synchronization domains
         * ====================================================================================== */
        DOM_STRIP_INFO_BE_COLLECTION_ID =
                new Domain.Builder(DBKey.STRIP_INFO_COLL_ID, SqLiteDataType.Integer)
                        .build();

        DOM_STRIP_INFO_BE_OWNED =
                new Domain.Builder(DBKey.STRIP_INFO_OWNED, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_BE_DIGITAL =
                new Domain.Builder(DBKey.STRIP_INFO_DIGITAL, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_BE_WANTED =
                new Domain.Builder(DBKey.STRIP_INFO_WANTED, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_BE_AMOUNT =
                new Domain.Builder(DBKey.STRIP_INFO_AMOUNT, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_STRIP_INFO_BE_LAST_SYNC__UTC =
                new Domain.Builder(DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC,
                                   SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        /* ======================================================================================
         *  Calibre bridge table domains
         * ====================================================================================== */
        DOM_CALIBRE_BOOK_UUID =
                new Domain.Builder(DBKey.CALIBRE_BOOK_UUID, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_BOOK_ID =
                new Domain.Builder(DBKey.CALIBRE_BOOK_ID, SqLiteDataType.Integer)
                        .build();

        DOM_CALIBRE_BOOK_MAIN_FORMAT =
                new Domain.Builder(DBKey.CALIBRE_BOOK_MAIN_FORMAT, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_NAME =
                new Domain.Builder(DBKey.CALIBRE_CUSTOM_FIELD_NAME, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_TYPE =
                new Domain.Builder(DBKey.CALIBRE_CUSTOM_FIELD_TYPE, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_MAPPING =
                new Domain.Builder(DBKey.CALIBRE_CUSTOM_FIELD_MAPPING, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC,
                                   SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_CALIBRE_LIBRARY_STRING_ID =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_STRING_ID, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        // can be empty when our Calibre extension is not installed
        DOM_CALIBRE_LIBRARY_UUID =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_UUID, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_CALIBRE_LIBRARY_NAME =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_NAME, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        // not sure if we should allow empty?
        DOM_CALIBRE_VIRT_LIB_EXPR =
                new Domain.Builder(DBKey.CALIBRE_VIRT_LIB_EXPR, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        /* ======================================================================================
         *  Loanee domains
         * ====================================================================================== */

        DOM_LOANEE =
                new Domain.Builder(DBKey.LOANEE_NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        /* ======================================================================================
         *  Link table domains
         * ====================================================================================== */

        DOM_BOOK_AUTHOR_TYPE_BITMASK =
                new Domain.Builder(DBKey.AUTHOR_TYPE__BITMASK, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Author.TYPE_UNKNOWN)
                        .build();

        DOM_BOOK_AUTHOR_POSITION =
                new Domain.Builder(DBKey.BOOK_AUTHOR_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BOOK_SERIES_POSITION =
                new Domain.Builder(DBKey.BOOK_SERIES_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BOOK_NUM_IN_SERIES =
                new Domain.Builder(DBKey.SERIES_BOOK_NUMBER, SqLiteDataType.Text)
                        .localized()
                        .build();

        DOM_BOOK_PUBLISHER_POSITION =
                new Domain.Builder(DBKey.BOOK_PUBLISHER_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BOOK_TOC_ENTRY_POSITION =
                new Domain.Builder(DBKey.BOOK_TOC_ENTRY_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  Style domains
         * ====================================================================================== */

        DOM_STYLE_UUID =
                new Domain.Builder(DBKey.STYLE_UUID, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_STYLE_TYPE =
                new Domain.Builder(DBKey.STYLE_TYPE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(StyleType.User.getId())
                        .build();

        DOM_STYLE_IS_PREFERRED =
                new Domain.Builder(DBKey.STYLE_IS_PREFERRED, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_MENU_POSITION =
                new Domain.Builder(DBKey.STYLE_MENU_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.MENU_POSITION_NOT_PREFERRED)
                        .build();


        DOM_STYLE_NAME =
                new Domain.Builder(DBKey.STYLE_NAME, SqLiteDataType.Text)
                        .localized()
                        .build();

        DOM_STYLE_GROUPS =
                new Domain.Builder(DBKey.STYLE_GROUPS, SqLiteDataType.Text)
                        .build();

        DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE =
                new Domain.Builder(DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE,
                                   SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Author.TYPE_UNKNOWN)
                        .build();

        DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();


        DOM_STYLE_LAYOUT =
                new Domain.Builder(DBKey.STYLE_LAYOUT, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.Layout.List.getId())
                        .build();

        DOM_STYLE_COVER_CLICK_ACTION =
                new Domain.Builder(DBKey.STYLE_COVER_CLICK_ACTION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.CoverClickAction.Zoom.getId())
                        .build();

        DOM_STYLE_COVER_LONG_CLICK_ACTION =
                new Domain.Builder(DBKey.STYLE_COVER_LONG_CLICK_ACTION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.CoverLongClickAction.Ignore.getId())
                        .build();

        DOM_STYLE_EXP_LEVEL =
                new Domain.Builder(DBKey.STYLE_EXP_LEVEL, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(1)
                        .build();

        DOM_STYLE_ROW_USES_PREF_HEIGHT =
                new Domain.Builder(DBKey.STYLE_ROW_USES_PREF_HEIGHT,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(true)
                        .build();

        DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME =
                new Domain.Builder(DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();
        DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME =
                new Domain.Builder(DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_TITLE_SHOW_REORDERED =
                new Domain.Builder(DBKey.STYLE_TITLE_SHOW_REORDERED,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_READ_STATUS_WITH_PROGRESS =
                new Domain.Builder(DBKey.STYLE_READ_STATUS_WITH_PROGRESS,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_TEXT_SCALE =
                new Domain.Builder(DBKey.STYLE_TEXT_SCALE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(TextScale.DEFAULT.getScale())
                        .build();
        DOM_STYLE_COVER_SCALE =
                new Domain.Builder(DBKey.STYLE_COVER_SCALE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(CoverScale.DEFAULT.getScale())
                        .build();

        DOM_STYLE_LIST_HEADER =
                new Domain.Builder(DBKey.STYLE_LIST_HEADER, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(BooklistHeader.BITMASK_ALL)
                        .build();

        DOM_STYLE_BOOK_DETAIL_FIELDS_VISIBILITY =
                new Domain.Builder(DBKey.STYLE_DETAILS_SHOW_FIELDS, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(FieldVisibility.getBitValue(
                                BookDetailsFieldVisibility.DEFAULT))
                        .build();

        DOM_STYLE_BOOK_LEVEL_FIELDS_VISIBILITY =
                new Domain.Builder(DBKey.STYLE_BOOK_LEVEL_FIELDS_VISIBILITY, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(FieldVisibility.getBitValue(
                                BookLevelFieldVisibility.DEFAULT))
                        .build();

        DOM_STYLE_BOOK_LEVEL_FIELDS_ORDER_BY =
                new Domain.Builder(DBKey.STYLE_BOOK_LEVEL_FIELDS_ORDER_BY, SqLiteDataType.Text)
                        .build();

        /* ======================================================================================
         *  app tables
         * ====================================================================================== */

        TBL_BOOKLIST_STYLES
                .addDomains(DOM_PK_ID,
                            DOM_STYLE_TYPE,
                            DOM_STYLE_IS_PREFERRED,
                            DOM_STYLE_MENU_POSITION,
                            DOM_STYLE_UUID,
                            DOM_STYLE_NAME,

                            DOM_STYLE_GROUPS,
                            DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
                            DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE,
                            DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
                            DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                            DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,

                            DOM_STYLE_LAYOUT,
                            DOM_STYLE_COVER_CLICK_ACTION,
                            DOM_STYLE_COVER_LONG_CLICK_ACTION,

                            DOM_STYLE_EXP_LEVEL,
                            DOM_STYLE_ROW_USES_PREF_HEIGHT,
                            DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME,
                            DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME,
                            DOM_STYLE_TITLE_SHOW_REORDERED,
                            DOM_STYLE_READ_STATUS_WITH_PROGRESS,
                            DOM_STYLE_TEXT_SCALE,
                            DOM_STYLE_COVER_SCALE,
                            DOM_STYLE_LIST_HEADER,
                            DOM_STYLE_BOOK_DETAIL_FIELDS_VISIBILITY,
                            DOM_STYLE_BOOK_LEVEL_FIELDS_VISIBILITY,
                            DOM_STYLE_BOOK_LEVEL_FIELDS_ORDER_BY)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.STYLE_UUID, true, DOM_STYLE_UUID)
                .addIndex(DBKey.STYLE_NAME, true, DOM_STYLE_NAME)
                .addIndex(DBKey.STYLE_MENU_POSITION, false, DOM_STYLE_MENU_POSITION);
        ALL_TABLES.put(TBL_BOOKLIST_STYLES.getName(), TBL_BOOKLIST_STYLES);

        /* ======================================================================================
         *  basic user data tables
         * ====================================================================================== */

        TBL_BOOKSHELF
                .addDomains(DOM_PK_ID,
                            DOM_FK_STYLE,
                            DOM_BOOKSHELF_NAME,
                            DOM_BOOKSHELF_BL_TOP_POS,
                            DOM_BOOKSHELF_BL_TOP_OFFSET)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKLIST_STYLES, DOM_FK_STYLE)
                .addIndex(DBKey.BOOKSHELF_NAME, true, DOM_BOOKSHELF_NAME);
        ALL_TABLES.put(TBL_BOOKSHELF.getName(), TBL_BOOKSHELF);

        TBL_BOOKSHELF_FILTERS
                .addDomains(DOM_FK_BOOKSHELF,
                            DOM_BOOKSHELF_FILTER_NAME,
                            DOM_BOOKSHELF_FILTER_VALUE)
                .setPrimaryKey(DOM_FK_BOOKSHELF, DOM_BOOKSHELF_FILTER_NAME)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOKSHELF_FILTERS.getName(), TBL_BOOKSHELF_FILTERS);


        TBL_AUTHORS
                .addDomains(DOM_PK_ID,
                            DOM_AUTHOR_FAMILY_NAME,
                            DOM_AUTHOR_FAMILY_NAME_OB,
                            DOM_AUTHOR_GIVEN_NAMES,
                            DOM_AUTHOR_GIVEN_NAMES_OB,
                            DOM_AUTHOR_IS_COMPLETE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.AUTHOR_FAMILY_NAME_OB, false, DOM_AUTHOR_FAMILY_NAME_OB)
                .addIndex(DBKey.AUTHOR_FAMILY_NAME, false, DOM_AUTHOR_FAMILY_NAME)
                .addIndex(DBKey.AUTHOR_GIVEN_NAMES_OB, false, DOM_AUTHOR_GIVEN_NAMES_OB)
                .addIndex(DBKey.AUTHOR_GIVEN_NAMES, false, DOM_AUTHOR_GIVEN_NAMES);
        ALL_TABLES.put(TBL_AUTHORS.getName(), TBL_AUTHORS);

        TBL_SERIES
                .addDomains(DOM_PK_ID,
                            DOM_SERIES_TITLE,
                            DOM_SERIES_TITLE_OB,
                            DOM_SERIES_IS_COMPLETE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("id", true, DOM_PK_ID)
                .addIndex(DBKey.SERIES_TITLE_OB, false, DOM_SERIES_TITLE_OB)
                .addIndex(DBKey.SERIES_TITLE, false, DOM_SERIES_TITLE);
        ALL_TABLES.put(TBL_SERIES.getName(), TBL_SERIES);

        TBL_PUBLISHERS
                .addDomains(DOM_PK_ID,
                            DOM_PUBLISHER_NAME,
                            DOM_PUBLISHER_NAME_OB)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("id", true, DOM_PK_ID)
                .addIndex(DBKey.PUBLISHER_NAME_OB, false, DOM_PUBLISHER_NAME_OB)
                .addIndex(DBKey.PUBLISHER_NAME, false, DOM_PUBLISHER_NAME);
        ALL_TABLES.put(TBL_PUBLISHERS.getName(), TBL_PUBLISHERS);

        TBL_BOOKS
                .addDomains(DOM_PK_ID,
                            // book data
                            DOM_TITLE,
                            DOM_TITLE_OB,
                            DOM_TITLE_ORIGINAL_LANG,
                            DOM_BOOK_ISBN,
                            DOM_BOOK_DATE_PUBLISHED,
                            DOM_DATE_FIRST_PUBLICATION,
                            DOM_BOOK_PRINT_RUN,

                            DOM_BOOK_PRICE_LISTED,
                            DOM_BOOK_PRICE_LISTED_CURRENCY,

                            DOM_BOOK_CONTENT_TYPE,
                            DOM_BOOK_FORMAT,
                            DOM_BOOK_COLOR,
                            DOM_BOOK_GENRE,
                            DOM_BOOK_LANGUAGE,
                            DOM_BOOK_PAGES,

                            DOM_BOOK_DESCRIPTION,

                            // personal data
                            DOM_BOOK_PRICE_PAID,
                            DOM_BOOK_PRICE_PAID_CURRENCY,
                            DOM_BOOK_DATE_ACQUIRED,

                            DOM_BOOK_READ,
                            DOM_BOOK_READ_PROGRESS,
                            DOM_BOOK_DATE_READ_START,
                            DOM_BOOK_DATE_READ_END,

                            DOM_BOOK_EDITION,
                            DOM_BOOK_SIGNED,
                            DOM_BOOK_RATING,
                            DOM_BOOK_LOCATION,
                            DOM_BOOK_PRIVATE_NOTES,
                            DOM_BOOK_CONDITION,
                            DOM_BOOK_CONDITION_DUST_COVER,
                            DOM_AUTO_UPDATE,

                            // external id/data
                            //NEWTHINGS: adding a new search engine: optional: add external id DOM
                            DOM_ESID_GOODREADS_BOOK,
                            DOM_ESID_ISFDB,
                            DOM_ESID_LIBRARY_THING,
                            DOM_ESID_OPEN_LIBRARY,
                            DOM_ESID_STRIP_INFO_BE,
                            DOM_ESID_LAST_DODO_NL,
                            DOM_ESID_BEDETHEQUE,
                            //NEWTHINGS: adding a new search engine:
                            // optional: add engine specific DOM

                            // internal data
                            DOM_BOOK_UUID,
                            DOM_ADDED__UTC,
                            DOM_LAST_UPDATED__UTC)

                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.TITLE_OB, false, DOM_TITLE_OB)
                .addIndex(DBKey.TITLE, false, DOM_TITLE)
                .addIndex(DBKey.BOOK_ISBN, false, DOM_BOOK_ISBN)
                .addIndex(DBKey.BOOK_UUID, true, DOM_BOOK_UUID)
                //NEWTHINGS: adding a new search engine: optional: add indexes as needed.
                // note that not all external id's warrant an index

                .addIndex(DBKey.SID_GOODREADS_BOOK, false, DOM_ESID_GOODREADS_BOOK)
                .addIndex(DBKey.SID_ISFDB, false, DOM_ESID_ISFDB)
                .addIndex(DBKey.SID_OPEN_LIBRARY, false, DOM_ESID_OPEN_LIBRARY)
                .addIndex(DBKey.SID_STRIP_INFO, false, DOM_ESID_STRIP_INFO_BE)
                .addIndex(DBKey.SID_LAST_DODO_NL, false, DOM_ESID_LAST_DODO_NL)
                .addIndex(DBKey.SID_BEDETHEQUE, false, DOM_ESID_BEDETHEQUE);
        ALL_TABLES.put(TBL_BOOKS.getName(), TBL_BOOKS);

        TBL_DELETED_BOOKS.addDomains(DOM_BOOK_UUID,
                                     DOM_ADDED__UTC)
                         .setPrimaryKey(DOM_BOOK_UUID);
        ALL_TABLES.put(TBL_DELETED_BOOKS.getName(), TBL_DELETED_BOOKS);

        TBL_TOC_ENTRIES
                .addDomains(DOM_PK_ID,
                            DOM_FK_AUTHOR,
                            DOM_TITLE,
                            DOM_TITLE_OB,
                            DOM_DATE_FIRST_PUBLICATION)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                .addIndex(DBKey.FK_AUTHOR, false, DOM_FK_AUTHOR)
                .addIndex(DBKey.TITLE_OB, false, DOM_TITLE_OB);
        ALL_TABLES.put(TBL_TOC_ENTRIES.getName(), TBL_TOC_ENTRIES);


        /* ======================================================================================
         *  link tables
         * ====================================================================================== */

        /*
         * Link a pseudonym with the real-author.
         * i.e. take an author from a book, query this table with
         * book-author-id == DOM_AUTHOR_PSEUDONYM-id
         * and retrieve the DOM_AUTHOR_REAL_AUTHOR-id as the real-author-id
         * <p>
         * <strong>Dev. note:</strong> instead of DOM_AUTHOR_REAL_AUTHOR we could have
         *  used FK_AUTHOR... but having a dedicated one saves us from special 'as'-ing
         *  and DomainExpression handling.
         */
        TBL_PSEUDONYM_AUTHOR
                .addDomains(DOM_AUTHOR_PSEUDONYM,
                            DOM_AUTHOR_REAL_AUTHOR)
                .setPrimaryKey(DOM_AUTHOR_PSEUDONYM)
                .addReference(TBL_AUTHORS, DOM_AUTHOR_PSEUDONYM)
                .addReference(TBL_AUTHORS, DOM_AUTHOR_REAL_AUTHOR)
                .addIndex(DBKey.AUTHOR_PSEUDONYM, true, DOM_AUTHOR_PSEUDONYM)
                .addIndex(DBKey.AUTHOR_REAL_AUTHOR, false, DOM_AUTHOR_REAL_AUTHOR);
        ALL_TABLES.put(TBL_PSEUDONYM_AUTHOR.getName(), TBL_PSEUDONYM_AUTHOR);


        TBL_BOOK_BOOKSHELF
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_BOOKSHELF)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_BOOKSHELF)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                .addIndex(DBKey.FK_BOOK, false, DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOKSHELF, false, DOM_FK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOK_BOOKSHELF.getName(), TBL_BOOK_BOOKSHELF);


        TBL_BOOK_AUTHOR
                .addDomains(DOM_FK_BOOK,
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
                .addIndex(DBKey.FK_AUTHOR, true,
                          DOM_FK_AUTHOR,
                          DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_FK_AUTHOR);
        ALL_TABLES.put(TBL_BOOK_AUTHOR.getName(), TBL_BOOK_AUTHOR);


        TBL_BOOK_SERIES
                .addDomains(DOM_FK_BOOK,
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
                .addIndex(DBKey.FK_SERIES, true,
                          DOM_FK_SERIES,
                          DOM_FK_BOOK,
                          DOM_BOOK_NUM_IN_SERIES)
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_FK_SERIES,
                          DOM_BOOK_NUM_IN_SERIES);
        ALL_TABLES.put(TBL_BOOK_SERIES.getName(), TBL_BOOK_SERIES);


        TBL_BOOK_PUBLISHER
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_PUBLISHER,
                            DOM_BOOK_PUBLISHER_POSITION)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_PUBLISHER, DOM_BOOK_PUBLISHER_POSITION)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_PUBLISHERS, DOM_FK_PUBLISHER)
                .addIndex(DBKey.FK_PUBLISHER, true,
                          DOM_FK_PUBLISHER,
                          DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_FK_PUBLISHER);
        ALL_TABLES.put(TBL_BOOK_PUBLISHER.getName(), TBL_BOOK_PUBLISHER);


        TBL_BOOK_TOC_ENTRIES
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_TOC_ENTRY,
                            DOM_BOOK_TOC_ENTRY_POSITION)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_TOC_ENTRY)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_TOC_ENTRIES, DOM_FK_TOC_ENTRY)
                .addIndex(DBKey.FK_TOC_ENTRY, false, DOM_FK_TOC_ENTRY)
                .addIndex(DBKey.FK_BOOK, false, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_TOC_ENTRIES.getName(), TBL_BOOK_TOC_ENTRIES);


        TBL_BOOK_LOANEE
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOK,
                            DOM_LOANEE)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOK, true, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_LOANEE.getName(), TBL_BOOK_LOANEE);


        TBL_CALIBRE_BOOKS
                .addDomains(DOM_FK_BOOK,
                            DOM_CALIBRE_BOOK_ID,
                            DOM_CALIBRE_BOOK_UUID,
                            DOM_CALIBRE_BOOK_MAIN_FORMAT,
                            DOM_FK_CALIBRE_LIBRARY)
                .setPrimaryKey(DOM_FK_BOOK)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_CALIBRE_LIBRARIES, DOM_FK_CALIBRE_LIBRARY)
                // false: leave it open to have multiple calibre books (i.e. different formats)
                .addIndex(DBKey.FK_BOOK, false, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_CALIBRE_BOOKS.getName(), TBL_CALIBRE_BOOKS);

        TBL_CALIBRE_LIBRARIES
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOKSHELF,
                            DOM_CALIBRE_LIBRARY_UUID,
                            DOM_CALIBRE_LIBRARY_STRING_ID,
                            DOM_CALIBRE_LIBRARY_NAME,
                            DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                .addIndex(DBKey.CALIBRE_LIBRARY_NAME, true,
                          DOM_CALIBRE_LIBRARY_STRING_ID,
                          DOM_CALIBRE_LIBRARY_NAME);
        ALL_TABLES.put(TBL_CALIBRE_LIBRARIES.getName(), TBL_CALIBRE_LIBRARIES);

        TBL_CALIBRE_VIRTUAL_LIBRARIES
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOKSHELF,
                            DOM_FK_CALIBRE_LIBRARY,
                            DOM_CALIBRE_LIBRARY_NAME,
                            DOM_CALIBRE_VIRT_LIB_EXPR)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                .addReference(TBL_CALIBRE_LIBRARIES, DOM_FK_CALIBRE_LIBRARY)
                .addIndex(DBKey.CALIBRE_LIBRARY_NAME, true,
                          DOM_FK_CALIBRE_LIBRARY,
                          DOM_CALIBRE_LIBRARY_NAME);
        ALL_TABLES.put(TBL_CALIBRE_VIRTUAL_LIBRARIES.getName(), TBL_CALIBRE_VIRTUAL_LIBRARIES);

        TBL_CALIBRE_CUSTOM_FIELDS
                .addDomains(DOM_PK_ID,
                            DOM_CALIBRE_CUSTOM_FIELD_NAME,
                            DOM_CALIBRE_CUSTOM_FIELD_TYPE,
                            DOM_CALIBRE_CUSTOM_FIELD_MAPPING)
                .setPrimaryKey(DOM_PK_ID);
        ALL_TABLES.put(TBL_CALIBRE_CUSTOM_FIELDS.getName(), TBL_CALIBRE_CUSTOM_FIELDS);


        TBL_STRIPINFO_COLLECTION
                .addDomains(DOM_FK_BOOK,
                            DOM_ESID_STRIP_INFO_BE,
                            DOM_STRIP_INFO_BE_COLLECTION_ID,
                            DOM_STRIP_INFO_BE_OWNED,
                            DOM_STRIP_INFO_BE_DIGITAL,
                            DOM_STRIP_INFO_BE_WANTED,
                            DOM_STRIP_INFO_BE_AMOUNT,
                            DOM_STRIP_INFO_BE_LAST_SYNC__UTC)
                .setPrimaryKey(DOM_FK_BOOK)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                // not unique: allow multiple local books to point to the same online book
                .addIndex(DBKey.SID_STRIP_INFO, false,
                          DOM_ESID_STRIP_INFO_BE);
        ALL_TABLES.put(TBL_STRIPINFO_COLLECTION.getName(),
                       TBL_STRIPINFO_COLLECTION);

    }

    static {

        TBL_BOOK_LIST_NODE_STATE = new TableDefinition("book_list_node_settings", "bl_ns");

        DOM_BL_NODE_KEY =
                new Domain.Builder(DBKey.BL_NODE_KEY, SqLiteDataType.Text)
                        .build();

        DOM_BL_NODE_GROUP =
                new Domain.Builder(DBKey.BL_NODE_GROUP, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BL_NODE_LEVEL =
                new Domain.Builder(DBKey.BL_NODE_LEVEL, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BL_NODE_VISIBLE =
                new Domain.Builder(DBKey.BL_NODE_VISIBLE, SqLiteDataType.Integer)
                        .withDefault(false)
                        .build();

        DOM_BL_NODE_EXPANDED =
                new Domain.Builder(DBKey.BL_NODE_EXPANDED, SqLiteDataType.Integer)
                        .withDefault(false)
                        .build();

        TBL_BOOK_LIST_NODE_STATE
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOKSHELF,
                            DOM_FK_STYLE,

                            DOM_BL_NODE_KEY,
                            DOM_BL_NODE_LEVEL,
                            DOM_BL_NODE_GROUP,
                            DOM_BL_NODE_EXPANDED,
                            DOM_BL_NODE_VISIBLE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("BOOKSHELF_STYLE", false,
                          DOM_FK_BOOKSHELF,
                          DOM_FK_STYLE);
        ALL_TABLES.put(TBL_BOOK_LIST_NODE_STATE.getName(),
                       TBL_BOOK_LIST_NODE_STATE);


        EXP_BOOKSHELF_NAME_CSV =
                "(SELECT GROUP_CONCAT(" + TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME) + ",', ')"
                + _FROM_ + TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF)
                + _WHERE_
                + TBL_BOOKS.dot(DBKey.PK_ID) + '=' + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK)
                + ')';

        EXP_PUBLISHER_NAME_CSV =
                "(SELECT GROUP_CONCAT(" + TBL_PUBLISHERS.dot(DBKey.PUBLISHER_NAME) + ",', ')"
                + _FROM_ + TBL_PUBLISHERS.startJoin(TBL_BOOK_PUBLISHER)
                + _WHERE_
                + TBL_BOOKS.dot(DBKey.PK_ID) + '=' + TBL_BOOK_PUBLISHER.dot(DBKey.FK_BOOK)
                + ')';
    }

    static {
        DOM_FTS_AUTHOR_NAME =
                new Domain.Builder(DBKey.FTS_AUTHOR_NAME, SqLiteDataType.Text)
                        .build();

        DOM_FTS_TOC_ENTRY_TITLE =
                new Domain.Builder(DBKey.FTS_TOC_ENTRY_TITLE, SqLiteDataType.Text)
                        .build();

        TBL_FTS_BOOKS = createFtsTableDefinition("books_fts");
    }

    private DBDefinitions() {
    }

    @NonNull
    public static TableDefinition createFtsTableDefinition(@NonNull final String name) {
        return new TableDefinition(name, name)
                .setType(TableDefinition.TableType.FTS)
                .addDomains(DOM_TITLE,
                            DOM_FTS_AUTHOR_NAME,
                            DOM_SERIES_TITLE,
                            DOM_PUBLISHER_NAME,

                            DOM_BOOK_DESCRIPTION,
                            DOM_BOOK_PRIVATE_NOTES,
                            DOM_BOOK_GENRE,
                            DOM_BOOK_LOCATION,
                            DOM_BOOK_ISBN,

                            DOM_FTS_TOC_ENTRY_TITLE);
    }
}
