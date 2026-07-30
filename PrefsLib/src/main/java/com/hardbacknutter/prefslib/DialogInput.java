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

package com.hardbacknutter.prefslib;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class DialogInput {
    private static final String TAG = "DialogInput";
    private static final String BKEY_KEY = TAG + ":k";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":m";

    @NonNull
    private final String key;
    @Nullable
    private final String dialogMessage;

    public DialogInput(@NonNull final String key,
                       @Nullable final String dialogMessage) {
        this.key = key;
        this.dialogMessage = dialogMessage;
    }

    @NonNull
    public static DialogInput fromBundle(@NonNull final Bundle args) {
        final String key = Objects.requireNonNull(args.getString(BKEY_KEY), BKEY_KEY);
        final String dialogMessage = args.getString(BKEY_DIALOG_MESSAGE);

        return new DialogInput(key, dialogMessage);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putString(DialogInput.BKEY_KEY, key);
        if (dialogMessage != null) {
            args.putString(DialogInput.BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        return args;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @Nullable
    public String getDialogMessage() {
        return dialogMessage;
    }
}
