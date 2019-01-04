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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * Collection of system-defined and user-defined Book List styles.
 *
 * @author Philip Warner
 */
public final class BooklistStyles {

    /** Preference name for the current default style to use */
    public static final String PREF_BL_STYLE_CURRENT_DEFAULT = "BookList.Style.Current";
    /** default style when none is set yet -1 -> R.string.style_builtin_author_series */
    @StringRes
    private static final long DEFAULT_STYLE = -1;
    /** initialised at first use */
    private static Map<Long, BooklistStyle> mBuiltinStyles = null;

    private BooklistStyles() {
    }

    public static long getDefaultStyle() {
        return Prefs.getLong(PREF_BL_STYLE_CURRENT_DEFAULT, DEFAULT_STYLE);
    }

    /**
     * Return all styles, with the preferred styles at the front of the list.
     */
    @NonNull
    public static Map<Long, BooklistStyle> getStyles(@NonNull final CatalogueDBAdapter db,
                                                     final boolean getAll) {

        // Get all styles: user
        LinkedHashMap<Long, BooklistStyle> allStyles = new LinkedHashMap<>(db.getBooklistStyles());
        // Get all styles: builtin
        allStyles.putAll(getBuiltinStyles());

        // filter
        Map<Long, BooklistStyle> styles = filterPreferredStyles(allStyles);

        if (getAll) {
            // Add missing styles to the end of the list
            if (styles != allStyles) {
                Set<Long> preferredStyleIds = getPreferredStyleIds();
                for (BooklistStyle style : allStyles.values()) {
                    if (!styles.containsKey(style.id)) {
                        style.setPreferred(preferredStyleIds.contains(style.id));
                        styles.put(style.id, style);
                    }
                }
            }
        }
        return styles;
    }

    /**
     * Get the ids of the preferred styles from user preferences.
     *
     * Contains id's for both builtin and user-defined.
     */
    @NonNull
    private static Set<Long> getPreferredStyleIds() {
        Set<Long> ids = new LinkedHashSet<>();
        Set<String> itemStr = Prefs.getStringSet(BooklistStyle.PREF_BL_PREFERRED_STYLES, null);
        if (itemStr != null && !itemStr.isEmpty()) {
            for (String entry : itemStr) {
                if (entry != null && !entry.isEmpty()) {
                    ids.add(Long.parseLong(entry));
                }
            }
        }
        return ids;
    }

