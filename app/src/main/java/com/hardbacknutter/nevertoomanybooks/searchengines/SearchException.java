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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

public class SearchException
        extends Exception {

    private static final long serialVersionUID = -907603819317034036L;

    /** The site that caused the issue. */
    @NonNull
    private final EngineId engineId;
    @Nullable
    private final String localizedMessage;

    /**
     * Constructor.
     *
     * @param engineId the SearchEngine which threw the exception
     * @param cause    the exception to wrap
     */
    public SearchException(@NonNull final EngineId engineId,
                           @NonNull final Throwable cause) {
        super(null, cause);
        this.engineId = engineId;
        this.localizedMessage = null;
    }

    /**
     * Constructor.
     *
     * @param engineId         the SearchEngine which threw the exception
     * @param logMessage       (optional) a message intended to be logged and NOT shown to the user
     * @param localizedMessage (optional) a <strong>localized</strong> message which
     *                         <strong>will</strong> be shown to the user
     */
    public SearchException(@NonNull final EngineId engineId,
                           @Nullable final String logMessage,
                           @Nullable final String localizedMessage) {
        super(logMessage);
        this.engineId = engineId;
        this.localizedMessage = localizedMessage;
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
        if (localizedMessage != null) {
            return context.getString(R.string.error_search_x_failed_y,
                                     engineId.getName(context), localizedMessage);
        } else {
            return context.getString(R.string.error_search_x_failed_y,
                                     engineId.getName(context),
                                     ExMsg.map(context, getCause()).orElseGet(
                                             () -> context.getString(R.string.error_unknown)));
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchException{"
               + "engineId=`" + engineId.getPreferenceKey() + '`'
               + ", localizedMessage=" + localizedMessage
               + ", " + super.toString()
               + '}';
    }
}
