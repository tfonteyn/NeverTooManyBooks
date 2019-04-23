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

package com.eleybourn.bookcatalogue.dialogs.fieldeditdialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.BookChangedListener;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
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
    private static final String TAG = EditPublisherDialogFragment.class.getSimpleName();
    private DBA mDb;
    private String mName;

    private AutoCompleteTextView mNameView;

    /**
     * (syntax sugar for newInstance)
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final Publisher publisher) {
        if (fm.findFragmentByTag(TAG) == null) {
            newInstance(publisher).show(fm, TAG);
        }
    }

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
        final Activity mActivity = requireActivity();
        mDb = new DBA(mActivity);

        Bundle args = requireArguments();

        final Publisher publisher = args.getParcelable(DBDefinitions.KEY_PUBLISHER);
        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            mName = publisher.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_PUBLISHER);
        }

        View root = mActivity.getLayoutInflater().inflate(R.layout.dialog_edit_publisher, null);

        ArrayAdapter<String> mAdapter =
                new ArrayAdapter<>(mActivity, android.R.layout.simple_dropdown_item_1line,
                                   mDb.getPublisherNames());

        mNameView = root.findViewById(R.id.name);
        mNameView.setText(mName);
        mNameView.setAdapter(mAdapter);

        root.findViewById(R.id.confirm).setOnClickListener(v -> {
            mName = mNameView.getText().toString().trim();
            if (mName.isEmpty()) {
                UserMessage.showUserMessage(mNameView, R.string.warning_required_name);
                return;
            }
            dismiss();

            //noinspection ConstantConditions
            if (publisher.getName().equals(mName)) {
                return;
            }
            mDb.updatePublisher(publisher.getName(), mName);
            // Let the Activity know
            if (mActivity instanceof BookChangedListener) {
                final BookChangedListener bcl = (BookChangedListener) mActivity;
                bcl.onBookChanged(0, BookChangedListener.PUBLISHER, null);
            }
        });

        root.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(mActivity)
                .setView(root)
                .setTitle(R.string.lbl_publisher)
                .create();
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
