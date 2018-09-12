package com.eleybourn.bookcatalogue;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SIGNED;
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
    public static final String BKEY_THUMBNAIL = "thumbnail";

    public static final String BKEY_ANTHOLOGY_TITLE_ARRAY = "anthology_title_array";
    public static final String BKEY_AUTHOR_ARRAY = "author_array";
    public static final String BKEY_SERIES_ARRAY = "series_array";

    public static final String BKEY_AUTHOR_DETAILS = "author_details";
    public static final String BKEY_SERIES_DETAILS = "series_details";

    //TODO: migrate to calling these BKEY once we cleaned up the over-use of the DOM equiv
    public static final String KEY_ID = DOM_ID.name;

    public static final String KEY_ANTHOLOGY_MASK = DOM_ANTHOLOGY_MASK.name;

    public static final String KEY_AUTHOR_FAMILY_NAME = DOM_AUTHOR_FAMILY_NAME.name;
    public static final String KEY_AUTHOR_FORMATTED = DOM_AUTHOR_FORMATTED.name;
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name;
    public static final String KEY_AUTHOR_ID = DOM_AUTHOR_ID.name;
    public static final String KEY_AUTHOR_GIVEN_NAMES = DOM_AUTHOR_GIVEN_NAMES.name;
    public static final String KEY_AUTHOR_NAME = DOM_AUTHOR_NAME.name;
    public static final String KEY_AUTHOR_POSITION = DOM_AUTHOR_POSITION.name;

    public static final String KEY_BOOK = DOM_BOOK.name;
    public static final String KEY_BOOK_UUID = DOM_BOOK_UUID.name;
    public static final String KEY_BOOKSHELF = DOM_BOOKSHELF_ID.name;
    public static final String KEY_DATE_ADDED = DOM_ADDED_DATE.name;
    public static final String KEY_DATE_PUBLISHED = DOM_DATE_PUBLISHED.name;
    public static final String KEY_DESCRIPTION = DOM_DESCRIPTION.name;
    public static final String KEY_FORMAT = DOM_FORMAT.name;
    public static final String KEY_GENRE = DOM_GENRE.name;
    public static final String KEY_ISBN = DOM_ISBN.name;
    public static final String KEY_LANGUAGE = DOM_LANGUAGE.name;
    public static final String KEY_LIST_PRICE = DOM_LIST_PRICE.name;
    public static final String KEY_LOANED_TO = DOM_LOANED_TO.name;
    public static final String KEY_LOCATION = DOM_LOCATION.name;
    public static final String KEY_NOTES = DOM_NOTES.name;
    public static final String KEY_PAGES = DOM_PAGES.name;
    public static final String KEY_POSITION = DOM_POSITION.name;
    public static final String KEY_PUBLISHER = DOM_PUBLISHER.name;
    public static final String KEY_RATING = DOM_RATING.name;
    public static final String KEY_READ = DOM_READ.name;
    public static final String KEY_READ_END = DOM_READ_END.name;
    public static final String KEY_READ_START = DOM_READ_START.name;

    public static final String KEY_SERIES_FORMATTED = DOM_SERIES_FORMATTED.name;
    public static final String KEY_SERIES_ID = DOM_SERIES_ID.name;
    public static final String KEY_SERIES_NAME = DOM_SERIES_NAME.name;
    public static final String KEY_SERIES_NUM = DOM_SERIES_NUM.name;
    public static final String KEY_SERIES_POSITION = DOM_SERIES_POSITION.name;

    public static final String KEY_SIGNED = DOM_SIGNED.name;
    public static final String KEY_TITLE = DOM_TITLE.name;

    public static final String KEY_GOODREADS_BOOK_ID = DOM_GOODREADS_BOOK_ID.name;
    public static final String KEY_GOODREADS_LAST_SYNC_DATE = DOM_GOODREADS_LAST_SYNC_DATE.name;
    public static final String KEY_LAST_UPDATE_DATE = DOM_LAST_UPDATE_DATE.name;
}
