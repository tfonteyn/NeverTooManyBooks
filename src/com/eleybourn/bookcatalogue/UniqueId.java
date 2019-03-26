package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_OPEN_LIBRARY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Global String constants.
 */
public final class UniqueId {

    /** Activity Request Code. */
    public static final int REQ_NAV_PANEL_EDIT_PREFERRED_STYLES = 1_001;
    /** Activity Request Code. */
    public static final int REQ_NAV_PANEL_ADMIN = 1_002;
    /** Activity Request Code. */
    public static final int REQ_NAV_PANEL_SETTINGS = 1_003;
    /** Activity Request Code. */
    public static final int REQ_NAV_PANEL_EDIT_BOOKSHELVES = 1_004;

    /** Activity Request Code. */
    @SuppressWarnings("WeakerAccess")
    public static final int REQ_BOOK_EDIT = 2_000;
    /** Activity Request Code. */
    @SuppressWarnings("WeakerAccess")
    public static final int REQ_BOOK_DUPLICATE = 2_001;

    /** Activity Request Code. */
    @SuppressWarnings("WeakerAccess")
    public static final int REQ_ANDROID_PERMISSIONS = 9_000;

    /** generic delete. */
    public static final int ACTIVITY_RESULT_DELETED_SOMETHING = 10_001;
    /** {@link PreferredStylesActivity} need distinct code as it can trickle up. */
    public static final int ACTIVITY_RESULT_OK_BooklistPreferredStyles = 10_101;
    /** need distinct code as it can trickle up. */
    public static final int ACTIVITY_RESULT_OK_BooklistStyleProperties = 10_102;

    /** result code from an Activity received in onActivityResult
     * indicating we should restart.
     */
    public static final int ACTIVITY_RESULT_RESTART_ON_RESUME = 10_200;

    /**
     * Bundle keys for ParcelableArrayList<Entity>.
     */
    public static final String BKEY_AUTHOR_ARRAY = "author_array";
    public static final String BKEY_SERIES_ARRAY = "series_array";
    public static final String BKEY_TOC_ENTRY_ARRAY = "toc_titles_array";
    public static final String BKEY_BOOKSHELF_ARRAY = "bookshelf_array";


    /** generic search text. */
    public static final String BKEY_SEARCH_TEXT = "searchText";
    /** author search text. */
    public static final String BKEY_SEARCH_AUTHOR = "searchAuthor";

    /**
     * Search site flags as in {@link com.eleybourn.bookcatalogue.searches.SearchSites}.
     * <p>
     * int (bitmask)
     */
    public static final String BKEY_SEARCH_SITES = "searchFlags";

    /**
     * Single fileSpecs or uri.
     * <p>
     * String
     */
    public static final String BKEY_FILE_SPEC = "fileSpec";

    /**
     * List of fileSpecs or uri.
     * <p>
     * ArrayList<String>
     */
    public static final String BKEY_FILE_SPEC_ARRAY = "fileSpec_array";

    /**
     * A generic layout resource id.
     * <p>
     * int (LayoutId)
     */
    public static final String BKEY_LAYOUT_ID = "layoutId";

    /**
     * The title to be used by generic Dialogs.
     * <p>
     * int (stringId)
     */
    public static final String BKEY_DIALOG_TITLE = "dialogTitle";

    /**
     * The message id to be used by generic Dialogs.
     * <p>
     * int (stringId)
     */
    public static final String BKEY_DIALOG_MSG_ID = "dialogMsgId";

    /**
     * The message to be used by generic Dialogs.
     * <p>
     * String
     */
    public static final String BKEY_DIALOG_MSG = "dialogMsg";

    /**
     * Identifier for the caller of a generic Dialog.
     * <p>
     * String (often/always the fragment TAG)
     */
    public static final String BKEY_CALLER_TAG = "dialogCallerId";

    /**
     * Identifier of the field we want the generic Dialog to handle.
     * <p>
     * int (resource id)
     */
    public static final String BKEY_FIELD_ID = "fieldId";

    /**
     * Bundle key to pass a Bundle with book data around.
     * i.e. before the data becomes an actual {@link Book}.
     * <p>
     * Bundle
     */
    public static final String BKEY_BOOK_DATA = "bookData";

    /**
     * Bundle key to pass a generic {@link java.util.ArrayList<Integer>} around.
     * <p>
     * IntegerArrayList
     */
    public static final String BKEY_ID_LIST = "idList";

    /**
     * 3 uses:
     * <p>
     * - Indicate if we 'have' a thumbnail (in which case {@link StorageUtils#getTempCoverFile()}
     * will point to that image.
     * <p>
     * - Flag to indicate we 'want' a thumbnail, in {@link Fields.FieldUsage.Usage}
     * - Visibility flag
     * <p>
     * boolean
     */
    public static final String BKEY_COVER_IMAGE = "thumbnail";

    /**
     * tag of fragment to display if an Activity supports multiple.
     * <p>
     * String
     */
    public static final String BKEY_FRAGMENT_TAG = "fragment";

    /**
     * The resulting {@link ImportSettings#what} flags after an import.
     * <p>
     * int (bitmask)
     * setResult
     */
    public static final String BKEY_IMPORT_RESULT = "importResult";

