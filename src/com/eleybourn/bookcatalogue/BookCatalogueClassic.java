/*
 * @copyright 2010 Evan Leybourn
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

import android.app.ExpandableListActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnGroupCollapseListener;
import android.widget.ExpandableListView.OnGroupExpandListener;
import android.widget.ImageView;
import android.widget.ResourceCursorTreeAdapter;
import android.widget.SectionIndexer;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.scanner.Scanner;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.SendOneBookTask;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;
import com.eleybourn.bookcatalogue.widgets.FastScrollExpandableListView;

import net.philipwarner.taskqueue.QueueManager;

import java.util.ArrayList;

/*
 * A book catalogue application that integrates with Google Books.
 */
public class BookCatalogueClassic extends ExpandableListActivity {

	// Target size of a thumbnail in a list (bbox dim)
	private static final int LIST_THUMBNAIL_SIZE=60;

	private CatalogueDBAdapter mDb;
	private static final int DELETE_ID = MenuHandler.FIRST + 7;
	private static final int EDIT_BOOK = MenuHandler.FIRST + 10;
	private static final int EDIT_BOOK_NOTES = MenuHandler.FIRST + 11;
	private static final int EDIT_BOOK_FRIENDS = MenuHandler.FIRST + 12;
	private static final int DELETE_SERIES_ID = MenuHandler.FIRST + 15;
	private static final int EDIT_AUTHOR_ID = MenuHandler.FIRST + 16;
	private static final int EDIT_SERIES_ID = MenuHandler.FIRST + 17;
	private static final int EDIT_BOOK_SEND_TO_GR = MenuHandler.FIRST + 19;

	private String mBookshelf = "";
	private ArrayAdapter<String> mSpinnerAdapter;
	private Spinner mBookshelfText;

	private SharedPreferences mPrefs;
	private int sort = 0;
	private static final int SORT_AUTHOR = 0;
	private static final int SORT_TITLE = 1;
	private static final int SORT_SERIES = 2;
	private static final int SORT_LOAN = 3;
	private static final int SORT_UNREAD = 4;
	private static final int SORT_GENRE = 5;
	private static final int SORT_AUTHOR_GIVEN = 6;
	private static final int SORT_AUTHOR_ONE = 7;
	private static final int SORT_PUBLISHED = 8;
	private ArrayList<Integer> currentGroup = new ArrayList<>();
	private Long mLoadingGroups = 0L;

	private SimpleTaskQueue mTaskQueue = null;

	private String search_query = "";
	// These are the states that get saved onPause
	private static final String STATE_SORT = "state_sort";
	private static final String STATE_CURRENT_GROUP_COUNT = "state_current_group_count";
	private static final String STATE_CURRENT_GROUP = "state_current_group";

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		//check which strings.xml file is currently active
		if (!getString(R.string.system_app_name).equals(Utils.APP_NAME)) {
			throw new NullPointerException();
		}

