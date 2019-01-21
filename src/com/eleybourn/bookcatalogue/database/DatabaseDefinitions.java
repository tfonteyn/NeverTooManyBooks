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
import com.eleybourn.bookcatalogue.database.definitions.ColumnInfo;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition.TableTypes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static definitions of database objects.
 * This is a *mostly* complete representation of the application database.
 *
 * TODO: TBL_BOOKS needs completing of domain list + Collated indexes need to be done manually.
 *
 * @author Philip Warner
 */
public final class DatabaseDefinitions {

    /** A collection of all tables, added in order so interdependency's work out */
    static final Map<String, TableDefinition> ALL_TABLES = new LinkedHashMap<>();

    /*
     * Basic table definitions. Split from where we add domains etc to avoid forward code
     * references.
     */
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF =
            new TableDefinition("bookshelf").setAlias("bsh");
    /** Basic table definition. */
    public static final TableDefinition TBL_AUTHORS =
            new TableDefinition("authors").setAlias("a");
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKS =
            new TableDefinition("books").setAlias("b");
    /** Basic table definition. */
    public static final TableDefinition TBL_SERIES =
            new TableDefinition("series").setAlias("s");
    /** Basic table definition. */
    @SuppressWarnings("WeakerAccess")
    public static final TableDefinition TBL_TOC_ENTRIES =
            new TableDefinition("anthology").setAlias("an");

    /** link table. */
    public static final TableDefinition TBL_BOOK_BOOKSHELF =
            new TableDefinition("book_bookshelf_weak").setAlias("bbsh");
    /** link table. */
    public static final TableDefinition TBL_BOOK_AUTHOR =
            new TableDefinition("book_author").setAlias("ba");
    /** link table. */
    public static final TableDefinition TBL_BOOK_SERIES =
            new TableDefinition("book_series").setAlias("bs");
    /** link table. */
    public static final TableDefinition TBL_BOOK_LOANEE =
            new TableDefinition("loan").setAlias("l");
    /** link table. */
    @SuppressWarnings("WeakerAccess")
    public static final TableDefinition TBL_BOOK_TOC_ENTRIES =
            new TableDefinition("book_anthology").setAlias("bat");

    /** app table. */
    static final TableDefinition TBL_BOOKLIST_STYLES =
            new TableDefinition("book_list_styles").setAlias("bls");

    /** Base Name of BOOK_LIST-related tables; should NOT be added to {@link #ALL_TABLES}. */
    private static final String DB_TB_BOOK_LIST_NAME = "book_list_tmp";

    /** app table. */
    public static final TableDefinition TBL_BOOK_LIST_NODE_SETTINGS =
            new TableDefinition(DB_TB_BOOK_LIST_NAME + "_node_settings")
                    .setAlias("blns");

    static {
        ALL_TABLES.put(TBL_BOOKSHELF.getName(), TBL_BOOKSHELF);
        ALL_TABLES.put(TBL_AUTHORS.getName(), TBL_AUTHORS);
        ALL_TABLES.put(TBL_BOOKS.getName(), TBL_BOOKS);
        ALL_TABLES.put(TBL_SERIES.getName(), TBL_SERIES);
        ALL_TABLES.put(TBL_TOC_ENTRIES.getName(), TBL_TOC_ENTRIES);

        ALL_TABLES.put(TBL_BOOK_BOOKSHELF.getName(), TBL_BOOK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOK_AUTHOR.getName(), TBL_BOOK_AUTHOR);
        ALL_TABLES.put(TBL_BOOK_SERIES.getName(), TBL_BOOK_SERIES);
        ALL_TABLES.put(TBL_BOOK_TOC_ENTRIES.getName(), TBL_BOOK_TOC_ENTRIES);
        ALL_TABLES.put(TBL_BOOK_LOANEE.getName(), TBL_BOOK_LOANEE);

        ALL_TABLES.put(TBL_BOOKLIST_STYLES.getName(), TBL_BOOKLIST_STYLES);

        ALL_TABLES.put(TBL_BOOK_LIST_NODE_SETTINGS.getName(), TBL_BOOK_LIST_NODE_SETTINGS);
    }

