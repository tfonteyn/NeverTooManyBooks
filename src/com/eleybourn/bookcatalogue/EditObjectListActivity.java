/*
 * @copyright 2011 Philip Warner
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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueListActivity;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.widgets.SimpleListAdapter;
import com.eleybourn.bookcatalogue.widgets.TouchListView;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Base class for editing a list of objects. The inheritor must specify a view id
 * and a row view id to the constructor of this class. Each view can have the
 * following sub-view IDs present which will be automatically handled. Optional
 * IDs are noted:
 * 
 * Main View:
 * 	- cancel
 *  - confirm
 *  - add (OPTIONAL)
 *  
 * Row View must have layout ID set to "@+id/row_details" (defined in ids.xml)

 * The row view is tagged using TAG_POSITION,, to save the rows position for
 * use when moving the row up/down or deleting it.
 *
 * Abstract methods are defined for specific tasks (Add, Save, Load etc). While would 
 * be tempting to add local implementations the java generic model seems to prevent this.
 * 
 * This Activity uses TouchListView from CommonsWare which is in turn based on Android code
 * for TouchInterceptor which was (reputedly) removed in Android 2.2.
 * 
 * For this code to work, the  main view must contain:
 * - a TouchListView with id = @id/android:list
 * - the TouchListView must have the following attributes:
 * 		tlv:ic_grabber="@+id/<SOME ID FOR AN IMAGE>" (eg. "@+id/ic_grabber")
 *		tlv:remove_mode="none"
 *		tlv:normal_height="64dip" ---- or some similar value
 * 
 * Each row view must have:
 * - an ID of @+id/row
 * - an ImageView with an ID of "@+id/<SOME ID FOR AN IMAGE>" (eg. "@+id/ic_grabber")
 * - (OPTIONAL) a subview with an ID of "@+d/row_details"; when clicked, this will result
 *   in the onRowClick event. If not present, then the onRowClick is set on the "@id/row"
 * 
 * @author Philip Warner
 *
 * @param <T>
 */
abstract public class EditObjectListActivity<T extends Serializable> extends BookCatalogueListActivity {

	protected ArrayList<T> mList = null;
	protected ListAdapter mAdapter;

	protected CatalogueDBAdapter mDb;

	protected String mBookTitle;

	// The key to use in the Bundle to get the array
	private final String mBKey;
	// The resource ID for the base view
	private final int mBaseViewId;
	// The resource ID for the row view
	private final int mRowViewId;

	// Row ID... mainly used (if list is from a book) to know if book is new.
	protected Long mRowId = null;

	/**
	 * Called when user clicks the 'Add' button (if present).
	 *
	 * @param v		The view that was clicked ('add' button).
	 */
	abstract protected void onAdd(View v);

	/**
	 * Call to set up the row view.
     * @param target    The target row view object
     * @param object    The object (or type T) from which to draw values.
     * @param position
     */
	abstract protected void onSetupView(View target, T object, int position);

	/**
	 * Called when an otherwise inactive part of the row is clicked.
	 *
     * @param target    The view clicked
     * @param object    The object associated with this row
     */
	abstract protected void onRowClick(View target, T object, int position);

	/**
	 * Called when user clicks the 'Save' button (if present). Primary task is
	 * to return a boolean indicating it is OK to continue.
	 *
	 * Can be overridden to perform other checks.
	 *
	 * @param intent	A newly created Intent to store output if necessary.
	 *
	 * @return		True if activity should exit, false to abort exit.
	 */
	protected boolean onSave(Intent intent) { return true; }

    /**
	 * Called when user presses 'Cancel' button if present. Primary task is
	 * return a boolean indicating it is OK to continue.
	 *
	 * Can be overridden to perform other checks.
	 *
	 * @return		True if activity should exit, false to abort exit.
	 */
	protected boolean onCancel() { return true;}

    /**
	 * Called when the list had been modified in some way.
	 */
	protected void onListChanged() { }

    /**
	 * Called to get the list if it was not in the intent.
	 */
	protected ArrayList<T> getList() { return null; }

    /**
	 * Constructor
	 * @param bkey          The key to use in the Bundle to get the array
	 * @param baseViewId	Resource id of base view
	 * @param rowViewId		Resource id of row view
	 */
	protected EditObjectListActivity(String bkey, int baseViewId, int rowViewId) {
		mBKey = bkey;
		mBaseViewId = baseViewId;
		mRowViewId = rowViewId;
	}

	/**
	 * Replace the current list
	 */
	protected void setList(ArrayList<T> newList) {
		View listView = this.getListView().getChildAt(0);
		final int savedTop = listView == null ? 0 : listView.getTop();
        final int savedRow = this.getListView().getFirstVisiblePosition();

		this.mList = newList;
		// Set up list handling
        this.mAdapter = new ListAdapter(this, mRowViewId, mList);
        setListAdapter(this.mAdapter);

        getListView().post(new Runnable() {
			@Override
			public void run() {
				getListView().setSelectionFromTop(savedRow, savedTop);
			}});
	}

    /**
     * bit of a kludge .. {@link SimpleListAdapter} needs to call its super.onSetupView
     * But we have encapsulation here, and not extension.
     * So wrap it up in this dummy class.
     * Still.. eliminated lots of duplicated code
     */
    protected class ListAdapter extends SimpleListAdapter<T> {
        ListAdapter(Context context, int rowViewId, ArrayList<T> items) {
            super(context, rowViewId, items);
        }

        @Override
        protected void onSetupView(View target, T object, int position) {
            EditObjectListActivity.this.onSetupView(target, object, position);
        }
        @Override
        protected void onRowClick(View v, T object, int position) {
            EditObjectListActivity.this.onRowClick(v, object, position);
        }
    }

