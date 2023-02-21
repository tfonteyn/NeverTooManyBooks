/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfContentBinding;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Bookshelf}.
 */
public class EditBookshelfDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditBookshelfDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** View Binding. */
    private DialogEditBookshelfContentBinding vb;

    /** The Bookshelf we're editing. */
    private Bookshelf bookshelf;

    /** Current edit. Using the 'name' directly. */
    private String name;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookshelfDialogFragment() {
        super(R.layout.dialog_edit_bookshelf, R.layout.dialog_edit_bookshelf_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                           DBKey.FK_BOOKSHELF);

        if (savedInstanceState == null) {
            name = bookshelf.getName();
        } else {
            //noinspection ConstantConditions
            name = savedInstanceState.getString(DBKey.BOOKSHELF_NAME);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditBookshelfContentBinding.bind(view.findViewById(R.id.dialog_content));

        vb.bookshelf.setText(name);
        autoRemoveError(vb.bookshelf, vb.lblBookshelf);

        vb.bookshelf.requestFocus();
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
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

        if (name.isEmpty()) {
            vb.lblBookshelf.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        final boolean nameChanged = !bookshelf.getName().equals(name);

        // anything actually changed ? If not, we're done.
        if (!nameChanged) {
            return true;
        }

        // store changes
        bookshelf.setName(name);

        final Context context = getContext();
        final BookshelfDao dao = ServiceLocator.getInstance().getBookshelfDao();

        // The logic flow here is different from the default one as used for an Author.
        // Here we reject using a name which already exists IF the user meant to create a NEW shelf.

        // Check if there is an existing one with the same name
        final Optional<Bookshelf> existingBookshelf = dao.findByName(bookshelf);

        // Are we adding a new one but trying to use an existing name? -> REJECT
        if (bookshelf.getId() == 0 && existingBookshelf.isPresent()) {
            vb.lblBookshelf.setError(getString(R.string.warning_x_already_exists,
                                               getString(R.string.lbl_bookshelf)));
            return false;
        }

        if (existingBookshelf.isPresent()) {
            // There is one with the same name; ask whether to merge the 2
            SaveChangesHelper.askToMerge(
                    this, dao, bookshelf,
                    savedBookshelf -> Launcher.setResult(this, requestKey,
                                                         savedBookshelf.getId()),
                    R.string.confirm_merge_bookshelves,
                    existingBookshelf.get());
        } else {
            try {
                // We have a unique/new name; either add or update and we're done
                if (bookshelf.getId() == 0) {
                    //noinspection ConstantConditions
                    dao.insert(context, bookshelf);
                } else {
                    //noinspection ConstantConditions
                    dao.update(context, bookshelf);
                }
                Launcher.setResult(this, requestKey, bookshelf.getId());
                return true;

            } catch (@NonNull final DaoWriteException e) {
                return false;
            }
        }
        return false;
    }

    private void viewToModel() {
        //noinspection ConstantConditions
        name = vb.bookshelf.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBKey.BOOKSHELF_NAME, name);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private String requestKey;
        private FragmentManager fragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IntRange(from = 1) final long bookshelfId) {
            final Bundle result = new Bundle(1);
            result.putLong(DBKey.FK_BOOKSHELF, bookshelfId);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.requestKey = requestKey;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param bookshelf to edit.
         */
        public void launch(@NonNull final Bookshelf bookshelf) {

            final Bundle args = new Bundle(2);
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);

            final DialogFragment frag = new EditBookshelfDialogFragment();
            frag.setArguments(args);
            frag.show(fragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(SanityCheck.requirePositiveValue(result.getLong(DBKey.FK_BOOKSHELF),
                                                      DBKey.FK_BOOKSHELF));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param bookshelfId the id of the updated shelf, or of the newly inserted shelf.
         */
        public abstract void onResult(long bookshelfId);
    }
}
