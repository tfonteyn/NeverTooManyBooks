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

import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
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
    /** full text search */
    private static final String DB_TB_FTS_BOOKS = "books_fts";
    /** Base Name of BOOK_LIST-related tables. */
    private static final String TBL_BOOK_LIST_NAME = "book_list_tmp";

    /** just to avoid typos */
    private static final String NOT_NULL = "not null";

    /**
     * {@link #DOM_BOOK_ANTHOLOGY_BITMASK}
     * 0%01 = it's an anthology (with a single author)
     * 0%10 = has multiple authors
     */
    public static final int DOM_IS_ANTHOLOGY = 1;
    public static final int DOM_BOOK_WITH_MULTIPLE_AUTHORS = 1 << 1;

    /*
     * Domain definitions.
     *
     * NOTE!!! Because Java String comparisons are not case-insensitive, it is
     * important that ALL these fields 'name' be listed in LOWER CASE.
     */

    /** FTS primary key */
    public static final DomainDefinition DOM_PK_DOCID = new DomainDefinition("docid", TableInfo.TYPE_INTEGER, NOT_NULL, "primary key autoincrement");
    /** primary key */
    public static final DomainDefinition DOM_PK_ID = new DomainDefinition("_id", TableInfo.TYPE_INTEGER, NOT_NULL, "primary key autoincrement");
    /** foreign key */
    public static final DomainDefinition DOM_FK_ANTHOLOGY_ID = new DomainDefinition("anthology", TableInfo.TYPE_INTEGER);
    /** foreign key */
    public static final DomainDefinition DOM_FK_AUTHOR_ID = new DomainDefinition("author", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    /** foreign key */
    public static final DomainDefinition DOM_FK_BOOKSHELF_ID = new DomainDefinition("bookshelf", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    /** foreign key */
    public static final DomainDefinition DOM_FK_BOOK_ID = new DomainDefinition("book", TableInfo.TYPE_INTEGER);
    /** foreign key */
    public static final DomainDefinition DOM_FK_SERIES_ID = new DomainDefinition("series_id", TableInfo.TYPE_INTEGER);

    /**{@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_DESCRIPTION = new DomainDefinition("description", TableInfo.TYPE_TEXT);

    /** {@link #TBL_BOOKS}  {@link #TBL_ANTHOLOGY} */
    public static final DomainDefinition DOM_TITLE = new DomainDefinition("title", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** {@link #TBL_BOOKS}  {@link #TBL_ANTHOLOGY} */
    public static final DomainDefinition DOM_FIRST_PUBLICATION = new DomainDefinition("first_publication", TableInfo.TYPE_DATE, NOT_NULL, "default ''");

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_LAST_UPDATE_DATE = new DomainDefinition("last_update_date", TableInfo.TYPE_DATETIME, NOT_NULL, "default current_timestamp");

    /* ========================================================================================== */

    /** {@link #TBL_AUTHORS} */
    public static final DomainDefinition DOM_AUTHOR_FAMILY_NAME = new DomainDefinition("family_name", TableInfo.TYPE_TEXT);
    /** {@link #TBL_AUTHORS} */
    public static final DomainDefinition DOM_AUTHOR_GIVEN_NAMES = new DomainDefinition("given_names", TableInfo.TYPE_TEXT);
    /** {@link #TBL_AUTHORS} "FamilyName, GivenName" */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED = new DomainDefinition("author_formatted", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** {@link #TBL_AUTHORS} "GivenName FamilyName" */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED_GIVEN_FIRST = new DomainDefinition("author_formatted_given_first", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** Partial representation of AUTHORS table */
    public static final TableDefinition TBL_AUTHORS =
            new TableDefinition(DB_TB_AUTHORS)
            .addDomains(DOM_PK_ID, DOM_AUTHOR_GIVEN_NAMES, DOM_AUTHOR_FAMILY_NAME)
            .setAlias(ALIAS_AUTHORS)
            .setPrimaryKey(DOM_PK_ID);

    /* ========================================================================================== */

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_UUID = new DomainDefinition("book_uuid", TableInfo.TYPE_TEXT, NOT_NULL, "default (lower(hex(randomblob(16))))");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_ISBN = new DomainDefinition("isbn", TableInfo.TYPE_TEXT);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_ISFDB_ID = new DomainDefinition("isfdb_book_id", TableInfo.TYPE_INTEGER);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_LIBRARY_THING_ID = new DomainDefinition("lt_book_id", TableInfo.TYPE_INTEGER);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_GOODREADS_BOOK_ID = new DomainDefinition("goodreads_book_id", TableInfo.TYPE_INTEGER);

    /** {@link #TBL_BOOKS} added to the collection */
    public static final DomainDefinition DOM_BOOK_DATE_ADDED = new DomainDefinition("date_added", TableInfo.TYPE_DATETIME, "", "default current_timestamp");

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_DATE_ACQUIRED = new DomainDefinition("date_acquired", TableInfo.TYPE_DATE);

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_PUBLISHER = new DomainDefinition("publisher", TableInfo.TYPE_TEXT);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_DATE_PUBLISHED = new DomainDefinition("date_published", TableInfo.TYPE_DATE);


    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_PRICE_LISTED = new DomainDefinition("list_price", TableInfo.TYPE_TEXT, "default ''");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_PRICE_LISTED_CURRENCY = new DomainDefinition("list_price_currency", TableInfo.TYPE_TEXT, "default ''");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_PRICE_PAID = new DomainDefinition("price_paid", TableInfo.TYPE_TEXT, "default ''");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_PRICE_PAID_CURRENCY = new DomainDefinition("price_paid_currency", TableInfo.TYPE_TEXT, "default ''");

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_FORMAT = new DomainDefinition("format", TableInfo.TYPE_TEXT, "default ''");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_GENRE = new DomainDefinition("genre", TableInfo.TYPE_TEXT);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_LANGUAGE = new DomainDefinition("language", TableInfo.TYPE_TEXT, "default ''");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_LOCATION = new DomainDefinition("location", TableInfo.TYPE_TEXT, "default ''");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_PAGES = new DomainDefinition("pages", TableInfo.TYPE_INTEGER);

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_NOTES = new DomainDefinition("notes", TableInfo.TYPE_TEXT);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_READ = new DomainDefinition("read", TableInfo.TYPE_BOOLEAN, NOT_NULL, "default 0");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_READ_START = new DomainDefinition("read_start", TableInfo.TYPE_DATE);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_READ_END = new DomainDefinition("read_end", TableInfo.TYPE_DATE);
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_RATING = new DomainDefinition("rating", TableInfo.TYPE_REAL, NOT_NULL, "default 0");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_SIGNED = new DomainDefinition("signed", TableInfo.TYPE_BOOLEAN, NOT_NULL, "default 0");


    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_GOODREADS_LAST_SYNC_DATE = new DomainDefinition("last_goodreads_sync_date", TableInfo.TYPE_DATE, "default '0000-00-00'");

    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_ANTHOLOGY_BITMASK = new DomainDefinition("anthology", TableInfo.TYPE_INTEGER, NOT_NULL, "default 0");
    /** {@link #TBL_BOOKS} */
    public static final DomainDefinition DOM_BOOK_EDITION_BITMASK = new DomainDefinition("edition_bm", TableInfo.TYPE_INTEGER, NOT_NULL, "default 0");


    /** Partial representation of BOOKS table */
    public static final TableDefinition TBL_BOOKS =
            new TableDefinition(DB_TB_BOOKS)
            .addDomains(DOM_PK_ID, DOM_TITLE)
            .setAlias(ALIAS_BOOKS)
            .setPrimaryKey(DOM_PK_ID);

    /* ========================================================================================== */

    /** {@link #TBL_BOOK_AUTHOR} */
    public static final DomainDefinition DOM_AUTHOR_POSITION = new DomainDefinition("author_position", TableInfo.TYPE_INTEGER, NOT_NULL, "");

    /** Partial representation of BOOK_AUTHOR table */
    public static final TableDefinition TBL_BOOK_AUTHOR =
            new TableDefinition(DB_TB_BOOK_AUTHOR)
            .addDomains(DOM_FK_BOOK_ID, DOM_FK_AUTHOR_ID)
            .setAlias(ALIAS_BOOK_AUTHOR)
            .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
            .addReference(TBL_AUTHORS, DOM_FK_AUTHOR_ID);

    /* ========================================================================================== */

    /** {@link #TBL_SERIES) */
    public static final DomainDefinition DOM_SERIES_NAME = new DomainDefinition("series_name", TableInfo.TYPE_TEXT);
    /** {@link #TBL_SERIES) */
    public static final DomainDefinition DOM_SERIES_FORMATTED = new DomainDefinition("series_formatted", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** Partial representation of SERIES table */
    public static final TableDefinition TBL_SERIES =
            new TableDefinition(DB_TB_SERIES)
            .addDomains(DOM_PK_ID, DOM_SERIES_NAME)
            .setAlias(ALIAS_SERIES)
            .setPrimaryKey(DOM_PK_ID);

    /* ========================================================================================== */

    /** {@link #TBL_BOOK_SERIES} */
    public static final DomainDefinition DOM_BOOK_SERIES_NUM = new DomainDefinition("series_num", TableInfo.TYPE_TEXT);
    /** {@link #TBL_BOOK_SERIES}
     * The Series position is the order the series show up in a book. Particularly important
     * for "primary series" and in lists where 'all' series are shown.
     */
    public static final DomainDefinition DOM_BOOK_SERIES_POSITION = new DomainDefinition("series_position", TableInfo.TYPE_INTEGER);
    /** Partial representation of BOOK_SERIES table */
    public static final TableDefinition TBL_BOOK_SERIES =
            new TableDefinition(DB_TB_BOOK_SERIES)
            .addDomains(DOM_FK_BOOK_ID, DOM_FK_SERIES_ID, DOM_BOOK_SERIES_NUM, DOM_BOOK_SERIES_POSITION)
            .setAlias(ALIAS_BOOK_SERIES)
            .setPrimaryKey(DOM_FK_BOOK_ID, DOM_BOOK_SERIES_POSITION)
            .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
            .addReference(TBL_SERIES, DOM_FK_SERIES_ID);

    /* ========================================================================================== */

    /** {@link #TBL_BOOKSHELF) */
    public static final DomainDefinition DOM_BOOKSHELF = new DomainDefinition("bookshelf", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** Partial representation of BOOKSHELF table */
    public static final TableDefinition TBL_BOOKSHELF =
            new TableDefinition(DB_TB_BOOKSHELF)
            .addDomains(DOM_PK_ID, DOM_BOOKSHELF)
            .setAlias(ALIAS_BOOKSHELF)
            .setPrimaryKey(DOM_PK_ID);

    /* ========================================================================================== */

    /** Partial representation of BOOK_BOOKSHELF table */
    public static final TableDefinition TBL_BOOK_BOOKSHELF =
            new TableDefinition(DB_TB_BOOK_BOOKSHELF)
            .addDomains(DOM_FK_BOOK_ID, DOM_FK_BOOKSHELF_ID)
            .setAlias(ALIAS_BOOK_BOOKSHELF)
            .setPrimaryKey(DOM_FK_BOOK_ID, DOM_FK_BOOKSHELF_ID)
            .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
            .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF_ID);

    /* ========================================================================================== */

    /** {@link #TBL_LOAN) */
    public static final DomainDefinition DOM_LOANED_TO = new DomainDefinition("loaned_to", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** Partial representation of LOAN table */
    public static final TableDefinition TBL_LOAN =
            new TableDefinition(DB_TB_LOAN)
            .addDomains(DOM_PK_ID, DOM_FK_BOOK_ID, DOM_LOANED_TO)
            .setPrimaryKey(DOM_PK_ID)
            .setAlias(ALIAS_LOAN)
            .addReference(TBL_BOOKS, DOM_FK_BOOK_ID);

    /* ========================================================================================== */

    /** {@link #TBL_BOOKLIST_STYLES} */
    public static final DomainDefinition DOM_STYLE = new DomainDefinition("style", TableInfo.TYPE_BLOB, NOT_NULL, "");
    /** FULL representation for the custom styles BOOKLIST_STYLES table */
    static final TableDefinition TBL_BOOKLIST_STYLES =
            new TableDefinition(DB_TB_BOOKLIST_STYLES)
            .addDomains(DOM_PK_ID, DOM_STYLE)
            .setAlias(ALIAS_BOOK_LIST_STYLES)
            .addIndex("id", true, DOM_PK_ID);

    /* ========================================================================================== */

    /** Partial representation of ANTHOLOGY table */
    public static final TableDefinition TBL_ANTHOLOGY =
            new TableDefinition(DB_TB_ANTHOLOGY)
            .addDomains(DOM_PK_ID, DOM_FK_AUTHOR_ID, DOM_TITLE, DOM_FIRST_PUBLICATION)
            .setAlias(ALIAS_ANTHOLOGY)
            .setPrimaryKey(DOM_PK_ID)
            .addReference(TBL_AUTHORS, DOM_FK_AUTHOR_ID);

    /* ========================================================================================== */

    /** {@link #TBL_BOOK_TOC_ENTRIES} */
    static final DomainDefinition DOM_BOOK_TOC_ENTRY_POSITION = new DomainDefinition("position", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    /** Partial representation of BOOK_ANTHOLOGY table */
    public static final TableDefinition TBL_BOOK_TOC_ENTRIES =
            new TableDefinition(DB_TB_BOOK_ANTHOLOGY)
            .addDomains(DOM_FK_BOOK_ID, DOM_FK_ANTHOLOGY_ID, DOM_BOOK_TOC_ENTRY_POSITION)
            .setAlias(ALIAS_BOOK_ANTHOLOGY)
            .setPrimaryKey(DOM_FK_BOOK_ID, DOM_FK_ANTHOLOGY_ID)
            .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
            .addReference(TBL_ANTHOLOGY, DOM_FK_ANTHOLOGY_ID);

    /* ========================================================================================== */
    /** {@link #TBL_BOOKS_FTS} */
    public static final DomainDefinition DOM_AUTHOR_NAME = new DomainDefinition("author_name", TableInfo.TYPE_TEXT, NOT_NULL, "");

    /**
     * FULL representation of BOOKS_FTS table
     */
    public static final TableDefinition TBL_BOOKS_FTS =
            new TableDefinition(DB_TB_FTS_BOOKS)
            .addDomains(DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION, DOM_BOOK_NOTES,
                    DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE, DOM_BOOK_LOCATION, DOM_BOOK_ISBN)
            .setType(TableTypes.FTS3);

    /* ========================================================================================== */

    /**
     * cast()'d for sorting purposes in {@link BooklistBuilder}
     * so we can sort it numerically regardless of content
     */
    public static final DomainDefinition DOM_SERIES_NUM_FLOAT = new DomainDefinition("series_num_float", TableInfo.TYPE_REAL);

    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_LOANED_TO_SORT = new DomainDefinition("loaned_to_sort", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_AUTHOR_SORT = new DomainDefinition("author_sort", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_READ_STATUS = new DomainDefinition("read_status", TableInfo.TYPE_TEXT, NOT_NULL, "");
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_TITLE_LETTER = new DomainDefinition("title_letter", TableInfo.TYPE_TEXT);


    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_ADDED_DAY = new DomainDefinition("added_day", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_ADDED_MONTH = new DomainDefinition("added_month", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_ADDED_YEAR = new DomainDefinition("added_year", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_UPDATE_DAY = new DomainDefinition("update_day", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_UPDATE_MONTH = new DomainDefinition("update_month", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_LAST_UPDATE_YEAR = new DomainDefinition("update_year", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_READ_DAY = new DomainDefinition("read_day", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_READ_MONTH = new DomainDefinition("read_month", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_READ_YEAR = new DomainDefinition("read_year", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_ACQUIRED_DAY = new DomainDefinition("acquired_day", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_ACQUIRED_MONTH = new DomainDefinition("acquired_month", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_ACQUIRED_YEAR = new DomainDefinition("acquired_year", TableInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_PUBLISHED_MONTH = new DomainDefinition("publication_month", TableInfo.TYPE_INTEGER);
    /** sorting and grouping in {@link BooklistBuilder} */
    public static final DomainDefinition DOM_DATE_PUBLISHED_YEAR = new DomainDefinition("publication_year", TableInfo.TYPE_INTEGER);

    /** {@link BooklistBuilder} */
    public static final DomainDefinition DOM_SELECTED = new DomainDefinition("selected", TableInfo.TYPE_BOOLEAN, "default 0");
    /** {@link BooklistBuilder} */
    public static final DomainDefinition DOM_ABSOLUTE_POSITION = new DomainDefinition("absolute_position", TableInfo.TYPE_INTEGER, NOT_NULL, "");


    /* ========================================================================================== */

    /** {@link #TBL_BOOK_LIST_NODE_SETTINGS} */
    public static final DomainDefinition DOM_BL_NODE_ROW_KIND = new DomainDefinition("kind", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    /** {@link #TBL_BOOK_LIST_NODE_SETTINGS} {@link #TBL_ROW_NAVIGATOR}*/
    public static final DomainDefinition DOM_ROOT_KEY = new DomainDefinition("root_key", TableInfo.TYPE_TEXT);

    /**
     * FULL representation of BOOK_LIST_NODE_SETTINGS temp table. This IS definitive
     *
     * {@link BooklistBuilder}
     *
     * Example: a small set of books, sorted by 'Format' booklist style.
     *
     * _id	kind	root_key
     * 1	1	    a/273
     * 2	1	    a/302
     * 5	13	    fmt/Hardcover - Traycase
     */
    public static final TableDefinition TBL_BOOK_LIST_NODE_SETTINGS = new TableDefinition(TBL_BOOK_LIST_NAME + "_node_settings")
            .addDomains(DOM_PK_ID, DOM_BL_NODE_ROW_KIND, DOM_ROOT_KEY)
            .setAlias(ALIAS_BOOK_LIST_NODE_SETTINGS)
            .addIndex("ROOT_KIND", true, DOM_ROOT_KEY, DOM_BL_NODE_ROW_KIND)
            .addIndex("KIND_ROOT", true, DOM_BL_NODE_ROW_KIND, DOM_ROOT_KEY);


    /** {@link #TBL_BOOK_LIST} {@link #TBL_ROW_NAVIGATOR}*/
    public static final DomainDefinition DOM_BL_NODE_LEVEL = new DomainDefinition("level", TableInfo.TYPE_INTEGER, NOT_NULL, "");
    /** {@link #TBL_BOOK_LIST} */
    public static final DomainDefinition DOM_BL_BOOK_COUNT = new DomainDefinition("book_count", TableInfo.TYPE_INTEGER);
    /** {@link #TBL_BOOK_LIST} */
    public static final DomainDefinition DOM_BL_PRIMARY_SERIES_COUNT = new DomainDefinition("primary_series_count", TableInfo.TYPE_INTEGER);

    /**
     * Temporary table used to store flattened book lists
     *
     * {@link BooklistBuilder}
     *
     * _id	level	kind	book_count	primary_series_count	root_key	                book	format	                book_uuid	                        selected	read	title
     * 1	1	    13			                                fmt/Hardcover                       Hardcover	            1d872e0edbda2c86758d8fc889351716	0	        0
     * 2	2	    0	    1		                            fmt/Hardcover	            1617	Hardcover	            fc78e8ee6918e54547c2561986ccbec2	0	        0	Accelerando
     * 3	2	    0	    1		                            fmt/Hardcover	            1564	Hardcover	            47f6f13fd98594afcdca1ce71df9f23c	0	        0	Bad Ronald
     * ... snip...
     * 21	2	    0	    1		                            fmt/Hardcover	            1531	Hardcover	            3ac5caf178d9c61cbf8810d6233d0a2c	0	        0	Shadow Captain
     * 22	2	    0	    1		                            fmt/Hardcover	            1334	Hardcover	            9a37fe8a709e2d9ba8c8d0301c4c0903	0	        0	Tales from the Perilous Realm
     * 23	2	    0	    1		                            fmt/Hardcover	            1616	Hardcover	            61891a4d06bf821a7f270769671c13d9	0	        0	Toast
     * 24	2	    0	    1		                            fmt/Hardcover	            1332	Hardcover	            ab291216c5d0d6612b8e5e6d4484ffd5	0	        0	Unfinished Tales of NÃºmenor and Middle-Earth
     * 25	1	    13			                                fmt/Hardcover - Traycase		    Hardcover - Traycase	ae199f646b992c321f3b8ff04d0387ce	0	        0
     * 26	2	    0	    1	    	                        fmt/Hardcover - Traycase	1505	Hardcover - Traycase	228bff45e6d9cb025c2f73911ef6e4c4	0	        0	Night Lamp
     * 27	2	    0	    1	    	                        fmt/Hardcover - Traycase	1561	Hardcover - Traycase	e6db432cb841ea0cb82901e6b3e7c0b3	0	        0	Ports Of Call
     * 28	1	    13			                                fmt/Paperback		                Paperback	            a5df7be3d84070e0152494bbbfe99eb6	0	        0
     * ...snip...
     * 32	2	    0	    1	    	                        fmt/Paperback	            1589	Paperback	            649dfb4b92a05e15e66821e2933f1930	0	        0	Wild Thyme and Violets and Other Unpublished Stories
     * 33	1	    13			                                fmt/Paperback - Trade		        Paperback - Trade	    07b91229ccb1de81de8f0fe7cdbefb83	0       	0
     * 34	2	    0	    1	    	                        fmt/Paperback - Trade	    1251	Paperback - Trade	    74034e297d73785aee87c26d3050a8cb	0	        0	Annals of Klepsis
     */
    public static final TableDefinition TBL_BOOK_LIST = new TableDefinition(TBL_BOOK_LIST_NAME)
            .addDomains(DOM_PK_ID, DOM_BL_NODE_LEVEL, DOM_BL_NODE_ROW_KIND,
                    // Many others...this is a temp table created at runtime.
                    DOM_BL_BOOK_COUNT, DOM_BL_PRIMARY_SERIES_COUNT)
            .setType(TableTypes.Temporary)
            .setPrimaryKey(DOM_PK_ID)
            .setAlias(ALIAS_BOOK_LIST);


    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} navigation. */
    public static final DomainDefinition DOM_REAL_ROW_ID = new DomainDefinition("real_row_id", TableInfo.TYPE_INTEGER);
    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node visible?  */
    public static final DomainDefinition DOM_BL_NODE_VISIBLE = new DomainDefinition("visible", TableInfo.TYPE_INTEGER, "default 0");
    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node expanded ? */
    public static final DomainDefinition DOM_BL_NODE_EXPANDED = new DomainDefinition("expanded", TableInfo.TYPE_INTEGER, "default 0");
    /**
     * FULL representation of ROW_NAVIGATOR temp table. This IS definitive.
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
    public static final TableDefinition TBL_ROW_NAVIGATOR =
            new TableDefinition(TBL_BOOK_LIST_NAME + "_row_pos")
            .addDomains(DOM_PK_ID, DOM_REAL_ROW_ID, DOM_BL_NODE_LEVEL, DOM_BL_NODE_VISIBLE, DOM_BL_NODE_EXPANDED, DOM_ROOT_KEY)
            .setType(TableTypes.Temporary)
            .addReference(TBL_BOOK_LIST, DOM_REAL_ROW_ID)
            .setAlias(ALIAS_BOOK_LIST_ROW_POSITION);

    /**
     * Definition of ROW_NAVIGATOR_FLATTENED temp table
     */
    public static final TableDefinition TBL_ROW_NAVIGATOR_FLATTENED =
            new TableDefinition(TBL_BOOK_LIST_NAME + "_row_pos_flattened")
            .addDomains(DOM_PK_ID, DOM_FK_BOOK_ID)
            .setType(TableTypes.Temporary)
            .setAlias(ALIAS_BOOK_LIST_ROW_POSITION_FLATTENED);
}
