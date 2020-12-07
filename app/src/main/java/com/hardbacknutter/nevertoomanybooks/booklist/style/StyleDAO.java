/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/** Utility class encapsulating database access and internal in-memory caches. */
public final class StyleDAO {

    /**
     * Preference for the current default style UUID to use.
     * Stored in global shared preferences.
     */
    private static final String PREF_BL_STYLE_CURRENT_DEFAULT = "bookList.style.current";

    private static final String ERROR_MISSING_UUID = "style.getUuid()";

    private StyleDAO() {
    }


    /**
     * Save the given style.
     * <p>
     * if an <strong>insert</strong> fails, the style retains id==0.
     *
     * @param db    Database Access
     * @param style to save
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean updateOrInsert(@NonNull final DAO db,
                                         @NonNull final ListStyle style) {

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
        }

        // resolve the id based on the UUID
        // e.g. we're might be importing a style with a known UUID
        style.setId(db.getStyleIdByUuid(style.getUuid()));

        if (style.getId() == 0) {
            if (BuildConfig.DEBUG /* always */) {
                if (style instanceof BuiltinStyle) {
                    throw new IllegalArgumentException("Builtin Style cannot be inserted");
                }
            }
            return insert(db, style);

        } else {
            return update(db, style);
        }
    }

    /**
     * Insert the given UserStyle.
     *
     * @param db    Database Access
     * @param style to insert
     *
     * @return {@code true} on success
     */
    public static boolean insert(@NonNull final DAO db,
                                 @NonNull final ListStyle style) {

        if (db.insert(style) > 0) {
            if (style instanceof UserStyle) {
                UserStyles.S_USER_STYLES.put(style.getUuid(), (UserStyle) style);
            }
            return true;
        }
        return false;
    }

    /**
     * Update the given style.
     *
     * @param db    Database Access
     * @param style to update
     *
     * @return {@code true} on success
     */
    public static boolean update(@NonNull final DAO db,
                                 @NonNull final ListStyle style) {

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
            SanityCheck.requireNonZero(style.getId(), "A new Style cannot be updated");
        }

        if (db.update(style)) {
            if (style instanceof UserStyle) {
                UserStyles.S_USER_STYLES.put(style.getUuid(), (UserStyle) style);
            } else if (style instanceof BuiltinStyle) {
                BuiltinStyles.S_BUILTIN_STYLES.put(style.getUuid(), (BuiltinStyle) style);
            } else {
                throw new IllegalStateException();
            }
            return true;
        }
        return false;
    }


    /**
     * Delete the given style.
     *
     * @param context Current context
     * @param db      Database Access
     * @param style   to delete
     *
     * @return {@code true} on success
     */
    public static boolean delete(@NonNull final Context context,
                                 @NonNull final DAO db,
                                 @NonNull final ListStyle style) {

        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
            SanityCheck.requirePositiveValue(style.getId(), "A new Style cannot be deleted");
        }

        // sanity check
        if (style instanceof BuiltinStyle) {
            throw new IllegalArgumentException("A Builtin Style cannot be deleted");
        }

        if (db.delete(style)) {
            UserStyles.S_USER_STYLES.remove(style.getUuid());
            context.deleteSharedPreferences(style.getUuid());
            return true;
        }
        return false;
    }


    public static void updateMenuOrder(@NonNull final DAO db,
                                       @NonNull final Collection<ListStyle> styles) {
        int order = 0;

        // sort the preferred styles at the top
        for (final ListStyle style : styles) {
            if (style.isPreferred()) {
                style.setMenuPosition(order);
                db.update(style);
                order++;
            }
        }
        // followed by the non preferred styles
        for (final ListStyle style : styles) {
            if (!style.isPreferred()) {
                style.setMenuPosition(order);
                db.update(style);
                order++;
            }
        }

        // keep it safe and easy, just clear the caches; almost certainly overkill
        StyleDAO.clearCache();
    }


    /**
     * Get a list with all the styles, ordered by preferred menu position.
     * If 'all' is {@code true} the list contains the preferred styles at the top,
     * followed by the non-preferred styles.
     * If 'all' is {@code false} the list only contains the preferred styles.
     *
     * @param context Current context
     * @param db      Database Access
     * @param all     if {@code true} then also return the non-preferred styles
     *
     * @return LinkedHashMap, key: uuid, value: style
     */
    @NonNull
    public static ArrayList<ListStyle> getStyles(@NonNull final Context context,
                                                 @NonNull final DAO db,
                                                 final boolean all) {

        // combine all styles in a NEW list; we need to keep the original lists as-is.
        final Collection<ListStyle> list = new ArrayList<>();
        list.addAll(UserStyles.getStyles(context, db).values());
        list.addAll(BuiltinStyles.getStyles(context, db).values());

        // and sort them in the user preferred order
        // The styles marked as preferred will have a menu-position < 1000,
        // while the non-preferred styles will be 1000.
        final ArrayList<ListStyle> allStyles = list
                .stream()
                .sorted((style1, style2) -> Integer.compare(
                        style1.getMenuPosition(), style2.getMenuPosition()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (all) {
            return allStyles;
        }

        final ArrayList<ListStyle> preferredStyles = allStyles
                .stream()
                .filter(ListStyle::isPreferred)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!preferredStyles.isEmpty()) {
            return preferredStyles;

        } else {
            // If there are no preferred styles, just return the full list
            return allStyles;
        }
    }

    /**
     * Get the specified style; {@code null} if not found.
     *
     * @param context Current context
     * @param db      Database Access
     * @param uuid    UUID of the style to get.
     *
     * @return the style, or {@code null} if not found
     */
    @Nullable
    public static ListStyle getStyle(@NonNull final Context context,
                                     @NonNull final DAO db,
                                     @NonNull final String uuid) {
        // Check Builtin first
        final ListStyle style = BuiltinStyles.getStyles(context, db).get(uuid);
        if (style != null) {
            return style;
        }

        // User defined ? or null if not found
        return UserStyles.getStyles(context, db).get(uuid);
    }

    /**
     * Get the specified style. If not found, {@link #getDefault(Context, DAO)} will be returned.
     *
     * @param context Current context
     * @param db      Database Access
     * @param uuid    UUID of the style to get.
     *
     * @return the style, or the default style if not found
     */
    @NonNull
    public static ListStyle getStyleOrDefault(@NonNull final Context context,
                                              @NonNull final DAO db,
                                              @NonNull final String uuid) {
        // Try to get user or builtin style
        final ListStyle style = getStyle(context, db, uuid);
        if (style != null) {
            return style;
        }
        // fall back to the user default.
        return getDefault(context, db);
    }

    /**
     * Get the user default style, or if none found, the Builtin default.
     *
     * @param context Current context
     * @param db      Database Access
     *
     * @return the style.
     */
    @NonNull
    public static ListStyle getDefault(@NonNull final Context context,
                                       @NonNull final DAO db) {

        // read the global user default, or if not present the hardcoded default.
        final String uuid = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PREF_BL_STYLE_CURRENT_DEFAULT,
                                                        BuiltinStyles.DEFAULT_STYLE_UUID);

        // Try to get user or builtin style
        final ListStyle style = getStyle(context, db, uuid);
        if (style != null) {
            return style;
        }
        // fall back to the builtin default.
        return BuiltinStyles.getDefault(context, db);
    }

    /**
     * store the given style as the user default one.
     *
     * @param global the <strong>GLOBAL</strong> preferences
     * @param uuid   style to set
     */
    public static void setDefault(@NonNull final SharedPreferences global,
                                  @NonNull final String uuid) {
        global.edit().putString(PREF_BL_STYLE_CURRENT_DEFAULT, uuid).apply();
    }

    public static void clearCache() {
        UserStyles.clearCache();
        BuiltinStyles.clearCache();
    }

    /**
     * Cache of user-defined booklist styles.
     */
    private static final class UserStyles {

        /**
         * We keep a cache of User styles in memory as it's to costly to keep
         * re-creating {@link UserStyles} objects.
         * Pre-loaded on first access.
         * Re-loaded when the Locale changes.
         * <p>
         * Key: uuid of style.
         */
        private static final Map<String, UserStyle> S_USER_STYLES = new LinkedHashMap<>();

        static void clearCache() {
            S_USER_STYLES.clear();
        }

        /**
         * Get the user-defined Styles from the database.
         *
         * @param context Current context
         * @param db      Database Access
         *
         * @return an ordered unmodifiableMap of styles
         */
        @NonNull
        static Map<String, UserStyle> getStyles(@NonNull final Context context,
                                                @NonNull final DAO db) {
            if (S_USER_STYLES.isEmpty()) {
                final Map<String, UserStyle> map = new LinkedHashMap<>();

                try (Cursor cursor = db.fetchStyles(true)) {
                    final DataHolder rowData = new CursorRow(cursor);
                    while (cursor.moveToNext()) {
                        final String uuid = rowData.getString(DBDefinitions.KEY_UUID);
                        map.put(uuid, new UserStyle(context, rowData));
                    }
                }


                S_USER_STYLES.putAll(map);
            }
            return Collections.unmodifiableMap(S_USER_STYLES);
        }
    }

    /**
     * Collection of system-defined booklist styles.
     * <p>
     * The UUID's should never be changed.
     */
    public static final class BuiltinStyles {

        // NEWTHINGS: ListStyle. Make sure to update the max id when adding a style!
        // and make sure a row is added to the database styles table.
        // next max is -20
        public static final int MAX_ID = -19;

        /**
         * Note the hardcoded negative ID's.
         * These id/uuid's should NEVER be changed as they will
         * get stored in preferences and serialized. Take care not to add duplicates.
         */
        static final int AUTHOR_THEN_SERIES_ID = -1;
        private static final String AUTHOR_THEN_SERIES_UUID
                = "6a82c4c0-48f1-4130-8a62-bbf478ffe184";

        static final String DEFAULT_STYLE_UUID = AUTHOR_THEN_SERIES_UUID;

        private static final int UNREAD_AUTHOR_THEN_SERIES_ID = -2;
        private static final String UNREAD_AUTHOR_THEN_SERIES_UUID
                = "f479e979-c43f-4b0b-9c5b-6942964749df";

        private static final int COMPACT_ID = -3;
        private static final String COMPACT_UUID
                = "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16";

        private static final int TITLE_FIRST_LETTER_ID = -4;
        private static final String TITLE_FIRST_LETTER_UUID
                = "16b4ecdf-edef-4bf2-a682-23f7230446c8";

        private static final int SERIES_ID = -5;
        private static final String SERIES_UUID
                = "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335";

        private static final int GENRE_ID = -6;
        private static final String GENRE_UUID
                = "edc5c178-60f0-40e7-9674-e08445b6c942";

        private static final int LENDING_ID = -7;
        private static final String LENDING_UUID
                = "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3";

        private static final int READ_AND_UNREAD_ID = -8;
        private static final String READ_AND_UNREAD_UUID
                = "e3678890-7785-4870-9213-333a68293a49";

        private static final int PUBLICATION_DATA_ID = -9;
        private static final String PUBLICATION_DATA_UUID
                = "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796";

        private static final int DATE_ADDED_ID = -10;
        private static final String DATE_ADDED_UUID
                = "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521";

        private static final int DATE_ACQUIRED_ID = -11;
        private static final String DATE_ACQUIRED_UUID
                = "b3255b1f-5b07-4b3e-9700-96c0f8f35a58";

        private static final int AUTHOR_AND_YEAR_ID = -12;
        private static final String AUTHOR_AND_YEAR_UUID
                = "7c9ad91e-df7c-415a-a205-cdfabff5465d";

        private static final int FORMAT_ID = -13;
        private static final String FORMAT_UUID
                = "bdc43f17-2a95-42ef-b0f8-c750ef920f28";

        private static final int DATE_READ_ID = -14;
        private static final String DATE_READ_UUID
                = "034fe547-879b-4fa0-997a-28d769ba5a84";

        private static final int LOCATION_ID = -15;
        private static final String LOCATION_UUID
                = "e21a90c9-5150-49ee-a204-0cab301fc5a1";

        private static final int LANGUAGE_ID = -16;
        private static final String LANGUAGE_UUID
                = "00379d95-6cb2-40e6-8c3b-f8278f34750a";

        private static final int RATING_ID = -17;
        private static final String RATING_UUID
                = "20a2ebdf-81a7-4eca-a3a9-7275062b907a";

        private static final int BOOKSHELF_ID = -18;
        private static final String BOOKSHELF_UUID
                = "999d383e-6e76-416a-86f9-960c729aa718";

        private static final int DATE_LAST_UPDATE_ID = -19;
        private static final String DATE_LAST_UPDATE_UUID
                = "427a0da5-0779-44b6-89e9-82772e5ad5ef";
        /**
         * Use the NEGATIVE builtin style id to get the UUID for it. Element 0 is not used.
         * NEVER change the order.
         */
        private static final String[] ID_UUID = {
                "",
                AUTHOR_THEN_SERIES_UUID,
                UNREAD_AUTHOR_THEN_SERIES_UUID,
                COMPACT_UUID,
                TITLE_FIRST_LETTER_UUID,
                SERIES_UUID,

                GENRE_UUID,
                LENDING_UUID,
                READ_AND_UNREAD_UUID,
                PUBLICATION_DATA_UUID,
                DATE_ADDED_UUID,

                DATE_ACQUIRED_UUID,
                AUTHOR_AND_YEAR_UUID,
                FORMAT_UUID,
                DATE_READ_UUID,
                LOCATION_UUID,

                LANGUAGE_UUID,
                RATING_UUID,
                BOOKSHELF_UUID,
                DATE_LAST_UPDATE_UUID,
                };
        /**
         * We keep a cache of Builtin styles in memory as it's to costly to keep
         * re-creating {@link BuiltinStyle} objects.
         * Pre-loaded on first access.
         * Re-loaded when the Locale changes.
         * <p>
         * Key: uuid of style.
         */
        private static final Map<String, BuiltinStyle> S_BUILTIN_STYLES = new LinkedHashMap<>();

        private BuiltinStyles() {
        }

        static void clearCache() {
            S_BUILTIN_STYLES.clear();
        }

        /**
         * Get the hardcoded default style.
         *
         * @param context Current context
         * @param db      Database Access
         *
         * @return style {@link #AUTHOR_THEN_SERIES_ID}
         */
        @NonNull
        public static BuiltinStyle getDefault(@NonNull final Context context,
                                              @NonNull final DAO db) {
            if (S_BUILTIN_STYLES.isEmpty()) {
                create(context, db);
            }
            return Objects.requireNonNull(S_BUILTIN_STYLES.get(AUTHOR_THEN_SERIES_UUID));
        }


        /**
         * Get all builtin styles.
         *
         * @param context Current context
         * @param db      Database Access
         *
         * @return an ordered unmodifiableMap of all builtin styles.
         */
        @NonNull
        static Map<String, BuiltinStyle> getStyles(@NonNull final Context context,
                                                   @NonNull final DAO db) {

            if (S_BUILTIN_STYLES.isEmpty()) {
                create(context, db);
            }
            return Collections.unmodifiableMap(S_BUILTIN_STYLES);
        }

        public static boolean isBuiltin(@NonNull final String uuid) {
            if (!uuid.isEmpty()) {
                for (final String key : ID_UUID) {
                    if (key.equals(uuid)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Only used during App setup to create the styles table.
         *
         * @param id to lookup
         *
         * @return the uuid
         */
        @NonNull
        public static String getUuidById(final int id) {
            return ID_UUID[id];
        }

        @SuppressWarnings("ConstantConditions")
        private static void create(@NonNull final Context context,
                                   @NonNull final DAO db) {

            final Map<Integer, Boolean> preferred = new HashMap<>();
            final Map<Integer, Integer> menuPos = new HashMap<>();

            try (Cursor cursor = db.fetchStyles(false)) {
                final DataHolder rowData = new CursorRow(cursor);
                while (cursor.moveToNext()) {
                    preferred.put(rowData.getInt(DBDefinitions.KEY_PK_ID),
                                  rowData.getBoolean(DBDefinitions.KEY_STYLE_IS_PREFERRED));
                    menuPos.put(rowData.getInt(DBDefinitions.KEY_PK_ID),
                                rowData.getInt(DBDefinitions.KEY_STYLE_MENU_POSITION));
                }
            }

            BuiltinStyle style;

            // Author/Series
            style = new BuiltinStyle(context,
                                     AUTHOR_THEN_SERIES_ID,
                                     AUTHOR_THEN_SERIES_UUID,
                                     R.string.style_builtin_author_series,
                                     preferred.get(AUTHOR_THEN_SERIES_ID),
                                     menuPos.get(AUTHOR_THEN_SERIES_ID),
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Unread
            style = new BuiltinStyle(context,
                                     UNREAD_AUTHOR_THEN_SERIES_ID,
                                     UNREAD_AUTHOR_THEN_SERIES_UUID,
                                     R.string.style_builtin_unread,
                                     preferred.get(UNREAD_AUTHOR_THEN_SERIES_ID),
                                     menuPos.get(UNREAD_AUTHOR_THEN_SERIES_ID),
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            style.getFilters().setFilter(Filters.PK_FILTER_READ, false);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Compact
            style = new BuiltinStyle(context,
                                     COMPACT_ID,
                                     COMPACT_UUID,
                                     R.string.style_builtin_compact,
                                     preferred.get(COMPACT_ID),
                                     menuPos.get(COMPACT_ID),
                                     BooklistGroup.AUTHOR);
            style.getTextScale().set(TextScale.TEXT_SCALE_1_SMALL);
            style.getListScreenBookFields()
                 .setShowField(ListScreenBookFields.PK_COVERS, false);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Title
            style = new BuiltinStyle(context,
                                     TITLE_FIRST_LETTER_ID,
                                     TITLE_FIRST_LETTER_UUID,
                                     R.string.style_builtin_first_letter_book_title,
                                     preferred.get(TITLE_FIRST_LETTER_ID),
                                     menuPos.get(TITLE_FIRST_LETTER_ID),
                                     BooklistGroup.BOOK_TITLE_LETTER);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Series
            style = new BuiltinStyle(context,
                                     SERIES_ID,
                                     SERIES_UUID,
                                     R.string.style_builtin_series,
                                     preferred.get(SERIES_ID),
                                     menuPos.get(SERIES_ID),
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Genre
            style = new BuiltinStyle(context,
                                     GENRE_ID,
                                     GENRE_UUID,
                                     R.string.style_builtin_genre,
                                     preferred.get(GENRE_ID),
                                     menuPos.get(GENRE_ID),
                                     BooklistGroup.GENRE,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Lending
            style = new BuiltinStyle(context,
                                     LENDING_ID,
                                     LENDING_UUID,
                                     R.string.style_builtin_lending,
                                     preferred.get(LENDING_ID),
                                     menuPos.get(LENDING_ID),
                                     BooklistGroup.LENDING,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Read & Unread
            style = new BuiltinStyle(context,
                                     READ_AND_UNREAD_ID,
                                     READ_AND_UNREAD_UUID,
                                     R.string.style_builtin_read_and_unread,
                                     preferred.get(READ_AND_UNREAD_ID),
                                     menuPos.get(READ_AND_UNREAD_ID),
                                     BooklistGroup.READ_STATUS,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Publication date
            style = new BuiltinStyle(context,
                                     PUBLICATION_DATA_ID,
                                     PUBLICATION_DATA_UUID,
                                     R.string.style_builtin_publication_date,
                                     preferred.get(PUBLICATION_DATA_ID),
                                     menuPos.get(PUBLICATION_DATA_ID),
                                     BooklistGroup.DATE_PUBLISHED_YEAR,
                                     BooklistGroup.DATE_PUBLISHED_MONTH,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Added date
            style = new BuiltinStyle(context,
                                     DATE_ADDED_ID,
                                     DATE_ADDED_UUID,
                                     R.string.style_builtin_added_date,
                                     preferred.get(DATE_ADDED_ID),
                                     menuPos.get(DATE_ADDED_ID),
                                     BooklistGroup.DATE_ADDED_YEAR,
                                     BooklistGroup.DATE_ADDED_MONTH,
                                     BooklistGroup.DATE_ADDED_DAY,
                                     BooklistGroup.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Acquired date
            style = new BuiltinStyle(context,
                                     DATE_ACQUIRED_ID,
                                     DATE_ACQUIRED_UUID,
                                     R.string.style_builtin_acquired_date,
                                     preferred.get(DATE_ACQUIRED_ID),
                                     menuPos.get(DATE_ACQUIRED_ID),
                                     BooklistGroup.DATE_ACQUIRED_YEAR,
                                     BooklistGroup.DATE_ACQUIRED_MONTH,
                                     BooklistGroup.DATE_ACQUIRED_DAY,
                                     BooklistGroup.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Author/Publication date
            style = new BuiltinStyle(context,
                                     AUTHOR_AND_YEAR_ID,
                                     AUTHOR_AND_YEAR_UUID,
                                     R.string.style_builtin_author_year,
                                     preferred.get(AUTHOR_AND_YEAR_ID),
                                     menuPos.get(AUTHOR_AND_YEAR_ID),
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.DATE_PUBLISHED_YEAR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Format
            style = new BuiltinStyle(context,
                                     FORMAT_ID,
                                     FORMAT_UUID,
                                     R.string.style_builtin_format,
                                     preferred.get(FORMAT_ID),
                                     menuPos.get(FORMAT_ID),
                                     BooklistGroup.FORMAT);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Read date
            style = new BuiltinStyle(context,
                                     DATE_READ_ID,
                                     DATE_READ_UUID,
                                     R.string.style_builtin_read_date,
                                     preferred.get(DATE_READ_ID),
                                     menuPos.get(DATE_READ_ID),
                                     BooklistGroup.DATE_READ_YEAR,
                                     BooklistGroup.DATE_READ_MONTH,
                                     BooklistGroup.AUTHOR);
            style.getFilters().setFilter(Filters.PK_FILTER_READ, true);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Location
            style = new BuiltinStyle(context,
                                     LOCATION_ID,
                                     LOCATION_UUID,
                                     R.string.style_builtin_location,
                                     preferred.get(LOCATION_ID),
                                     menuPos.get(LOCATION_ID),
                                     BooklistGroup.LOCATION,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Location
            style = new BuiltinStyle(context,
                                     LANGUAGE_ID,
                                     LANGUAGE_UUID,
                                     R.string.style_builtin_language,
                                     preferred.get(LANGUAGE_ID),
                                     menuPos.get(LANGUAGE_ID),
                                     BooklistGroup.LANGUAGE,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Rating
            style = new BuiltinStyle(context,
                                     RATING_ID,
                                     RATING_UUID,
                                     R.string.style_builtin_rating,
                                     preferred.get(RATING_ID),
                                     menuPos.get(RATING_ID),
                                     BooklistGroup.RATING,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Bookshelf
            style = new BuiltinStyle(context,
                                     BOOKSHELF_ID,
                                     BOOKSHELF_UUID,
                                     R.string.style_builtin_bookshelf,
                                     preferred.get(BOOKSHELF_ID),
                                     menuPos.get(BOOKSHELF_ID),
                                     BooklistGroup.BOOKSHELF,
                                     BooklistGroup.AUTHOR,
                                     BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Update date
            style = new BuiltinStyle(context,
                                     DATE_LAST_UPDATE_ID,
                                     DATE_LAST_UPDATE_UUID,
                                     R.string.style_builtin_update_date,
                                     preferred.get(DATE_LAST_UPDATE_ID),
                                     menuPos.get(DATE_LAST_UPDATE_ID),
                                     BooklistGroup.DATE_LAST_UPDATE_YEAR,
                                     BooklistGroup.DATE_LAST_UPDATE_MONTH,
                                     BooklistGroup.DATE_LAST_UPDATE_DAY);
            style.getListScreenBookFields()
                 .setShowField(ListScreenBookFields.PK_AUTHOR, true);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // NEWTHINGS: BuiltinStyle: add a new builtin style
        }

    }
}
