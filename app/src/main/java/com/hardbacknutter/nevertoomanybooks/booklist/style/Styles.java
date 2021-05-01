/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.StyleDao;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

/**
 * Helper class encapsulating {@link StyleDao} access and internal in-memory caches.
 * <p>
 * TODO: merge the two separate caches.
 */
public class Styles {

    /**
     * Preference for the current default style UUID to use.
     * Stored in global shared preferences.
     */
    private static final String PREF_BL_STYLE_CURRENT_DEFAULT = "bookList.style.current";

    private static final String ERROR_MISSING_UUID = "style.getUuid()";

    /**
     * We keep a cache of Builtin styles in memory.
     * Pre-loaded on first access.
     * Re-loaded when the Locale changes.
     * <p>
     * Key: uuid of style.
     */
    private final Map<String, BuiltinStyle> mBuiltinStyleCache = new LinkedHashMap<>();
    /**
     * We keep a cache of User styles in memory.
     * Pre-loaded on first access.
     * Re-loaded when the Locale changes.
     * <p>
     * Key: uuid of style.
     */
    private final Map<String, UserStyle> mUserStyleCache = new LinkedHashMap<>();

    /**
     * Get the specified style; {@code null} if not found.
     *
     * @param context Current context
     * @param uuid    UUID of the style to get.
     *
     * @return the style, or {@code null} if not found
     */
    @Nullable
    public ListStyle getStyle(@NonNull final Context context,
                              @NonNull final String uuid) {
        // Check Builtin first
        final ListStyle style = getBuiltinStyles(context).get(uuid);
        if (style != null) {
            return style;
        }

        // User defined ? or null if not found
        return getUserStyles(context).get(uuid);
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
    public ListStyle getStyleOrDefault(@NonNull final Context context,
                                       @NonNull final String uuid) {
        // Try to get user or builtin style
        final ListStyle style = getStyle(context, uuid);
        if (style != null) {
            return style;
        }

        // fall back to the user default.
        return getDefault(context);
    }

    /**
     * store the given style as the user default one.
     *
     * @param global the <strong>GLOBAL</strong> preferences
     * @param uuid   style to set
     */
    public void setDefault(@NonNull final SharedPreferences global,
                           @NonNull final String uuid) {
        global.edit().putString(PREF_BL_STYLE_CURRENT_DEFAULT, uuid).apply();
    }

    /**
     * Get the user default style, or if none found, the Builtin default.
     *
     * @param context Current context
     *
     * @return the style.
     */
    @NonNull
    public ListStyle getDefault(@NonNull final Context context) {

        // read the global user default, or if not present the hardcoded default.
        final String uuid = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(PREF_BL_STYLE_CURRENT_DEFAULT, BuiltinStyle.DEFAULT_UUID);

        // Try to get user or builtin style
        //noinspection ConstantConditions
        final ListStyle style = getStyle(context, uuid);
        if (style != null) {
            return style;
        }
        // fall back to the builtin default.
        //noinspection ConstantConditions
        return getBuiltinStyles(context).get(BuiltinStyle.DEFAULT_UUID);
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
    public List<ListStyle> getStyles(@NonNull final Context context,
                                     final boolean all) {

        // combine all styles in a NEW list; we need to keep the original lists as-is.
        final Collection<ListStyle> list = new ArrayList<>();
        list.addAll(getUserStyles(context).values());
        list.addAll(getBuiltinStyles(context).values());

        // and sort them in the user preferred order
        // The styles marked as preferred will have a menu-position < 1000,
        // while the non-preferred styles will be 1000.
        final List<ListStyle> allStyles = list
                .stream()
                .sorted((style1, style2) -> Integer.compare(
                        style1.getMenuPosition(), style2.getMenuPosition()))
                .collect(Collectors.toList());
        if (all) {
            return allStyles;
        }

        final List<ListStyle> preferredStyles = allStyles
                .stream()
                .filter(ListStyle::isPreferred)
                .collect(Collectors.toList());

        if (preferredStyles.isEmpty()) {
            // If there are no preferred styles, return the full list
            return allStyles;
        } else {
            return preferredStyles;
        }
    }

    /**
     * Get the user-defined Styles.
     *
     * @param context Current context
     *
     * @return an ordered Map of styles
     */
    @NonNull
    private Map<String, UserStyle> getUserStyles(@NonNull final Context context) {
        if (mUserStyleCache.isEmpty()) {
            mUserStyleCache.putAll(ServiceLocator.getInstance().getStyleDao()
                                                 .getUserStyles(context));
        }
        return mUserStyleCache;
    }

    /**
     * Get all builtin styles.
     *
     * @param context Current context
     *
     * @return an ordered Map of styles
     */
    @NonNull
    private Map<String, BuiltinStyle> getBuiltinStyles(@NonNull final Context context) {

        if (mBuiltinStyleCache.isEmpty()) {
            mBuiltinStyleCache.putAll(ServiceLocator.getInstance().getStyleDao()
                                                    .getBuiltinStyles(context));
        }
        return mBuiltinStyleCache;
    }

    public void clearCache() {
        // Clears the list of builtin styles. Why clear builtin data?
        // Because the entries also contain the user 'preferred' and 'menu order' data
        // which we want to be able to refresh after for example a restore from backup.
        mBuiltinStyleCache.clear();

        mUserStyleCache.clear();
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
    public void updateMenuOrder(@NonNull final Collection<ListStyle> styles) {
        int order = 0;

        final StyleDao styleDao = ServiceLocator.getInstance().getStyleDao();

        // sort the preferred styles at the top
        for (final ListStyle style : styles) {
            if (style.isPreferred()) {
                style.setMenuPosition(order);
                styleDao.update(style);
                order++;
            }
        }
        // followed by the non preferred styles
        for (final ListStyle style : styles) {
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
    public boolean updateOrInsert(@NonNull final ListStyle style) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
        }

        final StyleDao styleDao = ServiceLocator.getInstance().getStyleDao();

        // resolve the id based on the UUID
        // e.g. we're might be importing a style with a known UUID
        style.setId(styleDao.getStyleIdByUuid(style.getUuid()));
        if (style.getId() == 0) {
            if (styleDao.insert(style) > 0) {
                if (style instanceof UserStyle) {
                    mUserStyleCache.put(style.getUuid(), (UserStyle) style);
                }
                return true;
            }
            return false;

        } else {
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
    public boolean update(@NonNull final ListStyle style) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
            SanityCheck.requireNonZero(style.getId(), "A new Style cannot be updated");
        }

        if (ServiceLocator.getInstance().getStyleDao().update(style)) {
            // we're assuming the caches will exist & are populated
            if (style instanceof UserStyle) {
                mUserStyleCache.put(style.getUuid(), (UserStyle) style);
            } else if (style instanceof BuiltinStyle) {
                mBuiltinStyleCache.put(style.getUuid(), (BuiltinStyle) style);
            } else {
                throw new IllegalStateException("Unhandled style: " + style);
            }
            return true;
        }
        return false;
    }

    /**
     * Delete the given style.
     *
     * @param context Current context
     * @param style   to delete
     *
     * @return {@code true} on success
     */
    public boolean delete(@NonNull final Context context,
                          @NonNull final ListStyle style) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(style.getUuid(), ERROR_MISSING_UUID);
            SanityCheck.requirePositiveValue(style.getId(), "A new Style cannot be deleted");
        }

        if (ServiceLocator.getInstance().getStyleDao().delete(style)) {
            if (style instanceof UserStyle) {
                mUserStyleCache.remove(style.getUuid());
                context.deleteSharedPreferences(style.getUuid());
            }
            return true;
        }
        return false;
    }


    public void purgeNodeStatesByStyle(final long styleId) {
        ServiceLocator.getInstance().getStyleDao().purgeNodeStatesByStyle(styleId);
    }
}
