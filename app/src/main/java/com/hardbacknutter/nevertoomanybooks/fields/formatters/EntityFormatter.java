/*
 * @Copyright 2018-2022 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * A formatter which uses {@link Entity#getLabel(Context, Details, Style)}
 * to display a single {@link Entity}.
 *
 * <ul>
 *      <li>Multiple fields: <strong>no</strong></li>
 * </ul>
 *
 * @param <T> type of Entity (== Field) value.
 */
public class EntityFormatter<T extends Entity>
        extends HtmlFormatter<T> {

    @NonNull
    private final Details details;
    @Nullable
    private final Style style;

    /**
     * Constructor.
     *
     * @param details how much {@link Entity} details to show. May be ignored by implementations.
     * @param style   to use
     */
    public EntityFormatter(@NonNull final Details details,
                           @Nullable final Style style) {
        this.details = details;
        this.style = style;
    }

    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final T entity) {
        return entity == null ? "" : entity.getLabel(context, details, style);
    }
}
