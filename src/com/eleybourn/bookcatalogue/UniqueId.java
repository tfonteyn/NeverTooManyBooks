package com.eleybourn.bookcatalogue;

import com.eleybourn.bookcatalogue.backup.CsvImporter;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;

/**
 * Global String constants
 */
public class UniqueId {

    // can't move these to res/values/ids.xml, as they need to be 16 bit values
    // BEGIN Codes used for startActivityForResult / onActivityResult
    public static final int ACTIVITY_REQUEST_CODE_EDIT_BOOK = 101;
    public static final int ACTIVITY_REQUEST_CODE_EDIT_AUTHORS = 102;
    public static final int ACTIVITY_REQUEST_CODE_EDIT_SERIES = 103;
    public static final int ACTIVITY_REQUEST_CODE_VIEW_BOOK = 104;

    public static final int ACTIVITY_REQUEST_CODE_ADD_THUMB_FROM_CAMERA = 201;
    public static final int ACTIVITY_REQUEST_CODE_ADD_THUMB_FROM_GALLERY = 202;

    public static final int ACTIVITY_REQUEST_CODE_ADD_BOOK_MANUALLY = 301;
    public static final int ACTIVITY_REQUEST_CODE_ADD_BOOK_SCAN = 302;
    public static final int ACTIVITY_REQUEST_CODE_ADD_BOOK_ISBN = 303;
    public static final int ACTIVITY_REQUEST_CODE_ADD_BOOK_BARCODE = 304;

    public static final int ACTIVITY_REQUEST_CODE_CROP_RESULT_EXTERNAL = 401;
    public static final int ACTIVITY_REQUEST_CODE_CROP_RESULT_INTERNAL = 402;

    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_STYLES = 501;
    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE = 502;
    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_PROPERTIES = 503;
    public static final int ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_GROUPS = 504;

    public static final int ACTIVITY_REQUEST_CODE_GOODREADS_EXPORT_FAILURES = 601;
    // END Codes used for startActivityForResult / onActivityResult

    /* Bundle keys for entire ArrayLists */

    /** ArrayList<Author> */
    public static final String BKEY_AUTHOR_ARRAY = "author_array";
    /** ArrayList<Series> */
    public static final String BKEY_SERIES_ARRAY = "series_array";
    /** ArrayList<AnthologyTitle> */
    public static final String BKEY_ANTHOLOGY_TITLES_ARRAY = "anthology_titles_array";

    /* encoded strings containing more then one piece of data. */

    /** string-encoded - used in import/export, never change the string! */
    public static final String BKEY_AUTHOR_DETAILS = "author_details";
    /**  string-encoded - used in import/export, never change the string! */
    public static final String BKEY_SERIES_DETAILS = "series_details";
    /**  string-encoded - used in import/export, never change the string! */
    public static final String BKEY_ANTHOLOGY_DETAILS = "anthology_titles";

    /* BKEY_* and BVAL_* which are used in more then one class should be moved here */
    public static final String BKEY_NOCOVER = "nocover";
    public static final String BKEY_DIALOG_ID = "dialogId";
    public static final String BKEY_FILE_SPEC = "fileSpec";

    //^^^^ all verified & used correctly

    // the ones below still need checking

    /** used in {@link CsvImporter} (maybe from old versions?) + {@link BookDetailsAbstractFragment} seems not used. */
    public static final String BKEY_BOOKSHELF_TEXT = "bookshelf_text";

    public static final String BKEY_BOOK_DATA = "bookData";

    //TODO: why two....
    public static final String BKEY_THUMBNAIL = "thumbnail";
    public static final String BKEY_THUMBNAIL_USCORE = "__thumbnail";

    /** seems to be only ever read as a boolean in savedInstanceState, but never set TODO: find out why in original code, and if needed ? */
    public static final String BKEY_DIRTY = "Dirty";


    //TODO: migrate to calling these BKEY once we cleaned up the over-use of the DOM equiv

    // multi-table use
    public static final String KEY_ID = DOM_ID.name;
    public static final String KEY_TITLE = DOM_TITLE.name;

    // single table use
    public static final String KEY_ANTHOLOGY_MASK = DOM_BOOK_ANTHOLOGY_MASK.name;

    public static final String KEY_AUTHOR_ID = DOM_AUTHOR_ID.name;
    public static final String KEY_AUTHOR_FAMILY_NAME = DOM_AUTHOR_FAMILY_NAME.name;
    public static final String KEY_AUTHOR_FORMATTED = DOM_AUTHOR_FORMATTED.name;
    public static final String KEY_AUTHOR_GIVEN_NAMES = DOM_AUTHOR_GIVEN_NAMES.name;
    public static final String KEY_AUTHOR_NAME = DOM_AUTHOR_NAME.name;

    public static final String KEY_BOOKSHELF_NAME = DOM_BOOKSHELF.name;

    public static final String KEY_BOOK_ID = DOM_BOOK_ID.name; // TODO: does not seem to be in active use, but check DOM usage before deleting
    public static final String KEY_BOOK_UUID = DOM_BOOK_UUID.name;
    public static final String KEY_BOOK_DATE_ADDED = DOM_BOOK_DATE_ADDED.name;
    public static final String KEY_BOOK_DATE_PUBLISHED = DOM_BOOK_DATE_PUBLISHED.name;
    public static final String KEY_BOOK_FORMAT = DOM_BOOK_FORMAT.name;
    public static final String KEY_BOOK_GENRE = DOM_BOOK_GENRE.name;
    public static final String KEY_BOOK_LANGUAGE = DOM_BOOK_LANGUAGE.name;
    public static final String KEY_BOOK_LIST_PRICE = DOM_BOOK_LIST_PRICE.name;
    public static final String KEY_BOOK_LOCATION = DOM_BOOK_LOCATION.name;
    public static final String KEY_BOOK_PAGES = DOM_BOOK_PAGES.name;
    public static final String KEY_BOOK_RATING = DOM_BOOK_RATING.name;
    public static final String KEY_BOOK_READ = DOM_BOOK_READ.name;
    public static final String KEY_BOOK_READ_END = DOM_BOOK_READ_END.name;
    public static final String KEY_BOOK_READ_START = DOM_BOOK_READ_START.name;
    public static final String KEY_BOOK_SIGNED = DOM_BOOK_SIGNED.name;

    public static final String KEY_SERIES_NAME = DOM_SERIES_NAME.name;
    public static final String KEY_SERIES_NUM = DOM_BOOK_SERIES_NUM.name;

    // decide.... book only ? or ?
    public static final String KEY_NOTES = DOM_BOOK_NOTES.name;
    public static final String KEY_LOANED_TO = DOM_LOANED_TO.name;
    public static final String KEY_BOOK_PUBLISHER = DOM_BOOK_PUBLISHER.name;
    public static final String KEY_DESCRIPTION = DOM_DESCRIPTION.name;

    public static final String KEY_FIRST_PUBLICATION = DOM_FIRST_PUBLICATION.name;

    public static final String KEY_ISBN = DOM_BOOK_ISBN.name;
    public static final String KEY_GOODREADS_LAST_SYNC_DATE = DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name;
    public static final String KEY_LAST_UPDATE_DATE = DOM_LAST_UPDATE_DATE.name;
    /** If GoodReads returns a (numeric) if indicating it's an eBook, we store it as KEY_BOOK_FORMAT with "eBook" */
    public static final String BVAL_GOODREADS_FORMAT_EBOOK = "eBook";
}
