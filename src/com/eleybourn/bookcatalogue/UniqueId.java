package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

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

    private UniqueId() {
    }
}
