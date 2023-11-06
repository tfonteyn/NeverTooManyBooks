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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

/**
 * An Entity always has an id and some user-friendly label (aka 'displayName')
 * and optionally it's own Locale.
 */
public interface Entity {

    /**
     * Get the database row id of the Entity.
     *
     * @return id
     */
    long getId();

    /**
     * Get the label to use for <strong>displaying</strong>.
     * Suitable for (and may contain) HTML output.
     * <p>
     * Convenience method to {@link #getLabel(Context, Details, Style)}.
     *
     * @param context Current context
     *
     * @return formatted string
     */
    @NonNull
    default String getLabel(@NonNull final Context context) {
        return getLabel(context, Details.AutoSelect,
                        ServiceLocator.getInstance()
                                      .getStyles()
                                      .getGlobalStyle());
    }

    /**
     * Get the label to use for <strong>displaying</strong>.
     * Suitable for (and may contain) HTML output.
     *
     * @param context Current context
     * @param details the amount of details wanted
     * @param style   to use
     *
     * @return the label to use.
     */
    @NonNull
    String getLabel(@NonNull Context context,
                    @NonNull Details details,
                    @NonNull Style style);


    /**
     * Get the Locale of the Entity.
     *
     * @param context Current context
     *
     * @return the Entity Locale
     */
    @NonNull
    default Optional<Locale> getLocale(@NonNull final Context context) {
        return Optional.empty();
    }
}
