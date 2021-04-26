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
package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

/**
 * Should be thrown if the device has no network connectivity at all for whatever reason.
 */
public class NetworkUnavailableException
        extends IOException
        implements LocalizedException {


    private static final long serialVersionUID = 5622402043205751302L;

    NetworkUnavailableException() {
    }

    NetworkUnavailableException(@Nullable final Throwable cause) {
        super(cause);
    }

    public NetworkUnavailableException(@Nullable final String message) {
        super(message);
    }

    public NetworkUnavailableException(@Nullable final String message,
                                       @Nullable final Throwable cause) {
        super(message, cause);
    }

    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        return context.getString(R.string.error_network_please_connect);
    }
}
