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
package com.hardbacknutter.nevertoomanybooks.utils.exceptions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Authentication.
 */
public class CredentialsException
        extends Exception
        implements LocalizedException {

    private static final long serialVersionUID = -8898712365307463338L;

    /** The site. */
    @StringRes
    private final int siteResId;

    /**
     * Constructor.
     *
     * @param siteResId the site string res; which will be embedded in a default user message
     * @param message   message
     */
    public CredentialsException(@StringRes final int siteResId,
                                @NonNull final String message) {
        super(message);
        this.siteResId = siteResId;
    }

    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        return context.getString(R.string.error_site_authentication_failed,
                                 context.getString(siteResId));
    }
}
