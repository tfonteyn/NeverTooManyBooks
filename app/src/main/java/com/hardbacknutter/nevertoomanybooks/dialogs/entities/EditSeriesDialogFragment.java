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
import androidx.lifecycle.ViewModelProvider;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
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
 */
public class EditSeriesDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditSeriesDialogFrag";

    /** View Binding. */
    private DialogEditSeriesContentBinding vb;
    private EditSeriesViewModel vm;

    /**
     * No-arg constructor for OS use.
     */
    public EditSeriesDialogFragment() {
        super(R.layout.dialog_edit_series, R.layout.dialog_edit_series_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditSeriesViewModel.class);
        vm.init(requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditSeriesContentBinding.bind(view.findViewById(R.id.dialog_content));

        //noinspection DataFlowIssue
        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getSeriesDao().getNames());

        vb.seriesTitle.setText(vm.getCurrentEdit().getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);

        vb.cbxIsComplete.setChecked(vm.getCurrentEdit().isComplete());

        vb.seriesTitle.requestFocus();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
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

        final Series currentEdit = vm.getCurrentEdit();
        if (currentEdit.getTitle().isEmpty()) {
            vb.lblSeriesTitle.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isChanged()) {
            return true;
        }

        // store changes
        final Series series = vm.getSeries();
        series.copyFrom(currentEdit, false);

        final Context context = requireContext();
        final SeriesDao dao = ServiceLocator.getInstance().getSeriesDao();
        final Locale locale = series.getLocale(context).orElseGet(
                () -> getResources().getConfiguration().getLocales().get(0));

        final Consumer<Series> onSuccess = savedSeries -> ParcelableDialogLauncher
                .setEditInPlaceResult(this, vm.getRequestKey(), savedSeries);

        try {
            if (series.getId() == 0 || !series.isSameName(currentEdit)) {
                // Check if there is an another one with the same new name.
                final Optional<Series> existingEntity =
                        dao.findByName(context, series, locale);

                if (existingEntity.isPresent()) {
                    // There is one with the same name; ask whether to merge the 2.
                    StandardDialogs.askToMerge(context, R.string.confirm_merge_series,
                                               series.getLabel(context), () -> {
                                dismiss();
                                try {
                                    // Note that we ONLY move the books. No other attributes from
                                    // the source item are copied to the target item!
                                    dao.moveBooks(context, series, existingEntity.get());
                                    // return the item which 'lost' it's books
                                    onSuccess.accept(series);
                                } catch (@NonNull final DaoWriteException e) {
                                    ErrorDialog.show(context, TAG, e);
                                }
                            });
                    return false;

                } else {
                    // Just insert or update as needed
                    if (series.getId() == 0) {
                        dao.insert(context, series, locale);
                    } else {
                        dao.update(context, series, locale);
                    }
                    onSuccess.accept(series);
                    return true;
                }
            } else {
                // It's an existing one and the name was not changed;
                // just update the other attributes
                dao.update(context, series, locale);
                onSuccess.accept(series);
                return true;
            }
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, series);
            return false;
        }
    }

    private void viewToModel() {
        final Series currentEdit = vm.getCurrentEdit();
        currentEdit.setTitle(vb.seriesTitle.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
