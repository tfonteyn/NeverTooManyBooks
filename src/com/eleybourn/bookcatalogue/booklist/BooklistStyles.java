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

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.utils.Prefs;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collection of system-defined and user-defined Book List styles.
 *
 * @author Philip Warner
 */
public final class BooklistStyles {

    /** Preference name for the current default style to use. */
    public static final String PREF_BL_STYLE_CURRENT_DEFAULT = "BookList.Style.Current";

    /** Preference name for the StringSet of all user-defined style. Stores the UUID's */
    public static final String PREF_BL_STYLES_LIST = "BookList.Style.UserDefined";

    /** default style when none is set yet -1 -> R.string.style_builtin_author_series. */
    @StringRes
    private static final long DEFAULT_STYLE = -1;

    /** initialised at first use. */
    private static Map<Long, BooklistStyle> mBuiltinStyles;

    private BooklistStyles() {
    }

    public static long getDefaultStyle() {
        return Prefs.getPrefs().getLong(PREF_BL_STYLE_CURRENT_DEFAULT, DEFAULT_STYLE);
    }

    /**
     * Static method to get all defined styles.
     * <p>
     * NOTE: Do NOT call this in static initialization of application.
     * This method requires the application context to be present.
     * <p>
     * Note the hardcoded negative id's. These number should never be changed as they will
     * get stored in preferences and serialized. Take care not to add duplicates.
     * (maybe use statics instead of just the ints)
     *
     * @return a collection of all builtin styles.
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
        mBuiltinStyles.put(style.getId(), style);

        // Unread
        style = new BooklistStyle(-2, R.string.style_builtin_unread,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);
        style.setFilter(R.string.pk_bob_filter_read, false);

        // Compact
        style = new BooklistStyle(-3, R.string.style_builtin_compact,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.getId(), style);
        style.setScaleSize(BooklistStyle.SCALE_SIZE_SMALLER);
        style.setShowThumbnails(false);

        // Title
        style = new BooklistStyle(-4, R.string.style_builtin_title_first_letter,
                                  BooklistGroup.RowKind.TITLE_LETTER);
        mBuiltinStyles.put(style.getId(), style);

        // Series
        style = new BooklistStyle(-5, R.string.style_builtin_series,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Genre
        style = new BooklistStyle(-6, R.string.style_builtin_genre,
                                  BooklistGroup.RowKind.GENRE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Loaned
        style = new BooklistStyle(-7, R.string.style_builtin_loaned,
                                  BooklistGroup.RowKind.LOANED,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Read & Unread
        style = new BooklistStyle(-8, R.string.style_builtin_read_and_unread,
                                  BooklistGroup.RowKind.READ_STATUS,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Publication date
        style = new BooklistStyle(-9, R.string.style_builtin_publication_date,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_MONTH,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Added date
        style = new BooklistStyle(-10, R.string.style_builtin_added_date,
                                  BooklistGroup.RowKind.DATE_ADDED_YEAR,
                                  BooklistGroup.RowKind.DATE_ADDED_MONTH,
                                  BooklistGroup.RowKind.DATE_ADDED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.getId(), style);

        // Acquired date
        style = new BooklistStyle(-11, R.string.style_builtin_acquired_date,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_YEAR,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_MONTH,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.getId(), style);

        // Author/Publication date
        style = new BooklistStyle(-12, R.string.style_builtin_author_year,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Format
        style = new BooklistStyle(-13, R.string.lbl_format,
                                  BooklistGroup.RowKind.FORMAT);
        mBuiltinStyles.put(style.getId(), style);

        // Read date
        style = new BooklistStyle(-14, R.string.style_builtin_read_date,
                                  BooklistGroup.RowKind.DATE_READ_YEAR,
                                  BooklistGroup.RowKind.DATE_READ_MONTH,
                                  BooklistGroup.RowKind.AUTHOR);
        mBuiltinStyles.put(style.getId(), style);

        // Location
        style = new BooklistStyle(-15, R.string.lbl_location,
                                  BooklistGroup.RowKind.LOCATION,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Location
        style = new BooklistStyle(-16, R.string.lbl_language,
                                  BooklistGroup.RowKind.LANGUAGE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Rating
        style = new BooklistStyle(-17, R.string.lbl_rating,
                                  BooklistGroup.RowKind.RATING,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Bookshelf
        style = new BooklistStyle(-18, R.string.lbl_bookshelf,
                                  BooklistGroup.RowKind.BOOKSHELF,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        mBuiltinStyles.put(style.getId(), style);

        // Update date
        style = new BooklistStyle(-19, R.string.style_builtin_update_date,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY);
        mBuiltinStyles.put(style.getId(), style);
        style.setShowAuthor(true);

        // NEWKIND: BooklistStyle. next is -20

        return mBuiltinStyles;
    }

//    /**
//     * Get the user-defined Styles from the Preferences.
//     *
//     * @return list of BooklistStyle
//     */
//    @NonNull
//    private static Map<Long, BooklistStyle> getUserStyles() {
//        Map<Long, BooklistStyle> userStyles = new LinkedHashMap<>();
//        Set<String> set = Prefs.getPrefs()
//                               .getStringSet(PREF_BL_STYLES_LIST, new LinkedHashSet<String>());
//
//        return userStyles;
//    }

