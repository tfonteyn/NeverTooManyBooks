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
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.widgets.FastScroller;

/**
 * Cursor adapter for flattened multi-typed ListViews. Simplifies the implementation of such lists.
 * 
 * Users of this class need to implement MultiTypeListHandler to manage the creation and display of
 * each view.
 * 
 * @author Philip Warner
 */
public class MultiTypeListCursorAdapter extends CursorAdapter implements FastScroller.SectionIndexerV2 {

	private final LayoutInflater mInflater;
	private final MultiTypeListHandler mHandler;
    /**
     * TODO: the intention is to set a click listener on the cover image... but see fixme below first!
     */
	@SuppressWarnings("FieldCanBeLocal")
    private final Activity mActivity;

	//FIXME: https://www.androiddesignpatterns.com/2012/07/loaders-and-loadermanager-background.html

	MultiTypeListCursorAdapter(@NonNull final Activity activity, @NonNull final Cursor c, @NonNull final MultiTypeListHandler handler) {
		super(activity, c);
		mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mActivity = activity;
		mHandler = handler;
	}

     /**
	 * NOT USED. Should never be called. Die if it is.
	 */
	@Override
    public void bindView(View view, Context context, Cursor cursor) {
		throw new RuntimeException("MultiTypeListCursorAdapter.bindView is unsupported");
	}
	/**
	 * NOT USED. Should never be called. Die if it is.
	 */
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		throw new RuntimeException("MultiTypeListCursorAdapter.newView is unsupported");
	}

	@Override 
	public int getItemViewType(final int position) {
		final Cursor cursor = this.getCursor();
		//
		// At least on Android 2.3.4 we see attempts to get item types for cached items beyond the
		// end of empty cursors. This implies a cleanup ordering issue, but has not been confirmed.
		// This code attempts to gather more details of how this error occurs.
		//
		// NOTE: It DOES NOT fix the error; just gathers more debug info
		//
		if (cursor.isClosed()) {
			throw new RuntimeException("Attempt to get type of item on closed cursor (" + cursor + ")");
		} else if (position >= cursor.getCount()) {
			throw new RuntimeException("Attempt to get type of item beyond end of cursor (" + cursor + ")");
		} else {
			cursor.moveToPosition(position);
			return mHandler.getItemViewType(cursor);			
		}
	}
	@Override
	public int getViewTypeCount() {
		return mHandler.getViewTypeCount();
	}

	@Override
    public View getView(final int position, final View convertView, final ViewGroup parent)
    {
		Cursor cursor = this.getCursor();
		cursor.moveToPosition(position);

		return mHandler.getView(cursor, mInflater, convertView, parent);
    }

	/**
	 *
	 * this method gets called by {@link FastScroller}
	 *
     * actual text coming from {@link MultiTypeListHandler#getSectionText(Cursor)}}
	 */
	@Override
	@Nullable
	public String[] getSectionTextForPosition(final int position) {
        Tracker.enterFunction(this,"getSectionTextForPosition",position);
		final Cursor cursor = this.getCursor();
		if (position < 0 || position >= cursor.getCount()) {
			return null;
		}

		final int savedPos = cursor.getPosition();
		cursor.moveToPosition(position);
		final String[] section = mHandler.getSectionText(cursor);
		cursor.moveToPosition(savedPos);

		if (BuildConfig.DEBUG) {
		    for (String s : section) {
		        System.out.print("\n   " + s);
            }
            System.out.println();
        }
        Tracker.exitFunction(this, "getSectionTextForPosition");
		return section;
	}
}
