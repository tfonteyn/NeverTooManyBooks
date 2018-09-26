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
import android.widget.Checkable;
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
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogMenuItem;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.SendOneBookTask;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import com.eleybourn.bookcatalogue.taskqueue.QueueManager;

import java.util.ArrayList;
import java.util.List;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
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
 * <layout id="@id/ROW_INFO">
 * <TextView id="@id/name" />
 * ... more fields...
 * </layout>
 *
 * ROW_INFO is important, as it's that one that gets shown/hidden when needed.
 *
 * @author Philip Warner
 */
public class BooksMultiTypeListHandler implements MultiTypeListHandler {
    /** Queue for tasks getting extra row details as necessary */
    private static final SimpleTaskQueue mInfoQueue = new SimpleTaskQueue("extra-info", 1);

    /**
     * Return the row type for the current cursor position.
     */
    @Override
    public int getItemViewType(@NonNull final Cursor c) {
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
     * called by {@link MultiTypeListCursorAdapter#getSectionTextForPosition(int)}}
     *
     * @param cursor Cursor, positioned at current row
     *
     * @return the section text as an array with all levels in order
     */
    @Override
    public String[] getSectionText(@NonNull final Cursor cursor) {
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

    private void scaleViewText(@SuppressWarnings("unused") @NonNull final BooklistRowView rowView, @NonNull final View root) {
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
    public View getView(@NonNull final Cursor cursor,
                        @NonNull final LayoutInflater inflater,
                        @Nullable View convertView,
                        @NonNull final ViewGroup parent) {
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
    private String getAuthorFromRow(@NonNull final CatalogueDBAdapter db, @NonNull final BooklistRowView rowView) {
        if (rowView.hasAuthorId() && rowView.getAuthorId() > 0) {
            return db.getAuthor(rowView.getAuthorId()).getDisplayName();
        } else if (rowView.getKind() == RowKinds.ROW_KIND_BOOK) {
            List<Author> authors = db.getBookAuthorList(rowView.getBookId());
            if (authors.size() > 0)
                return authors.get(0).getDisplayName();
        }
        return null;
    }

    @Nullable
    private String getSeriesFromRow(@NonNull final CatalogueDBAdapter db, @NonNull final BooklistRowView rowView) {
        if (rowView.hasSeriesId() && rowView.getSeriesId() > 0) {
            Series s = db.getSeriesById(rowView.getSeriesId());
            if (s != null) {
                return s.name;
            }
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
    private void addMenuItem(@NonNull final List<SimpleDialogItem> items,
                             final int id, final int stringId, final int iconId) {
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
                                 @NonNull final List<SimpleDialogItem> menu) {
        try {
            switch (rowView.getKind()) {
                case RowKinds.ROW_KIND_BOOK: {
                    addMenuItem(menu, R.id.MENU_BOOK_DELETE, R.string.menu_delete, R.drawable.ic_delete);
                    addMenuItem(menu, R.id.MENU_BOOK_EDIT, R.string.edit_book, R.drawable.ic_mode_edit);
                    addMenuItem(menu, R.id.MENU_BOOK_EDIT_NOTES, R.string.edit_book_notes,R.drawable.ic_note);
                    if (rowView.isRead()) {
                        addMenuItem(menu, R.id.MENU_BOOK_UNREAD, R.string.menu_mark_as_unread, R.drawable.btn_check_buttonless_off);
                    } else {
                        addMenuItem(menu, R.id.MENU_BOOK_READ, R.string.menu_mark_as_read, R.drawable.btn_check_buttonless_on);
                    }
                    addMenuItem(menu, R.id.MENU_BOOK_EDIT_LOANS, R.string.edit_book_friends, R.drawable.ic_people);
                    addMenuItem(menu, R.id.MENU_BOOK_SEND_TO_GOODREADS, R.string.edit_book_send_to_gr, R.drawable.ic_menu_goodreads_holo_dark);
                    break;
                }
                case RowKinds.ROW_KIND_AUTHOR: {
                    addMenuItem(menu, R.id.MENU_AUTHOR_EDIT, R.string.menu_edit_author, R.drawable.ic_mode_edit);
                    break;
                }
                case RowKinds.ROW_KIND_SERIES: {
                    long id = rowView.getSeriesId();
                    if (id != 0) {
                        addMenuItem(menu, R.id.MENU_SERIES_DELETE, R.string.menu_delete_series, R.drawable.ic_delete);
                        addMenuItem(menu, R.id.MENU_SERIES_EDIT, R.string.menu_edit_series,  R.drawable.ic_mode_edit);
                    }
                    break;
                }

                case RowKinds.ROW_KIND_PUBLISHER: {
                    String s = rowView.getPublisherName();
                    if (!s.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_PUBLISHER_EDIT, R.string.menu_edit_publisher, R.drawable.ic_mode_edit);
                    }
                    break;
                }
                case RowKinds.ROW_KIND_LANGUAGE: {
                    String s = rowView.getLanguage();
                    if (!s.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_LANGUAGE_EDIT, R.string.menu_edit_language, R.drawable.ic_mode_edit);
                    }
                    break;
                }
                case RowKinds.ROW_KIND_LOCATION: {
                    String s = rowView.getLocation();
                    if (!s.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_LOCATION_EDIT, R.string.menu_edit_location, R.drawable.ic_mode_edit);
                    }
                    break;
                }
                case RowKinds.ROW_KIND_GENRE: {
                    String s = rowView.getGenre();
                    if (!s.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_GENRE_EDIT, R.string.menu_edit_genre, R.drawable.ic_mode_edit);
                    }
                    break;
                }
                case RowKinds.ROW_KIND_FORMAT: {
                    String s = rowView.getFormat();
                    if (!s.isEmpty()) {
                        addMenuItem(menu, R.id.MENU_FORMAT_EDIT, R.string.menu_edit_format, R.drawable.ic_mode_edit);
                    }
                    break;
                }
            }

            // add search by author ?
            boolean hasAuthor = (rowView.hasAuthorId() && rowView.getAuthorId() > 0);
            if (hasAuthor) {
                addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, R.string.amazon_books_by_author, R.drawable.ic_search);
            }

            // add search by series ?
            boolean hasSeries = (rowView.hasSeriesId() && rowView.getSeriesId() > 0);
            if (hasSeries) {
                if (hasAuthor) {
                    addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, R.string.amazon_books_by_author_in_series,R.drawable.ic_search);
                }
                addMenuItem(menu, R.id.MENU_AMAZON_BOOKS_IN_SERIES, R.string.amazon_books_in_series,R.drawable.ic_search);
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
     * @param db        CatalogueDBAdapter
     * @param bookView  The view on which was clicked
     * @param rowView   Row view for affected cursor row
     * @param context   Calling Activity
     * @param itemId    Related MenuItem
     *
     * @return True, if handled.
     */
    public boolean onContextItemSelected(@NonNull final CatalogueDBAdapter db,
                                         @NonNull final View bookView,
                                         @NonNull final BooklistRowView rowView,
                                         @NonNull final Activity context,
                                         final int itemId) {
        switch (itemId) {

            case R.id.MENU_BOOK_DELETE: {
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
            }
            case R.id.MENU_BOOK_EDIT: {
                // Start the activity in the correct tab
                BookDetailsActivity.startEditMode(context, rowView.getBookId(), BookDetailsActivity.TAB_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_NOTES: {
                // Start the activity in the correct tab
                BookDetailsActivity.startEditMode(context, rowView.getBookId(), BookDetailsActivity.TAB_EDIT_NOTES);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_LOANS: {
                // Start the activity in the correct tab
                BookDetailsActivity.startEditMode(context, rowView.getBookId(), BookDetailsActivity.TAB_EDIT_FRIENDS);
                return true;
            }
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
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
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
                qm.enqueueTask(task, BCQueueManager.QUEUE_MAIN);
                return true;
            }
            case R.id.MENU_SERIES_EDIT: {
                long id = rowView.getSeriesId();
                if (id == -1) {
                    Toast.makeText(context, R.string.cannot_edit_system, Toast.LENGTH_LONG).show();
                    System.out.println("FIXME id==-1, ... how? why? R.string.cannot_edit_system)");
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
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                long id = rowView.getSeriesId();
                Series series = db.getSeriesById(id);
                if (series != null) {
                    StandardDialogs.deleteSeriesAlert(context, db, series, new Runnable() {
                        @Override
                        public void run() {
                            // Let the Activity know
                            if (context instanceof BooklistChangeListener) {
                                final BooklistChangeListener l = (BooklistChangeListener) context;
                                l.onBooklistChange(BooklistChangeListener.FLAG_SERIES);
                            }
                        }
                    });
                } else {
                    Logger.logError("Series " + id + " not found in database?");
                }
                return true;
            }
            case R.id.MENU_AUTHOR_EDIT: {
                Author s = db.getAuthor(rowView.getAuthorId());
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
                d.edit(s);
                return true;
            }
            case R.id.MENU_PUBLISHER_EDIT: {
                Publisher s = new Publisher(rowView.getPublisherName());
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
                d.edit(s);
                return true;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                String s = rowView.getLanguage();
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
                d.edit(s);
                return true;
            }
            case R.id.MENU_LOCATION_EDIT: {
                String s = rowView.getLocation();
                EditLocationDialog d = new EditLocationDialog(context, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_LOCATION);
                        }
                    }
                });
                d.edit(s);
                return true;
            }
            case R.id.MENU_GENRE_EDIT: {
                String s = rowView.getGenre();
                EditGenreDialog d = new EditGenreDialog(context, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (context instanceof BooklistChangeListener) {
                            final BooklistChangeListener l = (BooklistChangeListener) context;
                            l.onBooklistChange(BooklistChangeListener.FLAG_GENRE);
                        }
                    }
                });
                d.edit(s);
                return true;
            }
            case R.id.MENU_FORMAT_EDIT: {
                String s = rowView.getFormat();
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
                d.edit(s);
                return true;
            }
            case R.id.MENU_BOOK_READ: {
                BookUtils.setRead(db, rowView.getBookId(), true);
                // maybe not elegant, but avoids a list rebuild
                Checkable readView = bookView.findViewById(R.id.read);
                readView.toggle();
                return true;
            }
            case R.id.MENU_BOOK_UNREAD: {
                BookUtils.setRead(db, rowView.getBookId(), false);
                // maybe not elegant, but avoids a list rebuild
                Checkable readView = bookView.findViewById(R.id.read);
                readView.toggle();
                return true;
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
                return new GenericStringHolder(rowView, DOM_BOOK_GENRE, R.string.no_genre);
            case RowKinds.ROW_KIND_LANGUAGE:
                return new GenericStringHolder(rowView, DOM_BOOK_LANGUAGE, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_AUTHOR:
                return new GenericStringHolder(rowView, DOM_AUTHOR_FORMATTED, R.string.no_author);
            case RowKinds.ROW_KIND_FORMAT:
                return new GenericStringHolder(rowView, DOM_BOOK_FORMAT, R.string.empty_with_brackets);
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
                return new GenericStringHolder(rowView, DOM_BOOK_LOCATION, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_UPDATE_YEAR:
                return new GenericStringHolder(rowView, DOM_UPDATE_YEAR, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_UPDATE_MONTH:
                return new MonthHolder(rowView, DOM_UPDATE_MONTH.name);
            case RowKinds.ROW_KIND_UPDATE_DAY:
                return new GenericStringHolder(rowView, DOM_UPDATE_DAY, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_BOOKSHELF:
                return new GenericStringHolder(rowView, DOM_BOOKSHELF_NAME, R.string.empty_with_brackets);
            case RowKinds.ROW_KIND_RATING:
                return new RatingHolder(rowView, DOM_BOOK_RATING.name);

            default:
                throw new RuntimeException("Invalid row kind " + k);
        }
    }

    public interface BooklistChangeListener {
        int FLAG_AUTHOR = 1;
        int FLAG_SERIES = (1 << 1);
        int FLAG_FORMAT = (1 << 2);
        int FLAG_PUBLISHER = (1 << 3);
        int FLAG_LANGUAGE = (1 << 4);
        int FLAG_LOCATION = (1 << 5);
        int FLAG_GENRE = (1 << 6);

        void onBooklistChange(int flags);
    }

    /**
     * Background task to get 'extra' details for a book row. Doing this in a
     * background task keeps the booklist cursor simple and small.
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
        private final int mFlags;

        /**
         * Constructor.
         *
         * @param bookId Book to fetch
         * @param holder View holder of view for the book
         */
        GetBookExtrasTask(final long bookId, @NonNull final BookHolder holder, final int flags) {
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
        public void run(@NonNull final SimpleTaskContext taskContext) {
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
                                mAuthorCol = c.getColumnIndex(UniqueId.KEY_AUTHOR_FORMATTED);
                            //if (mLocationRes == null)
                            //	mLocationRes = BookCatalogueApp.getResourceString(R.string.location);

                            mAuthor = c.getString(mAuthorCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_LOCATION) != 0) {
                            if (mLocationCol < 0) {
                                mLocationCol = c.getColumnIndex(UniqueId.KEY_BOOK_LOCATION);
                            }
                            if (mLocationRes == null) {
                                mLocationRes = BookCatalogueApp.getResourceString(R.string.location);
                            }

                            mLocation = mLocationRes + ": " + c.getString(mLocationCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                            if (mPublisherCol < 0) {
                                mPublisherCol = c.getColumnIndex(UniqueId.KEY_PUBLISHER);
                            }
                            if (mPublisherRes == null) {
                                mPublisherRes = BookCatalogueApp.getResourceString(R.string.publisher);
                            }

                            mPublisher = mPublisherRes + ": " + c.getString(mPublisherCol);
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_FORMAT) != 0) {
                            if (mFormatCol < 0) {
                                mFormatCol = c.getColumnIndex(UniqueId.KEY_BOOK_FORMAT);
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
    public abstract static class BooklistHolder extends MultiTypeHolder<BooklistRowView> {
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
        public void setText(@NonNull final TextView view, @Nullable final String s, final int emptyStringId, final int level) {
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
     * Holder for a {@link RowKinds#ROW_KIND_BOOK} row.
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
        public void map(@NonNull final BooklistRowView rowView, @NonNull final View bookView) {
            final BooklistStyle style = rowView.getStyle();

            title = bookView.findViewById(R.id.title);

            // scaling is done on the covers and on the 'read' icon
            float scale = style.isCondensed() ? 0.8f : 1.0f;

            read = bookView.findViewById(R.id.read);
            if (!FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_READ)) {
                read.setVisibility(View.GONE);
            } //else {
            // use the title text size to scale the 'read' icon
            // Since using CheckedTextView, this makes the icon to small or gone altogether
//                final int iconSize = (int) (title.getTextSize() * scale);
//                read.setMaxHeight(iconSize);
//                read.setMaxWidth(iconSize);
//                read.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, iconSize));
//            }

            seriesNum = bookView.findViewById(R.id.series_num);
            seriesNumLong = bookView.findViewById(R.id.series_num_long);
            if (!rowView.hasSeries()) {
                seriesNum.setVisibility(View.GONE);
                seriesNumLong.setVisibility(View.GONE);
            }

            final int extras = style.getExtras();

            cover = bookView.findViewById(R.id.cover);
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

            shelves = bookView.findViewById(R.id.shelves);
            if ((extras & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                shelves.setVisibility(View.VISIBLE);
            } else {
                shelves.setVisibility(View.GONE);
            }

            author = bookView.findViewById(R.id.author);
            if ((extras & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                author.setVisibility(View.VISIBLE);
            } else {
                author.setVisibility(View.GONE);
            }

            location = bookView.findViewById(R.id.location);
            if ((extras & BooklistStyle.EXTRAS_LOCATION) != 0) {
                location.setVisibility(View.VISIBLE);
            } else {
                location.setVisibility(View.GONE);
            }

            publisher = bookView.findViewById(R.id.publisher);
            if ((extras & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                publisher.setVisibility(View.VISIBLE);
            } else {
                publisher.setVisibility(View.GONE);
            }

            format = bookView.findViewById(R.id.format);
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
            bookView.setPadding(level * 5, 0, 0, 0);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowView, @NonNull final View v, final int level) {

            final int extras = rowView.getStyle().getExtras();

            // Title
            title.setText(rowView.getTitle());

            // Series details
            if (rowView.hasSeries()) {
                final String number = rowView.getSeriesNumber();
                String name = rowView.getSeriesName();
                if (name != null && name.isEmpty()) {
                    // Hide it.
                    seriesNum.setVisibility(View.GONE);
                    seriesNumLong.setVisibility(View.GONE);
                } else {
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
            if (FieldVisibilityActivity.isVisible(UniqueId.KEY_BOOK_READ)) {
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
                        rowView.getBookUuid(), rowView.getMaxThumbnailWidth(), rowView.getMaxThumbnailHeight(),
                        true,
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
        public View newView(@NonNull final BooklistRowView rowView,
                            @NonNull final LayoutInflater inflater,
                            @NonNull final ViewGroup parent,
                            final int level) {
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

        MonthHolder(@NonNull final BooklistRowView rowView, @NonNull final String source) {
            mSource = source;
            mSourceCol = rowView.getColumnIndex(mSource);
        }

        @Override
        public void map(@NonNull final BooklistRowView rowView, @NonNull final View v) {
            rowInfo = v.findViewById(R.id.ROW_INFO);
            text = v.findViewById(R.id.name);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowView, @NonNull final View v, final int level) {
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
        public View newView(@NonNull final BooklistRowView rowView,
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
    public static class RatingHolder extends BooklistHolder {
        /** Source column name */
        private final String mSource;
        /** Source column number */
        private final int mSourceCol;
        /** TextView for month name */
        TextView text;

        RatingHolder(@NonNull final BooklistRowView rowView,
                     @NonNull final String source) {
            mSource = source;
            mSourceCol = rowView.getColumnIndex(mSource);
        }

        @Override
        public void map(@NonNull final BooklistRowView rowView, @NonNull final View v) {
            rowInfo = v.findViewById(R.id.ROW_INFO);
            text = v.findViewById(R.id.name);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowView, @NonNull final View v, final int level) {
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
        public View newView(@NonNull final BooklistRowView rowView,
                            @NonNull final LayoutInflater inflater,
                            @NonNull final ViewGroup parent,
                            final int level) {
            return inflater.inflate(getDefaultLayoutId(level), parent, false);
        }
    }

    /**
     * Holder to handle any field that can be displayed as a simple string.
     * Assumes there is a 'name' TextView and an optional enclosing ViewGroup
     * called ROW_INFO.
     *
     * @author Philip Warner
     */
    public class GenericStringHolder extends BooklistHolder {
        /** Index of related data column */
        private final int mColIndex;
        /*** Field to use */
        TextView text;
        /** String ID to use when data is blank */
        private final int mNoDataId;

        /**
         * Constructor
         *
         * @param rowView  Row view that represents a typical row of this kind.
         * @param domain   Domain name to use
         * @param noDataId String ID to use when data is blank
         */
        private GenericStringHolder(@NonNull final BooklistRowView rowView,
                                    @NonNull final DomainDefinition domain,
                                    final int noDataId) {
            mColIndex = rowView.getColumnIndex(domain.name);
            if (mColIndex < 0)
                throw new RuntimeException("Domain '" + domain.name + "'not found in row view");
            mNoDataId = noDataId;
        }

        @Override
        public void map(@NonNull final BooklistRowView rowView, @NonNull final View v) {
            rowInfo = v.findViewById(R.id.ROW_INFO);
            text = v.findViewById(R.id.name);
        }

        @Override
        public void set(@NonNull final BooklistRowView rowView, @NonNull final View v, final int level) {
            String s = rowView.getString(mColIndex);
            setText(text, s, mNoDataId, level);
        }

        @Override
        public View newView(@NonNull final BooklistRowView rowView,
                            @NonNull final LayoutInflater inflater,
                            @NonNull final ViewGroup parent,
                            final int level) {
            return inflater.inflate(getDefaultLayoutId(level), parent, false);
        }
    }
}
