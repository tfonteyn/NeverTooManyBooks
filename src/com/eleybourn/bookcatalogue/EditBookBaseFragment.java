package com.eleybourn.bookcatalogue;


import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;

/**
 * Base class for all fragments that appear in {@link EditBookFragment}.
 * <p>
 * Full list:
 * {@link EditBookFieldsFragment}
 * {@link EditBookPublicationFragment}
 * {@link EditBookNotesFragment}
 * {@link EditBookTocFragment}
 */
public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor {

    @Override
    protected void onLoadFieldsFromBook(final boolean setAllFrom) {
        super.onLoadFieldsFromBook(setAllFrom);

        // new book ?
        if (!mBookBaseFragmentModel.isExistingBook()) {
            populateNewBookFieldsFromBundle(getArguments());
        }
    }

    /**
     * Uses the values from the Bundle to populate the Book but don't overwrite existing values.
     * <p>
     * Can/should be overwritten for handling specific field defaults, e.g. Bookshelf.
     *
     * @param bundle to load values from
     */
    protected void populateNewBookFieldsFromBundle(@Nullable final Bundle bundle) {
        // Check if we have any data, for example from a Search
        if (bundle != null) {
            Bundle values = bundle.getBundle(UniqueId.BKEY_BOOK_DATA);
            if (values != null) {
                // if we do, add if not there yet
                mFields.setAllFrom(values, false);
            }
        }
    }

    /**
     * Trigger the Fragment to save its Fields to the Book.
     * <p>
     * This is always done, even when the user 'cancel's the edit.
     * The latter will then result in a "are you sure" where they can 'cancel the cancel'
     * and continue with all data present.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        saveFields();
        super.onPause();
        Tracker.exitOnPause(this);
    }

    /**
     * <br>{@inheritDoc}
     * <br>
     * <p>This is 'final' because we want inheritors to implement {@link #onSaveFieldsToBook}
     */
    @Override
    public final void saveFields() {
        onSaveFieldsToBook();
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Fields.Field} into the {@link DataManager} e.g. the {@link Book}
     * <p>
     * Override as needed.
     */
    @CallSuper
    protected void onSaveFieldsToBook() {
        mFields.putAllInto(mBookBaseFragmentModel.getBook());
    }
}
