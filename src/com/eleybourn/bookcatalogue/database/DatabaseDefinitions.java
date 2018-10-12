/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.database;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition.TableTypes;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;

/**
 * Static definitions of database objects; this is an incomplete representation of the
 * BookCatalogue database. It should probably become the 'real' representation of the database
 * when DbUtils is more mature. For now, it suffices to build the complex queries used in BooklistBuilder.
 *
 * @author Philip Warner
 */
public class DatabaseDefinitions {
    /**
     * UNIQUE table aliases to use for each table.
     * They are collected here so checking uniqueness is as simple as possible.
     *
     * If you think you need these public, think again and use the TableDefinition.getAlias()
     */
    private static final String ALIAS_ANTHOLOGY = "an";
    private static final String ALIAS_AUTHORS = "a";
    private static final String ALIAS_BOOKS = "b";
    private static final String ALIAS_BOOK_ANTHOLOGY = "bat";
    private static final String ALIAS_BOOK_AUTHOR = "ba";
    private static final String ALIAS_BOOK_LIST = "bl";
    private static final String ALIAS_BOOK_LIST_ROW_POSITION = "blrp";
    private static final String ALIAS_BOOK_LIST_ROW_POSITION_FLATTENED = "blrpf";
    private static final String ALIAS_BOOK_LIST_NODE_SETTINGS = "blns";
    private static final String ALIAS_BOOK_LIST_STYLES = "bls";
    private static final String ALIAS_BOOK_BOOKSHELF = "bbsh";
    private static final String ALIAS_BOOK_SERIES = "bs";
    private static final String ALIAS_BOOKSHELF = "bsh";
    private static final String ALIAS_LOAN = "l";
    private static final String ALIAS_SERIES = "s";

    /**
     * Actual table names
     *
     * If you think you need these public, think again and use the TableDefinition.getName()
     */
    private static final String DB_TB_ANTHOLOGY = "anthology";
    private static final String DB_TB_AUTHORS = "authors";
    private static final String DB_TB_BOOKS = "books";
    private static final String DB_TB_BOOKSHELF = "bookshelf";
    private static final String DB_TB_BOOKLIST_STYLES = "book_list_styles";
    private static final String DB_TB_LOAN = "loan";
    private static final String DB_TB_SERIES = "series";

    private static final String DB_TB_BOOK_ANTHOLOGY = "book_anthology";
    private static final String DB_TB_BOOK_AUTHOR = "book_author";
    private static final String DB_TB_BOOK_BOOKSHELF = "book_bookshelf_weak";
    private static final String DB_TB_BOOK_SERIES = "book_series";


    // just to avoid typos
    private static final String NOT_NULL = "not null";

    /*
     * Domain definition.
     *
     * NOTE!!! Because Java String comparisons are not case-insensitive, it is
     * important that ALL these fields 'name' be listed in LOWER CASE.
     */

    /** FTS primary key */
    public static final DomainDefinition DOM_DOCID = new DomainDefinition("docid", TableInfo.TYPE_INTEGER, NOT_NULL, "primary key autoincrement");