		mBookshelf = getString(R.string.all_books);
		try {
			super.onCreate(savedInstanceState);

			// In V4.0 the startup activity is StartupActivity, but we need to deal with old icons.
			// So we check the intent.
			/* FIXME ? is this not done already ? -> check! -> Consider renaming 'BookCatalogue' activity to 'BookCatalogueClassic' and
			         creating a dummy BookCatalogue activity stub to avoid this check
			 */
			if ( ! StartupActivity.hasBeenCalled() ) {
				// The startup activity has NOT been called; this may be because of a restart after FC, in which case the action may be null, or may be valid
				Intent i = getIntent();
				final String action = i.getAction();
				if (action != null && "android.intent.action.MAIN".equals(action) && i.hasCategory("android.intent.category.LAUNCHER")) {
					// This is a startup for the main application, so defer it to the StartupActivity
					Logger.logError("Old shortcut detected, redirecting");
					i = new Intent(this.getApplicationContext(), StartupActivity.class);
					startActivity(i);
					finish();
					return;
				}
			}

			// Extract the sort type from the bundle. getInt will return 0 if there is no attribute
			// sort (which is exactly what we want)
			try {
				mPrefs = BookCatalogueApp.getSharedPreferences();
				sort = mPrefs.getInt(STATE_SORT, sort);
				mBookshelf = mPrefs.getString(BooksOnBookshelf.PREF_BOOKSHELF, mBookshelf);
				loadCurrentGroup();
			} catch (Exception e) {
				Logger.logError(e);
			}
			// This sets the search capability to local (application) search
			setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
			setContentView(R.layout.classic_list_authors);
			mDb = new CatalogueDBAdapter(this);
			mDb.open();

			// Did the user search
			Intent intent = getIntent();
			if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
				// Return the search results instead of all books (for the bookshelf)
				search_query = intent.getStringExtra(SearchManager.QUERY).trim();
			} else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				// Handle a suggestions click (because the suggestions all use ACTION_VIEW)
				search_query = intent.getDataString();
			}
			if (search_query == null || ".".equals(search_query)) {
				search_query = "";
			}

			bookshelf();
			//fillData();

			registerForContextMenu(getExpandableListView());
		} catch (Exception e) {
			Logger.logError(e);
			// Need to finish this activity, otherwise we end up in an invalid state.
			finish();
		}
	}

	/**
	 * Setup the bookshelf spinner. This function will also call fillData when
	 * complete having loaded the appropriate bookshelf.
	 */
	private void bookshelf() {
		// Setup the Bookshelf Spinner
		mBookshelfText = findViewById(R.id.bookshelf_name);
		mSpinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_frontpage);
		mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mBookshelfText.setAdapter(mSpinnerAdapter);

		// Add the default All Books bookshelf
		int pos = 0;
		int bspos = pos;
		mSpinnerAdapter.add(getString(R.string.all_books));
		pos++;

		try (Cursor bookshelves = mDb.fetchAllBookshelves()) {
			if (bookshelves.moveToFirst()) {
				do {
					String this_bookshelf = bookshelves.getString(1);
					if (mBookshelf.equals(this_bookshelf)) {
						bspos = pos;
					}
					pos++;
					mSpinnerAdapter.add(this_bookshelf);
				}
				while (bookshelves.moveToNext());
			}
		}
		// Set the current bookshelf. We use this to force the correct bookshelf after
		// the state has been restored.
		mBookshelfText.setSelection(bspos);

		/*
		 * This is fired whenever a bookshelf is selected. It is also fired when the
		 * page is loaded with the default (or current) bookshelf.
		 */
		mBookshelfText.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
				String new_bookshelf = mSpinnerAdapter.getItem(position);
				if (position == 0) {
					new_bookshelf = "";
				}
				if (!mBookshelf.equals(new_bookshelf)) {
					currentGroup = new ArrayList<>();
				}
				mBookshelf = new_bookshelf;
				// save the current bookshelf into the preferences
				SharedPreferences.Editor ed = mPrefs.edit();
				ed.putString(BooksOnBookshelf.PREF_BOOKSHELF, mBookshelf);
				ed.apply();
				fillData();
			}

			public void onNothingSelected(AdapterView<?> parentView) {
				// Do Nothing

			}
		});

		ImageView mBookshelfDown = findViewById(R.id.bookshelf_down);
		mBookshelfDown.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBookshelfText.performClick();
				return;
			}
		});

		TextView mBookshelfNum = findViewById(R.id.bookshelf_num);
		mBookshelfNum.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mBookshelfText.performClick();
				return;
			}
		});

	}

	/*
	 * Class that handles list/view specific initializations etc.
	 * The member variable mViewManager is set early in object
	 * initialization of the containing class.
	 *
	 * All child views are assumed to have books in them and a single
	 * method call is made to bind a child view.
	 */
	private abstract class ViewManager {
		int mGroupIdColumnIndex;
		int mLayout = -1;		// Top level resource I
		int mChildLayout = -1;	// Child resource ID
		Cursor mCursor = null;	// Top level cursor
		String[] mFrom = null;	// Source fields for top level resource
		int[] mTo = null;		// Dest field resource IDs for top level
		// Methods to 'get' list/view related items
		public int getLayout() { return mLayout; }

		int getLayoutChild() { return mChildLayout; }

		public Cursor getCursor() {
			if (mCursor == null) {
				newGroupCursor();
				BookCatalogueClassic.this.startManagingCursor(mCursor);
			}
			return mCursor;
		}

		protected abstract Cursor newGroupCursor();
		public String[] getFrom() { return mFrom; }
		public int[] getTo() { return mTo; }
		/**
		 * Method to return the group cursor column that contains text that can be used
		 * to derive the section name used by the ListView overlay.
		 *
		 * @return	column number
		 */
		protected abstract int getSectionNameColumn();

		/**
		 * Get a cursor to retrieve list of children; must be a database cursor
		 * and will be converted to a CursorSnapshotCursor
		 */
		protected abstract SQLiteCursor getChildrenCursor(Cursor groupCursor);

		BasicBookListAdapter newAdapter(Context context) {
			return new BasicBookListAdapter(context);
		}

		/**
		 * Record to store the details of a TextView in the list items.
		 */
		private class TextViewInfo {
			boolean show;
			TextView view;
		}
		/**
		 * Record to store the details of a ImafeView in the list items.
		 */
		private class ImageViewInfo {
			boolean show;
			ImageView view;
		}

		/**
		 * Record to implement the 'holder' model for the list.
		 */
		private class BookHolder {
			final TextViewInfo author = new TextViewInfo();
			final TextViewInfo title = new TextViewInfo();
			final TextViewInfo series = new TextViewInfo();
			final ImageViewInfo image = new ImageViewInfo();
			final TextViewInfo publisher = new TextViewInfo();
			final ImageViewInfo read = new ImageViewInfo();
		}

		/**
		 * Adapter for the the expandable list of books. Uses ViewManager to manage
		 * cursor.
		 *
		 * @author Philip Warner
		 */
		public class BasicBookListAdapter extends ResourceCursorTreeAdapter implements SectionIndexer {

			/** A local Inflater for convenience */
			final LayoutInflater mInflater;

			/**
			 *
			 * Pass the parameters directly to the overridden function and
			 * create an Inflater for use later.
			 *
			 * Note: It would be great to pass a ViewManager to the constructor
			 * as an instance variable, but the 'super' initializer calls
			 * getChildrenCursor which needs the ViewManager...which can not be set
			 * before the call to 'super'...so we use an instance variable in the
			 * containing class.
			 *
			 * @param context
			 */
			private int[] mFromCols = null;
			private final int[] mToIds;
			BasicBookListAdapter(Context context) {
				super(context, ViewManager.this.getCursor(), ViewManager.this.getLayout(), ViewManager.this.getLayoutChild());

				mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				mToIds = ViewManager.this.getTo();
			}

			/**
			 * Bind the passed 'from' text fields to the 'to' fields.
			 *
			 * If anything more fancy is needed, we probably need to implement it
			 * in the subclass.
			 */
			@Override
			protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
				if (mFromCols == null) {
					String [] fromNames = ViewManager.this.getFrom();
					mFromCols = new int[fromNames.length];
					for(int i = 0 ; i < fromNames.length; i++ )
						mFromCols[i] = cursor.getColumnIndex(fromNames[i]);
				}
		        for (int i = 0; i < mToIds.length; i++) {
		            View v = view.findViewById(mToIds[i]);
		            if (v != null) {
		                String text = cursor.getString(mFromCols[i]);
		                if (text == null) {
		                    text = "";
		                }
		                if (v instanceof TextView) {
		                    ((TextView) v).setText(text);
		                } else {
		                    throw new IllegalStateException("Can only bind to TextView for groups");
		                }
		            }
		        }
			}

			/**
			 * Override the getChildrenCursor. This runs the SQL to extract the titles per author
			 */
			@Override
			protected Cursor getChildrenCursor(Cursor groupCursor) {
				if (mDb == null) // We are terminating
					return null;

				// Get the DB cursor
				SQLiteCursor children = ViewManager.this.getChildrenCursor(groupCursor);

				// // Make a snapshot of it to avoid keeping potentially hundreds of cursors open
				// // If we ever set Android 2.0 as minimum, do this...
				// CursorSnapshotCursor csc;
				// if (children instanceof BooksCursor) {
				// 	csc = new BooksSnapshotCursor(children);
				// } else {
				// 	csc = new CursorSnapshotCursor(children);
				// }
				// children.close();
				// BookCatalogue.this.startManagingCursor(csc);

				// TODO FIND A BETTER CURSOR MANAGEMENT SOLUTION!
				// THIS CAUSES CRASH IN HONEYCOMB when viewing book details then clicking 'back', so we have
				// overridden startManagingCursor to only close cursors in onDestroy().
				BookCatalogueClassic.this.startManagingCursor(children);
				return children;
			}

			/**
			 * Setup the related info record based on actual View contents
			 */
			private void initViewInfo(View v, TextViewInfo info, int id, String setting) {
				info.show = mPrefs.getBoolean(setting, true);
				info.view = v.findViewById(id);
				if (!info.show) {
					if (info.view != null)
						info.view.setVisibility(View.GONE);
				} else {
					info.show = (info.view != null);
					if (info.show)
						info.view.setVisibility(View.VISIBLE);
				}
			}
			/**
			 * Setup the related info record based on actual View contents
			 */
			private void initViewInfo(View v, ImageViewInfo info, int id, String setting) {
				info.show = mPrefs.getBoolean(setting, true);
				info.view = v.findViewById(id);
				if (!info.show) {
					info.view.setVisibility(View.GONE);
				} else {
					info.show = (info.view != null);
					if (info.show)
						info.view.setVisibility(View.VISIBLE);
				}
			}

			/**
			 * Override the newChildView method so we can implement a holder model to improve performance.
			 */
			@Override
			public View newChildView(Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
				View v = mInflater.inflate(ViewManager.this.getLayoutChild(), parent, false);
				BookHolder holder = new BookHolder();

				initViewInfo(v, holder.author, R.id.row_author, FieldVisibilityActivity.TAG + DatabaseDefinitions.DOM_AUTHOR_NAME.name);
				initViewInfo(v, holder.title, R.id.row_title, FieldVisibilityActivity.TAG + DatabaseDefinitions.DOM_TITLE.name);
				initViewInfo(v, holder.image, R.id.row_image_view, FieldVisibilityActivity.TAG + UniqueId.BKEY_THUMBNAIL );
				initViewInfo(v, holder.publisher, R.id.row_publisher, FieldVisibilityActivity.TAG + DatabaseDefinitions.DOM_PUBLISHER.name);
				initViewInfo(v, holder.read, R.id.row_read_image_view, FieldVisibilityActivity.TAG + DatabaseDefinitions.DOM_READ.name);
				initViewInfo(v, holder.series, R.id.row_series, FieldVisibilityActivity.TAG + DatabaseDefinitions.DOM_SERIES_NAME.name);

				ViewTagger.setTag(v, R.id.TAG_HOLDER, holder);

				return v;
			}

			/**
			 * Rather than having setText/setImage etc, or using messy from/to fields,
			 * we just bind child views using a Holder object and the cursor RowView.
			 */
			@Override
			protected void bindChildView(View view, Context context, Cursor origCursor, boolean isLastChild) {
				BookHolder holder = ViewTagger.getTag(view, R.id.TAG_HOLDER);
				if (holder == null) {
					throw new RuntimeException("Holder null?");
				}
				final BooksCursor snapshot = (BooksCursor) origCursor;
				final BooksRow rowView = snapshot.getRowView();

				if (holder.author.show)
					holder.author.view.setText(rowView.getPrimaryAuthorName());

				if (holder.title.show)
					holder.title.view.setText(rowView.getTitle());

				if (holder.image.show) {
					//CatalogueDBAdapter.fetchThumbnailIntoImageView(cursor.getId(),holder.image.view, LIST_THUMBNAIL_SIZE, LIST_THUMBNAIL_SIZE, true, mTaskQueue);
					ImageUtils.fetchBookCoverIntoImageView(holder.image.view, rowView.getBookUuid(), LIST_THUMBNAIL_SIZE, LIST_THUMBNAIL_SIZE, true,
                            BooklistPreferencesActivity.isThumbnailCacheEnabled(), BooklistPreferencesActivity.isBackgroundThumbnailsEnabled());
				}

				if (holder.read.show) {
					int read;
					try {
						read = rowView.getRead();
					} catch (Exception e) {
						read = 0;
					}
					if (read == 1) {
						holder.read.view.setImageResource(R.drawable.btn_check_buttonless_on);
					} else {
						holder.read.view.setImageResource(R.drawable.btn_check_buttonless_off);
					}
				}

				if (holder.series.show) {
					String series = rowView.getSeries();
					if (sort == SORT_SERIES || series.isEmpty()) {
						holder.series.view.setText("");
					} else {
						holder.series.view.setText(getResources().getString(R.string.brackets, series));
					}
				}

				if (holder.publisher.show)
					holder.publisher.view.setText(rowView.getPublisher());
			}

			/**
			 * Utility routine to regenerate the groups cursor using the enclosing ViewManager.
			 */
			private void regenGroups() {
				setGroupCursor(newGroupCursor());
				notifyDataSetChanged();
				// Reset the scroller, just in case
				FastScrollExpandableListView fselv = (FastScrollExpandableListView)BookCatalogueClassic.this.getExpandableListView();
				fselv.setFastScrollEnabled(false);
				fselv.setFastScrollEnabled(true);
			}

			/**
			 * Get section names for the ListView. We just return all the groups.
			 */
			@Override
			public Object[] getSections() {
				// Get the group cursor and save its position
				Cursor c = ViewManager.this.getCursor();
				int savedPosition = c.getPosition();
				// Create the string array
				int count = c.getCount();
				String[] sections = new String[count];
				c.moveToFirst();
				// Get the column number from the cursor column we use for sections.
				int sectionCol = ViewManager.this.getSectionNameColumn();
				// Populate the sections
				for(int i = 0; i < count; i++) {
					sections[i] = c.getString(sectionCol);
					c.moveToNext();
				}
				// Reset cursor and return
				c.moveToPosition(savedPosition);
				return sections;
			}

			/**
			 * Passed a section number, return the flattened position in the list
			 */
			@Override
			public int getPositionForSection(int section) {
				return getExpandableListView().getFlatListPosition(ExpandableListView.getPackedPositionForGroup(section));
			}

			/**
			 * Passed a flattened position in the list, return the section number
			 */
			@Override
			public int getSectionForPosition(int position) {
				final ExpandableListView list = getExpandableListView();
				long packedPos = list.getExpandableListPosition(position);
				return ExpandableListView.getPackedPositionGroup(packedPos);
			}

		}
	}

	/*
	 * ViewManager for sorting by Title
	 */
	private class TitleViewManager extends ViewManager {
		TitleViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_ID.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.isEmpty()) {
				return mDb.classicFetchAllBooksByChar(groupCursor.getString(mGroupIdColumnIndex), mBookshelf, "");
			} else {
				return mDb.classicFetchBooksByChar(search_query, groupCursor.getString(mGroupIdColumnIndex), mBookshelf);
			}
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.isEmpty()) {
				// Return all books (for the bookshelf)
				mCursor = mDb.classicFetchAllBookChars(mBookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDb.classicFetchBooksChars(search_query, mBookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
		}
	}

	/*
	 * ViewManager for sorting by Author
	 */
	private class AuthorViewManager extends ViewManager {
		AuthorViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_authors_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_AUTHOR_FORMATTED.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(final Cursor groupCursor) {
			return mDb.classicFetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), mBookshelf, search_query, false);
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.isEmpty()) {
				// Return all books for the given bookshelf
				mCursor = mDb.classicFetchAllAuthors(mBookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDb.classicFetchAuthors(search_query, mBookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_AUTHOR_FORMATTED.name);
		}
	}

	/*
	 * ViewManager for sorting by Author
	 */
	private class AuthorFirstViewManager extends ViewManager {
		AuthorFirstViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_authors_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDb.classicFetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), mBookshelf, search_query, false);
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.isEmpty()) {
				// Return all books for the given bookshelf
				mCursor = mDb.classicFetchAllAuthors(mBookshelf, false, false);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDb.classicFetchAuthors(search_query, mBookshelf, false, false);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name);
		}
	}

	/*
	 * ViewManager for sorting by Author
	 */
	private class AuthorOneViewManager extends ViewManager {
		AuthorOneViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_authors_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_AUTHOR_FORMATTED.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDb.classicFetchAllBooksByAuthor(groupCursor.getInt(mGroupIdColumnIndex), mBookshelf, search_query, true);
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.isEmpty()) {
				// Return all books for the given bookshelf
				mCursor = mDb.classicFetchAllAuthors(mBookshelf, true, true);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDb.classicFetchAuthors(search_query, mBookshelf, true, true);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_AUTHOR_FORMATTED.name);
		}
	}

	/*
	 * ViewManager for sorting by Series
	 */
	private class SeriesViewManager extends ViewManager {
		SeriesViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_series_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_SERIES_NAME.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDb.classicFetchAllBooksBySeries(groupCursor.getString(groupCursor.getColumnIndex(DatabaseDefinitions.DOM_SERIES_NAME.name)), mBookshelf, search_query);
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.isEmpty()) {
				mCursor = mDb.classicFetchAllSeries(mBookshelf);
			} else {
				mCursor = mDb.classicFetchSeries(search_query, mBookshelf);
			}
			BookCatalogueClassic.this.startManagingCursor(mCursor);
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_SERIES_NAME.name);
		}
	}

	/*
	 * ViewManager for sorting by Loan status
	 */
	private class LoanViewManager extends ViewManager {
		LoanViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_series_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_ID.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDb.classicFetchAllBooksByLoan(groupCursor.getString(mGroupIdColumnIndex), search_query);
		}
		@Override
		public Cursor newGroupCursor() {
			mCursor = mDb.classicFetchAllLoans();
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
		}
	}

	/*
	 * ViewManager for sorting by Unread
	 */
	private class UnreadViewManager extends ViewManager {
		UnreadViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_series_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_ID.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			return mDb.classicFetchAllBooksByRead(groupCursor.getString(mGroupIdColumnIndex), mBookshelf, search_query);
		}
		@Override
		public Cursor newGroupCursor() {
			mCursor = mDb.classicFetchAllUnreadPseudo();
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
		}
	}

	/*
	 * ViewManager for sorting by Genre
	 */
	private class GenreViewManager extends ViewManager {
		GenreViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_ID.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.isEmpty()) {
				return mDb.classicFetchAllBooksByGenre(groupCursor.getString(mGroupIdColumnIndex), mBookshelf, "");
			} else {
				return mDb.classicFetchBooksByGenre(search_query, groupCursor.getString(mGroupIdColumnIndex), mBookshelf);
			}
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.isEmpty()) {
				// Return all books (for the bookshelf)
				mCursor = mDb.fetchGenresByBookshelf(mBookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDb.fetchGenres(search_query, mBookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
		}
	}

	/*
	 * ViewManager for sorting by Genre
	 */
	private class PublishedViewManager extends ViewManager {
		PublishedViewManager() {
			mLayout = R.layout.classic_row_authors;
			mChildLayout = R.layout.classic_row_books;
			mFrom = new String[]{DatabaseDefinitions.DOM_ID.name};
			mTo = new int[]{R.id.row_family};
		}
		public SQLiteCursor getChildrenCursor(Cursor groupCursor) {
			if (search_query.isEmpty()) {
				return mDb.classicFetchAllBooksByDatePublished(groupCursor.getString(mGroupIdColumnIndex), mBookshelf, "");
			} else {
				return mDb.classicFetchBooksByDatePublished(search_query, groupCursor.getString(mGroupIdColumnIndex), mBookshelf);
			}
		}
		@Override
		public Cursor newGroupCursor() {
			if (search_query.isEmpty()) {
				// Return all books (for the bookshelf)
				mCursor = mDb.classicFetchAllDatePublished(mBookshelf);
			} else {
				// Return the search results instead of all books (for the bookshelf)
				mCursor = mDb.classicFetchDatePublished(search_query, mBookshelf);
			}
			mGroupIdColumnIndex = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
			return mCursor;
		}
		@Override
		public int getSectionNameColumn() {
			return mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
		}
	}

	/**
	 * Build the tree view
	 */
	private void fillData() {
		//check and reset mDb. Avoid leaking cursors. Each one is 1MB (allegedly)!
		// so we're not interested in the Cursor returned, only in the fact that
		// the db does the work ?? AND that we don't leak the Cursor
		//FIXME: is there any real reason to run a SELECT and then discard it ??
		try (Cursor c = mDb.classicFetchAllAuthors(mBookshelf)){
		} catch (NullPointerException e) {
			//reset
			mDb = new CatalogueDBAdapter(this);
			mDb.open();
		}

		ViewManager vm;
		// Select between the different ViewManager objects based on the sort parameter
		switch(sort) {
			case SORT_TITLE:
				vm  = new TitleViewManager();
				break;
			case SORT_AUTHOR:
				vm = new AuthorViewManager();
				break;
			case SORT_AUTHOR_GIVEN:
				vm = new AuthorFirstViewManager();
				break;
			case SORT_AUTHOR_ONE:
				vm = new AuthorOneViewManager();
				break;
			case SORT_SERIES:
				vm = new SeriesViewManager();
				break;
			case SORT_LOAN:
				vm = new LoanViewManager();
				break;
			case SORT_UNREAD:
				vm = new UnreadViewManager();
				break;
			case SORT_GENRE:
				vm = new GenreViewManager();
				break;
			case SORT_PUBLISHED:
				vm = new PublishedViewManager();
				break;
			default:
				throw new IllegalArgumentException();
		}

		// Manage it
		startManagingCursor(vm.getCursor());

		// Set view title
		if (search_query.isEmpty()) {
			this.setTitle(R.string.app_name);
		} else {
			int numResults = vm.getCursor().getCount();
			Toast.makeText(this, numResults + " " + this.getResources().getString(R.string.results_found), Toast.LENGTH_LONG).show();
			this.setTitle(getResources().getString(R.string.search_title) + " - " + search_query);
		}

		// Instantiate the List Adapter
		ViewManager.BasicBookListAdapter adapter = vm.newAdapter(this);

		// Handle the click event. Do not open, but goto the book edit page
		ExpandableListView expandableList = getExpandableListView();

		// Extend the onGroupClick (Open) - Every click should add to the currentGroup array
		expandableList.setOnGroupExpandListener(new OnGroupExpandListener() {
			@Override
			public void onGroupExpand(int groupPosition) {
				if (mLoadingGroups == 0)
					adjustCurrentGroup(groupPosition, 1, false);
			}
		});
		// Extend the onGroupClick (Close) - Every click should remove from the currentGroup array
		expandableList.setOnGroupCollapseListener(new OnGroupCollapseListener() {
			@Override
			public void onGroupCollapse(int groupPosition) {
				if (mLoadingGroups == 0)
					adjustCurrentGroup(groupPosition, -1, false);
			}
		});

		/* Hide the default expandable icon, and use a different icon (actually the same icon)
		 * The override is for when changing back from the title view and it has hidden the icon. */
		Drawable indicator = this.getResources().getDrawable(R.drawable.expander_group);
		expandableList.setGroupIndicator(indicator);

		setListAdapter(adapter);
		// Force a rebuild of the fast scroller
		adapterChanged();

		adapter.notifyDataSetChanged();

		gotoCurrentGroup();
		/* Add number to bookshelf */
		TextView mBookshelfNumView = findViewById(R.id.bookshelf_num);
		try {
			mBookshelfNumView.setText(BookCatalogueApp.getResourceString(R.string.brackets, mDb.countBooks(mBookshelf)));
		} catch (IllegalStateException e) {
			Logger.logError(e);
		}
	}

	@SuppressWarnings({"unused"})
    private MenuHandler mMenuHandler;

	/*
	 * Save Current group to preferences
	 */
	private void saveCurrentGroup() {
		try {
			SharedPreferences.Editor ed = mPrefs.edit();
			ed.putInt(STATE_CURRENT_GROUP_COUNT, currentGroup.size());

			int i = 0;
			for (Integer currentValue : currentGroup) {
				ed.putInt(STATE_CURRENT_GROUP + " " + i, currentValue);
				i++;
			}

			ed.apply();

		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}

	/*
	 * Load Current group from preferences
	 */
	private void loadCurrentGroup() {
		try {
			if (currentGroup != null)
				currentGroup.clear();
			else
				currentGroup = new ArrayList<>();

			int count = mPrefs.getInt(STATE_CURRENT_GROUP_COUNT, -1);

			int i = 0;
			while(i < count) {
				int pos = mPrefs.getInt(STATE_CURRENT_GROUP + " " + i, -1);
				if (pos >= 0) {
					adjustCurrentGroup(pos, 1, true);
				}
				i++;
			}

		} catch (Exception e) {
			Logger.logError(e);
		}
		return;
	}

	/**
	 * Expand and scroll to the current group
	 */
	private void gotoCurrentGroup() {
		try {
			synchronized(mLoadingGroups) {
				mLoadingGroups += 1;
			}
			// DEBUG:
			//System.gc();
			//Debug.MemoryInfo before = new Debug.MemoryInfo();
			//Debug.getMemoryInfo(before);
			//long t0 = System.currentTimeMillis();

			ExpandableListView view = this.getExpandableListView();
			ArrayList<Integer> localCurrentGroup = currentGroup;
			for (Integer aLocalCurrentGroup : localCurrentGroup) {
				view.expandGroup(aLocalCurrentGroup);
				//System.out.println("Cursor count: " + TrackedCursor.getCursorCountApproximate());
			}

			// DEBUG:
			//Debug.MemoryInfo after = new Debug.MemoryInfo();
			//t0 = System.currentTimeMillis() - t0;
			//System.gc();
			//Debug.getMemoryInfo(after);
			//
			//int delta = (after.dalvikPrivateDirty + after.nativePrivateDirty + after.otherPrivateDirty)
			//				- (before.dalvikPrivateDirty + before.nativePrivateDirty + before.otherPrivateDirty);
			//System.out.println("Usage Change = " + delta + " (completed in " + t0 + "ms)");

			int pos = localCurrentGroup.size()-1;
			if (pos >= 0) {
				view.setSelectedGroup(localCurrentGroup.get(pos));
			}
		} catch (NoSuchFieldError e) {
			//do nothing
		} catch (Exception e) {
			Logger.logError(e);
		} finally {
			synchronized(mLoadingGroups) {
				mLoadingGroups -= 1;
			}
		}
		return;
	}

	/**
	 * add / remove items from the current group arrayList
	 *  @param pos    The position to add or remove
	 * @param adj    Adjustment to make (+1/-1 = open/close)
     * @param force    If force is true, then it will be always be added (if adj=1), even if it already exists - but moved to the end
     */
	private void adjustCurrentGroup(int pos, int adj, boolean force) {
		int index = currentGroup.indexOf(pos);
		if (index == -1) {
			//it does not exist (so is not open), so if adj=1, add to the list
			if (adj > 0) {
				currentGroup.add(pos);
				/* Add the latest position to the preferences */
			}
		} else {
			//it does exist (so is open), so remove from the list if adj=-1
			if (adj < 0) {
				currentGroup.remove(index);
			} else {
				if (force) {
					currentGroup.remove(index);
					currentGroup.add(pos);
					/* Add the latest position to the preferences */
				}
			}
		}
	}


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
		try {
			// Only delete titles, not authors
			if (ExpandableListView.getPackedPositionType(info.packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
				MenuItem delete = menu.add(0, DELETE_ID, 0, R.string.menu_delete);
				delete.setIcon(android.R.drawable.ic_menu_delete);
				MenuItem edit_book = menu.add(0, EDIT_BOOK, 0, R.string.edit_book);
				edit_book.setIcon(android.R.drawable.ic_menu_edit);
				MenuItem edit_book_notes = menu.add(0, EDIT_BOOK_NOTES, 0, R.string.edit_book_notes);
				edit_book_notes.setIcon(R.drawable.ic_menu_compose);
				MenuItem edit_book_friends = menu.add(0, EDIT_BOOK_FRIENDS, 0, R.string.edit_book_friends);
				edit_book_friends.setIcon(R.drawable.ic_menu_cc);
				// Send book to goodreads
				MenuItem edit_book_send_to_gr = menu.add(0, EDIT_BOOK_SEND_TO_GR, 0, R.string.edit_book_send_to_gr);
				edit_book_send_to_gr.setIcon(R.drawable.ic_menu_cc);
			} else if (ExpandableListView.getPackedPositionType(info.packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
				switch(sort) {
				case SORT_AUTHOR:
				case SORT_AUTHOR_GIVEN:
					{
						MenuItem edit_book = menu.add(0, EDIT_AUTHOR_ID, 0, R.string.menu_edit_author);
						edit_book.setIcon(android.R.drawable.ic_menu_edit);
						break;
					}
				case SORT_SERIES:
					{
						MenuItem delete = menu.add(0, DELETE_SERIES_ID, 0, R.string.menu_delete_series);
						delete.setIcon(android.R.drawable.ic_menu_delete);
						MenuItem edit_book = menu.add(0, EDIT_SERIES_ID, 0, R.string.menu_edit_series);
						edit_book.setIcon(android.R.drawable.ic_menu_edit);
						break;
					}
				}
			}
		} catch (NullPointerException e) {
			Logger.logError(e);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
		switch(item.getItemId()) {

		case DELETE_ID:
			int res = StandardDialogs.deleteBookAlert(this, mDb, info.id, new Runnable() {
				@Override
				public void run() {
					mDb.purgeAuthors();
					mDb.purgeSeries();
					fillData();
				}});
			if (res != 0)
				Toast.makeText(this, res, Toast.LENGTH_LONG).show();
			return true;

		case EDIT_BOOK:
			EditBookActivity.editBook(this, info.id, EditBookActivity.TAB_EDIT);
			return true;

		case EDIT_BOOK_NOTES:
			EditBookActivity.editBook(this, info.id, EditBookActivity.TAB_EDIT_NOTES);
			return true;

		case EDIT_BOOK_FRIENDS:
			EditBookActivity.editBook(this, info.id, EditBookActivity.TAB_EDIT_FRIENDS);
			return true;

		case EDIT_BOOK_SEND_TO_GR:
			// Get a GoodreadsManager and make sure we are authorized.
			GoodreadsManager grMgr = new GoodreadsManager();
			if (!grMgr.hasValidCredentials()) {
				try {
					grMgr.requestAuthorization(this);
				} catch (NetworkException e) {
					Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
				}
			}
			// get a QueueManager and queue the task.
			QueueManager qm = BookCatalogueApp.getQueueManager();
			SendOneBookTask task = new SendOneBookTask(info.id);
			qm.enqueueTask(task, BCQueueManager.QUEUE_MAIN, 0);
			return true;

		case EDIT_SERIES_ID:
			{
				if (info.id==-1) {
					Toast.makeText(this, R.string.cannot_edit_system, Toast.LENGTH_LONG).show();
				} else {
					EditSeriesDialog dialog = new EditSeriesDialog(this, mDb, new Runnable() {
						@Override
						public void run() {
							mDb.purgeSeries();
							regenGroups();
						}});
					Series series = mDb.getSeriesById(info.id);
					dialog.edit(series);
				}
				break;
			}
		case DELETE_SERIES_ID:
			{
				StandardDialogs.deleteSeriesAlert(this, mDb, mDb.getSeriesById(info.id), new Runnable() {
					@Override
					public void run() {
						regenGroups();
					}});
				break;
			}
		case EDIT_AUTHOR_ID:
			{
				EditAuthorDialog d = new EditAuthorDialog(this, mDb, new Runnable() {
					@Override
					public void run() {
						mDb.purgeAuthors();
						regenGroups();
					}});
				d.edit(mDb.getAuthorById(info.id));
				break;
			}
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * Utility routine to regenerate the groups cursor.
	 */
	private void regenGroups() {
		ViewManager.BasicBookListAdapter adapter = (ViewManager.BasicBookListAdapter) getExpandableListAdapter();
		adapter.regenGroups();
	}

//	/**
//	 * Change the sort order of the view and refresh the page
//	 */
//	private void saveSortBy(int sortType) {
//		sort = sortType;
//		currentGroup = new ArrayList<>();
//		fillData();
//		/* Save the current sort settings */
//		SharedPreferences.Editor ed = mPrefs.edit();
//		ed.putInt(STATE_SORT, sortType);
//		ed.apply();
//	}

	@Override
	public boolean onChildClick(ExpandableListView l, View v, int position, int childPosition, long id) {
		boolean result = super.onChildClick(l, v, position, childPosition, id);
		adjustCurrentGroup(position, 1, true);
		EditBookActivity.openBook(this, id);
		return result;
	}

	/**
	 * Called when an activity launched exits, giving you the requestCode you started it with,
	 * the resultCode it returned, and any additional data from it.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		switch(requestCode) {
		case UniqueId.ACTIVITY_CREATE_BOOK_SCAN:
			try {
				String contents = intent.getStringExtra(Scanner.SCAN_RESULT);
				// Handle the possibility of null/empty scanned string
				if (contents != null && !contents.isEmpty()) {
					Toast.makeText(this, R.string.isbn_found, Toast.LENGTH_LONG).show();
					Intent i = new Intent(this, BookISBNSearchActivity.class);
					i.putExtra(UniqueId.KEY_ISBN, contents);
					startActivityForResult(i, UniqueId.ACTIVITY_CREATE_BOOK_SCAN);
				} else {
					fillData();
				}
			} catch (NullPointerException ignore) {
				// This is not a scan result, but a normal return
				fillData();
			}
			break;
		case UniqueId.ACTIVITY_CREATE_BOOK_ISBN:
		case UniqueId.ACTIVITY_CREATE_BOOK_MANUALLY:
		case UniqueId.ACTIVITY_EDIT_BOOK:
		case UniqueId.ACTIVITY_SORT:
		case UniqueId.ACTIVITY_ADMIN:
			try {
				// Use the ADDED_* fields if present.
				if (intent != null && intent.hasExtra(EditBookActivity.ADDED_HAS_INFO)) {
					String justAdded;
					switch (sort) {
						case SORT_TITLE:
                        {
							justAdded = intent.getStringExtra(EditBookActivity.ADDED_TITLE);
							int position = mDb.classicFetchBookPositionByTitle(justAdded, mBookshelf);
							adjustCurrentGroup(position, 1, true);
							break;
						}
						case SORT_AUTHOR: {
							justAdded = intent.getStringExtra(EditBookActivity.ADDED_AUTHOR);
                            Author author = Author.toAuthor(justAdded);
							int position = mDb.classicGetAuthorPositionByName(author, mBookshelf);
							adjustCurrentGroup(position, 1, true);
							break;
						}
						case SORT_AUTHOR_GIVEN: {
							justAdded = intent.getStringExtra(EditBookActivity.ADDED_AUTHOR);
                            Author author = Author.toAuthor(justAdded);
							int position = mDb.classicGetAuthorPositionByGivenName(author, mBookshelf);
							adjustCurrentGroup(position, 1, true);
							break;
						}
						case SORT_SERIES: {
							justAdded = intent.getStringExtra(EditBookActivity.ADDED_SERIES);
							int position = mDb.classicGetSeriesPositionBySeries(justAdded, mBookshelf);
							adjustCurrentGroup(position, 1, true);
							break;
						}
						case SORT_GENRE: {
							justAdded = intent.getStringExtra(EditBookActivity.ADDED_GENRE);
							int position = mDb.getGenrePositionByGenre(justAdded, mBookshelf);
							adjustCurrentGroup(position, 1, true);
							break;
						}
					}
				}
			} catch (Exception e) {
				Logger.logError(e);
			}
			// We call bookshelf not fillData in case the bookshelves have been updated.
			bookshelf();
			break;
		case UniqueId.ACTIVITY_ADMIN_FINISH:
			finish();
			break;
		}
	}

	/**
	 * Restore UI state when loaded.
	 */
	@Override
	public void onResume() {
		try {
			mPrefs = getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
			sort = mPrefs.getInt(STATE_SORT, sort);
			mBookshelf = mPrefs.getString(BooksOnBookshelf.PREF_BOOKSHELF, mBookshelf);
			loadCurrentGroup();
		} catch (Exception e) {
			Logger.logError(e);
		}
		super.onResume();
	}

	/**
	 * Save UI state changes.
	 */
	@Override
	public void onPause() {
		saveCurrentGroup();
		SharedPreferences.Editor ed = mPrefs.edit();
		ed.putInt(STATE_SORT, sort);
		ed.putString(BooksOnBookshelf.PREF_BOOKSHELF, mBookshelf);
		ed.apply();
		saveCurrentGroup();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		try {
			if (mDb != null) {
				mDb.close();
				mDb = null;
			}
		} catch (RuntimeException ignored) {}
		if (mTaskQueue != null) {
			try {
				mTaskQueue.finish();
			} catch (Exception ignored) {}
            mTaskQueue = null;
		}
		super.onDestroy();
	}

	//@Override
	//public boolean onKeyDown(int keyCode, KeyEvent event) {
	//	if (keyCode == KeyEvent.KEYCODE_BACK) {
	//		if (search_query.equals("")) {
	//			int opened = mPrefs.getInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
	//			SharedPreferences.Editor ed = mPrefs.edit();
	//			if (opened == 0){
	//				ed.putInt(STATE_OPENED, BACKUP_PROMPT_WAIT);
	//				ed.commit();
	//				BookCatalogueApp.backupPopup(this);
	//				return true;
	//			} else {
	//				ed.putInt(STATE_OPENED, opened - 1);
	//				ed.commit();
	//			}
	//		}
	//	}
	//	return super.onKeyDown(keyCode, event);
	//}


	/**
	 * When the adapter is changed, we need to rebuild the ListView.
	 */
	private void adapterChanged() {
		// Reset the fast scroller
		FastScrollExpandableListView lv = (FastScrollExpandableListView)this.getExpandableListView();
		lv.setFastScrollEnabled(false);
		lv.setFastScrollEnabled(true);
	}

	/**
	 * Accessor used by Robotium test harness.
	 *
	 * @param s		New search string.
	 */
	public void setSearchQuery(String s) {
		search_query = s;
		regenGroups();
	}
}