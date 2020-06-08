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

    /** Everything was good. */
    public static final int SUCCESS = 0;
    /** The user cancelled the action. */
    public static final int CANCELLED = 1;

    public static final int SUCCESS_TASK_QUEUED = 2;
    public static final int SUCCESS_AUTHORIZATION_GRANTED = 3;
    public static final int SUCCESS_AUTHORIZATION_ALREADY_GRANTED = 4;

    /** There simply is no network available to use. */
    public static final int FAILED_NETWORK_UNAVAILABLE = 100;

    /** Authorizing this application with Goodreads failed. */
    public static final int FAILED_AUTHORIZATION = 101;
    /** The (current) user credentials are not valid. i.e. Authentication failed. */
    public static final int FAILED_CREDENTIALS = 102;

    /** The book has no ISBN! We can only lookup books with an ISBN. */
    public static final int FAILED_BOOK_HAS_NO_ISBN = 103;
    /** A specific action to get a book failed to find it. */
    public static final int FAILED_BOOK_NOT_FOUND_ON_GOODREADS = 104;

    public static final int FAILED_IMPORT_TASK_ALREADY_QUEUED = 105;
    public static final int FAILED_EXPORT_TASK_ALREADY_QUEUED = 106;


    /** Not a clue what happened. */
    public static final int FAILED_UNEXPECTED_EXCEPTION = 200;
    /** There is network connectivity but something went wrong. */
    public static final int FAILED_IO_EXCEPTION = 201;


    private GrStatus() {
    }

    @NonNull
    public static String getString(@NonNull final Context context,
                                   @Status final int errorCode) {
        // We could have created an array or map... but we have a couple of special cases,
        // so just leaving this as a switch.
        switch (errorCode) {
            case SUCCESS:
                return context.getString(R.string.gr_tq_completed);
            case CANCELLED:
                return context.getString(R.string.progress_end_cancelled);

            case SUCCESS_AUTHORIZATION_ALREADY_GRANTED:
                return context.getString(R.string.gr_authorization_already_granted);
            case SUCCESS_AUTHORIZATION_GRANTED:
                return context.getString(R.string.info_site_authorization_successful,
                                         context.getString(R.string.site_goodreads));
            case FAILED_AUTHORIZATION:
                return context.getString(R.string.error_site_authorization_failed,
                                         context.getString(R.string.site_goodreads));

            case FAILED_CREDENTIALS:
                return context.getString(R.string.error_site_authentication_failed,
                                         context.getString(R.string.site_goodreads));

            case SUCCESS_TASK_QUEUED:
                return context.getString(R.string.gr_tq_task_has_been_queued);
            case FAILED_IMPORT_TASK_ALREADY_QUEUED:
                return context.getString(R.string.gr_tq_import_task_is_already_queued);
            case FAILED_EXPORT_TASK_ALREADY_QUEUED:
                return context.getString(R.string.gr_tq_export_task_is_already_queued);


            case FAILED_BOOK_HAS_NO_ISBN:
                return context.getString(R.string.warning_no_isbn_stored_for_book);
            case FAILED_BOOK_NOT_FOUND_ON_GOODREADS:
                return context.getString(R.string.warning_no_matching_book_found);

            case FAILED_NETWORK_UNAVAILABLE:
                return context.getString(R.string.error_network_no_connection);
            case FAILED_IO_EXCEPTION:
                return context.getString(R.string.error_site_access_failed,
                                         context.getString(R.string.site_goodreads));
            case FAILED_UNEXPECTED_EXCEPTION:
            default:
                return context.getString(R.string.error_unexpected_error);
        }
    }

    @IntDef({SUCCESS, CANCELLED,
             SUCCESS_TASK_QUEUED,
             SUCCESS_AUTHORIZATION_GRANTED, SUCCESS_AUTHORIZATION_ALREADY_GRANTED,
             FAILED_NETWORK_UNAVAILABLE,
             FAILED_AUTHORIZATION, FAILED_CREDENTIALS,
             FAILED_BOOK_HAS_NO_ISBN, FAILED_BOOK_NOT_FOUND_ON_GOODREADS,
             FAILED_IMPORT_TASK_ALREADY_QUEUED, FAILED_EXPORT_TASK_ALREADY_QUEUED,
             FAILED_UNEXPECTED_EXCEPTION, FAILED_IO_EXCEPTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {

    }
}
