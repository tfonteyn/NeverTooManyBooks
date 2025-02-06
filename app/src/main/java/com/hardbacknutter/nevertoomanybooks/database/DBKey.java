/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Keys used as domain names / Bundle keys.
 */
@SuppressWarnings("WeakerAccess")
public final class DBKey {

    /** Primary key. */
    public static final String PK_ID = "_id";
    /** Foreign key. */
    public static final String FK_BOOK = "book";
    /** Foreign key. */
    public static final String FK_AUTHOR = "author";
    /** Foreign key. */
    public static final String FK_SERIES = "series_id";
    /** Foreign key. */
    public static final String FK_PUBLISHER = "publisher_id";
    /** Foreign key. */
    public static final String FK_BOOKSHELF = "bookshelf_id";
    /** Foreign key. */
    public static final String FK_TOC_ENTRY = "anthology";
    /** Foreign key. */
    public static final String FK_STYLE = "style";
    /** Foreign key. */
    public static final String FK_CALIBRE_LIBRARY = "clb_lib_id";
    /** Foreign key. */
    public static final String FK_IDENTIFIER = "ident_id";
    /** Foreign key. */
    public static final String FK_TAG = "tag_id";

    // Adding new FK's...  make sure to name them "xxx_id" to avoid duplicate use of "xxx"

    /*
     * ======================================================================================
     * External Site id's.
     * ======================================================================================
     */

    /** {@link DBDefinitions#TBL_BOOK_IDENTIFIER}. The external site id. */
    public static final String BOOK_IDENTIFIER_SID = "sid";


    /** {@link DBDefinitions#TBL_BOOKSHELF}. The name of the bookshelf. */
    public static final String BOOKSHELF_NAME = "bookshelf_name";
    /** {@link DBDefinitions#TBL_BOOKSHELF}. The booklist adapter position of current top row. */
    public static final String BOOKSHELF_BL_TOP_POS = "bl_top_pos";
    /** {@link DBDefinitions#TBL_BOOKSHELF}. The booklist adapter top row offset from view top. */
    public static final String BOOKSHELF_BL_TOP_OFFSET = "bl_top_offset";
    /** Alias: a list of bookshelf names as a comma separated list. */
    public static final String BOOKSHELF_NAMES_AS_CSV = "bs_name_csv";

    /** {@link DBDefinitions#TBL_BOOKSHELF_FILTERS}. */
    public static final String BOOKSHELF_FILTER_NAME = "filter_name";
    public static final String BOOKSHELF_FILTER_VALUE = "filter_value";


    /** {@link DBDefinitions#TBL_AUTHORS} */
    public static final String AUTHOR_FAMILY_NAME = "family_name";
    public static final String AUTHOR_GIVEN_NAMES = "given_names";
    public static final String AUTHOR_IS_COMPLETE = "author_complete";


    /* Aliases for CASE expressions. */

    /**
     * Virtual column.
     * <p>
     * The first/family name order is determined in the SQL statement.
     * Hence, reading the data from the cursor is <strong>always</strong>
     * done using this key.
     */
    public static final String AUTHOR_FORMATTED = "author_formatted";
    /**
     * Virtual column: "GivenName FamilyName".
     * <p>
     * Only used for the special case.
     * {@link com.hardbacknutter.nevertoomanybooks.search.SearchBookByTextFragment}
     */
    public static final String AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";

    /** {@link DBDefinitions#TBL_BOOK_AUTHOR}. */
    public static final String AUTHOR_TYPE__BITMASK = "author_type";
    public static final String BOOK_AUTHOR_POSITION = "author_position";

    /**
     * Foreign key.
     * {@link DBDefinitions#TBL_PSEUDONYM_AUTHOR}.
     * This is a FK to {@link DBDefinitions#TBL_AUTHORS}.
     */
    public static final String FK_AUTHOR_PSEUDONYM = "pseudonym";
    /**
     * Foreign key.
     * {@link DBDefinitions#TBL_PSEUDONYM_AUTHOR}.
     * This is a FK to {@link DBDefinitions#TBL_AUTHORS}.
     * Dev. note: We SHOULD just have used "author" for the column name,
     * i.e. we SHOULD have used FK_AUTHOR key.
     */
    public static final String FK_AUTHOR_REAL_AUTHOR = "real_author";

    /** {@link DBDefinitions#TBL_SERIES}. */
    public static final String SERIES_TITLE = "series_name";
    public static final String SERIES_IS_COMPLETE = "series_complete";
    /** {@link DBDefinitions#TBL_BOOK_SERIES}. */
    public static final String BOOK_SERIES_NUMBER = "series_num";
    public static final String BOOK_SERIES_POSITION = "series_position";

