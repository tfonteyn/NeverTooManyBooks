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

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_DAY_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_GENRE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOANED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_READ;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_RATING;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_READ_AND_UNREAD;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_SERIES;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_READ;

/**
 * Collection of system-defined and user-defined Book List styles.
 *
 * @author Philip Warner
 */
public class BooklistStyles extends ArrayList<BooklistStyle> {
    private static final String TAG = "BooklistStyles";
    private static final String PREF_MENU_ITEMS = TAG + ".Menu.Items";

    /** Internal storage for preferred styles represented by this object */
    @NonNull
    private final Set<String> mPreferredStyleNames;

    /**
     * Constructor
     */
    private BooklistStyles() {
        mPreferredStyleNames = getPreferredStyleNames();
    }

    /**
     * Get a list of canonical names of the preferred styles from user preferences.
     *
     */
    @NonNull
    private static Set<String> getPreferredStyleNames() {
        Set<String> names = new HashSet<>();
        String itemStr = BookCatalogueApp.Prefs.getString(PREF_MENU_ITEMS, null);
        if (itemStr != null && !itemStr.isEmpty()) {
            List<String> list = ArrayUtils.decodeList(itemStr);
            for (String name : list) {
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Static method to get all defined styles
     *
     * NOTE: Do NOT call this in static initialization of application.
     * This method requires the application context to be present.
     */
    @NonNull
    private static List<BooklistStyle> getBuiltinStyles() {
        List<BooklistStyle> list = new ArrayList<>();
        BooklistStyle style;

        // Author/Series
        style = new BooklistStyle(R.string.sort_author_series);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Unread
        style = new BooklistStyle(R.string.sort_unread);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR, ROW_KIND_SERIES);
        style.setReadFilter(BooklistStyle.FILTER_NO);

        // Compact
        style = new BooklistStyle(R.string.compact);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR);
        style.setCondensed(true);
        style.setShowThumbnails(false);

        // Title
        style = new BooklistStyle(R.string.sort_title_first_letter);
        list.add(style);
        style.addGroups(RowKinds.ROW_KIND_TITLE_LETTER);

        // Series
        style = new BooklistStyle(R.string.sort_series);
        list.add(style);
        style.addGroup(RowKinds.ROW_KIND_SERIES);

        // Genre
        style = new BooklistStyle(R.string.sort_genre);
        list.add(style);
        style.addGroups(ROW_KIND_GENRE, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Loaned
        style = new BooklistStyle(R.string.sort_loaned);
        list.add(style);
        style.addGroups(ROW_KIND_LOANED, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Read & Unread
        style = new BooklistStyle(R.string.sort_read_and_unread);
        list.add(style);
        style.addGroups(ROW_KIND_READ_AND_UNREAD, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Publication date
        style = new BooklistStyle(R.string.sort_publication_date);
        list.add(style);
        style.addGroups(ROW_KIND_YEAR_PUBLISHED, ROW_KIND_MONTH_PUBLISHED, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Added date
        style = new BooklistStyle(R.string.sort_added_date);
        list.add(style);
        style.addGroups(ROW_KIND_YEAR_ADDED, ROW_KIND_MONTH_ADDED, ROW_KIND_DAY_ADDED, ROW_KIND_AUTHOR);

        // Author/Publication date
        style = new BooklistStyle(R.string.sort_author_year);
        list.add(style);
        style.addGroups(ROW_KIND_AUTHOR, ROW_KIND_YEAR_PUBLISHED, ROW_KIND_SERIES);

        // Format
        style = new BooklistStyle(R.string.format);
        list.add(style);
        style.addGroups(ROW_KIND_FORMAT);

        // Read date
        style = new BooklistStyle(R.string.sort_read_date);
        list.add(style);
        style.addGroups(ROW_KIND_YEAR_READ, ROW_KIND_MONTH_READ, ROW_KIND_AUTHOR);

        // Location
        style = new BooklistStyle(R.string.location);
        list.add(style);
        style.addGroups(ROW_KIND_LOCATION, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Location
        style = new BooklistStyle(R.string.lbl_language);
        list.add(style);
        style.addGroups(ROW_KIND_LANGUAGE, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Rating
        style = new BooklistStyle(R.string.rating);
        list.add(style);
        style.addGroups(ROW_KIND_RATING, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Bookshelf
        style = new BooklistStyle(R.string.bookshelf);
        list.add(style);
        style.addGroups(ROW_KIND_BOOKSHELF, ROW_KIND_AUTHOR, ROW_KIND_SERIES);

        // Update date
        style = new BooklistStyle(R.string.update_date);
        list.add(style);
        style.addGroups(ROW_KIND_UPDATE_YEAR, ROW_KIND_UPDATE_MONTH, ROW_KIND_UPDATE_DAY);
        style.setShowAuthor(true);
        // NEWKIND: Add new kinds to this list so the user sees them (Optional)

        return list;
    }

    /**
     * @param allStyles a collection of styles
     *
     * @return the ordered set of preferred styles.
     */
    @NonNull
    private static BooklistStyles filterPreferredStyles(@NonNull final BooklistStyles allStyles) {
        BooklistStyles styles = new BooklistStyles();

        // Get the user preference
        String itemStr = BookCatalogueApp.Prefs.getString(PREF_MENU_ITEMS, null);
        if (itemStr != null && !itemStr.isEmpty()) {
            // Break it up and process in order
            List<String> list = ArrayUtils.decodeList(itemStr);
            for (String n : list) {
                // Add any exiting style that is preferred
                BooklistStyle s = allStyles.findCanonical(n);
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
     * Get the preferred styles using system and user-defined styles.
     */
    @NonNull
    public static BooklistStyles getPreferredStyles(@NonNull final CatalogueDBAdapter db) {
        BooklistStyles allStyles = new BooklistStyles();

        // Get all styles: user & builtin
        allStyles.addAll(db.getBooklistStyles());
        allStyles.addAll(getBuiltinStyles());

        // Return filtered list
        return filterPreferredStyles(allStyles);
    }

    /**
     * Return all styles, with the preferred styles move to front of list.
     */
    @NonNull
    public static BooklistStyles getAllStyles(@NonNull final CatalogueDBAdapter db) {
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
     * Save the preferred style menu list.
     */
    static void saveMenuOrder(@Nullable final List<BooklistStyle> list) {
        Objects.requireNonNull(list);

        StringBuilder items = new StringBuilder();
        for (BooklistStyle style : list) {
            if (style.isPreferred()) {
                if (items.length() > 0) {
                    items.append(ArrayUtils.MULTI_STRING_SEPARATOR);
                }
                items.append(ArrayUtils.encodeListItem(style.getCanonicalName()));
            }
        }
        BookCatalogueApp.Prefs.putString(PREF_MENU_ITEMS, items.toString());
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
    public boolean add(@NonNull final BooklistStyle style) {
        style.setPreferred(mPreferredStyleNames.contains(style.getCanonicalName()));
        return super.add(style);
    }

    /**
     * Find a style based on the passed name.
     *
     * @param name Style to find
     *
     * @return Named style, or null
     */
    @Nullable
    public BooklistStyle findCanonical(@NonNull final String name) {
        for (BooklistStyle style : this) {
            if (style.getCanonicalName().equalsIgnoreCase(name)) {
                return style;
            }
        }
        return null;
    }
}

