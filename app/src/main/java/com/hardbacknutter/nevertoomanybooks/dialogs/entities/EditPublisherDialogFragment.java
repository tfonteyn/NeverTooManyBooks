/*
 * @Copyright 2019 HardBackNutter
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

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Dialog to edit an existing publisher.
 * <p>
 * Calling point is a List.
 */
public class EditPublisherDialogFragment
        extends DialogFragment {

    public static final String TAG = "EditPublisherDialogFrag";

    /** Database Access. */
    private DAO mDb;

    private WeakReference<BookChangedListener> mBookChangedListener;

    private AutoCompleteTextView mNameView;

    /** The Publisher we're editing. */
    private Publisher mPublisher;
    /** Current edit. */
    private String mName;

    /**
     * Constructor.
     *
     * @param publisher to edit.
     *
     * @return the instance
     */
    public static EditPublisherDialogFragment newInstance(@NonNull final Publisher publisher) {

        EditPublisherDialogFragment frag = new EditPublisherDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(DBDefinitions.KEY_PUBLISHER, publisher);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        mPublisher = requireArguments().getParcelable(DBDefinitions.KEY_PUBLISHER);
        Objects.requireNonNull(mPublisher, "Publisher must be passed in args");

        if (savedInstanceState == null) {
            mName = mPublisher.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_PUBLISHER);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_edit_publisher, null);

        @SuppressWarnings("ConstantConditions")
        ArrayAdapter<String> mAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line,
                                   mDb.getPublisherNames());

        mNameView = root.findViewById(R.id.name);
        mNameView.setText(mName);
        mNameView.setAdapter(mAdapter);

        return new AlertDialog.Builder(getContext())
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(R.string.lbl_publisher)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (dialog, which) -> {
                    mName = mNameView.getText().toString().trim();
                    if (mName.isEmpty()) {
                        UserMessage.show(mNameView, R.string.warning_missing_name);
                        return;
                    }

                    if (mPublisher.getName().equals(mName)) {
                        return;
                    }
                    mDb.updatePublisher(mPublisher.getName(), mName);

//                    Bundle data = new Bundle();
//                    data.putString(DBDefinitions.KEY_PUBLISHER, mPublisher.getName());
                    if (mBookChangedListener.get() != null) {
                        mBookChangedListener
                                .get().onBookChanged(0, BookChangedListener.PUBLISHER, null);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Log.d(TAG, "onBookChanged|" + Logger.WEAK_REFERENCE_DEAD);
                        }
                    }
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_PUBLISHER, mName);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mBookChangedListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        mName = mNameView.getText().toString().trim();
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