    @Override
	protected int getLayoutId() {
		return mBaseViewId;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			// Setup the DB
			mDb = new CatalogueDBAdapter(this);
			mDb.open();

			// Add handlers for 'Save', 'Cancel' and 'Add'
			setupListener(R.id.confirm, mSaveListener);
			setupListener(R.id.cancel, mCancelListener);
			setupListener(R.id.add, mAddListener);

			// Ask the subclass to setup the list; we need this before building the adapter.
			if (savedInstanceState != null && mBKey != null && savedInstanceState.containsKey(mBKey)) {
				mList = ArrayUtils.getListFromBundle(savedInstanceState, mBKey);
			}
			// not in bundle ? check the intent
			if (mList == null) {
                mList = ArrayUtils.getListFromIntentExtras(getIntent(), mBKey);
            }
            // still nothing ? then ask subclass explicitly
            if (mList == null) {
                mList = getList();
            }
            // sigh... give up
            if (mList == null) {
                throw new RuntimeException("Unable to find list key '" + mBKey + "' in passed intent extras");
            }

			// Set up list handling
	        this.mAdapter = new ListAdapter(this, mRowViewId, mList);
	        setListAdapter(this.mAdapter);

	        // Look for title and title_label
			Bundle extras = getIntent().getExtras();
			if (extras != null) {
				mRowId = extras.getLong(ColumnNames.KEY_ROWID);

				mBookTitle = extras.getString(ColumnNames.KEY_TITLE);
				setTextOrHideView(R.id.title, mBookTitle);
			}

			TouchListView tlv=(TouchListView)getListView();
			tlv.setDropListener(mDropListener);
			//tlv.setRemoveListener(onRemove);

		} catch (Exception ignore) {
			Logger.logError(ignore);
		}
	}

	/**
	 * Handle drop events; also preserves current position.
	 */
	private final TouchListView.DropListener mDropListener=new TouchListView.DropListener() {

		@Override
		public void drop(int from, final int to) {
            final ListView lv = getListView();
			// Check if nothing to do; also avoids the nasty case where list size == 1
			if (from == to)
				return;

            final int firstPos = lv.getFirstVisiblePosition();

			T item=mAdapter.getItem(from);
			mAdapter.remove(item);
			mAdapter.insert(item, to);
            onListChanged();

            int first2 = lv.getFirstVisiblePosition();
            if (BuildConfig.DEBUG) {
				System.out.println(from + " -> " + to + ", first " + firstPos + "(" + first2 + ")");
			}
            final int newFirst = (to > from && from < firstPos) ? (firstPos - 1) : firstPos;

            View firstView = lv.getChildAt(0);
            final int offset = firstView.getTop();
            lv.post(new Runnable() {
				@Override
				public void run() {
					if (BuildConfig.DEBUG) {
						System.out.println("Positioning to " + newFirst + "+{" + offset + "}");
					}
					lv.requestFocusFromTouch();
					lv.setSelectionFromTop(newFirst, offset);
					lv.post(new Runnable() {
						@Override
						public void run() {
							for(int i = 0; ; i++) {
								View c = lv.getChildAt(i);
								if (c == null)
									break;
								if (lv.getPositionForView(c) == to) {
									lv.setSelectionFromTop(to, c.getTop());
									//c.requestFocusFromTouch();
									break;
								}
							}
						}});
				}});
		}
	};

	/**
	 * Utility routine to setup a listener for the specified view id
	 * 
	 * @param id    Resource ID
	 * @param l        Listener
	 */
	private void setupListener(int id, OnClickListener l) {
		View v = this.findViewById(id);
		if (v != null) {
			v.setOnClickListener(l);
		}
    }

	/**
	 * Utility routine to set a TextView to a string, or hide it.
	 * 
	 * @param id	View ID
	 * @param s		String to set
	 */
    protected void setTextOrHideView(int id, @Nullable String s) {
        TextView v = this.findViewById(id);
        if (v == null) {
            return;
        }
        if (s == null || s.isEmpty()) {
            v.setVisibility(View.GONE);
            return;
        }
        v.setText(s);
	}

	/**
	 * Handle 'Save'
	 */
	private final OnClickListener mSaveListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			Intent i = new Intent();
			i.putExtra(mBKey, mList);
			if (onSave(i)) {
				setResult(RESULT_OK, i);
				finish();
			}
		}
	};

	/**
	 * Handle 'Cancel'
	 */
	private final OnClickListener mCancelListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			if (onCancel())
				finish();
		}
	};

	/**
	 * Handle 'Add'
	 */
	private final OnClickListener mAddListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			onAdd(v);
			onListChanged();
		}		
	};

	/**
	 * Ensure that the list is saved.
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {    	
    	super.onSaveInstanceState(outState);
    	// save list
    	outState.putSerializable(mBKey, mList);
    }

	/**
	 * This is totally bizarre. Without this piece of code, under Android 1.6, the
	 * native onRestoreInstanceState() fails to restore custom classes, throwing
	 * a ClassNotFoundException, when the activity is resumed.
	 * 
	 * To test this, remove this line, edit a custom style, and save it. App will
	 * crash in AVD under Android 1.6.
	 * 
	 * It is not entirely clear how this happens but since the Bundle has a classLoader
	 * it is fair to surmise that the code that creates the bundle determines the class
	 * loader to use based (somehow) on the class being called, and if we don't implement
	 * this method, then in Android 1.6, the class is a basic android class NOT and app 
	 * class.
	 */
	@Override
	public void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mDb != null)
			mDb.close();
	}


}
