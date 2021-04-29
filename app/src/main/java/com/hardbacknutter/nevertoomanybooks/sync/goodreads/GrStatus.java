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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.R;

public class GrStatus {

    /** Everything was good. */
    public static final int SUCCESS = 0;
    /** The user cancelled the action. */
    public static final int CANCELLED = 1;

    public static final int SUCCESS_TASK_QUEUED = 2;
    public static final int SUCCESS_AUTHORIZATION_GRANTED = 3;
    public static final int SUCCESS_AUTHORIZATION_ALREADY_GRANTED = 4;
    public static final int SUCCESS_AUTHORIZATION_REQUESTED = 5;


    /** Authorizing this application with Goodreads failed. */
    public static final int FAILED_AUTHORIZATION = 100;
    /** There are no current credentials to use. */
    public static final int CREDENTIALS_MISSING = 101;
    /** The (current) user credentials are not valid. i.e. Authentication failed. */
    public static final int CREDENTIALS_INVALID = 102;


    /** The book has no ISBN! We can only lookup books with an ISBN. */
    public static final int FAILED_BOOK_HAS_NO_ISBN = 200;
    /** A specific action to get a book failed to find it. */
    public static final int FAILED_BOOK_NOT_FOUND_ON_GOODREADS = 201;
    public static final int FAILED_BOOK_NOT_FOUND_LOCALLY = 202;

    public static final int FAILED_IMPORT_TASK_ALREADY_QUEUED = 300;
    public static final int FAILED_EXPORT_TASK_ALREADY_QUEUED = 301;


    /** Not a clue what happened. */
    public static final int FAILED_UNEXPECTED_EXCEPTION = 500;
    /** There is network connectivity but something went wrong. */
    public static final int FAILED_IO_EXCEPTION = 501;
    public static final int FAILED_DISK_FULL_EXCEPTION = 502;
    public static final int FAILED_STORAGE_EXCEPTION = 503;
    /** There simply is no network available to use. */
    public static final int FAILED_NETWORK_UNAVAILABLE = 504;

    @Status
    private final int mMessageId;
    @Nullable
    private final Exception mException;

    public GrStatus(@Status final int messageId) {
        mMessageId = messageId;
        mException = null;
    }

    public GrStatus(@Status final int messageId,
                    @Nullable final Exception e) {
        mMessageId = messageId;
        mException = e;
    }

    @Status
    public int getStatus() {
        return mMessageId;
    }

    @NonNull
    public String getMessage(@NonNull final Context context) {

        if (mException != null) {
            return getMessage(context, mMessageId) + ' ' + mException.getLocalizedMessage();
        } else {
            return getMessage(context, mMessageId);
        }
    }

    @NonNull
    private String getMessage(@NonNull final Context context,
                              @Status final int errorCode) {
        // We could have created an array or map... but we have a couple of special cases,
        // so just leaving this as a switch.
        switch (errorCode) {
            case SUCCESS:
                return context.getString(R.string.lbl_completed);
            case CANCELLED:
                return context.getString(R.string.cancelled);


            case SUCCESS_AUTHORIZATION_ALREADY_GRANTED:
                return context.getString(R.string.gr_authorization_already_granted);

            case SUCCESS_AUTHORIZATION_GRANTED:
                return context.getString(R.string.info_site_authorization_successful,
                                         context.getString(R.string.site_goodreads));


            case FAILED_AUTHORIZATION:
                return context.getString(R.string.error_site_authorization_failed,
                                         context.getString(R.string.site_goodreads));

            case SUCCESS_AUTHORIZATION_REQUESTED:
                //TEST: might need a SUCCESS_AUTHORIZATION_REQUESTED message
                // not sure if this will be seen though
                return context.getString(R.string.lbl_completed);


            case CREDENTIALS_MISSING:
                return context.getString(R.string.info_not_authorized);

            case CREDENTIALS_INVALID:
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

            case FAILED_BOOK_NOT_FOUND_LOCALLY:
                return context.getString(R.string.warning_book_not_found);


            case FAILED_NETWORK_UNAVAILABLE:
                return context.getString(R.string.error_network_please_connect);

            case FAILED_IO_EXCEPTION:
                return context.getString(R.string.error_network_site_access_failed,
                                         context.getString(R.string.site_goodreads));
            case FAILED_DISK_FULL_EXCEPTION:
                return context.getString(R.string.error_storage_no_space_left);

            case FAILED_STORAGE_EXCEPTION:
                return context.getString(R.string.error_storage_not_accessible);


            case FAILED_UNEXPECTED_EXCEPTION:
            default:
                return context.getString(R.string.error_unknown_long,
                                         context.getString(R.string.lbl_send_debug));
        }
    }

    public enum SendBook {
        Success(SUCCESS),
        NoIsbn(FAILED_BOOK_HAS_NO_ISBN),
        NotFound(FAILED_BOOK_NOT_FOUND_ON_GOODREADS);

        @Status
        private final int mStatus;

        SendBook(@Status final int status) {
            mStatus = status;
        }

        public GrStatus getGrStatus() {
            return new GrStatus(mStatus);
        }

        @Status
        public int getStatus() {
            return mStatus;
        }
    }

    @IntDef({SUCCESS,
             CANCELLED,
             SUCCESS_TASK_QUEUED,
             SUCCESS_AUTHORIZATION_GRANTED, SUCCESS_AUTHORIZATION_ALREADY_GRANTED,
             SUCCESS_AUTHORIZATION_REQUESTED,
             FAILED_NETWORK_UNAVAILABLE,
             FAILED_AUTHORIZATION,
             CREDENTIALS_MISSING, CREDENTIALS_INVALID,
             FAILED_BOOK_HAS_NO_ISBN,
             FAILED_BOOK_NOT_FOUND_LOCALLY,
             FAILED_BOOK_NOT_FOUND_ON_GOODREADS,
             FAILED_IMPORT_TASK_ALREADY_QUEUED, FAILED_EXPORT_TASK_ALREADY_QUEUED,

             FAILED_IO_EXCEPTION,
             FAILED_DISK_FULL_EXCEPTION,
             FAILED_STORAGE_EXCEPTION,
             FAILED_UNEXPECTED_EXCEPTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {

    }
}