    /**
     * The resulting {@link ExportSettings#what} flags after an export.
     * <p>
     * int (bitmask)
     * setResult
     */
    public static final String BKEY_EXPORT_RESULT = "exportResult";

    /**
     * {@link ExportSettings} or {@link ImportSettings}.
     * <p>
     * Parcel
     */
    public static final String BKEY_IMPORT_EXPORT_SETTINGS = "importExportSettings";

    /**
     * Indicate the called activity made global changes.
     * <p>
     * boolean
     * setResult
     */
    public static final String BKEY_GLOBAL_CHANGES_MADE = "globalChanges";

    /**
     * Return the status of a startActivityForResult when a task was 'isCancelled'.
     * <p>
     * boolean
     * setResult
     */
    public static final String BKEY_CANCELED = "cancelled";

    /* ****************************************************************************************** */
    public static final String KEY_ID = DOM_PK_ID.name;

    public static final String KEY_AUTHOR = DOM_FK_AUTHOR_ID.name;
    public static final String KEY_AUTHOR_FAMILY_NAME = DOM_AUTHOR_FAMILY_NAME.name;
    public static final String KEY_AUTHOR_GIVEN_NAMES = DOM_AUTHOR_GIVEN_NAMES.name;
    public static final String KEY_AUTHOR_FORMATTED = DOM_AUTHOR_FORMATTED.name;
    public static final String KEY_AUTHOR_IS_COMPLETE = DOM_AUTHOR_IS_COMPLETE.name;

    public static final String KEY_BOOKSHELF = DOM_BOOKSHELF.name;

    public static final String KEY_SERIES = DOM_SERIES_NAME.name;
    public static final String KEY_SERIES_NUM = DOM_BOOK_SERIES_NUM.name;
    public static final String KEY_SERIES_IS_COMPLETE = DOM_SERIES_IS_COMPLETE.name;

    public static final String KEY_ISBN = DOM_BOOK_ISBN.name;
    public static final String KEY_BOOK_UUID = DOM_BOOK_UUID.name;
    public static final String KEY_BOOK_GR_LAST_SYNC_DATE = DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name;
    public static final String KEY_BOOK_GOODREADS_ID = DOM_BOOK_GOODREADS_BOOK_ID.name;
    public static final String KEY_LIBRARY_THING_ID = DOM_BOOK_LIBRARY_THING_ID.name;
    public static final String KEY_ISFDB_ID = DOM_BOOK_ISFDB_ID.name;
    public static final String KEY_OPEN_LIBRARY_ID = DOM_BOOK_OPEN_LIBRARY_ID.name;

    public static final String KEY_DATE_ADDED = DOM_BOOK_DATE_ADDED.name;
    public static final String KEY_DATE_ACQUIRED = DOM_BOOK_DATE_ACQUIRED.name;
    public static final String KEY_DATE_FIRST_PUBLISHED = DOM_FIRST_PUBLICATION.name;
    public static final String KEY_DATE_LAST_UPDATED = DOM_LAST_UPDATE_DATE.name;
    public static final String KEY_DATE_PUBLISHED = DOM_BOOK_DATE_PUBLISHED.name;
    public static final String KEY_DESCRIPTION = DOM_BOOK_DESCRIPTION.name;
    public static final String KEY_EDITION_BITMASK = DOM_BOOK_EDITION_BITMASK.name;
    public static final String KEY_FORMAT = DOM_BOOK_FORMAT.name;
    public static final String KEY_GENRE = DOM_BOOK_GENRE.name;
    public static final String KEY_LANGUAGE = DOM_BOOK_LANGUAGE.name;
    public static final String KEY_LOCATION = DOM_BOOK_LOCATION.name;
    public static final String KEY_LOANEE = DOM_BOOK_LOANEE.name;
    public static final String KEY_NOTES = DOM_BOOK_NOTES.name;
    public static final String KEY_PAGES = DOM_BOOK_PAGES.name;
    public static final String KEY_PRICE_LISTED = DOM_BOOK_PRICE_LISTED.name;
    public static final String KEY_PRICE_LISTED_CURRENCY = DOM_BOOK_PRICE_LISTED_CURRENCY.name;
    public static final String KEY_PRICE_PAID = DOM_BOOK_PRICE_PAID.name;
    public static final String KEY_PRICE_PAID_CURRENCY = DOM_BOOK_PRICE_PAID_CURRENCY.name;
    public static final String KEY_PUBLISHER = DOM_BOOK_PUBLISHER.name;
    public static final String KEY_RATING = DOM_BOOK_RATING.name;
    public static final String KEY_READ = DOM_BOOK_READ.name;
    public static final String KEY_READ_START = DOM_BOOK_READ_START.name;
    public static final String KEY_READ_END = DOM_BOOK_READ_END.name;
    public static final String KEY_SIGNED = DOM_BOOK_SIGNED.name;
    public static final String KEY_TITLE = DOM_TITLE.name;
    public static final String KEY_TOC_BITMASK = DOM_BOOK_ANTHOLOGY_BITMASK.name;

    private UniqueId() {
    }
}
