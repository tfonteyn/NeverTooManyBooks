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
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Dialog to edit an existing or new bookshelf.
 * <p>
 * Calling point is a List.
 * <p>
 * Don't move this to the {@link com.eleybourn.bookcatalogue.EditBookshelfListActivity}.
 * The intention is to allow editing 'on the fly' wherever a bookshelf is used.
 */
public class EditBookshelfDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = EditBookshelfDialogFragment.class.getSimpleName();

    private static final String BKEY_BOOKSHELF = TAG + ":bs";

    /** Database access. */
    private DAO mDb;

    private Bookshelf mBookshelf;

    private EditText mNameView;

    private String mName;
    private WeakReference<BookshelfChangedListener> mListener;

    /**
     *
     */
    public static EditBookshelfDialogFragment newInstance(@NonNull final Bookshelf bookshelf) {
        EditBookshelfDialogFragment frag = new EditBookshelfDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(BKEY_BOOKSHELF, bookshelf);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        Context context = getContext();

        Bundle args = requireArguments();

        mBookshelf = args.getParcelable(BKEY_BOOKSHELF);
        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            mName = mBookshelf.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_BOOKSHELF);
        }

        mDb = new DAO();

        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_edit_bookshelf, null);

        mNameView = root.findViewById(R.id.name);
        mNameView.setText(mName);

        //noinspection ConstantConditions
        return new AlertDialog.Builder(context)
                .setView(root)
                .setTitle(R.string.lbl_bookshelf)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (d, which) -> doSave())
                .create();
    }

    private void doSave() {
        mName = mNameView.getText().toString().trim();
        if (mName.isEmpty()) {
            UserMessage.showUserMessage(mNameView, R.string.warning_required_name);
            return;
        }

        // check if a shelf with this name already exists (null if not)
        Bookshelf existingShelf = mDb.getBookshelfByName(mName);

        // are we adding a new Bookshelf but trying to use an existing name?
        if ((mBookshelf.getId() == 0) && (existingShelf != null)) {
            Context c = getContext();
            //noinspection ConstantConditions
            String msg = c.getString(R.string.warning_thing_already_exists,
                                     c.getString(R.string.lbl_bookshelf));
            UserMessage.showUserMessage(mNameView, msg);
            return;
        }

        dismiss();

        // check if there was something changed at all.
        if (mBookshelf.getName().equals(mName)) {
            return;
        }

        // At this point, we know changes were made.
        // Create a new Bookshelf as a holder for the changes.
        Bookshelf newBookshelf = new Bookshelf(mName, mBookshelf.getStyle(mDb).getId());
        // yes, this is NOT efficient and plain dumb. But.. it will allow flex later on.
        // copy new values
        mBookshelf.copyFrom(newBookshelf);

        if (existingShelf != null) {
            mergeShelves(mBookshelf, existingShelf);
        } else {
            if (mDb.updateOrInsertBookshelf(mBookshelf)) {
                if (mListener.get() != null) {
                    mListener.get().onBookshelfChanged(mBookshelf.getId(), 0);
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                        Logger.debug(this, "onBookshelfChanged",
                                     "WeakReference to listener was dead");
                    }
                }
            }
        }
    }

    @Override
    public void onPause() {
        mName = mNameView.getText().toString().trim();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_BOOKSHELF, mName);
    }

    /**
     * Bring up a dumb dialog asking for the next action.
     */
    private void mergeShelves(@NonNull final Bookshelf source,
                              @NonNull final Bookshelf destination) {

        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.menu_edit_bookshelf)
                .setMessage(R.string.confirm_merge_bookshelves)
                .setNegativeButton(android.R.string.cancel, ((d, which) -> d.dismiss()))
                .setPositiveButton(R.string.btn_merge, (d, which) -> {
                    d.dismiss();

                    // move all books from bookshelf to existingShelf
                    int booksMoved = mDb.mergeBookshelves(source.getId(), destination.getId());
                    if (mListener.get() != null) {
                        mListener.get().onBookshelfChanged(destination.getId(), booksMoved);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Logger.debug(this, "onBookshelfChanged",
                                         "WeakReference to listener was dead");
                        }
                    }
                })
                .create()
                .show();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(final BookshelfChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    public interface BookshelfChangedListener {

        /**
         * Called after the user confirms a change.
         *
         * @param bookshelfId the id of the updated shelf, or of the newly inserted shelf.
         * @param booksMoved  if a merge took place, the amount of books moved (or 0).
         */
        void onBookshelfChanged(long bookshelfId,
                                int booksMoved);
    }
}
