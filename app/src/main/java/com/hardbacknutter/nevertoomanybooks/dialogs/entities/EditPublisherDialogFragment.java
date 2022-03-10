/*
 * @Copyright 2018-2021 HardBackNutter
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
    private String mRequestKey;

    /** View Binding. */
    private DialogEditPublisherBinding mVb;

    /** The Publisher we're editing. */
    private Publisher mPublisher;

    /** Current edit. */
    private Publisher mCurrentEdit;

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
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             BKEY_REQUEST_KEY);
        mPublisher = Objects.requireNonNull(args.getParcelable(DBKey.FK_PUBLISHER),
                                            DBKey.FK_PUBLISHER);

        if (savedInstanceState == null) {
            mCurrentEdit = new Publisher(mPublisher.getName());
        } else {
            //noinspection ConstantConditions
            mCurrentEdit = savedInstanceState.getParcelable(DBKey.FK_PUBLISHER);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditPublisherBinding.bind(view);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                ServiceLocator.getInstance().getPublisherDao().getNames());

        mVb.publisher.setText(mCurrentEdit.getName());
        mVb.publisher.setAdapter(nameAdapter);

        mVb.publisher.requestFocus();
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
        if (mCurrentEdit.getName().isEmpty()) {
            showError(mVb.lblPublisher, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mPublisher.getName().equals(mCurrentEdit.getName())) {
            return true;
        }

        // store changes
        mPublisher.copyFrom(mCurrentEdit);

        final Context context = getContext();
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        // There is no book involved here, so use the users Locale instead
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        final PublisherDao publisherDao = serviceLocator.getPublisherDao();
        // check if it already exists (will be 0 if not)
        //noinspection ConstantConditions
        final long existingId = publisherDao.find(context, mPublisher, true, bookLocale);

        if (existingId == 0) {
            final boolean success;
            if (mPublisher.getId() == 0) {
                success = publisherDao.insert(context, mPublisher, bookLocale) > 0;
            } else {
                success = publisherDao.update(context, mPublisher, bookLocale);
            }
            if (success) {
                RowChangedListener.setResult(this, mRequestKey,
                                             DBKey.FK_PUBLISHER, mPublisher.getId());
                return true;
            }
        } else {
            // Merge the 2
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(mPublisher.getLabel(context))
                    .setMessage(R.string.confirm_merge_publishers)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        dismiss();
                        // move all books from the one being edited to the existing one
                        try {
                            publisherDao.merge(context, mPublisher, existingId);
                            RowChangedListener.setResult(
                                    this, mRequestKey,
                                    // return the publisher who 'lost' it's books
                                    DBKey.FK_PUBLISHER,
                                    mPublisher.getId());
                        } catch (@NonNull final DaoWriteException e) {
                            Logger.error(TAG, e);
                            StandardDialogs.showError(context, R.string.error_storage_not_writable);
                        }
                    })
                    .create()
                    .show();
        }

        return false;
    }

    private void viewToModel() {
        mCurrentEdit.setName(mVb.publisher.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_PUBLISHER, mCurrentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
