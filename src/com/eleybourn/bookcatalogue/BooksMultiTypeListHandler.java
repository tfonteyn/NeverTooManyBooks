/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListHandler;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListRowHolder;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKind;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistSupportProvider;
import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditAuthorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditFormatDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditGenreDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditLanguageDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditLocationDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditPublisherDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditSeriesDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.LendBookDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonSearchPage;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.ZoomedImageDialogFragment;

/**
 * Handles all views in a multi-type ListView showing books, authors, series etc.
 * <p>
 * Each row(level) needs to have a layout like:
 * <layout id="@id/ROW_INFO">
 * <TextView id="@id/name" />
 * ... more fields...
 * </layout>
 * <p>
 * ROW_INFO is important, as it's that one that gets shown/hidden when needed.
 *
 * @author Philip Warner
 */
public class BooksMultiTypeListHandler
        implements MultiTypeListHandler {

    @NonNull
    private final DBA mDb;

    /**
     * Constructor.
     *
     * @param db the database
     */
    public BooksMultiTypeListHandler(@NonNull final DBA db) {
        mDb = db;
    }

    @Override
    public int getItemViewType(@NonNull final Cursor cursor) {
        BooklistCursorRow row = ((BooklistSupportProvider) cursor).getCursorRow();
        return row.getRowKind();
    }

    @Override
    public int getViewTypeCount() {
        return BooklistGroup.RowKind.size();
    }

    /**
     * Get the text to display in the ListView for the row at the current cursor position.
     * <p>
     * called by {@link MultiTypeListCursorAdapter#getSectionTextForPosition(int)}}
     *
     * @param cursor Cursor, positioned at current row
     *
     * @return the section text as an array with all levels in order
     */
    @Nullable
    @Override
    public String[] getSectionText(@NonNull final Cursor cursor) {
        BooklistCursorRow row = ((BooklistSupportProvider) cursor).getCursorRow();
        return new String[]{row.getLevelText(1), row.getLevelText(2)};
    }

    /**
     * @return the *absolute* position of the passed view in the list of books.
     */
    int getAbsolutePosition(@NonNull final View v) {
        final RowViewHolder holder = (RowViewHolder) v.getTag();
        Objects.requireNonNull(holder);
        return holder.absolutePosition;
    }

    /**
     * Scale text in a View (and children) as per user preferences.
     *
     * @param scale to use, with 1.0f no scaling.
     * @param row   the data
     * @param root  the view (and its children) we'll scale
     */
    private void scaleView(final float scale,
                           @SuppressWarnings("unused") @NonNull final BooklistCursorRow row,
                           @NonNull final View root) {

        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            float px = textView.getTextSize();
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * scale);

        }
//        else if (root instanceof ImageView) {
            // experiments from the original code never worked.
            // Bottom line is that Android will scale *down* (i.e. image to big ? make it smaller)
            // but will NOT scale up to fill the provided space.