    /** Should NOT be added to {@link #ALL_TABLES}. */
    public static final TableDefinition TBL_BOOK_LIST =
            new TableDefinition(DB_TB_BOOK_LIST_NAME)
                    //RELEASE Make sure is TEMPORARY
                    .setType(TableTypes.Temporary)
                    .setAlias("bl");

    /** Should NOT be added to {@link #ALL_TABLES}. */
    public static final TableDefinition TBL_ROW_NAVIGATOR =
            new TableDefinition(DB_TB_BOOK_LIST_NAME + "_row_pos")
                    //RELEASE Make sure is TEMPORARY
                    .setType(TableTypes.Temporary)
                    .setAlias("blrp");

    /**
     * Definition of ROW_NAVIGATOR_FLATTENED temp table.
     * Should NOT be added to {@link #ALL_TABLES}.
     *
     * This table should always be created without column constraints applied,
     * with the exception of the "_id" primary key autoincrement
     */
    public static final TableDefinition TBL_ROW_NAVIGATOR_FLATTENED =
            new TableDefinition(DB_TB_BOOK_LIST_NAME + "_row_pos_flattened")
                    //RELEASE Make sure is TEMPORARY
                    .setType(TableTypes.Temporary)
                    .setAlias("blrpf");

    /** full text search; should NOT be added to {@link #ALL_TABLES}. */
    public static final TableDefinition TBL_BOOKS_FTS =
            new TableDefinition("books_fts")
                    .setType(TableTypes.FTS3);

    /**
     * The original code was a bit vague on the exact meaning of the 'anthology mask'.
     * So this information was mainly written for myself.
     * <p>
     * Original, it looked like this was the meaning:
     * 0%00 == book by single author
     * 0%01 == anthology by one author
     * 0%11 == anthology by multiple authors.
     * which would mean it missed books with a single story, but multiple authors; e.g. the 0%10
     * <p>
     * A more complete definition below.
     * <p>
     * {@link #DOM_BOOK_ANTHOLOGY_BITMASK}
     * <p>
     * 0%00 = contains one 'work' and is written by a single author.
     * 0%01 = multiple 'work' and is written by a single author (it's an anthology from ONE author)
     * 0%10 = multiple authors cooperating on a single 'work'
     * 0%11 = multiple authors and multiple 'work's (it's an anthology from multiple author)
     * <p>
     * or in other words:
     * * bit 0 indicates if a book has one (bit unset) or multiple (bit set) works
     * * bit 1 indicates if a book has one (bit unset) or multiple (bit set) authors.
     * <p>
     * Having said all that, the 0%10 should not actually occur, as this is a simple case of
     * collaborating authors which is covered without the use of
     * {@link #DOM_BOOK_ANTHOLOGY_BITMASK}
     * Which of course brings it back full-circle to the original and correct meaning.
     * <p>
     * Leaving all this here, as it will remind myself (and maybe others) of the 'missing' bit.
     * <p>
     * Think about actually updating the column to 0%10 as a cache for a book having multiple
     * authors without the need to 'count' them in the book_author table ?
     */
    //public static final int DOM_BOOK_SINGLE_AUTHOR_SINGLE_WORK = 0;
    public static final int DOM_BOOK_WITH_MULTIPLE_WORKS = 1;
    public static final int DOM_BOOK_WITH_MULTIPLE_AUTHORS = 1 << 1;

    /*
     * Domain definitions.
     *
     * NOTE!!! Because Java String comparisons are not case-insensitive, it is
     * important that ALL these fields 'name' be listed in LOWER CASE.
     */

    /** FTS primary key. */
    public static final DomainDefinition DOM_PK_DOCID = new DomainDefinition("docid");
    /** primary key. */
    public static final DomainDefinition DOM_PK_ID = new DomainDefinition("_id");

    /* ========================================================================================== */

