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

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Dialog to edit an existing or new bookshelf.
 * <p>
 * Calling point is a List.
 */
public class EditBookshelfDialogFragment
        extends DialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditBookshelfDialogFrag";

    /** Database Access. */
    private DAO mDb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookshelfChangedListener> mListener;
    @Nullable
    private String mDialogTitle;
    /** View Binding. */
    private DialogEditBookshelfBinding mVb;

    /** The Bookshelf we're editing. */
    private Bookshelf mBookshelf;

    /** Current edit. */
    private String mName;

    /**
     * Constructor.
     *
     * @param bookshelf to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Bookshelf bookshelf) {
        final DialogFragment frag = new EditBookshelfDialogFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(DBDefinitions.KEY_FK_BOOKSHELF, bookshelf);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mDialogTitle = args.getString(StandardDialogs.BKEY_DIALOG_TITLE,
                                      getString(R.string.lbl_edit_bookshelf));

        mBookshelf = args.getParcelable(DBDefinitions.KEY_FK_BOOKSHELF);
        Objects.requireNonNull(mBookshelf, ErrorMsg.ARGS_MISSING_BOOKSHELF);

        if (savedInstanceState == null) {
            mName = mBookshelf.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_BOOKSHELF_NAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = DialogEditBookshelfBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
        mVb.toolbar.setTitle(mDialogTitle);
        mVb.toolbar.inflateMenu(R.menu.toolbar_save);
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        mVb.name.setText(mName);
    }

    private boolean saveChanges() {
        mName = mVb.name.getText().toString().trim();
        if (mName.isEmpty()) {
            Snackbar.make(mVb.name, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        // check if a shelf with this name already exists (null if not)
        final Bookshelf existingShelf = mDb.getBookshelfByName(mName);

        // are we adding a new Bookshelf but trying to use an existing name?
        if ((mBookshelf.getId() == 0) && (existingShelf != null)) {
            final Context context = getContext();

            //noinspection ConstantConditions
            String msg = context.getString(R.string.warning_x_already_exists,
                                           context.getString(R.string.lbl_bookshelf));
            Snackbar.make(mVb.name, msg, Snackbar.LENGTH_LONG).show();
            return false;
        }

        // anything actually changed ?
        if (mBookshelf.getName().equals(mName)) {
            return true;
        }

        // Create a new Bookshelf as a holder for the changes.
        //noinspection ConstantConditions
        Bookshelf newBookshelf = new Bookshelf(mName, mBookshelf.getStyle(getContext(), mDb));
        mBookshelf.copyFrom(newBookshelf);

        if (existingShelf != null) {
            // Merge the 2 shelves
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.lbl_edit_bookshelf)
                    .setMessage(R.string.confirm_merge_bookshelves)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        // move all books
                        int booksMoved = mDb.mergeBookshelves(mBookshelf.getId(),
                                                              existingShelf.getId());
                        if (mListener != null && mListener.get() != null) {
                            mListener.get().onBookshelfChanged(existingShelf.getId(), booksMoved);
                        } else {
                            if (BuildConfig.DEBUG /* always */) {
                                Log.w(TAG, "onBookshelfChanged(merge)|" +
                                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                              : ErrorMsg.LISTENER_WAS_DEAD));
                            }
                        }
                        // close the DialogFrame
                        dismiss();
                    })
                    .create()
                    .show();
            return false;

        } else {
            long styleId = mBookshelf.getStyle(getContext(), mDb).getId();
            if (mDb.updateOrInsertBookshelf(getContext(), mBookshelf, styleId)) {
                if (mListener != null && mListener.get() != null) {
                    mListener.get().onBookshelfChanged(mBookshelf.getId(), 0);
                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.w(TAG, "onBookshelfChanged(new)|" +
                                   (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                      : ErrorMsg.LISTENER_WAS_DEAD));
                    }
                }
            }
            return true;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_BOOKSHELF_NAME, mName);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookshelfChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        mName = mVb.name.getText().toString().trim();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    public interface BookshelfChangedListener {

        /**
         * Called after the user confirms a change.
         *
         * @param bookshelfId the id of the updated shelf, or of the newly inserted shelf.
         * @param booksMoved  if a merge took place, the amount of books moved (otherwise 0).
         */
        void onBookshelfChanged(long bookshelfId,
                                int booksMoved);
    }
}
