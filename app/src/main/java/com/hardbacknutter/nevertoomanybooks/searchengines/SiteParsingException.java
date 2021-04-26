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
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

/**
 * Used to wrap site specific parsing exceptions.
 * <p>
 * Either the remote end gave us garbage, or the data format changed and we failed to decode it.
 * <p>
 * Dev note: DO NOT make this an IOException (again)!
 */
public class SiteParsingException
        extends Exception
        implements LocalizedException {

    private static final long serialVersionUID = -6026123597563696379L;

    /** The site that caused the issue. */
    @SearchSites.EngineId
    private final int mSiteResId;

    public SiteParsingException(@SearchSites.EngineId final int siteId,
                                @NonNull final Throwable cause) {
        super(cause);
        mSiteResId = siteId;
    }

    public SiteParsingException(@SearchSites.EngineId final int siteId,
                                @NonNull final String message) {
        super(message);
        mSiteResId = siteId;
    }

    @Nullable
    @Override
    public String getMessage() {
        return "SiteResId=" + mSiteResId + ": " + super.getMessage();
    }

    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        //TODO: look at cause and give more details
        return context.getString(R.string.error_network_site_has_problems);
    }

    @Override
    @NonNull
    public String toString() {
        return "SiteParsingException{"
               + "mSiteResId=" + mSiteResId
               + '}';
    }
}
