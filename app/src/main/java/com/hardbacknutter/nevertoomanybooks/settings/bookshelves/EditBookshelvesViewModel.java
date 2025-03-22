/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.util.logger.LoggerFactory;

@SuppressWarnings("WeakerAccess")
public class EditBookshelvesViewModel
        extends ViewModel {

    private static final String TAG = "EditBookshelvesViewMode";

    /** Currently selected row. */
    private int selectedPosition = RecyclerView.NO_POSITION;

    /** The list we're editing. */
    private List<Bookshelf> list;

    /**
     * Stores the {@link Bookshelf} id we received when the fragment/vm got started.
     * We'll return it if there is no selected {@link Bookshelf} when the user taps 'back'
     */
    private long initialBookshelfId;

    private BookshelfDao bookshelfDao;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@Nullable final Bundle args) {
        if (bookshelfDao == null) {
            bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();

            list = bookshelfDao.getAll();
            if (args != null) {
                initialBookshelfId = args.getLong(DBKey.FK_BOOKSHELF);
                selectedPosition = findSelectedPosition(initialBookshelfId);
            }
        }
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

    /**
     * Get the currently selected {@link Bookshelf} id,
     * or the id we originally got when started.
     *
     * @return Bookshelf id
     */
    long getSelectedBookshelfId() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            return list.get(selectedPosition).getId();
        }
        return initialBookshelfId;
    }

    @NonNull
    Bookshelf getBookshelf(final int position) {
        return Objects.requireNonNull(list.get(position), () -> String.valueOf(position));
    }

    int getSelectedPosition() {
        return selectedPosition;
    }

    void setSelectedPosition(final int position) {
        selectedPosition = position;
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
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        bookshelfDao.fixId(context, bookshelf, locale);

        list.clear();
        list.addAll(bookshelfDao.getAll());
        selectedPosition = findSelectedPosition(bookshelf.getId());
    }

    /**
     * Delete the given {@link Bookshelf}.
     *
     * @param context   Current context
     * @param bookshelf to delete
     */
    void deleteBookshelf(@NonNull final Context context,
                         @NonNull final Bookshelf bookshelf) {
        bookshelfDao.delete(context, bookshelf);
        list.remove(bookshelf);
        selectedPosition = findSelectedPosition(initialBookshelfId);
    }

    /**
     * User explicitly wants to purge the node states for the given {@link Bookshelf}.
     *
     * @param bookshelf to purge
     */
    void purgeNodeStates(@NonNull final Bookshelf bookshelf) {
        try {
            bookshelfDao.purgeNodeStates(bookshelf);
        } catch (@NonNull final DaoWriteException e) {
            // ignore, but log it.
            LoggerFactory.getLogger().e(TAG, e);
        }
    }
}
