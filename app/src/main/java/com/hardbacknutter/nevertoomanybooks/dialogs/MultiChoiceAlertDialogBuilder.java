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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.widgets.ChecklistRecyclerAdapter;

public class MultiChoiceAlertDialogBuilder<T> {

    @NonNull
    private final LayoutInflater layoutInflater;

    @Nullable
    private CharSequence dialogTitle;
    @Nullable
    private CharSequence dialogMessage;

    @Nullable
    private List<T> itemIds;
    @Nullable
    private List<String> itemLabels;
    @Nullable
    private Set<T> selectedItems;

    @Nullable
    private Consumer<Set<T>> resultConsumer;
    @Nullable
    private Runnable onDismiss;

    public MultiChoiceAlertDialogBuilder(@NonNull final LayoutInflater layoutInflater) {
        this.layoutInflater = layoutInflater;
    }

    public MultiChoiceAlertDialogBuilder<T> setDialogTitle(
            @Nullable final CharSequence dialogTitle) {
        this.dialogTitle = dialogTitle;
        return this;
    }

    public MultiChoiceAlertDialogBuilder<T> setDialogMessage(
            @Nullable final CharSequence dialogMessage) {
        this.dialogMessage = dialogMessage;
        return this;
    }

    public MultiChoiceAlertDialogBuilder<T> setItems(@NonNull final List<T> itemIds,
                                                     @NonNull final List<String> itemLabels) {
        this.itemIds = itemIds;
        this.itemLabels = itemLabels;
        return this;
    }

    public MultiChoiceAlertDialogBuilder<T> setSelectedItems(@Nullable final Set<T> selectedItems) {
        this.selectedItems = selectedItems;
        return this;
    }

    public MultiChoiceAlertDialogBuilder<T> setResultConsumer(
            @NonNull final Consumer<Set<T>> resultConsumer) {
        this.resultConsumer = resultConsumer;
        return this;
    }

    public MultiChoiceAlertDialogBuilder<T> setOnDismiss(@Nullable final Runnable onDismiss) {
        this.onDismiss = onDismiss;
        return this;
    }

    public AlertDialog create() {
        Objects.requireNonNull(itemIds);
        Objects.requireNonNull(itemLabels);
        Objects.requireNonNull(resultConsumer);
        if (selectedItems == null) {
            selectedItems = new HashSet<>();
        }

        final View view = layoutInflater.inflate(R.layout.dialog_edit_checklist, null);
        final TextView messageView = view.findViewById(R.id.message);
        if (dialogMessage != null && dialogMessage.length() > 0) {
            messageView.setText(dialogMessage);
            messageView.setVisibility(View.VISIBLE);
        } else {
            messageView.setVisibility(View.GONE);
        }

        final Context context = layoutInflater.getContext();
        final ChecklistRecyclerAdapter<T, String> adapter =
                new ChecklistRecyclerAdapter<>(context, itemIds, itemLabels, selectedItems,
                                               (id, checked) -> {
                                                   if (checked) {
                                                       selectedItems.add(id);
                                                   } else {
                                                       selectedItems.remove(id);
                                                   }
                                               });

        final RecyclerView listView = view.findViewById(R.id.item_list);
        listView.setAdapter(adapter);

        return new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setTitle(dialogTitle)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    if (onDismiss != null) {
                        onDismiss.run();
                    } else {
                        d.dismiss();
                    }
                })
                .setPositiveButton(android.R.string.ok, (d, which) ->
                        resultConsumer.accept(selectedItems))
                .create();
    }
}