    /** {@link DBDefinitions#TBL_PUBLISHERS}. */
    public static final String PUBLISHER_NAME = "publisher_name";
    /** {@link DBDefinitions#TBL_BOOK_PUBLISHER}. */
    public static final String BOOK_PUBLISHER_POSITION = "publisher_position";
    /** Alias: a list of publisher names as a comma separated list. */
    public static final String PUBLISHER_NAMES_AS_CSV = "pub_name_csv";

    /** {@link DBDefinitions#TBL_TOC_ENTRIES}. */
    public static final String BOOK_TOC_ENTRY_POSITION = "toc_entry_position";


    /** Suffix added to a price column name to create a joined currency column. */
    public static final String CURRENCY_SUFFIX = "_currency";


    /** {@link DBDefinitions#TBL_BOOKS}. */
    public static final String DATE_ADDED__UTC = "date_added";
    public static final String DATE_LAST_UPDATED__UTC = "last_update_date";

    public static final String BOOK_UUID = "book_uuid";
    /**
     * {@link DBDefinitions#TBL_BOOKS}, {@link DBDefinitions#TBL_TOC_ENTRIES}.
     * The actual title of the book (as printed on the cover).
     * This will either be a translated title or the original title.
     */
    public static final String TITLE = "title";
    /** The original-language title of a translated book. */
    public static final String TITLE_ORIGINAL_LANG = "title_original_lang";

    public static final String BOOK_ISBN = "isbn";
    /** {@link DBDefinitions#TBL_BOOKS} + {@link DBDefinitions#TBL_TOC_ENTRIES} */
    public static final String FIRST_PUBLICATION__DATE = "first_publication";
    public static final String BOOK_PUBLICATION__DATE = "date_published";
    public static final String PRINT_RUN = "print_run";
    public static final String PRICE_LISTED = "list_price";
    public static final String PRICE_LISTED_CURRENCY = PRICE_LISTED + CURRENCY_SUFFIX;
    /**
     * This field value is <strong>TEXT</strong> data by design to accommodate
     * sites which provide a description of the page structure.
     * Example: "xxxvi+278" -> a book which has 36 roman numerals numbered pages
     * with an introduction, followed by 278 numbered content pages.
     */
    public static final String PAGES = "pages";
    public static final String FORMAT = "format";
    public static final String COLOR = "color";
    public static final String LANGUAGE = "language";
    public static final String DESCRIPTION = "description";

    public static final String EDITION__BITMASK = "edition_bm";
    /**
     * The column name is incorrect for historic reasons.
     *
     * @see Book.ContentType
     */
    public static final String BOOK_CONTENT_TYPE = "anthology";


    /** {@link DBDefinitions#TBL_BOOKS} Personal data. */
    public static final String PRICE_PAID = "price_paid";
    public static final String PRICE_PAID_CURRENCY = PRICE_PAID + CURRENCY_SUFFIX;
    public static final String DATE_ACQUIRED = "date_acquired";
    public static final String LOCATION = "location";
    public static final String READ__BOOL = "read";
    public static final String READ_PROGRESS = "read_progress";
    public static final String READ_START__DATE = "read_start";
    public static final String READ_END__DATE = "read_end";
    public static final String SIGNED__BOOL = "signed";
    /** A rating goes from 1 to 5 stars, in 0.5 increments; 0 == not set. */
    public static final String RATING = "rating";
    public static final String PERSONAL_NOTES = "notes";
    public static final String BOOK_CONDITION = "cond_bk";
    public static final String BOOK_CONDITION_COVER = "cond_cvr";

    /** Flag: the user can 'lock' (i.e. set 'false') a book from being automatically updated. */
    public static final String AUTO_UPDATE = "auto_update";

    /** {@link DBDefinitions#TBL_TAGS}. Localized. */
    public static final String TAG = "tag";

    /** {@link DBDefinitions#TBL_TAG_MAPPINGS}. Localized. */
    public static final String TAG_MAPPING = "mapping";

    /** {@link DBDefinitions#TBL_BOOK_LOANEE}. */
    public static final String LOANEE_NAME = "loaned_to";

    /** Alias. */
    public static final String BOOK_COUNT = "book_count";


    /** Column alias for {@link AuthorWork.Type}. */
    public static final String AUTHOR_WORK_TYPE = "work_type";


    /**
     * All money keys.
     * Used with {@code MONEY_KEYS.contains(key)} to check if a key represents money.
     */
    public static final Set<String> MONEY_KEYS = Set.of(
            PRICE_LISTED,
            PRICE_PAID);

