package com.eleybourn.bookcatalogue.database;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.*;

/**
 * Column info support. This is useful for auto-building queries from maps that have
 * more columns than are in the table.
 *
 * @author Philip Warner
 */
public class ColumnInfo {
    /*
     * This used to be an inner class of {@link CatalogueDBAdapter}
     * was not just used in there, but also (abused?) as Bundle keys passed around in the Extras
     *
     * NOTE!!! Because Java String comparisons are not case-insensitive, it is
     * important that ALL these fields be listed in LOWER CASE.
     *
     */

    /* these are not physical columns TODO: this is where to start unifying BKEY_* names */
    public static final String KEY_ANTHOLOGY_TITLE_ARRAY = "anthology_title_array";
    public static final String KEY_AUTHOR_ARRAY = "author_array";
    public static final String KEY_AUTHOR_DETAILS = "author_details";
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";
    public static final String KEY_DATE_PUBLISHED = "date_published";
    public static final String KEY_SERIES_ARRAY = "series_array";
    public static final String KEY_SERIES_DETAILS = "series_details";
    public static final String KEY_SERIES_FORMATTED = "series_formatted";
    public static final String KEY_THUMBNAIL = "thumbnail";


    /* This is the list of all column names as static variables for reference */
    public static final String KEY_ANTHOLOGY_MASK = DOM_ANTHOLOGY_MASK.name;
    public static final String KEY_AUTHOR_FORMATTED = DOM_AUTHOR_FORMATTED.name;
    public static final String KEY_AUTHOR_ID = DOM_AUTHOR_ID.name;
    public static final String KEY_AUTHOR_NAME = DOM_AUTHOR_NAME.name;
    public static final String KEY_AUTHOR_POSITION = DOM_AUTHOR_POSITION.name;
    public static final String KEY_BOOK = DOM_BOOK.name;
    public static final String KEY_BOOKSHELF = DOM_BOOKSHELF_ID.name;
    public static final String KEY_DATE_ADDED = DOM_ADDED_DATE.name;
    public static final String KEY_DESCRIPTION = DOM_DESCRIPTION.name;
    public static final String KEY_FAMILY_NAME = DOM_FAMILY_NAME.name;
    public static final String KEY_FORMAT = DOM_FORMAT.name;
    public static final String KEY_GENRE = DOM_GENRE.name;
    public static final String KEY_GIVEN_NAMES = DOM_GIVEN_NAMES.name;
    public static final String KEY_ISBN = DOM_ISBN.name;
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
    public static final String KEY_ID = DOM_ID.name;
    public static final String KEY_SERIES_ID = DOM_SERIES_ID.name;
    public static final String KEY_SERIES_NAME = DOM_SERIES_NAME.name;
    public static final String KEY_SERIES_NUM = DOM_SERIES_NUM.name;
    public static final String KEY_SERIES_POSITION = DOM_SERIES_POSITION.name;
    public static final String KEY_SIGNED = DOM_SIGNED.name;
    public static final String KEY_TITLE = DOM_TITLE.name;

    /* bit flags, used for {@link #KEY_ANTHOLOGY_MASK} */
    public static final int ANTHOLOGY_NO = 0;
    public static final int ANTHOLOGY_IS_ANTHOLOGY = 1;
    public static final int ANTHOLOGY_MULTIPLE_AUTHORS = 2;

    public int position;
    public String name;
    public String typeName;
    public boolean allowNull;
    public boolean isPrimaryKey;
    public String defaultValue;
    public int typeClass;
}
