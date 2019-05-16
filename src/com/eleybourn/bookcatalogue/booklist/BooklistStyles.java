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

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.utils.Prefs;

/**
 * Collection of system-defined and user-defined Book List styles.
 *
 * @author Philip Warner
 */
public final class BooklistStyles {

    /** Preference name for the current default style to use. */
    public static final String PREF_BL_STYLE_CURRENT_DEFAULT = "BookList.Style.Current";

    /**
     * Preferred styles / menu order.
     * Stored in global shared preferences as a CSV String of id's.
     */
    public static final String PREF_BL_PREFERRED_STYLES = "BookList.Style.Preferred.Order";

    /** default style when none is set yet -1 -> R.string.style_builtin_author_series. */
    @StringRes
    public static final long DEFAULT_STYLE_ID = -1;
    // NEWKIND: BooklistStyle. Make sure to update the max id when adding a style!
    // and make sure a row is added to the database styles table.
    public static final int BUILTIN_MAX_ID = -19;

    private static final int BUILTIN_AUTHOR_THEN_SERIES = -1;
    /**
     * Hardcoded initial/default style. Avoids having the create the full set of styles just
     * to load the default one.
     */
    public static final BooklistStyle DEFAULT_STYLE =
            new BooklistStyle(BUILTIN_AUTHOR_THEN_SERIES,
                              R.string.style_builtin_author_series,
                              BooklistGroup.RowKind.AUTHOR,
                              BooklistGroup.RowKind.SERIES);
    private static final int BUILTIN_UNREAD_AUTHOR_THEN_SERIES = -2;
    private static final int BUILTIN_COMPACT = -3;
    private static final int BUILTIN_TITLE_FIRST_LETTER = -4;
    private static final int BUILTIN_SERIES = -5;
    private static final int BUILTIN_GENRE = -6;
    private static final int BUILTIN_LENDING = -7;
    private static final int BUILTIN_READ_AND_UNREAD = -8;
    private static final int BUILTIN_PUBLICATION_DATA = -9;
    private static final int BUILTIN_DATE_ADDED = -10;
    private static final int BUILTIN_DATE_ACQUIRED = -11;
    private static final int BUILTIN_AUTHOR_AND_YEAR = -12;
    private static final int BUILTIN_FORMAT = -13;
    private static final int BUILTIN_DATE_READ = -14;
    private static final int BUILTIN_LOCATION = -15;
    private static final int BUILTIN_LANGUAGE = -16;
    private static final int BUILTIN_RATING = -17;
    private static final int BUILTIN_BOOKSHELF = -18;
    private static final int BUILTIN_DATE_LAST_UPDATE = -19;

    private BooklistStyles() {
    }

    /**
     * Get the global default style, or if that fails, the builtin default style..
     *
     * @param db the database
     *
     * @return the style.
     */
    public static BooklistStyle getDefaultStyle(@NonNull final DAO db) {
        // read the global user default, or if not present the hardcoded default.
        long id = App.getPrefs().getLong(PREF_BL_STYLE_CURRENT_DEFAULT, DEFAULT_STYLE_ID);
        if (id == DEFAULT_STYLE_ID) {
            return DEFAULT_STYLE;
        }

        // check that the style really/still exists!
        BooklistStyle style = getStyles(db, true).get(id);
        if (style == null) {
            return DEFAULT_STYLE;
        }
        return style;
    }