    /**
     * All date keys (i.e. NOT datetime!).
     * Used with {@code DATE_KEYS.contains(key)} to check if a key represents a date.
     */
    public static final Set<String> DATE_KEYS = Set.of(
            BOOK_PUBLICATION__DATE,
            FIRST_PUBLICATION__DATE,
            DATE_ACQUIRED,
            READ_START__DATE,
            READ_END__DATE);

    /**
     * All datetime keys (i.e. NOT date!).
     */
    public static final Set<String> DATETIME_KEYS = Set.of(
            DATE_LAST_UPDATED__UTC,
            DATE_ADDED__UTC);

    /** Suffix added to a column name to create a specific 'order by' copy of that column. */
    private static final String ORDER_BY_SUFFIX = "_ob";
    public static final String AUTHOR_FAMILY_NAME_OB = AUTHOR_FAMILY_NAME + ORDER_BY_SUFFIX;
    public static final String AUTHOR_GIVEN_NAMES_OB = AUTHOR_GIVEN_NAMES + ORDER_BY_SUFFIX;
    public static final String SERIES_TITLE_OB = SERIES_TITLE + ORDER_BY_SUFFIX;
    public static final String PUBLISHER_NAME_OB = PUBLISHER_NAME + ORDER_BY_SUFFIX;
    public static final String TITLE_OB = TITLE + ORDER_BY_SUFFIX;

    /**
     * The "field is used" key for thumbnails and other places where we need
     * to represent a cover.
     * There is NO VALUE linked to this key.
     */
    private static final String PREFIX_COVER = "thumbnail";
    public static final String[] COVER = {
            PREFIX_COVER + ".0",
            PREFIX_COVER + ".1"
    };

    private DBKey() {
    }

    /**
     * {@link DBDefinitions#TBL_BOOKLIST_STYLES}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class STYLE {

        public static final String UUID = "uuid";
        /**
         * The name for a {@link com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle}.
         */
        public static final String NAME = "name";
        /**
         * The type of Style.
         * <p>
         * Note the actual name of the column is incorrect.
         * This used to be a boolean (0=user, 1=builtin).
         *
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.Type
         */
        public static final String TYPE = "builtin";
        public static final String IS_PREFERRED = "preferred";
        public static final String MENU_POSITION = "menu_order";
        /**
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.Layout
         */
        public static final String LAYOUT = "layout";
        /**
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.CoverClickAction
         */
        public static final String COVER_CLICK_ACTION = "cover_click_action";
        /**
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.CoverLongClickAction
         */
        public static final String COVER_LONG_CLICK_ACTION = "cover_long_click_action";
        public static final String EXP_LEVEL = "exp_level";
        public static final String ROW_USES_PREF_HEIGHT = "row_pref_height";
        public static final String AUTHOR_SORT_BY_GIVEN_NAME = "author_sort_gn";
        public static final String AUTHOR_SHOW_BY_GIVEN_NAME = "author_show_gn";
        public static final String TITLE_SHOW_REORDERED = "show_reorder_title";
        public static final String READ_STATUS_WITH_PROGRESS = "read_status_with_progress";
        public static final String CITATION_TYPE = "citation_style";
        /**
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.TextScale
         */
        public static final String TEXT_SCALE = "text_scale";
        /**
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale
         */
        public static final String COVER_SCALE = "cover_scale";
        /**
         * @see com.hardbacknutter.nevertoomanybooks.booklist.header.BooklistHeader
         */
        public static final String LIST_HEADER = "list_header";
        public static final String BOOK_DETAIL_FIELD_VISIBILITY = "detail_fields_vis";
        public static final String BOOK_LIST_FIELD_VISIBILITY = "list_fields_vis";
        public static final String BOOK_LIST_FIELD_ORDER_BY = "list_fields_sort";
        public static final String GROUPS = "groups";
        public static final String GROUPS_AUTHOR_SHOW_UNDER_EACH =
                "groups_author_under_each";
        public static final String GROUPS_AUTHOR_PRIMARY_TYPE =
                "groups_author_prim_type";
        public static final String GROUPS_SERIES_SHOW_UNDER_EACH =
                "groups_series_under_each";
        public static final String GROUPS_PUBLISHER_SHOW_UNDER_EACH =
                "groups_publisher_under_each";
        public static final String GROUPS_BOOKSHELF_SHOW_UNDER_EACH =
                "groups_bookshelf_under_each";

