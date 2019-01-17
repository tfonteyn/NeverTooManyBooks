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

import android.content.Intent;
import android.database.Cursor;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListHandler;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKind;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistSupportProvider;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.BooklistRowView;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditAuthorDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditFormatDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditGenreDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditLanguageDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditLocationDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditPublisherDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditSeriesDialog;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    /** Queue for tasks getting extra row details as necessary. */
    private static final SimpleTaskQueue ROW_DETAILS_TASK_QUEUE =
            new SimpleTaskQueue("BoB-GetBookExtrasTask", 1);

    /**
     * Return the row type for the current cursor position.
     */
    @Override
    public int getItemViewType(@NonNull final Cursor cursor) {
        BooklistRowView rowView = ((BooklistSupportProvider) cursor).getCursorRow();
        return rowView.getRowKind();
    }

    /**
     * Return the number of different View types in this list.
     */
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
        Tracker.enterFunction(this, "getSectionTextForPosition", cursor);
        BooklistRowView rowView = ((BooklistSupportProvider) cursor).getCursorRow();
        String[] st = new String[]{rowView.getLevel1Data(), rowView.getLevel2Data()};
        Tracker.exitFunction(this, "getSectionTextForPosition");
        return st;
    }

    /**
     * Return the *absolute* position of the passed view in the list of books.
     */
    int getAbsolutePosition(@NonNull final View v) {
        final BooklistHolder holder = ViewTagger.getTagOrThrow(v);
        return holder.absolutePosition;
    }

    private void scaleViewText(final float scale,
                               @SuppressWarnings("unused") @NonNull final BooklistRowView rowView,
                               @NonNull final View root) {

        if (root instanceof TextView) {
            TextView txt = (TextView) root;
            float px = txt.getTextSize();
            txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * scale);
        }
        /*
         * No matter what I tried, this particular piece of code does not seem to work.
         * All image scaling is moved to the relevant holder constructors until the
         * reason this code fails is understood.
         */