    /** foreign key. */
    public static final DomainDefinition DOM_FK_AUTHOR_ID =
            new DomainDefinition("author", ColumnInfo.TYPE_INTEGER)
                    .references(TBL_AUTHORS, "ON DELETE CASCADE ON UPDATE CASCADE");

    /** foreign key. */
    public static final DomainDefinition DOM_FK_BOOKSHELF_ID =
            new DomainDefinition("bookshelf", ColumnInfo.TYPE_INTEGER)
                    .references(TBL_BOOKSHELF, "ON DELETE CASCADE ON UPDATE CASCADE");

    /** foreign key. */
    public static final DomainDefinition DOM_FK_BOOK_ID =
            new DomainDefinition("book", ColumnInfo.TYPE_INTEGER)
                    .references(TBL_BOOKS, "ON DELETE CASCADE ON UPDATE CASCADE");

    /** foreign key. */
    public static final DomainDefinition DOM_FK_SERIES_ID =
            new DomainDefinition("series_id", ColumnInfo.TYPE_INTEGER)
                    .references(TBL_SERIES, "ON DELETE CASCADE ON UPDATE CASCADE");

    /** foreign key. */
    static final DomainDefinition DOM_FK_TOC_ENTRY_ID =
            new DomainDefinition("anthology", ColumnInfo.TYPE_INTEGER)
                    .references(TBL_TOC_ENTRIES, "ON DELETE CASCADE ON UPDATE CASCADE");

