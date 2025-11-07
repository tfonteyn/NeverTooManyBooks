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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public final class StandardDialogs {

    private StandardDialogs() {
    }

    /**
     * Show a dialog asking if the indicated change should be applied to all books,
     * or just the current book.
     *
     * @param context    Current context
     * @param itemType   The name of the type of object (e.g. 'Author', 'Publisher' ...)
     * @param original   entity label
     * @param modified   entity label
     * @param onAllBooks Runnable to execute if the user picks 'all books''
     * @param onThisBook Runnable to execute if the user picks 'this book''
     */
    public static void confirmScopeForChange(@NonNull final Context context,
                                             @NonNull final String itemType,
                                             @NonNull final String original,
                                             @NonNull final String modified,
                                             @NonNull final Runnable onAllBooks,
                                             @NonNull final Runnable onThisBook) {
        final String allBooks = context.getString(R.string.btn_all_books);
        final String thisBook = context.getString(R.string.btn_this_book);

        final String message = context.getString(R.string.confirm_scope_for_change,
                                                 original,
                                                 modified,
                                                 allBooks,
                                                 thisBook,
                                                 itemType);
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.lbl_scope_of_change)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, w) -> onAllBooks.run())
                .setPositiveButton(thisBook, (d, w) -> onThisBook.run())
                .create()
                .show();
    }

    /**
     * Show a dialog asking if unsaved edits should be ignored.
     * <p>
     * To show the Save and/or Exit button, you must provide a Runnable, even an empty one.
     *
     * @param context   Current context
     * @param onSave    (optional) Runnable to execute if the user clicks the Save button.
     * @param onDiscard (optional) Runnable to execute if the user clicks the Discard button.
     */
    public static void unsavedEdits(@NonNull final Context context,
                                    @Nullable final Runnable onSave,
                                    @Nullable final Runnable onDiscard) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.lbl_details_have_changed)
                .setMessage(R.string.confirm_unsaved_edits)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                .setNeutralButton(R.string.action_edit, (d, w) -> d.dismiss());

        if (onDiscard != null) {
            builder.setNegativeButton(R.string.action_discard, (d, w) -> onDiscard.run());
        }
        if (onSave != null) {
            builder.setPositiveButton(R.string.action_save, (d, w) -> onSave.run());
        }

        builder.show();
    }

    /**
     * Purge {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} for the given entity.
     *
     * @param context     Current context
     * @param label       resource string id for the type of entity
     * @param entityLabel the label of the entity
     * @param onConfirm   Runnable to execute if the user clicks the confirm button.
     */
    public static void purgeNodeStates(@NonNull final Context context,
                                       @StringRes final int label,
                                       @NonNull final String entityLabel,
                                       @NonNull final Runnable onConfirm) {

        final String msg = context.getString(R.string.info_purge_blns_item_name,
                                             context.getString(label),
                                             entityLabel);
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.lbl_purge_blns)
                .setMessage(msg)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.ok, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    public static void askToMerge(@NonNull final Context context,
                                  @StringRes final int mergeMessageResId,
                                  @NonNull final CharSequence title,
                                  @NonNull final Runnable onMerge) {

        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setTitle(title)
                .setMessage(mergeMessageResId)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_merge, (d, w) -> onMerge.run())
                .create()
                .show();
    }


    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param style     Style we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteStyle(@NonNull final Context context,
                                   @NonNull final Style style,
                                   @NonNull final Runnable onConfirm) {
        final String msg = context.getString(R.string.confirm_delete_style,
                                             style.getLabel(context));
        delete(context, onConfirm, msg);
    }

    private static void delete(@NonNull final Context context,
                               @NonNull final Runnable onConfirm,
                               @NonNull final CharSequence msg) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.warning_24px)
                .setTitle(R.string.action_delete)
                .setMessage(msg)
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
                .create()
                .show();
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context    Current context
     * @param tagMapping we're about to delete
     * @param onConfirm  Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteTagMapping(@NonNull final Context context,
                                        @NonNull final TagMapping tagMapping,
                                        final Runnable onConfirm) {
        final String msg = context.getString(R.string.confirm_delete_substitutions,
                                             tagMapping.getName());
        delete(context, onConfirm, msg);
    }


    /**
     * Ask the user to confirm a delete.
     *
     * @param context    Current context
     * @param title      Title of book we're about to delete
     * @param authorList Authors of book we're about to delete
     * @param onConfirm  Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteBook(@NonNull final Context context,
                                  @NonNull final String title,
                                  @NonNull final List<Author> authorList,
                                  @NonNull final Runnable onConfirm) {

        // Format the list of authors nicely
        final StringBuilder authors = new StringBuilder();
        if (authorList.isEmpty()) {
            authors.append(context.getString(R.string.unknown_author));

        } else {
            // "a1, a2 and a3"
            authors.append(authorList.get(0).getLabel(context));
            for (int i = 1; i < authorList.size() - 1; i++) {
                authors.append(", ").append(authorList.get(i).getLabel(context));
            }

            if (authorList.size() > 1) {
                authors.append(' ').append(context.getString(R.string.list_and)).append(' ')
                       .append(authorList.get(authorList.size() - 1).getLabel(context));
            }
        }

        final String msg = context.getString(R.string.confirm_delete_book, title, authors);
        delete(context, onConfirm, msg);
    }


    /**
     * Ask the user to confirm a delete.
     * <ul>
     *   <li>Prevents deleting when it's the only bookshelf present.</li>
     *   <li>Warns and auto-promotes the 'oldest' bookshelf when the one
     *   to delete is the current default.</li>
     * </ul>
     * @param context   Current context
     * @param bookshelf Bookshelf we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteBookshelf(@NonNull final Context context,
                                       @NonNull final Bookshelf bookshelf,
                                       @NonNull final Runnable onConfirm) {
        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();

        // Create the base message with the name of the shelf and the number of books on it.
        final int books = bookshelfDao.countBooks(bookshelf);
        final String nrOfBooks = context.getResources().getQuantityString(R.plurals.n_books,
                                                                          books, books);
        String msg = context.getString(R.string.confirm_delete_bookshelf_from_x_books,
                                       bookshelf.getLabel(context),
                                       nrOfBooks,
                                       context.getString(R.string.bookshelf_all_books));

        // Check for this being the default shelf and/or if it can be deleted
        final long defShelfId = bookshelfDao.getDefault().getId();
        @Nullable
        final Bookshelf futureDefault;
        if (bookshelf.getId() == defShelfId) {
            // Find the smallest id (i.e. oldest added) which is not the current default.
            final long futureDefaultId = bookshelfDao.getAll()
                                                     .stream()
                                                     .map(Bookshelf::getId)
                                                     .sorted()
                                                     .filter(id -> id != defShelfId)
                                                     .findFirst()
                                                     .orElse(0L);

            if (futureDefaultId == 0) {
                // There is only a single shelf, and the user wants to delete it... sigh...
                new MaterialAlertDialogBuilder(context)
                        .setIcon(R.drawable.warning_24px)
                        .setMessage(R.string.warning_cannot_delete_only_bookshelf)
                        .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                        .create()
                        .show();
                return;
            }

            futureDefault = bookshelfDao.getBookshelf(context, futureDefaultId).orElseThrow();

            // Deletion is allowed.
            // Prefix with an extra warning that the default shelf will be changed.
            msg = context.getString(R.string.warning_delete_default_bookshelf,
                                    futureDefault.getLabel(context),
                                    context.getString(R.string.lbl_bookshelves))
                  + "\n\n" + msg;
        } else {
            futureDefault = null;
        }

        delete(context, () -> {
            if (futureDefault != null) {
                bookshelfDao.setDefault(futureDefault);
            }
            onConfirm.run();
        }, msg);
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param series    Series we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteSeries(@NonNull final Context context,
                                    @NonNull final Series series,
                                    @NonNull final Runnable onConfirm) {
        final int books = ServiceLocator.getInstance().getSeriesDao().countBooks(series);
        final String nrOfBooks = context.getResources().getQuantityString(R.plurals.n_books,
                                                                          books, books);

        final String msg = context.getString(R.string.confirm_delete_series_from_x_books,
                                             series.getLabel(context),
                                             nrOfBooks);
        delete(context, onConfirm, msg);
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param publisher Publisher we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deletePublisher(@NonNull final Context context,
                                       @NonNull final Publisher publisher,
                                       @NonNull final Runnable onConfirm) {
        final int books = ServiceLocator.getInstance().getPublisherDao().countBooks(publisher);
        final String nrOfBooks = context.getResources().getQuantityString(R.plurals.n_books,
                                                                          books, books);

        final String msg = context.getString(R.string.confirm_delete_publisher_from_x_books,
                                             publisher.getLabel(context),
                                             nrOfBooks);
        delete(context, onConfirm, msg);
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param tocEntry  TocEntry we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteTocEntry(@NonNull final Context context,
                                      @NonNull final TocEntry tocEntry,
                                      @NonNull final Runnable onConfirm) {
        final int books = ServiceLocator.getInstance().getTocEntryDao().countBooks(tocEntry);
        final String nrOfBooks = context.getResources().getQuantityString(R.plurals.n_books,
                                                                          books, books);

        final String msg = context.getString(R.string.confirm_delete_toc_entry_from_x_books,
                                             tocEntry.getTitle(),
                                             tocEntry.getPrimaryAuthor().getLabel(context),
                                             nrOfBooks);
        delete(context, onConfirm, msg);
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context   Current context
     * @param tag       we're about to delete
     * @param onConfirm Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteTag(@NonNull final Context context,
                                 @NonNull final Tag tag,
                                 @NonNull final Runnable onConfirm) {
        final int books = ServiceLocator.getInstance().getTagDao().countBooks(tag);
        final String nrOfBooks = context.getResources().getQuantityString(R.plurals.n_books,
                                                                          books, books);
        final String msg = context.getString(R.string.confirm_delete_tag_from_x_books,
                                             tag.getName(),
                                             nrOfBooks);
        delete(context, onConfirm, msg);
    }

    /**
     * Ask the user to confirm a delete.
     *
     * @param context    Current context
     * @param identifier we're about to delete
     * @param onConfirm  Runnable to execute if the user clicks the confirm button.
     */
    public static void deleteIdentifier(@NonNull final Context context,
                                        @NonNull final Identifier identifier,
                                        @NonNull final Runnable onConfirm) {
        final int books = ServiceLocator.getInstance().getBookIdentifierDao()
                                        .countLinks(identifier);
        final String nrOfBooks = context.getResources().getQuantityString(R.plurals.n_books,
                                                                          books, books);
        final String msg = context.getString(R.string.confirm_delete_identifier_from_x_books,
                                             identifier.getName(),
                                             nrOfBooks);
        delete(context, onConfirm, msg);
    }
}
