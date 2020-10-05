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
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

/** Utility class encapsulating database access and internal in-memory cache list. */
public final class StyleDAO {

    /**
     * We keep a cache of User styles in memory as it's to costly to keep
     * re-creating {@link BooklistStyle} objects.
     * Pre-loaded on first access.
     * Re-loaded when the Locale changes.
     * <p>
     * Key: uuid of style.
     */
    private static final Map<String, BooklistStyle> S_USER_STYLES = new LinkedHashMap<>();

    private StyleDAO() {
    }

    public static void clearCache() {
        S_USER_STYLES.clear();
    }

    /**
     * Save the given style to the database / cached list.
     * <p>
     * if an insert fails, the style retains id==0.
     *
     * @param db    Database Access
     * @param style to save
     */
    public static void updateOrInsert(@NonNull final DAO db,
                                      @NonNull final BooklistStyle style) {

        if (!style.isUserDefined()) {
            throw new IllegalArgumentException("Builtin Style cannot be saved to database");
        }

        SanityCheck.requireValue(style.getUuid(), "style.getUuid()");

        // check if the style already exists.
        final long existingId = db.getStyleIdByUuid(style.getUuid());
        if (existingId == 0) {
            if (db.insert(style) > 0) {
                S_USER_STYLES.put(style.getUuid(), style);
            }
        } else {
            style.setId(existingId);
            S_USER_STYLES.put(style.getUuid(), style);
        }
    }

    /**
     * Update the given style.
     *
     * @param style to update
     */
    public static void update(@NonNull final BooklistStyle style) {
        if (style.getId() == 0 || !style.isUserDefined()) {
            throw new IllegalArgumentException("Only an existing user Style can be updated");
        }
        // Note there is no database access needed here
        S_USER_STYLES.put(style.getUuid(), style);
    }

    /**
     * Delete the given style from the database / cached list.
     *
     * @param context Current context
     * @param db      Database Access
     * @param style   to delete
     */
    public static void delete(@NonNull final Context context,
                              @NonNull final DAO db,
                              @NonNull final BooklistStyle style) {

        // cannot delete a builtin or a 'new' style(id==0)
        if (style.getId() == 0 || !style.isUserDefined()) {
            throw new IllegalArgumentException("Builtin Style cannot be deleted");
        }
        // sanity check, cannot delete the global style settings.
        if (style.getUuid().isEmpty()) {
            throw new IllegalArgumentException("Global Style cannot be deleted");
        }

        S_USER_STYLES.remove(style.getUuid());
        db.delete(style);

        if (Build.VERSION.SDK_INT >= 24) {
            context.deleteSharedPreferences(style.getUuid());
        } else {
            context.getSharedPreferences(style.getUuid(), Context.MODE_PRIVATE)
                   .edit().clear().apply();
        }
    }

    public static void discard(@NonNull final Context context,
                               @NonNull final BooklistStyle style) {
        // can ONLY discard a new style
        if (style.getId() != 0) {
            throw new IllegalArgumentException("Can only discard a new style");
        }
        if (Build.VERSION.SDK_INT >= 24) {
            context.deleteSharedPreferences(style.getUuid());
        } else {
            context.getSharedPreferences(style.getUuid(), Context.MODE_PRIVATE)
                   .edit().clear().apply();
        }
    }

    /**
     * Get the user-defined Styles from the database.
     *
     * @param context Current context
     * @param db      Database Access
     *
     * @return an ordered unmodifiableMap of BooklistStyle
     */
    @NonNull
    public static Map<String, BooklistStyle> getStyles(@NonNull final Context context,
                                                       @NonNull final DAO db) {
        if (S_USER_STYLES.isEmpty()) {
            S_USER_STYLES.putAll(db.getUserStyles(context));
        }
        return Collections.unmodifiableMap(S_USER_STYLES);
    }

    @Nullable
    static BooklistStyle getStyle(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final String uuid) {
        return getStyles(context, db).get(uuid);
    }
}
