package com.eleybourn.bookcatalogue;


import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;

/**
 * Base class for all fragments that appear in {@link EditBookActivity}.
 * <p>
 * Full list:
 * {@link EditBookFieldsFragment}
 * {@link EditBookPublicationFragment}
 * {@link EditBookNotesFragment}
 * {@link EditBookTOCFragment}
 */
public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor {


    @Override
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getId());
        super.onLoadFieldsFromBook(book, setAllFrom);

        // new book ? load data fields from Extras
        if (book.getId() == 0) {
            Bundle extras = requireActivity().getIntent().getExtras();
            populateNewBookFieldsFromBundle(book, extras);
        }

        Tracker.exitOnLoadFieldsFromBook(this, book.getId());
    }

    /**
     * Uses the values from the Bundle to populate the Book but don't overwrite existing values.
     * <p>
     * Can/should be overwritten for handling specific field defaults, e.g. Bookshelf.
     *
     * @param book   to populate
     * @param bundle to load values from
     */
    protected void populateNewBookFieldsFromBundle(@NonNull final Book book,
                                                   @Nullable final Bundle bundle) {
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
     * Trigger the Fragment to save it's Fields to the Book.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        saveFieldsTo(getBookManager().getBook());
        super.onPause();
        Tracker.exitOnPause(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This is 'final' because we want inheritors to implement {@link #onSaveFieldsToBook}
     * (for consistency with the load process).
     */
    @Override
    public final <T extends DataManager> void saveFieldsTo(@NonNull final T dataManager) {
        onSaveFieldsToBook((Book) dataManager);
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Fields.Field} into the {@link DataManager} e.g. the {@link Book}
     * <p>
     * Override as needed, calling super if needed.
     *
     * @param book field content will be copied to this object
     */
    @CallSuper
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getId());
        mFields.putAllInto(book);
        Tracker.exitOnSaveFieldsToBook(this, book.getId());
    }
}