//        if (root instanceof ImageView) {
//            ImageView img = (ImageView) root;
//            if (img.getLongFromBundles() == R.id.read) {
//                img.setMaxHeight((int) (30 * BooklistStyle.SCALE));
//                img.setMaxWidth((int) (30 * BooklistStyle.SCALE));
//                Logger.info("SCALE READ");
//                img.requestLayout();
//            } else if (img.getLongFromBundles() == R.id.cover) {
//                img.setMaxHeight((int) (rowView.getMaxThumbnailHeight() * BooklistStyle.SCALE));
//                img.setMaxWidth((int) (rowView.getMaxThumbnailWidth() * BooklistStyle.SCALE));
//                LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
//                                (int) (rowView.getMaxThumbnailHeight() * BooklistStyle.SCALE));
//                img.setLayoutParams(lp);
//                img.requestLayout();
//                Logger.info("SCALE COVER");
//            } else {
//                Logger.info("UNKNOWN IMAGE");
//            }
//        }


        root.setPadding(
                (int) (scale * root.getPaddingLeft()),
                (int) (scale * root.getPaddingTop()),
                (int) (scale * root.getPaddingRight()),
                (int) (scale * root.getPaddingBottom()));

        root.getPaddingBottom();
        if (root instanceof ViewGroup) {
            ViewGroup grp = (ViewGroup) root;
            for (int i = 0; i < grp.getChildCount(); i++) {
                View v = grp.getChildAt(i);
                scaleViewText(scale, rowView, v);
            }
        }
    }

    /**
     * @param cursor      Cursor, positioned at current row
     * @param inflater    Inflater to use in case a new view resource must be expanded
     * @param convertView Pointer to reusable view of correct type (may be null)
     * @param parent      Parent view group
     *
     * @return the view for the correct level of the item.
     */
    @Override
    @NonNull
    public View getView(@NonNull final Cursor cursor,
                        @NonNull final LayoutInflater inflater,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {

        final BooklistRowView rowView = ((BooklistSupportProvider) cursor).getCursorRow();
        final BooklistHolder holder;
        final int level = rowView.getLevel();

        if (convertView == null) {
            holder = createHolder(rowView);
            convertView = holder.createView(rowView, inflater, parent, level);

            // Scale the text if necessary
            if (rowView.getStyle().getScaleSize() != 0f) {
                scaleViewText(rowView.getStyle().getScaleSize(), rowView, convertView);
            }

            convertView.setPadding((level - 1) * 5, 0, 0, 0);
            holder.map(rowView, convertView);
            ViewTagger.setTag(convertView, holder);
            // Indent based on level; we assume rows of a given type only occur at the same level
        } else {
            // recycling convertView
            holder = ViewTagger.getTagOrThrow(convertView);
        }

        holder.absolutePosition = rowView.getAbsolutePosition();
        holder.set(rowView, convertView, level);
        return convertView;
    }

    @Nullable
    private String getAuthorFromRow(@NonNull final DBA db,
                                    @NonNull final BooklistRowView rowView) {
        if (rowView.hasAuthorId() && rowView.getAuthorId() > 0) {
            Author author = db.getAuthor(rowView.getAuthorId());
            Objects.requireNonNull(author);
            return author.getDisplayName();

        } else if (rowView.getRowKind() == RowKind.BOOK) {
            List<Author> authors = db.getBookAuthorList(rowView.getBookId());
            if (authors.size() > 0) {
                return authors.get(0).getDisplayName();
            }
        }
        return null;
    }

    @Nullable
    private String getSeriesFromRow(@NonNull final DBA db,
                                    @NonNull final BooklistRowView rowView) {
        if (rowView.hasSeriesId() && rowView.getSeriesId() > 0) {
            Series s = db.getSeries(rowView.getSeriesId());
            if (s != null) {
                return s.name;
            }
        } else if (rowView.getRowKind() == RowKind.BOOK) {
            ArrayList<Series> series = db.getBookSeriesList(rowView.getBookId());
            if (series.size() > 0) {
                return series.get(0).name;
            }
        }
        return null;
    }

    /**
     * Adds 'standard' menu options based on row type.
     *
     * @param cursorRow Row view pointing to current row for this context menu
     *
     * @see SimpleDialog.SimpleDialogMenuItem : can't use SubMenu.
     */
    void prepareListViewContextMenu(@NonNull final Menu /* in/out */ menu,
                                    @NonNull final BooklistRowView cursorRow) {
        menu.clear();
        int rowKind = cursorRow.getRowKind();
        switch (rowKind) {
            case RowKind.BOOK: {
                if (cursorRow.isRead()) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_unread)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.menu_set_read)
                        .setIcon(R.drawable.ic_check_box);
                }

                menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete_book)
                    .setIcon(R.drawable.ic_delete);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit_book)
                    .setIcon(R.drawable.ic_edit);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_NOTES, 0, R.string.menu_edit_book_notes)
                    .setIcon(R.drawable.ic_note);

                if (Fields.isVisible(UniqueId.KEY_LOAN_LOANED_TO)) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_LOAN, 0, R.string.menu_edit_book_loan)
                        .setIcon(R.drawable.ic_people);
                }

                menu.add(Menu.NONE, R.id.MENU_SHARE, 0, R.string.share)
                    .setIcon(R.drawable.ic_share);

                menu.add(Menu.NONE, R.id.MENU_BOOK_SEND_TO_GOODREADS, 0,
                         R.string.gr_menu_send_to_goodreads)
                    .setIcon(BookCatalogueApp.getAttr(R.attr.ic_goodreads));
                break;
            }
            case RowKind.AUTHOR: {
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_DETAILS, 0, R.string.menu_author_details)
                    .setIcon(R.drawable.ic_details);
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_EDIT, 0, R.string.menu_edit_author)
                    .setIcon(R.drawable.ic_edit);
                if (cursorRow.isAuthorComplete()) {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_incomplete)
                        .setIcon(R.drawable.ic_check_box);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_AUTHOR_COMPLETE, 0, R.string.menu_set_complete)
                        .setIcon(R.drawable.ic_check_box_outline_blank);
                }
                break;
            }
            case RowKind.SERIES: {
                long id = cursorRow.getSeriesId();
                if (id != 0) {
                    menu.add(Menu.NONE, R.id.MENU_SERIES_DELETE, 0, R.string.menu_delete_series)
                        .setIcon(R.drawable.ic_delete);
                    menu.add(Menu.NONE, R.id.MENU_SERIES_EDIT, 0, R.string.menu_edit_series)
                        .setIcon(R.drawable.ic_edit);
                    if (cursorRow.isSeriesComplete()) {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_incomplete)
                            .setIcon(R.drawable.ic_check_box);
                    } else {
                        menu.add(Menu.NONE, R.id.MENU_SERIES_COMPLETE, 0,
                                 R.string.menu_set_complete)
                            .setIcon(R.drawable.ic_check_box_outline_blank);
                    }
                }
                break;
            }
            case RowKind.PUBLISHER: {
                String s = cursorRow.getPublisherName();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_PUBLISHER_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case RowKind.LANGUAGE: {
                String s = cursorRow.getLanguageCode();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case RowKind.LOCATION: {
                String s = cursorRow.getLocation();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case RowKind.GENRE: {
                String s = cursorRow.getGenre();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case RowKind.FORMAT: {
                String s = cursorRow.getFormat();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT, 0, R.string.menu_edit)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }

            default:
                Logger.error("Unexpected rowKind=" + rowKind);
                break;
        }

        // add search menus ?
        boolean hasAuthor = (cursorRow.hasAuthorId() && cursorRow.getAuthorId() > 0);
        boolean hasSeries = (cursorRow.hasSeriesId() && cursorRow.getSeriesId() > 0);
        if (hasAuthor || hasSeries) {
            SubMenu subMenu = menu.addSubMenu(R.string.menu_search);

            if (hasAuthor) {
                subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0,
                            R.string.menu_amazon_books_by_author)
                       .setIcon(R.drawable.ic_search);
            }

            // add search by series ?
            if (hasSeries) {
                if (hasAuthor) {
                    subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0,
                                R.string.menu_amazon_books_by_author_in_series)
                           .setIcon(R.drawable.ic_search);
                }
                subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0,
                            R.string.menu_amazon_books_in_series)
                       .setIcon(R.drawable.ic_search);
            }
        }
    }

    /**
     * Handle the 'standard' menu items. If the passed activity implements
     * BooklistChangeListener then inform it when changes have been made.
     * <p>
     * ENHANCE: Consider using {@link LocalBroadcastManager} instead to all BooklistChangeListener
     *
     * @param menuItem  Related MenuItem
     * @param db        DBA
     * @param cursorRow Row view for affected cursor row
     * @param activity  Calling Activity
     *
     * @return <tt>true</tt> if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                  @NonNull final DBA db,
                                  @NonNull final BooklistRowView cursorRow,
                                  @NonNull final FragmentActivity activity) {

        long bookId = cursorRow.getBookId();

        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_DELETE: {
                // Show the standard dialog
                @StringRes
                int result = StandardDialogs.deleteBookAlert(activity, db, bookId, new Runnable() {
                    @Override
                    public void run() {
                        // Let the activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(
                                    cursorRow.getStyle().getExtraFieldsStatus(),
                                    BooklistChangeListener.FLAG_AUTHOR
                                            | BooklistChangeListener.FLAG_SERIES);
                        }
                    }
                });
                // Display an error, if any
                if (result != 0) {
                    StandardDialogs.showUserMessage(activity, result);
                }
                return true;
            }
            case R.id.MENU_BOOK_READ: {
                // toggle the read status
                if (Book.setRead(db, bookId, !cursorRow.isRead())) {
                    // Let the activity know
                    if (activity instanceof BooklistChangeListener) {
                        final BooklistChangeListener listener = (BooklistChangeListener) activity;
                        listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                  BooklistChangeListener.FLAG_BOOK_READ,
                                                  cursorRow.getAbsolutePosition(), bookId);
                    }
                }
                return true;
            }
            case R.id.MENU_BOOK_EDIT: {
                Intent intent = new Intent(activity, EditBookActivity.class);
                intent.putExtra(UniqueId.KEY_ID, bookId);
                intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT);
                activity.startActivityForResult(intent, BooksOnBookshelf.REQ_BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_NOTES: {
                Intent intent = new Intent(activity, EditBookActivity.class);
                intent.putExtra(UniqueId.KEY_ID, bookId);
                intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT_NOTES);
                activity.startActivityForResult(intent, BooksOnBookshelf.REQ_BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_LOAN: {
                Intent intent = new Intent(activity, EditBookActivity.class);
                intent.putExtra(UniqueId.KEY_ID, bookId);
                intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB, EditBookFragment.TAB_EDIT_LOANS);
                activity.startActivityForResult(intent, BooksOnBookshelf.REQ_BOOK_EDIT);
                return true;
            }
            case R.id.MENU_SHARE: {
                Book book = Book.getBook(db, bookId);
                activity.startActivity(Intent.createChooser(book.getShareBookIntent(activity),
                                                            activity.getString(R.string.share)));
                return true;
            }

            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                //TEST sendOneBookToGoodreads
                GoodreadsUtils.sendOneBookToGoodreads(activity, bookId);
                return true;
            }
            case R.id.MENU_SERIES_EDIT: {
                long id = cursorRow.getSeriesId();
                Series s = db.getSeries(id);
                Objects.requireNonNull(s);
                EditSeriesDialog d = new EditSeriesDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                      BooklistChangeListener.FLAG_SERIES);
                        }
                    }
                });
                d.edit(s);
                return true;
            }
            case R.id.MENU_SERIES_COMPLETE: {
                // toggle the complete status
                if (Series.setComplete(db, cursorRow.getSeriesId(),
                                       !cursorRow.isSeriesComplete())) {
                    // Let the activity know
                    if (activity instanceof BooklistChangeListener) {
                        final BooklistChangeListener listener = (BooklistChangeListener) activity;
                        listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                  BooklistChangeListener.FLAG_SERIES,
                                                  cursorRow.getAbsolutePosition(), bookId);
                    }
                }
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                long id = cursorRow.getSeriesId();
                Series series = db.getSeries(id);
                if (series != null) {
                    StandardDialogs.deleteSeriesAlert(activity, db, series, new Runnable() {
                        @Override
                        public void run() {
                            // Let the Activity know
                            if (activity instanceof BooklistChangeListener) {
                                final BooklistChangeListener listener =
                                        (BooklistChangeListener) activity;
                                listener.onBooklistChange(
                                        cursorRow.getStyle().getExtraFieldsStatus(),
                                        BooklistChangeListener.FLAG_SERIES);
                            }
                        }
                    });
                } else {
                    Logger.error("Series " + id + " not found in database?");
                }
                return true;
            }
            case R.id.MENU_AUTHOR_DETAILS: {
                Intent intent = new Intent(activity, AuthorActivity.class);
                intent.putExtra(UniqueId.KEY_ID, cursorRow.getAuthorId());
                activity.startActivity(intent);
                return true;
            }
            case R.id.MENU_AUTHOR_EDIT: {
                Author author = db.getAuthor(cursorRow.getAuthorId());
                Objects.requireNonNull(author);
                EditAuthorDialog d = new EditAuthorDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                      BooklistChangeListener.FLAG_AUTHOR);
                        }
                    }
                });
                d.edit(author);
                return true;
            }
            case R.id.MENU_AUTHOR_COMPLETE: {
                // toggle the complete status
                if (Author.setComplete(db, cursorRow.getAuthorId(),
                                       !cursorRow.isAuthorComplete())) {
                    // Let the activity know
                    if (activity instanceof BooklistChangeListener) {
                        final BooklistChangeListener listener = (BooklistChangeListener) activity;
                        listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                  BooklistChangeListener.FLAG_AUTHOR,
                                                  cursorRow.getAbsolutePosition(), bookId);
                    }
                }
                return true;
            }
            case R.id.MENU_PUBLISHER_EDIT: {
                Publisher publisher = new Publisher(cursorRow.getPublisherName());
                EditPublisherDialog d = new EditPublisherDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                      BooklistChangeListener.FLAG_PUBLISHER);
                        }
                    }
                });
                d.edit(publisher);
                return true;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                String languageCode = cursorRow.getLanguageCode();
                EditLanguageDialog d = new EditLanguageDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                      BooklistChangeListener.FLAG_LANGUAGE);
                        }
                    }
                });
                d.edit(languageCode);
                return true;
            }
            case R.id.MENU_LOCATION_EDIT: {
                String location = cursorRow.getLocation();
                EditLocationDialog d = new EditLocationDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                      BooklistChangeListener.FLAG_LOCATION);
                        }
                    }
                });
                d.edit(location);
                return true;
            }
            case R.id.MENU_GENRE_EDIT: {
                String genre = cursorRow.getGenre();
                EditGenreDialog d = new EditGenreDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                      BooklistChangeListener.FLAG_GENRE);
                        }
                    }
                });
                d.edit(genre);
                return true;
            }
            case R.id.MENU_FORMAT_EDIT: {
                String format = cursorRow.getFormat();
                EditFormatDialog d = new EditFormatDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener =
                                    (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                                      BooklistChangeListener.FLAG_FORMAT);
                        }
                    }
                });
                d.edit(format);
                return true;
            }


            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR:
                AmazonUtils.openSearchPage(activity, getAuthorFromRow(db, cursorRow), null);
                return true;

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES:
                AmazonUtils.openSearchPage(activity, null, getSeriesFromRow(db, cursorRow));
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES:
                AmazonUtils.openSearchPage(activity, getAuthorFromRow(db, cursorRow),
                                           getSeriesFromRow(db, cursorRow));
                return true;

            default:
                return false;
        }
    }

    /**
     * Return a 'Holder' object for the row pointed to by rowView.
     *
     * @param rowView Points to the current row in the cursor.
     *
     * @return a 'Holder' object for the row pointed to by rowView.
     */
    private BooklistHolder createHolder(@NonNull final BooklistRowView rowView) {

        final RowKind rowKind = RowKind.get(rowView.getRowKind());

        switch (rowKind.getKind()) {
            // NEWKIND: ROW_KIND_x

            case RowKind.BOOK:
                return new BookHolder();

            case RowKind.AUTHOR:
                return new CheckableStringHolder(rowView, rowKind.getDisplayDomain().name,
                                                 DatabaseDefinitions.DOM_AUTHOR_IS_COMPLETE.name,
                                                 R.string.not_set_with_brackets);

            case RowKind.SERIES:
                return new CheckableStringHolder(rowView, rowKind.getDisplayDomain().name,
                                                 DatabaseDefinitions.DOM_SERIES_IS_COMPLETE.name,
                                                 R.string.not_set_with_brackets);

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
                return new GenericStringHolder(rowView, rowKind.getDisplayDomain().name,
                                               R.string.not_set_with_brackets);

            /* Months are displayed by name */
            case RowKind.DATE_PUBLISHED_MONTH:
            case RowKind.DATE_FIRST_PUBLICATION_MONTH:
            case RowKind.DATE_ACQUIRED_MONTH:
            case RowKind.DATE_ADDED_MONTH:
            case RowKind.DATE_READ_MONTH:
            case RowKind.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(rowView, rowKind.getDisplayDomain().name,
                                       R.string.not_set_with_brackets);

            /* some special formatting holders */
            case RowKind.RATING:
                return new RatingHolder(rowView, rowKind.getDisplayDomain().name,
                                        R.string.not_set_with_brackets);
            case RowKind.LANGUAGE:
                return new LanguageHolder(rowView, rowKind.getDisplayDomain().name,
                                          R.string.not_set_with_brackets);
            case RowKind.READ_STATUS:
                return new ReadUnreadHolder(rowView, rowKind.getDisplayDomain().name,
                                            R.string.not_set_with_brackets);

            default:
                throw new RTE.IllegalTypeException("" + rowKind.getKind());
        }
    }

    /**
     * Interface to be implemented by the Activity/Fragment that hosts a ListView
     * populated by a {@link com.eleybourn.bookcatalogue.booklist.BooklistBuilder}
     * <p>
     * Allows to be notified of changes made to objects in the list.
     */
    public interface BooklistChangeListener {

        int FLAG_AUTHOR = 1;
        int FLAG_SERIES = 1 << 1;

        int FLAG_FORMAT = 1 << 2;
        int FLAG_PUBLISHER = 1 << 3;
        int FLAG_LANGUAGE = 1 << 4;
        int FLAG_LOCATION = 1 << 5;
        int FLAG_GENRE = 1 << 6;
        int FLAG_BOOK_READ = 1 << 7;


        /**
         * Called if global changes were made that (potentially) affect the whole list.
         *
         * @param extraFieldsInUse a bitmask with the Extra fields being used (visible)
         *                         in the current {@link BooklistStyle} as configured by the user
         * @param fieldsChanged    a bitmask build from the flags of {@link BooklistChangeListener}
         */
        void onBooklistChange(final int extraFieldsInUse,
                              final int fieldsChanged);

        /**
         * Called if changes were made to a single book.
         *
         * @param extraFieldsInUse a bitmask with the Extra fields being used (visible)
         *                         in the current {@link BooklistStyle} as configured by the user
         * @param fieldsChanged    a bitmask build from the flags of {@link BooklistChangeListener}
         * @param rowPosition      the absolute position of the row in the cursor view
         * @param bookId           the book that was changed
         */
        void onBooklistChange(final int extraFieldsInUse,
                              final int fieldsChanged,
                              final int rowPosition,
                              final long bookId);
    }

    /**
     * Background task to get 'extra' details for a book row. Doing this in a
     * background task keeps the booklist cursor simple and small.
     *
     * @author Philip Warner
     */
    private static class GetBookExtrasTask
            implements SimpleTaskQueue.SimpleTask {

        static final int BKEY_HANDLED = BooklistStyle.EXTRAS_AUTHOR
                | BooklistStyle.EXTRAS_LOCATION
                | BooklistStyle.EXTRAS_PUBLISHER
                | BooklistStyle.EXTRAS_FORMAT
                | BooklistStyle.EXTRAS_BOOKSHELVES;

        /** Bookshelves resource string. */
        private final String mShelvesLabel =
                BookCatalogueApp.getResString(R.string.lbl_bookshelves);
        /** Location resource string. */
        private final String mLocationLabel = BookCatalogueApp.getResString(R.string.lbl_location);
        /** Publisher resource string. */
        private final String mPublisherLabel = BookCatalogueApp.getResString(
                R.string.lbl_publisher);
        /** Format resource string. */
        private final String mFormatLabel = BookCatalogueApp.getResString(R.string.lbl_format);

        /** The filled-in view holder for the book view. */
        @NonNull
        private final BookHolder mHolder;
        /** The book ID to fetch. */
        private final long mBookId;
        /** Flags indicating which extras to get. */
        private final int mFlags;
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
        /** Flag indicating we want finished() to be called. */
        private boolean mWantFinished = true;

        /**
         * Constructor.
         *
         * @param bookId Book to fetch
         * @param holder View holder of view for the book
         */
        GetBookExtrasTask(final long bookId,
                          @NonNull final BookHolder holder,
                          final int flags) {
            if ((flags & BKEY_HANDLED) == 0) {
                throw new IllegalArgumentException("GetBookExtrasTask called for unhandled extras");
            }

            mHolder = holder;
            mBookId = bookId;
            mFlags = flags;
            synchronized (mHolder) {
                mHolder.extrasTask = this;
            }
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            try {
                // Make sure we are the right task.
                synchronized (mHolder) {
                    if (mHolder.extrasTask != this) {
                        mWantFinished = false;
                        return;
                    }
                }
                // Get a DB connection and find the book, do not close the database!
                DBA db = taskContext.getDb();
                try (BookCursor cursor = db.fetchBookById(mBookId)) {
                    // If we have a book, use it. Otherwise we are done.
                    if (cursor.moveToFirst()) {
                        BookRowView rowView = cursor.getCursorRow();

                        if ((mFlags & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                            mAuthor = rowView.getPrimaryAuthorNameFormatted();
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_LOCATION) != 0) {
                            mLocation = rowView.getLocation();
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                            String tmpPublisher = rowView.getPublisherName();
                            String tmpPubDate = rowView.getDatePublished();

                            if (tmpPubDate != null && tmpPubDate.length() >= 4) {
                                mPublisher = BookCatalogueApp.getResString(
                                        R.string.a_bracket_b_bracket,
                                        tmpPublisher, DateUtils.toPrettyDate(tmpPubDate));
                            } else {
                                mPublisher = tmpPublisher;
                            }
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_FORMAT) != 0) {
                            mFormat = rowView.getFormat();
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                            mShelves = Bookshelf.toDisplayString(
                                    db.getBookshelvesByBookId(mBookId));
                        }
                    } else {
                        // No data, no need for UI thread call.
                        mWantFinished = false;
                    }
                }
            } finally {
                taskContext.setRequiresFinish(mWantFinished);
            }
        }

        /**
         * Handle the results of the task.
         */
        @Override
        public void onFinish(@Nullable final Exception e) {
            synchronized (mHolder) {
                if (mHolder.extrasTask != this) {
                    return;
                }

                if ((mFlags & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                    mHolder.shelves.setText(
                            BookCatalogueApp.getResString(R.string.name_colon_value,
                                                          mShelvesLabel,
                                                          mShelves));
                }
                if ((mFlags & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    mHolder.author.setText(mAuthor);
                }
                if ((mFlags & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    mHolder.location.setText(
                            BookCatalogueApp.getResString(R.string.name_colon_value,
                                                          mLocationLabel, mLocation));
                }
                if ((mFlags & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                    mHolder.publisher.setText(
                            BookCatalogueApp.getResString(R.string.name_colon_value,
                                                          mPublisherLabel, mPublisher));
                }
                if ((mFlags & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    mHolder.format.setText(
                            BookCatalogueApp.getResString(R.string.name_colon_value,
                                                          mFormatLabel,
                                                          mFormat));
                }
            }
        }
    }

    /**
     * Implementation of general code used by Booklist holders.
     *
     * @author Philip Warner
     */
    public abstract static class BooklistHolder
            extends MultiTypeHolder<BooklistRowView> {

        /** Pointer to the container of all info for this row. */
        View rowInfo;
        /** Absolute position of this row. */
        int absolutePosition;

        /**
         * Used to get the 'default' layout to use for differing row levels.
         *
         * @param level Level of layout
         *
         * @return Layout ID
         */
        @LayoutRes
        static int getDefaultLayoutId(final int level) {
            switch (level) {
                case 1:
                    // top-level uses a larger font
                    return R.layout.booksonbookshelf_row_level_1;
                case 2:
                    // second level uses a small font
                    return R.layout.booksonbookshelf_row_level_2;
                default:
                    // this is in fact either level 3 or 4 for non-Book rows; uses a small font
                    return R.layout.booksonbookshelf_row_level_3;
            }
        }

        /**
         * For a simple row, just set the text (or hide it).
         *
         * @param view          View to set
         * @param text          String to display; can be null or empty'
         * @param emptyStringId String to display if 'text' is not set, and the row is not hidden
         * @param level         Level of this item (we never hide level 1 items).
         */
        public void setText(@NonNull final TextView view,
                            @Nullable final String text,
                            @StringRes final int emptyStringId,
                            final int level) {
            if (text == null || text.isEmpty()) {
                if (level > 1 && rowInfo != null) {
                    rowInfo.setVisibility(View.GONE);
                    return;
                }
                view.setText(BookCatalogueApp.getResString(emptyStringId));
            } else {
                if (rowInfo != null) {
                    rowInfo.setVisibility(View.VISIBLE);
                }
                view.setText(text);
            }
        }
    }

    /**
     * Holder for a {@link RowKind#BOOK} row.
     *
     * @author Philip Warner
     */
    public static class BookHolder
            extends BooklistHolder {

        /** Pointer to the view that stores the related book field. */
        TextView title;
        /** Pointer to the view that stores the related book field. */
        TextView author;
        /** Pointer to the view that stores the related book field. */
        TextView shelves;
        /** Pointer to the view that stores the related book field. */
        TextView location;
        /** Pointer to the view that stores the related book field. */
        TextView publisher;
        /** Pointer to the view that stores the related book field. */
        TextView format;
        /** Pointer to the view that stores the related book field. */
        ImageView cover;
        /** Pointer to the view that stores the series number when it is a small piece of text. */
        TextView seriesNum;
        /** Pointer to the view that stores the series number when it is a long piece of text. */
        TextView seriesNumLong;
        /** the "I've read it" checkbox. */
        CheckedTextView read;
        /** The current task to get book extra info for this view. Can be null if none. */
        @Nullable
        GetBookExtrasTask extrasTask;

        @Override
        public void map(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view) {
            final BooklistStyle style = rowContext.getStyle();
            // scaling is done on the covers and on the 'read' icon
            float scale = style.getScaleSize();

            title = view.findViewById(R.id.title);

            // theoretically we should check Fields.isVisible(UniqueId.KEY_SERIES)
            // but BooklistBuilder is not taking those settings into account
            seriesNum = view.findViewById(R.id.series_num);
            seriesNumLong = view.findViewById(R.id.series_num_long);
            if (!rowContext.hasSeriesNumber()) {
                seriesNum.setVisibility(View.GONE);
                seriesNumLong.setVisibility(View.GONE);
            }

            read = view.findViewById(R.id.read);
            if (!Fields.isVisible(UniqueId.KEY_BOOK_READ)) {
                read.setVisibility(View.GONE);
            } //else {
            // use the title text size to SCALE the 'read' icon
            // Since using CheckedTextView, this makes the icon to small or gone altogether
//                final int iconSize = (int) (title.getTextSize() * BooklistStyle.SCALE);
//                read.setMaxHeight(iconSize);
//                read.setMaxWidth(iconSize);
//                read.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, iconSize));
//            }


            final int extraFieldsInUse = style.getExtraFieldsStatus();

            shelves = view.findViewById(R.id.shelves);
            shelves.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_BOOKSHELVES) != 0
                                  ? View.VISIBLE : View.GONE);

            author = view.findViewById(R.id.author);
            author.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_AUTHOR) != 0
                                 ? View.VISIBLE : View.GONE);

            location = view.findViewById(R.id.location);
            location.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_LOCATION) != 0
                                   ? View.VISIBLE : View.GONE);

            publisher = view.findViewById(R.id.publisher);
            publisher.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_PUBLISHER) != 0
                                    ? View.VISIBLE : View.GONE);

            format = view.findViewById(R.id.format);
            format.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_FORMAT) != 0
                                 ? View.VISIBLE : View.GONE);

            cover = view.findViewById(R.id.coverImage);
            if ((extraFieldsInUse & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                cover.setVisibility(View.VISIBLE);

                LayoutParams clp =
                        new LayoutParams(LayoutParams.WRAP_CONTENT,
                                         (int) (rowContext.getMaxThumbnailHeight() * scale));
                clp.setMargins(0, 1, 0, 1);
                cover.setLayoutParams(clp);
                cover.setScaleType(ScaleType.CENTER);
            } else {
                cover.setVisibility(View.GONE);
            }

            // The default is to indent all views based on the level, but with book covers on
            // the far left, it looks better if we 'out-dent' one step.
            int level = rowContext.getLevel();
            if (level > 0) {
                --level;
            }
            view.setPadding(level * 5, 0, 0, 0);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view,
                        final int level) {

            final int extraFields = rowContext.getStyle().getExtraFieldsStatus();

            // Title
            title.setText(rowContext.getTitle());

            // Series details
            if (rowContext.hasSeriesNumber()) {
                String name = rowContext.getSeriesName();
                if (name == null || name.isEmpty()) {
                    // Hide it.
                    seriesNum.setVisibility(View.GONE);
                    seriesNumLong.setVisibility(View.GONE);
                } else {
                    final String number = rowContext.getSeriesNumber();
                    if (number != null) {
                        // Display it in one of the views, based on the size of the text.
                        if (number.length() > 4) {
                            seriesNum.setVisibility(View.GONE);
                            seriesNumLong.setVisibility(View.VISIBLE);
                            seriesNumLong.setText(number);
                        } else {
                            seriesNum.setVisibility(View.VISIBLE);
                            seriesNum.setText(number);
                            seriesNumLong.setVisibility(View.GONE);
                        }
                    }
                }
            }

            // Read
            if (Fields.isVisible(UniqueId.KEY_BOOK_READ)) {
                read.setChecked(rowContext.isRead());
            }

            // Thumbnail
            if ((extraFields & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                ImageUtils.fetchFileIntoImageView(cover,
                                                  rowContext.getBookUuid(),
                                                  rowContext.getMaxThumbnailWidth(),
                                                  rowContext.getMaxThumbnailHeight(),
                                                  true,
                                                  BooklistBuilder.thumbnailsAreCached(),
                                                  BooklistBuilder.thumbnailsAreGeneratedInBackground());
            }

            // Extras

            // We are displaying a new row, so delete any existing background task.
            // It is now irrelevant.
            if (extrasTask != null) {
                ROW_DETAILS_TASK_QUEUE.remove(extrasTask);
                extrasTask = null;
            }

            // Build the flags indicating which extras to get.
            int flags = extraFields & GetBookExtrasTask.BKEY_HANDLED;

            // If there are extras to get, run the background task.
            if (flags != 0) {
                // Fill in the extras field as blank initially.
                shelves.setText("");
                location.setText("");
                publisher.setText("");
                format.setText("");
                author.setText("");
                // Queue the task.
                ROW_DETAILS_TASK_QUEUE.enqueue(
                        new GetBookExtrasTask(rowContext.getBookId(), this, flags));
            }
        }

        /**
         * The actual book entry.
         */
        @Override
        public View createView(@NonNull final BooklistRowView rowContext,
                               @NonNull final LayoutInflater inflater,
                               @NonNull final ViewGroup parent,
                               final int level) {
            // level==3; but all book rows have the same type of view.
            return inflater.inflate(R.layout.booksonbookshelf_row_book, parent, false);
        }
    }

    /**
     * Holder to handle any field that can be displayed as a simple string.
     * Assumes there is a 'name' TextView and an optional enclosing ViewGroup
     * called ROW_INFO.
     *
     * @author Philip Warner
     */
    public static class GenericStringHolder
            extends BooklistHolder {

        /** Index of related data column. */
        final int mSourceCol;
        /** String ID to use when data is blank. */
        @StringRes
        final int mNoDataId;

        /*** View to populate. */
        TextView mTextView;

        /**
         * Constructor.
         *
         * @param rowView  Row view that represents a typical row of this kind.
         * @param source   Column name to use
         * @param noDataId String ID to use when data is blank
         */
        GenericStringHolder(@NonNull final BooklistRowView rowView,
                            @NonNull final String source,
                            @StringRes final int noDataId) {
            mSourceCol = rowView.getColumnIndex(source);
            if (mSourceCol < 0) {
                throw new DBExceptions.ColumnNotPresent(source);
            }
            mNoDataId = noDataId;
        }

        /**
         * Called at View construction time.
         */
        @Override
        public void map(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view) {
            rowInfo = view.findViewById(R.id.BLB_ROW_DETAILS);
            mTextView = view.findViewById(R.id.name);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view,
                        final int level) {
            String s = rowContext.getString(mSourceCol);
            setText(mTextView, s, mNoDataId, level);
        }

        @Override
        public View createView(@NonNull final BooklistRowView rowContext,
                               @NonNull final LayoutInflater inflater,
                               @NonNull final ViewGroup parent,
                               final int level) {
            return inflater.inflate(getDefaultLayoutId(level), parent, false);
        }
    }

    /**
     * Holder for a row that displays a 'rating'.
     *
     * @author Philip Warner
     */
    public static class RatingHolder
            extends GenericStringHolder {

        RatingHolder(@NonNull final BooklistRowView rowView,
                     @NonNull final String source,
                     @StringRes final int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view,
                        final int level) {
            String s = rowContext.getString(mSourceCol);
            if (s != null) {
                try {
                    int i = (int) Float.parseFloat(s);
                    // If valid, get the name
                    if (i >= 0 && i <= 5) {
                        s = view.getContext().getResources().getQuantityString(R.plurals.n_stars, i,
                                                                               i);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(e);
                }
            }
            setText(mTextView, s, mNoDataId, level);
        }
    }

    /**
     * Holder for a row that displays a 'language'.
     */
    public static class LanguageHolder
            extends GenericStringHolder {

        LanguageHolder(@NonNull final BooklistRowView rowView,
                       @NonNull final String source,
                       @StringRes final int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view,
                        final int level) {
            String s = rowContext.getString(mSourceCol);
            if (s != null && !s.isEmpty()) {
                s = LocaleUtils.getDisplayName(s);
            }
            setText(this.mTextView, s, mNoDataId, level);
        }
    }

    /**
     * Holder for a row that displays a 'read/unread' (as text) status.
     */
    public static class ReadUnreadHolder
            extends GenericStringHolder {

        ReadUnreadHolder(@NonNull final BooklistRowView rowView,
                         @NonNull final String source,
                         @StringRes final int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view,
                        final int level) {
            String s = rowContext.getString(mSourceCol);
            if (Datum.toBoolean(s, true)) {
                s = BookCatalogueApp.getResString(R.string.lbl_read);
            } else {
                s = BookCatalogueApp.getResString(R.string.lbl_unread);
            }
            setText(this.mTextView, s, mNoDataId, level);
        }
    }

    /**
     * Holder for a row that displays a 'month'. This code turns a month number into a
     * locale-based month name.
     *
     * @author Philip Warner
     */
    public static class MonthHolder
            extends GenericStringHolder {

        MonthHolder(@NonNull final BooklistRowView rowView,
                    @NonNull final String source,
                    @StringRes final int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view,
                        final int level) {
            String s = rowContext.getString(mSourceCol);
            if (s != null) {
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the name
                    if (i > 0 && i <= 12) {
                        s = DateUtils.getMonthName(i);
                    }
                } catch (NumberFormatException e) {
                    Logger.error(e);
                }
            }
            setText(mTextView, s, mNoDataId, level);
        }
    }

    /**
     * Holder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    public static class CheckableStringHolder
            extends GenericStringHolder {

        /** Index of related boolean column. */
        private final int mIsLockedSourceCol;

        /**
         * Constructor.
         *
         * @param rowView        Row view that represents a typical row of this kind.
         * @param source         Column name to use
         * @param isLockedSource Column name to use for the boolean 'lock' status
         * @param noDataId       String ID to use when data is blank
         */
        CheckableStringHolder(@NonNull final BooklistRowView rowView,
                              @NonNull final String source,
                              @NonNull final String isLockedSource,
                              @StringRes final int noDataId) {
            super(rowView, source, noDataId);

            mIsLockedSourceCol = rowView.getColumnIndex(isLockedSource);
            if (mIsLockedSourceCol < 0) {
                throw new DBExceptions.ColumnNotPresent(source);
            }
        }

        @Override
        public void set(@NonNull final BooklistRowView rowContext,
                        @NonNull final View view,
                        final int level) {
            super.set(rowContext, view, level);
            if (rowContext.getBoolean(mIsLockedSourceCol)) {
                mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                                                                          R.drawable.ic_lock,
                                                                          0);
            }
        }
    }
}
