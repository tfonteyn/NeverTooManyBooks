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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.admin;

import android.view.View;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public abstract class BaseViewHolder {

    /** Container View for this item. */
    @NonNull
    protected final View itemView;

    BaseViewHolder(@NonNull final View view) {
        itemView = view;
    }

    /**
     * Pretty format a LocalDateTime to a datetime-string, using the specified locale.
     *
     * @param date          to format
     * @param displayLocale to use
     *
     * @return human readable datetime string
     */
    @NonNull
    static String toPrettyDateTime(@NonNull final LocalDateTime date,
                                   @NonNull final Locale displayLocale) {
        return date.format(DateTimeFormatter
                                   .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                                   .withLocale(displayLocale));
    }
}
