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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Bookshelf}.
 */
public class EditBookshelfDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditBookshelfDialogFrag";

    /** Database Access. */
    private DAO mDb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookshelfChangedListener> mListener;
    /** View Binding. */
    private DialogEditBookshelfBinding mVb;

    /** The Bookshelf we're editing. */
    private Bookshelf mBookshelf;

    /** Current edit. */
    private String mName;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookshelfDialogFragment() {
        super(R.layout.dialog_edit_bookshelf);
    }

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

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mBookshelf = args.getParcelable(DBDefinitions.KEY_FK_BOOKSHELF);
        Objects.requireNonNull(mBookshelf, ErrorMsg.ARGS_MISSING_BOOKSHELF);

        if (savedInstanceState == null) {
            mName = mBookshelf.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_BOOKSHELF_NAME);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookshelfBinding.bind(view);

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

        mVb.bookshelf.setText(mName);
    }

    private boolean saveChanges() {
        viewToModel();
        if (mName.isEmpty()) {
            showError(mVb.lblBookshelf, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mBookshelf.getName().equals(mName)) {
            return true;
        }

        // check if a shelf with this name already exists (will be null if not)
        final Bookshelf existingShelfWithSameName = mDb.getBookshelfByName(mName);

        // are we adding a new Bookshelf but trying to use an existing name?
        if ((mBookshelf.getId() == 0) && (existingShelfWithSameName != null)) {
            final Context context = getContext();

            //noinspection ConstantConditions
            final String msg = context.getString(R.string.warning_x_already_exists,
                                                 context.getString(R.string.lbl_bookshelf));
            showError(mVb.lblBookshelf, msg);
            return false;
        }

        if (existingShelfWithSameName == null) {
            // It's a simple rename, store changes
            mBookshelf.setName(mName);

            //noinspection ConstantConditions
            final long styleId = mBookshelf.getStyle(getContext(), mDb).getId();

            final boolean success;
            if (mBookshelf.getId() == 0) {
                success = mDb.insert(mBookshelf, styleId) > 0;
            } else {
                success = mDb.update(mBookshelf, styleId);
            }
            if (success) {
                if (mListener != null && mListener.get() != null) {
                    mListener.get().onBookshelfChanged(mBookshelf.getId(), 0);
                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.w(TAG, "onBookshelfChanged(rename)|"
                                   + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                        : ErrorMsg.LISTENER_WAS_DEAD));
                    }
                }
            }
            return true;

        } else {
            // Merge the 2 shelves
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(mName)
                    .setMessage(R.string.confirm_merge_bookshelves)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        // move all books from the shelf being edited to the existing shelf
                        final long toShelfId = existingShelfWithSameName.getId();
                        final int booksMoved = mDb.mergeBookshelves(mBookshelf.getId(), toShelfId);
                        if (mListener != null && mListener.get() != null) {
                            mListener.get().onBookshelfChanged(toShelfId, booksMoved);
                        } else {
                            if (BuildConfig.DEBUG /* always */) {
                                Log.w(TAG, "onBookshelfChanged(merge)|"
                                           + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                                : ErrorMsg.LISTENER_WAS_DEAD));
                            }
                        }
                        // close the DialogFrame
                        dismiss();
                    })
                    .create()
                    .show();
            return false;
        }
    }

    private void viewToModel() {
        mName = mVb.bookshelf.getText().toString().trim();
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
