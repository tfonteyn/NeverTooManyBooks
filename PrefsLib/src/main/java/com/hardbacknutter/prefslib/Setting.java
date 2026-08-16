/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.prefslib;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

public abstract class Setting {

    @NonNull
    private final Type type;
    @NonNull
    private final String key;
    @NonNull
    SettingsDataStore dataStore;

    @DrawableRes
    private int iconResId;

    @Nullable
    private String title;

    @StringRes
    private int summaryResId;
    @Nullable
    private CharSequence summary;

    @Nullable
    Function<Context, CharSequence> summaryProvider;

    protected Setting(@NonNull final Type type,
                      @NonNull final String key,
                      @NonNull final SettingsDataStore dataStore) {
        this.type = type;
        this.key = key;
        this.dataStore = dataStore;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @DrawableRes
    public int getIconResId() {
        return iconResId;
    }

    public void setIcon(@DrawableRes final int iconResId) {
        this.iconResId = iconResId;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable final String title) {
        this.title = title;
    }

    /**
     * Get the summary.
     * <p>
     * Source:
     * <ol>
     *     <li>The summary provider</li>
     *     <li>The summary resource id</li>
     *     <li>The summary fixed text</li>
     *     <li>{@code null}</li>
     * </ol>
     *
     * @param context Current context
     *
     * @return summary
     */
    @Nullable
    public CharSequence getSummary(@NonNull final Context context) {
        // The provider ALWAYS wins.
        if (summaryProvider != null) {
            return summaryProvider.apply(context);
        }
        if (summary == null && summaryResId != 0) {
            summary = context.getString(summaryResId);
        }
        return summary;
    }

    /**
     * Meant to be called from the initialisation block.
     *
     * @param summaryResId to set
     */
    public void setSummary(@StringRes final int summaryResId) {
        this.summaryResId = summaryResId;
        this.summary = null;
    }

    /**
     * Meant to be called from the initialisation block.
     *
     * @param summary to set
     */
    public void setSummary(@Nullable final CharSequence summary) {
        this.summaryResId = 0;
        this.summary = summary;
    }

    /**
     * Meant to be called from the initialisation block.
     *
     * @param provider to set
     */
    public void setSummaryProvider(@NonNull final Function<Context, CharSequence> provider) {
        this.summaryProvider = provider;
    }

    /**
     * Override the global data-store (as set by the {@link SettingsManager.Builder}.
     *
     * @param dataStore to use
     */
    public void setDataStore(@NonNull final SettingsDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public abstract void load(@NonNull Context context);

    public abstract void save(@NonNull Context context);

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Setting that = (Setting) o;
        return type == that.type
               && Objects.equals(key, that.key)
               && Objects.equals(title, that.title)
               && iconResId == that.iconResId
               && summaryResId == that.summaryResId
               && Objects.equals(summary, that.summary)
               && Objects.equals(summaryProvider, that.summaryProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key, iconResId, title, summaryResId, summary, summaryProvider);
    }

    public enum Type {
        /** Custom click-action. */
        Action,
        /** Click starts a Fragment. */
        Fragment,
        /** Click opens a list of sub-settings. */
        Group,

        /** Information/heading only. */
        Header,

        /** On/Off. */
        Boolean,
        /** Text input. */
        String,
        /** Value range. */
        Float,
        /** Single choice from a list. */
        SingleChoice,
        /** Multi choice from a list. */
        MultiChoice;

        @NonNull
        public static Type byId(final int id) {
            return Arrays.stream(values())
                         .filter(v -> v.ordinal() == id)
                         .findAny()
                         .orElseThrow();
        }
    }
}
