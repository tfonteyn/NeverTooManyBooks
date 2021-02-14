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
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditPublisherBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Publisher}.
 */
public class EditPublisherDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditPublisherDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    /** Database Access. */
    private DAO mDb;
    /** View Binding. */
    private DialogEditPublisherBinding mVb;

    /** The Publisher we're editing. */
    private Publisher mPublisher;

    /** Current edit. */
    private String mName;

    /**
     * No-arg constructor for OS use.
     */
    public EditPublisherDialogFragment() {
        super(R.layout.dialog_edit_publisher);
    }

    /**
     * Launch the dialog.
     *
     * @param publisher to edit.
     */
    public static void launch(@NonNull final FragmentActivity activity,
                              @NonNull final Publisher publisher) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, BooksOnBookshelf.RowChangeListener.REQUEST_KEY);
        args.putParcelable(DBDefinitions.KEY_FK_PUBLISHER, publisher);

        final DialogFragment frag = new EditPublisherDialogFragment();
        frag.setArguments(args);
        frag.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mDb = new DAO(getContext(), TAG);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mPublisher = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_PUBLISHER),
                                            "KEY_FK_PUBLISHER");

        if (savedInstanceState == null) {
            mName = mPublisher.getName();
        } else {
            //noinspection ConstantConditions
            mName = savedInstanceState.getString(DBDefinitions.KEY_PUBLISHER_NAME);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditPublisherBinding.bind(view);

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> adapter = new ExtArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                ExtArrayAdapter.FilterType.Diacritic, mDb.getPublisherNames());
        mVb.publisher.setText(mName);
        mVb.publisher.setAdapter(adapter);
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
        if (mName.isEmpty()) {
            showError(mVb.lblPublisher, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mPublisher.getName().equals(mName)) {
            return true;
        }

        // store changes
        mPublisher.setName(mName);

        final Context context = getContext();

        // There is no book involved here, so use the users Locale instead
        //noinspection ConstantConditions
        final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

        // check if it already exists (will be 0 if not)
        final long existingId = mDb.getPublisherId(context, mPublisher, true, bookLocale);

        if (existingId == 0) {
            final boolean success;
            if (mPublisher.getId() == 0) {
                success = mDb.insert(context, mPublisher, bookLocale) > 0;
            } else {
                success = mDb.update(context, mPublisher, bookLocale);
            }
            if (success) {
                BooksOnBookshelf.RowChangeListener
                        .setResult(this, mRequestKey,
                                   BooksOnBookshelf.RowChangeListener.PUBLISHER,
                                   mPublisher.getId());
                return true;
            }
        } else {
            // Merge the 2
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(mPublisher.getLabel(context))
                    .setMessage(R.string.confirm_merge_publishers)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        dismiss();
                        // move all books from the one being edited to the existing one
                        try {
                            mDb.merge(context, mPublisher, existingId);
                            BooksOnBookshelf.RowChangeListener.setResult(
                                    this, mRequestKey,
                                    // return the publisher who 'lost' it's books
                                    BooksOnBookshelf.RowChangeListener.PUBLISHER,
                                    mPublisher.getId());
                        } catch (@NonNull final DAO.DaoWriteException e) {
                            Logger.error(context, TAG, e);
                            StandardDialogs.showError(context, R.string.error_storage_not_writable);
                        }
                    })
                    .create()
                    .show();
        }

        return false;
    }

    private void viewToModel() {
        mName = mVb.publisher.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_PUBLISHER_NAME, mName);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
