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
 * {@link EditBookLoanedFragment}
 * {@link EditBookTOCFragment}
 */
public abstract class EditBookBaseFragment
        extends BookBaseFragment
        implements DataEditor {

    /**
     * Here we trigger the Fragment to save it's Fields to the Book.
     */
    @Override
    @CallSuper
    public void onPause() {
        // This is now done in onPause() since the view may have been deleted when this is called
        saveFieldsTo(getBookManager().getBook());
        super.onPause();
    }

    /**
     * This is 'final' because we want inheritors to implement {@link #onSaveFieldsToBook}
     * (just for consistency with the load process).
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
     */
    protected void onSaveFieldsToBook(@NonNull final Book book) {
        Tracker.enterOnSaveFieldsToBook(this, book.getId());
        mFields.putAllInto(book);
        Tracker.exitOnSaveFieldsToBook(this, book.getId());
    }

}
