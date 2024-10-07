/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogSelectMultipleSimpleBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.ChecklistRecyclerAdapter;

/**
 * This is a wrapper for a {@link MaterialAlertDialogBuilder}
 * with a suitable RecyclerView/Adapter and builtin listener.
 * Items are handled as {@code List} and {@code Set}s of {@code Number} values
 * (usually and id of some sort) and matching labels.
 * <p>
 * It will <strong>NOT</strong> survive device rotations.
 * <p>
 * For rotation-safe behaviour, use {@link MultiChoiceLauncher} and related classes.
 *
 * @param <T> type of id based on {@code Number}
 */
public class MultiChoiceAlertDialogBuilder<T extends Number> {

    @NonNull
    private final LayoutInflater layoutInflater;
    @NonNull
    private final Context context;
    @NonNull
    private final Set<T> selectedItems = new HashSet<>();
    @DrawableRes
    private int iconId;
    @Nullable
    private CharSequence dialogTitle;
    @Nullable
    private CharSequence dialogMessage;
    @Nullable
    private List<T> items;
    @Nullable
    private List<String> itemLabels;
    @StringRes
    private int positiveButtonTextId;
    @Nullable
    private Consumer<Set<T>> positiveButtonConsumer;
    @StringRes
    private int neutralButtonTextId;
    @Nullable
    private Consumer<Set<T>> neutralButtonConsumer;

    @Nullable
    private Runnable onDismiss;

    /**
     * Constructor - standalone usage.
     *
     * @param context Current context
     */
    public MultiChoiceAlertDialogBuilder(@NonNull final Context context) {
        this.context = context;
        this.layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setIcon(@DrawableRes final int iconId) {
        this.iconId = iconId;
        return this;
    }

    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setTitle(@StringRes final int titleId) {
        this.dialogTitle = context.getString(titleId);
        return this;
    }

    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setTitle(@Nullable final CharSequence title) {
        this.dialogTitle = title;
        return this;
    }

    /**
     * Optional. Set a message shown below the title, and above the list of items.
     *
     * @param messageId to show
     *
     * @return {@code this} (for chaining)
     *
     * @see #setMessage(CharSequence)
     */
    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setMessage(@StringRes final int messageId) {
        this.dialogMessage = context.getString(messageId);
        return this;
    }

    /**
     * Optional. Set a message shown below the title, and above the list of items.
     *
     * @param message to show
     *
     * @return {@code this} (for chaining)
     *
     * @see #setMessage(int)
     */
    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setMessage(@Nullable final CharSequence message) {
        this.dialogMessage = message;
        return this;
    }

    /**
     * Required. Set the list of selectable items. Both {@code List}s must be the same length.
     *
     * @param items      list of id values
     * @param itemLabels matching labels for the id's
     *
     * @return {@code this} (for chaining)
     *
     * @throws IllegalArgumentException if the lists have a different size
     */
    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setItems(@NonNull final List<T> items,
                                                     @NonNull final List<String> itemLabels) {
        if (items.size() != itemLabels.size()) {
            throw new IllegalArgumentException("Lists must be the same size");
        }
        this.items = items;
        this.itemLabels = itemLabels;
        return this;
    }

    /**
     * Optional. Set the set of pre-selected items.
     *
     * @param selectedItems set of id values
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setSelectedItems(@Nullable final Set<T> selectedItems) {
        this.selectedItems.clear();
        if (selectedItems != null) {
            this.selectedItems.addAll(selectedItems);
        }
        return this;
    }

    /**
     * Required. Set the text and action listener for the positive button.
     *
     * @param textId         for the button
     * @param resultConsumer to receive the set of selected items.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setPositiveButton(
            @StringRes final int textId,
            @NonNull final Consumer<Set<T>> resultConsumer) {
        positiveButtonTextId = textId;
        positiveButtonConsumer = resultConsumer;
        return this;
    }

    /**
     * Optional. Set the text and action listener for the neutral button.
     *
     * @param textId         for the button
     * @param resultConsumer to receive the set of selected items.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setNeutralButton(
            @StringRes final int textId,
            @NonNull final Consumer<Set<T>> resultConsumer) {
        neutralButtonTextId = textId;
        neutralButtonConsumer = resultConsumer;
        return this;
    }

    @NonNull
    public MultiChoiceAlertDialogBuilder<T> setOnDismiss(@Nullable final Runnable onDismiss) {
        this.onDismiss = onDismiss;
        return this;
    }

    /**
     * Create (but does not show) the dialog.
     *
     * @return the dialog ready to be shown.
     */
    @NonNull
    public AlertDialog build() {
        Objects.requireNonNull(items);
        Objects.requireNonNull(itemLabels);
        Objects.requireNonNull(positiveButtonConsumer);

        final DialogSelectMultipleSimpleBinding vb = DialogSelectMultipleSimpleBinding.inflate(
                layoutInflater, null, false);
        // Ensure the drag handle is hidden.
        vb.dragHandle.setVisibility(View.GONE);
        // Ensure the unused title field is hidden
        vb.title.setVisibility(View.GONE);

        if (dialogMessage != null && dialogMessage.length() > 0) {
            vb.message.setText(dialogMessage);
            vb.message.setVisibility(View.VISIBLE);
        } else {
            vb.message.setVisibility(View.GONE);
        }

        final ChecklistRecyclerAdapter<T> adapter = new ChecklistRecyclerAdapter<>(
                context, items, position -> itemLabels.get(position), selectedItems,
                (id, checked) -> {
                    if (checked) {
                        selectedItems.add(id);
                    } else {
                        selectedItems.remove(id);
                    }
                });
        vb.itemList.setAdapter(adapter);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(vb.getRoot())
                .setTitle(dialogTitle)
                .setIcon(iconId)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    if (onDismiss != null) {
                        onDismiss.run();
                    } else {
                        d.dismiss();
                    }
                });

        if (neutralButtonConsumer != null) {
            builder.setNeutralButton(neutralButtonTextId, (d, which) ->
                    neutralButtonConsumer.accept(selectedItems));
        }

        return builder.setPositiveButton(positiveButtonTextId, (d, which) ->
                              positiveButtonConsumer.accept(selectedItems))
                      .create();
    }
}