    /**
     * Save the preferred style menu list
     */
    public static void savePreferredStyleIds(@NonNull final List<BooklistStyle> list) {
        Set<String> items = new LinkedHashSet<>();
        for (BooklistStyle style : list) {
            if (style.isPreferred()) {
                items.add(String.valueOf(style.getId()));
            }
        }
        Prefs.getPrefs().edit().putStringSet(BooklistStyle.PREF_BL_PREFERRED_STYLES, items).apply();
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
    private static Map<Long, BooklistStyle> getBuiltinStyles() {
        if (mBuiltinStyles != null) {
            return mBuiltinStyles;
        }

        mBuiltinStyles = new LinkedHashMap<>();
        BooklistStyle style;

        // Author/Series
        style = new BooklistStyle(-1, R.string.style_builtin_author_series,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Unread
        style = new BooklistStyle(-2, R.string.style_builtin_unread,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);
        style.setFilter(R.string.pk_bob_filter_read, false);

        // Compact
        style = new BooklistStyle(-3, R.string.style_builtin_compact,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.id, style);
        style.setScaleSize(BooklistStyle.SCALE_SIZE_SMALLER);
        style.setShowThumbnails(false);

        // Title
        style = new BooklistStyle(-4, R.string.style_builtin_title_first_letter,
                                  BooklistGroup.RowKind.TITLE_LETTER);
        mBuiltinStyles.put(style.id, style);

        // Series
        style = new BooklistStyle(-5, R.string.style_builtin_series,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Genre
        style = new BooklistStyle(-6, R.string.style_builtin_genre,
                                  BooklistGroup.RowKind.GENRE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Loaned
        style = new BooklistStyle(-7, R.string.style_builtin_loaned,
                                  BooklistGroup.RowKind.LOANED,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Read & Unread
        style = new BooklistStyle(-8, R.string.style_builtin_read_and_unread,
                                  BooklistGroup.RowKind.READ_STATUS,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Publication date
        style = new BooklistStyle(-9, R.string.style_builtin_publication_date,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_MONTH,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Added date
        style = new BooklistStyle(-10, R.string.style_builtin_added_date,
                                  BooklistGroup.RowKind.DATE_ADDED_YEAR,
                                  BooklistGroup.RowKind.DATE_ADDED_MONTH,
                                  BooklistGroup.RowKind.DATE_ADDED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.id, style);

        // Acquired date
        style = new BooklistStyle(-11, R.string.style_builtin_acquired_date,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_YEAR,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_MONTH,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.id, style);

        // Author/Publication date
        style = new BooklistStyle(-12, R.string.style_builtin_author_year,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Format
        style = new BooklistStyle(-13, R.string.lbl_format,
                                  BooklistGroup.RowKind.FORMAT);
        mBuiltinStyles.put(style.id, style);

        // Read date
        style = new BooklistStyle(-14, R.string.style_builtin_read_date,
                                  BooklistGroup.RowKind.DATE_READ_YEAR,
                                  BooklistGroup.RowKind.DATE_READ_MONTH,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.id, style);

        // Location
        style = new BooklistStyle(-15, R.string.lbl_location,
                                  BooklistGroup.RowKind.LOCATION,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Location
        style = new BooklistStyle(-16, R.string.lbl_language,
                                  BooklistGroup.RowKind.LANGUAGE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Rating
        style = new BooklistStyle(-17, R.string.lbl_rating,
                                  BooklistGroup.RowKind.RATING,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Bookshelf
        style = new BooklistStyle(-18, R.string.lbl_bookshelf,
                                  BooklistGroup.RowKind.BOOKSHELF,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.id, style);

        // Update date
        style = new BooklistStyle(-19, R.string.style_builtin_update_date,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY);
        mBuiltinStyles.put(style.id, style);
        style.setShowAuthor(true);

        // NEWKIND: BooklistStyle. next is -20

        return mBuiltinStyles;
    }

    /**
     * @param allStyles a list of styles
     *
     * @return list of preferred styles.
     */
    @NonNull
    private static Map<Long, BooklistStyle> filterPreferredStyles(@NonNull final Map<Long, BooklistStyle> allStyles) {
        Map<Long, BooklistStyle> styles = new LinkedHashMap<>();

        Set<Long> preferredStyleIds = getPreferredStyleIds();
        for (Long entry : preferredStyleIds) {
            // Add any exiting style that is preferred
            BooklistStyle style = allStyles.get(entry);
            if (style != null) {
                style.setPreferred(preferredStyleIds.contains(style.getId()));
                styles.put(style.id, style);
            }
        }


        // Return the ones we found.
        if (styles.size() > 0) {
            return styles;
        } else {
            // If none found, return all.
            return allStyles;
        }
    }

    /**
     * Used in migration/import. Convert the style name to the id.
     *
     * @return internal id, or the default style id if not found.
     */
    public static long getStyleId(@NonNull final String name) {
        // check builtin first.
        for (BooklistStyle style : getBuiltinStyles().values()) {
            if (style.getDisplayName().equals(name)) {
                return style.getId();
            }
        }

        // try user-defined
        try (CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext())) {
            for (BooklistStyle style : db.getBooklistStyles().values()) {
                if (style.getDisplayName().equals(name)) {
                    return style.getId();
                }
            }
        }
        // not found...
        return DEFAULT_STYLE;
    }
}

