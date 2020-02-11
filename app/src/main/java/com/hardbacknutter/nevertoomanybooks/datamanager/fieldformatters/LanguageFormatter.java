/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

/**
 * FieldFormatter for language fields.
 * <ul>
 * <li>Multiple fields: <strong>yes</strong></li>
 * <li>Extract: <strong>View</strong></li>
 * </ul>
 */
public class LanguageFormatter
        implements FieldFormatter<String> {

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }
        return LanguageUtils.getDisplayName(context, rawValue);
    }

    @Override
    public void apply(@Nullable final String rawValue,
                      @NonNull final View view) {
        ((TextView) view).setText(format(view.getContext(), rawValue));
    }

    /**
     * Extract a localised language name to its ISO equivalent.
     *
     * @return the ISO3 code for the language
     */
    @NonNull
    @Override
    public String extract(@NonNull final View view) {
        String text = ((TextView) view).getText().toString().trim();
        return LanguageUtils.getISO3FromDisplayName(view.getContext(), text);
    }
}
