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

package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
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
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAction;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableInput;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.series.EditSeriesViewModel;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.nevertoomanybooks.widgets.endicon.ExtClearTextEndIconDelegate;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;

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
                           @NonNull final EditParcelableInput<Parcelable> args) {
        this.owner = owner;
        requestKey = args.getRequestKey();
        action = args.getAction();

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
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_book_series, container, false);
        vb = DialogEditBookSeriesContentBinding.bind(view.findViewById(R.id.dialog_content));
        return view;
    }

    @NonNull
    public Toolbar getToolbar() {
        return Objects.requireNonNull(toolbar, "No toolbar set");
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {

        new InsetsListenerBuilder(vb.dialogContent.getRootView())
                .padding(Side.Start, Side.End, Side.Top, Side.Bottom)
                .systemBars()
                .displayCutout()
                .ime()
                .systemGestures()
                .apply();

        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
            toolbar.setSubtitle(vm.getBook().getTitle());
        }

        final Context context = vb.getRoot().getContext();

        final Series currentEdit = seriesVm.getCurrentEdit();

        initTitle(context, currentEdit);
        initNumber(currentEdit);
        initIssn(currentEdit);
        vb.cbxIsComplete.setChecked(currentEdit.isComplete());
    }

    private void initIssn(@NonNull final Series series) {
        final String issn = series.getIdentifierValue(Identifier.SID_ISSN)
                                  .orElse(seriesVm.getBookIssn());
        if (issn != null) {
            vb.seriesIssn.setText(issn);
            ExtClearTextEndIconDelegate.attach(vb.lblSeriesIssn, null);
            TilUtil.autoRemoveError(vb.seriesIssn, vb.lblSeriesIssn);

            vb.lblSeriesIssn.setVisibility(View.VISIBLE);
            vb.seriesIssn.setVisibility(View.VISIBLE);
        } else {
            vb.lblSeriesIssn.setVisibility(View.GONE);
            vb.seriesIssn.setVisibility(View.GONE);
        }
    }

    private void initTitle(@NonNull final Context context,
                           @NonNull final Series series) {
        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllSeriesTitles());

        vb.seriesTitle.setText(series.getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        TilUtil.autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);
    }

    private void initNumber(@NonNull final Series series) {
        vb.seriesNum.setText(series.getNumber());
        ExtClearTextEndIconDelegate.attach(vb.lblSeriesNum, null);
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
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
                                         seriesVm.getOriginal(), currentEdit);
        return true;
    }

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        final Series series = seriesVm.getCurrentEdit();

        series.setTitle(vb.seriesTitle.getText().toString().strip());
        //noinspection DataFlowIssue
        series.setNumber(vb.seriesNum.getText().toString().strip());

        //noinspection DataFlowIssue
        series.setIdentifierValue(Identifier.SID_ISSN,
                                  vb.seriesIssn.getText().toString().strip());
        series.setComplete(vb.cbxIsComplete.isChecked());
    }
}
