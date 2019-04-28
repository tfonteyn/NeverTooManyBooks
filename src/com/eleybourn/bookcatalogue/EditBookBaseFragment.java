package com.eleybourn.bookcatalogue;


import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Base class for all fragments that appear in {@link EditBookActivity}.
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
    @CallSuper
    protected void onLoadFieldsFromBook(final boolean setAllFrom) {
        super.onLoadFieldsFromBook(setAllFrom);

        // new book ?
        if (mBookModel.getBook().getId() == 0) {
            populateNewBookFieldsFromBundle(requireArguments());
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

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_HIDE_KEYBOARD, 0, R.string.menu_hide_keyboard)
            .setIcon(R.drawable.ic_keyboard_hide)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_HIDE_KEYBOARD) {
            //noinspection ConstantConditions
            Utils.hideKeyboard(getView());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Trigger the Fragment to save its Fields to the Book.
     * <p>
     * <p>{@inheritDoc}
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
     * <p>{@inheritDoc}
     * <p>
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
        mFields.putAllInto(mBookModel.getBook());
    }
}
