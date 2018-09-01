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
import android.content.res.Resources;
import android.database.Cursor;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistRowView;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistSupportProvider;
import com.eleybourn.bookcatalogue.database.DbUtils.DomainDefinition;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogMenuItem;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.SendOneBookTask;
import com.eleybourn.bookcatalogue.utils.BcQueueManager;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import net.philipwarner.taskqueue.QueueManager;

import java.util.ArrayList;

import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOK;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_GENRE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOANED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MAX;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_PUBLISHER;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_RATING;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_READ_AND_UNREAD;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_SERIES;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ADDED_DAY;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOKSHELF_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_GENRE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_RATING;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_DAY;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_STATUS;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_READ_YEAR;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_UPDATE_YEAR;

/**
 * Handles all views in a multi-type ListView showing books, authors, series etc.
 *
 * @author Philip Warner
 */
public class BooksMultitypeListHandler implements MultitypeListHandler {

    /**
     * Queue for tasks getting extra row details as necessary
     */
    private static final SimpleTaskQueue mInfoQueue = new SimpleTaskQueue("extra-info", 1);

    /**
     * Return the row type for the current cursor position.
     */
    public int getItemViewType(Cursor c) {
        BooklistRowView rowView = ((BooklistSupportProvider) c).getRowView();
        return rowView.getKind();
    }

    /**
     * Return the number of different View types in this list.
     */
    public int getViewTypeCount() {
        return ROW_KIND_MAX + 1;
    }

    /**
     * Get the text to display in the FastScroller for the row at the current cursor position.
     */
    public String[] getSectionText(Cursor c) {
        BooklistRowView rowView = ((BooklistSupportProvider) c).getRowView();
        return new String[]{rowView.getLevel1Data(), rowView.getLevel2Data()};
    }

