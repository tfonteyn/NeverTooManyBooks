package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.settings.FieldVisibilitySettingsFragment;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANEE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Global String constants.
 */
public final class UniqueId {

    // BEGIN RequestCodes used in more then one class for startActivityForResult / onActivityResult

    public static final int REQ_NAV_PANEL_EDIT_PREFERRED_STYLES = 1_001;
    public static final int REQ_NAV_PANEL_ADMIN = 1_002;
    public static final int REQ_NAV_PANEL_EDIT_BOOKSHELVES = 1_003;
    /** generic delete. */
    public static final int ACTIVITY_RESULT_DELETED_SOMETHING = 10_001;
    /** {@link PreferredStylesActivity} need distinct code as it can trickle up. */
    public static final int ACTIVITY_RESULT_OK_BooklistPreferredStylesActivity = 10_101;
    /** need distinct code as it can trickle up. */
    public static final int ACTIVITY_RESULT_OK_BooklistStylePropertiesActivity = 10_102;
    // END Request Codes used for startActivityForResult / onActivityResult

    // BEGIN Result Codes used for setResult / onActivityResult
    /**
     * If set when calling startActivity, it will override the default layout for that activity.
     * This is done in {@link BaseActivity}
     */
    public static final String BKEY_LAYOUT = "layout";
    /** used to pass a Bundle with book data around; e.g. before becoming an actual {@link Book}. */
    public static final String BKEY_BOOK_DATA = "bookData";
    /** bundle key to pass an {@link java.util.ArrayList<Integer>} around. */
    public static final String BKEY_BOOK_ID_LIST = "bookIdList";


    // END Result Codes used for setResult / onActivityResult
    /** Bundle keys for ParcelableArrayList<Entity>. */
    public static final String BKEY_AUTHOR_ARRAY = "author_array";
    public static final String BKEY_SERIES_ARRAY = "series_array";
    public static final String BKEY_TOC_TITLES_ARRAY = "toc_titles_array";
    public static final String BKEY_BOOKSHELF_ARRAY = "bookshelf_array";
    /* BKEY_* and BVAL_* which are used in more then one class should be moved here. */
    public static final String BKEY_DIALOG_TITLE = "dialogTitle";
    public static final String BKEY_FIELD_ID = "fieldId";
    public static final String BKEY_CALLER_ID = "dialogCallerId";
    /** ArrayList<String> of fileSpecs of thumbnails fetches from the internet. */
    public static final String BKEY_THUMBNAIL_FILE_SPEC_ARRAY = "thumbnail_file_spec_array";
    /**
     * 3 uses.
     * Boolean indicating if we have a thumbnail or not.
     * Visibility indicator, see {@link FieldVisibilitySettingsFragment}
     * Flag to indicate we 'want' a thumbnail, in {@link Fields.FieldUsage.Usage}
     */
    public static final String BKEY_HAVE_THUMBNAIL = "thumbnail";
    /** a generic filename or uri. */
    public static final String BKEY_FILE_SPEC = "fileSpec";
    /** generic search text. */
    public static final String BKEY_SEARCH_TEXT = "searchText";
    /** author search text. */
    public static final String BKEY_SEARCH_AUTHOR = "searchAuthor";
    /** int, id of fragment to display if an Activity supports multiple. */
    public static final String FRAGMENT_ID = "fragment";
    public static final String BKEY_NO_COVER = "noCover";
    /** the resulting {@link ImportSettings#what} flags after an import. */
    public static final String BKEY_IMPORT_RESULT_OPTIONS = "importResult";
    /** the resulting {@link ExportSettings#what} flags after an export. */
    public static final String BKEY_EXPORT_RESULT_OPTIONS = "exportResult";
    /** to return the status of a startActivityForResult when a task was 'isCancelled'. */
    public static final String BKEY_CANCELED = "cancelled";
    // mapped to the database, (potential) multi-table use.
    public static final String KEY_ID = DOM_PK_ID.name;
    public static final String KEY_TITLE = DOM_TITLE.name;
    public static final String KEY_FIRST_PUBLICATION = DOM_FIRST_PUBLICATION.name;


    //^^^^ all verified & used correctly.

