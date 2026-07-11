/*
 * @Copyright 2018-2026 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.booklist.style.ScreenLayout;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale;
import com.hardbacknutter.nevertoomanybooks.citations.CitationType;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

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
 *  <li>{@link #DOM_DATE_ADDED__UTC}</li>
 *  <li>{@link #DOM_LAST_UPDATED__UTC}</li>
 *  <li>{@link #DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC}</li>
 *  <li>{@link #DOM_STRIP_INFO_LAST_SYNC__UTC}</li>
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
     * {@link #TBL_TAG_MAPPINGS},
     * <p>
     * basic user data tables
     * {@link #TBL_BOOKSHELF},
     * {@link #TBL_BOOKSHELF_FILTERS}
     * {@link #TBL_AUTHORS},
     * {@link #TBL_SERIES},
     * {@link #TBL_PUBLISHERS},
     * {@link #TBL_BOOKS},
     * {@link #TBL_TOC_ENTRIES},
     * {@link #TBL_IDENTIFIERS},
     * {@link #TBL_TAGS},
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
     * {@link #TBL_BOOK_TAG}
     * <p>
     * {@link #TBL_BOOK_IDENTIFIER}
     * {@link #TBL_AUTHOR_IDENTIFIER}
     * {@link #TBL_SERIES_IDENTIFIER}
     * <p>
     * {@link #TBL_SERIES_PUBLICATION_FREQUENCY}
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
    @SuppressWarnings("PublicStaticCollectionField")
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
    /** Basic table definition. */
    public static final TableDefinition TBL_IDENTIFIERS;
    /** Basic table definition. */
    public static final TableDefinition TBL_TAGS;

    /** link table. */
    public static final TableDefinition TBL_BOOK_BOOKSHELF;
    /** link table. */
    public static final TableDefinition TBL_BOOK_AUTHOR;
    /** link table. */
    public static final TableDefinition TBL_BOOK_SERIES;
    /** link table. */
    public static final TableDefinition TBL_BOOK_PUBLISHER;
    /** LEFT JOIN table. */
    public static final TableDefinition TBL_BOOK_LOANEE;
    /** link table. */
    public static final TableDefinition TBL_BOOK_TOC_ENTRIES;
    /** link table. */
    public static final TableDefinition TBL_BOOK_IDENTIFIER;
    /** link table. */
    public static final TableDefinition TBL_BOOK_TAG;

    /** link table. */
    public static final TableDefinition TBL_AUTHOR_IDENTIFIER;
    /** link table. */
    public static final TableDefinition TBL_SERIES_IDENTIFIER;
    /** LEFT JOIN table. */
    public static final TableDefinition TBL_SERIES_PUBLICATION_FREQUENCY;

    /** Map alternative names for Authors. */
    public static final TableDefinition TBL_PSEUDONYM_AUTHOR;
    /** Map site tags to local Tags. */
    public static final TableDefinition TBL_TAG_MAPPINGS;

    /**
     * IMPORTANT: this table can be joined by {@link DBKey#LANGUAGE}
     * <strong>AND</strong> {@link DBKey#TRANSLATION_ORIGINAL_LANGUAGE}.
     * Each join <strong>MUST</strong> use a unique alias instead
     * of the default one.
     */
    public static final TableDefinition TBL_LANG_MAPPINGS;
    public static final String ALIAS_LANG_MAPPINGS_LANGUAGE;
    public static final String ALIAS_LANG_MAPPINGS_ORIGINAL_LANGUAGE;

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

    /** A bridge to the stripinfo.be website. Site specific imported data. */
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
    /** Foreign key. */
    public static final Domain DOM_FK_IDENTIFIER;
    /** Foreign key. */
    public static final Domain DOM_FK_TAG;
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
    public static final Domain DOM_BOOKSHELF_NAMES_AS_CSV;
    /** Saved booklist adapter position of current top row. */
    public static final Domain DOM_BOOKSHELF_BL_TOP_POS;
    /** Saved booklist adapter top row offset from view top. */
    public static final Domain DOM_BOOKSHELF_BL_TOP_OFFSET;

    /** {@link #TBL_BOOKSHELF_FILTERS}. */
    public static final Domain DOM_BOOKSHELF_FILTER_NAME;
    public static final Domain DOM_BOOKSHELF_FILTER_VALUE;

    /** {@link #TBL_IDENTIFIERS}. */
    public static final Domain DOM_IDENTIFIER_KEY;
    public static final Domain DOM_IDENTIFIER_TYPE;
    public static final Domain DOM_IDENTIFIER_NAME;
    public static final Domain DOM_IDENTIFIER_ENTITY;
    public static final Domain DOM_IDENTIFIER_WIKIDATA_CLAIM;
    public static final Domain DOM_IDENTIFIER_SITE_URL;
    public static final Domain DOM_IDENTIFIER_URI;

    /**
     * {@link #TBL_BOOK_IDENTIFIER},
     * {@link #TBL_AUTHOR_IDENTIFIER},
     * {@link #TBL_SERIES_IDENTIFIER}.
     */
    public static final Domain DOM_IDENTIFIER_SID;

    /** {@link #TBL_TAGS}. */
    public static final Domain DOM_TAG;
    /** {@link #TBL_TAG_MAPPINGS}. */
    public static final Domain DOM_TAG_MAPPING;

    public static final Domain DOM_LANG_USER_ISO3;
    public static final Domain DOM_LANG_ISO3;
    public static final Domain DOM_LANG_DISPLAY_NAME;

    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_FAMILY_NAME;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_FAMILY_NAME_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_GIVEN_NAMES;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_GIVEN_NAMES_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_BIRTH_DATE;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_DEATH_DATE;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_PICTURE_UUID;

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
    public static final Domain DOM_AUTHOR_FORMATTED;

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
    public static final Domain DOM_PUBLISHER_NAMES_AS_CSV;


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
     * {@link #TBL_BOOKS}.
     * For translated books, the title in the original language.
     */
    public static final Domain DOM_TRANSLATION_ORIGINAL_TITLE;
    /**
     * {@link #TBL_BOOKS}.
     * For translated books, the language of the original.
     */
    public static final Domain DOM_TRANSLATION_ORIGINAL_LANGUAGE;

    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final Domain DOM_DATE_FIRST_PUBLICATION;

    /** {@link #TBL_BOOKS} added to the collection. */
    public static final Domain DOM_DATE_ADDED__UTC;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_LAST_UPDATED__UTC;

    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_AUTO_UPDATE;

    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_ISBN;
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
     * Note this is a <strong>TEXT</strong> field. See {@link DBKey#PAGES}.
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

    /**
     * {@link #TBL_STRIPINFO_COLLECTION}.
     * Foreign key with {@link #TBL_BOOK_IDENTIFIER} column {@link #DOM_IDENTIFIER_SID}
     * for rows where the {@link #DOM_FK_IDENTIFIER} == "stripinfo"
     * from {@link #TBL_IDENTIFIERS} column {@link #DOM_IDENTIFIER_KEY}
     */
    public static final Domain DOM_STRIP_INFO_BOOK_ID;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_COLLECTION_ID;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_OWNED;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_DIGITAL;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_WANTED;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_AMOUNT;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_LAST_SYNC__UTC;

    /** {@link #TBL_SERIES_PUBLICATION_FREQUENCY}. */
    public static final Domain DOM_PUBLICATION_FREQUENCY_TYPE;
    /** {@link #TBL_SERIES_PUBLICATION_FREQUENCY}. */
    public static final Domain DOM_PUBLICATION_FREQUENCY_CADENCE;
    /** {@link #TBL_SERIES_PUBLICATION_FREQUENCY}. */
    public static final Domain DOM_PUBLICATION_FREQUENCY_IS_ORDINAL;

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final Domain DOM_LOANEE;

    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final Domain DOM_BOOK_AUTHOR_ROLE_BITMASK;
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
    //NEWTHINGS: style option: add a DOM
    public static final Domain DOM_STYLE_EXP_LEVEL;
    public static final Domain DOM_STYLE_ROW_USES_PREF_HEIGHT;
    public static final Domain DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME;
    public static final Domain DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME;
    public static final Domain DOM_STYLE_TITLE_SHOW_REORDERED;
    public static final Domain DOM_STYLE_SHOW_GROUP_BOOK_COUNT;
    public static final Domain DOM_STYLE_READ_STATUS_WITH_PROGRESS;
    public static final Domain DOM_STYLE_TEXT_SCALE;
    public static final Domain DOM_STYLE_COVER_SCALE;
    public static final Domain DOM_STYLE_LIST_HEADER;
    public static final Domain DOM_STYLE_BOOK_DETAIL_FIELD_VISIBILITY;
    public static final Domain DOM_STYLE_BOOK_LIST_FIELD_VISIBILITY;
    public static final Domain DOM_STYLE_BOOK_LIST_FIELD_ORDER_BY;
    public static final Domain DOM_STYLE_GROUPS;
    public static final Domain DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_AUTHOR_PRIMARY_ROLE;
    public static final Domain DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_CITATION_TYPE;

    /** {@link #TBL_BOOK_SERIES}. */
    public static final Domain DOM_BOOK_SERIES_NUMBER;
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

    static final Domain DOM_FTS_AUTHOR_NAME;
    static final Domain DOM_FTS_SERIES_NAMES;
    static final Domain DOM_FTS_PUBLISHER_NAMES;
    static final Domain DOM_FTS_TOC_ENTRY_TITLE;

    private static final String ON_DELETE_CASCADE_ON_UPDATE_CASCADE =
            "ON DELETE CASCADE ON UPDATE CASCADE";

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

        TBL_IDENTIFIERS = new TableDefinition("identifiers", "ids");
        TBL_TAGS = new TableDefinition("tags", "tags");
        TBL_TAG_MAPPINGS = new TableDefinition("tag_mappings", "tgmp");

        TBL_LANG_MAPPINGS = new TableDefinition("lang_mappings", "lm");
        ALIAS_LANG_MAPPINGS_LANGUAGE = "lm_lang";
        ALIAS_LANG_MAPPINGS_ORIGINAL_LANGUAGE = "lm_olang";

        TBL_PSEUDONYM_AUTHOR = new TableDefinition("pseudonym_author", "ap");

        TBL_BOOK_BOOKSHELF = new TableDefinition("book_bookshelf", "bbsh");
        TBL_BOOK_AUTHOR = new TableDefinition("book_author", "ba");
        TBL_BOOK_SERIES = new TableDefinition("book_series", "bs");
        TBL_BOOK_PUBLISHER = new TableDefinition("book_publisher", "bp");
        TBL_BOOK_LOANEE = new TableDefinition("loan", "l");
        TBL_BOOK_TOC_ENTRIES = new TableDefinition("book_anthology", "bat");
        TBL_BOOK_TAG = new TableDefinition("book_tags", "btgs");

        TBL_BOOK_IDENTIFIER = new TableDefinition("book_identifiers", "b_ids");
        TBL_AUTHOR_IDENTIFIER = new TableDefinition("author_identifiers", "a_ids");
        TBL_SERIES_IDENTIFIER = new TableDefinition("series_identifiers", "s_ids");
        TBL_SERIES_PUBLICATION_FREQUENCY = new TableDefinition("series_pub_freq", "spf");

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
        DOM_FK_IDENTIFIER =
                new Domain.Builder(DBKey.FK_IDENTIFIER, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_IDENTIFIERS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();

        DOM_FK_TAG =
                new Domain.Builder(DBKey.FK_TAG, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_TAGS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
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

        DOM_DATE_FIRST_PUBLICATION =
                new Domain.Builder(DBKey.FIRST_PUBLICATION_DATE, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .setIndexSortingOrder(Sort.Desc)
                        .build();

        DOM_LAST_UPDATED__UTC =
                new Domain.Builder(DBKey.DATE_LAST_UPDATED__UTC, SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultCurrentTimeStamp()
                        .setIndexSortingOrder(Sort.Desc)
                        .build();

        /* ======================================================================================
         *  Bookshelf domains
         * ====================================================================================== */

        DOM_BOOKSHELF_NAME =
                new Domain.Builder(DBKey.BOOKSHELF.NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        // Virtual, display only, unsorted
        DOM_BOOKSHELF_NAMES_AS_CSV =
                new Domain.Builder(DBKey.BOOKSHELF.BOOK_BOOKSHELF_NAMES_AS_CSV, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_BOOKSHELF_BL_TOP_POS =
                new Domain.Builder(DBKey.BOOKSHELF.BL_TOP_POS, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(RecyclerView.NO_POSITION)
                        .build();

        DOM_BOOKSHELF_BL_TOP_OFFSET =
                new Domain.Builder(DBKey.BOOKSHELF.BL_TOP_OFFSET, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_BOOKSHELF_FILTER_NAME =
                new Domain.Builder(DBKey.BOOKSHELF.FILTER_NAME, SqLiteDataType.Text)
                        .notNull()
                        .build();
        DOM_BOOKSHELF_FILTER_VALUE =
                new Domain.Builder(DBKey.BOOKSHELF.FILTER_VALUE, SqLiteDataType.Text)
                        .build();

        /* ======================================================================================
         *  Author domains
         * ====================================================================================== */

        DOM_AUTHOR_FAMILY_NAME =
                new Domain.Builder(DBKey.AUTHOR.FAMILY_NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_AUTHOR_FAMILY_NAME_OB =
                new Domain.Builder(DBKey.AUTHOR.FAMILY_NAME_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES =
                new Domain.Builder(DBKey.AUTHOR.GIVEN_NAMES, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES_OB =
                new Domain.Builder(DBKey.AUTHOR.GIVEN_NAMES_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_BIRTH_DATE =
                new Domain.Builder(DBKey.AUTHOR.BIRTH_DATE, SqLiteDataType.Date)
                        .build();
        DOM_AUTHOR_DEATH_DATE =
                new Domain.Builder(DBKey.AUTHOR.DEATH_DATE, SqLiteDataType.Date)
                        .build();
        DOM_AUTHOR_PICTURE_UUID =
                new Domain.Builder(DBKey.AUTHOR.PICTURE_UUID, SqLiteDataType.Text)
                        .build();

        DOM_AUTHOR_IS_COMPLETE =
                new Domain.Builder(DBKey.AUTHOR.COMPLETE, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_AUTHOR_FORMATTED =
                new Domain.Builder(DBKey.AUTHOR.FORMATTED_FULL_NAME, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_AUTHOR_PSEUDONYM =
                new Domain.Builder(DBKey.FK_AUTHOR_PSEUDONYM, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_AUTHORS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();

        DOM_AUTHOR_REAL_AUTHOR =
                new Domain.Builder(DBKey.FK_AUTHOR_REAL_AUTHOR, SqLiteDataType.Integer)
                        .notNull()
                        .references(TBL_AUTHORS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();

        /* ======================================================================================
         *  Series domains
         * ====================================================================================== */

        DOM_SERIES_TITLE =
                new Domain.Builder(DBKey.SERIES.TITLE, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_SERIES_TITLE_OB =
                new Domain.Builder(DBKey.SERIES.TITLE_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_SERIES_IS_COMPLETE =
                new Domain.Builder(DBKey.SERIES.COMPLETE, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        /* ======================================================================================
         *  Publisher domains
         * ====================================================================================== */
        DOM_PUBLISHER_NAME =
                new Domain.Builder(DBKey.PUBLISHER.NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_PUBLISHER_NAME_OB =
                new Domain.Builder(DBKey.PUBLISHER.NAME_OB, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_PUBLISHER_NAMES_AS_CSV =
                new Domain.Builder(DBKey.PUBLISHER.BOOK_PUBLISHER_NAMES_AS_CSV, SqLiteDataType.Text)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  Book domains
         * ====================================================================================== */

        DOM_BOOK_ISBN =
                new Domain.Builder(DBKey.ISBN, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_TRANSLATION_ORIGINAL_TITLE =
                new Domain.Builder(DBKey.TRANSLATION_ORIGINAL_TITLE, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_TRANSLATION_ORIGINAL_LANGUAGE =
                new Domain.Builder(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_DATE_PUBLISHED =
                new Domain.Builder(DBKey.PUBLICATION_DATE, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .setIndexSortingOrder(Sort.Desc)
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
                new Domain.Builder(DBKey.PAGES, SqLiteDataType.Text)
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

        DOM_BOOK_DESCRIPTION =
                new Domain.Builder(DBKey.DESCRIPTION, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_CONTENT_TYPE =
                new Domain.Builder(DBKey.CONTENT_TYPE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Book.ContentType.Book.getId())
                        .build();

        /* ======================================================================================
         *  Book personal data domains
         * ====================================================================================== */

        DOM_BOOK_UUID =
                new Domain.Builder(DBKey.BOOK_UUID, SqLiteDataType.Text)
                        .notNull()
                        // Yes, despite we refer to this as a UUID,
                        // this is NOT a real UUID. It's just a 16 byte hex string.
                        // This is as-designed/fine for our purpose.
                        .withDefault("(lower(hex(randomblob(16))))")
                        .build();

        DOM_BOOK_EDITION =
                new Domain.Builder(DBKey.EDITION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Book.Edition.UNKNOWN)
                        .build();

        DOM_BOOK_DATE_ACQUIRED =
                new Domain.Builder(DBKey.DATE_ACQUIRED, SqLiteDataType.Date)
                        .notNull()
                        .withDefaultEmptyString()
                        .setIndexSortingOrder(Sort.Desc)
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

        DOM_DATE_ADDED__UTC =
                new Domain.Builder(DBKey.DATE_ADDED__UTC, SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultCurrentTimeStamp()
                        .setIndexSortingOrder(Sort.Desc)
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
                new Domain.Builder(DBKey.CONDITION_BOOK, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(0)
                        .build();
        DOM_BOOK_CONDITION_DUST_COVER =
                new Domain.Builder(DBKey.CONDITION_COVER, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_AUTO_UPDATE =
                new Domain.Builder(DBKey.AUTO_UPDATE, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(true)
                        .build();

        /* ======================================================================================
         *  Tags
         * ====================================================================================== */

        // localised but with non-unique index, so these are case-sensitive and diacritic aware
        DOM_TAG =
                new Domain.Builder(DBKey.TAGS.TAG, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        // localised but with non-unique index, so these are case-sensitive and diacritic aware
        DOM_TAG_MAPPING =
                new Domain.Builder(DBKey.TAGS.TAG_MAPPING, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        /* ======================================================================================
         *  Language ISO3 lookup cache
         * ====================================================================================== */

        DOM_LANG_USER_ISO3 =
                new Domain.Builder(DBKey.LANG_MAPPING.ISO3_USER, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_LANG_ISO3 =
                new Domain.Builder(DBKey.LANG_MAPPING.ISO3, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_LANG_DISPLAY_NAME =
                new Domain.Builder(DBKey.LANG_MAPPING.DISPLAY_NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        /* ======================================================================================
         *  Book identifiers
         * ====================================================================================== */

        // not localised!
        DOM_IDENTIFIER_KEY =
                new Domain.Builder(DBKey.IDENTIFIERS.KEY, SqLiteDataType.Text)
                        .notNull()
                        .build();
        DOM_IDENTIFIER_ENTITY =
                new Domain.Builder(DBKey.IDENTIFIERS.ENTITY, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Identifier.EntityType.Book.getId())
                        .build();

        DOM_IDENTIFIER_TYPE =
                new Domain.Builder(DBKey.IDENTIFIERS.TYPE, SqLiteDataType.Text)
                        .notNull()
                        .withDefault("'" + Identifier.Type.Text.getId() + "'")
                        .build();
        DOM_IDENTIFIER_NAME =
                new Domain.Builder(DBKey.IDENTIFIERS.NAME, SqLiteDataType.Text)
                        .notNull()
                        .localized()
                        .build();

        DOM_IDENTIFIER_WIKIDATA_CLAIM =
                new Domain.Builder(DBKey.IDENTIFIERS.WIKIDATA_CLAIM, SqLiteDataType.Text)
                        .build();
        DOM_IDENTIFIER_SITE_URL =
                new Domain.Builder(DBKey.IDENTIFIERS.SITE_URL, SqLiteDataType.Text)
                        .build();
        DOM_IDENTIFIER_URI =
                new Domain.Builder(DBKey.IDENTIFIERS.URI, SqLiteDataType.Text)
                        .build();

        DOM_IDENTIFIER_SID =
                new Domain.Builder(DBKey.IDENTIFIERS.SID, SqLiteDataType.Text)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  StripInfo.be synchronization domains
         * ====================================================================================== */
        DOM_STRIP_INFO_BOOK_ID =
                new Domain.Builder(DBKey.STRIP_INFO.BOOK_ID, SqLiteDataType.Integer)
                        .build();

        DOM_STRIP_INFO_COLLECTION_ID =
                new Domain.Builder(DBKey.STRIP_INFO.COLLECTION_ID, SqLiteDataType.Integer)
                        .build();

        DOM_STRIP_INFO_WANTED =
                new Domain.Builder(DBKey.STRIP_INFO.WANTED, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_OWNED =
                new Domain.Builder(DBKey.STRIP_INFO.OWNED, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_DIGITAL =
                new Domain.Builder(DBKey.STRIP_INFO.DIGITAL, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_AMOUNT =
                new Domain.Builder(DBKey.STRIP_INFO.AMOUNT, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(1)
                        .build();

        DOM_STRIP_INFO_LAST_SYNC__UTC =
                new Domain.Builder(DBKey.STRIP_INFO.LAST_SYNC_DATE__UTC,
                                   SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        /* ======================================================================================
         *  Calibre bridge table domains
         * ====================================================================================== */
        DOM_CALIBRE_BOOK_UUID =
                new Domain.Builder(DBKey.CALIBRE.BOOK_UUID, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_BOOK_ID =
                new Domain.Builder(DBKey.CALIBRE.BOOK_ID, SqLiteDataType.Integer)
                        .build();

        DOM_CALIBRE_BOOK_MAIN_FORMAT =
                new Domain.Builder(DBKey.CALIBRE.BOOK_MAIN_FORMAT, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_NAME =
                new Domain.Builder(DBKey.CALIBRE.CUSTOM_FIELD_NAME, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_TYPE =
                new Domain.Builder(DBKey.CALIBRE.CUSTOM_FIELD_TYPE, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_MAPPING =
                new Domain.Builder(DBKey.CALIBRE.CUSTOM_FIELD_MAPPING, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC =
                new Domain.Builder(DBKey.CALIBRE.LIBRARY_LAST_SYNC_DATE__UTC,
                                   SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_CALIBRE_LIBRARY_STRING_ID =
                new Domain.Builder(DBKey.CALIBRE.LIBRARY_STRING_ID, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        // can be empty when our Calibre extension is not installed
        DOM_CALIBRE_LIBRARY_UUID =
                new Domain.Builder(DBKey.CALIBRE.LIBRARY_UUID, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_CALIBRE_LIBRARY_NAME =
                new Domain.Builder(DBKey.CALIBRE.LIBRARY_NAME, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        // not sure if we should allow empty?
        DOM_CALIBRE_VIRT_LIB_EXPR =
                new Domain.Builder(DBKey.CALIBRE.VIRT_LIB_EXPR, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        /* ======================================================================================
         *  Publication frequency domains
         * ====================================================================================== */

        DOM_PUBLICATION_FREQUENCY_TYPE =
                new Domain.Builder(DBKey.PUBLICATION_FREQUENCY.TYPE, SqLiteDataType.Integer)
                        .notNull()
                        .build();
        DOM_PUBLICATION_FREQUENCY_CADENCE =
                new Domain.Builder(DBKey.PUBLICATION_FREQUENCY.CADENCE, SqLiteDataType.Integer)
                        .notNull()
                        .build();
        DOM_PUBLICATION_FREQUENCY_IS_ORDINAL =
                new Domain.Builder(DBKey.PUBLICATION_FREQUENCY.IS_ORDINAL, SqLiteDataType.Boolean)
                        .notNull()
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

        DOM_BOOK_AUTHOR_ROLE_BITMASK =
                new Domain.Builder(DBKey.AUTHOR.BOOK_AUTHOR_ROLE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(AuthorRole.UNKNOWN)
                        .build();

        DOM_BOOK_AUTHOR_POSITION =
                new Domain.Builder(DBKey.AUTHOR.BOOK_AUTHOR_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BOOK_SERIES_POSITION =
                new Domain.Builder(DBKey.SERIES.BOOK_SERIES_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BOOK_SERIES_NUMBER =
                new Domain.Builder(DBKey.SERIES.BOOK_SERIES_NUMBER, SqLiteDataType.Text)
                        .localized()
                        .build();

        DOM_BOOK_PUBLISHER_POSITION =
                new Domain.Builder(DBKey.PUBLISHER.BOOK_PUBLISHER_POSITION, SqLiteDataType.Integer)
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
                new Domain.Builder(DBKey.STYLE.UUID, SqLiteDataType.Text)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_STYLE_TYPE =
                new Domain.Builder(DBKey.STYLE.TYPE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.Type.User.getId())
                        .build();

        DOM_STYLE_IS_PREFERRED =
                new Domain.Builder(DBKey.STYLE.IS_PREFERRED, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_MENU_POSITION =
                new Domain.Builder(DBKey.STYLE.MENU_POSITION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.MENU_POSITION_NOT_PREFERRED)
                        .build();


        DOM_STYLE_NAME =
                new Domain.Builder(DBKey.STYLE.NAME, SqLiteDataType.Text)
                        .localized()
                        .build();

        DOM_STYLE_GROUPS =
                new Domain.Builder(DBKey.STYLE.GROUPS, SqLiteDataType.Text)
                        .build();

        DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE.GROUPS_AUTHOR_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_AUTHOR_PRIMARY_ROLE =
                new Domain.Builder(DBKey.STYLE.GROUPS_AUTHOR_PRIMARY_ROLE,
                                   SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(AuthorRole.UNKNOWN)
                        .build();

        DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE.GROUPS_SERIES_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE.GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE.GROUPS_BOOKSHELF_SHOW_UNDER_EACH,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();


        DOM_STYLE_LAYOUT =
                new Domain.Builder(DBKey.STYLE.LAYOUT, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(ScreenLayout.List.getId())
                        .build();

        DOM_STYLE_COVER_CLICK_ACTION =
                new Domain.Builder(DBKey.STYLE.COVER_CLICK_ACTION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.CoverClickAction.Zoom.getId())
                        .build();

        DOM_STYLE_COVER_LONG_CLICK_ACTION =
                new Domain.Builder(DBKey.STYLE.COVER_LONG_CLICK_ACTION, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(Style.CoverLongClickAction.Ignore.getId())
                        .build();

        DOM_STYLE_EXP_LEVEL =
                new Domain.Builder(DBKey.STYLE.EXP_LEVEL, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(1)
                        .build();

        DOM_STYLE_ROW_USES_PREF_HEIGHT =
                new Domain.Builder(DBKey.STYLE.ROW_USES_PREF_HEIGHT,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(true)
                        .build();

        DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME =
                new Domain.Builder(DBKey.STYLE.AUTHOR_SORT_BY_GIVEN_NAME,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();
        DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME =
                new Domain.Builder(DBKey.STYLE.AUTHOR_SHOW_BY_GIVEN_NAME,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_TITLE_SHOW_REORDERED =
                new Domain.Builder(DBKey.STYLE.TITLE_SHOW_REORDERED,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_SHOW_GROUP_BOOK_COUNT =
                new Domain.Builder(DBKey.STYLE.SHOW_GROUP_BOOK_COUNT,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(true)
                        .build();

        DOM_STYLE_READ_STATUS_WITH_PROGRESS =
                new Domain.Builder(DBKey.STYLE.READ_STATUS_WITH_PROGRESS,
                                   SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_TEXT_SCALE =
                new Domain.Builder(DBKey.STYLE.TEXT_SCALE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(TextScale.DEFAULT.getId())
                        .build();
        DOM_STYLE_COVER_SCALE =
                new Domain.Builder(DBKey.STYLE.COVER_SCALE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(CoverScale.DEFAULT.getId())
                        .build();

        DOM_STYLE_CITATION_TYPE =
                new Domain.Builder(DBKey.STYLE.CITATION_TYPE, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(CitationType.Default.getId())
                        .build();

        DOM_STYLE_LIST_HEADER =
                new Domain.Builder(DBKey.STYLE.LIST_HEADER, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(BooklistHeader.BITMASK_ALL)
                        .build();

        DOM_STYLE_BOOK_DETAIL_FIELD_VISIBILITY =
                new Domain.Builder(DBKey.STYLE.BOOK_DETAIL_FIELD_VISIBILITY, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(FieldVisibility.getBitValue(
                                BookDetailsFieldVisibility.DEFAULT))
                        .build();

        DOM_STYLE_BOOK_LIST_FIELD_VISIBILITY =
                new Domain.Builder(DBKey.STYLE.BOOK_LIST_FIELD_VISIBILITY, SqLiteDataType.Integer)
                        .notNull()
                        .withDefault(FieldVisibility.getBitValue(
                                BookLevelFieldVisibility.DEFAULT))
                        .build();

        DOM_STYLE_BOOK_LIST_FIELD_ORDER_BY =
                new Domain.Builder(DBKey.STYLE.BOOK_LIST_FIELD_ORDER_BY, SqLiteDataType.Text)
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
                            DOM_STYLE_GROUPS_AUTHOR_PRIMARY_ROLE,
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
                            DOM_STYLE_SHOW_GROUP_BOOK_COUNT,
                            DOM_STYLE_READ_STATUS_WITH_PROGRESS,
                            DOM_STYLE_CITATION_TYPE,
                            DOM_STYLE_TEXT_SCALE,
                            DOM_STYLE_COVER_SCALE,
                            DOM_STYLE_LIST_HEADER,
                            DOM_STYLE_BOOK_DETAIL_FIELD_VISIBILITY,
                            DOM_STYLE_BOOK_LIST_FIELD_VISIBILITY,
                            DOM_STYLE_BOOK_LIST_FIELD_ORDER_BY)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.STYLE.UUID, true,
                          DOM_STYLE_UUID)
                .addIndex(DBKey.STYLE.NAME, true,
                          DOM_STYLE_NAME)
                .addIndex(DBKey.STYLE.MENU_POSITION, false,
                          DOM_STYLE_MENU_POSITION,
                          DOM_STYLE_NAME,
                          DOM_PK_ID);
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
                .addIndex("SORT", true,
                          DOM_BOOKSHELF_NAME)
                .addIndex(DBKey.FK_STYLE, false,
                          DOM_FK_STYLE);
        ALL_TABLES.put(TBL_BOOKSHELF.getName(), TBL_BOOKSHELF);

        TBL_BOOKSHELF_FILTERS
                .addDomains(DOM_FK_BOOKSHELF,
                            DOM_BOOKSHELF_FILTER_NAME,
                            DOM_BOOKSHELF_FILTER_VALUE)
                .setPrimaryKey(DOM_FK_BOOKSHELF, DOM_BOOKSHELF_FILTER_NAME)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOKSHELF_FILTERS.getName(), TBL_BOOKSHELF_FILTERS);

        TBL_IDENTIFIERS
                .addDomains(DOM_PK_ID,
                            DOM_IDENTIFIER_KEY,
                            DOM_IDENTIFIER_ENTITY,
                            DOM_IDENTIFIER_TYPE,
                            DOM_IDENTIFIER_NAME,
                            DOM_IDENTIFIER_WIKIDATA_CLAIM,
                            DOM_IDENTIFIER_SITE_URL,
                            DOM_IDENTIFIER_URI)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.IDENTIFIERS.KEY, true,
                          DOM_IDENTIFIER_KEY,
                          DOM_IDENTIFIER_ENTITY)
                .addIndex(DBKey.IDENTIFIERS.NAME, false,
                          DOM_IDENTIFIER_NAME,
                          DOM_IDENTIFIER_KEY,
                          DOM_IDENTIFIER_TYPE);
        ALL_TABLES.put(TBL_IDENTIFIERS.getName(), TBL_IDENTIFIERS);

        TBL_TAGS
                .addDomains(DOM_PK_ID,
                            DOM_TAG)
                .setPrimaryKey(DOM_PK_ID)
                // for historic reasons NOT unique
                .addIndex(DBKey.TAGS.TAG, false,
                          DOM_TAG);
        ALL_TABLES.put(TBL_TAGS.getName(), TBL_TAGS);

        TBL_TAG_MAPPINGS
                .addDomains(DOM_PK_ID,
                            DOM_TAG,
                            DOM_TAG_MAPPING)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.TAGS.TAG, false, DOM_TAG);
        ALL_TABLES.put(TBL_TAG_MAPPINGS.getName(), TBL_TAG_MAPPINGS);

        TBL_LANG_MAPPINGS
                .addDomains(DOM_PK_ID,
                            DOM_LANG_USER_ISO3,
                            DOM_LANG_ISO3,
                            DOM_LANG_DISPLAY_NAME)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.LANG_MAPPING.ISO3, false, DOM_LANG_ISO3)
                .addIndex(DBKey.LANG_MAPPING.DISPLAY_NAME, false, DOM_LANG_DISPLAY_NAME);
        ALL_TABLES.put(TBL_LANG_MAPPINGS.getName(), TBL_LANG_MAPPINGS);

        TBL_AUTHORS
                .addDomains(DOM_PK_ID,
                            DOM_AUTHOR_FAMILY_NAME,
                            DOM_AUTHOR_FAMILY_NAME_OB,
                            DOM_AUTHOR_GIVEN_NAMES,
                            DOM_AUTHOR_GIVEN_NAMES_OB,
                            DOM_AUTHOR_BIRTH_DATE,
                            DOM_AUTHOR_DEATH_DATE,
                            DOM_AUTHOR_PICTURE_UUID,
                            DOM_AUTHOR_IS_COMPLETE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("SORT", false,
                          DOM_AUTHOR_FAMILY_NAME_OB,
                          DOM_AUTHOR_GIVEN_NAMES_OB,
                          DOM_AUTHOR_FAMILY_NAME,
                          DOM_AUTHOR_GIVEN_NAMES)
                .addIndex("DISPLAY", false,
                          DOM_AUTHOR_FAMILY_NAME,
                          DOM_AUTHOR_GIVEN_NAMES);
        ALL_TABLES.put(TBL_AUTHORS.getName(), TBL_AUTHORS);

        TBL_SERIES
                .addDomains(DOM_PK_ID,
                            DOM_SERIES_TITLE,
                            DOM_SERIES_TITLE_OB,
                            DOM_SERIES_IS_COMPLETE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("SORT", false,
                          DOM_SERIES_TITLE_OB,
                          DOM_SERIES_TITLE)
                .addIndex("DISPLAY", false,
                          DOM_SERIES_TITLE);
        ALL_TABLES.put(TBL_SERIES.getName(), TBL_SERIES);

        TBL_PUBLISHERS
                .addDomains(DOM_PK_ID,
                            DOM_PUBLISHER_NAME,
                            DOM_PUBLISHER_NAME_OB)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("SORT", false,
                          DOM_PUBLISHER_NAME_OB,
                          DOM_PUBLISHER_NAME)
                .addIndex("DISPLAY", false,
                          DOM_PUBLISHER_NAME);
        ALL_TABLES.put(TBL_PUBLISHERS.getName(), TBL_PUBLISHERS);

        TBL_BOOKS
                .addDomains(DOM_PK_ID,
                            // book data
                            DOM_TITLE,
                            DOM_TITLE_OB,
                            DOM_TRANSLATION_ORIGINAL_TITLE,
                            DOM_TRANSLATION_ORIGINAL_LANGUAGE,
                            DOM_BOOK_ISBN,
                            DOM_BOOK_DATE_PUBLISHED,
                            DOM_DATE_FIRST_PUBLICATION,
                            DOM_BOOK_PRINT_RUN,

                            DOM_BOOK_PRICE_LISTED,
                            DOM_BOOK_PRICE_LISTED_CURRENCY,

                            DOM_BOOK_CONTENT_TYPE,
                            DOM_BOOK_FORMAT,
                            DOM_BOOK_COLOR,
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

                            // internal data
                            DOM_BOOK_UUID,
                            DOM_DATE_ADDED__UTC,
                            DOM_LAST_UPDATED__UTC)

                .setPrimaryKey(DOM_PK_ID)
                .addIndex("SORT", false,
                          DOM_TITLE_OB,
                          DOM_TITLE)
                .addIndex(DBKey.BOOK_UUID, true,
                          DOM_BOOK_UUID)
                .addIndex(DBKey.ISBN, false,
                          DOM_BOOK_ISBN)

                .addIndex(DBKey.TITLE, false,
                          DOM_TITLE)

                .addIndex(DBKey.DATE_LAST_UPDATED__UTC, false,
                          DOM_LAST_UPDATED__UTC)
                .addIndex(DBKey.DATE_ADDED__UTC, false,
                          DOM_DATE_ADDED__UTC)
                .addIndex(DBKey.DATE_ACQUIRED, false,
                          DOM_BOOK_DATE_ACQUIRED)

                .addIndex(DBKey.FIRST_PUBLICATION_DATE, false,
                          DOM_DATE_FIRST_PUBLICATION,
                          DOM_TITLE_OB)
                .addIndex(DBKey.PUBLICATION_DATE, false,
                          DOM_BOOK_DATE_PUBLISHED,
                          DOM_TITLE_OB);

        ALL_TABLES.put(TBL_BOOKS.getName(), TBL_BOOKS);

        TBL_DELETED_BOOKS.addDomains(DOM_BOOK_UUID,
                                     DOM_DATE_ADDED__UTC)
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
                .addIndex(DBKey.TITLE_OB, false,
                          DOM_TITLE_OB,
                          DOM_TITLE,
                          DOM_FK_AUTHOR)
                .addIndex(DBKey.FK_AUTHOR, false,
                          DOM_FK_AUTHOR,
                          DOM_TITLE_OB)
                .addIndex(DBKey.TITLE, false,
                          DOM_TITLE);
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
                // Reverse lookup
                // Not unique, an author can have multiple pseudonyms
                .addIndex(DBKey.FK_AUTHOR_REAL_AUTHOR, false,
                          DOM_AUTHOR_REAL_AUTHOR,
                          DOM_AUTHOR_PSEUDONYM);
        ALL_TABLES.put(TBL_PSEUDONYM_AUTHOR.getName(), TBL_PSEUDONYM_AUTHOR);


        TBL_BOOK_BOOKSHELF
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_BOOKSHELF)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_BOOKSHELF)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                // Reverse lookup
                .addIndex(DBKey.FK_BOOKSHELF, true,
                          DOM_FK_BOOKSHELF,
                          DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_BOOKSHELF.getName(), TBL_BOOK_BOOKSHELF);


        TBL_BOOK_AUTHOR
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_AUTHOR,
                            DOM_BOOK_AUTHOR_POSITION,
                            DOM_BOOK_AUTHOR_ROLE_BITMASK)
                // enforce: only one author on a particular position for a book.
                // allow: multiple copies of that author and multiple types.
                // The latter has some restrictions handled in code.
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_AUTHOR, DOM_BOOK_AUTHOR_POSITION)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                // Forward
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_BOOK_AUTHOR_POSITION,
                          DOM_FK_AUTHOR)
                // Reverse lookup
                .addIndex(DBKey.FK_AUTHOR, true,
                          DOM_FK_AUTHOR,
                          DOM_FK_BOOK)
                // Bitmask Optimizer: user has a preferred primary role
                .addIndex("ROLE_POS", false,
                          DOM_BOOK_AUTHOR_ROLE_BITMASK,
                          DOM_BOOK_AUTHOR_POSITION);
        ALL_TABLES.put(TBL_BOOK_AUTHOR.getName(), TBL_BOOK_AUTHOR);


        TBL_BOOK_SERIES
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_SERIES,
                            DOM_BOOK_SERIES_NUMBER,
                            DOM_BOOK_SERIES_POSITION)
                // enforce: only one series on a particular position for a book.
                // allow: multiple copies of that series and multiple numbers.
                // The latter has some restrictions handled in code.
                // In contract to TBL_BOOK_AUTHOR we don't want to add the DOM_FK_SERIES
                // to the primary key, as want to allow a single book to be
                // present in a series multiple times at different positions
                // (each entry with a different number in the series).
                .setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_SERIES_POSITION)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_SERIES, DOM_FK_SERIES)
                // Forward
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_BOOK_SERIES_POSITION,
                          DOM_FK_SERIES,
                          DOM_BOOK_SERIES_NUMBER)
                // Reverse lookup; not unique, see above
                .addIndex(DBKey.FK_SERIES, false,
                          DOM_FK_SERIES,
                          DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_SERIES.getName(), TBL_BOOK_SERIES);


        TBL_BOOK_PUBLISHER
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_PUBLISHER,
                            DOM_BOOK_PUBLISHER_POSITION)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_PUBLISHER, DOM_BOOK_PUBLISHER_POSITION)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_PUBLISHERS, DOM_FK_PUBLISHER)
                // Forward
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_BOOK_PUBLISHER_POSITION,
                          DOM_FK_PUBLISHER)
                // Reverse lookup
                .addIndex(DBKey.FK_PUBLISHER, true,
                          DOM_FK_PUBLISHER,
                          DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_PUBLISHER.getName(), TBL_BOOK_PUBLISHER);


        TBL_BOOK_TOC_ENTRIES
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_TOC_ENTRY,
                            DOM_BOOK_TOC_ENTRY_POSITION)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_TOC_ENTRY)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_TOC_ENTRIES, DOM_FK_TOC_ENTRY)
                // Forward
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_BOOK_TOC_ENTRY_POSITION,
                          DOM_FK_TOC_ENTRY)
                // Reverse lookup
                .addIndex(DBKey.FK_TOC_ENTRY, true,
                          DOM_FK_TOC_ENTRY,
                          DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_TOC_ENTRIES.getName(), TBL_BOOK_TOC_ENTRIES);


        TBL_BOOK_LOANEE
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOK,
                            DOM_LOANEE)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                // Forward
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_LOANEE)
                // Reverse lookup
                .addIndex(DBKey.LOANEE_NAME, false,
                          DOM_LOANEE,
                          DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_LOANEE.getName(), TBL_BOOK_LOANEE);

        TBL_BOOK_TAG
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_TAG)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_TAG)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_TAGS, DOM_FK_TAG)
                // Reverse lookup
                .addIndex(DBKey.FK_TAG, true,
                          DOM_FK_TAG,
                          DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_TAG.getName(), TBL_BOOK_TAG);

        TBL_BOOK_IDENTIFIER
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_IDENTIFIER,
                            DOM_IDENTIFIER_SID)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_IDENTIFIER)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_IDENTIFIERS, DOM_FK_IDENTIFIER)
                // Forward
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_FK_IDENTIFIER,
                          DOM_IDENTIFIER_SID)
                // Reverse lookup
                // not unique to allow for "bad data" during imports
                .addIndex(DBKey.FK_IDENTIFIER, false,
                          DOM_FK_IDENTIFIER,
                          DOM_IDENTIFIER_SID,
                          DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_IDENTIFIER.getName(), TBL_BOOK_IDENTIFIER);

        TBL_AUTHOR_IDENTIFIER
                .addDomains(DOM_FK_AUTHOR,
                            DOM_FK_IDENTIFIER,
                            DOM_IDENTIFIER_SID)
                .setPrimaryKey(DOM_FK_AUTHOR, DOM_FK_IDENTIFIER)
                .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                .addReference(TBL_IDENTIFIERS, DOM_FK_IDENTIFIER)
                // Forward
                // 2026-05-20: previously we mistakenly used DBKey.FK_BOOK as the name
                // As indexes are dropped and recreated, correcting the name should be fine.
                .addIndex(DBKey.FK_AUTHOR, true,
                          DOM_FK_AUTHOR,
                          DOM_FK_IDENTIFIER,
                          DOM_IDENTIFIER_SID)
                // Reverse lookup
                // not unique to allow for "bad data" during imports
                .addIndex(DBKey.FK_IDENTIFIER, false,
                          DOM_FK_IDENTIFIER,
                          DOM_IDENTIFIER_SID,
                          DOM_FK_AUTHOR);
        ALL_TABLES.put(TBL_AUTHOR_IDENTIFIER.getName(), TBL_AUTHOR_IDENTIFIER);

        TBL_SERIES_IDENTIFIER
                .addDomains(DOM_FK_SERIES,
                            DOM_FK_IDENTIFIER,
                            DOM_IDENTIFIER_SID)
                .setPrimaryKey(DOM_FK_SERIES, DOM_FK_IDENTIFIER)
                .addReference(TBL_SERIES, DOM_FK_SERIES)
                .addReference(TBL_IDENTIFIERS, DOM_FK_IDENTIFIER)
                // Forward
                .addIndex(DBKey.FK_SERIES, true,
                          DOM_FK_SERIES,
                          DOM_FK_IDENTIFIER,
                          DOM_IDENTIFIER_SID)
                // Reverse lookup
                // not unique to allow for "bad data" during imports
                .addIndex(DBKey.FK_IDENTIFIER, false,
                          DOM_FK_IDENTIFIER,
                          DOM_IDENTIFIER_SID,
                          DOM_FK_SERIES);
        ALL_TABLES.put(TBL_SERIES_IDENTIFIER.getName(), TBL_SERIES_IDENTIFIER);

        TBL_SERIES_PUBLICATION_FREQUENCY
                .addDomains(DOM_FK_SERIES,
                            DOM_PUBLICATION_FREQUENCY_TYPE,
                            DOM_PUBLICATION_FREQUENCY_CADENCE,
                            DOM_PUBLICATION_FREQUENCY_IS_ORDINAL)
                .setPrimaryKey(DOM_FK_SERIES)
                .addReference(TBL_SERIES, DOM_FK_SERIES);
        ALL_TABLES.put(TBL_SERIES_PUBLICATION_FREQUENCY.getName(),
                       TBL_SERIES_PUBLICATION_FREQUENCY);

        TBL_CALIBRE_BOOKS
                .addDomains(DOM_FK_BOOK,
                            DOM_CALIBRE_BOOK_ID,
                            DOM_CALIBRE_BOOK_UUID,
                            DOM_CALIBRE_BOOK_MAIN_FORMAT,
                            DOM_FK_CALIBRE_LIBRARY)
                .setPrimaryKey(DOM_FK_BOOK)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_CALIBRE_LIBRARIES, DOM_FK_CALIBRE_LIBRARY)
                // Forward
                .addIndex(DBKey.FK_BOOK, false,
                          DOM_FK_BOOK,
                          DOM_CALIBRE_BOOK_ID,
                          DOM_CALIBRE_BOOK_UUID,
                          DOM_CALIBRE_BOOK_MAIN_FORMAT,
                          DOM_FK_CALIBRE_LIBRARY)
                .addIndex(DBKey.CALIBRE.BOOK_UUID, true,
                          DOM_CALIBRE_BOOK_UUID);
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
                .addIndex(DBKey.CALIBRE.LIBRARY_NAME, true,
                          DOM_CALIBRE_LIBRARY_STRING_ID,
                          DOM_CALIBRE_LIBRARY_NAME)
                .addIndex(DBKey.FK_BOOKSHELF, false,
                          DOM_FK_BOOKSHELF)
                // for historic reasons NOT unique
                .addIndex(DBKey.CALIBRE.LIBRARY_UUID, false,
                          DOM_CALIBRE_LIBRARY_UUID);
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
                .addIndex(DBKey.CALIBRE.LIBRARY_NAME, true,
                          DOM_FK_CALIBRE_LIBRARY,
                          DOM_CALIBRE_LIBRARY_NAME)
                .addIndex(DBKey.FK_BOOKSHELF, false,
                          DOM_FK_BOOKSHELF);
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
                            DOM_STRIP_INFO_BOOK_ID,
                            DOM_STRIP_INFO_COLLECTION_ID,
                            DOM_STRIP_INFO_WANTED,
                            DOM_STRIP_INFO_OWNED,
                            DOM_STRIP_INFO_DIGITAL,
                            DOM_STRIP_INFO_AMOUNT,
                            DOM_STRIP_INFO_LAST_SYNC__UTC)
                .setPrimaryKey(DOM_FK_BOOK)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addIndex(DBKey.STRIP_INFO.BOOK_ID, true,
                          DOM_STRIP_INFO_BOOK_ID);
        ALL_TABLES.put(TBL_STRIPINFO_COLLECTION.getName(), TBL_STRIPINFO_COLLECTION);
    }

    static {

        TBL_BOOK_LIST_NODE_STATE = new TableDefinition("book_list_node_settings", "bl_ns");

        DOM_BL_NODE_KEY =
                new Domain.Builder(DBKey.BL_NODE.KEY, SqLiteDataType.Text)
                        .build();

        DOM_BL_NODE_GROUP =
                new Domain.Builder(DBKey.BL_NODE.GROUP, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BL_NODE_LEVEL =
                new Domain.Builder(DBKey.BL_NODE.LEVEL, SqLiteDataType.Integer)
                        .notNull()
                        .build();

        DOM_BL_NODE_VISIBLE =
                new Domain.Builder(DBKey.BL_NODE.VISIBLE, SqLiteDataType.Integer)
                        .withDefault(false)
                        .build();

        DOM_BL_NODE_EXPANDED =
                new Domain.Builder(DBKey.BL_NODE.EXPANDED, SqLiteDataType.Integer)
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
                // for historic reasons NOT unique
                .addIndex(DBKey.BL_NODE.KEY, false,
                          DOM_FK_BOOKSHELF,
                          DOM_FK_STYLE,
                          DOM_BL_NODE_KEY)
                .addIndex(DBKey.FK_STYLE, false,
                          DOM_FK_STYLE);
        ALL_TABLES.put(TBL_BOOK_LIST_NODE_STATE.getName(),
                       TBL_BOOK_LIST_NODE_STATE);
    }

    static {
        DOM_FTS_AUTHOR_NAME =
                new Domain.Builder(DBKey.FTS.AUTHOR_NAME, SqLiteDataType.Text)
                        .build();

        DOM_FTS_SERIES_NAMES =
                new Domain.Builder(DBKey.FTS.SERIES_NAMES, SqLiteDataType.Text)
                        .build();

        DOM_FTS_PUBLISHER_NAMES =
                new Domain.Builder(DBKey.FTS.PUBLISHER_NAMES, SqLiteDataType.Text)
                        .build();

        DOM_FTS_TOC_ENTRY_TITLE =
                new Domain.Builder(DBKey.FTS.TOC_ENTRY_TITLE, SqLiteDataType.Text)
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
                            DOM_FTS_SERIES_NAMES,
                            DOM_FTS_PUBLISHER_NAMES,

                            DOM_BOOK_DESCRIPTION,
                            DOM_BOOK_PRIVATE_NOTES,
                            DOM_BOOK_LOCATION,
                            DOM_BOOK_ISBN,

                            DOM_FTS_TOC_ENTRY_TITLE);
    }
}
