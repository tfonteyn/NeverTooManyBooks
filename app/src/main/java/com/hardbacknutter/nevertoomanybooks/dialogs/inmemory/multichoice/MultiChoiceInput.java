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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.multichoice;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

class MultiChoiceInput {
    private static final String TAG = "MultiChoiceInput";

    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";
    private static final String BKEY_EXTRAS = TAG + ":extras";
    /** The list of strings to display in the dropdown. */
    private static final String BKEY_LIST_LABELS = TAG + ":items-text";
    /** The ids for the list of strings to display in the dropdown. */
    private static final String BKEY_LIST_IDS = TAG + ":items-id";
    private static final String BKEY_SELECTED_IDS = TAG + ":edit";

    @NonNull
    private final String requestKey;
    @Nullable
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;
    @NonNull
    private final String[] labels;
    @NonNull
    private final long[] ids;
    @Nullable
    private final long[] selectedIds;
    @Nullable
    private final Bundle extras;

    private MultiChoiceInput(@NonNull final String requestKey,
                             @Nullable final String dialogTitle,
                             @Nullable final String dialogMessage,
                             @NonNull final String[] labels,
                             @NonNull final long[] ids,
                             @Nullable final long[] selectedIds,
                             @Nullable final Bundle extras) {
        this.requestKey = requestKey;
        this.dialogTitle = dialogTitle;
        this.dialogMessage = dialogMessage;
        this.labels = labels;
        this.ids = ids;
        this.selectedIds = selectedIds;
        this.extras = extras;
    }

    /**
     * Constructor.
     *
     * @param context Current context; used to extract labels from the list of Entities.
     * @param <T>     Entity
     */
    @SuppressWarnings("CheckStyle")
    <T extends Entity> MultiChoiceInput(@NonNull final Context context,
                                        @NonNull final String requestKey,
                                        @Nullable final String dialogTitle,
                                        @Nullable final String dialogMessage,
                                        @NonNull final List<T> allItems,
                                        @Nullable final List<T> selectedIds,
                                        @Nullable final Bundle extras) {
        this.requestKey = requestKey;
        this.dialogTitle = dialogTitle;
        this.dialogMessage = dialogMessage;
        this.extras = extras;

        this.labels = allItems.stream()
                              .map(item -> item.getLabel(context))
                              .toArray(String[]::new);

        this.ids = allItems.stream()
                           .mapToLong(Entity::getId)
                           .toArray();

        if (selectedIds != null) {
            this.selectedIds = selectedIds.stream().mapToLong(Entity::getId).toArray();
        } else {
            this.selectedIds = null;
        }
    }

    @NonNull
    static MultiChoiceInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);
        final String dialogTitle = args.getString(MultiChoiceInput.BKEY_DIALOG_TITLE);
        final String dialogMessage = args.getString(MultiChoiceInput.BKEY_DIALOG_MESSAGE);

        final String[] labels = Objects.requireNonNull(
                args.getStringArray(MultiChoiceInput.BKEY_LIST_LABELS),
                MultiChoiceInput.BKEY_LIST_LABELS);

        final long[] ids = Objects.requireNonNull(
                args.getLongArray(MultiChoiceInput.BKEY_LIST_IDS),
                MultiChoiceInput.BKEY_LIST_IDS);

        @Nullable
        final long[] selectedIds = args.getLongArray(MultiChoiceInput.BKEY_SELECTED_IDS);

        final Bundle extras = args.getBundle(MultiChoiceInput.BKEY_EXTRAS);


        return new MultiChoiceInput(requestKey, dialogTitle, dialogMessage,
                                    labels, ids, selectedIds, extras);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle();
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(MultiChoiceInput.BKEY_DIALOG_TITLE, dialogTitle);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            args.putString(MultiChoiceInput.BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        args.putLongArray(MultiChoiceInput.BKEY_LIST_IDS, ids);
        args.putStringArray(MultiChoiceInput.BKEY_LIST_LABELS, labels);

        if (selectedIds != null) {
            args.putLongArray(MultiChoiceInput.BKEY_SELECTED_IDS, selectedIds);
        }

        if (extras != null && !extras.isEmpty()) {
            args.putBundle(MultiChoiceInput.BKEY_EXTRAS, extras);
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
    List<String> getLabels() {
        return List.of(labels);
    }

    @NonNull
    List<Long> getIds() {
        return Arrays.stream(ids).boxed().collect(Collectors.toList());
    }

    @NonNull
    Set<Long> getSelectedIds() {
        if (selectedIds == null) {
            return Set.of();
        }
        return Arrays.stream(selectedIds).boxed().collect(Collectors.toSet());
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }

    @Override
    @NonNull
    public String toString() {
        return "MultiChoiceInput{"
               + "requestKey='" + requestKey + '\''
               + ", dialogTitle='" + dialogTitle + '\''
               + ", dialogMessage='" + dialogMessage + '\''
               + ", labels=" + Arrays.toString(labels)
               + ", ids=" + Arrays.toString(ids)
               + ", selectedIds=" + Arrays.toString(selectedIds)
               + ", extras=" + extras
               + '}';
    }
}
