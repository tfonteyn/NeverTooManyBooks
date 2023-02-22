/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.StyleDao;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

/**
 * Helper class encapsulating {@link StyleDao} access and internal in-memory caches.
 */
public class StylesHelper {

    /** Preference for the current default style UUID to use. */
    private static final String PK_DEFAULT_STYLE = "bookList.style.current";

    private static final String ERROR_MISSING_UUID = "style.getUuid()";

    /**
     * We keep a cache of styles in memory.
     * Pre-loaded on first access.
     * Re-loaded when the Locale changes.
     * <p>
     * Key: uuid of style.
     */
    private final Map<String, Style> cache = new LinkedHashMap<>();

    /**
     * Get the specified style; {@code null} if not found.
     *
     * @param context Current context
     * @param uuid    UUID of the style to get.
     *
     * @return the style, or {@code null} if not found
     */
    @NonNull
    public Optional<Style> getStyle(@NonNull final Context context,
                                    @NonNull final String uuid) {
        final Style style = getAllStyles(context).get(uuid);
        return style == null ? Optional.empty() : Optional.of(style);
    }

    /**
     * Get the specified style. If not found, {@link #getDefault(Context)} will be returned.
     *
     * @param context Current context
     * @param uuid    UUID of the style to get.
     *
     * @return the style, or the default style if not found
     */
    @NonNull
    public Style getStyleOrDefault(@NonNull final Context context,
                                   @Nullable final String uuid) {
        if (uuid != null) {
            // Try to get user or builtin style
            final Style style = getAllStyles(context).get(uuid);
            if (style != null) {
                return style;
            }
        }

        // fall back to the user default.
        return getDefault(context);
    }

    /**
     * store the given style as the user default one.
     *
     * @param context Current context
     * @param uuid    style to set
     */
    public void setDefault(@NonNull final Context context,
                           @NonNull final String uuid) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putString(PK_DEFAULT_STYLE, uuid).apply();
    }

    /**
     * Get the user default style, or if none found, the Builtin default.
     *
     * @param context Current context
     *
     * @return the style.
     */
    @NonNull
    public Style getDefault(@NonNull final Context context) {
        final Map<String, Style> allStyles = getAllStyles(context);

        // read the global user default, or if not present the hardcoded default.
        final String uuid = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(PK_DEFAULT_STYLE, BuiltinStyle.DEFAULT_UUID);

        // Get the user or builtin or worst case the builtin default.
        final Style style = allStyles.get(uuid);
        //noinspection ConstantConditions
        return style != null ? style : allStyles.get(BuiltinStyle.DEFAULT_UUID);
    }

    /**
     * Get a list with all the styles, ordered by preferred menu position.
     * If 'all' is {@code true} the list contains the preferred styles at the top,
     * followed by the non-preferred styles.
     * If 'all' is {@code false} the list only contains the preferred styles.
     *
     * @param context Current context
     * @param all     if {@code true} then also return the non-preferred styles
     *
     * @return LinkedHashMap, key: uuid, value: style
     */
    @NonNull
    public List<Style> getStyles(@NonNull final Context context,
                                 final boolean all) {

        // combine all styles in a NEW list; we need to keep the original lists as-is.
        final Collection<Style> list = new ArrayList<>(getAllStyles(context).values());

        // and sort them in the user preferred order
        // The styles marked as preferred will have a menu-position < 1000,
        // while the non-preferred styles will be 1000.
        final List<Style> allStyles = list
                .stream()
                .sorted(Comparator.comparingInt(Style::getMenuPosition))
                .collect(Collectors.toList());
        if (all) {
            return allStyles;
        }

        final List<Style> preferredStyles = allStyles
                .stream()
                .filter(Style::isPreferred)
                .collect(Collectors.toList());

        if (preferredStyles.isEmpty()) {
            // If there are no preferred styles, return the full list
            return allStyles;
        } else {
            return preferredStyles;
        }
    }

    /**
     * Get all styles.
     *
     * @param context Current context
     *
     * @return an ordered Map of styles
     */
    @NonNull
    private Map<String, Style> getAllStyles(@NonNull final Context context) {

        if (cache.isEmpty()) {
            cache.putAll(ServiceLocator.getInstance().getStyleDao()
                                       .getBuiltinStyles(context));
            cache.putAll(ServiceLocator.getInstance().getStyleDao()
                                       .getUserStyles(context));
        }
        return cache;
    }

    public void clearCache() {
        cache.clear();
    }


    /**
     * Convenience wrapper that gets called after an import to re-sort all styles.
     *
     * @param context Current context
     */
    public void updateMenuOrder(@NonNull final Context context) {
        updateMenuOrder(getStyles(context, true));
    }

    /**
     * Sort the incoming list of styles according to their preferred status.
     *
     * @param styles list to sort and update
     */
    public void updateMenuOrder(@NonNull final Collection<Style> styles) {
        int order = 0;

        final StyleDao styleDao = ServiceLocator.getInstance().getStyleDao();

        // sort the preferred styles at the top
        for (final Style style : styles) {
            if (style.isPreferred()) {
                style.setMenuPosition(order);
                styleDao.update(style);
                order++;
            }
        }
        // followed by the non preferred styles
        for (final Style style : styles) {
            if (!style.isPreferred()) {
                style.setMenuPosition(order);
                styleDao.update(style);
                order++;
            }
        }

        // keep it safe and easy, just clear the caches; almost certainly overkill
        clearCache();
    }


    /**
     * Save the given style.
     * <p>
     * if an <strong>insert</strong> fails, the style retains id==0.
     *
     * @param style to save
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsert(@NonNull final Style style) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
        }

        final StyleDao styleDao = ServiceLocator.getInstance().getStyleDao();

        // resolve the id based on the UUID
        // e.g. we might be importing a style with a known UUID
        style.setId(styleDao.getStyleIdByUuid(style.getUuid()));

        if (style.getId() == 0) {
            // When we get here, we know it's a UserStyle.
            if (styleDao.insert((UserStyle) style) > 0) {
                cache.put(style.getUuid(), style);
                return true;
            }
            return false;

        } else {
            // Both BuiltinStyle and UserStyle
            return update(style);
        }
    }

    /**
     * Update the given style.
     *
     * @param style to update
     *
     * @return {@code true} on success
     */
    public boolean update(@NonNull final Style style) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
            // Reminder: do NOT use requirePositiveValue here!
            // It's perfectly fine to update builtin styles here;
            // ONLY new styles must be rejected
            SanityCheck.requireNonZero(style.getId(), "A new Style cannot be updated");
        }

        if (ServiceLocator.getInstance().getStyleDao().update(style)) {
            cache.put(style.getUuid(), style);
            return true;
        }
        return false;
    }

    /**
     * Delete the given style.
     *
     * @param style to delete
     *
     * @return {@code true} on success
     */
    public boolean delete(@NonNull final Style style) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
            SanityCheck.requirePositiveValue(style.getId(),
                                             "A new or builtin Style cannot be deleted");
        }

        if (ServiceLocator.getInstance().getStyleDao().delete(style)) {
            if (style.isUserDefined()) {
                cache.remove(style.getUuid());
            }
            return true;
        }
        return false;
    }


    public void purgeNodeStatesByStyle(final long styleId) {
        ServiceLocator.getInstance().getStyleDao().purgeNodeStatesByStyle(styleId);
    }
}
