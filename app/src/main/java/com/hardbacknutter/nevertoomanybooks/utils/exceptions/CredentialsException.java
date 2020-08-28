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
package com.hardbacknutter.nevertoomanybooks.utils.exceptions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * Thrown when for some reason a website rejects our requests.
 * This could be due to Authentication and/or Authorization (Goodreads OAuth).
 * Maybe this should be split in two classes.
 */
public class CredentialsException
        extends Exception {

    private static final long serialVersionUID = 4436573044442202621L;
    /** The site that caused the issue. */
    @NonNull
    private final String mSite;

    /**
     * Constructor.
     *
     * @param site name
     */
    public CredentialsException(@NonNull final String site) {
        super(site);
        mSite = site;
    }

    @Nullable
    @Override
    public String getLocalizedMessage() {
        final Context context = AppLocale.getInstance().apply(App.getAppContext());
        return context.getString(R.string.error_site_authentication_failed, mSite);
    }
}
