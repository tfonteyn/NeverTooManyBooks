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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

public class SearchException
        extends Exception
        implements LocalizedException {

    private static final long serialVersionUID = -6801167882310671147L;

    /** The site that caused the issue. */
    @NonNull
    private final String siteName;

    public SearchException(@NonNull final String siteName,
                           @NonNull final Throwable cause) {
        super(cause);
        this.siteName = siteName;
    }

    public SearchException(@NonNull final String siteName,
                           @NonNull final String message) {
        super(message);
        this.siteName = siteName;
    }

    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        return context.getString(R.string.error_search_x_failed_y, siteName,
                                 ExMsg.map(context, getCause())
                                      .orElse(context.getString(R.string.error_unknown)));
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchException{"
               + "siteName='" + siteName + '\''
               + ", " + super.toString()
               + '}';
    }
}
