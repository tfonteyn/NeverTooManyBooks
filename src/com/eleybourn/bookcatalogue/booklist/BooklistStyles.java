/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.booklist;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.filters.TrinaryFilter;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_ACQUIRED_DAY;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_ACQUIRED_MONTH;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_ACQUIRED_YEAR;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_ADDED_DAY;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_LAST_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_LAST_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_LAST_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_PUBLISHED_MONTH;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_PUBLISHED_YEAR;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_READ_MONTH;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_DATE_READ_YEAR;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_GENRE;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_LOANED;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_RATING;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_READ_STATUS;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_SERIES;

/**
 * Collection of system-defined and user-defined Book List styles.
 *
 * @author Philip Warner
 */
public class BooklistStyles extends ArrayList<BooklistStyle> {

    /** Preference name for the current default style to use */
    public final static String PREF_BL_STYLE_CURRENT_DEFAULT = BooklistStyle.TAG + "CurrentListStyle";

    /** default style when none is set yet -1 -> R.string.style_builtin_author_series */
    @StringRes
    public final static int DEFAULT_STYLE = -1;

    /** Internal storage for preferred styles represented by this object */
    @NonNull
    private final Set<Long> mPreferredStyles;

    /**
     * Constructor
     */
    private BooklistStyles() {
        mPreferredStyles = getPreferredStyleIds();
    }

    /**
     * Get the ids of the preferred styles from user preferences.
     */
    @NonNull
    private static Set<Long> getPreferredStyleIds() {
        Set<Long> ids = new HashSet<>();
        String itemStr = BookCatalogueApp.getStringPreference(BooklistStyle.PREF_BL_STYLE_MENU_ITEMS, null);
        if (itemStr != null && !itemStr.isEmpty()) {
            List<String> list = StringList.decode(itemStr);
            for (String entry : list) {
                if (entry != null && !entry.isEmpty()) {
                    ids.add(Long.parseLong(entry));
                }
            }
        }
        return ids;
    }

    public static long getDefaultStyle() {
        return BookCatalogueApp.getLongPreference(PREF_BL_STYLE_CURRENT_DEFAULT, DEFAULT_STYLE);
    }

    /**
     * Get the preferred styles using system and user-defined styles.
     */
    @NonNull
    public static BooklistStyles getPreferredStyles(final @NonNull CatalogueDBAdapter db) {
        BooklistStyles allStyles = new BooklistStyles();

        // Get all styles: user & builtin
        allStyles.addAll(db.getBooklistStyles());
        allStyles.addAll(getBuiltinStyles());

        // Return filtered list
        return filterPreferredStyles(allStyles);
    }

    /**
     * Static method to get all defined styles
     *
     * NOTE: Do NOT call this in static initialization of application.
     * This method requires the application context to be present.
     *
     * Note the hardcoded negative id's. These number should never be changed as they will
     * get stored in preferences and serialized. Take care not to add duplicates.
     * (maybe use statics instead of just the ints)
     */
    @NonNull
    private static List<BooklistStyle> getBuiltinStyles() {
        List<BooklistStyle> list = new ArrayList<>();
        BooklistStyle style;

        // Author/Series
        style = new BooklistStyle(-1, R.string.style_builtin_author_series);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Unread
        style = new BooklistStyle(-2, R.string.style_builtin_unread);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR, ROW_KIND_SERIES);
        style.setReadFilter(TrinaryFilter.FILTER_NO);

