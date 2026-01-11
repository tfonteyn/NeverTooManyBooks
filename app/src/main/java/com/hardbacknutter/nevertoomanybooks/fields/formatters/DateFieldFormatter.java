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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.fields.TextViewField;

/**
 * FieldFormatter for 'date' fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> but sharing the same Locale.</li>
 * </ul>
 * <p>
 * This class can be used with a {@link TextViewField}: the value is stored in the object,<br>
 * This is meant to be used with a Date-Picker (i.e. the user selects a date).
 */
public class DateFieldFormatter
        implements FieldFormatter<String> {

    @NonNull
    private final Locale locale;
    private final boolean isUtc;

    @NonNull
    private final PartialDateParser parser = new PartialDateParser();

    /**
     * Constructor.
     *
     * @param locale Current Locale
     * @param isUtc  set to {@code true} if dates are to be handled as UTC
     *               with {@link #format(Context, String)}
     */
    public DateFieldFormatter(@NonNull final Locale locale,
                              final boolean isUtc) {
        this.locale = locale;
        this.isUtc = isUtc;
    }

    /**
     * Display as a human-friendly date, local timezone.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        return parser.parse(rawValue, null, isUtc)
                     .map(date -> date.toDisplay(locale, rawValue))
                     .orElse("");
    }
}
