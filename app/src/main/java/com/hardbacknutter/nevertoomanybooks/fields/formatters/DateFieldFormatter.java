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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.dialogs.PartialDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextAccessor;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * FieldFormatter for 'date' fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 *      <li>Extract: <strong>View</strong></li>
 * </ul>
 * <p>
 * This class can be used in two ways:
 * <ol>
 *     <li>with a {@link TextAccessor}: the value is stored in the accessor,<br>
 *         This is meant to be used with a {@link PartialDatePickerDialogFragment}.</li>
 *     <li>with an {@link EditTextAccessor}: the value will be extracted from the View.<br>
 *         This is meant to be used as a free-entry field (i.e. the user types in the date).</li>
 * </ol>
 */
public class DateFieldFormatter
        implements EditFieldFormatter<String> {

    @NonNull
    private final Locale mLocale;

    /**
     * Constructor.
     *
     * @param locale to use
     */
    public DateFieldFormatter(@NonNull final Locale locale) {
        mLocale = locale;
    }

    /**
     * Display as a human-friendly date, local timezone.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        } else {
            return new PartialDate(rawValue).toPrettyDate(mLocale, rawValue);
        }
    }

    /**
     * Extract as an ISO date (full or partial)
     */
    @Override
    @NonNull
    public String extract(@NonNull final TextView view) {
        return new PartialDate(view.getText().toString().trim()).getIsoString();
    }
}
