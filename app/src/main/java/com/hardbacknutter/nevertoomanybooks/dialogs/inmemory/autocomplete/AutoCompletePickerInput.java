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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.autocomplete;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class AutoCompletePickerInput {

    private static final String TAG = "AutoCompletePickerInput";

    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";
    private static final String BKEY_EDIT = TAG + ":edit";
    private static final String BKEY_EXTRAS = TAG + ":extras";
    /** The list of strings to display in the dropdown. */
    private static final String BKEY_ITEM_LIST_TEXT = TAG + ":items-text";

    @NonNull
    private final String requestKey;
    @Nullable
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;
    @NonNull
    private final List<String> allItems;
    @Nullable
    private final String currentSelection;
    @Nullable
    private final Bundle extras;

    AutoCompletePickerInput(@NonNull final String requestKey,
                            @Nullable final String dialogTitle,
                            @Nullable final String dialogMessage,
                            @NonNull final List<String> allItems,
                            @Nullable final String currentSelection,
                            @Nullable final Bundle extras) {
        this.requestKey = requestKey;
        this.dialogTitle = dialogTitle;
        this.dialogMessage = dialogMessage;
        this.allItems = allItems;
        this.currentSelection = currentSelection;
        this.extras = extras;
    }

    @NonNull
    static AutoCompletePickerInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);
        final String dialogTitle = args.getString(BKEY_DIALOG_TITLE, null);
        final String dialogMessage = args.getString(BKEY_DIALOG_MESSAGE, null);

        final List<String> items = Arrays
                .stream(Objects.requireNonNull(args.getStringArray(BKEY_ITEM_LIST_TEXT),
                                               BKEY_ITEM_LIST_TEXT))
                .collect(Collectors.toList());

        final String currentSelection = args.getString(BKEY_EDIT, null);

        final Bundle extras = args.getBundle(BKEY_EXTRAS);

        return new AutoCompletePickerInput(requestKey, dialogTitle, dialogMessage,
                                           items, currentSelection, extras);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle();

        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        // pass in the texts; there are no ids
        args.putStringArray(BKEY_ITEM_LIST_TEXT, allItems.toArray(String[]::new));

        if (currentSelection != null) {
            args.putString(BKEY_EDIT, currentSelection);
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

    @NonNull
    List<String> getAllItems() {
        return allItems;
    }

    @Nullable
    String getCurrentSelection() {
        return currentSelection;
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }
}