    /** primary key */
    public static final DomainDefinition DOM_ID = new DomainDefinition("_id", TableInfo.TYPE_INTEGER, NOT_NULL, "primary key autoincrement");
    /** foreign keys */
    static final DomainDefinition DOM_ANTHOLOGY_ID = new DomainDefinition("anthology", TableInfo.TYPE_INTEGER);
    public static final DomainDefinition DOM_AUTHOR_ID = new DomainDefinition("author", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    public static final DomainDefinition DOM_BOOK_ID = new DomainDefinition("book", TableInfo.TYPE_INTEGER);
    public static final DomainDefinition DOM_BOOKSHELF_ID = new DomainDefinition("bookshelf", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    public static final DomainDefinition DOM_SERIES_ID = new DomainDefinition("series_id", TableInfo.TYPE_INTEGER);


    /** used in multiple tables */
    public static final DomainDefinition DOM_TITLE = new DomainDefinition("title", TableInfo.TYPE_TEXT, NOT_NULL, "");
    public static final DomainDefinition DOM_FIRST_PUBLICATION = new DomainDefinition("first_publication", TableInfo.TYPE_DATE);
    public static final DomainDefinition DOM_DESCRIPTION = new DomainDefinition("description", TableInfo.TYPE_TEXT);

    /* {@link #TBL_ANTHOLOGY} */
    // DOM_FIRST_PUBLICATION

    /** {@link #TBL_AUTHORS} */
    public static final DomainDefinition DOM_AUTHOR_FAMILY_NAME = new DomainDefinition("family_name", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_AUTHOR_GIVEN_NAMES = new DomainDefinition("given_names", TableInfo.TYPE_TEXT);

    /**
     * {@link #DOM_BOOK_ANTHOLOGY_BITMASK}
     *  0x01 = ant from one author
     *  0x10 = ant from multiple authors
     */
    public static final int DOM_ANTHOLOGY_SINGLE_AUTHOR = 1;
    public static final int DOM_ANTHOLOGY_MULTIPLE_AUTHORS = 1 << 1;

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_UUID = new DomainDefinition("book_uuid", TableInfo.TYPE_TEXT, NOT_NULL, "default (lower(hex(randomblob(16))))");
    public static final DomainDefinition DOM_BOOK_ISBN = new DomainDefinition("isbn", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_BOOK_ANTHOLOGY_BITMASK = new DomainDefinition("anthology", TableInfo.TYPE_INT, NOT_NULL, "default 0");
    public static final DomainDefinition DOM_BOOK_DATE_ADDED = new DomainDefinition("date_added", TableInfo.TYPE_DATETIME,"", "default current_timestamp" );
    public static final DomainDefinition DOM_BOOK_DATE_PUBLISHED = new DomainDefinition("date_published", TableInfo.TYPE_DATE);
    public static final DomainDefinition DOM_BOOK_FORMAT = new DomainDefinition("format", TableInfo.TYPE_TEXT, "default ''");
    public static final DomainDefinition DOM_BOOK_GENRE = new DomainDefinition("genre", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_BOOK_GOODREADS_BOOK_ID = new DomainDefinition("goodreads_book_id", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_BOOK_GOODREADS_LAST_SYNC_DATE = new DomainDefinition("last_goodreads_sync_date", TableInfo.TYPE_DATE, "default '0000-00-00'");
    public static final DomainDefinition DOM_BOOK_LANGUAGE = new DomainDefinition("language", TableInfo.TYPE_TEXT, "default ''");
    public static final DomainDefinition DOM_BOOK_LIST_PRICE = new DomainDefinition("list_price", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_BOOK_LOCATION = new DomainDefinition("location", TableInfo.TYPE_TEXT, "default ''");
    public static final DomainDefinition DOM_BOOK_NOTES = new DomainDefinition("notes", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_BOOK_PAGES = new DomainDefinition("pages", TableInfo.TYPE_INTEGER);
    public static final DomainDefinition DOM_BOOK_PUBLISHER = new DomainDefinition("publisher", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_BOOK_RATING = new DomainDefinition("rating", TableInfo.TYPE_FLOAT, NOT_NULL, "default 0");
    public static final DomainDefinition DOM_BOOK_READ = new DomainDefinition("read", TableInfo.TYPE_BOOLEAN, NOT_NULL, "default 0");
    public static final DomainDefinition DOM_BOOK_READ_START = new DomainDefinition("read_start", TableInfo.TYPE_DATE);
    public static final DomainDefinition DOM_BOOK_READ_END = new DomainDefinition("read_end", TableInfo.TYPE_DATE);
    public static final DomainDefinition DOM_BOOK_SIGNED = new DomainDefinition("signed", TableInfo.TYPE_BOOLEAN, NOT_NULL, "default 0");

    /** {@link #TBL_BOOKSHELF) */
    public static final DomainDefinition DOM_BOOKSHELF = new DomainDefinition("bookshelf", TableInfo.TYPE_TEXT, NOT_NULL, "");

    /** {@link #TBL_LOAN) */
    public static final DomainDefinition DOM_LOANED_TO = new DomainDefinition("loaned_to", TableInfo.TYPE_TEXT, NOT_NULL, "");

    /** {@link #TBL_SERIES) */
    public static final DomainDefinition DOM_SERIES_NAME = new DomainDefinition("series_name", TableInfo.TYPE_TEXT);

    /** {@link #TBL_BOOKLIST_STYLES} */
    public static final DomainDefinition DOM_STYLE = new DomainDefinition("style", TableInfo.TYPE_BLOB, NOT_NULL, "");


    /** {@link #TBL_BOOK_ANTHOLOGY} */
    static final DomainDefinition DOM_BOOK_ANTHOLOGY_POSITION = new DomainDefinition("position", TableInfo.TYPE_INTEGER, NOT_NULL, "");

    /** {@link #TBL_BOOK_AUTHOR} */
    public static final DomainDefinition DOM_AUTHOR_POSITION = new DomainDefinition("author_position", TableInfo.TYPE_INTEGER, NOT_NULL, "");

    /** {@link #TBL_BOOK_SERIES}
     *  the Series position is the order the series show up in a book. Particularly important for "primary series"
     * and in lists where 'all' series are shown. */
    public static final DomainDefinition DOM_BOOK_SERIES_NUM = new DomainDefinition("series_num", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_BOOK_SERIES_POSITION = new DomainDefinition("series_position", TableInfo.TYPE_INTEGER);



    // sort these one day....

    public static final DomainDefinition DOM_BOOK_COUNT = new DomainDefinition("book_count", TableInfo.TYPE_INTEGER);


    public static final DomainDefinition DOM_AUTHOR_NAME = new DomainDefinition("author_name", TableInfo.TYPE_TEXT, NOT_NULL, "");
    public static final DomainDefinition DOM_AUTHOR_SORT = new DomainDefinition("author_sort", TableInfo.TYPE_TEXT, NOT_NULL, "");
    public static final DomainDefinition DOM_AUTHOR_FORMATTED = new DomainDefinition("author_formatted", TableInfo.TYPE_TEXT, NOT_NULL, "");
    public static final DomainDefinition DOM_AUTHOR_FORMATTED_GIVEN_FIRST = new DomainDefinition("author_formatted_given_first", TableInfo.TYPE_TEXT, NOT_NULL, "");


    public static final DomainDefinition DOM_TITLE_LETTER = new DomainDefinition("title_letter", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_SERIES_FORMATTED = new DomainDefinition("series_formatted", TableInfo.TYPE_TEXT, NOT_NULL, "");

    public static final DomainDefinition DOM_SERIES_NUM_FLOAT = new DomainDefinition("series_num_float", TableInfo.TYPE_FLOAT);
    public static final DomainDefinition DOM_LOANED_TO_SORT = new DomainDefinition("loaned_to_sort", TableInfo.TYPE_INT, NOT_NULL, "");

    public static final DomainDefinition DOM_ADDED_DAY = new DomainDefinition("added_day", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_ADDED_MONTH = new DomainDefinition("added_month", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_ADDED_YEAR = new DomainDefinition("added_year", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_LAST_UPDATE_DATE = new DomainDefinition("last_update_date", TableInfo.TYPE_DATE, NOT_NULL, "default current_timestamp");

    public static final DomainDefinition DOM_UPDATE_DAY = new DomainDefinition("update_day", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_UPDATE_MONTH = new DomainDefinition("update_month", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_UPDATE_YEAR = new DomainDefinition("update_year", TableInfo.TYPE_INT);

    public static final DomainDefinition DOM_READ_STATUS = new DomainDefinition("read_status", TableInfo.TYPE_TEXT, NOT_NULL, "");
    public static final DomainDefinition DOM_READ_DAY = new DomainDefinition("read_day", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_READ_MONTH = new DomainDefinition("read_month", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_READ_YEAR = new DomainDefinition("read_year", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_REAL_ROW_ID = new DomainDefinition("real_row_id", TableInfo.TYPE_INT);

    public static final DomainDefinition DOM_PUBLICATION_YEAR = new DomainDefinition("publication_year", TableInfo.TYPE_INT);
    public static final DomainDefinition DOM_PUBLICATION_MONTH = new DomainDefinition("publication_month", TableInfo.TYPE_INT);

    public static final DomainDefinition DOM_ABSOLUTE_POSITION = new DomainDefinition("absolute_position", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    public static final DomainDefinition DOM_PRIMARY_SERIES_COUNT = new DomainDefinition("primary_series_count", TableInfo.TYPE_INTEGER);
    public static final DomainDefinition DOM_ROOT_KEY = new DomainDefinition("root_key", TableInfo.TYPE_TEXT);
    public static final DomainDefinition DOM_ROW_KIND = new DomainDefinition("kind", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    public static final DomainDefinition DOM_MARK = new DomainDefinition("mark", "boolean", "default 0");
    public static final DomainDefinition DOM_LEVEL = new DomainDefinition("level", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    public static final DomainDefinition DOM_EXPANDED = new DomainDefinition("expanded", TableInfo.TYPE_INT, "default 0");
    public static final DomainDefinition DOM_VISIBLE = new DomainDefinition("visible", TableInfo.TYPE_INT, "default 0");

    /**
     * FTS Table
     */
    public static final TableDefinition TBL_BOOKS_FTS = new TableDefinition("books_fts")
            .addDomains(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_BOOK_NOTES,
                    DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE, DOM_BOOK_LOCATION, DOM_BOOK_ISBN)
            .setType(TableTypes.FTS3);
    /**
     * Partial representation of BOOKSHELF table
     */
    public static final TableDefinition TBL_BOOKSHELF = new TableDefinition(DB_TB_BOOKSHELF)
            .addDomains(DOM_ID, DOM_BOOKSHELF)
            .setAlias(ALIAS_BOOKSHELF)
            .setPrimaryKey(DOM_ID);
    /**
     * Definition for the custom booklist styles table
     */
    static final TableDefinition TBL_BOOKLIST_STYLES = new TableDefinition(DB_TB_BOOKLIST_STYLES)
            .addDomains(DOM_ID, DOM_STYLE)
            .setAlias(ALIAS_BOOK_LIST_STYLES)
            .addIndex("id", true, DOM_ID);

    /**
     * Partial representation of SERIES table
     */
    public static final TableDefinition TBL_SERIES = new TableDefinition(DB_TB_SERIES)
            .addDomains(DOM_ID, DOM_SERIES_NAME)
            .setAlias(ALIAS_SERIES)
            .setPrimaryKey(DOM_ID);
    /**
     * Partial representation of BOOKS table
     */
    public static final TableDefinition TBL_BOOKS = new TableDefinition(DB_TB_BOOKS)
            .addDomains(DOM_ID, DOM_TITLE)
            .setAlias(ALIAS_BOOKS)
            .setPrimaryKey(DOM_ID);
    /**
     * Partial representation of AUTHORS table
     */
    public static final TableDefinition TBL_AUTHORS = new TableDefinition(DB_TB_AUTHORS)
            .addDomains(DOM_ID, DOM_AUTHOR_GIVEN_NAMES, DOM_AUTHOR_FAMILY_NAME)
            .setAlias(ALIAS_AUTHORS)
            .setPrimaryKey(DOM_ID);
    /**
     * Partial representation of ANTHOLOGY table
     */
    public static final TableDefinition TBL_ANTHOLOGY = new TableDefinition(DB_TB_ANTHOLOGY)
            .addDomains(DOM_ID, DOM_AUTHOR_ID, DOM_TITLE, DOM_FIRST_PUBLICATION)
            .setAlias(ALIAS_ANTHOLOGY)
            .setPrimaryKey(DOM_ID)
            .addReference(TBL_AUTHORS, DOM_AUTHOR_ID);

    /**
     * Partial representation of BOOK_ANTHOLOGY table
     */
    public static final TableDefinition TBL_BOOK_ANTHOLOGY = new TableDefinition(DB_TB_BOOK_ANTHOLOGY)
            .addDomains(DOM_BOOK_ID, DOM_ANTHOLOGY_ID, DOM_BOOK_ANTHOLOGY_POSITION)
            .setAlias(ALIAS_BOOK_ANTHOLOGY)
            .setPrimaryKey(DOM_BOOK_ID, DOM_ANTHOLOGY_ID)
            .addReference(TBL_BOOKS, DOM_BOOK_ID)
            .addReference(TBL_ANTHOLOGY, DOM_ANTHOLOGY_ID);

    /**
     * Partial representation of BOOK_AUTHOR table
     */
    public static final TableDefinition TBL_BOOK_AUTHOR = new TableDefinition(DB_TB_BOOK_AUTHOR)
            .addDomains(DOM_BOOK_ID, DOM_AUTHOR_ID)
            .setAlias(ALIAS_BOOK_AUTHOR)
            .addReference(TBL_BOOKS, DOM_BOOK_ID)
            .addReference(TBL_AUTHORS, DOM_AUTHOR_ID);

    /**
     * Partial representation of BOOK_SERIES table
     */
    public static final TableDefinition TBL_BOOK_SERIES = new TableDefinition(DB_TB_BOOK_SERIES)
            .addDomains(DOM_BOOK_ID, DOM_SERIES_ID, DOM_BOOK_SERIES_NUM, DOM_BOOK_SERIES_POSITION)
            .setAlias(ALIAS_BOOK_SERIES)
            .setPrimaryKey(DOM_BOOK_ID, DOM_BOOK_SERIES_POSITION)
            .addReference(TBL_BOOKS, DOM_BOOK_ID)
            .addReference(TBL_SERIES, DOM_SERIES_ID);
    /**
     * Partial representation of BOOK_BOOKSHELF table
     */
    public static final TableDefinition TBL_BOOK_BOOKSHELF = new TableDefinition(DB_TB_BOOK_BOOKSHELF)
            .addDomains(DOM_BOOK_ID, DOM_BOOKSHELF_ID)
            .setAlias(ALIAS_BOOK_BOOKSHELF)
            .setPrimaryKey(DOM_BOOK_ID, DOM_BOOKSHELF_ID)
            .addReference(TBL_BOOKS, DOM_BOOK_ID)
            .addReference(TBL_BOOKSHELF, DOM_BOOKSHELF_ID);

    /**
     * Partial representation of LOAN table
     */
    public static final TableDefinition TBL_LOAN = new TableDefinition(DB_TB_LOAN)
            .addDomains(DOM_ID, DOM_BOOK_ID, DOM_LOANED_TO)
            .setPrimaryKey(DOM_ID)
            .setAlias(ALIAS_LOAN)
            .addReference(TBL_BOOKS, DOM_BOOK_ID);



    // Base Name of BOOK_LIST-related tables.
    private static final String TBL_BOOK_LIST_NAME = "book_list_tmp";
    /**
     * Temporary table used to store flattened bok lists
     */
    public static final TableDefinition TBL_BOOK_LIST = new TableDefinition(TBL_BOOK_LIST_NAME)
            .addDomains(DOM_ID, DOM_LEVEL, DOM_ROW_KIND,
            // Many others...this is a temp table created at runtime.
            DOM_BOOK_COUNT, DOM_PRIMARY_SERIES_COUNT)
            .setType(TableTypes.Temporary)
            .setPrimaryKey(DOM_ID)
            .setAlias(ALIAS_BOOK_LIST);

    /**
     * Definition of BOOK_LIST_NODE_SETTINGS temp table. This IS definitive
     */
    public static final TableDefinition TBL_BOOK_LIST_NODE_SETTINGS = new TableDefinition(TBL_BOOK_LIST_NAME + "_node_settings")
            .addDomains(DOM_ID, DOM_ROW_KIND, DOM_ROOT_KEY)
            .setAlias(ALIAS_BOOK_LIST_NODE_SETTINGS)
            .addIndex("ROOT_KIND", true, DOM_ROOT_KEY, DOM_ROW_KIND)
            .addIndex("KIND_ROOT", true, DOM_ROW_KIND, DOM_ROOT_KEY);
    /**
     * Definition of ROW_NAVIGATOR temp table
     */
    public static final TableDefinition TBL_ROW_NAVIGATOR = new TableDefinition(TBL_BOOK_LIST_NAME + "_row_pos")
            .addDomains(DOM_ID, DOM_REAL_ROW_ID, DOM_LEVEL, DOM_VISIBLE, DOM_EXPANDED, DOM_ROOT_KEY)
            .setType(TableTypes.Temporary)
            .addReference(TBL_BOOK_LIST, DOM_REAL_ROW_ID)
            .setAlias(ALIAS_BOOK_LIST_ROW_POSITION);
    /**
     * Definition of ROW_NAVIGATOR_FLATTENED temp table
     */
    public static final TableDefinition TBL_ROW_NAVIGATOR_FLATTENED = new TableDefinition(TBL_BOOK_LIST_NAME + "_row_pos_flattened")
            .addDomains(DOM_ID, DOM_BOOK_ID)
            .setType(TableTypes.Temporary)
            .setAlias(ALIAS_BOOK_LIST_ROW_POSITION_FLATTENED);

}
