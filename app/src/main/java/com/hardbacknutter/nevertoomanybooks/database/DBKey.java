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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.booklist.style.ScreenLayout;
import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

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
    /**
     * Foreign key.
     * {@link DBDefinitions#TBL_PSEUDONYM_AUTHOR}.
     * References {@link DBDefinitions#TBL_AUTHORS}.
     */
    public static final String FK_AUTHOR_PSEUDONYM = "pseudonym";
    /**
     * Foreign key.
     * {@link DBDefinitions#TBL_PSEUDONYM_AUTHOR}.
     * References {@link DBDefinitions#TBL_AUTHORS}.
     * Dev. note: We SHOULD have used FK_AUTHOR key.
     */
    public static final String FK_AUTHOR_REAL_AUTHOR = "real_author";

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

    /** Suffix added to a price column name to create a joined currency column. */
    public static final String CURRENCY_SUFFIX = "_currency";

    /** {@link DBDefinitions#TBL_BOOKS}. */
    public static final String DATE_ADDED__UTC = "date_added";
    public static final String DATE_LAST_UPDATED__UTC = "last_update_date";

    /**
     * {@link DBDefinitions#TBL_BOOKS}.
     * Formatted as a <strong>16 character hex string</strong>, i.e. there are NO '-' separators.
     */
    public static final String BOOK_UUID = "book_uuid";
    /**
     * {@link DBDefinitions#TBL_BOOKS}, {@link DBDefinitions#TBL_TOC_ENTRIES}.
     * The actual title of the book (as printed on the cover).
     * This will either be a translated title or the original title.
     */
    public static final String TITLE = "title";
    /** The original title of a translated book. */
    public static final String TRANSLATION_ORIGINAL_TITLE = "title_original_lang";
    /** The original language of a translated book. */
    public static final String TRANSLATION_ORIGINAL_LANGUAGE = "translation_orig_lang";

    public static final String COLOR = "color";
    /**
     * The column name is incorrect for historic reasons.
     *
     * @see com.hardbacknutter.nevertoomanybooks.entities.Book.ContentType
     */
    public static final String CONTENT_TYPE = "anthology";
    public static final String DESCRIPTION = "description";
    /**
     * Bitmask value for the edition(s).
     *
     * @see com.hardbacknutter.nevertoomanybooks.entities.Book.Edition
     */
    public static final String EDITION_FLAGS = "edition_bm";
    /**
     * Free-form field for extra information about the edition of the user's copy.
     * (e.g. exact impression)
     */
    public static final String EDITION_INFO = "edition_info";

    public static final String FORMAT = "format";
    /** {@link DBDefinitions#TBL_BOOKS} + {@link DBDefinitions#TBL_TOC_ENTRIES}. */
    public static final String FIRST_PUBLICATION_DATE = "first_publication";
    public static final String ISBN = "isbn";
    public static final String LANGUAGE = "language";
    /**
     * This field value is <strong>TEXT</strong> data by design to accommodate
     * sites which provide a description of the page structure.
     * Example: "xxxvi+278" -> a book which has 36 roman numerals numbered pages
     * with an introduction, followed by 278 numbered content pages.
     */
    public static final String PAGES = "pages";

    /** REAL, i.e. a BigDecimal, always a numeric value. */
    public static final String PRICE_LISTED = "list_price";
    /** Iso3 code, or a displayble String for unknown currencies. */
    public static final String PRICE_LISTED_CURRENCY = PRICE_LISTED + CURRENCY_SUFFIX;
    /** Info on limited editions, circulation numbers, etc... */
    public static final String PRINT_RUN = "print_run";
    public static final String PUBLICATION_DATE = "date_published";

    /**
     * {@link DBDefinitions#TBL_BOOKS} Personal data.
     */
    public static final String CONDITION_BOOK = "cond_bk";
    public static final String CONDITION_COVER = "cond_cvr";
    /** REAL, i.e. a BigDecimal, always a numeric value. */
    public static final String PRICE_PAID = "price_paid";
    /** Iso3 code, or a displayble String for unknown currencies. */
    public static final String PRICE_PAID_CURRENCY = PRICE_PAID + CURRENCY_SUFFIX;
    public static final String DATE_ACQUIRED = "date_acquired";
    public static final String PERSONAL_NOTES = "notes";
    public static final String LOCATION = "location";
    /** A rating goes from 1 to 5 stars, in 0.5 increments; 0 == not set. */
    public static final String RATING = "rating";
    public static final String READ__BOOL = "read";
    /**
     * A JSON object containing pages or percentage.
     *
     * @see ReadingProgress
     */
    public static final String READ_PROGRESS = "read_progress";
    public static final String READ_START__DATE = "read_start";
    public static final String READ_END__DATE = "read_end";
    public static final String SIGNED__BOOL = "signed";

    /**
     * Flag: the user can 'lock' (i.e. set 'false') a book from being automatically updated.
     */
    public static final String AUTO_UPDATE = "auto_update";

    /** {@link DBDefinitions#TBL_BOOK_TOC_ENTRIES}. */
    public static final String BOOK_TOC_ENTRY_POSITION = "toc_entry_position";

    /** {@link DBDefinitions#TBL_BOOK_LOANEE}. */
    public static final String LOANEE_NAME = "loaned_to";

    /** Alias. */
    public static final String BOOK_COUNT = "book_count";

    /**
     * Column alias.
     *
     * @see com.hardbacknutter.nevertoomanybooks.entities.AuthorWork.Type
     */
    public static final String AUTHOR_WORK_TYPE = "work_type";

    /** The number of supported Book images. */
    public static final int NR_OF_BOOK_COVERS = 4;
    /**
     * The "field is used" key for covers and other places where we need
     * to represent an image.
     * There is NO VALUE linked to this key.
     */
    public static final String[] COVER = new String[NR_OF_BOOK_COVERS];
    /** As used for {@link #COVER} with a ".x" added for the number. */
    private static final String PREFIX_COVER = "thumbnail";

    /** Suffix added to a column name to create a specific 'order by' copy of that column. */
    private static final String ORDER_BY_SUFFIX = "_ob";
    /** {@link DBDefinitions#TBL_BOOKS} + {@link DBDefinitions#TBL_BOOK_TOC_ENTRIES}. */
    public static final String TITLE_OB = TITLE + ORDER_BY_SUFFIX;

    private static final Set<String> MONEY_KEYS = Set.of(
            PRICE_LISTED,
            PRICE_PAID);
    private static final Set<String> LANGUAGE_KEYS = Set.of(
            LANGUAGE,
            TRANSLATION_ORIGINAL_LANGUAGE);
    private static final Set<String> DATE_KEYS_PARTIAL = Set.of(
            PUBLICATION_DATE,
            FIRST_PUBLICATION_DATE,
            DATE_ACQUIRED);
    private static final Set<String> DATE_KEYS_FULL = Set.of(
            READ_START__DATE,
            READ_END__DATE);
    private static final Set<String> DATE_KEYS;
    private static final Set<String> DATETIME_KEYS = Set.of(
            DATE_LAST_UPDATED__UTC,
            DATE_ADDED__UTC);

    static {
        final Set<String> tmp = new HashSet<>(DATE_KEYS_PARTIAL);
        tmp.addAll(DATE_KEYS_FULL);
        DATE_KEYS = Collections.unmodifiableSet(tmp);

        for (int cIdx = 0; cIdx < NR_OF_BOOK_COVERS; cIdx++) {
            COVER[cIdx] = PREFIX_COVER + '.' + cIdx;
        }
    }

    private DBKey() {
    }

    /**
     * All money keys.
     *
     * @return keys
     */
    @NonNull
    public static Set<String> getMoneyKeys() {
        return MONEY_KEYS;
    }

    /**
     * All language keys.
     *
     * @return keys
     */
    @NonNull
    public static Set<String> getLanguageKeys() {
        return LANGUAGE_KEYS;
    }

    /**
     * All the <strong>partial</strong> date field keys.
     *
     * @return keys
     *
     * @see #getDateKeys()
     */
    @NonNull
    public static Set<String> getPartialDateKeys() {
        return DATE_KEYS_PARTIAL;
    }

    /**
     * All the <strong>full</strong> date field keys.
     *
     * @return keys
     *
     * @see #getDateKeys()
     */
    @NonNull
    public static Set<String> getFullDateKeys() {
        return DATE_KEYS_FULL;
    }

    /**
     * All date keys (i.e. NOT datetime!).
     * <p>
     * Combines the keys from {@link #getFullDateKeys()} and {@link #getPartialDateKeys()}.
     *
     * @return keys
     */
    @NonNull
    public static Set<String> getDateKeys() {
        return DATE_KEYS;
    }

    /**
     * All datetime keys (i.e. NOT date!).
     *
     * @return keys
     */
    @NonNull
    public static Set<String> getDateTimeKeys() {
        return DATETIME_KEYS;
    }

    /**
     * {@link DBDefinitions#TBL_BOOKSHELF}.
     * {@link DBDefinitions#TBL_BOOKSHELF_FILTERS}.
     * {@link DBDefinitions#TBL_BOOK_BOOKSHELF}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class BOOKSHELF {

        /** The name of the bookshelf. */
        public static final String NAME = "bookshelf_name";
        /** The booklist adapter position of current top row. */
        public static final String BL_TOP_POS = "bl_top_pos";
        /** The booklist adapter top row offset from view top. */
        public static final String BL_TOP_OFFSET = "bl_top_offset";

        /** Alias: a list of bookshelf names as a comma separated list. */
        public static final String BOOK_BOOKSHELF_NAMES_AS_CSV = "bs_name_csv";

        /** The name == DBKey of the field we'll filter on. */
        public static final String FILTER_NAME = "filter_name";
        /** The value depends on the name/DBKey. */
        public static final String FILTER_VALUE = "filter_value";

        private BOOKSHELF() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_AUTHORS}.
     * {@link DBDefinitions#TBL_BOOK_AUTHOR}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class AUTHOR {

        /** The name, as entered. */
        public static final String FAMILY_NAME = "family_name";
        /** The reordered name as per user preference for sorting. */
        public static final String FAMILY_NAME_OB = FAMILY_NAME + ORDER_BY_SUFFIX;
        /** The names, as entered. */
        public static final String GIVEN_NAMES = "given_names";
        /** The reordered name as per user preference for sorting. */
        public static final String GIVEN_NAMES_OB = GIVEN_NAMES + ORDER_BY_SUFFIX;

        /** ISO (Partial) date string. */
        public static final String BIRTH_DATE = "birth_date";
        /** ISO (Partial) date string. */
        public static final String DEATH_DATE = "death_date";
        /**
         * Formatted as a 20 character UUID string, i.e. with 4 '-' separators.
         */
        public static final String PICTURE_UUID = "pic_uuid";

        /** Users "author is complete" flag. */
        public static final String COMPLETE = "author_complete";
        /**
         * Virtual column.
         * <p>
         * The first/family name order is determined in the SQL statement.
         * Hence, reading the data from the cursor is <strong>always</strong>
         * done using this key.
         */
        public static final String FORMATTED_FULL_NAME = "author_formatted";
        /**
         * Virtual column: "GivenName FamilyName".
         * <p>
         * Only used for a special case when searching for a book by text.
         *
         * @see com.hardbacknutter.nevertoomanybooks.search.SearchBookByTextFragment
         */
        public static final String FORMATTED_FULL_NAME_GIVEN_FIRST = "author_formatted_given_first";

        /** Bitmask of the role(s) of this author for a book. */
        public static final String BOOK_AUTHOR_ROLE = "author_type";
        /** The position in the ordered list of Authors for a book. */
        public static final String BOOK_AUTHOR_POSITION = "author_position";

        private AUTHOR() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_SERIES}.
     * {@link DBDefinitions#TBL_BOOK_SERIES}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class SERIES {

        /** The name, as entered. */
        public static final String TITLE = "series_name";
        /** The reordered name as per user preference for sorting. */
        public static final String TITLE_OB = TITLE + ORDER_BY_SUFFIX;
        /** Users "series is complete" flag. */
        public static final String COMPLETE = "series_complete";

        /** Nr of a book in this series. */
        public static final String BOOK_SERIES_NUMBER = "series_num";
        /** The position in the ordered list of series for a book. */
        public static final String BOOK_SERIES_POSITION = "series_position";

        private SERIES() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_PUBLISHERS}.
     * {@link DBDefinitions#TBL_BOOK_PUBLISHER}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class PUBLISHER {

        /** The name, as entered. */
        public static final String NAME = "publisher_name";
        /** The reordered name as per user preference for sorting. */
        public static final String NAME_OB = NAME + ORDER_BY_SUFFIX;

        /** The position in the ordered list of publishers for a book. */
        public static final String BOOK_PUBLISHER_POSITION = "publisher_position";
        /** Alias: a list of publisher {@link #NAME}s as a comma separated list. */
        public static final String BOOK_PUBLISHER_NAMES_AS_CSV = "pub_name_csv";

        private PUBLISHER() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_SERIES_PUBLICATION_FREQUENCY}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class PUBLICATION_FREQUENCY {
        /** {@link com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency} id.*/
        public static final String TYPE = "pub_freq_type";
        public static final String CADENCE = "pub_freq_cadence";
        public static final String IS_ORDINAL = "pub_freq_is_ordinal";

        private PUBLICATION_FREQUENCY() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_BOOKLIST_STYLES}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class STYLE {
        //NEWTHINGS: style option: add dbkey

        /**
         * Formatted as a 20 character UUID string, i.e. with 4 '-' separators.
         */
        public static final String UUID = "uuid";
        /**
         * The name for a user defined style.
         *
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle
         */
        public static final String NAME = "name";
        /**
         * The type of Style.
         * <p>
         * Note the actual name of the column is incorrect.
         * This used to be a boolean (0=user, 1=built-in).
         *
         * @see com.hardbacknutter.nevertoomanybooks.booklist.style.Style.Type
         */
        public static final String TYPE = "builtin";
        public static final String IS_PREFERRED = "preferred";
        public static final String MENU_POSITION = "menu_order";
        /**
         * @see ScreenLayout
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
        public static final String SHOW_GROUP_BOOK_COUNT = "show_group_book_count";
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
        public static final String GROUPS_AUTHOR_PRIMARY_ROLE =
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

    @SuppressWarnings("CheckStyle")
    public static final class LANG_MAPPING {
        public static final String ISO3_USER = "iso3_user";
        public static final String ISO3 = "iso3";
        public static final String DISPLAY_NAME = "dname";

        private LANG_MAPPING() {
        }
    }

    public static final class FTS {

        /** FTS Primary key. */
        public static final String PK_BOOK_ID = "docid";
        /**
         * {@link DBDefinitions#TBL_FTS_BOOKS}. Semicolon concatenated Authors.
         * Example: "stephen baxter;arthur c. clarke;"
         */
        public static final String AUTHOR_NAME = "author_name";
        /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semicolon concatenated Series. */
        public static final String SERIES_NAMES = "series_name";
        /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semicolon concatenated Publishers. */
        public static final String PUBLISHER_NAMES = "publisher_name";
        /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semicolon concatenated TOC titles. */
        public static final String TOC_ENTRY_TITLE = "toc_title";

        private FTS() {
        }
    }

    /**
     * {@link DBDefinitions#TBL_IDENTIFIERS}.
     */
    @SuppressWarnings("CheckStyle")
    public static final class IDENTIFIERS {

        /** The keyword. Not localised. */
        public static final String KEY = "key";
        /**
         * The (sub)entity this Identifier is valid for.
         *
         * @see Identifier.EntityType
         */
        public static final String ENTITY = "entity";

        /**
         * Type: a char: S==String or L==Long.
         *
         * @see Identifier.Type
         */
        public static final String TYPE = "type";
        /** A short name; i.e. website name. Not Localized. */
        public static final String NAME = "name";

        /** The WikiData claim, i.e. "P" number. */
        public static final String WIKIDATA_CLAIM = "wikidata_p";
        /** URL to the main page of the site. */
        public static final String SITE_URL = "site_url";
        /**
         * <strong>URI</strong> with an "%s" taking a sid.
         * Typically, a url, but we allow/use all uri style values.
         */
        public static final String URI = "uri";
        /**
         * {@link DBDefinitions#TBL_BOOK_IDENTIFIER},
         * {@link DBDefinitions#TBL_AUTHOR_IDENTIFIER}.
         * The external site id.
         */
        public static final String SID = "sid";

        private IDENTIFIERS() {
        }
    }

    public static final class TAGS {

        /**
         * {@link DBDefinitions#TBL_TAGS}.
         * {@link DBDefinitions#TBL_TAG_MAPPINGS}.
         * <p>
         * The tag itself + the name of the tag-mapping.
         * The tables are linked by this text field in joins,
         * but NOT enforced by reference.
         * <p>
         * Localized.
         */
        public static final String TAG = "tag";
        /**
         * {@link DBDefinitions#TBL_TAG_MAPPINGS}.
         * <p>
         * The encoded list of mappings.
         * <p>
         * Localized.
         */
        public static final String TAG_MAPPING = "mapping";

        private TAGS() {
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
        /** {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}. */
        public static final String LIBRARY_STRING_ID = "clb_lib_id";
        /**
         * {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}.
         * Formatted as a 20 character UUID string, i.e. with 4 '-' separators.
         */
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
         * as being in the users' collection. This is the case as soon as they set "some"
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