    //TODO: cleaned up any over-use of the DOM equiv
    public static final String KEY_LAST_UPDATE_DATE = DOM_LAST_UPDATE_DATE.name;
    // mapped to the database, single table use
    public static final String KEY_BOOK_ANTHOLOGY_BITMASK = DOM_BOOK_ANTHOLOGY_BITMASK.name;
    public static final String KEY_BOOK_EDITION_BITMASK = DOM_BOOK_EDITION_BITMASK.name;
    public static final String KEY_AUTHOR = DOM_FK_AUTHOR_ID.name;
    public static final String KEY_AUTHOR_FAMILY_NAME = DOM_AUTHOR_FAMILY_NAME.name;
    public static final String KEY_AUTHOR_GIVEN_NAMES = DOM_AUTHOR_GIVEN_NAMES.name;
    public static final String KEY_AUTHOR_FORMATTED = DOM_AUTHOR_FORMATTED.name;
    public static final String KEY_BOOKSHELF_NAME = DOM_BOOKSHELF.name;
    public static final String KEY_BOOK_UUID = DOM_BOOK_UUID.name;
    public static final String KEY_BOOK_DATE_ADDED = DOM_BOOK_DATE_ADDED.name;
    public static final String KEY_BOOK_DATE_PUBLISHED = DOM_BOOK_DATE_PUBLISHED.name;
    public static final String KEY_BOOK_FORMAT = DOM_BOOK_FORMAT.name;
    public static final String KEY_BOOK_GENRE = DOM_BOOK_GENRE.name;
    public static final String KEY_BOOK_ISBN = DOM_BOOK_ISBN.name;
    public static final String KEY_BOOK_LANGUAGE = DOM_BOOK_LANGUAGE.name;
    public static final String KEY_BOOK_NOTES = DOM_BOOK_NOTES.name;
    public static final String KEY_BOOK_DESCRIPTION = DOM_BOOK_DESCRIPTION.name;
    public static final String KEY_BOOK_PRICE_LISTED = DOM_BOOK_PRICE_LISTED.name;
    public static final String KEY_BOOK_PRICE_LISTED_CURRENCY = DOM_BOOK_PRICE_LISTED_CURRENCY.name;
    public static final String KEY_BOOK_PRICE_PAID = DOM_BOOK_PRICE_PAID.name;
    public static final String KEY_BOOK_PRICE_PAID_CURRENCY = DOM_BOOK_PRICE_PAID_CURRENCY.name;
    public static final String KEY_BOOK_DATE_ACQUIRED = DOM_BOOK_DATE_ACQUIRED.name;
    public static final String KEY_BOOK_LOCATION = DOM_BOOK_LOCATION.name;
    public static final String KEY_BOOK_PAGES = DOM_BOOK_PAGES.name;
    public static final String KEY_BOOK_PUBLISHER = DOM_BOOK_PUBLISHER.name;
    public static final String KEY_BOOK_RATING = DOM_BOOK_RATING.name;
    public static final String KEY_BOOK_READ = DOM_BOOK_READ.name;
    public static final String KEY_BOOK_READ_END = DOM_BOOK_READ_END.name;
    public static final String KEY_BOOK_READ_START = DOM_BOOK_READ_START.name;
    public static final String KEY_BOOK_SIGNED = DOM_BOOK_SIGNED.name;
    public static final String KEY_SERIES = DOM_SERIES_NAME.name;
    public static final String KEY_SERIES_NUM = DOM_BOOK_SERIES_NUM.name;
    public static final String KEY_BOOK_LIBRARY_THING_ID = DOM_BOOK_LIBRARY_THING_ID.name;
    public static final String KEY_BOOK_ISFDB_ID = DOM_BOOK_ISFDB_ID.name;
    public static final String KEY_BOOK_GR_LAST_SYNC_DATE = DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name;
    public static final String KEY_LOAN_LOANED_TO = DOM_LOANEE.name;
    static final int REQ_BOOK_EDIT = 2_000;
    static final int REQ_BOOK_DUPLICATE = 2_001;
    // Build-in system.
    static final int ACTIVITY_REQUEST_CODE_ANDROID_PERMISSIONS = 9_000;

    private UniqueId() {
    }
}