    /**
     * Get the user-defined Styles from the database.
     *
     * The plan is to eliminate the styled database table, and store a list of uuid's in
     * Preferences instead.
     *
     * @param db database
     *
     * @return list of BooklistStyle
     */
    @NonNull
    public static Map<Long, BooklistStyle> getUserStyles(@NonNull final DBA db) {
        Map<Long, BooklistStyle> styles = new LinkedHashMap<>();
        Map<Long, String> uuids = db.getBooklistStyles();
        for (Long id : uuids.keySet()) {
            styles.put(id, new BooklistStyle(id, uuids.get(id)));
        }
        return styles;
    }

    /**
     * @param getAll if <tt>true</tt> then also return the non-preferred styles
     *
     * @return all styles, with the preferred styles at the front of the list.
     */
    @NonNull
    public static Map<Long, BooklistStyle> getStyles(@NonNull final DBA db,
                                                     final boolean getAll) {

        // Get all styles: user
        Map<Long, BooklistStyle> allStyles = getUserStyles(db);
        // Get all styles: builtin
        allStyles.putAll(getBuiltinStyles());

        // filter, so the list only shows the preferred ones.
        Map<Long, BooklistStyle> styles = filterPreferredStyles(allStyles);

        // but if we want all, add the missing styles to the end of the list
        if (getAll) {
            if (!styles.equals(allStyles)) {
                for (BooklistStyle style : allStyles.values()) {
                    if (!styles.containsKey(style.getId())) {
                        styles.put(style.getId(), style);
                    }
                }
            }
        }
        return styles;
    }

    /**
     * @param allStyles a list of styles
     *
     * @return list of preferred styles, or the incoming list if none were preferred.
     */
    @NonNull
    private static Map<Long, BooklistStyle> filterPreferredStyles(
            @NonNull final Map<Long, BooklistStyle> allStyles) {

        Map<Long, BooklistStyle> resultingStyles = new LinkedHashMap<>();

        // first check the saved and ordered list
        for (Long id : getPreferredStyleMenuOrder()) {
            BooklistStyle style = allStyles.get(id);
            if (style != null) {
                // catch mismatches in any imported bad-data.
                style.setPreferred(true);
                // and add to results
                resultingStyles.put(id, style);
            }
        }
        // now check for styles marked preferred, but not in the menu list,
        // again to catch mismatches in any imported bad-data.
        for (BooklistStyle style : allStyles.values()) {
            if (style.isPreferred() && !resultingStyles.containsKey(style.getId())) {
                resultingStyles.put(style.getId(), style);
            }
        }

        // Return the ones we found.
        if (resultingStyles.size() > 0) {
            return resultingStyles;
        } else {
            // If none found, return what we were given.
            return allStyles;
        }
    }

    /**
     * @return the ids of the preferred styles from user preferences.
     */
    @NonNull
    private static Set<Long> getPreferredStyleMenuOrder() {
        Set<Long> ids = new LinkedHashSet<>();
        String itemsStr = Prefs.getPrefs().getString(BooklistStyle.PREF_BL_PREFERRED_STYLES,
                                                     null);

        if (itemsStr != null && !itemsStr.isEmpty()) {
            String[] entries = itemsStr.split(",");
            for (String entry : entries) {
                if (entry != null && !entry.isEmpty()) {
                    ids.add(Long.parseLong(entry));
                }
            }
        }
        return ids;
    }

    /**
     * Internal single-point of writing the preferred styles menu order.
     * <p>
     * Note: this and related methods use a LinkedHashSet as a bit of a hack
     * to eliminated duplicate id's
     *
     * @param list of style id's
     */
    private static void setPreferredStyleMenuOrder(@NonNull final Set<Long> list) {
        Prefs.getPrefs().edit().putString(BooklistStyle.PREF_BL_PREFERRED_STYLES,
                                          TextUtils.join(",", list)).apply();
    }

    /**
     * Add a style (its id) to the menu list of preferred styles.
     *
     * @param style to add.
     */
    public static void addPreferredStyle(@NonNull final BooklistStyle style) {
        Set<Long> list = getPreferredStyleMenuOrder();
        list.add(style.getId());
        setPreferredStyleMenuOrder(list);
    }

    /**
     * Save the preferred style menu list.
     * <p>
     * This list contains the id's for user-defined *AND* system-styles.
     *
     * @param styles full list of preferred styles to save 'in order'
     */
    public static void savePreferredStyleMenuOrder(@NonNull final List<BooklistStyle> styles) {
        Set<Long> list = new LinkedHashSet<>();
        for (BooklistStyle style : styles) {
            if (style.isPreferred()) {
                list.add(style.getId());
            }
        }
        setPreferredStyleMenuOrder(list);
    }

    /**
     * Used in migration/import. Convert the style name to the id.
     *
     * @param name of the style
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
        try (DBA db = new DBA(BookCatalogueApp.getAppContext())) {
            for (BooklistStyle style : BooklistStyles.getUserStyles(db).values()) {
                if (style.getDisplayName().equals(name)) {
                    return style.getId();
                }
            }
        }
        // not found...
        return DEFAULT_STYLE;
    }
}

