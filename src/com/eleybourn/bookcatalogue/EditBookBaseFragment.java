package com.eleybourn.bookcatalogue;


import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

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
 * {@link EditBookNotesFragment}
 * {@link EditBookTOCFragment}
 */
public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor {

    /**
     * Trigger the Fragment to save it's Fields to the Book.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        saveFieldsTo(getBookManager().getBook());
        super.onPause();
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
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getId());
        mFields.putAllInto(book);
        Tracker.exitOnSaveFieldsToBook(this, book.getId());
    }
}
