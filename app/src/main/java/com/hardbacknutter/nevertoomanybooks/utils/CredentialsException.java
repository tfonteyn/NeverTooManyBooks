/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Thrown when for some reason a website rejects our requests.
 * This could be due to Authentication and/or Authorization (Goodreads OAuth).
 * Maybe this should be split in two classes.
 * <p>
 * Note that the exception message can/will be shown to the end-user.
 */
public class CredentialsException
        extends FormattedMessageException {

    private static final long serialVersionUID = 7568007755552123171L;
    /** Args to pass to format function. */
    @StringRes
    private final int mSite;

    /**
     * Constructor.
     *
     * @param site String resource id with the name of the site.
     */
    public CredentialsException(@StringRes final int site) {
        super(R.string.error_site_authentication_failed);
        mSite = site;
    }

    @NonNull
    public String getLocalizedMessage(@NonNull final Context context) {
        return context.getString(mStringId, context.getString(mSite));
    }
}
