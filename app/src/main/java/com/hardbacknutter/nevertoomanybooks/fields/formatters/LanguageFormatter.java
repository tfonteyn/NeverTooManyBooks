/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * FieldFormatter for language fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> but sharing the same Locale.</li>
 * </ul>
 */
public class LanguageFormatter
        implements EditFieldFormatter<String> {

    @NonNull
    private final Locale locale;
    @NonNull
    private final Languages languages;

    /**
     * Constructor.
     *
     * @param locale    to use
     * @param languages the language cache container
     */
    public LanguageFormatter(@NonNull final Locale locale,
                             @NonNull final Languages languages) {
        this.locale = locale;
        this.languages = languages;
    }

    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        } else {
            return languages.getDisplayLanguageFromISO3(context, rawValue);
        }
    }

    /**
     * Extract a localised language name to its ISO equivalent.
     *
     * @return the ISO3 code for the language
     */
    @Override
    @NonNull
    public String extract(@NonNull final Context context,
                          @NonNull final String text) {
        return languages.getISO3FromDisplayLanguage(context, locale, text);
    }
}
