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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.dao.StyleDao;

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
    @NonNull
    private final Supplier<StyleDao> styleDaoSupplier;
    @NonNull
    private final Supplier<Context> appContextSupplier;
    @Nullable
    private Style globalStyle;

    /**
     * Constructor.
     *
     * @param appContextSupplier deferred supplier for the raw Application Context
     * @param styleDaoSupplier   deferred supplier for the {@link StyleDao}
     */
    public StylesHelper(@NonNull final Supplier<Context> appContextSupplier,
                        @NonNull final Supplier<StyleDao> styleDaoSupplier) {
        this.appContextSupplier = appContextSupplier;
        this.styleDaoSupplier = styleDaoSupplier;
    }

    /**
     * Get the user preferences for the defaults for all styles.
     *
     * @return a hard-reference to the global/default style preferences
     *         i.e. any updates done will be preserved (but not automatically saved)
     */
    @NonNull
    public Style getGlobalStyle() {
        if (globalStyle == null) {
            globalStyle = styleDaoSupplier.get().getGlobalStyle();
        }
        return globalStyle;
    }

    /**
     * Get the specified style; {@code null} if not found.
     *
     * @param uuid UUID of the style to get.
     *
     * @return the style, or {@code null} if not found
     */
    @NonNull
    public Optional<Style> getStyle(@NonNull final String uuid) {
        final Style style = getAllStyles().get(uuid);
        return style == null ? Optional.empty() : Optional.of(style);
    }

    /**
     * Get the specified style. If not found, {@link #getDefault()} will be returned.
     *
     * @param uuid UUID of the style to get.
     *
     * @return the style, or the default style if not found
     */
    @NonNull
    public Style getStyleOrDefault(@Nullable final String uuid) {
        if (uuid != null) {
            // Try to get user or builtin style
            final Style style = getAllStyles().get(uuid);
            if (style != null) {
                return style;
            }
        }

        // fall back to the user default.
        return getDefault();
    }

    /**
     * Get the user default style, or if none found, the Builtin default.
     *
     * @return the style.
     */
    @NonNull
    public Style getDefault() {
        final Map<String, Style> allStyles = getAllStyles();

        // read the global user default, or if not present the hardcoded default.
        final String uuid = PreferenceManager
                .getDefaultSharedPreferences(appContextSupplier.get())
                .getString(PK_DEFAULT_STYLE, BuiltinStyle.DEFAULT_UUID);

        // Get the user or builtin or worst case the builtin default.
        final Style style = allStyles.get(uuid);
        //noinspection DataFlowIssue
        return style != null ? style : allStyles.get(BuiltinStyle.DEFAULT_UUID);
    }

    /**
     * store the given style as the user default one.
     *
     * @param uuid style to set
     */
    public void setDefault(@NonNull final String uuid) {
        PreferenceManager.getDefaultSharedPreferences(appContextSupplier.get())
                         .edit().putString(PK_DEFAULT_STYLE, uuid).apply();
    }

    /**
     * Get a list with all the styles, ordered by preferred menu position.
     * If 'all' is {@code true} the list contains the preferred styles at the top,
     * followed by the non-preferred styles.
     * If 'all' is {@code false} the list only contains the preferred styles.
     *
     * @param all if {@code true} then also return the non-preferred styles
     *
     * @return LinkedHashMap, key: uuid, value: style
     */
    @NonNull
    public List<Style> getStyles(final boolean all) {

        // combine all styles in a NEW list; we need to keep the original lists as-is.
        final Collection<Style> list = new ArrayList<>(getAllStyles().values());

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
     * @return an ordered Map of styles
     */
    @NonNull
    private Map<String, Style> getAllStyles() {

        if (cache.isEmpty()) {
            final StyleDao dao = styleDaoSupplier.get();
            cache.putAll(dao.getBuiltinStyles());
            cache.putAll(dao.getUserStyles());
        }
        return cache;
    }


    /**
     * Convenience wrapper that gets called after an import to re-sort all styles.
     *
     * @param context Current context
     */
    public void updateMenuOrder(@NonNull final Context context) {
        updateMenuOrder(context, getStyles(true));
    }

    /**
     * Sort the incoming list of styles according to their preferred status.
     *
     * @param context Current context
     * @param styles  list to sort and update
     */
    public void updateMenuOrder(@NonNull final Context context,
                                @NonNull final Collection<Style> styles) {
        int order = 0;

        final StyleDao dao = styleDaoSupplier.get();

        // sort the preferred styles at the top
        for (final Style style : styles) {
            if (style.isPreferred()) {
                style.setMenuPosition(order);
                dao.update(context, style);
                order++;
            }
        }
        // followed by the non preferred styles
        for (final Style style : styles) {
            if (!style.isPreferred()) {
                style.setMenuPosition(order);
                dao.update(context, style);
                order++;
            }
        }

        // keep it safe and easy, just clear the caches; almost certainly overkill
        cache.clear();
    }


    /**
     * Save the given style.
     * <p>
     * if an <strong>insert</strong> fails, the style retains id==0.
     *
     * @param context Current context
     * @param style   to save
     *
     * @return {@code true} on success
     *
     * @throws IllegalStateException if the UUID is missing
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsert(@NonNull final Context context,
                                  @NonNull final Style style) {
        if (BuildConfig.DEBUG /* always */) {
            if (style.getUuid().isEmpty()) {
                throw new IllegalStateException(ERROR_MISSING_UUID);
            }
        }

        if (style.getType() == StyleType.Global) {
            return update(context, style);
        }

        final StyleDao dao = styleDaoSupplier.get();

        // resolve the id based on the UUID
        // e.g. we might be importing a style with a known UUID
        style.setId(dao.getStyleIdByUuid(style.getUuid()));

        if (style.getId() == 0) {
            if (dao.insert(context, style) > 0) {
                cache.put(style.getUuid(), style);
                return true;
            }
            return false;

        } else {
            return update(context, style);
        }
    }

    /**
     * Update the given style.
     *
     * @param context Current context
     * @param style   to update
     *
     * @return {@code true} on success
     *
     * @throws IllegalStateException if the UUID is missing
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Style style) {
        if (BuildConfig.DEBUG /* always */) {
            if (style.getUuid().isEmpty()) {
                throw new IllegalStateException(ERROR_MISSING_UUID);
            }
            // Reminder: do NOT require a positive value here!
            // It's perfectly fine to update builtin styles;
            // ONLY NEW styles must be rejected
            if (style.getId() == 0) {
                throw new IllegalStateException("A new Style cannot be updated");
            }
        }

        if (styleDaoSupplier.get().update(context, style)) {
            if (style.getType() == StyleType.Global) {
                // ensure both the global style and any inheriting styles get reloaded
                globalStyle = null;
                cache.clear();
            } else {
                // replace (or insert) in the cache
                cache.put(style.getUuid(), style);
            }
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
     *
     * @throws IllegalStateException if the UUID is missing
     */
    public boolean delete(@NonNull final Style style) {
        if (BuildConfig.DEBUG /* always */) {
            if (style.getUuid().isEmpty()) {
                throw new IllegalStateException(ERROR_MISSING_UUID);
            }
            if (style.getId() <= 0) {
                throw new IllegalStateException("A new or builtin/global Style cannot be deleted");
            }
        }

        if (styleDaoSupplier.get().delete(style)) {
            // Sanity check, it should always be a StyleType.User
            if (style.getType() == StyleType.User) {
                cache.remove(style.getUuid());
            }
            return true;
        }
        return false;
    }

    /**
     * Purge Booklist node state data for the given Style.<br>
     * Called when a style is deleted or manually from the Styles management context menu.
     *
     * @param styleId to purge
     */
    public void purgeNodeStatesByStyle(final long styleId) {
        styleDaoSupplier.get().purgeNodeStatesByStyle(styleId);
    }
}
