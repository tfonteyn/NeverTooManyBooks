/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;

public enum GrStatus {
    /** The no-error code. */
    Completed,

    AuthorizationNeeded,
    AuthorizationAlreadyGranted,
    AuthorizationSuccessful,
    AuthorizationFailed,

    CredentialsMissing,
    CredentialsError,

    TaskQueuedWithSuccess,
    ImportTaskAlreadyQueued,
    ExportTaskAlreadyQueued,

    /** The book has no ISBN! We can only lookup books with an ISBN. */
    NoIsbn,
    /** A specific action to get a book failed to find it. */
    BookNotFound,

    /** A generic action to find 'something' failed. */
    NotFound,

    /** The user cancelled the action. */
    Cancelled,
    /** There is no connectivity. */
    NoInternet,
    /** There is connectivity but something went wrong. */
    IOError,
    /** Not a clue. */
    UnexpectedError;

    @NonNull
    public String getString(@NonNull final Context context) {
        switch (this) {
            case Completed:
                return context.getString(R.string.gr_tq_completed);


            case AuthorizationNeeded:
                return context.getString(R.string.gr_authorization_needed);
            case AuthorizationAlreadyGranted:
                return context.getString(R.string.gr_authorization_already_granted);
            case AuthorizationSuccessful:
                return context.getString(R.string.info_authorization_successful,
                                         context.getString(R.string.site_goodreads));
            case AuthorizationFailed:
                return context.getString(R.string.error_site_authorization_failed,
                                         context.getString(R.string.site_goodreads));

            // the internal logic for CredentialsMissing and CredentialsError
            // is different, but we can use the same message for displaying
            // as the two appear identical to the user
            case CredentialsMissing:
            case CredentialsError:
                return context.getString(R.string.error_site_authentication_failed,
                                         context.getString(R.string.site_goodreads));

            case TaskQueuedWithSuccess:
                return context.getString(R.string.gr_tq_task_has_been_queued);

            case ImportTaskAlreadyQueued:
                return context.getString(R.string.gr_tq_import_task_is_already_queued);

            case ExportTaskAlreadyQueued:
                return context.getString(R.string.gr_tq_export_task_is_already_queued);


            case NoIsbn:
                return context.getString(R.string.warning_no_isbn_stored_for_book);

            case BookNotFound:
                return context.getString(R.string.warning_no_matching_book_found);

            case Cancelled:
                return context.getString(R.string.progress_end_cancelled);

            case NoInternet:
                return context.getString(R.string.error_network_no_connection);

            case IOError:
                return context.getString(R.string.error_site_access_failed,
                                         context.getString(R.string.site_goodreads));

            // This could do with a more informative message...
            case NotFound:
                return context.getString(R.string.gr_tq_failed);

            case UnexpectedError:
            default:
                return context.getString(R.string.error_unexpected_error);
        }
    }
}
