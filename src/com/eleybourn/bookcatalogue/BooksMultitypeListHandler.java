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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
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
import com.eleybourn.bookcatalogue.database.ColumnInfo;
import com.eleybourn.bookcatalogue.database.DomainDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogMenuItem;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.SendOneBookTask;
import com.eleybourn.bookcatalogue.utils.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import net.philipwarner.taskqueue.QueueManager;

import java.util.ArrayList;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_STATUS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UPDATE_YEAR;

/**
 * Handles all views in a multi-type ListView showing books, authors, series etc.
 *
 * Each row(level) needs to have a layout like:
 * <layout id="@id/row_info">
 * <TextView id="@id/name" />
 * ... more fields...
 * </layout>
 *
 * row_info is important, as it's that one that gets shown/hidden when needed.
 *
 * @author Philip Warner
 */
public class BooksMultitypeListHandler implements MultitypeListHandler {
    /** Queue for tasks getting extra row details as necessary */
    private static final SimpleTaskQueue mInfoQueue = new SimpleTaskQueue("extra-info", 1);

    /**
     * Return the row type for the current cursor position.
     */
    @Override
    public int getItemViewType(Cursor c) {
        BooklistRowView rowView = ((BooklistSupportProvider) c).getRowView();
        return rowView.getKind();
    }

    /**
     * Return the number of different View types in this list.
     */
    @Override
    public int getViewTypeCount() {
        return RowKinds.ROW_KIND_MAX + 1;
    }

    /**
     * Get the text to display in the ListView for the row at the current cursor position.
     *
     * called by {@link MultitypeListCursorAdapter#getSectionTextForPosition(int)}}
     *
     * @param cursor Cursor, positioned at current row
     *
     * @return the section text as an array with all levels in order
     */
    @Override
    public String[] getSectionText(Cursor cursor) {
        Tracker.enterFunction(this, "getSectionTextForPosition", cursor);
        BooklistRowView rowView = ((BooklistSupportProvider) cursor).getRowView();
        String[] st = new String[]{rowView.getLevel1Data(), rowView.getLevel2Data()};
        Tracker.exitFunction(this, "getSectionTextForPosition");
        return st;
    }

    /**
     * Return the *absolute* position of the passed view in the list of books.
     */
    public int getAbsolutePosition(View v) {
        final BooklistHolder holder = ViewTagger.getTag(v, R.id.TAG_HOLDER);
        return holder.absolutePosition;
    }

    private void scaleViewText(@SuppressWarnings("unused") BooklistRowView rowView, View root) {
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
    public View getView(@NonNull final Cursor cursor, final LayoutInflater inflater,
                        @Nullable View convertView, final ViewGroup parent) {
        final BooklistRowView rowView = ((BooklistSupportProvider) cursor).getRowView();
        final BooklistHolder holder;
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
        } else {
            holder = ViewTagger.getTag(convertView, R.id.TAG_HOLDER);
        }

        holder.absolutePosition = rowView.getAbsolutePosition();
        holder.set(rowView, convertView, level);
        return convertView;
    }

    @Nullable
    private String getAuthorFromRow(@NonNull final CatalogueDBAdapter db, @NonNull BooklistRowView rowView) {
        if (rowView.hasAuthorId() && rowView.getAuthorId() > 0) {
            return db.getAuthorById(rowView.getAuthorId()).getDisplayName();
        } else if (rowView.getKind() == RowKinds.ROW_KIND_BOOK) {
            ArrayList<Author> authors = db.getBookAuthorList(rowView.getBookId());
            if (authors != null && authors.size() > 0)
                return authors.get(0).getDisplayName();
        }
        return null;
    }

    @Nullable
    private String getSeriesFromRow(@NonNull final CatalogueDBAdapter db, @NonNull final BooklistRowView rowView) {
        if (rowView.hasSeriesId() && rowView.getSeriesId() > 0) {
            return db.getSeriesById(rowView.getSeriesId()).name;
        } else if (rowView.getKind() == RowKinds.ROW_KIND_BOOK) {
            ArrayList<Series> series = db.getBookSeriesList(rowView.getBookId());
            if (series != null && series.size() > 0)
                return series.get(0).name;
        }
        return null;
    }