        // Compact
        style = new BooklistStyle(-3, R.string.style_builtin_compact);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR);
        style.setScaleSize(BooklistStyle.SCALE_SIZE_SMALLER);
        style.setShowThumbnails(false);

        // Title
        style = new BooklistStyle(-4, R.string.style_builtin_title_first_letter);
        list.add(style);
        style.addGroups(RowKinds.ROW_KIND_TITLE_LETTER);

        // Series
        style = new BooklistStyle(-5, R.string.style_builtin_series);
        list.add(style);
        style.addGroup(RowKinds.ROW_KIND_SERIES);

        // Genre
        style = new BooklistStyle(-6, R.string.style_builtin_genre);
        list.add(style);
        style.addGroups(ROW_KIND_GENRE, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Loaned
        style = new BooklistStyle(-7, R.string.style_builtin_loaned);
        list.add(style);
        style.addGroups(ROW_KIND_LOANED, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Read & Unread
        style = new BooklistStyle(-8, R.string.style_builtin_read_and_unread);
        list.add(style);
        style.addGroups(ROW_KIND_READ_STATUS, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Publication date
        style = new BooklistStyle(-9, R.string.style_builtin_publication_date);
        list.add(style);
        style.addGroups(ROW_KIND_DATE_PUBLISHED_YEAR, ROW_KIND_DATE_PUBLISHED_MONTH, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Added date
        style = new BooklistStyle(-10, R.string.style_builtin_added_date);
        list.add(style);
        style.addGroups(ROW_KIND_DATE_ADDED_YEAR, ROW_KIND_DATE_ADDED_MONTH, ROW_KIND_DATE_ADDED_DAY, ROW_KIND_AUTHOR);

        // Acquired date
        style = new BooklistStyle(-11, R.string.style_builtin_acquired_date);
        list.add(style);
        style.addGroups(ROW_KIND_DATE_ACQUIRED_YEAR, ROW_KIND_DATE_ACQUIRED_MONTH, ROW_KIND_DATE_ACQUIRED_DAY, ROW_KIND_AUTHOR);

        // Author/Publication date
        style = new BooklistStyle(-12, R.string.style_builtin_author_year);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR, ROW_KIND_DATE_PUBLISHED_YEAR, ROW_KIND_SERIES);

        // Format
        style = new BooklistStyle(-13, R.string.lbl_format);
        list.add(style);
        style.addGroups(ROW_KIND_FORMAT);

        // Read date
        style = new BooklistStyle(-14, R.string.style_builtin_read_date);
        list.add(style);
        style.addGroups(ROW_KIND_DATE_READ_YEAR, ROW_KIND_DATE_READ_MONTH, ROW_KIND_AUTHOR);

        // Location
        style = new BooklistStyle(-15, R.string.lbl_location);
        list.add(style);
        style.addGroups(ROW_KIND_LOCATION, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Location
        style = new BooklistStyle(-16, R.string.lbl_language);
        list.add(style);
        style.addGroups(ROW_KIND_LANGUAGE, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Rating
        style = new BooklistStyle(-17, R.string.lbl_rating);
        list.add(style);
        style.addGroups(ROW_KIND_RATING, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Bookshelf
        style = new BooklistStyle(-18, R.string.lbl_bookshelf);
        list.add(style);
        style.addGroups(ROW_KIND_BOOKSHELF, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Update date
        style = new BooklistStyle(-19, R.string.style_builtin_update_date);
        list.add(style);
        style.addGroups(ROW_KIND_DATE_LAST_UPDATE_YEAR, ROW_KIND_DATE_LAST_UPDATE_MONTH, ROW_KIND_DATE_LAST_UPDATE_DAY);
        style.setShowAuthor(true);

        // NEWKIND: BooklistStyle. next is -20

        return list;
    }

    /**
     * @param allStyles a list of styles
     *
     * @return list of preferred styles.
     */
    @NonNull
    private static BooklistStyles filterPreferredStyles(final @NonNull BooklistStyles allStyles) {
        BooklistStyles styles = new BooklistStyles();

        // Get the user preference
        String itemStr = BookCatalogueApp.getStringPreference(BooklistStyle.PREF_BL_STYLE_MENU_ITEMS, null);
        if (itemStr != null && !itemStr.isEmpty()) {
            // Break it up and process in order
            List<String> list = StringList.decode(itemStr);
            for (String entry : list) {
                // Add any exiting style that is preferred
                BooklistStyle s = allStyles.getStyle(Long.parseLong(entry));
                if (s != null)
                    styles.add(s);
            }
        }

        // If none found, return all. Otherwise return the ones we found.
        if (styles.size() > 0)
            return styles;
        else {
            return allStyles;
        }
    }

    /**
     * Return all styles, with the preferred styles move to front of list.
     */
    @NonNull
    public static BooklistStyles getAllStyles(final @NonNull CatalogueDBAdapter db) {
        BooklistStyles allStyles = new BooklistStyles();

        // Get all styles and preferred styles.
        allStyles.addAll(db.getBooklistStyles());
        allStyles.addAll(getBuiltinStyles());

        BooklistStyles styles = filterPreferredStyles(allStyles);

        // Add missing styles to the end of the list
        if (styles != allStyles) {
            for (BooklistStyle s : allStyles)
                if (!styles.contains(s))
                    styles.add(s);
        }

        return styles;
    }

    /**
     * Save the preferred style menu list as a {@link StringList}
     */
    static void saveMenuOrder(final @NonNull List<BooklistStyle> list) {
        StringBuilder items = new StringBuilder();
        for (BooklistStyle style : list) {
            if (style.isPreferred()) {
                if (items.length() > 0) {
                    items.append(StringList.MULTI_STRING_SEPARATOR);
                }
                items.append(style.getId());
            }
        }
        BookCatalogueApp.getSharedPreferences().edit().putString(BooklistStyle.PREF_BL_STYLE_MENU_ITEMS, items.toString()).apply();
    }

    /**
     * Used in migration/import. Convert the style name to the id.
     *
     * @return internal id, or the default style id if not found.
     */
    public static long getBuiltinStyleId(final @NonNull String name) {
        for (BooklistStyle style : getBuiltinStyles()) {
            if (style.getDisplayName().equals(name)) {
                return style.getId();
            }
        }
        return DEFAULT_STYLE;
    }

    /**
     * Add a style to this list and set its preferred status
     *
     * @param style to add
     *
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     */
    @CallSuper
    @Override
    public boolean add(final @NonNull BooklistStyle style) {
        style.setPreferred(mPreferredStyles.contains(style.getId()));
        return super.add(style);
    }

    /**
     * Method should never be used, always use {@link #getStyle}
     */
    @Override
    public BooklistStyle get(final int index) {
        throw new IllegalStateException();
    }

    /**
     * Find a style based on the passed name.
     *
     * @param id Style to find
     *
     * @return style, or null
     */
    @Nullable
    public BooklistStyle getStyle(final long id) {
        for (BooklistStyle style : this) {
            if (style.getId() == id) {
                return style;
            }
        }
        return null;
    }
}

