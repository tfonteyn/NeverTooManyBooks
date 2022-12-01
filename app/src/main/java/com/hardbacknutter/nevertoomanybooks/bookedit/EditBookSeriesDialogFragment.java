/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

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
    private DialogEditBookSeriesBinding vb;

    /** Displayed for info only. */
    @Nullable
    private String bookTitle;

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
        super(R.layout.dialog_edit_book_series);
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
        bookTitle = args.getString(DBKey.TITLE);

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
        vb = DialogEditBookSeriesBinding.bind(view);
        vb.toolbar.setSubtitle(bookTitle);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllSeriesTitles());

        vb.seriesTitle.setText(currentEdit.getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.seriesNum.setText(currentEdit.getNumber());
    }

    @Nullable
    @Override
    protected Button mapButton(@NonNull final Button actionButton,
                               @NonNull final View buttonPanel) {
        if (actionButton.getId() == R.id.btn_save) {
            return buttonPanel.findViewById(R.id.btn_positive);
        }
        return null;
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM && button != null) {
            if (button.getId() == R.id.btn_save) {
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
            showError(vb.lblSeriesTitle, R.string.vldt_non_blank_required);
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

        @Override
        public void launch(@NonNull final String bookTitle,
                           @NonNull final EditAction action,
                           @NonNull final Series series) {
            super.launch(new EditBookSeriesDialogFragment(),
                         bookTitle, action, DBKey.FK_SERIES, series);
        }
    }
}
