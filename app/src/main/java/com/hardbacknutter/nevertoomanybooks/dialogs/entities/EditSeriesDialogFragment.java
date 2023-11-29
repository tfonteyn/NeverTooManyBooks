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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

/**
 * Dialog to edit an <strong>EXISTING</strong> {@link Series}.
 */
public class EditSeriesDialogFragment
        extends EditMergeableDialogFragment<Series> {

    /** Fragment/Log tag. */
    public static final String TAG = "EditSeriesDialogFrag";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** View Binding. */
    private DialogEditSeriesContentBinding vb;

    /** The Series we're editing. */
    private Series series;

    /** Current edit. */
    private Series currentEdit;

    /**
     * No-arg constructor for OS use.
     */
    public EditSeriesDialogFragment() {
        super(R.layout.dialog_edit_series, R.layout.dialog_edit_series_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(
                args.getString(EditLauncher.BKEY_REQUEST_KEY), EditLauncher.BKEY_REQUEST_KEY);
        series = Objects.requireNonNull(
                args.getParcelable(EditLauncher.BKEY_ITEM), EditLauncher.BKEY_ITEM);

        if (savedInstanceState == null) {
            currentEdit = new Series(series.getTitle(), series.isComplete());
        } else {
            currentEdit = savedInstanceState.getParcelable(EditLauncher.BKEY_ITEM);
        }
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

        vb.seriesTitle.setText(currentEdit.getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        autoRemoveError(vb.seriesTitle, vb.lblSeriesTitle);

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.seriesTitle.requestFocus();
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

        if (currentEdit.getTitle().isEmpty()) {
            vb.lblSeriesTitle.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // Case-sensitive! We must allow the user to correct case.
        final boolean nameChanged = !series.isSameName(currentEdit);

        // anything actually changed ? If not, we're done.
        if (!nameChanged && series.isComplete() == currentEdit.isComplete()) {
            return true;
        }

        // store changes
        series.copyFrom(currentEdit, false);

        final Context context = requireContext();
        final SeriesDao dao = ServiceLocator.getInstance().getSeriesDao();
        final Locale locale = series.getLocale(context).orElseGet(
                () -> getResources().getConfiguration().getLocales().get(0));

        final Consumer<Series> onSuccess = savedSeries -> EditInPlaceParcelableLauncher
                .setResult(this, requestKey, savedSeries);

        try {
            if (series.getId() == 0 || nameChanged) {
                // Check if there is an another one with the same new name.
                final Optional<Series> existingEntity =
                        dao.findByName(context, series, locale);

                if (existingEntity.isPresent()) {
                    // There is one with the same name; ask whether to merge the 2
                    askToMerge(dao, R.string.confirm_merge_series,
                               series, existingEntity.get(), onSuccess);
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
        currentEdit.setTitle(vb.seriesTitle.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(EditLauncher.BKEY_ITEM, currentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
