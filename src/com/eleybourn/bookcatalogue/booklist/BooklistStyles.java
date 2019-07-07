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
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.settings.Prefs;

/**
 * Collection of system-defined and user-defined Book List styles.
 * <p>
 * The UUID's should never be changed.
 *
 * @author Philip Warner
 */
public final class BooklistStyles {

    /** Preference for the current default style UUID to use. */
    public static final String PREF_BL_STYLE_CURRENT_DEFAULT = "BookList.Style.Current";

    /**
     * Preferred styles / menu order.
     * Stored in global shared preferences as a CSV String of uuid's.
     */
    public static final String PREF_BL_PREFERRED_STYLES = "BookList.Style.Preferred.Order";

    // NEWKIND: BooklistStyle. Make sure to update the max id when adding a style!
    // and make sure a row is added to the database styles table.
    // next max is -20
    public static final int BUILTIN_MAX_ID = -19;


    private static final int BUILTIN_AUTHOR_THEN_SERIES = -1;
    /** default style when none is set yet. */
    public static final int DEFAULT_STYLE_ID = BUILTIN_AUTHOR_THEN_SERIES;
    private static final String BUILTIN_AUTHOR_THEN_SERIES_UUID
            = "6a82c4c0-48f1-4130-8a62-bbf478ffe184";
    /**
     * Hardcoded initial/default style. Avoids having the create the full set of styles just
     * to load the default one.
     */
    public static final BooklistStyle DEFAULT_STYLE =
            new BooklistStyle(BUILTIN_AUTHOR_THEN_SERIES,
                              BUILTIN_AUTHOR_THEN_SERIES_UUID,
                              R.string.style_builtin_author_series,
                              BooklistGroup.RowKind.AUTHOR,
                              BooklistGroup.RowKind.SERIES);
    private static final String DEFAULT_STYLE_UUID = BUILTIN_AUTHOR_THEN_SERIES_UUID;

    private static final int BUILTIN_UNREAD_AUTHOR_THEN_SERIES = -2;
    private static final String BUILTIN_UNREAD_AUTHOR_THEN_SERIES_UUID
            = "f479e979-c43f-4b0b-9c5b-6942964749df";
    private static final int BUILTIN_COMPACT = -3;
    private static final String BUILTIN_COMPACT_UUID
            = "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16";
    private static final int BUILTIN_TITLE_FIRST_LETTER = -4;
    private static final String BUILTIN_TITLE_FIRST_LETTER_UUID
            = "16b4ecdf-edef-4bf2-a682-23f7230446c8";
    private static final int BUILTIN_SERIES = -5;
    private static final String BUILTIN_SERIES_UUID
            = "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335";

    private static final int BUILTIN_GENRE = -6;
    private static final String BUILTIN_GENRE_UUID
            = "edc5c178-60f0-40e7-9674-e08445b6c942";
    private static final int BUILTIN_LENDING = -7;
    private static final String BUILTIN_LENDING_UUID
            = "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3";
    private static final int BUILTIN_READ_AND_UNREAD = -8;
    private static final String BUILTIN_READ_AND_UNREAD_UUID
            = "e3678890-7785-4870-9213-333a68293a49";
    private static final int BUILTIN_PUBLICATION_DATA = -9;
    private static final String BUILTIN_PUBLICATION_DATA_UUID
            = "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796";
    private static final int BUILTIN_DATE_ADDED = -10;
    private static final String BUILTIN_DATE_ADDED_UUID
            = "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521";

    private static final int BUILTIN_DATE_ACQUIRED = -11;
    private static final String BUILTIN_DATE_ACQUIRED_UUID
            = "b3255b1f-5b07-4b3e-9700-96c0f8f35a58";
    private static final int BUILTIN_AUTHOR_AND_YEAR = -12;
    private static final String BUILTIN_AUTHOR_AND_YEAR_UUID
            = "7c9ad91e-df7c-415a-a205-cdfabff5465d";
    private static final int BUILTIN_FORMAT = -13;
    private static final String BUILTIN_FORMAT_UUID
            = "bdc43f17-2a95-42ef-b0f8-c750ef920f28";
    private static final int BUILTIN_DATE_READ = -14;
    private static final String BUILTIN_DATE_READ_UUID
            = "034fe547-879b-4fa0-997a-28d769ba5a84";
    private static final int BUILTIN_LOCATION = -15;
    private static final String BUILTIN_LOCATION_UUID
            = "e21a90c9-5150-49ee-a204-0cab301fc5a1";

