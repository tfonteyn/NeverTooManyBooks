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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class PartialDatePickerInput {

    private static final String TAG = "PartialDatePickerInput";

    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";
    private static final String BKEY_EXTRAS = TAG + ":extras";
    /** A standard SQL style (partial) date string, must/will be valid. */
    private static final String BKEY_EDIT = TAG + ":edit";

    @NonNull
    private final String requestKey;
    @Nullable
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;
    @Nullable
    private final String selectedDate;
    @Nullable
    private final Bundle extras;

    PartialDatePickerInput(@NonNull final String requestKey,
                           @Nullable final String dialogTitle,
                           @Nullable final String dialogMessage,
                           @Nullable final String selectedDate,
                           @Nullable final Bundle extras) {
        this.requestKey = requestKey;
        this.dialogTitle = dialogTitle;
        this.dialogMessage = dialogMessage;
        this.selectedDate = selectedDate;
        this.extras = extras;
    }

    @NonNull
    static PartialDatePickerInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);
        final String dialogTitle = args.getString(BKEY_DIALOG_TITLE);
        final String dialogMessage = args.getString(BKEY_DIALOG_MESSAGE);
        final String date = args.getString(BKEY_EDIT);
        final Bundle extras = args.getBundle(BKEY_EXTRAS);

        return new PartialDatePickerInput(requestKey, dialogTitle, dialogMessage, date, extras);

    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle();
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        if (selectedDate != null) {
            args.putString(BKEY_EDIT, selectedDate);
        }

        if (extras != null && !extras.isEmpty()) {
            args.putBundle(BKEY_EXTRAS, extras);
        }

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @NonNull
    String getDialogTitle(@NonNull final Context context) {
        if (dialogTitle == null) {
            return context.getString(R.string.action_edit);
        }
        return dialogTitle;
    }

    @NonNull
    String getDialogMessage(@NonNull final Context context) {
        if (dialogMessage == null) {
            // the default help text
            return context.getString(R.string.info_partial_date_picker);
        }
        return dialogMessage;
    }

    /**
     * The selected date, or {@link PartialDate#NOT_SET}.
     *
     * @return date
     */
    @NonNull
    PartialDate getSelectedDate() {
        final DateParser<PartialDate> partialDateParser = new PartialDateParser();
        return partialDateParser.parse(selectedDate).orElse(PartialDate.NOT_SET);
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }
}
