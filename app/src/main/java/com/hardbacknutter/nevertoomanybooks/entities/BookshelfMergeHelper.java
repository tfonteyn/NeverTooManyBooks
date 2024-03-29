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

public class BookshelfMergeHelper
        extends EntityMergeHelper<Bookshelf> {

    @Override
    protected boolean merge(@NonNull final Context context,
                            @NonNull final Bookshelf previous,
                            @NonNull final Locale previousLocale,
                            @NonNull final Bookshelf current,
                            @NonNull final Locale currentLocale) {

        if (current.getId() > 0) {
            previous.setId(current.getId());
        }

        // no other attributes, so we can always merge
        return true;
    }
}
