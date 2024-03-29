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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * Thrown during an import of data.
 */
public class DataReaderException
        extends Exception {

    private static final long serialVersionUID = -1993624111906098070L;

    /**
     * Constructor.
     *
     * @param localizedMessage a <strong>localized</strong> message which
     *                         <strong>will</strong> be shown to the user
     */
    public DataReaderException(@NonNull final String localizedMessage) {
        super(localizedMessage);
    }

    /**
     * Constructor.
     * <p>
     * When reporting to the user, the 'orElse' message from {@link #getUserMessage(Context)}
     * will be shown.
     *
     * @param cause Exception to wrap
     */
    public DataReaderException(@NonNull final Throwable cause) {
        // Pass null, so getUserMessage works as expected!
        super(null, cause);
    }

    /**
     * Constructor.
     *
     * @param localizedMessage a <strong>localized</strong> message which
     *                         <strong>will</strong> be shown to the user
     * @param cause            Exception to wrap
     */
    public DataReaderException(@NonNull final String localizedMessage,
                               @NonNull final Throwable cause) {
        super(localizedMessage, cause);
    }

    /**
     * Get (and create if needed) the localized message to show to the user.
     *
     * @param context Current context
     *
     * @return message
     */
    @NonNull
    public String getUserMessage(@NonNull final Context context) {
        // if a custom message was added, use that.
        if (getMessage() != null) {
            return getMessage();
        }

        return ExMsg.map(context, getCause())
                    .orElseGet(() -> context.getString(R.string.error_import_file_not_supported));
    }
}
