/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.ExtArrayAdapter;

/**
 * Edit a single Series from the book's series list.
 * It could exist (i.e. have an ID) or could be a previously added/new one (ID==0).
 * <p>
 * Must be a public static class to be properly recreated from instance state.
 */
public class EditBookSeriesDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditSeriesForBookDialog";
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    /** View model. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** View Binding. */
    private DialogEditBookSeriesContentBinding vb;

    /** The Series we're editing. */
    private Series series;
    /** Current edit. */
    private Series currentEdit;
    /** Adding or Editing. */
    private EditAction action;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookSeriesDialogFragment() {
        super(R.layout.dialog_edit_book_series, R.layout.dialog_edit_book_series_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        action = Objects.requireNonNull(args.getParcelable(EditAction.BKEY), EditAction.BKEY);
        series = Objects.requireNonNull(args.getParcelable(DBKey.FK_SERIES), DBKey.FK_SERIES);

        if (savedInstanceState == null) {
            currentEdit = new Series(series.getTitle(), series.isComplete());
            currentEdit.setNumber(series.getNumber());
        } else {
            //noinspection ConstantConditions
            currentEdit = savedInstanceState.getParcelable(DBKey.FK_SERIES);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditBookSeriesContentBinding.bind(view.findViewById(R.id.dialog_content));
        setSubtitle(vm.getBook().getTitle());

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllSeriesTitles());

        vb.seriesTitle.setText(currentEdit.getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.seriesNum.setText(currentEdit.getNumber());
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getTitle().isEmpty()) {
            vb.lblSeriesTitle.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        if (action == EditAction.Add) {
            Launcher.setResult(this, requestKey, currentEdit);
        } else {
            Launcher.setResult(this, requestKey, series, currentEdit);
        }
        return true;
    }

    private void viewToModel() {
        currentEdit.setTitle(vb.seriesTitle.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        //noinspection ConstantConditions
        currentEdit.setNumber(vb.seriesNum.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_SERIES, currentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public abstract static class Launcher
            extends EditLauncher<Series> {

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKeyValue,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            super.registerForFragmentResult(fragmentManager,
                                            BKEY_REQUEST_KEY,
                                            requestKeyValue,
                                            lifecycleOwner);
        }

        @Override
        public void launch(@NonNull final EditAction action,
                           @NonNull final Series series) {
            super.launch(new EditBookSeriesDialogFragment(),
                         action, DBKey.FK_SERIES, series);
        }
    }
}