    private static final int BUILTIN_LANGUAGE = -16;
    private static final String BUILTIN_LANGUAGE_UUID
            = "00379d95-6cb2-40e6-8c3b-f8278f34750a";
    private static final int BUILTIN_RATING = -17;
    private static final String BUILTIN_RATING_UUID
            = "20a2ebdf-81a7-4eca-a3a9-7275062b907a";
    private static final int BUILTIN_BOOKSHELF = -18;
    private static final String BUILTIN_BOOKSHELF_UUID
            = "999d383e-6e76-416a-86f9-960c729aa718";
    private static final int BUILTIN_DATE_LAST_UPDATE = -19;
    private static final String BUILTIN_DATE_LAST_UPDATE_UUID
            = "427a0da5-0779-44b6-89e9-82772e5ad5ef";


    /** Use the NEGATIVE builtin style id to get the UUID for it. Element 0 is not used. */
    public static final String[] ID_UUID = {
            "",
            BUILTIN_AUTHOR_THEN_SERIES_UUID,
            BUILTIN_UNREAD_AUTHOR_THEN_SERIES_UUID,
            BUILTIN_COMPACT_UUID,
            BUILTIN_TITLE_FIRST_LETTER_UUID,
            BUILTIN_SERIES_UUID,

            BUILTIN_GENRE_UUID,
            BUILTIN_LENDING_UUID,
            BUILTIN_READ_AND_UNREAD_UUID,
            BUILTIN_PUBLICATION_DATA_UUID,
            BUILTIN_DATE_ADDED_UUID,

            BUILTIN_DATE_ACQUIRED_UUID,
            BUILTIN_AUTHOR_AND_YEAR_UUID,
            BUILTIN_FORMAT_UUID,
            BUILTIN_DATE_READ_UUID,
            BUILTIN_LOCATION_UUID,

            BUILTIN_LANGUAGE_UUID,
            BUILTIN_RATING_UUID,
            BUILTIN_BOOKSHELF_UUID,
            BUILTIN_DATE_LAST_UPDATE_UUID,
    };

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
        String uuid = App.getPrefs().getString(PREF_BL_STYLE_CURRENT_DEFAULT, DEFAULT_STYLE_UUID);
        if (DEFAULT_STYLE_UUID.equals(uuid)) {
            return DEFAULT_STYLE;
        }

