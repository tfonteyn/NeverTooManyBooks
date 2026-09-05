/*
 * @Copyright 2018-2026 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings.bookshelves;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

@SuppressWarnings("WeakerAccess")
public class EditBookshelvesViewModel
        extends ViewModel {

    private static final String TAG = "EditBookshelvesViewMode";

    /**
     * Stores the {@link Bookshelf} id we received when the fragment/vm got started.
     * We'll return it if there is no selected {@link Bookshelf} when the user taps 'back'
     * <p>
     * Can be {@code 0}.
     */
    @IntRange(from = 0)
    private long initialBookshelfId;

    /** Was anything at all modified. */
    private boolean modified;

    /**
     * Currently selected {@link Bookshelf} id.
     * <p>
     * Can be {@code 0} when nothing is selected.
     */
    @IntRange(from = 0)
    private long selectedBookshelfId;

    /** The list we're editing. */
    private List<Bookshelf> list;

    private BookshelfDao bookshelfDao;

    /**
     * Pseudo constructor.
     *
     * @param initialBookshelfId the initial selected Bookshelf
     */
    void init(final long initialBookshelfId) {
        if (bookshelfDao == null) {
            bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
            list = bookshelfDao.getAll();

            this.initialBookshelfId = initialBookshelfId;
            selectedBookshelfId = this.initialBookshelfId;
        }
    }

    /**
     * Was anything at all modified.
     *
     * @return flag
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Find the position in the list of the {@link Bookshelf} with the given id.
     *
     * @param id of the bookshelf to find
     *
     * @return position; or {@link RecyclerView#NO_POSITION} if the bookshelf
     *         id is either invalid, or not present
     */
    private int findSelectedPosition(final long id) {
        if (id > 0) {
            for (int i = 0; i < list.size(); i++) {
                final Bookshelf bookshelf = list.get(i);
                if (bookshelf.getId() == id) {
                    return i;
                }
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @NonNull
    List<Bookshelf> getList() {
        // used directly by the adapter.
        return list;
    }

    @NonNull
    Bookshelf getBookshelf(final int position) {
        return Objects.requireNonNull(list.get(position), () -> String.valueOf(position));
    }

    @NonNull
    Bookshelf getDefaultBookshelf() {
        return bookshelfDao.getDefault();
    }

    void setDefaultBookshelf(@NonNull final Bookshelf bookshelf) {
        modified = true;
        bookshelfDao.setDefault(bookshelf);
    }

    /**
     * Get the currently selected {@link Bookshelf} id,
     * or the bookshelf id we initially where on when started
     * or the default bookshelf if the initial one was deleted.
     *
     * @return id, can be {@code 0}.
     */
    @IntRange(from = 0)
    long getSelectedBookshelfId() {
        return selectedBookshelfId;
    }

    int getSelectedPosition() {
        return findSelectedPosition(selectedBookshelfId);
    }

    void setSelectedPosition(final int position) {
        modified = true;
        selectedBookshelfId = list.get(position).getId();
    }

    /**
     * Called after a {@link Bookshelf} has been edited.
     * Reloads the entire list, and sets the edited row as the selected.
     *
     * @param context   Current context
     * @param bookshelf the modified Bookshelf
     */
    void onBookshelfEdited(@NonNull final Context context,
                           @NonNull final Bookshelf bookshelf) {
        modified = true;
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        bookshelfDao.fixId(context, bookshelf, locale);

        list.clear();
        list.addAll(bookshelfDao.getAll());
        selectedBookshelfId = bookshelf.getId();
    }

    /**
     * Delete the given {@link Bookshelf}.
     *
     * @param context   Current context
     * @param bookshelf to delete
     */
    void deleteBookshelf(@NonNull final Context context,
                         @NonNull final Bookshelf bookshelf) {
        modified = true;
        // preserve before deleting
        final long deletedId = bookshelf.getId();

        // delete first so that findSelectedPosition will get post-delete positions correct
        bookshelfDao.delete(context, bookshelf);
        list.remove(bookshelf);

        if (deletedId == initialBookshelfId) {
            // we've deleted the initially selected shelf,
            // select the default as the new initial
            initialBookshelfId = getDefaultBookshelf().getId();
        }

        if (deletedId == selectedBookshelfId) {
            // we've deleted the currently selected shelf,
            // reselect the initial shelf.
            selectedBookshelfId = initialBookshelfId;
        }
    }

    /**
     * User explicitly wants to purge the node states for the given {@link Bookshelf} id.
     *
     * @param bookshelfId id of the Bookshelf
     */
    void purgeNodeStates(final long bookshelfId) {
        // don't set the modified flag, don't care
        bookshelfDao.purgeNodeStates(bookshelfId);
    }
}
