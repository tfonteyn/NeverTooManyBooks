package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.entities.Book;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Global String constants
 */
public class UniqueId {

    // can't move these to res/values/ids.xml, as they need to be 16 bit values
    // BEGIN Codes used for startActivityForResult / onActivityResult

    // this first set should not be used directly, but via the REQUEST_CODE of the actual Activity.
    public static final int ACTIVITY_REQUEST_CODE_EDIT_BOOK = 101; // ok
    public static final int ACTIVITY_REQUEST_CODE_EDIT_AUTHORS = 102; // ok
    public static final int ACTIVITY_REQUEST_CODE_EDIT_SERIES = 103; // ok
    public static final int ACTIVITY_REQUEST_CODE_VIEW_BOOK = 104; // ok

    public static final int ACTIVITY_REQUEST_CODE_UPDATE_FROM_INTERNET = 201; // ok
    public static final int ACTIVITY_REQUEST_CODE_GOODREADS_EXPORT_FAILURES = 202; // ok
    public static final int ACTIVITY_REQUEST_CODE_GOODREADS_SEARCH_CRITERIA = 203; // ok

    public static final int ACTIVITY_REQUEST_CODE_SCANNER = 300; // ok
    public static final int ACTIVITY_REQUEST_CODE_ADD_BOOK_BY_SCAN = 301; // ok
    public static final int ACTIVITY_REQUEST_CODE_ADD_BOOK_BY_SEARCH = 302; // ok

    public static final int ACTIVITY_REQUEST_CODE_CROP_IMAGE = 401; // ok

    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_STYLES = 501; // ok
    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_PROPERTIES = 502; // ok
    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_GROUPS = 503; // ok
    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_PREFERENCES = 504;

    public static final int ACTIVITY_REQUEST_CODE_ADMIN = 601; // ok
    public static final int ACTIVITY_REQUEST_CODE_PREFERENCES = 602; // ok
    public static final int ACTIVITY_REQUEST_CODE_FIELD_VISIBILITY = 603; // ok
    public static final int ACTIVITY_REQUEST_CODE_SEARCH = 604; // ok

    public static final int ACTIVITY_REQUEST_CODE_EDIT_BOOKSHELF_LIST = 701; // ok
    public static final int ACTIVITY_REQUEST_CODE_BOOKSHELF_CREATE = 702; // ok
    public static final int ACTIVITY_REQUEST_CODE_BOOKSHELF_EDIT = 703; // ok


    // Build-in system
    public static final int ACTIVITY_REQUEST_CODE_ANDROID_PERMISSIONS_REQUEST = 1000;
    public static final int ACTIVITY_REQUEST_CODE_ANDROID_IMAGE_CAPTURE = 1001;
    public static final int ACTIVITY_REQUEST_CODE_ANDROID_ACTION_GET_CONTENT = 1002;
    // External app
    public static final int ACTIVITY_REQUEST_CODE_EXTERNAL_CROP_IMAGE =1003;

    // END Codes used for startActivityForResult / onActivityResult


    /** If set when calling startActivity, it will override the default layout for that activity.
     *  This is done in {@link BaseActivity} */
    public static final String BKEY_LAYOUT = "layout";

    /** used to pass a Bundle with book data around; e.g. before becoming an actual {@link Book} */
    public static final String BKEY_BOOK_DATA = "bookData";

    /** bundle key to pass an {@link java.util.ArrayList<Integer>} around. */
     public static final String BKEY_BOOK_ID_LIST = "bookIdList";

    /* Bundle keys for serialised ArrayList<Entity> */
    public static final String BKEY_AUTHOR_ARRAY = "author_array";
    public static final String BKEY_SERIES_ARRAY = "series_array";
    public static final String BKEY_ANTHOLOGY_TITLES_ARRAY = "anthology_titles_array";
    public static final String BKEY_BOOKSHELF_ARRAY = "bookshelf_array";


    //TODO: these should become ArrayList<String> which is supported by Bundle
    /* The CSV file has columns with these names */
    /** string-encoded - used in import/export and internet searches, never change the string! */
    public static final String BKEY_AUTHOR_STRING_LIST = "author_details";
    /** string-encoded - used in import/export and internet searches, never change the string! */
    public static final String BKEY_SERIES_STRING_LIST = "series_details";
    /** string-encoded - used in import/export and internet searches, never change the string! */
    public static final String BKEY_ANTHOLOGY_STRING_LIST = "anthology_titles";