    /* ========================================================================================== */

    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final DomainDefinition DOM_TITLE =
            new DomainDefinition("title", ColumnInfo.TYPE_TEXT, true);

    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final DomainDefinition DOM_FIRST_PUBLICATION =
            new DomainDefinition("first_publication", ColumnInfo.TYPE_DATE, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_LAST_UPDATE_DATE =
            new DomainDefinition("last_update_date", ColumnInfo.TYPE_DATETIME, true)
                    .setDefault("current_timestamp");

    /** {@link #TBL_BOOKLIST_STYLES} java.util.UUID value stored as a string. */
    static final DomainDefinition DOM_UUID =
            new DomainDefinition("uuid", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /* ========================================================================================== */

    /** {@link #TBL_AUTHORS}. */
    public static final DomainDefinition DOM_AUTHOR_FAMILY_NAME =
            new DomainDefinition("family_name", ColumnInfo.TYPE_TEXT, true);

    /** {@link #TBL_AUTHORS}. */
    public static final DomainDefinition DOM_AUTHOR_GIVEN_NAMES =
            new DomainDefinition("given_names", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_AUTHORS}. */
    public static final DomainDefinition DOM_AUTHOR_IS_COMPLETE =
            new DomainDefinition("author_complete", ColumnInfo.TYPE_BOOLEAN, true)
                    .setDefault(0);

    /** "FamilyName, GivenName". */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED =
            new DomainDefinition("author_formatted", ColumnInfo.TYPE_TEXT, true);

    /** "GivenName FamilyName". */
    public static final DomainDefinition DOM_AUTHOR_FORMATTED_GIVEN_FIRST =
            new DomainDefinition("author_formatted_given_first", ColumnInfo.TYPE_TEXT, true);

    static {
        TBL_AUTHORS.addDomains(DOM_PK_ID,
                               DOM_AUTHOR_FAMILY_NAME,
                               DOM_AUTHOR_GIVEN_NAMES,
                               DOM_AUTHOR_IS_COMPLETE)
                   .setPrimaryKey(DOM_PK_ID)
                   .addIndex(DOM_AUTHOR_FAMILY_NAME.name, false, DOM_AUTHOR_FAMILY_NAME)
                   .addIndex(DOM_AUTHOR_GIVEN_NAMES.name, false, DOM_AUTHOR_GIVEN_NAMES);
    }

    /* ========================================================================================== */

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_UUID =
            new DomainDefinition("book_uuid", ColumnInfo.TYPE_TEXT, true)
                    .setDefault("(lower(hex(randomblob(16))))");

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_ISBN =
            new DomainDefinition("isbn", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PUBLISHER =
            new DomainDefinition("publisher", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_DATE_PUBLISHED =
            new DomainDefinition("date_published", ColumnInfo.TYPE_DATE, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_EDITION_BITMASK =
            new DomainDefinition("edition_bm", ColumnInfo.TYPE_INTEGER, true)
                    .setDefault(0);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_ANTHOLOGY_BITMASK =
            new DomainDefinition("anthology", ColumnInfo.TYPE_INTEGER, true)
                    .setDefault(0);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRICE_LISTED =
            new DomainDefinition("list_price", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRICE_LISTED_CURRENCY =
            new DomainDefinition("list_price_currency", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRICE_PAID =
            new DomainDefinition("price_paid", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PRICE_PAID_CURRENCY =
            new DomainDefinition("price_paid_currency", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_DATE_ACQUIRED =
            new DomainDefinition("date_acquired", ColumnInfo.TYPE_DATE, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_FORMAT =
            new DomainDefinition("format", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_GENRE =
            new DomainDefinition("genre", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_LANGUAGE =
            new DomainDefinition("language", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_LOCATION =
            new DomainDefinition("location", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_PAGES =
            new DomainDefinition("pages", ColumnInfo.TYPE_INTEGER);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_READ =
            new DomainDefinition("read", ColumnInfo.TYPE_BOOLEAN, true)
                    .setDefault(0);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_READ_START =
            new DomainDefinition("read_start", ColumnInfo.TYPE_DATE, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_READ_END =
            new DomainDefinition("read_end", ColumnInfo.TYPE_DATE, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_SIGNED =
            new DomainDefinition("signed", ColumnInfo.TYPE_BOOLEAN, true)
                    .setDefault(0);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_RATING =
            new DomainDefinition("rating", ColumnInfo.TYPE_REAL, true)
                    .setDefault(0);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_DESCRIPTION =
            new DomainDefinition("description", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_NOTES =
            new DomainDefinition("notes", ColumnInfo.TYPE_TEXT, true)
                    .setDefaultEmptyString();

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_ISFDB_ID =
            new DomainDefinition("isfdb_book_id", ColumnInfo.TYPE_INTEGER);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_LIBRARY_THING_ID =
            new DomainDefinition("lt_book_id", ColumnInfo.TYPE_INTEGER);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_GOODREADS_BOOK_ID =
            new DomainDefinition("goodreads_book_id", ColumnInfo.TYPE_INTEGER);

    /** {@link #TBL_BOOKS}. */
    public static final DomainDefinition DOM_BOOK_GOODREADS_LAST_SYNC_DATE =
            new DomainDefinition("last_goodreads_sync_date", ColumnInfo.TYPE_DATE)
                    .setDefault("'0000-00-00'");

    /** {@link #TBL_BOOKS} added to the collection. */
    public static final DomainDefinition DOM_BOOK_DATE_ADDED =
            new DomainDefinition("date_added", ColumnInfo.TYPE_DATETIME, true)
                    .setDefault("current_timestamp");

    static {
        TBL_BOOKS.addDomains(DOM_PK_ID,
                             // book data
                             DOM_TITLE,
                             DOM_BOOK_ISBN,
                             DOM_BOOK_PUBLISHER,
                             DOM_BOOK_DATE_PUBLISHED,
                             DOM_FIRST_PUBLICATION,

                             DOM_BOOK_PRICE_LISTED,
                             DOM_BOOK_PRICE_LISTED_CURRENCY,

                             DOM_BOOK_ANTHOLOGY_BITMASK,
                             DOM_BOOK_FORMAT,
                             DOM_BOOK_GENRE,
                             DOM_BOOK_LANGUAGE,
                             DOM_BOOK_PAGES,

                             DOM_BOOK_DESCRIPTION,

                             // personal data
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
                             DOM_BOOK_NOTES,

                             // external id/data
                             DOM_BOOK_ISFDB_ID,
                             DOM_BOOK_LIBRARY_THING_ID,
                             DOM_BOOK_GOODREADS_BOOK_ID,
                             DOM_BOOK_GOODREADS_LAST_SYNC_DATE,

                             // internal data
                             DOM_BOOK_UUID,
                             DOM_BOOK_DATE_ADDED,
                             DOM_LAST_UPDATE_DATE)
                 .setPrimaryKey(DOM_PK_ID)
                 .addIndex(DOM_TITLE.name, false, DOM_TITLE)
                 .addIndex(DOM_BOOK_ISBN.name, false, DOM_BOOK_ISBN)
                 .addIndex(DOM_BOOK_PUBLISHER.name, false, DOM_BOOK_PUBLISHER)
                 .addIndex(DOM_BOOK_UUID.name, true, DOM_BOOK_UUID)
                 .addIndex(DOM_BOOK_GOODREADS_BOOK_ID.name, false, DOM_BOOK_GOODREADS_BOOK_ID)
                 .addIndex(DOM_BOOK_ISFDB_ID.name, false, DOM_BOOK_ISFDB_ID);
    }

    /* ========================================================================================== */

    /** {@link #TBL_SERIES). */
    public static final DomainDefinition DOM_SERIES_NAME =
            new DomainDefinition("series_name", ColumnInfo.TYPE_TEXT, true);

    /** {@link #TBL_SERIES}. */
    public static final DomainDefinition DOM_SERIES_IS_COMPLETE =
            new DomainDefinition("series_complete", ColumnInfo.TYPE_BOOLEAN, true)
                    .setDefault(0);

    /** {@link #TBL_SERIES). */
    public static final DomainDefinition DOM_SERIES_FORMATTED =
            new DomainDefinition("series_formatted", ColumnInfo.TYPE_TEXT, true);

    static {
        TBL_SERIES.addDomains(DOM_PK_ID,
                              DOM_SERIES_NAME,
                              DOM_SERIES_IS_COMPLETE)
                  .setPrimaryKey(DOM_PK_ID)
                  .addIndex("id", true, DOM_PK_ID);
    }


    /* ========================================================================================== */

    /** {@link #TBL_BOOKSHELF). */
    public static final DomainDefinition DOM_BOOKSHELF =
            new DomainDefinition("bookshelf", ColumnInfo.TYPE_TEXT, true);

    static {
        TBL_BOOKSHELF.addDomains(DOM_PK_ID,
                                 DOM_BOOKSHELF)
                     .setPrimaryKey(DOM_PK_ID)
                     .addIndex(DOM_BOOKSHELF.name, true, DOM_BOOKSHELF);
    }

    /* ========================================================================================== */

    static {
        TBL_TOC_ENTRIES.addDomains(DOM_PK_ID,
                                   DOM_FK_AUTHOR_ID,
                                   DOM_TITLE,
                                   DOM_FIRST_PUBLICATION)
                       .setPrimaryKey(DOM_PK_ID)
                       .addReference(TBL_AUTHORS, DOM_FK_AUTHOR_ID)
                       .addIndex(DOM_FK_AUTHOR_ID.name,false,DOM_FK_AUTHOR_ID)
                       .addIndex(DOM_TITLE.name,false,DOM_TITLE)
                       .addIndex("pk",true,DOM_FK_AUTHOR_ID, DOM_TITLE);
    }

    /* ========================================================================================== */
    /* ========================================================================================== */

    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final DomainDefinition DOM_BOOK_AUTHOR_POSITION =
            new DomainDefinition("author_position", ColumnInfo.TYPE_INTEGER, true);

    static {
        TBL_BOOK_AUTHOR.addDomains(DOM_FK_BOOK_ID,
                                   DOM_FK_AUTHOR_ID,
                                   DOM_BOOK_AUTHOR_POSITION)
                       .setPrimaryKey(DOM_FK_BOOK_ID, DOM_BOOK_AUTHOR_POSITION)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
                       .addReference(TBL_AUTHORS, DOM_FK_AUTHOR_ID)
                       .addIndex(DOM_FK_AUTHOR_ID.name, true,
                                 DOM_FK_AUTHOR_ID,
                                 DOM_FK_BOOK_ID)
                       .addIndex(DOM_FK_BOOK_ID.name, true,
                                 DOM_FK_BOOK_ID,
                                 DOM_FK_AUTHOR_ID);
    }

    /* ========================================================================================== */

    /** {@link #TBL_BOOK_SERIES}. */
    public static final DomainDefinition DOM_BOOK_SERIES_NUM =
            new DomainDefinition("series_num", ColumnInfo.TYPE_TEXT);

    /**
     * {@link #TBL_BOOK_SERIES}.
     * The Series position is the order the series show up in a book. Particularly important
     * for "primary series" and in lists where 'all' series are shown.
     */
    public static final DomainDefinition DOM_BOOK_SERIES_POSITION =
            new DomainDefinition("series_position", ColumnInfo.TYPE_INTEGER, true);

    static {
        TBL_BOOK_SERIES.addDomains(DOM_FK_BOOK_ID,
                                   DOM_FK_SERIES_ID,
                                   DOM_BOOK_SERIES_NUM,
                                   DOM_BOOK_SERIES_POSITION)
                       .setPrimaryKey(DOM_FK_BOOK_ID, DOM_BOOK_SERIES_POSITION)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
                       .addReference(TBL_SERIES, DOM_FK_SERIES_ID)
                       .addIndex(DOM_FK_SERIES_ID.name, true,
                                 DOM_FK_SERIES_ID,
                                 DOM_FK_BOOK_ID,
                                 DOM_BOOK_SERIES_NUM)
                       .addIndex(DOM_FK_BOOK_ID.name, true,
                                 DOM_FK_BOOK_ID,
                                 DOM_FK_SERIES_ID,
                                 DOM_BOOK_SERIES_NUM);
    }

    /* ========================================================================================== */

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final DomainDefinition DOM_LOANEE =
            new DomainDefinition("loaned_to", ColumnInfo.TYPE_TEXT, true);

    static {
        TBL_BOOK_LOANEE.addDomains(DOM_PK_ID,
                                   DOM_FK_BOOK_ID,
                                   DOM_LOANEE)
                       .setPrimaryKey(DOM_PK_ID)
                       .addIndex(DOM_FK_BOOK_ID.name, true, DOM_FK_BOOK_ID)
                       .addReference(TBL_BOOKS, DOM_FK_BOOK_ID);
    }

    /* ========================================================================================== */

    static {
        TBL_BOOK_BOOKSHELF.addDomains(DOM_FK_BOOK_ID,
                                      DOM_FK_BOOKSHELF_ID)
                          .setPrimaryKey(DOM_FK_BOOK_ID, DOM_FK_BOOKSHELF_ID)
                          .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
                          .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF_ID)
                          .addIndex(DOM_FK_BOOK_ID.name, false, DOM_FK_BOOK_ID)
                          .addIndex(DOM_FK_BOOKSHELF_ID.name, false, DOM_FK_BOOKSHELF_ID);
    }

    /* ========================================================================================== */

    /** {@link #TBL_BOOK_TOC_ENTRIES}. */
    static final DomainDefinition DOM_BOOK_TOC_ENTRY_POSITION =
            new DomainDefinition("position", ColumnInfo.TYPE_INTEGER, true);

    static {
        TBL_BOOK_TOC_ENTRIES.addDomains(DOM_FK_BOOK_ID,
                                        DOM_FK_TOC_ENTRY_ID,
                                        DOM_BOOK_TOC_ENTRY_POSITION)
                            .setPrimaryKey(DOM_FK_BOOK_ID, DOM_FK_TOC_ENTRY_ID)
                            .addReference(TBL_BOOKS, DOM_FK_BOOK_ID)
                            .addReference(TBL_TOC_ENTRIES, DOM_FK_TOC_ENTRY_ID)
                            .addIndex(DOM_FK_TOC_ENTRY_ID.name, false, DOM_FK_TOC_ENTRY_ID)
                            .addIndex(DOM_FK_BOOK_ID.name, false, DOM_FK_BOOK_ID);

    }

    /* ========================================================================================== */
    /* ========================================================================================== */

    static {
        TBL_BOOKLIST_STYLES.addDomains(DOM_PK_ID,
                                       DOM_UUID)
                           .addIndex(DOM_UUID.name, true, DOM_UUID);
    }

    /* ========================================================================================== */

    /**
     * {@link #TBL_BOOKS_FTS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    static final DomainDefinition DOM_FTS_AUTHOR_NAME =
            new DomainDefinition("author_name", ColumnInfo.TYPE_TEXT, true);

    /*
     * reminder: FTS columns don't need a type nor constraints.
     * https://sqlite.org/fts3.html
     */
    static {
        TBL_BOOKS_FTS.addDomains(DOM_FTS_AUTHOR_NAME,
                                 DOM_TITLE,
                                 DOM_BOOK_DESCRIPTION,
                                 DOM_BOOK_NOTES,
                                 DOM_BOOK_PUBLISHER,
                                 DOM_BOOK_GENRE,
                                 DOM_BOOK_LOCATION,
                                 DOM_BOOK_ISBN);
    }

    /* ========================================================================================== */

    /**
     * Series number, cast()'d for sorting purposes in {@link BooklistBuilder}
     * so we can sort it numerically regardless of content.
     */
    public static final DomainDefinition DOM_SERIES_NUM_FLOAT =
            new DomainDefinition("series_num_float", ColumnInfo.TYPE_REAL);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_LOANED_TO_SORT =
            new DomainDefinition("loaned_to_sort", ColumnInfo.TYPE_INTEGER, true);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_AUTHOR_SORT =
            new DomainDefinition("author_sort", ColumnInfo.TYPE_TEXT, true);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_READ_STATUS =
            new DomainDefinition("read_status", ColumnInfo.TYPE_TEXT, true);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_TITLE_LETTER =
            new DomainDefinition("title_letter", ColumnInfo.TYPE_TEXT);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_ADDED_DAY =
            new DomainDefinition("added_day", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_ADDED_MONTH =
            new DomainDefinition("added_month", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_ADDED_YEAR =
            new DomainDefinition("added_year", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_UPDATE_DAY =
            new DomainDefinition("upd_day", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_UPDATE_MONTH =
            new DomainDefinition("upd_month", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_LAST_UPDATE_YEAR =
            new DomainDefinition("upd_year", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_READ_DAY =
            new DomainDefinition("read_day", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_READ_MONTH =
            new DomainDefinition("read_month", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_READ_YEAR =
            new DomainDefinition("read_year", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_ACQUIRED_DAY =
            new DomainDefinition("acq_day", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_ACQUIRED_MONTH =
            new DomainDefinition("acq_month", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_ACQUIRED_YEAR =
            new DomainDefinition("acq_year", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_FIRST_PUBLICATION_MONTH =
            new DomainDefinition("first_pub_month", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_FIRST_PUBLICATION_YEAR =
            new DomainDefinition("first_pub_year", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_PUBLISHED_MONTH =
            new DomainDefinition("pub_month", ColumnInfo.TYPE_INTEGER);

    /** sorting and grouping in {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_DATE_PUBLISHED_YEAR =
            new DomainDefinition("pub_year", ColumnInfo.TYPE_INTEGER);


    /** {@link BooklistBuilder} the 'selected' book, i.e. the one to scroll back into view. */
    public static final DomainDefinition DOM_SELECTED =
            new DomainDefinition("selected", ColumnInfo.TYPE_BOOLEAN)
                    .setDefault(0);

    /** {@link BooklistBuilder}. */
    public static final DomainDefinition DOM_ABSOLUTE_POSITION =
            new DomainDefinition("abs_pos", ColumnInfo.TYPE_INTEGER, true);

    /* ========================================================================================== */

    /** {@link #TBL_BOOK_LIST_NODE_SETTINGS}. */
    public static final DomainDefinition DOM_BL_NODE_ROW_KIND =
            new DomainDefinition("kind", ColumnInfo.TYPE_INTEGER, true);

    /** {@link #TBL_BOOK_LIST_NODE_SETTINGS} {@link #TBL_ROW_NAVIGATOR}. */
    public static final DomainDefinition DOM_ROOT_KEY =
            new DomainDefinition("root_key", ColumnInfo.TYPE_TEXT);

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
    static {
        TBL_BOOK_LIST_NODE_SETTINGS
                .addDomains(DOM_PK_ID,
                            DOM_BL_NODE_ROW_KIND,
                            DOM_ROOT_KEY)
                .addIndex("ROOT_KIND", true,
                          DOM_ROOT_KEY,
                          DOM_BL_NODE_ROW_KIND)
                .addIndex("KIND_ROOT", true,
                          DOM_BL_NODE_ROW_KIND,
                          DOM_ROOT_KEY);
    }


    /** {@link #TBL_BOOK_LIST} {@link #TBL_ROW_NAVIGATOR}. */
    public static final DomainDefinition DOM_BL_NODE_LEVEL =
            new DomainDefinition("level", ColumnInfo.TYPE_INTEGER, true);

    /** {@link #TBL_BOOK_LIST}. */
    public static final DomainDefinition DOM_BL_BOOK_COUNT =
            new DomainDefinition("book_count", ColumnInfo.TYPE_INTEGER);

    /** {@link #TBL_BOOK_LIST}. */
    public static final DomainDefinition DOM_BL_PRIMARY_SERIES_COUNT =
            new DomainDefinition("primary_series_count", ColumnInfo.TYPE_INTEGER);

    /*
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
     *
     * This table should always be created without column constraints applied,
     * with the exception of the "_id" primary key autoincrement
     */
    static {
        // PARTIAL list of domains... this is a temp table created at runtime.
        TBL_BOOK_LIST.addDomains(DOM_PK_ID,
                                 DOM_BL_NODE_LEVEL,
                                 DOM_BL_NODE_ROW_KIND,
                                 DOM_BL_BOOK_COUNT,
                                 DOM_BL_PRIMARY_SERIES_COUNT)
                     .setPrimaryKey(DOM_PK_ID);
    }

    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} navigation. */
    public static final DomainDefinition DOM_REAL_ROW_ID =
            new DomainDefinition("real_row_id", ColumnInfo.TYPE_INTEGER);

    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node visible. */
    public static final DomainDefinition DOM_BL_NODE_VISIBLE =
            new DomainDefinition("visible", ColumnInfo.TYPE_INTEGER)
                    .setDefault(0);

    /** {@link #TBL_ROW_NAVIGATOR} {@link BooklistBuilder} is node expanded. */
    public static final DomainDefinition DOM_BL_NODE_EXPANDED =
            new DomainDefinition("expanded", ColumnInfo.TYPE_INTEGER)
                    .setDefault(0);

    /*
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
    static {
        TBL_ROW_NAVIGATOR.addDomains(DOM_PK_ID,
                                     DOM_REAL_ROW_ID,
                                     DOM_BL_NODE_LEVEL,
                                     DOM_BL_NODE_VISIBLE,
                                     DOM_BL_NODE_EXPANDED,
                                     DOM_ROOT_KEY);

        // do NOT add the reference here. It will be added *after* cloning in BooklistBuilder.
        //.addReference(TBL_BOOK_LIST, DOM_REAL_ROW_ID);
    }

    /*
     * Definition of ROW_NAVIGATOR_FLATTENED temp table.
     *
     * This table should always be created without column constraints applied,
     * with the exception of the "_id" primary key autoincrement
     */
    static {
        TBL_ROW_NAVIGATOR_FLATTENED.addDomains(DOM_PK_ID,
                                               DOM_FK_BOOK_ID);
    }

    private DatabaseDefinitions() {
    }
}
