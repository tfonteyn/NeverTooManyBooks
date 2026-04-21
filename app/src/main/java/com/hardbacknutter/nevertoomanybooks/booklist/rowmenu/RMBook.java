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

package com.hardbacknutter.nevertoomanybooks.booklist.rowmenu;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.lender.EditLenderLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.menus.MenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.SiteSearchMenuHandler;
import com.hardbacknutter.nevertoomanybooks.menus.ViewBookOnSiteMenuHandler;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;

public class RMBook
        implements RowMenu {

    @NonNull
    private final BooksOnBookshelfViewModel vm;
    @NonNull
    private final ActivityResultLauncher<EditBookContract.Input> editBookLauncher;
    @NonNull
    private final ActivityResultLauncher<Book> updateBookLauncher;
    @NonNull
    private final EditLenderLauncher editLenderLauncher;
    @Nullable
    private final CalibreHandler calibreHandler;
    private final List<MenuHandler<DataHolder>> menuHandlers;

    public RMBook(@NonNull final BooksOnBookshelfViewModel vm,
                  @NonNull final ActivityResultLauncher<EditBookContract.Input> editBookLauncher,
                  @NonNull final ActivityResultLauncher<Book> updateBookLauncher,
                  @Nullable final CalibreHandler calibreHandler) {
        this.vm = vm;
        this.editBookLauncher = editBookLauncher;
        this.updateBookLauncher = updateBookLauncher;
        this.calibreHandler = calibreHandler;

        menuHandlers = List.of(new ViewBookOnSiteMenuHandler(),
                               new SiteSearchMenuHandler());

        editLenderLauncher = new EditLenderLauncher(vm::onBookLoaneeChanged);
    }

    @Override
    public void registerForFragmentResult(@NonNull final FragmentManager fm,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        editLenderLauncher.registerForFragmentResult(fm, lifecycleOwner);
    }

    @Override
    public void onCreateMenu(@NonNull final Context context,
                             @NonNull final MenuInflater menuInflater,
                             @NonNull final Menu menu,
                             @NonNull final DataHolder rowData) {
        menuInflater.inflate(R.menu.book, menu);

        // Always hide this for the book-row menu.
        // It is used only when we're in embedded mode in the book-details fragment itself.
        // Reason: we share the R.menu.books file
        menu.findItem(R.id.MENU_SYNC_LIST_WITH_DETAILS).setVisible(false);

        if (calibreHandler != null) {
            calibreHandler.onCreateMenu(menu, menuInflater);
        }
        menuHandlers.forEach(h -> h.onCreateMenu(context, menuInflater, menu, rowData));

        final boolean isRead = rowData.getBoolean(DBKey.READ__BOOL);
        menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
        menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);

        // specifically check LOANEE_NAME independent of the style in use.
        final boolean useLending = ServiceLocator.getInstance()
                                                 .isFieldEnabled(DBKey.LOANEE_NAME);
        final boolean isAvailable = vm.isAvailable(rowData);
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

        if (calibreHandler != null) {
            final Book book = Book.from(rowData.getLong(DBKey.FK_BOOK));
            calibreHandler.onPrepareMenu(context, menu, book);
        }

        menuHandlers.forEach(h -> h.onPrepareMenu(context, menu, rowData));
    }

    @Override
    public boolean onMenuItemSelected(@NonNull final Context context,
                                      final int menuItemId,
                                      @NonNull final DataHolder rowData,
                                      final int adapterPosition) {
        final long bookId = rowData.getLong(DBKey.FK_BOOK);
        vm.setSelectedBook(bookId, adapterPosition);

        if (menuItemId == R.id.MENU_BOOK_SET_READ
            || menuItemId == R.id.MENU_BOOK_SET_UNREAD) {
            // toggle the read status
            final boolean status = !rowData.getBoolean(DBKey.READ__BOOL);
            vm.setBookRead(bookId, status);
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_EDIT) {
            editBookLauncher.launch(new EditBookContract.Input(bookId, vm.getStyle()));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_DUPLICATE) {
            final Book book = Book.from(bookId);
            editBookLauncher.launch(new EditBookContract.Input(book.duplicate(context),
                                                               vm.getStyle()));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_DELETE) {
            final String title = rowData.getString(DBKey.TITLE);
            final List<Author> authors = vm.getAuthorsByBookId(bookId);
            StandardDialogs.deleteBook(context, title, authors, () -> vm.deleteBook(bookId));
            return true;

        } else if (menuItemId == R.id.MENU_UPDATE_ITEM_BY_SEARCH) {
            final Book book = Book.from(bookId);
            updateBookLauncher.launch(book);
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_LOAN_ADD) {
            editLenderLauncher.launch(context, bookId, rowData.getString(DBKey.TITLE));
            return true;

        } else if (menuItemId == R.id.MENU_BOOK_LOAN_DELETE) {
            vm.deleteLoan(bookId);
            return true;

        } else if (menuItemId == R.id.MENU_SHARE) {
            final Book book = Book.from(bookId);
            context.startActivity(book.getShareIntent(context, vm.getStyle()));
            return true;
        }

        if (calibreHandler != null) {
            final Book book = Book.from(bookId);
            return calibreHandler.onMenuItemSelected(context, menuItemId, book);
        }

        return menuHandlers.stream().anyMatch(
                h -> h.onMenuItemSelected(context, menuItemId, rowData));
    }
}
