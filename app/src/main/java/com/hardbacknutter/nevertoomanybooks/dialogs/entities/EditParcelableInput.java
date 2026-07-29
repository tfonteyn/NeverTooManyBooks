/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class EditParcelableInput<T extends Parcelable> {

    private static final String TAG = "EditParcelableInput";
    /** Input value: the item we're going to edit. */
    private static final String BKEY_ITEM = TAG + ":item";
    /**
     * Input value: the issn-8 code from a book.
     */
    private static final String BKEY_BOOK_ISSN = TAG + ":issn";

    @NonNull
    private final String requestKey;
    @NonNull
    private final EditAction action;
    @NonNull
    private final T item;
    @Nullable
    private final String bookIssn;

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public EditParcelableInput(@NonNull final String requestKey,
                        @NonNull final EditAction action,
                        @NonNull final T item,
                        @Nullable final String bookIssn) {
        this.requestKey = requestKey;
        this.action = action;
        this.item = item;
        this.bookIssn = bookIssn;
    }

    @SuppressWarnings("deprecation")
    @NonNull
    public static <T extends Parcelable> EditParcelableInput<T>
    fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);

        final EditAction action = Objects.requireNonNull(
                args.getParcelable(EditAction.BKEY), EditAction.BKEY);
        final T item = Objects.requireNonNull(args.getParcelable(BKEY_ITEM), BKEY_ITEM);

        final String bookIssn = args.getString(BKEY_BOOK_ISSN, null);

        return new EditParcelableInput<>(requestKey, action, item, bookIssn);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(4);
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(EditAction.BKEY, action);
        args.putParcelable(BKEY_ITEM, item);
        if (bookIssn != null) {
            args.putString(BKEY_BOOK_ISSN, bookIssn);
        }

        return args;
    }

    @NonNull
    public String getRequestKey() {
        return requestKey;
    }

    @NonNull
    public EditAction getAction() {
        return action;
    }

    @NonNull
    public T getItem() {
        return item;
    }

    @Nullable
    public String getBookIssn() {
        return bookIssn;
    }
}
