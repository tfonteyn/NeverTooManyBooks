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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

/**
 * Dialog to edit an <strong>EXISTING</strong> {@link Series}.
 * For now this class is not in fact called to create a new entry.
 * We do however keep the code flexible enough to allow it for future usage.
 * <ul>
 * <li>Direct/in-place editing.</li>
 * <li>Modifications ARE STORED in the database</li>
 * <li>Returns the modified item.</li>
 * </ul>
 *
 * @see EditAuthorDialogFragment
 * @see EditSeriesDialogFragment
 * @see EditPublisherDialogFragment
 * @see EditBookshelfDialogFragment
 * @see EditAuthorBottomSheet
 * @see EditSeriesBottomSheet
 * @see EditPublisherBottomSheet
 * @see EditBookshelfBottomSheet
 */
public class EditSeriesDelegate
        implements FlexDialog {

    private static final String TAG = "EditSeriesDelegate";

    /** View Binding. */
    private DialogEditSeriesContentBinding vb;
    private final EditSeriesViewModel vm;

    @NonNull
    private final DialogFragment owner;

    EditSeriesDelegate(@NonNull final DialogFragment owner,
                       @NonNull final Bundle args) {
        this.owner = owner;
        vm = new ViewModelProvider(owner).get(EditSeriesViewModel.class);
        vm.init(args);
    }

    void onViewCreated(@NonNull final DialogEditSeriesContentBinding vb) {
        this.vb = vb;
        final Context context = vb.getRoot().getContext();

        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getSeriesDao().getNames());

        vb.seriesTitle.setText(vm.getCurrentEdit().getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);

        vb.cbxIsComplete.setChecked(vm.getCurrentEdit().isComplete());

        vb.seriesTitle.requestFocus();
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        final Context context = vb.getRoot().getContext();

        final Series currentEdit = vm.getCurrentEdit();
        if (currentEdit.getTitle().isEmpty()) {
            vb.lblSeriesTitle.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        try {
            final Optional<Series> existingEntity = vm.saveIfUnique(context);
            if (existingEntity.isEmpty()) {
                sendResultBack(vm.getSeries());
                return true;
            }

            // There is one with the same name; ask whether to merge the 2.
            StandardDialogs.askToMerge(context, R.string.confirm_merge_series,
                                       vm.getSeries().getLabel(context), () -> {
                        owner.dismiss();
                        try {
                            vm.move(context, existingEntity.get());
                            // return the item which 'lost' it's books
                            sendResultBack(vm.getSeries());
                        } catch (@NonNull final DaoWriteException e) {
                            // log, but ignore - should never happen unless disk full
                            LoggerFactory.getLogger().e(TAG, e, vm.getSeries());
                        }
                    });
            return false;

        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, vm.getSeries());
            return false;
        }
    }

    void viewToModel() {
        final Series currentEdit = vm.getCurrentEdit();
        currentEdit.setTitle(vb.seriesTitle.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());
    }

    private void sendResultBack(@NonNull final Series series) {
        EditParcelableLauncher.setEditInPlaceResult(owner, vm.getRequestKey(), series);
    }
}
