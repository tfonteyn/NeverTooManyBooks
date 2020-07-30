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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin;

import android.view.View;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;

public abstract class BaseViewHolder {

    /** Container View for this item. */
    @NonNull
    protected final View itemView;

    protected BaseViewHolder(@NonNull final View view) {
        itemView = view;
        itemView.setTag(R.id.TAG_VIEW_HOLDER, this);
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
    protected static String toPrettyDateTime(@NonNull final LocalDateTime date,
                                             @NonNull final Locale displayLocale) {
        return date.format(DateTimeFormatter
                                   .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                                   .withLocale(displayLocale));
    }
}