    /* BKEY_* and BVAL_* which are used in more then one class should be moved here */
    public static final String BKEY_NO_COVER = "noCover";
    public static final String BKEY_CALLER_ID = "dialogId";
    public static final String BKEY_FIELD_ID = "fieldId";
    public static final String BKEY_FILE_SPEC = "fileSpec";
    public static final String BKEY_SEARCH_TEXT = "searchText";

    /** If a search site indicates in any form it's an eBook, we store it as KEY_BOOK_FORMAT with "eBook" */
    public static final String BVAL_FORMAT_EBOOK = "eBook";

    /** fileSpec of a thumbnail fetches from the internet */
    public static final String BKEY_THUMBNAIL_FILES_SPEC = "__thumbnail";

    /** to return the status of a startActivityForResult when a task was 'isCancelled' */
    public static final String BKEY_CANCELED = "cancelled";
    /** to return the status of a startActivityForResult with an onBackPressed event */
    public static final String BKEY_BACK_PRESSED = "backPressed";


    //^^^^ all verified & used correctly

    // the ones below still need checking



    //TODO: cleaned up any over-use of the DOM equiv

    public static final String KEY_BOOK_THUMBNAIL = "thumbnail";

    // mapped to the database, (potential) multi-table use
    public static final String KEY_ID = DOM_ID.name;
    public static final String KEY_TITLE = DOM_TITLE.name;
    public static final String KEY_FIRST_PUBLICATION = DOM_FIRST_PUBLICATION.name;
    public static final String KEY_NOTES = DOM_BOOK_NOTES.name;
    public static final String KEY_DESCRIPTION = DOM_DESCRIPTION.name;
    public static final String KEY_LAST_UPDATE_DATE = DOM_LAST_UPDATE_DATE.name;

    // mapped to the database, single table use
    public static final String KEY_BOOK_ANTHOLOGY_BITMASK = DOM_BOOK_ANTHOLOGY_BITMASK.name;
    public static final String KEY_BOOK_EDITION_BITMASK = DOM_BOOK_EDITION_BITMASK.name;

    public static final String KEY_AUTHOR_ID = DOM_AUTHOR_ID.name;
    public static final String KEY_AUTHOR_FAMILY_NAME = DOM_AUTHOR_FAMILY_NAME.name;
    public static final String KEY_AUTHOR_FORMATTED = DOM_AUTHOR_FORMATTED.name;
    public static final String KEY_AUTHOR_GIVEN_NAMES = DOM_AUTHOR_GIVEN_NAMES.name;
    public static final String KEY_AUTHOR_NAME = DOM_AUTHOR_NAME.name;

    public static final String KEY_BOOKSHELF_NAME = DOM_BOOKSHELF.name;

    public static final String KEY_BOOK_UUID = DOM_BOOK_UUID.name;
    public static final String KEY_BOOK_DATE_ADDED = DOM_BOOK_DATE_ADDED.name;
    public static final String KEY_BOOK_DATE_PUBLISHED = DOM_BOOK_DATE_PUBLISHED.name;
    public static final String KEY_BOOK_FORMAT = DOM_BOOK_FORMAT.name;
    public static final String KEY_BOOK_GENRE = DOM_BOOK_GENRE.name;
    public static final String KEY_BOOK_GOODREADS_LAST_SYNC_DATE = DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name;
    public static final String KEY_BOOK_ISBN = DOM_BOOK_ISBN.name;
    public static final String KEY_BOOK_LANGUAGE = DOM_BOOK_LANGUAGE.name;
    public static final String KEY_BOOK_LIST_PRICE = DOM_BOOK_LIST_PRICE.name;
    public static final String KEY_BOOK_LOCATION = DOM_BOOK_LOCATION.name;
    public static final String KEY_BOOK_PAGES = DOM_BOOK_PAGES.name;
    public static final String KEY_BOOK_PUBLISHER = DOM_BOOK_PUBLISHER.name;
    public static final String KEY_BOOK_RATING = DOM_BOOK_RATING.name;
    public static final String KEY_BOOK_READ = DOM_BOOK_READ.name;
    public static final String KEY_BOOK_READ_END = DOM_BOOK_READ_END.name;
    public static final String KEY_BOOK_READ_START = DOM_BOOK_READ_START.name;
    public static final String KEY_BOOK_SIGNED = DOM_BOOK_SIGNED.name;

    public static final String KEY_SERIES_NAME = DOM_SERIES_NAME.name;
    public static final String KEY_SERIES_NUM = DOM_BOOK_SERIES_NUM.name;

    public static final String KEY_LOAN_LOANED_TO = DOM_LOANED_TO.name;

}
