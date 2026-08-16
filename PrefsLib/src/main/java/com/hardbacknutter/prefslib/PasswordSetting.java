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
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Specialised {@link StringSetting} which automatically
 * sets the input-type and provides a masking summary.
 */
public class PasswordSetting
        extends StringSetting {

    private static final String MASK = "********";

    PasswordSetting(@NonNull final String key,
                    @NonNull final SettingsDataStore dataStore) {
        super(key, dataStore);

        setInputType(InputType.TYPE_CLASS_TEXT
                     | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    /**
     * Get the summary.
     * <p>
     * Source:
     * <ol>
     *     <li>The summary provider</li>
     *     <li>The mask</li>
     *     <li>The summary resource id</li>
     *     <li>The summary fixed text</li>
     *     <li>{@code null}</li>
     * </ol>
     *
     * @param context Current context
     *
     * @return summary
     */
    @Override
    @Nullable
    public CharSequence getSummary(@NonNull final Context context) {
        // The provider ALWAYS wins.
        if (summaryProvider != null) {
            return summaryProvider.apply(context);
        }

        @Nullable
        final String value = getValue();
        if (value != null && !value.isEmpty()) {
            return MASK;
        }

        // fallback to the fixed summary if any.
        return super.getSummary(context);
    }
}