        // check that the style really/still exists!
        BooklistStyle style = getStyles(db, true).get(uuid);
        if (style == null) {
            return DEFAULT_STYLE;
        }
        return style;
    }

    /**
     * Get the specified style.
     *
     * @param db   the database
     * @param uuid of the style to get.
     *
     * @return the style, or if not found, some default.
     */
    public static BooklistStyle getStyle(@NonNull final DAO db,
                                         @NonNull final String uuid) {
        BooklistStyle style = getStyles(db, true).get(uuid);
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
     *
     * @return a collection of all builtin styles.
     */
    @NonNull
    private static Map<String, BooklistStyle> getBuiltinStyles() {

        //TODO: cache this map, but rebuild if the Locale changes.
        Map<String, BooklistStyle> builtinStyles = new LinkedHashMap<>();
        BooklistStyle style;

        // Author/Series
        builtinStyles.put(DEFAULT_STYLE.getUuid(), DEFAULT_STYLE);

        // Unread
        style = new BooklistStyle(BUILTIN_UNREAD_AUTHOR_THEN_SERIES,
                                  BUILTIN_UNREAD_AUTHOR_THEN_SERIES_UUID,
                                  R.string.style_builtin_unread,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);
        style.setFilter(Prefs.pk_bob_filter_read, false);

        // Compact
        style = new BooklistStyle(BUILTIN_COMPACT,
                                  BUILTIN_COMPACT_UUID,
                                  R.string.style_builtin_compact,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getUuid(), style);
        style.setScaleFactor(BooklistStyle.TEXT_SCALE_SMALL);
        style.setShowThumbnails(false);

        // Title
        style = new BooklistStyle(BUILTIN_TITLE_FIRST_LETTER,
                                  BUILTIN_TITLE_FIRST_LETTER_UUID,
                                  R.string.style_builtin_title_first_letter,
                                  BooklistGroup.RowKind.TITLE_LETTER);
        builtinStyles.put(style.getUuid(), style);

        // Series
        style = new BooklistStyle(BUILTIN_SERIES,
                                  BUILTIN_SERIES_UUID,
                                  R.string.style_builtin_series,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Genre
        style = new BooklistStyle(BUILTIN_GENRE,
                                  BUILTIN_GENRE_UUID,
                                  R.string.style_builtin_genre,
                                  BooklistGroup.RowKind.GENRE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Loaned
        style = new BooklistStyle(BUILTIN_LENDING,
                                  BUILTIN_LENDING_UUID,
                                  R.string.style_builtin_loaned,
                                  BooklistGroup.RowKind.LOANED,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Read & Unread
        style = new BooklistStyle(BUILTIN_READ_AND_UNREAD,
                                  BUILTIN_READ_AND_UNREAD_UUID,
                                  R.string.style_builtin_read_and_unread,
                                  BooklistGroup.RowKind.READ_STATUS,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Publication date
        style = new BooklistStyle(BUILTIN_PUBLICATION_DATA,
                                  BUILTIN_PUBLICATION_DATA_UUID,
                                  R.string.style_builtin_publication_date,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_MONTH,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Added date
        style = new BooklistStyle(BUILTIN_DATE_ADDED,
                                  BUILTIN_DATE_ADDED_UUID,
                                  R.string.style_builtin_added_date,
                                  BooklistGroup.RowKind.DATE_ADDED_YEAR,
                                  BooklistGroup.RowKind.DATE_ADDED_MONTH,
                                  BooklistGroup.RowKind.DATE_ADDED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getUuid(), style);

        // Acquired date
        style = new BooklistStyle(BUILTIN_DATE_ACQUIRED,
                                  BUILTIN_DATE_ACQUIRED_UUID,
                                  R.string.style_builtin_acquired_date,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_YEAR,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_MONTH,
                                  BooklistGroup.RowKind.DATE_ACQUIRED_DAY,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getUuid(), style);

        // Author/Publication date
        style = new BooklistStyle(BUILTIN_AUTHOR_AND_YEAR,
                                  BUILTIN_AUTHOR_AND_YEAR_UUID,
                                  R.string.style_builtin_author_year,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Format
        style = new BooklistStyle(BUILTIN_FORMAT,
                                  BUILTIN_FORMAT_UUID,
                                  R.string.lbl_format,
                                  BooklistGroup.RowKind.FORMAT);
        builtinStyles.put(style.getUuid(), style);

        // Read date
        style = new BooklistStyle(BUILTIN_DATE_READ,
                                  BUILTIN_DATE_READ_UUID,
                                  R.string.style_builtin_read_date,
                                  BooklistGroup.RowKind.DATE_READ_YEAR,
                                  BooklistGroup.RowKind.DATE_READ_MONTH,
                                  BooklistGroup.RowKind.AUTHOR);
        builtinStyles.put(style.getUuid(), style);

        // Location
        style = new BooklistStyle(BUILTIN_LOCATION,
                                  BUILTIN_LOCATION_UUID,
                                  R.string.lbl_location,
                                  BooklistGroup.RowKind.LOCATION,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Location
        style = new BooklistStyle(BUILTIN_LANGUAGE,
                                  BUILTIN_LANGUAGE_UUID,
                                  R.string.lbl_language,
                                  BooklistGroup.RowKind.LANGUAGE,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Rating
        style = new BooklistStyle(BUILTIN_RATING,
                                  BUILTIN_RATING_UUID,
                                  R.string.lbl_rating,
                                  BooklistGroup.RowKind.RATING,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Bookshelf
        style = new BooklistStyle(BUILTIN_BOOKSHELF,
                                  BUILTIN_BOOKSHELF_UUID,
                                  R.string.lbl_bookshelf,
                                  BooklistGroup.RowKind.BOOKSHELF,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
        builtinStyles.put(style.getUuid(), style);

        // Update date
        style = new BooklistStyle(BUILTIN_DATE_LAST_UPDATE,
                                  BUILTIN_DATE_LAST_UPDATE_UUID,
                                  R.string.style_builtin_update_date,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH,
                                  BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY);
        builtinStyles.put(style.getUuid(), style);
        style.setShowAuthor(true);

        // NEWKIND: BooklistStyle

        return builtinStyles;
    }

    /**
     * Get the user-defined Styles from the database.
     *
     * @param db database
     *
     * @return list of BooklistStyle
     */
    @NonNull
    public static Map<String, BooklistStyle> getUserStyles(@NonNull final DAO db) {
        return db.getUserBooklistStyles();
    }

    /**
     * @param db  the database
     * @param all if {@code true} then also return the non-preferred styles
     *
     * @return all styles, with the preferred styles at the front of the list.
     */
    @NonNull
    public static Map<String, BooklistStyle> getStyles(@NonNull final DAO db,
                                                       final boolean all) {
        // Get all styles: user
        Map<String, BooklistStyle> allStyles = getUserStyles(db);
        // Get all styles: builtin
        allStyles.putAll(getBuiltinStyles());

        // filter, so the list only shows the preferred ones.
        Map<String, BooklistStyle> styles = filterPreferredStyles(allStyles);

        // but if we want all, add the missing styles to the end of the list
        if (all) {
            if (!styles.equals(allStyles)) {
                for (BooklistStyle style : allStyles.values()) {
                    if (!styles.containsKey(style.getUuid())) {
                        styles.put(style.getUuid(), style);
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
    private static Map<String, BooklistStyle> filterPreferredStyles(
            @NonNull final Map<String, BooklistStyle> allStyles) {

        Map<String, BooklistStyle> resultingStyles = new LinkedHashMap<>();

        // first check the saved and ordered list
        for (String uuid : getPreferredStyleMenuOrder()) {
            BooklistStyle style = allStyles.get(uuid);
            if (style != null) {
                // catch mismatches in any imported bad-data.
                style.setPreferred(true);
                // and add to results
                resultingStyles.put(uuid, style);
            }
        }
        // now check for styles marked preferred, but not in the menu list,
        // again to catch mismatches in any imported bad-data.
        for (BooklistStyle style : allStyles.values()) {
            if (style.isPreferred() && !resultingStyles.containsKey(style.getUuid())) {
                resultingStyles.put(style.getUuid(), style);
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
     * @return the uuid's of the preferred styles from user preferences.
     */
    @NonNull
    private static Set<String> getPreferredStyleMenuOrder() {
        Set<String> uuidSet = new LinkedHashSet<>();
        String itemsStr = App.getPrefs().getString(PREF_BL_PREFERRED_STYLES, null);

        if (itemsStr != null && !itemsStr.isEmpty()) {
            String[] entries = itemsStr.split(",");
            for (String entry : entries) {
                if (entry != null && !entry.isEmpty()) {
                    uuidSet.add(entry);
                }
            }
        }
        return uuidSet;
    }

    /**
     * Internal single-point of writing the preferred styles menu order.
     *
     * @param uuidSet of style uuid's
     */
    private static void setPreferredStyleMenuOrder(@NonNull final Set<String> uuidSet) {
        App.getPrefs()
           .edit()
           .putString(PREF_BL_PREFERRED_STYLES, TextUtils.join(",", uuidSet))
           .apply();
    }

    /**
     * Add a style (its uuid) to the menu list of preferred styles.
     *
     * @param style to add.
     */
    public static void addPreferredStyle(@NonNull final BooklistStyle style) {
        Set<String> list = getPreferredStyleMenuOrder();
        list.add(style.getUuid());
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
        Set<String> list = new LinkedHashSet<>();
        for (BooklistStyle style : styles) {
            if (style.isPreferred()) {
                list.add(style.getUuid());
            }
        }
        setPreferredStyleMenuOrder(list);
    }

    /**
     * Used in migration/import. Convert the style name to a uuid.
     *
     * @param context Current context, for accessing resources.
     * @param name    of the style
     *
     * @return style uuid
     */
    public static BooklistStyle getStyle(@NonNull final Context context,
                                         @NonNull final String name) {

        // check builtin first.
        for (BooklistStyle style : getBuiltinStyles().values()) {
            if (style.getLabel(context).equals(name)) {
                return style;
            }
        }

        // try user-defined
        try (DAO db = new DAO()) {
            for (BooklistStyle style : BooklistStyles.getUserStyles(db).values()) {
                if (style.getLabel(context).equals(name)) {
                    return style;
                }
            }
        }
        // not found...
        return DEFAULT_STYLE;
    }
}
