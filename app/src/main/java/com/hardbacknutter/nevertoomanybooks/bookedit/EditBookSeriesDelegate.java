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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesViewModel;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;

/**
 * Add/Edit a single {@link Series} from the book's series list.
 * <p>
 * Can already exist (i.e. have an id) or can be a previously added/new one (id==0).
 * <p>
 * {@link EditAction#Add}:
 * <ul>
 * <li>List-dialogs ADD a NEW item</li>
 * <li>The new item is <strong>NOT stored</strong> in the database</li>
 * <li>Returns the new item</li>
 * </ul>
 * <p>
 * {@link EditAction#Edit}:
 * <ul>
 * <li>List-dialogs EDIT an EXISTING item</li>
 * <li>Modifications are <strong>NOT STORED</strong> in the database</li>
 * <li>Returns the original + a new instance/copy with the modifications</li>
 * </ul>
 */
class EditBookSeriesDelegate
        implements FlexDialogDelegate {

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    /** Book View model. Activity scope. */
    private final EditBookViewModel vm;
    /** Series View model. Fragment scope. */
    private final EditSeriesViewModel seriesVm;
    /** Adding or Editing. */
    private final EditAction action;
    /** View Binding. */
    private DialogEditBookSeriesContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    EditBookSeriesDelegate(@NonNull final DialogFragment owner,
                           @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        action = Objects.requireNonNull(args.getParcelable(EditAction.BKEY), EditAction.BKEY);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(owner.getActivity()).get(EditBookViewModel.class);
        seriesVm = new ViewModelProvider(owner).get(EditSeriesViewModel.class);
        seriesVm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditBookSeriesContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onCreateView(@NonNull final View view) {
        vb = DialogEditBookSeriesContentBinding.bind(view.findViewById(R.id.dialog_content));
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        if (toolbar != null) {
            initToolbar(owner, dialogType, toolbar);
        }

        final Context context = vb.getRoot().getContext();

        final Series currentEdit = seriesVm.getCurrentEdit();

        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllSeriesTitles());

        vb.seriesTitle.setText(currentEdit.getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        TilUtil.autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.seriesNum.setText(currentEdit.getNumber());
    }

    @Override
    public void initToolbar(@NonNull final DialogFragment owner,
                            @NonNull final DialogType dialogType,
                            @NonNull final Toolbar toolbar) {
        FlexDialogDelegate.super.initToolbar(owner, dialogType, toolbar);
        toolbar.setSubtitle(vm.getBook().getTitle());
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

        final Series currentEdit = seriesVm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getTitle().isEmpty()) {
            vb.lblSeriesTitle.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        EditParcelableLauncher.setResult(owner, requestKey, action,
                                         seriesVm.getSeries(), currentEdit);
        return true;
    }

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        final Series currentEdit = seriesVm.getCurrentEdit();

        currentEdit.setTitle(vb.seriesTitle.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        //noinspection DataFlowIssue
        currentEdit.setNumber(vb.seriesNum.getText().toString().trim());
    }
}
