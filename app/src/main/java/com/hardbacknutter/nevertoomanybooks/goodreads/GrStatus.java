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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.R;

public final class GrStatus {

    /** The no-error code. */
    public static final int COMPLETED = 0;

    /** Not a clue what happened. */
    public static final int UNEXPECTED_ERROR = 1;

    public static final int AUTHORIZATION_NEEDED = 2;
    public static final int AUTHORIZATION_ALREADY_GRANTED = 3;
    public static final int AUTHORIZATION_SUCCESSFUL = 4;
    public static final int AUTHORIZATION_FAILED = 5;

    public static final int AUTHENTICATION_FAILED = 6;

    public static final int CREDENTIALS_MISSING = 7;
    public static final int CREDENTIALS_ERROR = 8;

    public static final int TASK_QUEUED_WITH_SUCCESS = 9;
    public static final int IMPORT_TASK_ALREADY_QUEUED = 10;
    public static final int EXPORT_TASK_ALREADY_QUEUED = 11;

    /** The book has no ISBN! We can only lookup books with an ISBN. */
    public static final int NO_ISBN = 12;
    /** A specific action to get a book failed to find it. */
    public static final int BOOK_NOT_FOUND = 13;

    /** A generic action to find 'something' failed. */
    public static final int NOT_FOUND = 14;

    /** The user cancelled the action. */
    public static final int CANCELLED = 15;
    /** There is no connectivity. */
    public static final int NO_INTERNET = 16;
    /** There is connectivity but something went wrong. */
    public static final int IO_ERROR = 17;

    private GrStatus() {
    }

    @NonNull
    public static String getString(@NonNull final Context context,
                                   @Status final int errorCode) {
        // We could have created an array or map... but we have a couple of special cases,
        // so just leaving this as a switch.
        switch (errorCode) {
            case COMPLETED:
                return context.getString(R.string.gr_tq_completed);
            case CANCELLED:
                return context.getString(R.string.progress_end_cancelled);


            case AUTHORIZATION_NEEDED:
                return context.getString(R.string.gr_authorization_needed);
            case AUTHORIZATION_ALREADY_GRANTED:
                return context.getString(R.string.gr_authorization_already_granted);
            case AUTHORIZATION_SUCCESSFUL:
                return context.getString(R.string.info_site_authorization_successful,
                                         context.getString(R.string.site_goodreads));
            case AUTHORIZATION_FAILED:
                return context.getString(R.string.error_site_authorization_failed,
                                         context.getString(R.string.site_goodreads));

            // the internal logic for these is different, but we can use the same
            // message for displaying as they appear to be identical to the user
            case AUTHENTICATION_FAILED:
            case CREDENTIALS_MISSING:
            case CREDENTIALS_ERROR:
                return context.getString(R.string.error_site_authentication_failed,
                                         context.getString(R.string.site_goodreads));

            case TASK_QUEUED_WITH_SUCCESS:
                return context.getString(R.string.gr_tq_task_has_been_queued);
            case IMPORT_TASK_ALREADY_QUEUED:
                return context.getString(R.string.gr_tq_import_task_is_already_queued);
            case EXPORT_TASK_ALREADY_QUEUED:
                return context.getString(R.string.gr_tq_export_task_is_already_queued);


            case NO_ISBN:
                return context.getString(R.string.warning_no_isbn_stored_for_book);

            case BOOK_NOT_FOUND:
                return context.getString(R.string.warning_no_matching_book_found);

            // This could do with a more informative message...
            case NOT_FOUND:
                return context.getString(R.string.gr_tq_failed);


            case NO_INTERNET:
                return context.getString(R.string.error_network_no_connection);

            case IO_ERROR:
                return context.getString(R.string.error_site_access_failed,
                                         context.getString(R.string.site_goodreads));

            case UNEXPECTED_ERROR:
            default:
                return context.getString(R.string.error_unexpected_error);
        }
    }

    @IntDef({COMPLETED, UNEXPECTED_ERROR,
             AUTHORIZATION_NEEDED, AUTHORIZATION_ALREADY_GRANTED,
             AUTHORIZATION_SUCCESSFUL, AUTHORIZATION_FAILED,
             CREDENTIALS_MISSING, CREDENTIALS_ERROR,
             TASK_QUEUED_WITH_SUCCESS, IMPORT_TASK_ALREADY_QUEUED, EXPORT_TASK_ALREADY_QUEUED,
             NO_ISBN, BOOK_NOT_FOUND, NOT_FOUND,
             CANCELLED, NO_INTERNET, IO_ERROR,
             AUTHENTICATION_FAILED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {

    }
}