    /**
     * Return a 'Holder' object for the row pointed to by rowView.
     *
     * @param rowView Points to the current row in the cursor.
     *
     * @return a 'Holder' object for the row pointed to by rowView.
     */
    private BooklistHolder newHolder(BooklistRowView rowView) {
        final int k = rowView.getKind();

        switch (k) {
            // NEWKIND: Add new kinds to this list

            case ROW_KIND_BOOK:
                return new BookHolder();
            case ROW_KIND_SERIES:
                return new GenericStringHolder(rowView, DOM_SERIES_NAME, R.string.no_series);
            case ROW_KIND_TITLE_LETTER:
                return new GenericStringHolder(rowView, DOM_TITLE_LETTER, R.string.no_title);
            case ROW_KIND_GENRE:
                return new GenericStringHolder(rowView, DOM_GENRE, R.string.no_genre);
            case ROW_KIND_LANGUAGE:
                return new GenericStringHolder(rowView, DOM_LANGUAGE, R.string.empty_with_brackets);
            case ROW_KIND_AUTHOR:
                return new GenericStringHolder(rowView, DOM_AUTHOR_FORMATTED, R.string.no_author);
            case ROW_KIND_FORMAT:
                return new GenericStringHolder(rowView, DOM_FORMAT, R.string.empty_with_brackets);
            case ROW_KIND_PUBLISHER:
                return new GenericStringHolder(rowView, DOM_PUBLISHER, R.string.no_publisher);
            case ROW_KIND_READ_AND_UNREAD:
                return new GenericStringHolder(rowView, DOM_READ_STATUS, R.string.empty_with_brackets);
            case ROW_KIND_LOANED:
                return new GenericStringHolder(rowView, DOM_LOANED_TO, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_YEAR_PUBLISHED:
                return new GenericStringHolder(rowView, DOM_PUBLICATION_YEAR, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_YEAR_ADDED:
                return new GenericStringHolder(rowView, DOM_ADDED_YEAR, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_DAY_ADDED:
                return new GenericStringHolder(rowView, DOM_ADDED_DAY, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_MONTH_PUBLISHED:
                return new MonthHolder(rowView, DOM_PUBLICATION_MONTH.name);
            case RowKinds.ROW_KIND_MONTH_ADDED:
                return new MonthHolder(rowView, DOM_ADDED_MONTH.name);

            case RowKinds.ROW_KIND_YEAR_READ:
                return new GenericStringHolder(rowView, DOM_READ_YEAR, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_MONTH_READ:
                return new MonthHolder(rowView, DOM_READ_MONTH.name);
            case RowKinds.ROW_KIND_DAY_READ:
                return new GenericStringHolder(rowView, DOM_READ_DAY, R.string.empty_with_brackets);

            case ROW_KIND_LOCATION:
                return new GenericStringHolder(rowView, DOM_LOCATION, R.string.empty_with_brackets);

            case RowKinds.ROW_KIND_UPDATE_YEAR:
                return new GenericStringHolder(rowView, DOM_UPDATE_YEAR, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_UPDATE_MONTH:
                return new MonthHolder(rowView, DOM_UPDATE_MONTH.name);
            case RowKinds.ROW_KIND_UPDATE_DAY:
                return new GenericStringHolder(rowView, DOM_UPDATE_DAY, R.string.empty_with_brackets);

            case ROW_KIND_BOOKSHELF:
                return new GenericStringHolder(rowView, DOM_BOOKSHELF_NAME, R.string.empty_with_brackets);

            case ROW_KIND_RATING:
                return new RatingHolder(rowView, DOM_RATING.name);


            default:
                throw new RuntimeException("Invalid row kind " + k);
        }
    }

    /**
     * Return the *absolute* position of the passed view in the list of books.
     */
    public int getAbsolutePosition(View v) {
        BooklistHolder h = ViewTagger.getTag(v, R.id.TAG_HOLDER);
        return h.absolutePosition;
    }

    private void scaleViewText(BooklistRowView rowView, View root) {
        final float scale = 0.8f;

        if (root instanceof TextView) {
            TextView txt = (TextView) root;
            float px = txt.getTextSize();
            txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * scale);
        }
		/*
		 * No matter what I tried, this particular piece of code does not seem to work.
		 * All image scaling is moved to the relevant holder constructors until the
		 * reason this code fails is understood.
		 *
		if (root instanceof ImageView) {
			ImageView img = (ImageView) root;
			if (img.getId() == R.id.read) {
				img.setMaxHeight((int) (30*scale));
				img.setMaxWidth((int) (30*scale) );
				System.out.println("SCALE READ");
				img.requestLayout();
			} else if (img.getId() == R.id.cover) {
				img.setMaxHeight((int) (rowView.getMaxThumbnailHeight()*scale));
				img.setMaxWidth((int) (rowView.getMaxThumbnailWidth()*scale) );
				LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, (int) (rowView.getMaxThumbnailHeight()*scale) );
				img.setLayoutParams(lp);
				img.requestLayout();
				System.out.println("SCALE COVER");
			} else {
				System.out.println("UNKNOWN IMAGE");
			}
		}
		 */

        root.setPadding((int) (scale * root.getPaddingLeft()), (int) (scale * root.getPaddingTop()), (int) (scale * root.getPaddingRight()), (int) (scale * root.getPaddingBottom()));

        root.getPaddingBottom();
        if (root instanceof ViewGroup) {
            ViewGroup grp = (ViewGroup) root;
            for (int i = 0; i < grp.getChildCount(); i++) {
                View v = grp.getChildAt(i);
                scaleViewText(rowView, v);
            }
        }
    }

    @Override
    public View getView(Cursor c, LayoutInflater inflater, View convertView, ViewGroup parent) {
        final BooklistRowView rowView = ((BooklistSupportProvider) c).getRowView();
        BooklistHolder holder;
        final int level = rowView.getLevel();
        if (convertView == null) {
            holder = newHolder(rowView);
            convertView = holder.newView(rowView, inflater, parent, level);
            // Scale the text if necessary
            if (rowView.getStyle().isCondensed()) {
                scaleViewText(rowView, convertView);
            }
            convertView.setPadding((level - 1) * 5, 0, 0, 0);
            holder.map(rowView, convertView);
            ViewTagger.setTag(convertView, R.id.TAG_HOLDER, holder);
            // Indent based on level; we assume rows of a given type only occur at the same level
        } else
            holder = ViewTagger.getTag(convertView, R.id.TAG_HOLDER);

        holder.absolutePosition = rowView.getAbsolutePosition();
        holder.set(rowView, convertView, level);

        return convertView;
    }

    /**
     * Utility routine to add an item to a ContextMenu object.
     *
     * @param items    list to add to
     * @param id       unique item ID
     * @param stringId string ID of string to display
     * @param iconId   icon of menu item
     */
    private void addMenuItem(ArrayList<SimpleDialogItem> items, int id, int stringId, int iconId) {
        SimpleDialogMenuItem i = new SimpleDialogMenuItem(BookCatalogueApp.getResourceString(stringId), id, iconId);
        // Add the menu
        items.add(i);
    }

    /**
     * Utility routine to add 'standard' menu options based on row type.
     *
     * @param rowView Row view pointing to current row for this context menu
     * @param menu    Base menu item
     */
    public void buildContextMenu(BooklistRowView rowView, ArrayList<SimpleDialogItem> menu) {
        try {
            boolean hasSeries = rowView.hasSeriesId() && rowView.getSeriesId() > 0;
            boolean hasAuthor = rowView.hasAuthorId() ? rowView.getAuthorId() > 0 : rowView.getKind() == RowKinds.ROW_KIND_BOOK;
            boolean isRead = rowView.isRead();

            switch (rowView.getKind()) {
                case ROW_KIND_BOOK: {
                    addMenuItem(menu, R.id.MENU_DELETE_BOOK, R.string.menu_delete, android.R.drawable.ic_menu_delete);
                    addMenuItem(menu, R.id.MENU_EDIT_BOOK, R.string.edit_book, android.R.drawable.ic_menu_edit);
                    addMenuItem(menu, R.id.MENU_EDIT_BOOK_NOTES, R.string.edit_book_notes, R.drawable.ic_menu_compose_holo_dark);
                    addMenuItem(menu, R.id.MENU_EDIT_BOOK_FRIENDS, R.string.edit_book_friends, R.drawable.ic_menu_cc_holo_dark);
                    if (!isRead) {
                        addMenuItem(menu, R.id.MENU_MARK_AS_READ, R.string.menu_mark_as_read, R.drawable.btn_check_clipped);
                    } else {
                        addMenuItem(menu, R.id.MENU_MARK_AS_UNREAD, R.string.menu_mark_as_unread, R.drawable.btn_uncheck_clipped);
                    }
                    addMenuItem(menu, R.id.MENU_SEND_BOOK_TO_GR, R.string.edit_book_send_to_gr, R.drawable.ic_menu_goodreads_holo_dark);
                    break;
                }
                case ROW_KIND_AUTHOR: {
                    addMenuItem(menu, R.id.MENU_EDIT_AUTHOR, R.string.menu_edit_author, android.R.drawable.ic_menu_edit);
                    addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.string.amazon_books_by_author, R.drawable.ic_menu_cc_holo_dark);
                    break;
                }
                case ROW_KIND_SERIES: {
                    long id = rowView.getSeriesId();
                    if (id != 0) {
                        addMenuItem(menu, R.id.MENU_DELETE_SERIES, R.string.menu_delete_series, android.R.drawable.ic_menu_delete);
                        addMenuItem(menu, R.id.MENU_EDIT_SERIES, R.string.menu_edit_series, android.R.drawable.ic_menu_edit);
                    }
                    break;
                }
                case ROW_KIND_FORMAT: {
                    String format = rowView.getFormat();
                    if (format != null && !format.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_EDIT_FORMAT, R.string.menu_edit_format, android.R.drawable.ic_menu_edit);
                    }
                    break;
                }
            }
            if (hasAuthor)
                addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.string.amazon_books_by_author, R.drawable.ic_www_search_2_holo_dark);
            if (hasSeries) {
                if (hasAuthor)
                    addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.string.amazon_books_by_author_in_series, R.drawable.ic_www_search_2_holo_dark);
                addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.string.amazon_books_in_series, R.drawable.ic_www_search_2_holo_dark);
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    private String getAuthorFromRow(CatalogueDBAdapter db, BooklistRowView rowView) {
        String author = null;
        if (rowView.hasAuthorId() && rowView.getAuthorId() > 0) {
            author = db.getAuthorById(rowView.getAuthorId()).getDisplayName();
        } else if (rowView.getKind() == RowKinds.ROW_KIND_BOOK) {
            ArrayList<Author> authors = db.getBookAuthorList(rowView.getBookId());
            if (authors != null && authors.size() > 0)
                author = authors.get(0).getDisplayName();
        }
        return author;
    }

    private String getSeriesFromRow(CatalogueDBAdapter db, BooklistRowView rowView) {
        String series = null;
        if (rowView.hasSeriesId() && rowView.getSeriesId() > 0) {
            series = db.getSeriesById(rowView.getSeriesId()).name;
        } else if (rowView.getKind() == RowKinds.ROW_KIND_BOOK) {
            ArrayList<Series> seriess = db.getBookSeriesList(rowView.getBookId());
            if (seriess != null && seriess.size() > 0)
                series = seriess.get(0).name;
        }
        return series;
    }

    /**
     * Handle the 'standard' menu items. If the passed activity implements BooklistChangeListener then
     * inform it when changes have been made.
     * <p>
     * ENHANCE: Consider using LocalBroadcastManager instead (requires Android compatibility library)
     *
     * @param db      CatalogueDBAdapter
     * @param rowView Row view for affected cursor row
     * @param context Calling Activity
     * @param dba     Database helper
     * @param itemId  Related MenuItem
     *
     * @return True, if handled.
     */
    public boolean onContextItemSelected(CatalogueDBAdapter db, BooklistRowView rowView, final Activity context, final CatalogueDBAdapter dba, final int itemId) {
        switch (itemId) {

            case R.id.MENU_DELETE_BOOK:
                // Show the standard dialog
                int res = StandardDialogs.deleteBookAlert(context, dba, rowView.getBookId(), new Runnable() {
                    @Override
                    public void run() {
                        dba.purgeAuthors();
                        dba.purgeSeries();
                        // Let the activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_AUTHOR | BooklistChangeListener.FLAG_SERIES);
                        }
                    }
                });
                // Display an error, if any
                if (res != 0)
                    Toast.makeText(context, res, Toast.LENGTH_LONG).show();
                return true;

            case R.id.MENU_EDIT_BOOK:
                // Start the activity in the correct tab
                BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT);
                return true;

            case R.id.MENU_EDIT_BOOK_NOTES:
                // Start the activity in the correct tab
                BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT_NOTES);
                return true;

            case R.id.MENU_EDIT_BOOK_FRIENDS:
                // Start the activity in the correct tab
                BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT_FRIENDS);
                return true;

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                String author = getAuthorFromRow(db, rowView);
                AmazonUtils.openAmazonSearchPage(context, author, null);
                return true;
            }

            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                String series = getSeriesFromRow(db, rowView);
                AmazonUtils.openAmazonSearchPage(context, null, series);
                return true;
            }

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                String author = getAuthorFromRow(db, rowView);
                String series = getSeriesFromRow(db, rowView);
                AmazonUtils.openAmazonSearchPage(context, author, series);
                return true;
            }

            case R.id.MENU_SEND_BOOK_TO_GR:
                // Get a GoodreadsManager and make sure we are authorized.
                // TODO: This does network traffic on main thread and will ALWAYS die in Android 4.2+. Should mimic code in GoodreadsUtils.sendBooksToGoodreads(...)
                GoodreadsManager grMgr = new GoodreadsManager();
                if (!grMgr.hasValidCredentials()) {
                    try {
                        grMgr.requestAuthorization(context);
                    } catch (NetworkException e) {
                        Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                }
                // get a QueueManager and queue the task.
                QueueManager qm = BookCatalogueApp.getQueueManager();
                SendOneBookTask task = new SendOneBookTask(rowView.getBookId());
                qm.enqueueTask(task, BcQueueManager.QUEUE_MAIN, 0);
                return true;

            case R.id.MENU_EDIT_SERIES: {
                long id = rowView.getSeriesId();
                if (id == -1) {
                    Toast.makeText(context, R.string.cannot_edit_system, Toast.LENGTH_LONG).show();
                } else {
                    Series s = dba.getSeriesById(id);
                    EditSeriesDialog d = new EditSeriesDialog(context, dba, new Runnable() {
                        @Override
                        public void run() {
                            dba.purgeSeries();
                            // Let the Activity know
                            if (context instanceof BooklistChangeListener) {
                                final BooklistChangeListener l = (BooklistChangeListener) context;
                                l.onBooklistChange(BooklistChangeListener.FLAG_SERIES);
                            }
                        }
                    });
                    d.editSeries(s);
                }
                break;
            }
            case R.id.MENU_DELETE_SERIES: {
                long id = rowView.getSeriesId();
                StandardDialogs.deleteSeriesAlert(context, dba, dba.getSeriesById(id), new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_SERIES);
                        }
                    }
                });
                break;
            }
            case R.id.MENU_EDIT_AUTHOR: {
                long id = rowView.getAuthorId();
                EditAuthorDialog d = new EditAuthorDialog(context, dba, new Runnable() {
                    @Override
                    public void run() {
                        dba.purgeAuthors();
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_AUTHOR);
                        }
                    }
                });
                d.editAuthor(dba.getAuthorById(id));
                break;
            }
            case R.id.MENU_EDIT_FORMAT: {
                String format = rowView.getFormat();
                EditFormatDialog d = new EditFormatDialog(context, dba, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_FORMAT);
                        }
                    }
                });
                d.edit(format);
                break;
            }
            case R.id.MENU_MARK_AS_READ: {
                BookUtils.setRead(dba, rowView.getBookId(), true);
                break;
            }
            case R.id.MENU_MARK_AS_UNREAD: {
                BookUtils.setRead(dba, rowView.getBookId(), false);
                break;
            }
        }
        return false;
    }

    public interface BooklistChangeListener {
        int FLAG_AUTHOR = 1;
        int FLAG_SERIES = 2;
        int FLAG_FORMAT = 4;

        void onBooklistChange(int flags);
    }

    /**
     * Implementation of general code used by Booklist holders.
     *
     * @author Philip Warner
     */
    public abstract static class BooklistHolder extends MultitypeHolder<BooklistRowView> {
        /**
         * Pointer to the container of all info for this row.
         */
        View rowInfo;
        /**
         * Absolute position of this row
         */
        int absolutePosition;

        /**
         * Used to get the 'default' layout to use for differing row levels.
         *
         * @param level Level of layout
         *
         * @return Layout ID
         */
        static int getDefaultLayoutId(final int level) {
            int id;
            switch (level) {
                case 1:
                    id = R.layout.booksonbookshelf_row_level_1;
                    break;
                case 2:
                    id = R.layout.booksonbookshelf_row_level_2;
                    break;
                default:
                    id = R.layout.booksonbookshelf_row_level_3;
                    break;
            }
            return id;
        }

        /**
         * For a simple row, just set the text (or hide it)
         *
         * @param view          View to set
         * @param s             String to display
         * @param emptyStringId String to display if first is empty and can not hide row
         * @param level         Level of this item (we never hide level 1 items).
         */
        public void setText(TextView view, String s, int emptyStringId, int level) {
            if (s == null || s.isEmpty()) {
                if (level > 1 && rowInfo != null) {
                    rowInfo.setVisibility(View.GONE);
                    return;
                }
                view.setText(BookCatalogueApp.getResourceString(emptyStringId));
            } else {
                if (rowInfo != null)
                    rowInfo.setVisibility(View.VISIBLE);
                view.setText(s);
            }
        }
    }

    /**
     * Holder for a BOOK row.
     *
     * @author Philip Warner
     */
    public static class BookHolder extends BooklistHolder {
        /**
         * Pointer to the view that stores the related book field
         */
        TextView title;
        /**
         * Pointer to the view that stores the related book field
         */
        TextView author;
        /**
         * Pointer to the view that stores the related book field
         */
        TextView shelves;
        /**
         * Pointer to the view that stores the related book field
         */
        TextView location;
        /**
         * Pointer to the view that stores the related book field
         */
        TextView publisher;
        /**
         * Pointer to the view that stores the related book field
         */
        ImageView cover;
        /**
         * Pointer to the view that stores the series number when it is a small piece of text
         */
        TextView seriesNum;
        /**
         * Pointer to the view that stores the series number when it is a long piece of text
         */
        TextView seriesNumLong;
        /**
         * Pointer to the view that stores the series number in PORTRAIT mode
         */
        ImageView read;
        /**
         * The current task to get book extra info for this view. Can be null if none.
         */
        GetBookExtrasTask extrasTask;

        @Override
        public void map(BooklistRowView rowView, View v) {
            final BooklistStyle style = rowView.getStyle();

            float scale = 1.0f;
            if (style.isCondensed())
                scale = 0.8f;

            // Find the various views we use.
            title = v.findViewById(R.id.title);
            cover = v.findViewById(R.id.cover);
            seriesNum = v.findViewById(R.id.series_num);
            seriesNumLong = v.findViewById(R.id.series_num_long);

            final int iconSize = (int) (title.getTextSize() * scale);
            read = v.findViewById(R.id.read);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, iconSize);
            read.setMaxHeight(iconSize);
            read.setMaxWidth(iconSize);
            read.setLayoutParams(lp);
            read.setScaleType(ScaleType.CENTER);

            if (!rowView.hasSeries()) {
                seriesNum.setVisibility(View.GONE);
                seriesNumLong.setVisibility(View.GONE);
            }

            final int extras = style.getExtras();

            if ((extras & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                cover.setVisibility(View.VISIBLE);

                //cover.setMaxHeight( (int)(rowView.getMaxThumbnailHeight()*scale) );
                //cover.setMaxWidth((int)(rowView.getMaxThumbnailWidth()*scale));
                LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT, (int) (rowView.getMaxThumbnailHeight() * scale));
                clp.setMargins(0, 1, 0, 1);
                cover.setLayoutParams(clp);
                cover.setScaleType(ScaleType.CENTER);
            } else
                cover.setVisibility(View.GONE);

            shelves = v.findViewById(R.id.shelves);
            if ((extras & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                shelves.setVisibility(View.VISIBLE);
            } else {
                shelves.setVisibility(View.GONE);
            }

            author = v.findViewById(R.id.author);
            if ((extras & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                author.setVisibility(View.VISIBLE);
            } else {
                author.setVisibility(View.GONE);
            }

            location = v.findViewById(R.id.location);
            if ((extras & BooklistStyle.EXTRAS_LOCATION) != 0) {
                location.setVisibility(View.VISIBLE);
            } else {
                location.setVisibility(View.GONE);
            }

            publisher = v.findViewById(R.id.publisher);
            if ((extras & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                publisher.setVisibility(View.VISIBLE);
            } else {
                publisher.setVisibility(View.GONE);
            }

            // The default is to indent all views based on the level, but with book covers on
            // the far left, it looks better if we 'outdent' one step.
            int level = rowView.getLevel();
            if (level > 0)
                --level;
            v.setPadding(level * 5, 0, 0, 0);
        }

        @Override
        public void set(BooklistRowView rowView, View v, final int level) {

            final int extras = rowView.getStyle().getExtras();

            // Title
            title.setText(rowView.getTitle());

            // Series details
            if (rowView.hasSeries()) {
                final String seriesNumber = rowView.getSeriesNumber();
                final String seriesName = rowView.getSeriesName();
                if (seriesName == null || seriesName.isEmpty()) {
                    // Hide it.
                    seriesNum.setVisibility(View.GONE);
                    seriesNumLong.setVisibility(View.GONE);
                } else {
                    // Display it in one of the views, based on the size of the text.
                    if (seriesNumber.length() > 4) {
                        seriesNum.setVisibility(View.GONE);
                        seriesNumLong.setVisibility(View.VISIBLE);
                        seriesNumLong.setText(seriesNumber);
                    } else {
                        seriesNum.setVisibility(View.VISIBLE);
                        seriesNum.setText(seriesNumber);
                        seriesNumLong.setVisibility(View.GONE);
                    }
                }
            }

            // READ
            if (rowView.getRead()) {
                read.setVisibility(View.VISIBLE);
                read.setImageResource(R.drawable.btn_check_clipped);
            } else {
                read.setVisibility(View.GONE);
            }

            // Thumbnail
            if ((extras & BooklistStyle.EXTRAS_THUMBNAIL) != 0)
                ImageUtils.fetchBookCoverIntoImageView(cover,
                        rowView.getMaxThumbnailWidth(), rowView.getMaxThumbnailHeight(),
                        true, rowView.getBookUuid(),
                        BooklistPreferencesActivity.isThumbnailCacheEnabled(),
                        BooklistPreferencesActivity.isBackgroundThumbnailsEnabled());

            // Extras

            // We are displaying a new row, so delete any existing background task. It is now irrelevant.
            if (extrasTask != null) {
                mInfoQueue.remove(extrasTask);
                extrasTask = null;
            }

            // Build the flags indicating which extras to get.
            int flags = extras & GetBookExtrasTask.EXTRAS_HANDLED;

            // If there are extras to get, run the background task.
            if (flags != 0) {
                // Fill in the extras field as blank initially.
                shelves.setText("");
                location.setText("");
                publisher.setText("");
                author.setText("");
                // Queue the task.
                GetBookExtrasTask t = new GetBookExtrasTask(rowView.getBookId(), this, flags);
                mInfoQueue.enqueue(t);
            }
        }

        @Override
        public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
            // All book rows have the same type of view.
            return inflater.inflate(R.layout.booksonbookshelf_row_book, parent, false);
        }
    }

//	/**
//	 * Utility routine to add 'standard' menu options based on row type.
//	 * 
//	 * @param rowView		Row view pointing to current row for this context menu
//	 * @param menu			Base menu item
//	 * @param v				View that was clicked
//	 * @param menuInfo		menuInfo object from Adapter (not really needed since we have holders and cursor)
//	 */
//	public void onCreateContextMenu(BooklistRowView rowView, ContextMenu menu) {
//		try {
//			switch(rowView.getKind()) {
//			case ROW_KIND_BOOK:
//			{
//				long series = rowView.getSeriesId();
//				addMenuItem(menu, R.id.MENU_DELETE_BOOK, R.string.menu_delete, android.R.drawable.ic_menu_delete);
//				addMenuItem(menu, R.id.MENU_EDIT_BOOK, R.string.edit_book, android.R.drawable.ic_menu_edit);
//				addMenuItem(menu, R.id.MENU_EDIT_BOOK_NOTES, R.string.edit_book_notes, R.drawable.ic_menu_compose);
//				addMenuItem(menu, R.id.MENU_EDIT_BOOK_FRIENDS, R.string.edit_book_friends, R.drawable.ic_menu_cc);
//				addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.string.amazon_books_by_author, R.drawable.ic_menu_cc);
//				if (series != 0) {
//					addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.string.amazon_books_by_author_in_series, R.drawable.ic_menu_cc);
//					addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.string.amazon_books_in_series, R.drawable.ic_menu_cc);
//				}
//				addMenuItem(menu, R.id.MENU_SEND_BOOK_TO_GR, R.string.edit_book_send_to_gr, R.drawable.ic_menu_cc);
//				break;
//			}
//			case ROW_KIND_AUTHOR:
//			{
//				addMenuItem(menu, R.id.MENU_EDIT_AUTHOR, R.string.menu_edit_author, android.R.drawable.ic_menu_edit);
//				addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.string.amazon_books_by_author, R.drawable.ic_menu_cc);
//				long series = rowView.getSeriesId();
//				if (series != 0)
//					addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.string.amazon_books_by_author_in_series, R.drawable.ic_menu_cc);
//				break;
//			}
//			case ROW_KIND_SERIES:
//			{
//				long id = rowView.getSeriesId();
//				if (id != 0) {
//					addMenuItem(menu, R.id.MENU_DELETE_SERIES, R.string.menu_delete_series, android.R.drawable.ic_menu_delete);
//					addMenuItem(menu, R.id.MENU_EDIT_SERIES, R.string.menu_edit_series, android.R.drawable.ic_menu_edit);
//					long author = rowView.getAuthorId();
//					if (author != 0)
//						addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.string.amazon_books_by_author_in_series, R.drawable.ic_menu_cc);
//					addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.string.amazon_books_in_series, R.drawable.ic_menu_cc);
//				}
//				break;
//			}
//			case ROW_KIND_FORMAT:
//			{
//				String format = rowView.getFormat();
//				if (format != null && !format.equals("")) {
//					addMenuItem(menu, R.id.MENU_EDIT_FORMAT, R.string.menu_edit_format, android.R.drawable.ic_menu_edit);
//				}
//				break;
//			}
//			}
//		} catch (Exception e) {
//			Logger.logError(e);
//		}
//	}

    /**
     * Background task to get 'extra' details for a book row. Doing this in a background task keeps the booklist cursor
     * simple and small.
     *
     * @author Philip Warner
     */
    private static class GetBookExtrasTask implements SimpleTask {

        public static final int EXTRAS_HANDLED = BooklistStyle.EXTRAS_AUTHOR | BooklistStyle.EXTRAS_LOCATION | BooklistStyle.EXTRAS_PUBLISHER | BooklistStyle.EXTRAS_BOOKSHELVES;
        /**
         * Location resource string
         */
        static String mLocationRes = null;
        /**
         * Publisher resource string
         */
        static String mPublisherRes = null;
        /**
         * The filled-in view holder for the book view.
         */
        final BookHolder mHolder;
        /**
         * The book ID to fetch
         */
        final long mBookId;
        /**
         * Resulting location data
         */
        String mLocation;
        /**
         * Location column number
         */
        int mLocationCol = -2;
        /**
         * Resulting publisher data
         */
        String mPublisher;
        /**
         * Publisher column number
         */
        int mPublisherCol = -2;
        /**
         * Resulting author data
         */
        String mAuthor;
        /**
         * Author column number
         */
        int mAuthorCol = -2;

        /**
         * Resulting shelves data
         */
        String mShelves;

        /**
         * Flag indicating we want finished() to be called
         */
        private boolean mWantFinished = true;
        /**
         * Flags indicating which extras to get
         */
        private int mFlags;

        /**
         * Constructor.
         *
         * @param bookId Book to fetch
         * @param holder View holder of view for the book
         */
        GetBookExtrasTask(long bookId, BookHolder holder, int flags) {
            if ((flags & EXTRAS_HANDLED) == 0)
                throw new RuntimeException("GetBookExtrasTask called for unhandled extras");

            mHolder = holder;
            mBookId = bookId;
            mFlags = flags;
            synchronized (mHolder) {
                mHolder.extrasTask = this;
            }
        }

        @Override
        public void run(SimpleTaskContext taskContext) {
            try {
                // Make sure we are the right task.
                synchronized (mHolder) {
                    if (mHolder.extrasTask != this) {
                        mWantFinished = false;
                        return;
                    }
                }
                // Get a DB connection and find the book.
                CatalogueDBAdapter dba = taskContext.getDb(); //new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
                //dba.open();
                BooksCursor c = dba.fetchBookById(mBookId);
                try {
                    // If we have a book, use it. Otherwise we are done.
                    if (c.moveToFirst()) {

                        if ((mFlags & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                            if (mAuthorCol < 0)
                                mAuthorCol = c.getColumnIndex(ColumnNames.KEY_AUTHOR_FORMATTED);
                            //if (mLocationRes == null)
                            //	mLocationRes = BookCatalogueApp.getResourceString(R.string.location);

                            mAuthor = c.getString(mAuthorCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_LOCATION) != 0) {
                            if (mLocationCol < 0)
                                mLocationCol = c.getColumnIndex(ColumnNames.KEY_LOCATION);
                            if (mLocationRes == null)
                                mLocationRes = BookCatalogueApp.getResourceString(R.string.location);

                            mLocation = mLocationRes + ": " + c.getString(mLocationCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                            if (mPublisherCol < 0)
                                mPublisherCol = c.getColumnIndex(ColumnNames.KEY_PUBLISHER);
                            if (mPublisherRes == null)
                                mPublisherRes = BookCatalogueApp.getResourceString(R.string.publisher);

                            mPublisher = mPublisherRes + ": " + c.getString(mPublisherCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                            // Now build a list of all bookshelves the book is on.
                            StringBuilder shelves = new StringBuilder();
                            Cursor sc = dba.getAllBookBookshelvesForGoodreadsCursor(mBookId);
                            try {
                                if (sc.moveToFirst()) {
                                    do {
                                        if (shelves.length() > 0)
                                            shelves.append(", ");
                                        shelves.append(sc.getString(0));
                                    } while (sc.moveToNext());
                                }
                            } finally {
                                sc.close();
                            }
                            mShelves = BookCatalogueApp.getResourceString(R.string.shelves) + ": " + shelves;
                        }
                    } else {
                        // No data, no need for UI thread call.
                        mWantFinished = false;
                    }
                } finally {
                    c.close();
                    //dba.close();
                }
            } finally {
                taskContext.setRequiresFinish(mWantFinished);
            }
        }

        /**
         * Handle the results of the task.
         */
        @Override
        public void onFinish(Exception e) {
            synchronized (mHolder) {
                if (mHolder.extrasTask != this) {
                    return;
                }

                if ((mFlags & BooklistStyle.EXTRAS_BOOKSHELVES) != 0)
                    mHolder.shelves.setText(mShelves);
                if ((mFlags & BooklistStyle.EXTRAS_AUTHOR) != 0)
                    mHolder.author.setText(mAuthor);
                if ((mFlags & BooklistStyle.EXTRAS_LOCATION) != 0)
                    mHolder.location.setText(mLocation);
                if ((mFlags & BooklistStyle.EXTRAS_PUBLISHER) != 0)
                    mHolder.publisher.setText(mPublisher);
            }
        }
    }

    /**
     * Holder for a row that displays a 'month'. This code turns a month number into a
     * locale-based month name.
     *
     * @author Philip Warner
     */
    public static class MonthHolder extends BooklistHolder {
        /**
         * Source column name
         */
        private final String mSource;
        /**
         * Source column number
         */
        private final int mSourceCol;
        /**
         * TextView for month name
         */
        TextView text;

        MonthHolder(BooklistRowView rowView, String source) {
            mSource = source;
            mSourceCol = rowView.getColumnIndex(mSource);
        }

        @Override
        public void map(BooklistRowView rowView, View v) {
            rowInfo = v.findViewById(R.id.row_info);
            text = v.findViewById(R.id.name);
        }

        @Override
        public void set(BooklistRowView rowView, View v, final int level) {
            // Get the month and try to format it.
            String s = rowView.getString(mSourceCol);
            try {
                int i = Integer.parseInt(s);
                // If valid, get the name
                if (i > 0 && i <= 12) {
                    // Create static formatter if necessary
                    s = DateUtils.getMonthName(i);
                }
            } catch (Exception e) {
                Logger.logError(e);
            }
            // Display whatever text we have
            setText(text, s, R.string.unknown_uc, level);
        }

        @Override
        public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
            return inflater.inflate(getDefaultLayoutId(level), parent, false);
        }
    }

    /**
     * Holder for a row that displays a 'month'. This code turns a month number into a
     * locale-based month name.
     *
     * @author Philip Warner
     */
    public static class RatingHolder extends BooklistHolder {
        /**
         * Source column name
         */
        private final String mSource;
        /**
         * Source column number
         */
        private final int mSourceCol;
        /**
         * TextView for month name
         */
        TextView text;

        RatingHolder(BooklistRowView rowView, String source) {
            mSource = source;
            mSourceCol = rowView.getColumnIndex(mSource);
        }

        @Override
        public void map(BooklistRowView rowView, View v) {
            rowInfo = v.findViewById(R.id.row_info);
            text = v.findViewById(R.id.name);
        }

        @Override
        public void set(BooklistRowView rowView, View v, final int level) {
            // Get the month and try to format it.
            String s = rowView.getString(mSourceCol);
            try {
                int i = (int) Float.parseFloat(s);
                // If valid, get the name
                if (i >= 0 && i <= 5) {
                    Resources r = BookCatalogueApp.getAppContext().getResources();
                    s = r.getQuantityString(R.plurals.n_stars, i, i);
                }
            } catch (Exception e) {
                Logger.logError(e);
            }
            // Display whatever text we have
            setText(text, s, R.string.unknown_uc, level);
        }

        @Override
        public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
            return inflater.inflate(getDefaultLayoutId(level), parent, false);
        }
    }

    /**
     * Holder to handle any field that can be displayed as a simple string.
     * Assumes there is a 'name' TextView and an optional enclosing ViewGroup
     * called row_info.
     *
     * @author Philip Warner
     */
    public class GenericStringHolder extends BooklistHolder {
        /**
         * Index of related data column
         */
        private final int mColIndex;
        /**
         * Field to use
         */
        TextView text;
        /**
         * String ID to use when data is blank
         */
        private int mNoDataId;

        /**
         * Constructor
         *
         * @param rowView  Row view that represents a typical row of this kind.
         * @param domain   Domain name to use
         * @param noDataId String ID to use when data is blank
         */
        private GenericStringHolder(BooklistRowView rowView, DomainDefinition domain, int noDataId) {
            mColIndex = rowView.getColumnIndex(domain.name);
            if (mColIndex < 0)
                throw new RuntimeException("Domain '" + domain.name + "'not found in row view");
            mNoDataId = noDataId;
        }

        @Override
        public void map(BooklistRowView rowView, View v) {
            rowInfo = v.findViewById(R.id.row_info);
            text = v.findViewById(R.id.name);
        }

        @Override
        public void set(BooklistRowView rowView, View v, final int level) {
            String s = rowView.getString(mColIndex);
            setText(text, s, mNoDataId, level);
        }

        @Override
        public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
            return inflater.inflate(getDefaultLayoutId(level), parent, false);
        }
    }

}
