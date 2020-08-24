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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.entities.Author;

public class AuthorFormatter
        extends HtmlFormatter<Author> {

    /** how much details to show. */
    private final Author.Details mDetails;

    /**
     * Constructor.
     *
     * @param details     how much details to show
     * @param enableLinks {@code true} to enable links.
     *                    Ignored if the View has an onClickListener
     */
    public AuthorFormatter(@NonNull final Author.Details details,
                           final boolean enableLinks) {
        super(enableLinks);
        mDetails = details;
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final Author author) {
        if (author == null) {
            return "";
        }

        switch (mDetails) {
            case Full:
                return author.getExtLabel(context);

            case Normal:
                return author.getLabel(context);

            case Short:
                return author.getGivenNames().substring(0, 1) + ' ' + author.getFamilyName();
        }
        return "";
    }
}