    /**
     * Get the specified style.
     *
     * @param db the database
     * @param id the id of the style to get.
     *
     * @return the style, or if not found, some default.
     */
    public static BooklistStyle getStyle(@NonNull final DAO db,
                                         final long id) {
        BooklistStyle style = getStyles(db, true).get(id);
        if (style == null) {
            return getDefaultStyle(db);
        }
        return style;
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

        //TODO: cache this map, but rebuild if the Locale changes.
        Map<Long, BooklistStyle> builtinStyles = new LinkedHashMap<>();
        BooklistStyle style;

        // Author/Series
        builtinStyles.put(DEFAULT_STYLE.getId(), DEFAULT_STYLE);

        // Unread
        style = new BooklistStyle(BUILTIN_UNREAD_AUTHOR_THEN_SERIES,
                                  R.string.style_builtin_unread,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);
        style.setFilter(Prefs.pk_bob_filter_read, false);

        // Compact
        style = new BooklistStyle(BUILTIN_COMPACT,
                                  R.string.style_builtin_compact,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getId(), style);
        style.setScale(BooklistStyle.SCALE_SIZE_SMALLER);
        style.setShowThumbnails(false);

        // Title
        style = new BooklistStyle(BUILTIN_TITLE_FIRST_LETTER,
                                  R.string.style_builtin_title_first_letter,
                                  BooklistGroup.RowKind.TITLE_LETTER);
        builtinStyles.put(style.getId(), style);

        // Series
        style = new BooklistStyle(BUILTIN_SERIES,
                                  R.string.style_builtin_series,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Genre
        style = new BooklistStyle(BUILTIN_GENRE,
                                  R.string.style_builtin_genre,
                                  BooklistGroup.RowKind.GENRE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Loaned
        style = new BooklistStyle(BUILTIN_LENDING,
                                  R.string.style_builtin_loaned,
                                  BooklistGroup.RowKind.LOANED,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Read & Unread
        style = new BooklistStyle(BUILTIN_READ_AND_UNREAD,
                                  R.string.style_builtin_read_and_unread,
                                  BooklistGroup.RowKind.READ_STATUS,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Publication date
        style = new BooklistStyle(BUILTIN_PUBLICATION_DATA,
                                  R.string.style_builtin_publication_date,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_MONTH,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Added date
        style = new BooklistStyle(BUILTIN_DATE_ADDED,
                                  R.string.style_builtin_added_date,
                                  BooklistGroup.RowKind.DATE_ADDED_YEAR,
                                  BooklistGroup.RowKind.DATE_ADDED_MONTH,
                                  BooklistGroup.RowKind.DATE_ADDED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getId(), style);

        // Acquired date
        style = new BooklistStyle(BUILTIN_DATE_ACQUIRED,
                                  R.string.style_builtin_acquired_date,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_YEAR,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_MONTH,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getId(), style);

        // Author/Publication date
        style = new BooklistStyle(BUILTIN_AUTHOR_AND_YEAR,
                                  R.string.style_builtin_author_year,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Format
        style = new BooklistStyle(BUILTIN_FORMAT,
                                  R.string.lbl_format,
                                  BooklistGroup.RowKind.FORMAT);
        builtinStyles.put(style.getId(), style);

        // Read date
        style = new BooklistStyle(BUILTIN_DATE_READ,
                                  R.string.style_builtin_read_date,
                                  BooklistGroup.RowKind.DATE_READ_YEAR,
                                  BooklistGroup.RowKind.DATE_READ_MONTH,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getId(), style);

        // Location
        style = new BooklistStyle(BUILTIN_LOCATION,
                                  R.string.lbl_location,
                                  BooklistGroup.RowKind.LOCATION,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Location
        style = new BooklistStyle(BUILTIN_LANGUAGE,
                                  R.string.lbl_language,
                                  BooklistGroup.RowKind.LANGUAGE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Rating
        style = new BooklistStyle(BUILTIN_RATING,
                                  R.string.lbl_rating,
                                  BooklistGroup.RowKind.RATING,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Bookshelf
        style = new BooklistStyle(BUILTIN_BOOKSHELF,
                                  R.string.lbl_bookshelf,
                                  BooklistGroup.RowKind.BOOKSHELF,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getId(), style);

        // Update date
        style = new BooklistStyle(BUILTIN_DATE_LAST_UPDATE,
                                  R.string.style_builtin_update_date,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY);
        builtinStyles.put(style.getId(), style);
        style.setShowAuthor(true);

        // NEWKIND: BooklistStyle

        return builtinStyles;
    }

    /**
     * Get the user-defined Styles from the database.
     * <p>
     * The plan is to eliminate the styled database table, and store a list of uuid's in
     * Preferences instead.
     *
     * @param db database
     *
     * @return list of BooklistStyle
     */
    @NonNull
    public static Map<Long, BooklistStyle> getUserStyles(@NonNull final DAO db) {
        return db.getBooklistStyles();
    }

    /**
     * @param db     the database
     * @param all if {@code true} then also return the non-preferred styles
     *
     * @return all styles, with the preferred styles at the front of the list.
     */
    @NonNull
    public static Map<Long, BooklistStyle> getStyles(@NonNull final DAO db,
                                                     final boolean all) {
        // Get all styles: user
        Map<Long, BooklistStyle> allStyles = getUserStyles(db);
        // Get all styles: builtin
        allStyles.putAll(getBuiltinStyles());

        // filter, so the list only shows the preferred ones.
        Map<Long, BooklistStyle> styles = filterPreferredStyles(allStyles);

        // but if we want all, add the missing styles to the end of the list
        if (all) {
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
        if (!resultingStyles.isEmpty()) {
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
        String itemsStr = App.getPrefs().getString(PREF_BL_PREFERRED_STYLES, null);

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
        App.getPrefs()
           .edit()
           .putString(PREF_BL_PREFERRED_STYLES, TextUtils.join(",", list))
           .apply();
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
     * @param resources caller context
     * @param name    of the style
     *
     * @return internal id, or the default style id if not found.
     */
    public static long getStyleId(@NonNull final Resources resources,
                                  @NonNull final String name) {

        // check builtin first.
        for (BooklistStyle style : getBuiltinStyles().values()) {
            if (style.getLabel(resources).equals(name)) {
                return style.getId();
            }
        }

        // try user-defined
        try (DAO db = new DAO()) {
            for (BooklistStyle style : BooklistStyles.getUserStyles(db).values()) {
                if (style.getLabel(resources).equals(name)) {
                    return style.getId();
                }
            }
        }
        // not found...
        return DEFAULT_STYLE_ID;
    }

}