        private STYLE() {
        }
    }

    @SuppressWarnings("CheckStyle")
    public static final class BL_NODE {

        /** {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE}. */
        public static final String KEY = "node_key";
        /** {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE}. */
        public static final String LEVEL = "node_level";
        public static final String GROUP = "node_group";
        public static final String VISIBLE = "node_visible";
        public static final String EXPANDED = "node_expanded";
        /**
         * Column alias.
         * <p>
         * Booklist: an alias for the rowId
         * listViewRowPosition = BL_LIST_VIEW_ROW_ID - 1.
         * <p>
         * See {@link com.hardbacknutter.nevertoomanybooks.booklist.Booklist}#sqlGetOffsetCursor
         */
        public static final String ROW_ID = "lv_node_row_id";

        private BL_NODE() {
        }
    }

    public static final class FTS {

        /** FTS Primary key. */
        public static final String PK_BOOK_ID = "docid";
        /**
         * {@link DBDefinitions#TBL_FTS_BOOKS}. Semi-colon concatenated Authors.
         * Example: "stephen baxter;arthur c. clarke;"
         */
        public static final String AUTHOR_NAME = "author_name";
        /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semi-colon concatenated Series. */
        public static final String SERIES_NAMES = "series_name";
        /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semi-colon concatenated Publishers. */
        public static final String PUBLISHER_NAMES = "publisher_name";
        /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semi-colon concatenated TOC titles. */
        public static final String TOC_ENTRY_TITLE = "toc_title";

        private FTS() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_IDENTIFIERS}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class IDENTIFIERS {

        /** The keyword. Not localized. */
        public static final String KEY = "key";
        /** Type: a char: S or L. */
        public static final String TYPE = "type";
        /** A short name; i.e. website name. Not Localized. */
        public static final String NAME = "name";
        /** URL to the main page of the site. */
        public static final String SITE_URL = "site_url";
        /**
         * <strong>URI</strong> with a "%s" taking a sid.
         * Typically a url, but we allow/use all uri style values.
         */
        public static final String BOOK_URI = "book_uri";

        private IDENTIFIERS() {
        }
    }

    @SuppressWarnings("CheckStyle")
    public static final class CALIBRE {

        /** {@link DBDefinitions#TBL_CALIBRE_CUSTOM_FIELDS}. */
        public static final String CUSTOM_FIELD_NAME = "clb_cf_name";
        public static final String CUSTOM_FIELD_TYPE = "clb_cf_type";
        public static final String CUSTOM_FIELD_MAPPING = "clb_cf_mapping";
        /** {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}. */
        public static final String LIBRARY_LAST_SYNC_DATE__UTC = "clb_lib_last_sync";
        public static final String LIBRARY_STRING_ID = "clb_lib_id";
        public static final String LIBRARY_UUID = "clb_lib_uuid";
        /**
         * {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}
         * {@link DBDefinitions#TBL_CALIBRE_VIRTUAL_LIBRARIES}.
         */
        public static final String LIBRARY_NAME = "clb_lib_name";
        /** {@link DBDefinitions#TBL_CALIBRE_VIRTUAL_LIBRARIES}. */
        public static final String VIRT_LIB_EXPR = "clb_vlib_expr";
        /**
         * External to this app, but NOT an "external Site id"
         * as it comes from a user importing their Calibre libraries.
         * {@link DBDefinitions#TBL_CALIBRE_BOOKS}.
         */
        public static final String BOOK_ID = "clb_book_id";
        public static final String BOOK_UUID = "clb_book_uuid";
        public static final String BOOK_MAIN_FORMAT = "clb_book_main_format";

        private CALIBRE() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_STRIPINFO_COLLECTION}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class STRIP_INFO {

        /**
         * The book id (sid).
         * This is a (redundant) copy of the same value stored in
         * {@link DBDefinitions#TBL_BOOK_IDENTIFIER} for
         * {@link com.hardbacknutter.nevertoomanybooks.entities.Identifier#SID_STRIP_INFO}.
         */
        public static final String BOOK_ID = "si_book_id";
        /**
         * The "CollectieId"; a secondary id used by the website for all books flagged
         * as being in the users collection. This is the case as soon as they set "some"
         * private date/flags on it.
         */
        public static final String COLLECTION_ID = "si_coll_id";
        /** The user wants this book. */
        public static final String WANTED = "si_coll_wanted";
        /** Owned as a physical book. */
        public static final String OWNED = "si_coll_owned";
        /** Owned as a digital book. */
        public static final String DIGITAL = "si_coll_digital";
        /** The amount of copies owned. */
        public static final String AMOUNT = "si_coll_amount";
        /** DateTimeStamp of last sync. */
        public static final String LAST_SYNC_DATE__UTC = "si_coll_last_sync";

        private STRIP_INFO() {
        }
    }
}
