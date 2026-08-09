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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * The value is a {@code boolean}.
 * <p>
 * Dev. note: technically it should be a {@code Boolean} object
 * allowing {@code null} for not-set.
 * But compatibility with SharedPreferences dictates not-set == false.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class BooleanSetting
        extends Setting {

    @Nullable
    private String summaryFalse;
    @Nullable
    private String summaryTrue;

    private boolean checked;

    BooleanSetting(@NonNull final String key,
                   @NonNull final SettingsDataStore dataStore) {
        super(Type.Boolean, key, dataStore);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(final boolean value) {
        this.checked = value;
    }

    @Nullable
    public String getSummaryFalse() {
        return summaryFalse;
    }

    public void setSummaryFalse(@Nullable final String summaryFalse) {
        this.summaryFalse = summaryFalse;
    }

    @Nullable
    public String getSummaryTrue() {
        return summaryTrue;
    }

    public void setSummaryTrue(@Nullable final String summaryTrue) {
        this.summaryTrue = summaryTrue;
    }

    /**
     * Get the summary.
     * <p>
     * Source:
     * <ol>
     *     <li>The {@code true} or {@code false} summary texts
     *      * as given in the constructor</li>
     *      <li>{@code null} if those are not provided</li>
     * </ol>
     *
     * @param context Current context
     *
     * @return summary
     */
    @Override
    @Nullable
    public CharSequence getSummary(@NonNull final Context context) {
        if (summaryProvider != null) {
            return summaryProvider.apply(context);
        }

        if (summaryFalse == null && !checked) {
            return null;
        }
        if (summaryTrue == null && checked) {
            return null;
        }
        return checked ? summaryTrue : summaryFalse;
    }

    @Override
    public void load(@NonNull final Context context) {
        setChecked(dataStore.getBoolean(getKey(), checked));
    }

    @Override
    public void save(@NonNull final Context context) {
        dataStore.putBoolean(getKey(), checked);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final BooleanSetting that = (BooleanSetting) o;
        return checked == that.checked
               && Objects.equals(summaryFalse, that.summaryFalse)
               && Objects.equals(summaryTrue, that.summaryTrue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), summaryFalse, summaryTrue, checked);
    }
}
