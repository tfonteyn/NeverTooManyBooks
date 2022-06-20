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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
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

    private EditBookViewModel vm;

    /** Displayed for info only. */
    @Nullable
    private String bookTitle;

    /** View Binding. */
    private DialogEditBookSeriesBinding vb;

    /** The Series we're editing. */
    private Series series;

    /** Current edit. */
    private Series currentEdit;

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
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                            BKEY_REQUEST_KEY);
        series = Objects.requireNonNull(args.getParcelable(DBKey.FK_SERIES),
                                        DBKey.FK_SERIES);
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

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
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

        Launcher.setResult(this, requestKey, series, currentEdit);
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
            implements FragmentResultListener {

        private static final String ORIGINAL = "original";
        private static final String MODIFIED = "modified";
        private String requestKey;
        private FragmentManager fragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final Series original,
                              @NonNull final Series modified) {
            final Bundle result = new Bundle(2);
            result.putParcelable(ORIGINAL, original);
            result.putParcelable(MODIFIED, modified);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.requestKey = requestKey;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param bookTitle displayed for info only
         * @param series    to edit
         */
        void launch(@NonNull final String bookTitle,
                    @NonNull final Series series) {
            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putString(DBKey.TITLE, bookTitle);
            args.putParcelable(DBKey.FK_SERIES, series);

            final DialogFragment frag = new EditBookSeriesDialogFragment();
            frag.setArguments(args);
            frag.show(fragmentManager, TAG);
        }


        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getParcelable(ORIGINAL), ORIGINAL),
                     Objects.requireNonNull(result.getParcelable(MODIFIED), MODIFIED));
        }

        /**
         * Callback handler.
         *
         * @param original the original item
         * @param modified the modified item
         */
        public abstract void onResult(@NonNull Series original,
                                      @NonNull Series modified);
    }
}