    /**
     * Utility routine to add an item to a ContextMenu object.
     *
     * @param items    list to add to
     * @param id       unique item ID
     * @param stringId string ID of string to display
     * @param iconId   icon of menu item
     */
    private void addMenuItem(@NonNull final ArrayList<SimpleDialogItem> items, int id, int stringId, int iconId) {
        SimpleDialogMenuItem i = new SimpleDialogMenuItem(BookCatalogueApp.getResourceString(stringId), id, iconId);
        items.add(i);
    }

    /**
     * Utility routine to add 'standard' menu options based on row type.
     *
     * @param rowView Row view pointing to current row for this context menu
     * @param menu    Base menu item
     */
    public void buildContextMenu(@NonNull final BooklistRowView rowView,
                                 @NonNull final ArrayList<SimpleDialogItem> menu) {
        try {
            switch (rowView.getKind()) {
                case RowKinds.ROW_KIND_BOOK: {
                    addMenuItem(menu, R.id.MENU_BOOK_DELETE, R.string.menu_delete, android.R.drawable.ic_menu_delete);
                    addMenuItem(menu, R.id.MENU_BOOK_EDIT, R.string.edit_book, android.R.drawable.ic_menu_edit);
                    addMenuItem(menu, R.id.MENU_BOOK_EDIT_NOTES, R.string.edit_book_notes, R.drawable.ic_menu_compose_holo_dark);
                    if (rowView.isRead()) {
                        addMenuItem(menu, R.id.MENU_MARK_AS_UNREAD, R.string.menu_mark_as_unread, R.drawable.btn_uncheck_clipped);
                    } else {
                        addMenuItem(menu, R.id.MENU_MARK_AS_READ, R.string.menu_mark_as_read, R.drawable.btn_check_clipped);
                    }
                    addMenuItem(menu, R.id.MENU_BOOK_EDIT_LOANS, R.string.edit_book_friends, R.drawable.ic_menu_cc_holo_dark);
                    addMenuItem(menu, R.id.MENU_BOOK_SEND_TO_GOODREADS, R.string.edit_book_send_to_gr, R.drawable.ic_menu_goodreads_holo_dark);
                    break;
                }
                case RowKinds.ROW_KIND_AUTHOR: {
                    addMenuItem(menu, R.id.MENU_AUTHOR_EDIT, R.string.menu_edit_author, android.R.drawable.ic_menu_edit);
                    break;
                }
                case RowKinds.ROW_KIND_PUBLISHER: {
                    String publisher = rowView.getPublisherName();
                    if (publisher != null && !publisher.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_PUBLISHER_EDIT, R.string.menu_edit_publisher, android.R.drawable.ic_menu_edit);
                    }
                    break;
                }
                case RowKinds.ROW_KIND_LANGUAGE: {
                    String language = rowView.getLanguage();
                    if (language != null && !language.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_LANGUAGE_EDIT, R.string.menu_edit_language, android.R.drawable.ic_menu_edit);
                    }
                    break;
                }
                case RowKinds.ROW_KIND_SERIES: {
                    long id = rowView.getSeriesId();
                    if (id != 0) {
                        addMenuItem(menu, R.id.MENU_SERIES_DELETE, R.string.menu_delete_series, android.R.drawable.ic_menu_delete);
                        addMenuItem(menu, R.id.MENU_SERIES_EDIT, R.string.menu_edit_series, android.R.drawable.ic_menu_edit);
                    }
                    break;
                }
                case RowKinds.ROW_KIND_FORMAT: {
                    String format = rowView.getFormat();
                    if (format != null && !format.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_FORMAT_EDIT, R.string.menu_edit_format, android.R.drawable.ic_menu_edit);
                    }
                    break;
                }
            }

            // add search by author ?
            boolean hasAuthor = (rowView.hasAuthorId() && rowView.getAuthorId() > 0);
            if (hasAuthor) {
                addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.string.amazon_books_by_author, R.drawable.ic_www_search_2_holo_dark);
            }

            // add search by series ?
            boolean hasSeries = (rowView.hasSeriesId() && rowView.getSeriesId() > 0);
            if (hasSeries) {
                if (hasAuthor) {
                    addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.string.amazon_books_by_author_in_series, R.drawable.ic_www_search_2_holo_dark);
                }
                addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.string.amazon_books_in_series, R.drawable.ic_www_search_2_holo_dark);
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    /**
     * Handle the 'standard' menu items. If the passed activity implements BooklistChangeListener then
     * inform it when changes have been made.
     *
     * ENHANCE: Consider using LocalBroadcastManager instead (requires Android compatibility library)
     *
     * @param db      CatalogueDBAdapter
     * @param rowView Row view for affected cursor row
     * @param context Calling Activity
     * @param itemId  Related MenuItem
     *
     * @return True, if handled.
     */
    public boolean onContextItemSelected(@NonNull final CatalogueDBAdapter db,
                                         @NonNull final BooklistRowView rowView,
                                         @NonNull final Activity context,
                                         int itemId) {
        switch (itemId) {

            case R.id.MENU_BOOK_DELETE:
                // Show the standard dialog
                int res = StandardDialogs.deleteBookAlert(context, db, rowView.getBookId(), new Runnable() {
                    @Override
                    public void run() {
                        db.purgeAuthors();
                        db.purgeSeries();
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

            case R.id.MENU_BOOK_EDIT:
                // Start the activity in the correct tab
                BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT);
                return true;

            case R.id.MENU_BOOK_EDIT_NOTES:
                // Start the activity in the correct tab
                BookEdit.editBook(context, rowView.getBookId(), BookEdit.TAB_EDIT_NOTES);
                return true;

            case R.id.MENU_BOOK_EDIT_LOANS:
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

            case R.id.MENU_BOOK_SEND_TO_GOODREADS:
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
                qm.enqueueTask(task, BCQueueManager.QUEUE_MAIN, 0);
                return true;

            case R.id.MENU_SERIES_EDIT: {
                long id = rowView.getSeriesId();
                if (id == -1) {
                    Toast.makeText(context, R.string.cannot_edit_system, Toast.LENGTH_LONG).show();
                } else {
                    Series s = db.getSeriesById(id);
                    EditSeriesDialog d = new EditSeriesDialog(context, db, new Runnable() {
                        @Override
                        public void run() {
                            db.purgeSeries();
                            // Let the Activity know
                            if (context instanceof BooklistChangeListener) {
                                final BooklistChangeListener l = (BooklistChangeListener) context;
                                l.onBooklistChange(BooklistChangeListener.FLAG_SERIES);
                            }
                        }
                    });
                    d.edit(s);
                }
                break;
            }
            case R.id.MENU_SERIES_DELETE: {
                long id = rowView.getSeriesId();
                StandardDialogs.deleteSeriesAlert(context, db, db.getSeriesById(id), new Runnable() {
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
            case R.id.MENU_AUTHOR_EDIT: {
                long id = rowView.getAuthorId();
                EditAuthorDialog d = new EditAuthorDialog(context, db, new Runnable() {
                    @Override
                    public void run() {
                        db.purgeAuthors();
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_AUTHOR);
                        }
                    }
                });
                d.edit(db.getAuthorById(id));
                break;
            }
            case R.id.MENU_PUBLISHER_EDIT: {
                String name = rowView.getPublisherName();
                EditPublisherDialog d = new EditPublisherDialog(context, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_PUBLISHER);
                        }
                    }
                });
                d.edit(new Publisher(name));
                break;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                String language = rowView.getLanguage();
                EditLanguageDialog d = new EditLanguageDialog(context, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_LANGUAGE);
                        }
                    }
                });
                d.edit(language);
                break;
            }
            case R.id.MENU_FORMAT_EDIT: {
                String format = rowView.getFormat();
                EditFormatDialog d = new EditFormatDialog(context, db, new Runnable() {
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
                BookUtils.setRead(db, rowView.getBookId(), true);
                break;
            }
            case R.id.MENU_MARK_AS_UNREAD: {
                BookUtils.setRead(db, rowView.getBookId(), false);
                break;
            }
        }
        return false;
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

            case RowKinds.ROW_KIND_BOOK:
                return new BookHolder();
            case RowKinds.ROW_KIND_SERIES:
                return new GenericStringHolder(rowView, DOM_SERIES_NAME, R.string.no_series);
            case RowKinds.ROW_KIND_TITLE_LETTER:
                return new GenericStringHolder(rowView, DOM_TITLE_LETTER, R.string.no_title);
            case RowKinds.ROW_KIND_GENRE:
                return new GenericStringHolder(rowView, DOM_GENRE, R.string.no_genre);
            case RowKinds.ROW_KIND_LANGUAGE:
                return new GenericStringHolder(rowView, DOM_LANGUAGE, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_AUTHOR:
                return new GenericStringHolder(rowView, DOM_AUTHOR_FORMATTED, R.string.no_author);
            case RowKinds.ROW_KIND_FORMAT:
                return new GenericStringHolder(rowView, DOM_FORMAT, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_PUBLISHER:
                return new GenericStringHolder(rowView, DOM_PUBLISHER, R.string.no_publisher);
            case RowKinds.ROW_KIND_READ_AND_UNREAD:
                return new GenericStringHolder(rowView, DOM_READ_STATUS, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_LOANED:
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
            case RowKinds.ROW_KIND_LOCATION:
                return new GenericStringHolder(rowView, DOM_LOCATION, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_UPDATE_YEAR:
                return new GenericStringHolder(rowView, DOM_UPDATE_YEAR, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_UPDATE_MONTH:
                return new MonthHolder(rowView, DOM_UPDATE_MONTH.name);
            case RowKinds.ROW_KIND_UPDATE_DAY:
                return new GenericStringHolder(rowView, DOM_UPDATE_DAY, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_BOOKSHELF:
                return new GenericStringHolder(rowView, DOM_BOOKSHELF_NAME, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_RATING:
                return new RatingHolder(rowView, DOM_RATING.name);

            default:
                throw new RuntimeException("Invalid row kind " + k);
        }
    }

    public interface BooklistChangeListener {
        int FLAG_AUTHOR = 1;
        int FLAG_SERIES = 2;
        int FLAG_FORMAT = 4;
        int FLAG_PUBLISHER = 8;
        int FLAG_LANGUAGE = 16;

        void onBooklistChange(int flags);
    }

    /**
     * Background task to get 'extra' details for a book row. Doing this in a background task keeps the booklist cursor
     * simple and small.
     *
     * @author Philip Warner
     */
    private static class GetBookExtrasTask implements SimpleTask {

        public static final int BKEY_HANDLED = BooklistStyle.EXTRAS_AUTHOR | BooklistStyle.EXTRAS_LOCATION
                | BooklistStyle.EXTRAS_PUBLISHER | BooklistStyle.EXTRAS_FORMAT | BooklistStyle.EXTRAS_BOOKSHELVES;

        /** Location resource string */
        static String mLocationRes = null;
        /** Publisher resource string */
        static String mPublisherRes = null;
        /** Format resource string */
        static String mFormatRes = null;
        /** The filled-in view holder for the book view. */
        final BookHolder mHolder;
        /** The book ID to fetch */
        final long mBookId;
        /** Resulting location data */
        String mLocation;
        /** Location column number */
        int mLocationCol = -2;
        /** Resulting publisher data */
        String mPublisher;
        /** Publisher column number */
        int mPublisherCol = -2;
        /** Resulting Format data */
        String mFormat;
        /** Format column number */
        int mFormatCol = -2;
        /** Resulting author data */
        String mAuthor;
        /** Author column number */
        int mAuthorCol = -2;

        /** Resulting shelves data */
        String mShelves;

        /** Flag indicating we want finished() to be called */
        private boolean mWantFinished = true;
        /** Flags indicating which extras to get */
        private int mFlags;

        /**
         * Constructor.
         *
         * @param bookId Book to fetch
         * @param holder View holder of view for the book
         */
        GetBookExtrasTask(long bookId, BookHolder holder, int flags) {
            if ((flags & BKEY_HANDLED) == 0)
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
                CatalogueDBAdapter db = taskContext.getDb();
                try (BooksCursor c = db.fetchBookById(mBookId)) {
                    // If we have a book, use it. Otherwise we are done.
                    if (c.moveToFirst()) {

                        if ((mFlags & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                            if (mAuthorCol < 0)
                                mAuthorCol = c.getColumnIndex(ColumnInfo.KEY_AUTHOR_FORMATTED);
                            //if (mLocationRes == null)
                            //	mLocationRes = BookCatalogueApp.getResourceString(R.string.location);

                            mAuthor = c.getString(mAuthorCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_LOCATION) != 0) {
                            if (mLocationCol < 0) {
                                mLocationCol = c.getColumnIndex(ColumnInfo.KEY_LOCATION);
                            }
                            if (mLocationRes == null) {
                                mLocationRes = BookCatalogueApp.getResourceString(R.string.location);
                            }

                            mLocation = mLocationRes + ": " + c.getString(mLocationCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                            if (mPublisherCol < 0) {
                                mPublisherCol = c.getColumnIndex(ColumnInfo.KEY_PUBLISHER);
                            }
                            if (mPublisherRes == null) {
                                mPublisherRes = BookCatalogueApp.getResourceString(R.string.publisher);
                            }

                            mPublisher = mPublisherRes + ": " + c.getString(mPublisherCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_FORMAT) != 0) {
                            if (mFormatCol < 0) {
                                mFormatCol = c.getColumnIndex(ColumnInfo.KEY_FORMAT);
                            }
                            if (mFormatRes == null) {
                                mFormatRes = BookCatalogueApp.getResourceString(R.string.format);
                            }

                            mFormat = mFormatRes + ": " + c.getString(mFormatCol);
                        }
                        if ((mFlags & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                            // Now build a list of all bookshelves the book is on.
                            StringBuilder shelves = new StringBuilder();
                            try (Cursor sc = db.getAllBookBookshelvesForGoodreadsCursor(mBookId)) {
                                if (sc.moveToFirst()) {
                                    do {
                                        if (shelves.length() > 0) {
                                            shelves.append(", ");
                                        }
                                        shelves.append(sc.getString(0));
                                    } while (sc.moveToNext());
                                }
                            }
                            mShelves = BookCatalogueApp.getResourceString(R.string.shelves) + ": " + shelves;
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
                if ((mFlags & BooklistStyle.EXTRAS_FORMAT) != 0)
                    mHolder.format.setText(mFormat);
            }
        }
    }

    /**
     * Implementation of general code used by Booklist holders.
     *
     * @author Philip Warner
     */
    public abstract static class BooklistHolder extends MultitypeHolder<BooklistRowView> {
        /** Pointer to the container of all info for this row. */
        View rowInfo;
        /** Absolute position of this row */
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
        /** Pointer to the view that stores the related book field */
        TextView title;
        /** Pointer to the view that stores the related book field */
        TextView author;
        /** Pointer to the view that stores the related book field */
        TextView shelves;
        /** Pointer to the view that stores the related book field */
        TextView location;
        /** Pointer to the view that stores the related book field */
        TextView publisher;
        /** Pointer to the view that stores the related book field */
        TextView format;
        /** Pointer to the view that stores the related book field */
        ImageView cover;
        /** Pointer to the view that stores the series number when it is a small piece of text */
        TextView seriesNum;
        /** Pointer to the view that stores the series number when it is a long piece of text */
        TextView seriesNumLong;
        /** the "I've read it" checkbox */
        CheckedTextView read;
        /** The current task to get book extra info for this view. Can be null if none. */
        GetBookExtrasTask extrasTask;

        @Override
        public void map(final BooklistRowView rowView, final View v) {
            final BooklistStyle style = rowView.getStyle();

            title = v.findViewById(R.id.title);

            // scaling is done on the covers and on the 'read' icon
            float scale = style.isCondensed() ? 0.8f : 1.0f;

            read = v.findViewById(R.id.read);
            if (!FieldVisibilityActivity.isVisible(ColumnInfo.KEY_READ)) {
                read.setVisibility(View.GONE);
            } //else {
            // use the title text size to scale the 'read' icon
            // Since using CheckedTextView, this makes the icon to small or gone altogether
//                final int iconSize = (int) (title.getTextSize() * scale);
//                read.setMaxHeight(iconSize);
//                read.setMaxWidth(iconSize);
//                read.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, iconSize));
//            }

            seriesNum = v.findViewById(R.id.series_num);
            seriesNumLong = v.findViewById(R.id.series_num_long);
            if (!rowView.hasSeries()) {
                seriesNum.setVisibility(View.GONE);
                seriesNumLong.setVisibility(View.GONE);
            }

            final int extras = style.getExtras();

            cover = v.findViewById(R.id.cover);
            if ((extras & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                cover.setVisibility(View.VISIBLE);

                LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                        (int) (rowView.getMaxThumbnailHeight() * scale));
                clp.setMargins(0, 1, 0, 1);
                cover.setLayoutParams(clp);
                cover.setScaleType(ScaleType.CENTER);
            } else {
                cover.setVisibility(View.GONE);
            }

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

            format = v.findViewById(R.id.format);
            if ((extras & BooklistStyle.EXTRAS_FORMAT) != 0) {
                format.setVisibility(View.VISIBLE);
            } else {
                format.setVisibility(View.GONE);
            }

            // The default is to indent all views based on the level, but with book covers on
            // the far left, it looks better if we 'outdent' one step.
            int level = rowView.getLevel();
            if (level > 0)
                --level;
            v.setPadding(level * 5, 0, 0, 0);
        }

        @Override
        public void set(final BooklistRowView rowView, View v, final int level) {

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

            // Read
            if (FieldVisibilityActivity.isVisible(ColumnInfo.KEY_READ)) {
                read.setChecked(rowView.isRead());
                read.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        read.setChecked(!read.isChecked());
                        BookUtils.setRead(rowView.getBookId(), read.isChecked());
                    }
                });
            }

            // Thumbnail
            if ((extras & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                ImageUtils.fetchBookCoverIntoImageView(cover,
                        rowView.getMaxThumbnailWidth(), rowView.getMaxThumbnailHeight(),
                        true,
                        rowView.getBookUuid(),
                        BooklistPreferencesActivity.isThumbnailCacheEnabled(),
                        BooklistPreferencesActivity.isBackgroundThumbnailsEnabled());
            }

            // Extras

            // We are displaying a new row, so delete any existing background task. It is now irrelevant.
            if (extrasTask != null) {
                mInfoQueue.remove(extrasTask);
                extrasTask = null;
            }

            // Build the flags indicating which extras to get.
            int flags = extras & GetBookExtrasTask.BKEY_HANDLED;

            // If there are extras to get, run the background task.
            if (flags != 0) {
                // Fill in the extras field as blank initially.
                shelves.setText("");
                location.setText("");
                publisher.setText("");
                format.setText("");
                author.setText("");
                // Queue the task.
                GetBookExtrasTask t = new GetBookExtrasTask(rowView.getBookId(), this, flags);
                mInfoQueue.enqueue(t);
            }
        }

        /**
         * The actual book entry
         */
        @Override
        public View newView(BooklistRowView rowView, LayoutInflater inflater, ViewGroup parent, final int level) {
            // All book rows have the same type of view.
            return inflater.inflate(R.layout.booksonbookshelf_row_book, parent, false);
        }
    }

    /**
     * Holder for a row that displays a 'month'. This code turns a month number into a
     * locale-based month name.
     *
     * @author Philip Warner
     */
    public static class MonthHolder extends BooklistHolder {
        /** Source column name */
        private final String mSource;
        /** Source column number */
        private final int mSourceCol;
        /** TextView for month name */
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
     * Holder for a row that displays a 'rating'.
     *
     * @author Philip Warner
     */
    public static class RatingHolder extends BooklistHolder {
        /** Source column name */
        private final String mSource;
        /** Source column number */
        private final int mSourceCol;
        /** TextView for month name */
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
        /** Index of related data column */
        private final int mColIndex;
        /*** Field to use */
        TextView text;
        /** String ID to use when data is blank */
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
}
