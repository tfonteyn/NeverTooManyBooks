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
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * Authentication.
 */
public class CredentialsException
        extends Exception {

    private static final long serialVersionUID = -8898712365307463338L;

    /** The site. */
    @StringRes
    private final int siteResId;

    /**
     * Constructor.
     *
     * @param siteResId the site string res; which will be embedded in a default user message
     * @param message   internal message for the log file
     */
    public CredentialsException(@StringRes final int siteResId,
                                @NonNull final String message) {
        super(message);
        this.siteResId = siteResId;
    }

    @StringRes
    public int getSiteResId() {
        return siteResId;
    }
}
