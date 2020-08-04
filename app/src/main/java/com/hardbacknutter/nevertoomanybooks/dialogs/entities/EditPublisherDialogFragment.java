/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditPublisherBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Publisher}.
 */
public class EditPublisherDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditPublisherDialogFrag";
    public static final String REQUEST_KEY = TAG + ":rk";

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
     * Constructor.
     *
     * @param publisher to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Publisher publisher) {
        final DialogFragment frag = new EditPublisherDialogFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(DBDefinitions.KEY_FK_PUBLISHER, publisher);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mPublisher = args.getParcelable(DBDefinitions.KEY_FK_PUBLISHER);
        Objects.requireNonNull(mPublisher, ErrorMsg.NULL_PUBLISHER);

        if (savedInstanceState == null) {
            mName = mPublisher.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_PUBLISHER_NAME);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditPublisherBinding.bind(view);

        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.MENU_SAVE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        //noinspection ConstantConditions
        final DiacriticArrayAdapter<String> adapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, mDb.getPublisherNames());
        mVb.publisher.setText(mName);
        mVb.publisher.setAdapter(adapter);
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

        // There is no book involved here, so use the users Locale instead
        //noinspection ConstantConditions
        final Locale bookLocale = LocaleUtils.getUserLocale(getContext());

        // store changes
        mPublisher.setName(mName);

        final boolean success;
        if (mPublisher.getId() == 0) {
            success = mDb.insert(getContext(), mPublisher, bookLocale) > 0;
        } else {
            success = mDb.update(getContext(), mPublisher, bookLocale);
        }
        if (success) {
            BookChangedListener.sendResult(this, REQUEST_KEY, BookChangedListener.PUBLISHER);
            return true;
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
