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
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
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

import com.eleybourn.bookcatalogue.adapters.MultiTypeListCursorAdapter;
import com.eleybourn.bookcatalogue.adapters.MultiTypeListHandler;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.booklist.BooklistSupportProvider;
import com.eleybourn.bookcatalogue.booklist.RowKinds;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursorRow;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditAuthorDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditFormatDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditGenreDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditLanguageDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditLocationDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditPublisherDialog;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialogs.EditSeriesDialog;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.searches.amazon.AmazonUtils;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.BookUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_LAST_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_STATUS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE_LETTER;

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
    public int getItemViewType(final @NonNull Cursor cursor) {
        BooklistCursorRow rowView = ((BooklistSupportProvider) cursor).getCursorRow();
        return rowView.getRowKind();
    }

    /**
     * Return the number of different View types in this list.
     */
    @Override
    public int getViewTypeCount() {
        return RowKinds.size();
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
    @Nullable
    @Override
    public String[] getSectionText(final @NonNull Cursor cursor) {
        Tracker.enterFunction(this, "getSectionTextForPosition", cursor);
        BooklistCursorRow rowView = ((BooklistSupportProvider) cursor).getCursorRow();
        String[] st = new String[]{rowView.getLevel1Data(), rowView.getLevel2Data()};
        Tracker.exitFunction(this, "getSectionTextForPosition");
        return st;
    }

    /**
     * Return the *absolute* position of the passed view in the list of books.
     */
    int getAbsolutePosition(@NonNull View v) {
        final BooklistHolder holder = ViewTagger.getTagOrThrow(v, R.id.TAG_HOLDER);// value: BooklistHolder suited for the ROW_KIND
        return holder.absolutePosition;
    }

    private void scaleViewText(@SuppressWarnings("unused") final @NonNull BooklistCursorRow rowView, final @NonNull View root) {

        if (root instanceof TextView) {
            TextView txt = (TextView) root;
            float px = txt.getTextSize();
            txt.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * BooklistStyle.SCALE);
        }
		/*
		 * No matter what I tried, this particular piece of code does not seem to work.
		 * All image scaling is moved to the relevant holder constructors until the
		 * reason this code fails is understood.
		 *
		if (root instanceof ImageView) {
			ImageView img = (ImageView) root;
			if (img.getLongFromBundles() == R.id.read) {
				img.setMaxHeight((int) (30*BooklistStyle.SCALE));
				img.setMaxWidth((int) (30*BooklistStyle.SCALE) );
				Logger.info("SCALE READ");
				img.requestLayout();
			} else if (img.getLongFromBundles() == R.id.cover) {
				img.setMaxHeight((int) (rowView.getMaxThumbnailHeight()*BooklistStyle.SCALE));
				img.setMaxWidth((int) (rowView.getMaxThumbnailWidth()*BooklistStyle.SCALE) );
				LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, (int) (rowView.getMaxThumbnailHeight()*BooklistStyle.SCALE) );
				img.setLayoutParams(lp);
				img.requestLayout();
				Logger.info("SCALE COVER");
			} else {
				Logger.info("UNKNOWN IMAGE");
			}
		}
		 */

        root.setPadding(
                (int) (BooklistStyle.SCALE * root.getPaddingLeft()),
                (int) (BooklistStyle.SCALE * root.getPaddingTop()),
                (int) (BooklistStyle.SCALE * root.getPaddingRight()),
                (int) (BooklistStyle.SCALE * root.getPaddingBottom()));

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
    public View getView(final @NonNull Cursor cursor,
                        final @NonNull LayoutInflater inflater,
                        @Nullable View convertView,
                        final @NonNull ViewGroup parent) {

        final BooklistCursorRow rowView = ((BooklistSupportProvider) cursor).getCursorRow();
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
            ViewTagger.setTag(convertView, R.id.TAG_HOLDER, holder);// value: BooklistHolder suited for the ROW_KIND
            // Indent based on level; we assume rows of a given type only occur at the same level
        } else {
            // recycling convertView
            holder = ViewTagger.getTagOrThrow(convertView, R.id.TAG_HOLDER);// value: BooklistHolder suited for the ROW_KIND
        }

        holder.absolutePosition = rowView.getAbsolutePosition();
        holder.set(rowView, convertView, level);
        return convertView;
    }

    @Nullable
    private String getAuthorFromRow(final @NonNull CatalogueDBAdapter db, final @NonNull BooklistCursorRow rowView) {
        if (rowView.hasAuthorId() && rowView.getAuthorId() > 0) {
            Author author = db.getAuthor(rowView.getAuthorId());
            Objects.requireNonNull(author);
            return author.getDisplayName();

        } else if (rowView.getRowKind() == RowKinds.ROW_KIND_BOOK) {
            List<Author> authors = db.getBookAuthorList(rowView.getBookId());
            if (authors.size() > 0) {
                return authors.get(0).getDisplayName();
            }
        }
        return null;
    }

    @Nullable
    private String getSeriesFromRow(final @NonNull CatalogueDBAdapter db, final @NonNull BooklistCursorRow rowView) {
        if (rowView.hasSeriesId() && rowView.getSeriesId() > 0) {
            Series s = db.getSeries(rowView.getSeriesId());
            if (s != null) {
                return s.name;
            }
        } else if (rowView.getRowKind() == RowKinds.ROW_KIND_BOOK) {
            ArrayList<Series> series = db.getBookSeriesList(rowView.getBookId());
            if (series.size() > 0) {
                return series.get(0).name;
            }
        }
        return null;
    }

    /**
     * Utility routine to add 'standard' menu options based on row type.
     *
     * @param cursorRow Row view pointing to current row for this context menu
     *
     * @see SelectOneDialog.SimpleDialogMenuItem : can't use SubMenu.
     */
    public void prepareListViewContextMenu(final @NonNull Menu /* in/out */ menu,
                                           final @NonNull BooklistCursorRow cursorRow) {
        menu.clear();

        switch (cursorRow.getRowKind()) {
            case RowKinds.ROW_KIND_BOOK: {
                if (cursorRow.isRead()) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_UNREAD, 0, R.string.set_as_unread)
                            .setIcon(R.drawable.btn_check_buttonless_off);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_READ, 0, R.string.set_as_read)
                            .setIcon(R.drawable.btn_check_buttonless_on);
                }

                menu.add(Menu.NONE, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete_book)
                        .setIcon(R.drawable.ic_delete);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT, 0, R.string.menu_edit_book)
                        .setIcon(R.drawable.ic_mode_edit);
                menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_NOTES, 0, R.string.menu_edit_book_notes)
                        .setIcon(R.drawable.ic_note);

                if (Fields.isVisible(UniqueId.KEY_LOAN_LOANED_TO)) {
                    menu.add(Menu.NONE, R.id.MENU_BOOK_EDIT_LOAN, 0, R.string.menu_edit_book_loan)
                            .setIcon(R.drawable.ic_people);
                }

                menu.add(Menu.NONE, R.id.MENU_SHARE, 0, R.string.share)
                        .setIcon(R.drawable.ic_share);

                menu.add(Menu.NONE, R.id.MENU_BOOK_SEND_TO_GOODREADS, 0, R.string.gr_menu_send_to_goodreads)
                        .setIcon(BookCatalogueApp.getAttr(R.attr.ic_goodreads));
                break;
            }
            case RowKinds.ROW_KIND_AUTHOR: {
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_DETAILS, 0, R.string.menu_author_details)
                        .setIcon(R.drawable.ic_details);
                menu.add(Menu.NONE, R.id.MENU_AUTHOR_EDIT, 0, R.string.menu_edit_author)
                        .setIcon(R.drawable.ic_mode_edit);
                break;
            }
            case RowKinds.ROW_KIND_SERIES: {
                long id = cursorRow.getSeriesId();
                if (id != 0) {
                    menu.add(Menu.NONE, R.id.MENU_SERIES_DELETE, 0, R.string.menu_delete_series)
                            .setIcon(R.drawable.ic_delete);
                    menu.add(Menu.NONE, R.id.MENU_SERIES_EDIT, 0, R.string.menu_edit_series)
                            .setIcon(R.drawable.ic_mode_edit);
                }
                break;
            }
            case RowKinds.ROW_KIND_PUBLISHER: {
                String s = cursorRow.getPublisherName();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_PUBLISHER_EDIT, 0, R.string.menu_edit_publisher)
                            .setIcon(R.drawable.ic_mode_edit);
                }
                break;
            }
            case RowKinds.ROW_KIND_LANGUAGE: {
                String s = cursorRow.getLanguageCode();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT, 0, R.string.menu_edit_language)
                            .setIcon(R.drawable.ic_mode_edit);
                }
                break;
            }
            case RowKinds.ROW_KIND_LOCATION: {
                String s = cursorRow.getLocation();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT, 0, R.string.menu_edit_location)
                            .setIcon(R.drawable.ic_mode_edit);
                }
                break;
            }
            case RowKinds.ROW_KIND_GENRE: {
                String s = cursorRow.getGenre();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT, 0, R.string.menu_edit_genre)
                            .setIcon(R.drawable.ic_mode_edit);
                }
                break;
            }
            case RowKinds.ROW_KIND_FORMAT: {
                String s = cursorRow.getFormat();
                if (!s.isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT, 0, R.string.menu_edit_format)
                            .setIcon(R.drawable.ic_mode_edit);
                }
                break;
            }
        }

        // add search menus ?
        boolean hasAuthor = (cursorRow.hasAuthorId() && cursorRow.getAuthorId() > 0);
        boolean hasSeries = (cursorRow.hasSeriesId() && cursorRow.getSeriesId() > 0);
        if (hasAuthor || hasSeries) {
            SubMenu subMenu = menu.addSubMenu(R.string.menu_search);

            if (hasAuthor) {
                subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR, 0, R.string.menu_amazon_books_by_author)
                        .setIcon(R.drawable.ic_search);
            }

            // add search by series ?
            if (hasSeries) {
                if (hasAuthor) {
                    subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES, 0, R.string.menu_amazon_books_by_author_in_series)
                            .setIcon(R.drawable.ic_search);
                }
                subMenu.add(Menu.NONE, R.id.MENU_AMAZON_BOOKS_IN_SERIES, 0, R.string.menu_amazon_books_in_series)
                        .setIcon(R.drawable.ic_search);
            }
        }
    }

    /**
     * Handle the 'standard' menu items. If the passed activity implements BooklistChangeListener then
     * inform it when changes have been made.
     *
     * ENHANCE: Consider using {@link LocalBroadcastManager} instead to all BooklistChangeListener
     *
     * @param menuItem  Related MenuItem
     * @param db        CatalogueDBAdapter
     * @param cursorRow Row view for affected cursor row
     * @param activity  Calling Activity
     *
     * @return <tt>true</tt> if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean onContextItemSelected(final @NonNull MenuItem menuItem,
                                  final @NonNull CatalogueDBAdapter db,
                                  final @NonNull BooklistCursorRow cursorRow,
                                  final @NonNull FragmentActivity activity) {

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
                            final BooklistChangeListener listener = (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                    BooklistChangeListener.FLAG_AUTHOR | BooklistChangeListener.FLAG_SERIES);
                        }
                    }
                });
                // Display an error, if any
                if (result != 0) {
                    StandardDialogs.showUserMessage(activity, result);
                }
                return true;
            }
            case R.id.MENU_BOOK_EDIT: {
                EditBookActivity.startActivityForResult(activity, /* 01564e26-b463-425e-8889-55a8228c82d5 */
                        bookId, EditBookFragment.TAB_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_NOTES: {
                EditBookActivity.startActivityForResult(activity, /* 8a5c649a-e97b-4d53-8133-6060ef3c3072 */
                        bookId, EditBookFragment.TAB_EDIT_NOTES);
                return true;
            }
            case R.id.MENU_BOOK_EDIT_LOAN: {
                EditBookActivity.startActivityForResult(activity, /* 0308715c-e1d2-4a7f-9ba3-cb8f641e096b */
                        bookId, EditBookFragment.TAB_EDIT_LOANS);
                return true;
            }
            case R.id.MENU_SHARE: {
                BookUtils.shareBook(activity, db, bookId);
                return true;
            }

            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                //TEST sendOneBookToGoodreads
                GoodreadsUtils.sendOneBookToGoodreads(activity, bookId);
                return true;
            }
            case R.id.MENU_SERIES_EDIT: {
                long id = cursorRow.getSeriesId();
                if (id == -1) {
                    StandardDialogs.showUserMessage(activity, R.string.warning_cannot_edit_system);
                    if (BuildConfig.DEBUG) {
                        Logger.debug("FIXME id==-1, ... how? why? R.string.cannot_edit_system)");
                    }
                } else {
                    Series s = db.getSeries(id);
                    Objects.requireNonNull(s);
                    EditSeriesDialog d = new EditSeriesDialog(activity, db, new Runnable() {
                        @Override
                        public void run() {
                            // Let the Activity know
                            if (activity instanceof BooklistChangeListener) {
                                final BooklistChangeListener listener = (BooklistChangeListener) activity;
                                listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                        BooklistChangeListener.FLAG_SERIES);
                            }
                        }
                    });
                    d.edit(s);
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
                                final BooklistChangeListener listener = (BooklistChangeListener) activity;
                                listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
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
                            final BooklistChangeListener listener = (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                    BooklistChangeListener.FLAG_AUTHOR);
                        }
                    }
                });
                d.edit(author);
                return true;
            }
            case R.id.MENU_PUBLISHER_EDIT: {
                Publisher publisher = new Publisher(cursorRow.getPublisherName());
                EditPublisherDialog d = new EditPublisherDialog(activity, db, new Runnable() {
                    @Override
                    public void run() {
                        // Let the Activity know
                        if (activity instanceof BooklistChangeListener) {
                            final BooklistChangeListener listener = (BooklistChangeListener) activity;
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
                            final BooklistChangeListener listener = (BooklistChangeListener) activity;
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
                            final BooklistChangeListener listener = (BooklistChangeListener) activity;
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
                            final BooklistChangeListener listener = (BooklistChangeListener) activity;
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
                            final BooklistChangeListener listener = (BooklistChangeListener) activity;
                            listener.onBooklistChange(cursorRow.getStyle().getExtraFieldsStatus(),
                                    BooklistChangeListener.FLAG_FORMAT);
                        }
                    }
                });
                d.edit(format);
                return true;
            }
            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle the read status
                if (BookUtils.setRead(db, bookId, !cursorRow.isRead())) {
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

            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                AmazonUtils.openSearchPage(activity, getAuthorFromRow(db, cursorRow), null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                AmazonUtils.openSearchPage(activity, null, getSeriesFromRow(db, cursorRow));
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                AmazonUtils.openSearchPage(activity, getAuthorFromRow(db, cursorRow), getSeriesFromRow(db, cursorRow));
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
    private BooklistHolder newHolder(final @NonNull BooklistCursorRow rowView) {
        final int k = rowView.getRowKind();

        switch (k) {
            // NEWKIND: ROW_KIND_x

            case RowKinds.ROW_KIND_BOOK:
                return new BookHolder();

            /* generic strings */
            case RowKinds.ROW_KIND_AUTHOR:
                return new GenericStringHolder(rowView, DOM_AUTHOR_FORMATTED.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_SERIES:
                return new GenericStringHolder(rowView, DOM_SERIES_NAME.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_TITLE_LETTER:
                return new GenericStringHolder(rowView, DOM_TITLE_LETTER.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_PUBLISHER:
                return new GenericStringHolder(rowView, DOM_BOOK_PUBLISHER.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_GENRE:
                return new GenericStringHolder(rowView, DOM_BOOK_GENRE.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_FORMAT:
                return new GenericStringHolder(rowView, DOM_BOOK_FORMAT.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_LOCATION:
                return new GenericStringHolder(rowView, DOM_BOOK_LOCATION.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_LOANED:
                return new GenericStringHolder(rowView, DOM_LOANED_TO.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_BOOKSHELF:
                return new GenericStringHolder(rowView, DOM_BOOKSHELF.name, R.string.not_set_with_brackets);

            /* some special formatting holders */
            case RowKinds.ROW_KIND_RATING:
                return new RatingHolder(rowView, DOM_BOOK_RATING.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_LANGUAGE:
                return new LanguageHolder(rowView, DOM_BOOK_LANGUAGE.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_READ_AND_UNREAD:
                return new ReadUnreadHolder(rowView, DOM_READ_STATUS.name, R.string.not_set_with_brackets);

            /* Dates */
            case RowKinds.ROW_KIND_DATE_PUBLISHED_YEAR:
                return new GenericStringHolder(rowView, DOM_DATE_PUBLISHED_YEAR.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_PUBLISHED_MONTH:
                return new MonthHolder(rowView, DOM_DATE_PUBLISHED_MONTH.name, R.string.not_set_with_brackets);

            case RowKinds.ROW_KIND_DATE_ACQUIRED_YEAR:
                return new GenericStringHolder(rowView, DOM_DATE_ACQUIRED_YEAR.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_ACQUIRED_MONTH:
                return new MonthHolder(rowView, DOM_DATE_ACQUIRED_MONTH.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_ACQUIRED_DAY:
                return new GenericStringHolder(rowView, DOM_DATE_ACQUIRED_DAY.name, R.string.not_set_with_brackets);

            case RowKinds.ROW_KIND_DATE_ADDED_YEAR:
                return new GenericStringHolder(rowView, DOM_DATE_ADDED_YEAR.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_ADDED_MONTH:
                return new MonthHolder(rowView, DOM_DATE_ADDED_MONTH.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_ADDED_DAY:
                return new GenericStringHolder(rowView, DOM_DATE_ADDED_DAY.name, R.string.not_set_with_brackets);

            case RowKinds.ROW_KIND_DATE_READ_YEAR:
                return new GenericStringHolder(rowView, DOM_DATE_READ_YEAR.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_READ_MONTH:
                return new MonthHolder(rowView, DOM_DATE_READ_MONTH.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_READ_DAY:
                return new GenericStringHolder(rowView, DOM_DATE_READ_DAY.name, R.string.not_set_with_brackets);

            case RowKinds.ROW_KIND_DATE_LAST_UPDATE_YEAR:
                return new GenericStringHolder(rowView, DOM_DATE_LAST_UPDATE_YEAR.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(rowView, DOM_DATE_UPDATE_MONTH.name, R.string.not_set_with_brackets);
            case RowKinds.ROW_KIND_DATE_LAST_UPDATE_DAY:
                return new GenericStringHolder(rowView, DOM_DATE_UPDATE_DAY.name, R.string.not_set_with_brackets);

            default:
                throw new RTE.IllegalTypeException("" + k);
        }
    }

    /**
     * Interface to be implemented by the Activity/Fragment that hosts a ListView
     * populated by a {@link com.eleybourn.bookcatalogue.booklist.BooklistBuilder}
     *
     * Allows to be notified of changes made to objects in the list.
     */
    public interface BooklistChangeListener {
        int FLAG_AUTHOR = 1;
        int FLAG_SERIES = (1 << 1);
        int FLAG_FORMAT = (1 << 2);
        int FLAG_PUBLISHER = (1 << 3);
        int FLAG_LANGUAGE = (1 << 4);
        int FLAG_LOCATION = (1 << 5);
        int FLAG_GENRE = (1 << 6);
        int FLAG_BOOK_READ = (1 << 7);

        /**
         * Called if global changes were made that (potentially) affect the whole list.
         *
         * @param extraFieldsInUse a bitmask with the Extra fields being used (visible) in the current
         *                         {@link BooklistStyle} as configured by the user
         * @param fieldsChanged    a bitmask build from the flags of {@link BooklistChangeListener}
         */
        void onBooklistChange(final int extraFieldsInUse, final int fieldsChanged);

        /**
         * Called if changes were made to a single book.
         *
         * @param extraFieldsInUse a bitmask with the Extra fields being used (visible) in the current
         *                         {@link BooklistStyle} as configured by the user
         * @param fieldsChanged    a bitmask build from the flags of {@link BooklistChangeListener}
         * @param rowPosition      the absolute position of the row in the cursor view
         * @param bookId           the book that was changed
         */
        void onBooklistChange(final int extraFieldsInUse, final int fieldsChanged,
                              final int rowPosition, final long bookId);
    }

    /**
     * Background task to get 'extra' details for a book row. Doing this in a
     * background task keeps the booklist cursor simple and small.
     *
     * @author Philip Warner
     */
    private static class GetBookExtrasTask implements SimpleTaskQueue.SimpleTask {

        static final int BKEY_HANDLED =
                BooklistStyle.EXTRAS_AUTHOR |
                        BooklistStyle.EXTRAS_LOCATION |
                        BooklistStyle.EXTRAS_PUBLISHER |
                        BooklistStyle.EXTRAS_FORMAT |
                        BooklistStyle.EXTRAS_BOOKSHELVES;

        /** Bookshelves resource string */
        final String mShelvesRes = BookCatalogueApp.getResourceString(R.string.lbl_bookshelves);
        /** Location resource string */
        final String mLocationRes = BookCatalogueApp.getResourceString(R.string.lbl_location);
        /** Publisher resource string */
        final String mPublisherRes = BookCatalogueApp.getResourceString(R.string.lbl_publisher);
        /** Format resource string */
        final String mFormatRes = BookCatalogueApp.getResourceString(R.string.lbl_format);

        /** The filled-in view holder for the book view. */
        @NonNull
        final BookHolder mHolder;
        /** The book ID to fetch */
        final long mBookId;
        /** Flags indicating which extras to get */
        private final int mFlags;
        /** Resulting location data */
        String mLocation;
        /** Resulting publisher data */
        String mPublisher;
        /** Resulting Format data */
        String mFormat;
        /** Resulting author data */
        String mAuthor;
        /** Resulting shelves data */
        String mShelves;
        /** Options indicating we want finished() to be called */
        private boolean mWantFinished = true;

        /**
         * Constructor.
         *
         * @param bookId Book to fetch
         * @param holder View holder of view for the book
         */
        GetBookExtrasTask(final long bookId, final @NonNull BookHolder holder, final int flags) {
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
        public void run(final @NonNull SimpleTaskContext taskContext) {
            try {
                // Make sure we are the right task.
                synchronized (mHolder) {
                    if (mHolder.extrasTask != this) {
                        mWantFinished = false;
                        return;
                    }
                }
                // Get a DB connection and find the book, do not close the database!
                CatalogueDBAdapter db = taskContext.getDb();

                try (BookCursor cursor = db.fetchBookById(mBookId)) {
                    // If we have a book, use it. Otherwise we are done.
                    if (cursor.moveToFirst()) {
                        BookCursorRow rowView = cursor.getCursorRow();

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
                                mPublisher = BookCatalogueApp.getResourceString(R.string.a_bracket_b_bracket,
                                        tmpPublisher, DateUtils.toPrettyDate(tmpPubDate));
                            } else {
                                mPublisher = tmpPublisher;
                            }
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_FORMAT) != 0) {
                            mFormat = rowView.getFormat();
                        }

                        if ((mFlags & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                            mShelves = db.getBookshelvesByBookIdAsStringList(mBookId);
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

                if ((mFlags & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                    mHolder.shelves.setText(BookCatalogueApp.getResourceString(R.string.name_colon_value, mShelvesRes, mShelves));
                }
                if ((mFlags & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    mHolder.author.setText(mAuthor);
                }
                if ((mFlags & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    mHolder.location.setText(BookCatalogueApp.getResourceString(R.string.name_colon_value, mLocationRes, mLocation));
                }
                if ((mFlags & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                    mHolder.publisher.setText(BookCatalogueApp.getResourceString(R.string.name_colon_value, mPublisherRes, mPublisher));
                }
                if ((mFlags & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    mHolder.format.setText(BookCatalogueApp.getResourceString(R.string.name_colon_value, mFormatRes, mFormat));
                }
            }
        }
    }

    /**
     * Implementation of general code used by Booklist holders.
     *
     * @author Philip Warner
     */
    public abstract static class BooklistHolder extends MultiTypeHolder<BooklistCursorRow> {
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
        @LayoutRes
        static int getDefaultLayoutId(final int level) {
            switch (level) {
                case 1:
                    return R.layout.booksonbookshelf_row_level_1;
                case 2:
                    return R.layout.booksonbookshelf_row_level_2;
                default:
                    return R.layout.booksonbookshelf_row_level_3;
            }
        }

        /**
         * For a simple row, just set the text (or hide it)
         *
         * @param view          View to set
         * @param s             String to display
         * @param emptyStringId String to display if first is empty and can not hide row
         * @param level         Level of this item (we never hide level 1 items).
         */
        public void setText(final @NonNull TextView view,
                            final @Nullable String s,
                            final @StringRes int emptyStringId,
                            final int level) {
            if (s == null || s.isEmpty()) {
                if (level > 1 && rowInfo != null) {
                    rowInfo.setVisibility(View.GONE);
                    return;
                }
                view.setText(BookCatalogueApp.getResourceString(emptyStringId));
            } else {
                if (rowInfo != null) {
                    rowInfo.setVisibility(View.VISIBLE);
                }
                view.setText(s);
            }
        }
    }

    /**
     * Holder to handle any field that can be displayed as a simple string.
     * Assumes there is a 'name' TextView and an optional enclosing ViewGroup
     * called ROW_INFO.
     *
     * @author Philip Warner
     */
    public static class GenericStringHolder extends BooklistHolder {
        /** Index of related data column */
        final int mSourceCol;
        /** String ID to use when data is blank */
        @StringRes
        final int mNoDataId;

        /*** View to populate */
        TextView mTextView;

        /**
         * Constructor
         *
         * @param rowView  Row view that represents a typical row of this kind.
         * @param source   Column name to use
         * @param noDataId String ID to use when data is blank
         */
        private GenericStringHolder(final @NonNull BooklistCursorRow rowView,
                                    final @NonNull String source,
                                    final @StringRes int noDataId) {
            mSourceCol = rowView.getColumnIndex(source);
            if (mSourceCol < 0) {
                throw new DBExceptions.ColumnNotPresent(source);
            }
            mNoDataId = noDataId;
        }

        @Override
        public void map(final @NonNull BooklistCursorRow rowView, final @NonNull View view) {
            rowInfo = view.findViewById(R.id.BLB_ROW_DETAILS);
            mTextView = view.findViewById(R.id.name);
        }

        @Override
        public void set(final @NonNull BooklistCursorRow rowView, final @NonNull View view, final int level) {
            String s = rowView.getString(mSourceCol);
            setText(mTextView, s, mNoDataId, level);
        }

        @Override
        public View newView(final @NonNull BooklistCursorRow rowView,
                            final @NonNull LayoutInflater inflater,
                            final @NonNull ViewGroup parent,
                            final int level) {
            return inflater.inflate(getDefaultLayoutId(level), parent, false);
        }
    }

    /**
     * Holder for a row that displays a 'rating'.
     *
     * @author Philip Warner
     */
    public static class RatingHolder extends GenericStringHolder {

        RatingHolder(final @NonNull BooklistCursorRow rowView,
                     final @NonNull String source,
                     final @StringRes int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(final @NonNull BooklistCursorRow rowView, final @NonNull View view, final int level) {
            String s = rowView.getString(mSourceCol);
            try {
                int i = (int) Float.parseFloat(s);
                // If valid, get the name
                if (i >= 0 && i <= 5) {
                    s = view.getContext().getResources().getQuantityString(R.plurals.n_stars, i, i);
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            setText(mTextView, s, mNoDataId, level);
        }
    }

    /**
     * Holder for a row that displays a 'language'.
     */
    public static class LanguageHolder extends GenericStringHolder {

        private LanguageHolder(final @NonNull BooklistCursorRow rowView,
                               final @NonNull String source,
                               final @StringRes int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(final @NonNull BooklistCursorRow rowView, final @NonNull View view, final int level) {
            String s = rowView.getString(mSourceCol);
            if (s != null && !s.isEmpty()) {
                s = LocaleUtils.getDisplayName(s);
            }
            setText(this.mTextView, s, mNoDataId, level);
        }
    }

    /**
     * Holder for a row that displays a 'language'.
     */
    public static class ReadUnreadHolder extends GenericStringHolder {

        private ReadUnreadHolder(final @NonNull BooklistCursorRow rowView,
                                 final @NonNull String source,
                                 final @StringRes int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(final @NonNull BooklistCursorRow rowView, final @NonNull View view, final int level) {
            String s = rowView.getString(mSourceCol);
            if (Datum.toBoolean(s, true)) {
                s = BookCatalogueApp.getResourceString(R.string.booklist_read);
            } else {
                s = BookCatalogueApp.getResourceString(R.string.booklist_unread);
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
    public static class MonthHolder extends GenericStringHolder {

        private MonthHolder(final @NonNull BooklistCursorRow rowView,
                            final @NonNull String source,
                            final @StringRes int noDataId) {
            super(rowView, source, noDataId);
        }

        @Override
        public void set(final @NonNull BooklistCursorRow rowView, final @NonNull View v, final int level) {
            String s = rowView.getString(mSourceCol);
            try {
                int i = Integer.parseInt(s);
                // If valid, get the name
                if (i > 0 && i <= 12) {
                    s = DateUtils.getMonthName(i);
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            setText(mTextView, s, mNoDataId, level);
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
        @Nullable
        GetBookExtrasTask extrasTask;

        @Override
        public void map(final @NonNull BooklistCursorRow rowView, final @NonNull View bookView) {
            final BooklistStyle style = rowView.getStyle();
            // scaling is done on the covers and on the 'read' icon
            float scale = style.isCondensed() ? BooklistStyle.SCALE : 1.0f;

            title = bookView.findViewById(R.id.title);

            // theoretically we should check Fields.isVisible(UniqueId.KEY_SERIES_NAME) but BooklistBuilder is not taking those settings into account
            seriesNum = bookView.findViewById(R.id.series_num);
            seriesNumLong = bookView.findViewById(R.id.series_num_long);
            if (!rowView.hasSeriesNumber()) {
                seriesNum.setVisibility(View.GONE);
                seriesNumLong.setVisibility(View.GONE);
            }

            read = bookView.findViewById(R.id.read);
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

            shelves = bookView.findViewById(R.id.shelves);
            shelves.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_BOOKSHELVES) != 0 ? View.VISIBLE : View.GONE);

            author = bookView.findViewById(R.id.author);
            author.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_AUTHOR) != 0 ? View.VISIBLE : View.GONE);

            location = bookView.findViewById(R.id.location);
            location.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_LOCATION) != 0 ? View.VISIBLE : View.GONE);

            publisher = bookView.findViewById(R.id.publisher);
            publisher.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_PUBLISHER) != 0 ? View.VISIBLE : View.GONE);

            format = bookView.findViewById(R.id.format);
            format.setVisibility((extraFieldsInUse & BooklistStyle.EXTRAS_FORMAT) != 0 ? View.VISIBLE : View.GONE);

            cover = bookView.findViewById(R.id.coverImage);
            if ((extraFieldsInUse & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
                cover.setVisibility(View.VISIBLE);

                LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                        (int) (rowView.getMaxThumbnailHeight() * scale));
                clp.setMargins(0, 1, 0, 1);
                cover.setLayoutParams(clp);
                cover.setScaleType(ScaleType.CENTER);
            } else {
                cover.setVisibility(View.GONE);
            }

            // The default is to indent all views based on the level, but with book covers on
            // the far left, it looks better if we 'out-dent' one step.
            int level = rowView.getLevel();
            if (level > 0) {
                --level;
            }
            bookView.setPadding(level * 5, 0, 0, 0);
        }

        @Override
        public void set(final @NonNull BooklistCursorRow rowView, final @NonNull View v, final int level) {

            final int extraFields = rowView.getStyle().getExtraFieldsStatus();

            // Title
            title.setText(rowView.getTitle());

            // Series details
            if (rowView.hasSeriesNumber()) {
                String name = rowView.getSeriesName();
                if (name == null || name.isEmpty()) {
                    // Hide it.
                    seriesNum.setVisibility(View.GONE);
                    seriesNumLong.setVisibility(View.GONE);
                } else {
                    final String number = rowView.getSeriesNumber();
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
                read.setChecked(rowView.isRead());
                // allow changing the read-status with a click.
                // disabled again, as this might not be a good idea. Use the context menu instead.
//                read.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        read.setChecked(!read.isChecked());
//                        BookUtils.setRead(rowView.getBookId(), read.isChecked());
//                    }
//                });
            }

            // Thumbnail
            if ((extraFields & BooklistStyle.EXTRAS_THUMBNAIL) != 0) {
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
                GetBookExtrasTask t = new GetBookExtrasTask(rowView.getBookId(), this, flags);
                mInfoQueue.enqueue(t);
            }
        }

        /**
         * The actual book entry
         */
        @Override
        public View newView(final @NonNull BooklistCursorRow rowView,
                            final @NonNull LayoutInflater inflater,
                            final @NonNull ViewGroup parent,
                            final int level) {
            // All book rows have the same type of view.
            return inflater.inflate(R.layout.booksonbookshelf_row_book, parent, false);
        }
    }
}
