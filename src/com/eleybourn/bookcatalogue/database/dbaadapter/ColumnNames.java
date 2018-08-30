package com.eleybourn.bookcatalogue.database.dbaadapter;

/* This is the list of all column names as static variables for reference
 *
 * NOTE!!! Because Java String comparisons are not case-insensitive, it is
 * important that ALL these fields be listed in LOWER CASE.
 *
 * Let's try to keep these alphabetically ?
 */
public class ColumnNames {
    private ColumnNames() {
    }

    public static final String KEY_ANTHOLOGY_MASK = "anthology";
    public static final String KEY_ANTHOLOGY_TITLE_ARRAY = "anthology_title_array";
    public static final String KEY_AUTHOR_ARRAY = "author_array";
    public static final String KEY_AUTHOR_DETAILS = "author_details";
    public static final String KEY_AUTHOR_FORMATTED = "author_formatted";
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";
    public static final String KEY_AUTHOR_ID = "author";
    public static final String KEY_AUTHOR_NAME = "author_name";
    public static final String KEY_AUTHOR_OLD = "author";
    public static final String KEY_AUTHOR_POSITION = "author_position";
    public static final String KEY_BOOK = "book";
    public static final String KEY_BOOKSHELF = "bookshelf";
    public static final String KEY_DATE_ADDED = "date_added";
    public static final String KEY_DATE_PUBLISHED = "date_published";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_FAMILY_NAME = "family_name";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_GENRE = "genre";
    public static final String KEY_GIVEN_NAMES = "given_names";
    public static final String KEY_ISBN = "isbn";
    public static final String KEY_LIST_PRICE = "list_price";
    public static final String KEY_LOANED_TO = "loaned_to";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_NOTES = "notes";
    public static final String KEY_PAGES = "pages";
    public static final String KEY_POSITION = "position";
    public static final String KEY_PUBLISHER = "publisher";
    public static final String KEY_RATING = "rating";
    public static final String KEY_READ = "read";
    public static final String KEY_READ_END = "read_end";
    public static final String KEY_READ_START = "read_start";
    public static final String KEY_ROWID = "_id";
    public static final String KEY_SERIES_ARRAY = "series_array";
    public static final String KEY_SERIES_DETAILS = "series_details";
    public static final String KEY_SERIES_FORMATTED = "series_formatted";
    public static final String KEY_SERIES_ID = "series_id";
    public static final String KEY_SERIES_NAME = "series_name";
    public static final String KEY_SERIES_NUM = "series_num";
    public static final String KEY_SERIES_NUM_FORMATTED = "series_num_formatted";
    public static final String KEY_SERIES_OLD = "series";
    public static final String KEY_SERIES_POSITION = "series_position";
    public static final String KEY_SIGNED = "signed";
    public static final String KEY_THUMBNAIL = "thumbnail";
    public static final String KEY_TITLE = "title";

    public static final String OLD_KEY_AUDIOBOOK = "audiobook";

}
