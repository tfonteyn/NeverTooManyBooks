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
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogSelectOneBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

/**
 * Replacement for an AlertDialog with radio button setup.
 */
class SingleChoiceDelegate {

    @NonNull
    private final DialogFragment owner;

    private final SingleChoiceViewModel vm;

    @NonNull
    private final String requestKey;
    @NonNull
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;

    /** The list of items to display. */
    @NonNull
    private final List<Long> itemIds;
    @NonNull
    private final List<String> itemLabels;

    SingleChoiceDelegate(@NonNull final DialogFragment owner,
                         @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        dialogTitle = args.getString(SingleChoiceLauncher.BKEY_DIALOG_TITLE,
                                     owner.getString(R.string.action_edit));
        dialogMessage = args.getString(SingleChoiceLauncher.BKEY_DIALOG_MESSAGE, null);

        itemIds = Arrays.stream(Objects.requireNonNull(
                                args.getLongArray(SingleChoiceLauncher.BKEY_ALL_IDS),
                                SingleChoiceLauncher.BKEY_ALL_IDS))
                        .boxed().collect(Collectors.toList());
        itemLabels = Arrays.stream(Objects.requireNonNull(
                                   args.getStringArray(SingleChoiceLauncher.BKEY_ALL_LABELS),
                                   SingleChoiceLauncher.BKEY_ALL_LABELS))
                           .collect(Collectors.toList());

        vm = new ViewModelProvider(owner).get(SingleChoiceViewModel.class);
        vm.init(args);
    }

    /**
     * For use as a {@link SingleChoiceDialogFragment}.
     *
     * @return the title for the hosting dialog.
     */
    @NonNull
    String getDialogTitle() {
        return dialogTitle;
    }

    public void onViewCreated(@NonNull final DialogSelectOneBinding vb) {

        final Context context = vb.getRoot().getContext();

        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            vb.message.setText(dialogMessage);
            vb.message.setVisibility(View.VISIBLE);
        } else {
            vb.message.setVisibility(View.GONE);
        }

        final RadioGroupRecyclerAdapter<Long> adapter = new RadioGroupRecyclerAdapter<>(
                context, itemIds, itemLabels::get, vm.getSelectedItem(), vm::setSelectedItem);
        vb.itemList.setAdapter(adapter);
    }

    void saveChanges() {
        SingleChoiceLauncher.setResult(owner, requestKey, vm.getSelectedItem());
    }
}
