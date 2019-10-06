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
 * IMPORTANT: do NOT auto-format! It will cause chaos in the order of definitions.
 * <p>
 * Domain definitions for {@link DAO} and {@link BooklistBuilder}.
 * <strong>Note:</strong> Fields 'name' attribute must be in LOWER CASE.
 * <p>
 * Static definitions of database objects.
 * This is a *mostly* complete representation of the application database.
 * <p>
 * TODO: Collated indexes need to be done manually. See {@link DBHelper} #createIndices
 */
@SuppressWarnings("WeakerAccess")
public final class DBDefinitions {

    /**
     * A collection of all tables used to be able to rebuild indexes etc...,
     * added in order so interdependency's work out
     */
    static final Map<String, TableDefinition> ALL_TABLES = new LinkedHashMap<>();

    /*
     * Basic table definitions with type & alias set.
     * All these should be added to {@link #ALL_TABLES}.
     *
     * Split from where we add domains etc to avoid forward code references.
     */
    /** Basic table definition. */
    public static final TableDefinition TBL_AUTHORS;
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKS;
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF;
    /** Basic table definition. */
    public static final TableDefinition TBL_SERIES;
    /** Basic table definition. */
    public static final TableDefinition TBL_TOC_ENTRIES;

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

    static {
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
    }

    /** User defined styles. */
    static final TableDefinition TBL_BOOKLIST_STYLES;
    static {
        TBL_BOOKLIST_STYLES =
                new TableDefinition("book_list_styles").setAlias("bls");
    }

    /** primary key. */
    public static final DomainDefinition DOM_PK_ID;

    /** foreign key. */
    public static final DomainDefinition DOM_FK_AUTHOR;
    /** foreign key. */
    public static final DomainDefinition DOM_FK_BOOKSHELF;
    /** foreign key. */
    public static final DomainDefinition DOM_FK_BOOK;
    /** foreign key. */
    public static final DomainDefinition DOM_FK_SERIES;
    /** foreign key. */
    public static final DomainDefinition DOM_FK_TOC_ENTRY;
    /**
     * foreign key.
     * When a style is deleted, this key will be (re)set to
     * {@link BooklistStyle#DEFAULT_STYLE_ID}
     */
    public static final DomainDefinition DOM_FK_STYLE_ID;

    public static final String KEY_PK_ID = "_id";
    public static final String KEY_FK_BOOK = "book";
    public static final String KEY_FK_AUTHOR = "author";
    public static final String KEY_FK_SERIES = "series_id";
    public static final String KEY_FK_BOOKSHELF = "bookshelf";
    public static final String KEY_FK_TOC_ENTRY = "anthology";
    public static final String KEY_FK_STYLE = "style";

    static {
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
    }

    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final DomainDefinition DOM_TITLE;
    /**
     * 'Order By' for the title. Lowercase, and stripped of spaces etc...
     * {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}.
     */
    public static final DomainDefinition DOM_TITLE_OB;
    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final DomainDefinition DOM_DATE_FIRST_PUBLICATION;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_DATE_LAST_UPDATED;

    public static final String KEY_TITLE = "title";
    public static final String KEY_DATE_FIRST_PUBLICATION = "first_publication";
    public static final String KEY_DATE_LAST_UPDATED = "last_update_date";

    /** Suffix added to a column name to create a specific 'order by' copy of that column. */
    private static final String COLUMN_SUFFIX_ORDER_BY = "_ob";

    static {
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
    }

    /** {@link #TBL_AUTHORS}. */
    static final DomainDefinition DOM_AUTHOR_FAMILY_NAME;
    static final DomainDefinition DOM_AUTHOR_FAMILY_NAME_OB;
    /** {@link #TBL_AUTHORS}. */
    static final DomainDefinition DOM_AUTHOR_GIVEN_NAMES;
    static final DomainDefinition DOM_AUTHOR_GIVEN_NAMES_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final DomainDefinition DOM_AUTHOR_IS_COMPLETE;

