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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.editstring;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class EditStringInput {
    private static final String TAG = "EditStringInput";

    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";
    private static final String BKEY_EXTRAS = TAG + ":extras";
    private static final String BKEY_INPUT_TYPE = TAG + ":it";
    private static final String BKEY_EDIT = TAG + ":edit";

    @NonNull
    private final String requestKey;
    @Nullable
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;
    private final int inputType;
    @Nullable
    private final String edit;
    @Nullable
    private final Bundle extras;

    EditStringInput(@NonNull final String requestKey,
                    @Nullable final String dialogTitle,
                    @Nullable final String dialogMessage,
                    final int inputType,
                    @Nullable final String edit,
                    @Nullable final Bundle extras) {
        this.requestKey = requestKey;
        this.dialogTitle = dialogTitle;
        this.dialogMessage = dialogMessage;
        this.inputType = inputType;
        this.edit = edit;
        this.extras = extras;
    }

    @NonNull
    static EditStringInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);
        final String dialogTitle = args.getString(BKEY_DIALOG_TITLE, null);
        final String dialogMessage = args.getString(BKEY_DIALOG_MESSAGE, null);

        final int inputType = args.getInt(BKEY_INPUT_TYPE,
                                          InputType.TYPE_CLASS_TEXT);

        final String edit = args.getString(BKEY_EDIT, null);
        final Bundle extras = args.getBundle(BKEY_EXTRAS);

        return new EditStringInput(requestKey, dialogTitle, dialogMessage, inputType, edit, extras);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle();
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        args.putInt(BKEY_INPUT_TYPE, inputType != 0 ? inputType : InputType.TYPE_CLASS_TEXT);

        if (edit != null) {
            args.putString(BKEY_EDIT, edit);
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

    @Nullable
    String getDialogMessage() {
        return dialogMessage;
    }

    int getInputType() {
        return inputType;
    }

    @Nullable
    String getEdit() {
        return edit;
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }
}
