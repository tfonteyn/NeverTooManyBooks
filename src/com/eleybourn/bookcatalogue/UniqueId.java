package com.eleybourn.bookcatalogue;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Global constants
 */
public class UniqueId {
    // Codes used for startActivityForResult / onActivityResult
    public static final int ACTIVITY_CREATE_BOOK_MANUALLY = 1;
    public static final int ACTIVITY_CREATE_BOOK_ISBN = 2;
    public static final int ACTIVITY_CREATE_BOOK_SCAN = 3;
    public static final int ACTIVITY_EDIT_BOOK = 4;
    public static final int ACTIVITY_ADMIN = 5;
    public static final int ACTIVITY_HELP = 6;
    public static final int ACTIVITY_PREFERENCES = 7;
    public static final int ACTIVITY_BOOKLIST_STYLE = 8;
    public static final int ACTIVITY_BOOKLIST_STYLE_PROPERTIES = 9;
    public static final int ACTIVITY_BOOKLIST_STYLE_GROUPS = 10;
    public static final int ACTIVITY_BOOKLIST_STYLES = 11;
    public static final int ACTIVITY_GOODREADS_EXPORT_FAILURES = 12;
    public static final int ACTIVITY_ADMIN_FINISH = 13;
    public static final int ACTIVITY_SORT = 14;
    public static final int ACTIVITY_SCAN = 15;
    public static final int ACTIVITY_ABOUT = 16;
    public static final int ACTIVITY_DONATE = 17;
    public static final int ACTIVITY_BOOKSHELF = 18;
    public static final int ACTIVITY_VIEW_BOOK = 19;
    public static final int ACTIVITY_DUPLICATE_BOOK = 20;

    public static final int DIALOG_PROGRESS_DETERMINATE = 101;
    public static final int DIALOG_PROGRESS_INDETERMINATE = 102;

    /* other global constants */
    public static final String GOODREADS_FILENAME_SUFFIX = "_GR";

    /* BKEY_* and BVAL_* which ae used in more then one class should be moved here */
    public static final String BKEY_NOCOVER = "nocover";
    public static final String BKEY_DIALOG_ID = "dialogId";
    public static final String BKEY_FILE_SPEC = "fileSpec";
    public static final String BKEY_BOOK_DATA = "bookData";
    public static final String BKEY_DIRTY = "Dirty";
    public static final String BKEY_ANTHOLOGY_TITLES = "anthology_titles";

    public static final String BKEY_THUMBNAIL = "thumbnail";
    public static final String BKEY_THUMBNAIL_USCORE = "__thumbnail";

    public static final String BKEY_ANTHOLOGY_TITLE_ARRAY = "anthology_title_array";
    public static final String BKEY_AUTHOR_ARRAY = "author_array";
    public static final String BKEY_SERIES_ARRAY = "series_array";

    public static final String BKEY_AUTHOR_DETAILS = "author_details";
    public static final String BKEY_SERIES_DETAILS = "series_details";

    //TODO: migrate to calling these BKEY once we cleaned up the over-use of the DOM equiv

    // multi-table use
    public static final String KEY_ID = DOM_ID.name;
    public static final String KEY_TITLE = DOM_TITLE.name;

    // single table use
    public static final String KEY_ANTHOLOGY_MASK = DOM_ANTHOLOGY_MASK.name;

    public static final String KEY_AUTHOR_ID = DOM_AUTHOR_ID.name;
    public static final String KEY_AUTHOR_FAMILY_NAME = DOM_AUTHOR_FAMILY_NAME.name;
    public static final String KEY_AUTHOR_FORMATTED = DOM_AUTHOR_FORMATTED.name;
    public static final String KEY_AUTHOR_GIVEN_NAMES = DOM_AUTHOR_GIVEN_NAMES.name;
    public static final String KEY_AUTHOR_NAME = DOM_AUTHOR_NAME.name;

    public static final String KEY_BOOKSHELF_NAME = DOM_BOOKSHELF_NAME.name;

    public static final String KEY_BOOK_ID = DOM_BOOK_ID.name; // TODO: does not seem to be in active use
    public static final String KEY_BOOK_UUID = DOM_BOOK_UUID.name;
    public static final String KEY_BOOK_DATE_ADDED = DOM_BOOK_DATE_ADDED.name;
    public static final String KEY_BOOK_DATE_PUBLISHED = DOM_BOOK_DATE_PUBLISHED.name;
    public static final String KEY_BOOK_FORMAT = DOM_BOOK_FORMAT.name;
    public static final String KEY_BOOK_GENRE = DOM_BOOK_GENRE.name;
    public static final String KEY_BOOK_LIST_PRICE = DOM_BOOK_LIST_PRICE.name;
    public static final String KEY_BOOK_LANGUAGE = DOM_BOOK_LANGUAGE.name;
    public static final String KEY_BOOK_LOCATION = DOM_BOOK_LOCATION.name;
    public static final String KEY_BOOK_PAGES = DOM_BOOK_PAGES.name;
    public static final String KEY_BOOK_READ = DOM_BOOK_READ.name;
    public static final String KEY_BOOK_READ_END = DOM_BOOK_READ_END.name;
    public static final String KEY_BOOK_READ_START = DOM_BOOK_READ_START.name;
    public static final String KEY_BOOK_SIGNED = DOM_BOOK_SIGNED.name;

    public static final String KEY_SERIES_NAME = DOM_SERIES_NAME.name;
    public static final String KEY_SERIES_NUM = DOM_SERIES_NUM.name;


    // decide.... book only ? or ?
    public static final String KEY_NOTES = DOM_NOTES.name;
    public static final String KEY_LOANED_TO = DOM_LOANED_TO.name;
    public static final String KEY_PUBLISHER = DOM_PUBLISHER.name;
    public static final String KEY_DESCRIPTION = DOM_DESCRIPTION.name;
    public static final String KEY_RATING = DOM_BOOK_RATING.name;


    public static final String KEY_ISBN = DOM_ISBN.name;
    public static final String KEY_GOODREADS_LAST_SYNC_DATE = DOM_GOODREADS_LAST_SYNC_DATE.name;
    public static final String KEY_LAST_UPDATE_DATE = DOM_LAST_UPDATE_DATE.name;
    /** If GoodReads returns a (numeric) if indicating it's an eBook, we store it as KEY_BOOK_FORMAT with "EBook" */
    public static final String BVAL_GOODREADS_FORMAT_EBOOK = "Ebook";
}
