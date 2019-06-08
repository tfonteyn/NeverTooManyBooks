/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.dialogs.entities;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Dialog to edit an existing publisher.
 * <p>
 * Calling point is a List.
 */
public class EditPublisherDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = EditPublisherDialogFragment.class.getSimpleName();

    /** Database access. */
    private DAO mDb;

    private String mName;

    private AutoCompleteTextView mNameView;
    private WeakReference<BookChangedListener> mBookChangedListener;

    /**
     * Constructor.
     */
    public static EditPublisherDialogFragment newInstance(@NonNull final Publisher publisher) {

        EditPublisherDialogFragment frag = new EditPublisherDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(DBDefinitions.KEY_PUBLISHER, publisher);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        mDb = new DAO();

        Publisher publisher = requireArguments().getParcelable(DBDefinitions.KEY_PUBLISHER);
        Objects.requireNonNull(publisher);
        if (savedInstanceState == null) {
            mName = publisher.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_PUBLISHER);
        }

        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_edit_publisher, null);

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
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                    mName = mNameView.getText().toString().trim();
                    if (mName.isEmpty()) {
                        UserMessage.showUserMessage(mNameView, R.string.warning_required_name);
                        return;
                    }
                    dismiss();

                    if (publisher.getName().equals(mName)) {
                        return;
                    }
                    mDb.updatePublisher(publisher.getName(), mName);

                    Bundle data = new Bundle();
                    data.putString(DBDefinitions.KEY_PUBLISHER, publisher.getName());
                    if (mBookChangedListener.get() != null) {
                        mBookChangedListener.get().onBookChanged(0, BookChangedListener.PUBLISHER,
                                                                 data);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Logger.debug(this, "onBookChanged",
                                         "WeakReference to listener was dead");
                        }
                    }
                })
                .create();
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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_PUBLISHER, mName);
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