//        }

        root.setPadding((int) (scale * root.getPaddingLeft()),
                        (int) (scale * root.getPaddingTop()),
                        (int) (scale * root.getPaddingRight()),
                        (int) (scale * root.getPaddingBottom()));

        // go recursive if needed.
        if (root instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View v = viewGroup.getChildAt(i);
                scaleView(scale, row, v);
            }
        }
    }

    @Override
    @NonNull
    public View getView(@NonNull final Cursor cursor,
                        @NonNull final LayoutInflater inflater,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {

        final BooklistCursorRow row = ((BooklistSupportProvider) cursor).getCursorRow();
        final RowViewHolder holder;

        if (convertView == null) {
            holder = createHolder(row);
            convertView = holder.createView(row, inflater, parent);

            // Scale if necessary
            float scale = row.getStyle().getScale();
            if (scale != 1.0f) {
                scaleView(scale, row, convertView);
            }

            // Indent based on level; we assume rows of a given type only occur at the same level
            convertView.setPadding((row.getLevel() - 1) * 5, 0, 0, 0);

            holder.map(row, convertView);
            convertView.setTag(holder);

        } else {
            // recycling convertView
            holder = (RowViewHolder) convertView.getTag();
        }

        holder.absolutePosition = row.getAbsolutePosition();
        holder.set(row, convertView);
        return convertView;
    }

    @Nullable
    private String getAuthorFromRow(@NonNull final DBA db,
                                    @NonNull final BooklistCursorRow row) {
        if (row.hasAuthorId() && row.getAuthorId() > 0) {
            Author author = db.getAuthor(row.getAuthorId());
            if (author != null) {
                return author.getDisplayName();
            }

        } else if (row.getRowKind() == RowKind.BOOK) {
            List<Author> authors = db.getAuthorsByBookId(row.getBookId());
            if (!authors.isEmpty()) {
                return authors.get(0).getDisplayName();
            }
        }
        return null;
    }

    @Nullable
    private String getSeriesFromRow(@NonNull final DBA db,
                                    @NonNull final BooklistCursorRow row) {
        if (row.hasSeriesId() && row.getSeriesId() > 0) {
            Series series = db.getSeries(row.getSeriesId());
            if (series != null) {
                return series.getName();
            }
        } else if (row.getRowKind() == RowKind.BOOK) {
            ArrayList<Series> series = db.getSeriesByBookId(row.getBookId());
            if (!series.isEmpty()) {
                return series.get(0).getName();
            }
        }
        return null;
    }

    /**
     * Adds 'standard' menu options based on row type.
     *
     * @param row Row view pointing to current row for this context menu
     *
     * @see SimpleDialog.SimpleDialogMenuItem : can't use SubMenu.
     */
    void prepareListViewContextMenu(@NonNull final Menu /* in/out */ menu,
                                    @NonNull final BooklistCursorRow row) {
        menu.clear();

        int rowKind = row.getRowKind();
        switch (rowKind) {
            case RowKind.BOOK:
                if (row.isRead()) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, Menu.NONE, R.string.menu_set_unread)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, Menu.NONE, R.string.menu_set_read)
                        .setIcon(R.drawable.ic_check_box);
                }

                menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, Menu.NONE, R.string.menu_delete_book)
                    .setIcon(R.drawable.ic_delete);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, Menu.NONE, R.string.menu_edit_book)
                    .setIcon(R.drawable.ic_edit);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_NOTES, Menu.NONE, R.string.menu_edit_book_notes)
                    .setIcon(R.drawable.ic_note);

                if (Fields.isVisible(DBDefinitions.KEY_LOANEE)) {
                    boolean isAvailable = null == mDb.getLoaneeByBookId(row.getBookId());
                    if (isAvailable) {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_LOAN, Menu.NONE,
                                 R.string.menu_loan_lend_book)
                            .setIcon(R.drawable.ic_people);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_RETURNED, Menu.NONE,
                                 R.string.menu_loan_return_book)
                            .setIcon(R.drawable.ic_people);
                    }
                }

                menu.add(Menu.NONE, R.id.MENU_SHARE, Menu.NONE, R.string.menu_share_this)
                    .setIcon(R.drawable.ic_share);

                menu.add(Menu.NONE, R.id.MENU_BOOK_SEND_TO_GOODREADS, Menu.NONE,
                         R.string.gr_menu_send_to_goodreads)
                    .setIcon(R.drawable.ic_goodreads);
                break;

            case RowKind.AUTHOR:
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_DETAILS, Menu.NONE, R.string.menu_author_details)
                    .setIcon(R.drawable.ic_details);
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_EDIT, Menu.NONE, R.string.menu_edit_author)
                    .setIcon(R.drawable.ic_edit);
                if (row.isAuthorComplete()) {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, Menu.NONE, R.string.menu_set_incomplete)
                        .setIcon(R.drawable.ic_check_box);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, Menu.NONE, R.string.menu_set_complete)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                }
                break;

            case RowKind.SERIES:
                if (row.getSeriesId() != 0) {
                    menu.add(Menu.NONE, R.id.MENU_SERIES_DELETE, Menu.NONE, R.string.menu_delete_series)
                        .setIcon(R.drawable.ic_delete);
                    menu.add(Menu.NONE, R.id.MENU_SERIES_EDIT, Menu.NONE, R.string.menu_edit_series)
                        .setIcon(R.drawable.ic_edit);
                    if (row.isSeriesComplete()) {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, Menu.NONE,
                                 R.string.menu_set_incomplete)
                            .setIcon(R.drawable.ic_check_box);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, Menu.NONE,
                                 R.string.menu_set_complete)
                            .setIcon(R.drawable.ic_check_box_outline_blank);
                    }
                }
                break;

            case RowKind.PUBLISHER:
                if (!row.getPublisherName().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_PUBLISHER_EDIT, Menu.NONE, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case RowKind.LANGUAGE:
                if (!row.getLanguageCode().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT, Menu.NONE, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case RowKind.LOCATION:
                if (!row.getLocation().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT, Menu.NONE, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case RowKind.GENRE:
                if (!row.getGenre().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT, Menu.NONE, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            case RowKind.FORMAT:
                if (!row.getFormat().isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT, Menu.NONE, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "Unexpected rowKind=" + rowKind);
                break;
        }

        // add Amazon menus ?
        boolean hasAuthor = row.hasAuthorId() && row.getAuthorId() > 0;
        boolean hasSeries = row.hasSeriesId() && row.getSeriesId() > 0;
        if (hasAuthor || hasSeries) {
            SubMenu subMenu = MenuHandler.addAmazonSearchSubMenu(menu);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, hasAuthor);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES,
                                    hasAuthor && hasSeries);
            subMenu.setGroupVisible(R.id.MENU_AMAZON_BOOKS_IN_SERIES, hasSeries);
        }
    }

    /**
     * Handle the 'standard' menu items. If the passed activity implements
     * {@link BookChangedListener} then inform it when changes have been made.
     * <p>
     * ENHANCE: Consider using {@link LocalBroadcastManager} instead to all BooklistChangeListener
     *
     * @param menuItem Related MenuItem
     * @param db       DBA
     * @param row      Row view for affected cursor row
     * @param activity Calling Activity
     *
     * @return <tt>true</tt> if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                  @NonNull final DBA db,
                                  @NonNull final BooklistCursorRow row,
                                  @NonNull final FragmentActivity activity) {

        final long bookId = row.getBookId();

        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_DELETE:
                StandardDialogs.deleteBookAlert(
                        activity, db, bookId,
                        new TellActivity(activity, bookId, BookChangedListener.BOOK_WAS_DELETED));

                return true;

            case R.id.MENU_BOOK_READ:
                // toggle the read status
                if (Book.setRead(bookId, !row.isRead(), db)) {
                    new TellActivity(activity, bookId, BookChangedListener.BOOK_READ).run();
                }
                return true;

            case R.id.MENU_BOOK_EDIT: {
                Intent intent = new Intent(activity, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, bookId)
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_NOTES: {
                Intent intent = new Intent(activity, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, bookId)
                        .putExtra(EditBookFragment.REQUEST_BKEY_TAB,
                                  EditBookFragment.TAB_EDIT_NOTES);
                activity.startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
                return true;
            }

            case R.id.MENU_BOOK_EDIT_LOAN:
                LendBookDialogFragment.show(activity.getSupportFragmentManager(),
                                            bookId, row.getAuthorId(), row.getTitle());
                return true;

            case R.id.MENU_BOOK_LOAN_RETURNED:
                mDb.deleteLoan(bookId);
                new TellActivity(activity, bookId, BookChangedListener.BOOK_LOANEE).run();
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_SHARE:
                Book book = new Book(bookId, db);
                activity.startActivity(Intent.createChooser(book.getShareBookIntent(activity),
                                                            activity.getString(
                                                                    R.string.menu_share_this)));
                return true;

            case R.id.MENU_BOOK_SEND_TO_GOODREADS:
                //TEST sendOneBook
                GoodreadsUtils.sendOneBook(activity, bookId);
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT:
                //noinspection ConstantConditions
                EditSeriesDialogFragment.show(activity.getSupportFragmentManager(),
                                              db.getSeries(row.getSeriesId()));
                return true;

            case R.id.MENU_SERIES_COMPLETE:
                // toggle the complete status
                if (Series.setComplete(db, row.getSeriesId(),
                                       !row.isSeriesComplete())) {
                    new TellActivity(activity, 0, BookChangedListener.SERIES).run();
                }
                return true;

            case R.id.MENU_SERIES_DELETE: {
                Series series = db.getSeries(row.getSeriesId());
                if (series != null) {
                    StandardDialogs.deleteSeriesAlert(
                            activity, db, series,
                            new TellActivity(activity, 0, BookChangedListener.SERIES));
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_AUTHOR_DETAILS: {
                Intent intent = new Intent(activity, AuthorWorksActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, row.getAuthorId());
                activity.startActivity(intent);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT:
                //noinspection ConstantConditions
                EditAuthorDialogFragment.show(activity.getSupportFragmentManager(),
                                              db.getAuthor(row.getAuthorId()));
                return true;

            case R.id.MENU_AUTHOR_COMPLETE:
                // toggle the complete status
                if (Author.setComplete(db, row.getAuthorId(), !row.isAuthorComplete())) {
                    new TellActivity(activity, 0, BookChangedListener.AUTHOR).run();
                }
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT:
                EditPublisherDialogFragment.show(activity.getSupportFragmentManager(),
                                                 new Publisher(row.getPublisherName()));
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT:
                new EditFormatDialog(activity, db,
                                     new TellActivity(activity, 0, BookChangedListener.FORMAT))
                        .edit(row.getFormat());
                return true;

            case R.id.MENU_GENRE_EDIT:
                new EditGenreDialog(activity, db,
                                    new TellActivity(activity, 0, BookChangedListener.GENRE))
                        .edit(row.getGenre());
                return true;

            case R.id.MENU_LANGUAGE_EDIT:
                new EditLanguageDialog(activity, db,
                                       new TellActivity(activity, 0, BookChangedListener.LANGUAGE))
                        .edit(row.getLanguageCode());
                return true;

            case R.id.MENU_LOCATION_EDIT:
                new EditLocationDialog(activity, db,
                                       new TellActivity(activity, 0, BookChangedListener.LOCATION))
                        .edit(row.getLocation());
                return true;

            /* ********************************************************************************** */

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonSearchPage.open(activity, getAuthorFromRow(db, row), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonSearchPage.open(activity, null, getSeriesFromRow(db, row));
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonSearchPage.open(activity,
                                      getAuthorFromRow(db, row), getSeriesFromRow(db, row));
                return true;

            default:
                return false;
        }
    }

    /**
     * Return a 'Holder' object for the row pointed to by row.
     *
     * @param row Points to the current row in the cursor.
     *
     * @return a 'Holder' object for the row pointed to by row.
     */
    private RowViewHolder createHolder(@NonNull final BooklistCursorRow row) {

        final RowKind rowKind = RowKind.get(row.getRowKind());

        switch (rowKind.getKind()) {
            // NEWKIND: ROW_KIND_x

            case RowKind.BOOK:
                return new BookHolder(mDb);

            case RowKind.AUTHOR:
                return new CheckableStringHolder(row, rowKind.getDisplayDomain().name,
                                                 DBDefinitions.DOM_AUTHOR_IS_COMPLETE.name,
                                                 R.string.field_not_set_with_brackets);

            case RowKind.SERIES:
                return new CheckableStringHolder(row, rowKind.getDisplayDomain().name,
                                                 DBDefinitions.DOM_SERIES_IS_COMPLETE.name,
                                                 R.string.field_not_set_with_brackets);

            // plain old Strings
            case RowKind.TITLE_LETTER:
            case RowKind.PUBLISHER:
            case RowKind.GENRE:
            case RowKind.FORMAT:
            case RowKind.LOCATION:
            case RowKind.LOANED:
            case RowKind.BOOKSHELF:
            case RowKind.DATE_PUBLISHED_YEAR:
            case RowKind.DATE_FIRST_PUBLICATION_YEAR:
            case RowKind.DATE_ACQUIRED_YEAR:
            case RowKind.DATE_ACQUIRED_DAY:
            case RowKind.DATE_ADDED_YEAR:
            case RowKind.DATE_ADDED_DAY:
            case RowKind.DATE_READ_YEAR:
            case RowKind.DATE_READ_DAY:
            case RowKind.DATE_LAST_UPDATE_YEAR:
            case RowKind.DATE_LAST_UPDATE_DAY:
                return new GenericStringHolder(row, rowKind.getDisplayDomain().name,
                                               R.string.field_not_set_with_brackets);

            // Months are displayed by name
            case RowKind.DATE_PUBLISHED_MONTH:
            case RowKind.DATE_FIRST_PUBLICATION_MONTH:
            case RowKind.DATE_ACQUIRED_MONTH:
            case RowKind.DATE_ADDED_MONTH:
            case RowKind.DATE_READ_MONTH:
            case RowKind.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(row, rowKind.getDisplayDomain().name,
                                       R.string.field_not_set_with_brackets);

            // some special formatting holders
            case RowKind.RATING:
                return new RatingHolder(row, rowKind.getDisplayDomain().name,
                                        R.string.field_not_set_with_brackets);
            case RowKind.LANGUAGE:
                return new LanguageHolder(row, rowKind.getDisplayDomain().name,
                                          R.string.field_not_set_with_brackets);
            case RowKind.READ_STATUS:
                return new ReadUnreadHolder(row, rowKind.getDisplayDomain().name,
                                            R.string.field_not_set_with_brackets);

            default:
                throw new IllegalTypeException("" + rowKind.getKind());
        }
    }

    /**
     * Runnable used to tell the activity that a field (or set of) has changed.
     */
    private static class TellActivity
            implements Runnable {

        private final Activity mActivity;
        private final int mFields;
        private final long mBookId;

        /**
         * Constructor.
         *
         * @param activity to tell
         * @param bookId   single book id, or 0 for all
         * @param fields   that changed
         */
        TellActivity(@NonNull final Activity activity,
                     final long bookId,
                     final int fields) {
            mActivity = activity;
            mBookId = bookId;
            mFields = fields;
        }

        @Override
        public void run() {
            // Let the Activity know
            if (mActivity instanceof BookChangedListener) {
                final BookChangedListener bcl = (BookChangedListener) mActivity;
                bcl.onBookChanged(mBookId, mFields, null);
            }
        }
    }

    /**
     * Background task to get 'extra' details for a book row.
     * Doing this in a background task keeps the booklist cursor simple and small.
     */
    private static class GetBookExtrasTask
            extends AsyncTask<Void, Void, Boolean> {

        // bitmask to '&' with covering all bits in use.
        static final int HANDLED = BooklistStyle.EXTRAS_AUTHOR
                | BooklistStyle.EXTRAS_LOCATION
                | BooklistStyle.EXTRAS_PUBLISHER
                | BooklistStyle.EXTRAS_FORMAT
                | BooklistStyle.EXTRAS_BOOKSHELVES;

        /** Bookshelves resource string. */
        @NonNull
        private final String mShelvesLabel;
        /** Location resource string. */
        @NonNull
        private final String mLocationLabel;
        /** Format string (not allowed to cache the context, so get in constructor). */
        @NonNull
        private final String mA_bracket_b_bracket;
        /** Format string (not allowed to cache the context, so get in constructor). */
        @NonNull
        private final String mName_colon_value;

        /** The filled-in view holder for the book view. */
        @NonNull
        private final BookHolder mHolder;
        /** The book ID to fetch. */
        private final long mBookId;
        /** Bitmask indicating which extras to get. */
        private final int mExtras;
        /** Locale to use for formatting. */
        private final Locale mLocale;
        /** Resulting location data. */
        private String mLocation;
        /** Resulting publisher data. */
        private String mPublisher;
        /** Resulting Format data. */
        private String mFormat;
        /** Resulting author data. */
        private String mAuthor;
        /** Resulting shelves data. */
        private String mShelves;

        /**
         * Constructor.
         *
         * @param context caller context
         * @param bookId  Book to fetch
         * @param holder  View holder of view for the book
         * @param extras  bitmap flags indicating which extras to get.
         */
        @UiThread
        GetBookExtrasTask(@NonNull final Context context,
                          final long bookId,
                          @NonNull final BookHolder holder,
                          final int extras) {
            if ((extras & HANDLED) == 0) {
                throw new IllegalArgumentException("GetBookExtrasTask called for unhandled extras");
            }

            mHolder = holder;
            mBookId = bookId;
            mExtras = extras;
            mLocale = context.getResources().getConfiguration().locale;

            mA_bracket_b_bracket = context.getString(R.string.a_bracket_b_bracket);
            mName_colon_value = context.getString(R.string.name_colon_value);

            mShelvesLabel = context.getString(R.string.lbl_bookshelves);
            mLocationLabel = context.getString(R.string.lbl_location);
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {

            //ENHANCE: TEST: minimize the SQL behind this? performance?
            try (BookCursor cursor = mHolder.mDb.fetchBookById(mBookId)) {
                // Bail out if we don't have a book.
                if (!cursor.moveToFirst()) {
                    return false;
                }

                BookCursorRow row = cursor.getCursorRow();

                if ((mExtras & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    mAuthor = row.getPrimaryAuthorNameFormatted();
                }

                if ((mExtras & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    mLocation = row.getLocation();
                }

                if ((mExtras & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    mFormat = row.getFormat();
                }

                if ((mExtras & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                    mShelves = Bookshelf
                            .toDisplayString(mHolder.mDb.getBookshelvesByBookId(mBookId));
                }

                if ((mExtras & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                    mPublisher = row.getPublisherName();
                    String tmpPubDate = row.getDatePublished();
                    if (tmpPubDate.length() >= 4) {
                        mPublisher = String.format(mA_bracket_b_bracket, mPublisher,
                                                   DateUtils.toPrettyDate(mLocale, tmpPubDate));
                    }
                }
            } catch (NumberFormatException e) {
                Logger.error(this, e);
                return false;
            }
            return true;
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final Boolean result) {
            if (!result) {
                return;
            }

            synchronized (mHolder) {

                display(mHolder.authorView, BooklistStyle.EXTRAS_AUTHOR, mAuthor);
                display(mHolder.publisherView, BooklistStyle.EXTRAS_PUBLISHER, mPublisher);
                display(mHolder.formatView, BooklistStyle.EXTRAS_FORMAT, mFormat);

                display(mHolder.bookshelvesView, BooklistStyle.EXTRAS_BOOKSHELVES,
                        mShelvesLabel, mShelves);

                display(mHolder.locationView, BooklistStyle.EXTRAS_LOCATION,
                        mLocationLabel, mLocation);
            }
        }

        /**
         * Conditionally display 'text'.
         */
        private void display(@NonNull final TextView view,
                             final int extras,
                             @Nullable final String text) {
            if ((mExtras & extras) != 0 && text != null && !text.isEmpty()) {
                view.setText(text);
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        }

        /**
         * Conditionally display 'label: text'.
         */
        private void display(@NonNull final TextView view,
                             final int extras,
                             @NonNull final String label,
                             @Nullable final String text) {
            if ((mExtras & extras) != 0 && text != null && !text.isEmpty()) {
                view.setText(String.format(mName_colon_value, label, text));
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Base for all row 'holder' classes. Not much to it.
     */
    private abstract static class RowViewHolder
            implements MultiTypeListRowHolder<BooklistCursorRow> {

        /** Absolute position of this row. */
        int absolutePosition;
    }

    /**
     * Holder for a {@link RowKind#BOOK} row.
     */
    private static class BookHolder
            extends RowViewHolder {

        /** Database, needed to fetch the Extra fields. */
        private final DBA mDb;

        /** Pointer to the view that stores the related book field. */
        TextView titleView;
        /** Pointer to the view that stores the related book field. */
        TextView authorView;
        /** Pointer to the view that stores the related book field. */
        TextView bookshelvesView;
        /** Pointer to the view that stores the related book field. */
        TextView locationView;
        /** Pointer to the view that stores the related book field. */
        TextView publisherView;
        /** Pointer to the view that stores the related book field. */
        TextView formatView;
        /** Pointer to the view that stores the series number when it is a short piece of text. */
        TextView seriesNumView;
        /** Pointer to the view that stores the series number when it is a long piece of text. */
        TextView seriesNumLongView;
        /** The "I've read it" checkbox. */
        CompoundButton readView;
        /** Pointer to the view that stores the related book field. */
        private ImageView coverView;

        /**
         * Constructor.
         *
         * @param db the database.
         */
        BookHolder(@NonNull final DBA db) {
            mDb = db;
        }


        /**
         * The actual book entry.
         */
        @Override
        public View createView(@NonNull final BooklistCursorRow row,
                               @NonNull final LayoutInflater inflater,
                               @NonNull final ViewGroup parent) {
            // level==3; but all book rows have the same type of view.
            return inflater.inflate(R.layout.booksonbookshelf_row_book, parent, false);
        }


        /**
         * Mapping is done when a view was created, but NOT when it was recycled.
         * So do not rely on row *book data* here. Only use style etc...
         *
         * @param row         with style etc...
         * @param convertView of the row
         */
        @Override
        public void map(@NonNull final BooklistCursorRow row,
                        @NonNull final View convertView) {
            final BooklistStyle style = row.getStyle();
            final int extraFields = style.getExtraFieldsStatus();

            // always visible
            titleView = convertView.findViewById(R.id.title);

            // visibility is independent from actual data, so set here.
            readView = convertView.findViewById(R.id.read);
            readView.setVisibility(Fields.isVisible(DBDefinitions.KEY_READ) ? View.VISIBLE
                                                                            : View.GONE);

            // visibility is independent from actual data, so set here.
            coverView = convertView.findViewById(R.id.coverImage);
            if (Fields.isVisible(UniqueId.BKEY_COVER_IMAGE)
                    && (extraFields & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                coverView.setVisibility(View.VISIBLE);
                // apply the scale as set per booklist style.
//                coverView.setMaxHeight(style.getScaledCoverImageMaxSize(convertView.getContext()));

            } else {
                coverView.setVisibility(View.GONE);
            }

            // visibility depends on actual data
            seriesNumView = convertView.findViewById(R.id.series_num);
            seriesNumLongView = convertView.findViewById(R.id.series_num_long);

            // visibility depends on actual data, but we need to set initial visibility
            // as actual visibility is postponed to a background task.
            // in short: we'll hide them if empty.
            bookshelvesView = convertView.findViewById(R.id.shelves);
            bookshelvesView.setVisibility((extraFields & BooklistStyle.EXTRAS_BOOKSHELVES) != 0
                                          ? View.VISIBLE : View.GONE);
            authorView = convertView.findViewById(R.id.author);
            authorView.setVisibility((extraFields & BooklistStyle.EXTRAS_AUTHOR) != 0
                                     ? View.VISIBLE : View.GONE);
            locationView = convertView.findViewById(R.id.location);
            locationView.setVisibility((extraFields & BooklistStyle.EXTRAS_LOCATION) != 0
                                       ? View.VISIBLE : View.GONE);
            publisherView = convertView.findViewById(R.id.publisher);
            publisherView.setVisibility((extraFields & BooklistStyle.EXTRAS_PUBLISHER) != 0
                                        ? View.VISIBLE : View.GONE);
            formatView = convertView.findViewById(R.id.format);
            formatView.setVisibility((extraFields & BooklistStyle.EXTRAS_FORMAT) != 0
                                     ? View.VISIBLE : View.GONE);

            // The default is to indent all views based on the level, but with book covers on
            // the far left, it looks better if we 'out-dent' one step.
            int level = row.getLevel();
            if (level > 0) {
                --level;
            }
            convertView.setPadding(level * 5, 0, 0, 0);
        }

        @Override
        public void set(@NonNull final BooklistCursorRow row,
                        @NonNull final View view) {

            final BooklistStyle style = row.getStyle();
            final int extraFields = style.getExtraFieldsStatus();
            final int imageMaxSize = style.getScaledCoverImageMaxSize(view.getContext());

            // Title
            titleView.setText(row.getTitle());

            // Read
            if (Fields.isVisible(DBDefinitions.KEY_READ)) {
                readView.setChecked(row.isRead());
            }

            // Series number
            if (Fields.isVisible(DBDefinitions.KEY_SERIES)
                    && row.hasSeriesNumber()) {

                String number = row.getSeriesNumber();
                if (number != null && !number.isEmpty()) {
                    // Display it in one of the views, based on the size of the text.
                    if (number.length() > 4) {
                        seriesNumView.setVisibility(View.GONE);
                        seriesNumLongView.setText(number);
                        seriesNumLongView.setVisibility(View.VISIBLE);
                    } else {
                        seriesNumView.setText(number);
                        seriesNumView.setVisibility(View.VISIBLE);
                        seriesNumLongView.setVisibility(View.GONE);
                    }
                } else {
                    seriesNumView.setVisibility(View.GONE);
                    seriesNumLongView.setVisibility(View.GONE);
                }
            } else {
                seriesNumView.setVisibility(View.GONE);
                seriesNumLongView.setVisibility(View.GONE);
            }

            if (Fields.isVisible(UniqueId.BKEY_COVER_IMAGE)
                    && (extraFields & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                // store the uuid for use in the onClick
                coverView.setTag(R.id.TAG_UUID, row.getBookUuid());

                ImageUtils.setImageView(coverView,
                                        row.getBookUuid(),
                                        imageMaxSize, imageMaxSize
                );

                //Allow zooming by clicking on the image
                coverView.setOnClickListener(this::showZoomedImage);
            }

            // Build the flags indicating which extras to get.
            int flags = extraFields & GetBookExtrasTask.HANDLED;

            // If there are extras to get, start a background task.
            if (flags != 0) {
                // Fill in the extras field as blank initially.
                bookshelvesView.setText("");
                locationView.setText("");
                publisherView.setText("");
                formatView.setText("");
                authorView.setText("");
                // Queue the task.
                new GetBookExtrasTask(view.getContext(), row.getBookId(), this, flags).execute();
            }
        }

        private void showZoomedImage(@NonNull final View v) {
            FragmentActivity activity = (FragmentActivity) v.getContext();
            String uuid = (String) v.getTag(R.id.TAG_UUID);
            ZoomedImageDialogFragment.show(activity.getSupportFragmentManager(),
                                           StorageUtils.getCoverFile(uuid));
        }
    }


    /**
     * Holder to handle any field that can be displayed as a simple string.
     * Assumes there is a 'name' TextView and an optional enclosing ViewGroup called ROW_INFO.
     */
    public static class GenericStringHolder
            extends RowViewHolder {

        /** Index of related data column. */
        final int mSourceCol;
        /** String ID to use when data is blank. */
        @StringRes
        final int mNoDataId;

        /*** View to populate. */
        @SuppressWarnings("NullableProblems")
        @NonNull
        TextView mTextView;
        /** Pointer to the container of all info for this row. */
        @SuppressWarnings("NullableProblems")
        @NonNull
        private View mRowDetailsView;
        /**
         * (optional) Pointer to the constraint group that controls visibility of all widgets
         * inside a ViewGroup. Used with ConstraintLayout only.
         */
        @Nullable
        private View mVisibilityControlView;

        /**
         * Constructor.
         *
         * @param row      Row view that represents a typical row of this kind.
         * @param source   Column name to use
         * @param noDataId String ID to use when data is blank
         */
        GenericStringHolder(@NonNull final BooklistCursorRow row,
                            @NonNull final String source,
                            @StringRes final int noDataId) {
            mSourceCol = row.getColumnIndex(source);
            if (mSourceCol < 0) {
                throw new ColumnNotPresentException(source);
            }
            mNoDataId = noDataId;
        }

        /**
         * Used to get the 'default' layout to use for differing row levels.
         *
         * @param level Level of layout
         *
         * @return Layout ID
         */
        @LayoutRes
        private int getDefaultLayoutId(final int level) {
            switch (level) {
                case 1:
                    // top-level uses a larger font
                    return R.layout.booksonbookshelf_row_level_1;
                case 2:
                    // second level uses a smaller font
                    return R.layout.booksonbookshelf_row_level_2;
                default:
                    // this is in fact either level 3 or 4 for non-Book rows; uses a smaller font
                    return R.layout.booksonbookshelf_row_level_3;
            }
        }

        @Override
        public View createView(@NonNull final BooklistCursorRow row,
                               @NonNull final LayoutInflater inflater,
                               @NonNull final ViewGroup parent) {
            return inflater.inflate(getDefaultLayoutId(row.getLevel()), parent, false);
        }

        /**
         * Called at View construction time.
         */
        @Override
        public void map(@NonNull final BooklistCursorRow row,
                        @NonNull final View convertView) {
            mRowDetailsView = convertView.findViewById(R.id.BLB_ROW_DETAILS);
            mTextView = convertView.findViewById(R.id.name);
            // optional
            mVisibilityControlView = mRowDetailsView.findViewById(R.id.group);
        }

        @Override
        public void set(@NonNull final BooklistCursorRow row,
                        @NonNull final View view) {
            setText(row.getString(mSourceCol), row.getLevel());
        }

        public boolean isVisible() {
            return mRowDetailsView.getVisibility() == View.VISIBLE;
        }

        /**
         * For a simple row, just set the text (or hide it).
         *
         * @param textId String to display
         * @param level  for this row
         */
        public void setText(@StringRes final int textId,
                            @IntRange(from = 1, to = 2) final int level) {
            setText(mTextView.getContext().getString(textId), level);
        }

        /**
         * For a simple row, just set the text (or hide it).
         *
         * @param text  String to display; can be null or empty
         * @param level for this row
         */
        public void setText(@Nullable final String text,
                            @IntRange(from = 1, to = 2) final int level) {
            int visibility = View.VISIBLE;

            if (text != null && !text.isEmpty()) {
                // if we have text, show it.
                mTextView.setText(text);
            } else {
                // we don't have text, but...
                if (level == 1) {
                    // we never hide level 1 and show the place holder text instead.
                    mTextView.setText(mNoDataId);
                } else {
                    visibility = View.GONE;
                }
            }

            mRowDetailsView.setVisibility(visibility);

            /*
                this is really annoying: setting visibility of the ConstraintLayout to GONE
                does NOT shrink it to size zero. You're forced to set all widgets inside also.
                Potentially this could be solved by fiddling with the constraints more.
            */
            if (mVisibilityControlView != null) {
                mVisibilityControlView.setVisibility(visibility);
            }
        }
    }

    /**
     * Holder for a row that displays a 'rating'.
     */
    private static class RatingHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param row      Row view that represents a typical row of this kind.
         * @param source   Column name to use
         * @param noDataId String ID to use when data is blank
         */
        RatingHolder(@NonNull final BooklistCursorRow row,
                     @NonNull final String source,
                     @StringRes final int noDataId) {
            super(row, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistCursorRow row,
                        @NonNull final View view) {
            String s = row.getString(mSourceCol);
            if (s != null) {
                try {
                    int i = (int) Float.parseFloat(s);
                    // If valid, format the description
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        s = view.getResources().getQuantityString(R.plurals.n_stars, i, i);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(this, e);
                }
            }
            setText(s, row.getLevel());
        }
    }

    /**
     * Holder for a row that displays a 'language'.
     */
    private static class LanguageHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param row      Row view that represents a typical row of this kind.
         * @param source   Column name to use
         * @param noDataId String ID to use when data is blank
         */
        LanguageHolder(@NonNull final BooklistCursorRow row,
                       @NonNull final String source,
                       @StringRes final int noDataId) {
            super(row, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistCursorRow row,
                        @NonNull final View view) {
            String s = row.getString(mSourceCol);
            if (s != null && !s.isEmpty()) {
                s = LocaleUtils.getDisplayName(view.getContext(), s);
            }
            setText(s, row.getLevel());
        }
    }

    /**
     * Holder for a row that displays a 'read/unread' (as text) status.
     */
    private static class ReadUnreadHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param row      Row view that represents a typical row of this kind.
         * @param source   Column name to use
         * @param noDataId String ID to use when data is blank
         */
        ReadUnreadHolder(@NonNull final BooklistCursorRow row,
                         @NonNull final String source,
                         @StringRes final int noDataId) {
            super(row, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistCursorRow row,
                        @NonNull final View view) {
            if (Datum.toBoolean(row.getString(mSourceCol), true)) {
                setText(R.string.lbl_read, row.getLevel());
            } else {
                setText(R.string.lbl_unread, row.getLevel());
            }
        }
    }

    /**
     * Holder for a row that displays a 'month'.
     * This code turns a month number into a locale-based month name.
     */
    private static class MonthHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param row      Row view that represents a typical row of this kind.
         * @param source   Column name to use
         * @param noDataId String ID to use when data is blank
         */
        MonthHolder(@NonNull final BooklistCursorRow row,
                    @NonNull final String source,
                    @StringRes final int noDataId) {
            super(row, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistCursorRow row,
                        @NonNull final View view) {
            String s = row.getString(mSourceCol);
            if (s != null) {
                Locale locale = LocaleUtils.from(view.getContext());
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        s = DateUtils.getMonthName(locale, i, false);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(this, e);
                }
            }
            setText(s, row.getLevel());
        }
    }

    /**
     * Holder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    private static class CheckableStringHolder
            extends GenericStringHolder {

        /** Index of related boolean column. */
        private final String mIsLockedSourceCol;

        /**
         * Constructor.
         *
         * @param row            Row view that represents a typical row of this kind.
         * @param source         Column name to use
         * @param isLockedSource Column name to use for the boolean 'lock' status
         * @param noDataId       String ID to use when data is blank
         */
        CheckableStringHolder(@NonNull final BooklistCursorRow row,
                              @NonNull final String source,
                              @NonNull final String isLockedSource,
                              @StringRes final int noDataId) {
            super(row, source, noDataId);
            mIsLockedSourceCol = isLockedSource;
        }

        @Override
        public void set(@NonNull final BooklistCursorRow row,
                        @NonNull final View view) {
            super.set(row, view);
            if (isVisible()) {
                Drawable lock = null;
                if (row.getBoolean(mIsLockedSourceCol)) {
                    lock = mTextView.getContext().getDrawable(R.drawable.ic_lock);
                }
                mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        null, null, lock, null);

            } else {
                mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        null, null, null, null);
            }
        }
    }
}
