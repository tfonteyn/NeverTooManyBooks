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
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditPublisherBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Publisher}.
 */
public class EditPublisherDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditPublisherDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** View Binding. */
    private DialogEditPublisherBinding vb;

    /** The Publisher we're editing. */
    private Publisher publisher;

    /** Current edit. */
    private Publisher currentEdit;

    /**
     * No-arg constructor for OS use.
     */
    public EditPublisherDialogFragment() {
        super(R.layout.dialog_edit_publisher);
    }

    /**
     * Launch the dialog.
     *
     * @param fm        The FragmentManager this fragment will be added to.
     * @param publisher to edit.
     */
    public static void launch(@NonNull final FragmentManager fm,
                              @NonNull final Publisher publisher) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, RowChangedListener.REQUEST_KEY);
        args.putParcelable(DBKey.FK_PUBLISHER, publisher);

        final DialogFragment frag = new EditPublisherDialogFragment();
        frag.setArguments(args);
        frag.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                            BKEY_REQUEST_KEY);
        publisher = Objects.requireNonNull(args.getParcelable(DBKey.FK_PUBLISHER),
                                           DBKey.FK_PUBLISHER);

        if (savedInstanceState == null) {
            currentEdit = new Publisher(publisher.getName());
        } else {
            //noinspection ConstantConditions
            currentEdit = savedInstanceState.getParcelable(DBKey.FK_PUBLISHER);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogEditPublisherBinding.bind(view);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getPublisherDao().getNames());

        vb.publisherName.setText(currentEdit.getName());
        vb.publisherName.setAdapter(nameAdapter);
        autoRemoveError(vb.publisherName, vb.lblPublisherName);

        vb.publisherName.requestFocus();
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

        if (currentEdit.getName().isEmpty()) {
            vb.lblPublisherName.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        final boolean nameChanged = !publisher.getName().equals(currentEdit.getName());

        // anything actually changed ? If not, we're done.
        if (nameChanged) {
            return true;
        }

        // store changes
        publisher.copyFrom(currentEdit);

        final Context context = getContext();
        final PublisherDao dao = ServiceLocator.getInstance().getPublisherDao();

        // There is no book involved here, so use the users Locale instead
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        boolean success = false;

        // Check if there is an existing one with the same name
        //noinspection ConstantConditions
        final long existingId = dao.find(context, publisher, true, bookLocale);
        if (existingId == 0) {
            if (publisher.getId() == 0) {
                success = dao.insert(context, publisher, bookLocale) > 0;
            } else {
                success = dao.update(context, publisher, bookLocale);
            }
        } else {
            // There is one with the same name; ask whether to merge the 2
            askToMerge(publisher, existingId);
        }

        if (success) {
            RowChangedListener.setResult(this, requestKey, DBKey.FK_PUBLISHER, publisher.getId());
        }
        return success;
    }

    private void askToMerge(@NonNull final Publisher source,
                            final long targetId) {
        final Context context = getContext();
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(source.getLabel(context))
                .setMessage(R.string.confirm_merge_publishers)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_merge, (d, w) -> {
                    dismiss();
                    try {
                        final PublisherDao dao = ServiceLocator.getInstance().getPublisherDao();
                        final Publisher target = Objects.requireNonNull(dao.getById(targetId));
                        // There are no extra attributes, just move the books
                        dao.moveBooks(context, source, target);

                        // return the publisher who 'lost' it's books
                        RowChangedListener.setResult(this, requestKey,
                                                     DBKey.FK_PUBLISHER, source.getId());
                    } catch (@NonNull final DaoWriteException e) {
                        Logger.error(TAG, e);
                        StandardDialogs.showError(context, R.string.error_storage_not_writable);
                    }
                })
                .create()
                .show();
    }

    private void viewToModel() {
        currentEdit.setName(vb.publisherName.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_PUBLISHER, currentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
