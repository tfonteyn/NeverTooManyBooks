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

package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesViewModel;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

/**
 * Add/Edit a single Series from the book's series list.
 * <p>
 * Can already exist (i.e. have an id) or can be a previously added/new one (id==0).
 * <p>
 * {@link EditAction#Add}:
 * <ul>
 * <li>used for list-dialogs needing to add a NEW item to the list</li>
 * <li>the item is NOT stored in the database</li>
 * <li>returns the new item</li>
 * </ul>
 * <p>
 * {@link EditAction#Edit}:
 * <ul>
 * <li>used for list-dialogs needing to EDIT an existing item in the list</li>
 * <li>the modifications are NOT stored in the database</li>
 * <li>returns the original untouched + a new copy with the modifications</li>
 * </ul>
 * Must be a public static class to be properly recreated from instance state.
 */
public class EditBookSeriesDelegate
        implements FlexDialogDelegate<DialogEditBookSeriesContentBinding> {

    @NonNull
    private final DialogFragment owner;

    /** View model. Must be in the Activity scope. */
    private final EditBookViewModel vm;
    /** Series View model. Fragment scope. */
    private final EditSeriesViewModel seriesVm;
    /** View Binding. */
    private DialogEditBookSeriesContentBinding vb;

    /** Adding or Editing. */
    private final EditAction action;

    EditBookSeriesDelegate(@NonNull final DialogFragment owner,
                           @NonNull final Bundle args) {

        this.owner = owner;
        action = Objects.requireNonNull(args.getParcelable(EditAction.BKEY), EditAction.BKEY);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(owner.getActivity()).get(EditBookViewModel.class);
        seriesVm = new ViewModelProvider(owner).get(EditSeriesViewModel.class);
        seriesVm.init(args);
    }

    @Nullable
    public String getToolbarSubtitle() {
        return vm.getBook().getTitle();
    }

    public void onViewCreated(@NonNull final DialogEditBookSeriesContentBinding vb) {
        this.vb = vb;

        final Context context = vb.getRoot().getContext();

        final Series currentEdit = seriesVm.getCurrentEdit();

        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllSeriesTitles());

        vb.seriesTitle.setText(currentEdit.getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.seriesNum.setText(currentEdit.getNumber());
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        return false;
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

        final Series currentEdit = seriesVm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getTitle().isEmpty()) {
            vb.lblSeriesTitle.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        EditParcelableLauncher.setResult(owner, seriesVm.getRequestKey(), action,
                                         seriesVm.getSeries(), currentEdit);
        return true;
    }

    public void viewToModel() {
        final Series currentEdit = seriesVm.getCurrentEdit();

        currentEdit.setTitle(vb.seriesTitle.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        //noinspection DataFlowIssue
        currentEdit.setNumber(vb.seriesNum.getText().toString().trim());
    }

}
