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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditSeriesBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING</strong> {@link Series}.
 */
public class EditSeriesDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditSeriesDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** View Binding. */
    private DialogEditSeriesBinding vb;

    /** The Series we're editing. */
    private Series series;

    /** Current edit. */
    private Series currentEdit;

    /**
     * No-arg constructor for OS use.
     */
    public EditSeriesDialogFragment() {
        super(R.layout.dialog_edit_series);
    }

    /**
     * Launch the dialog.
     *
     * @param fm     The FragmentManager this fragment will be added to.
     * @param series to edit.
     */
    public static void launch(@NonNull final FragmentManager fm,
                              @NonNull final Series series) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, RowChangedListener.REQUEST_KEY);
        args.putParcelable(DBKey.FK_SERIES, series);

        final DialogFragment frag = new EditSeriesDialogFragment();
        frag.setArguments(args);
        frag.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        series = Objects.requireNonNull(args.getParcelable(DBKey.FK_SERIES), DBKey.FK_SERIES);

        if (savedInstanceState == null) {
            currentEdit = new Series(series.getTitle(), series.isComplete());
        } else {
            //noinspection ConstantConditions
            currentEdit = savedInstanceState.getParcelable(DBKey.FK_SERIES);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditSeriesBinding.bind(view);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> titleAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getSeriesDao().getNames());

        vb.seriesTitle.setText(currentEdit.getTitle());
        vb.seriesTitle.setAdapter(titleAdapter);
        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.seriesTitle.requestFocus();
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

        if (currentEdit.getTitle().isEmpty()) {
            showError(vb.lblSeriesTitle, R.string.vldt_non_blank_required);
            return false;
        }

        final boolean nameChanged = !series.getTitle().equals(currentEdit.getTitle());

        // anything actually changed ? If not, we're done.
        if (!nameChanged
            && series.isComplete() == currentEdit.isComplete()) {
            return true;
        }

        // store changes
        series.copyFrom(currentEdit, false);

        final Context context = getContext();
        final SeriesDao dao = ServiceLocator.getInstance().getSeriesDao();

        // There is no book involved here, so use the users Locale instead
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        if (series.getId() == 0) {
            // It's a new one. Check if there is an existing one with the same name
            //noinspection ConstantConditions
            final long existingId = dao.find(context, series, true, bookLocale);
            if (existingId == 0) {
                // It's an entirely new one; add it.
                if (dao.insert(context, series, bookLocale) > 0) {
                    RowChangedListener.setResult(this, requestKey,
                                                 DBKey.FK_SERIES, series.getId());
                    return true;
                }
            } else {
                // There is one with the same name; ask whether to merge the 2
                askToMerge(series, existingId);
            }
        } else {
            // It's an existing one
            if (nameChanged) {
                // but the name was changed. Check if there is an existing one with the same name
                //noinspection ConstantConditions
                final long existingId = dao.find(context, series, true, bookLocale);
                if (existingId == 0) {
                    // none with the same name; so we just update this one
                    if (dao.update(context, series, bookLocale)) {
                        RowChangedListener.setResult(this, requestKey,
                                                     DBKey.FK_SERIES, series.getId());
                        return true;
                    }
                } else {
                    // There is one with the same name; ask whether to merge the 2
                    askToMerge(series, existingId);
                }
            } else {
                // The name was not changed; just update the other attributes
                //noinspection ConstantConditions
                if (dao.update(context, series, bookLocale)) {
                    RowChangedListener.setResult(this, requestKey,
                                                 DBKey.FK_SERIES, series.getId());
                    return true;
                }
            }
        }

        return false;
    }

    private void askToMerge(@NonNull final Series source,
                            final long targetId) {
        final Context context = getContext();
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(source.getLabel(context))
                .setMessage(R.string.confirm_merge_series)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_merge, (d, w) -> {
                    dismiss();
                    try {
                        final SeriesDao dao = ServiceLocator.getInstance().getSeriesDao();
                        final Series target = Objects.requireNonNull(dao.getById(targetId));
                        //URGENT: should we copy these extra attributes ? Probably NOT...
                        // target.setComplete(current.isComplete());
                        dao.moveBooks(context, source, target);

                        // return the series which 'lost' it's books
                        RowChangedListener.setResult(this, requestKey,
                                                     DBKey.FK_SERIES, source.getId());
                    } catch (@NonNull final DaoWriteException e) {
                        Logger.error(TAG, e);
                        StandardDialogs.showError(context, R.string.error_storage_not_writable);
                    }
                })
                .create()
                .show();
    }

    private void viewToModel() {
        currentEdit.setTitle(vb.seriesTitle.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());
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
}