    /** "FamilyName, GivenName". */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED;
    /** "GivenName FamilyName". */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED_GIVEN_FIRST;

    public static final String KEY_AUTHOR_FAMILY_NAME = "family_name";
    public static final String KEY_AUTHOR_GIVEN_NAMES = "given_names";
    public static final String KEY_AUTHOR_IS_COMPLETE = "author_complete";
    public static final String KEY_AUTHOR_FORMATTED = "author_formatted";
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";

    static {
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
    }

    /** {@link #TBL_SERIES}. */
    public static final DomainDefinition DOM_SERIES_TITLE;
    public static final DomainDefinition DOM_SERIES_TITLE_OB;
    /** {@link #TBL_SERIES}. */
    public static final DomainDefinition DOM_SERIES_IS_COMPLETE;
    /** {@link #TBL_SERIES}. */
    public static final DomainDefinition DOM_SERIES_FORMATTED;

    public static final String KEY_SERIES_TITLE = "series_name";
    public static final String KEY_SERIES_FORMATTED = "series_formatted";
    public static final String KEY_SERIES_IS_COMPLETE = "series_complete";

    static {
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
    }

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_ISBN;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PUBLISHER;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_DATE_PUBLISHED;
    /**
     *  {@link #TBL_BOOKS}.
     *  A String typed column due to supporting pre-decimal UK "Shilling/Pence" as used on
     *  the ISFDB web site.
     *  Hum... ISBN first intro 1970, Decimal day 1971... so very little overlap.
     */
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

    public static final String KEY_ISBN = "isbn";
    public static final String KEY_PUBLISHER = "publisher";
    public static final String KEY_DATE_PUBLISHED = "date_published";
    public static final String KEY_PRICE_LISTED = "list_price";
    public static final String KEY_PRICE_LISTED_CURRENCY = "list_price_currency";
    public static final String KEY_PAGES = "pages";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_DESCRIPTION = "description";

    static {
        DOM_BOOK_ISBN =
                new DomainDefinition(KEY_ISBN, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PUBLISHER =
                new DomainDefinition(KEY_PUBLISHER, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_DATE_PUBLISHED =
                new DomainDefinition(KEY_DATE_PUBLISHED, ColumnInfo.TYPE_DATE, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PRICE_LISTED =
                new DomainDefinition(KEY_PRICE_LISTED, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
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
        DOM_BOOK_DESCRIPTION =
                new DomainDefinition(KEY_DESCRIPTION, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
    }

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_UUID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_EDITION_BITMASK;
    /** {@link #TBL_BOOKS}. See {@link Book#TOC_SINGLE_AUTHOR_SINGLE_WORK}. */
    public static final DomainDefinition DOM_BOOK_TOC_BITMASK;
    /** {@link #TBL_BOOKS}. */
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

    public static final String KEY_BOOK_UUID = "book_uuid";

    public static final String KEY_EDITION_BITMASK = "edition_bm";
    public static final String KEY_TOC_BITMASK = "anthology";
    public static final String KEY_PRICE_PAID = "price_paid";
    public static final String KEY_PRICE_PAID_CURRENCY = "price_paid_currency";
    public static final String KEY_GENRE = "genre";
    public static final String KEY_LOCATION = "location";

    public static final String KEY_READ = "read";
    public static final String KEY_READ_START = "read_start";
    public static final String KEY_READ_END = "read_end";
    public static final String KEY_SIGNED = "signed";
    public static final String KEY_RATING = "rating";
    public static final String KEY_PRIVATE_NOTES = "notes";
    public static final String KEY_OWNED = "owned";
    public static final String KEY_DATE_ACQUIRED = "date_acquired";
    public static final String KEY_DATE_ADDED = "date_added";

    static {
        DOM_BOOK_UUID =
                new DomainDefinition(KEY_BOOK_UUID, ColumnInfo.TYPE_TEXT, true)
                        .setDefault("(lower(hex(randomblob(16))))");

        DOM_BOOK_EDITION_BITMASK =
                new DomainDefinition(KEY_EDITION_BITMASK, ColumnInfo.TYPE_INTEGER, true)
                        .setDefault(0);
        DOM_BOOK_TOC_BITMASK =
                new DomainDefinition(KEY_TOC_BITMASK, ColumnInfo.TYPE_INTEGER, true)
                        .setDefault(Book.TOC_SINGLE_AUTHOR_SINGLE_WORK);

        DOM_BOOK_IS_OWNED =
                new DomainDefinition(KEY_OWNED, ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);
        DOM_BOOK_DATE_ACQUIRED =
                new DomainDefinition(KEY_DATE_ACQUIRED, ColumnInfo.TYPE_DATE, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PRICE_PAID =
                new DomainDefinition(KEY_PRICE_PAID, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_PRICE_PAID_CURRENCY =
                new DomainDefinition(KEY_PRICE_PAID_CURRENCY, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
        DOM_BOOK_DATE_ADDED =
                new DomainDefinition(KEY_DATE_ADDED, ColumnInfo.TYPE_DATETIME, true)
                        .setDefault("current_timestamp");

        DOM_BOOK_GENRE =
                new DomainDefinition(KEY_GENRE, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();
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
    }

    /**
     * We don't store the ASIN (yet) but plumbing has started. ENHANCE: use/store the ASIN
     */
    public static final DomainDefinition DOM_ASIN;

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_ISFDB_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_OPEN_LIBRARY_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_LIBRARY_THING_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_GOODREADS_ID;
    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_GOODREADS_LAST_SYNC_DATE;

    public static final String KEY_ASIN = "asin";
    public static final String KEY_ISFDB_ID = "isfdb_book_id";
    public static final String KEY_OPEN_LIBRARY_ID = "ol_book_id";
    public static final String KEY_LIBRARY_THING_ID = "lt_book_id";
    /** Book ID, not 'work' ID. */
    public static final String KEY_GOODREADS_BOOK_ID = "goodreads_book_id";
    public static final String KEY_GOODREADS_LAST_SYNC_DATE = "last_goodreads_sync_date";

    // ENHANCE: the search engines already uses these where possible, but not stored yet.
    // Long
    public static final String KEY_WORLDCAT_ID = "worldcat_oclc_book_id";
    public static final String KEY_LCCN_ID = "lccn_book_id";

    static {
        DOM_ASIN =
                new DomainDefinition(KEY_ASIN, ColumnInfo.TYPE_TEXT);
        DOM_BOOK_ISFDB_ID =
                new DomainDefinition(KEY_ISFDB_ID, ColumnInfo.TYPE_INTEGER);
        DOM_BOOK_OPEN_LIBRARY_ID =
                new DomainDefinition(KEY_OPEN_LIBRARY_ID, ColumnInfo.TYPE_TEXT);
        DOM_BOOK_LIBRARY_THING_ID =
                new DomainDefinition(KEY_LIBRARY_THING_ID, ColumnInfo.TYPE_INTEGER);
        DOM_BOOK_GOODREADS_ID =
                new DomainDefinition(KEY_GOODREADS_BOOK_ID, ColumnInfo.TYPE_INTEGER);
        DOM_BOOK_GOODREADS_LAST_SYNC_DATE =
                new DomainDefinition(KEY_GOODREADS_LAST_SYNC_DATE, ColumnInfo.TYPE_DATE)
                        .setDefault("'0000-00-00'");
    }

    /** {@link #TBL_BOOKSHELF}. */
    public static final DomainDefinition DOM_BOOKSHELF;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ",', ')". */
    public static final DomainDefinition DOM_BOOKSHELF_CSV;

    public static final String KEY_BOOKSHELF = "bookshelf";
    public static final String KEY_BOOKSHELF_CSV = "bookshelves_csv";

    static {
        DOM_BOOKSHELF =
                new DomainDefinition(KEY_BOOKSHELF, ColumnInfo.TYPE_TEXT, true);
        DOM_BOOKSHELF_CSV =
                new DomainDefinition(KEY_BOOKSHELF_CSV, ColumnInfo.TYPE_TEXT, true);
    }

    /** {@link #TBL_BOOK_SERIES}. */
    public static final DomainDefinition DOM_BOOK_NUM_IN_SERIES;

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final DomainDefinition DOM_LOANEE;
    /**
     * {@link #TBL_BOOK_LOANEE}.
     * Virtual: returns 0 for 'available' or 1 for 'lend out'
     */
    public static final DomainDefinition DOM_LOANEE_AS_BOOLEAN;

    /** Virtual. The type of a TOC entry. See {@link TocEntry.Type} */
    static final DomainDefinition DOM_TOC_TYPE;

    public static final String KEY_BOOK_NUM_IN_SERIES = "series_num";
    public static final String KEY_LOANEE = "loaned_to";
    public static final String KEY_LOANEE_AS_BOOLEAN = "loaned_flag";

    public static final String KEY_TOC_TYPE = "type";

    static {
        DOM_BOOK_NUM_IN_SERIES =
                new DomainDefinition(KEY_BOOK_NUM_IN_SERIES, ColumnInfo.TYPE_TEXT);
        DOM_LOANEE =
                new DomainDefinition(KEY_LOANEE, ColumnInfo.TYPE_TEXT, true);
        DOM_LOANEE_AS_BOOLEAN =
                new DomainDefinition(KEY_LOANEE_AS_BOOLEAN, ColumnInfo.TYPE_INTEGER, true);
        DOM_TOC_TYPE =
                new DomainDefinition(KEY_TOC_TYPE, ColumnInfo.TYPE_TEXT);
    }

    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final DomainDefinition DOM_BOOK_AUTHOR_TYPE_BITMASK;
    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final DomainDefinition DOM_BOOK_AUTHOR_POSITION;

    public static final String KEY_AUTHOR_TYPE = "author_type";
    public static final String KEY_AUTHOR_POSITION = "author_position";

    static {
        DOM_BOOK_AUTHOR_TYPE_BITMASK =
                new DomainDefinition(KEY_AUTHOR_TYPE, ColumnInfo.TYPE_INTEGER, true)
                        .setDefault(0);
        DOM_BOOK_AUTHOR_POSITION =
                new DomainDefinition(KEY_AUTHOR_POSITION, ColumnInfo.TYPE_INTEGER, true);
    }


    /**
     * {@link #TBL_BOOK_SERIES}.
     * The Series position is the order the Series show up in a book. Particularly important
     * for "primary series" and in lists where 'all' Series are shown.
     */
    public static final DomainDefinition DOM_BOOK_SERIES_POSITION;

    /** {@link #TBL_BOOK_TOC_ENTRIES}. */
    static final DomainDefinition DOM_BOOK_TOC_ENTRY_POSITION;

    static {
        DOM_BOOK_SERIES_POSITION =
                new DomainDefinition("series_position", ColumnInfo.TYPE_INTEGER, true);

        DOM_BOOK_TOC_ENTRY_POSITION =
                new DomainDefinition("toc_entry_position", ColumnInfo.TYPE_INTEGER, true);
    }

    /** {@link #TBL_BOOKLIST_STYLES} java.util.UUID value stored as a string. */
    public static final DomainDefinition DOM_UUID;
    static final DomainDefinition DOM_STYLE_IS_BUILTIN;

    public static final String KEY_UUID = "uuid";

    static {
        DOM_UUID =
                new DomainDefinition(KEY_UUID, ColumnInfo.TYPE_TEXT, true)
                        .setDefaultEmptyString();

        DOM_STYLE_IS_BUILTIN =
                new DomainDefinition("builtin", ColumnInfo.TYPE_BOOLEAN, true)
                        .setDefault(0);
    }

    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_AUTHOR_SORT;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_SERIES_SORT;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_READ_STATUS;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_TITLE_LETTER;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_ADDED_DAY;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_ADDED_MONTH;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_ADDED_YEAR;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_UPDATE_DAY;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_UPDATE_MONTH;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_LAST_UPDATE_YEAR;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_READ_DAY;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_READ_MONTH;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_READ_YEAR;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_ACQUIRED_DAY;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_ACQUIRED_MONTH;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_ACQUIRED_YEAR;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_FIRST_PUBLICATION_MONTH;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_FIRST_PUBLICATION_YEAR;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_PUBLISHED_MONTH;
    /** sorting and grouping in {@link BooklistGroup}. */
    public static final DomainDefinition DOM_DATE_PUBLISHED_YEAR;

    static {
        DOM_AUTHOR_SORT =
                new DomainDefinition("author_sort", ColumnInfo.TYPE_TEXT, true);
        DOM_SERIES_SORT =
                new DomainDefinition("series_sort", ColumnInfo.TYPE_TEXT, true);
        DOM_READ_STATUS =
                new DomainDefinition("read_status", ColumnInfo.TYPE_TEXT, true);
        DOM_TITLE_LETTER =
                new DomainDefinition("title_letter", ColumnInfo.TYPE_TEXT);

        DOM_DATE_ADDED_DAY =
                new DomainDefinition("added_day", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_ADDED_MONTH =
                new DomainDefinition("added_month", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_ADDED_YEAR =
                new DomainDefinition("added_year", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_UPDATE_DAY =
                new DomainDefinition("upd_day", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_UPDATE_MONTH =
                new DomainDefinition("upd_month", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_LAST_UPDATE_YEAR =
                new DomainDefinition("upd_year", ColumnInfo.TYPE_INTEGER);

        DOM_DATE_READ_DAY =
                new DomainDefinition("read_day", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_READ_MONTH =
                new DomainDefinition("read_month", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_READ_YEAR =
                new DomainDefinition("read_year", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_ACQUIRED_DAY =
                new DomainDefinition("acq_day", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_ACQUIRED_MONTH =
                new DomainDefinition("acq_month", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_ACQUIRED_YEAR =
                new DomainDefinition("acq_year", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_FIRST_PUBLICATION_MONTH =
                new DomainDefinition("first_pub_month", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_FIRST_PUBLICATION_YEAR =
                new DomainDefinition("first_pub_year", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_PUBLISHED_MONTH =
                new DomainDefinition("pub_month", ColumnInfo.TYPE_INTEGER);
        DOM_DATE_PUBLISHED_YEAR =
                new DomainDefinition("pub_year", ColumnInfo.TYPE_INTEGER);
    }

    /**
     * Series number, cast()'d for sorting purposes in {@link BooklistBuilder}
     * so we can sort it numerically regardless of content.
     */
    public static final DomainDefinition DOM_BL_SERIES_NUM_FLOAT;
    /** {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_ABSOLUTE_POSITION;
    /** {@link #TBL_BOOK_LIST_NODE_SETTINGS} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_NODE_ROW_KIND;
    /** {@link #TBL_BOOK_LIST_NODE_SETTINGS} {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_ROOT_KEY;
    /** {@link #TBL_BOOK_LIST} {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_NODE_LEVEL;
    /** {@link #TBL_BOOK_LIST} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_BOOK_COUNT;
    /** {@link #TBL_BOOK_LIST} {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_BL_PRIMARY_SERIES_COUNT;
    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} navigation. */
    public static final DomainDefinition DOM_BL_REAL_ROW_ID;
    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node visible. */
    public static final DomainDefinition DOM_BL_NODE_VISIBLE;
    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node expanded. */
    public static final DomainDefinition DOM_BL_NODE_EXPANDED;
    /** {@link BooklistBuilder} the 'selected' book, i.e. the one to scroll back into view. */
    public static final DomainDefinition DOM_BL_NODE_SELECTED;

    public static final String KEY_BL_ABSOLUTE_POSITION = "abs_pos";
    public static final String KEY_BL_NODE_ROW_KIND = "kind";
    private static final String KEY_BL_ROOT_KEY = "root_key";
    public static final String KEY_BL_NODE_LEVEL = "level";
    private static final String KEY_BL_REAL_ROW_ID = "real_row_id";
    private static final String KEY_BL_NODE_VISIBLE = "visible";
    private static final String KEY_BL_NODE_EXPANDED = "expanded";
    private static final String KEY_BL_NODE_SELECTED = "selected";

    static {
        DOM_BL_SERIES_NUM_FLOAT =
                new DomainDefinition("series_num_float", ColumnInfo.TYPE_REAL);
        DOM_BL_ABSOLUTE_POSITION =
                new DomainDefinition(KEY_BL_ABSOLUTE_POSITION, ColumnInfo.TYPE_INTEGER, true);
        DOM_BL_NODE_ROW_KIND =
                new DomainDefinition(KEY_BL_NODE_ROW_KIND, ColumnInfo.TYPE_INTEGER, true);
        DOM_BL_ROOT_KEY =
                new DomainDefinition(KEY_BL_ROOT_KEY, ColumnInfo.TYPE_TEXT);
        DOM_BL_NODE_LEVEL =
                new DomainDefinition(KEY_BL_NODE_LEVEL, ColumnInfo.TYPE_INTEGER, true);
        DOM_BL_BOOK_COUNT =
                new DomainDefinition("book_count", ColumnInfo.TYPE_INTEGER);
        DOM_BL_PRIMARY_SERIES_COUNT =
                new DomainDefinition("primary_series_count", ColumnInfo.TYPE_INTEGER);
        DOM_BL_REAL_ROW_ID =
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
    }

    /** Base Name of BOOK_LIST-related tables. */
    private static final String DB_TN_BOOK_LIST_NAME;
    /** Keeps track of nodes in the list. Added to {@link #ALL_TABLES}. */
    public static final TableDefinition TBL_BOOK_LIST_NODE_SETTINGS;
    /** Should NOT be added to {@link #ALL_TABLES}. */
    public static final TableDefinition TBL_BOOK_LIST;
    /** Should NOT be added to {@link #ALL_TABLES}. */
    public static final TableDefinition TBL_ROW_NAVIGATOR;
    /**
     * Should NOT be added to {@link #ALL_TABLES}.
     * <p>
     * This table should always be created without column constraints applied,
     * with the exception of the "_id" primary key autoincrement
     */
    public static final TableDefinition TBL_ROW_NAVIGATOR_FLATTENED;

    static {

        DB_TN_BOOK_LIST_NAME = "book_list_tmp";

        TBL_BOOK_LIST_NODE_SETTINGS =
                new TableDefinition(DB_TN_BOOK_LIST_NAME + "_node_settings")
                        .setAlias("blns");

        TBL_BOOK_LIST =
                new TableDefinition(DB_TN_BOOK_LIST_NAME)
                        .setAlias("bl")
                        .setType(TableTypes.Temporary);

        TBL_ROW_NAVIGATOR =
                new TableDefinition(DB_TN_BOOK_LIST_NAME + "_row_pos")
                        .setAlias("blrp")
                        .setType(TableTypes.Temporary);

        TBL_ROW_NAVIGATOR_FLATTENED =
                new TableDefinition(DB_TN_BOOK_LIST_NAME + "_row_pos_flattened")
                        .setAlias("blrpf")
                        .setType(TableTypes.Temporary);

        // Allow debug mode to use standard tables so we can export and inspect the content.
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLES) {
            TBL_BOOK_LIST.setType(TableTypes.Standard);
            TBL_ROW_NAVIGATOR.setType(TableTypes.Standard);
            TBL_ROW_NAVIGATOR_FLATTENED.setType(TableTypes.Standard);
        }
    }

    static {
        TBL_BOOKLIST_STYLES.addDomains(DOM_PK_ID,
                                       DOM_STYLE_IS_BUILTIN,
                                       DOM_UUID)
                           .setPrimaryKey(DOM_PK_ID)
                           .addIndex(DOM_UUID, true, DOM_UUID);
        ALL_TABLES.put(TBL_BOOKLIST_STYLES.getName(), TBL_BOOKLIST_STYLES);

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
                             DOM_BOOK_ISFDB_ID,
                             DOM_BOOK_LIBRARY_THING_ID,
                             DOM_BOOK_OPEN_LIBRARY_ID,
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
                 .addIndex(DOM_BOOK_GOODREADS_ID, false, DOM_BOOK_GOODREADS_ID)
                 .addIndex(DOM_BOOK_OPEN_LIBRARY_ID, false, DOM_BOOK_OPEN_LIBRARY_ID)
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
    }

    static {
        /*
         * {@link BooklistBuilder}
         *
         * Example: a small set of books, sorted by 'Format' booklist style.
         *
         * _id  kind    root_key
         * 1    1       a/273
         * 2    1       a/302
         * 5    13      fmt/Hardcover - Traycase
         */
        TBL_BOOK_LIST_NODE_SETTINGS
                .addDomains(DOM_PK_ID,
                            DOM_BL_NODE_ROW_KIND,
                            DOM_BL_ROOT_KEY)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("ROOT_KIND", true,
                          DOM_BL_ROOT_KEY,
                          DOM_BL_NODE_ROW_KIND)
                .addIndex("KIND_ROOT", true,
                          DOM_BL_NODE_ROW_KIND,
                          DOM_BL_ROOT_KEY);
        ALL_TABLES.put(TBL_BOOK_LIST_NODE_SETTINGS.getName(), TBL_BOOK_LIST_NODE_SETTINGS);

        /*
         * Temporary table used to store flattened book lists.
         *
         * should NOT be added to {@link #ALL_TABLES}
         *
         * This is an EXAMPLE only. The structure will depend on {@link BooklistBuilder}.
         *
         * _id  level   kind book_count root_key                   book     book_uuid                           title
         * 1    1       13              fmt/Hardcover                       1d872e0edbda2c86758d8fc889351716
         * 2    2       0    1          fmt/Hardcover               1617    fc78e8ee6918e54547c2561986ccbec2	Accelerando
         * 3    2       0    1          fmt/Hardcover               1564    47f6f13fd98594afcdca1ce71df9f23c	Bad Ronald
         * ... snip...
         * 21   2       0    1          fmt/Hardcover               1531    3ac5caf178d9c61cbf8810d6233d0a2c	Shadow Captain
         * 22   2       0    1          fmt/Hardcover               1334    9a37fe8a709e2d9ba8c8d0301c4c0903	Tales from the Perilous Realm
         * 23   2       0    1          fmt/Hardcover               1616    61891a4d06bf821a7f270769671c13d9	Toast
         * 24   2       0    1          fmt/Hardcover               1332    ab291216c5d0d6612b8e5e6d4484ffd5	Unfinished Tales of Numenor and Middle-Earth
         * 25   1       13              fmt/Hardcover - Traycase            ae199f646b992c321f3b8ff04d0387ce
         * 26   2       0    1      	fmt/Hardcover - Traycase    1505    228bff45e6d9cb025c2f73911ef6e4c4	Night Lamp
         * 27   2       0    1      	fmt/Hardcover - Traycase    1561    e6db432cb841ea0cb82901e6b3e7c0b3	Ports Of Call
         * 28   1       13              fmt/Paperback   	                a5df7be3d84070e0152494bbbfe99eb6
         * ...snip...
         * 32   2       0    1      	fmt/Paperback               1589    649dfb4b92a05e15e66821e2933f1930	Wild Thyme and Violets and Other Unpublished Stories
         * 33   1       13              fmt/Paperback - Trade   	        07b91229ccb1de81de8f0fe7cdbefb83
         * 34   2       0    1      	fmt/Paperback - Trade       1251    74034e297d73785aee87c26d3050a8cb	Annals of Klepsis
         *
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         *
         * PARTIAL list of domains... this is a temp table created at runtime.
         * Domains are added (or not) depending on how the list is build.
         */
        TBL_BOOK_LIST.addDomains(DOM_PK_ID,
                                 DOM_BL_NODE_LEVEL,
                                 DOM_BL_NODE_ROW_KIND,
                                 DOM_BL_BOOK_COUNT,
                                 DOM_BL_PRIMARY_SERIES_COUNT,
                                 DOM_BL_ROOT_KEY,
                                 DOM_FK_BOOK)
                     .setPrimaryKey(DOM_PK_ID);

        /*
         * FULL representation of ROW_NAVIGATOR temp table. This IS definitive.
         *
         * should NOT be added to {@link #ALL_TABLES}
         *
         * {@link BooklistBuilder}
         *
         * _id	real_row_id	level	visible	expanded	root_key
         * 1	1	        1	    1	    0	        fmt/Hardcover
         * 2	2	        2	    0	    0	        fmt/Hardcover
         * 3	3	        2	    0	    0	        fmt/Hardcover
         * ..snip...
         * 22	22	        2	    0	    0	        fmt/Hardcover
         * 23	23	        2	    0	    0	        fmt/Hardcover
         * 24	24	        2	    0	    0	        fmt/Hardcover
         * 25	25	        1	    1	    1	        fmt/Hardcover - Traycase
         * 26	26	        2	    1	    1	        fmt/Hardcover - Traycase
         * 27	27	        2	    1	    1	        fmt/Hardcover - Traycase
         * 28	28	        1	    1	    0	        fmt/Paperback
         * ...snip...
         * 32	32	        2	    0	    0	        fmt/Paperback
         * 33	33	        1	    1	    0	        fmt/Paperback - Trade
         * 34	34	        2	    0	    0	        fmt/Paperback - Trade
         */
        TBL_ROW_NAVIGATOR.addDomains(DOM_PK_ID,
                                     DOM_BL_REAL_ROW_ID,
                                     DOM_BL_NODE_LEVEL,
                                     DOM_BL_NODE_VISIBLE,
                                     DOM_BL_NODE_EXPANDED,
                                     DOM_BL_ROOT_KEY)
                         .setPrimaryKey(DOM_PK_ID);
        // do ***NOT*** add the reference here. It will be added *after* cloning in BooklistBuilder.
        // as the TBL_BOOK_LIST name will have an instance specific suffix.
        //.addReference(TBL_BOOK_LIST, DOM_BL_REAL_ROW_ID);

        /*
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         *
         * should NOT be added to {@link #ALL_TABLES}
         */
        TBL_ROW_NAVIGATOR_FLATTENED.addDomains(DOM_PK_ID,
                                               DOM_FK_BOOK)
                                   .setPrimaryKey(DOM_PK_ID);
    }

    /** FTS primary key. */
    static final DomainDefinition DOM_PK_DOCID;

    /** Full text search; should NOT be added to {@link #ALL_TABLES}. */
    static final TableDefinition TBL_BOOKS_FTS;

    /**
     * {@link #TBL_BOOKS_FTS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    static final DomainDefinition DOM_FTS_AUTHOR_NAME;

    /* FTS table definition. */
    static {
        DOM_PK_DOCID = new DomainDefinition("docid");

        TBL_BOOKS_FTS = new TableDefinition("books_fts").setType(TableTypes.FTS3);

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
    }

    /*
     * Developer sanity checks.
     */
    static {
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
