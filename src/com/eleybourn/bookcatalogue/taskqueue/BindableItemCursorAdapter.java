/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 * 
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.taskqueue;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import java.util.Hashtable;
import java.util.Map;

public class BindableItemCursorAdapter extends CursorAdapter {

	public interface BindableItemBinder {
		/**
		 * Return the number of different Event types and event that the list will display. An approximation is fine, but
		 * overestimating will be more efficient than underestimating. This is analogous to the 
		 * CursorAdapter.getViewTypeCount() method but needs to be set to the minimum of the total number of Event subclasses
		 * that may be returned.
		 * 
		 * @return	number of view types that events will return.
		 */
		int getBindableItemTypeCount();
		/**
		 * Called to bind a specific event to a View in the list. It is passed to the subclass so that any
		 * application-specific context can be added, or it can just be passed off to the Event object itself.
		 *  
		 * @param context	Context of request
		 * @param view		View to populate
		 * @param cursor	Cursor, positions at the relevant row
		 */
		void bindViewToItem(Context context, View view, BindableItemSQLiteCursor cursor, BindableItem bindable);
	}

	/** A local Inflater for convenience */
	private final LayoutInflater mInflater;
	private final Context mContext;
	private final BindableItemBinder mBinder;

	/**
	 * Constructor; calls superclass and allocates an Inflater for later use.
	 * 
	 * @param context	Context of call
	 * @param cursor	Cursor to use as source
	 */
	public BindableItemCursorAdapter(BindableItemBinder binder, Context context, BindableItemSQLiteCursor cursor) {
		super(context, cursor);
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		// This is saved in the parent class, but inaccessible for some reason. Google sources say 
		// it's protected, but apparently not. So we keep a copy too.
		mContext = context;
		mBinder = binder;
	}

	/**
	 * NOT USED. Should never be called.
	 */
	@Override
    public void bindView(View view, Context context, Cursor cursor) {
		throw new RuntimeException("EventsCursorAdapter.bindView is unsupported");
	}
	/**
	 * NOT USED. Should never be called.
	 */
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		throw new RuntimeException("EventsCursorAdapter.newView is unsupported");
	}

	/** The position passed to the last call of getItemViewType() */
	private int m_lastItemViewTypePos = -1;
	/** The item type returned by the last call of getItemViewType() */
	private int m_lastItemViewType = -1;
	/** The Event used in the last call of getItemViewType() */
	private BindableItem m_lastItemViewTypeEvent = null;

	/** hash of class names and values used to dynamically allocate layout numbers */
	private final Map<String,Integer> m_itemTypeLookups = new Hashtable<>();
	private int m_itemTypeCount = 0;

	@Override
	public void notifyDataSetChanged() {
		super.notifyDataSetChanged();
		// Clear cached stuff
		m_lastItemViewTypePos = -1;
		m_lastItemViewType = -1;
		m_lastItemViewTypeEvent = null;
	}
	
	/**
	 * Uses the actual class name of the Event object to dynamically allocate layout numbers,
	 * and returns the layout number corresponding to the Event at the specificed position.
	 * 
	 * The values are cached in member variables because the usual process is to call
	 * getView() almost directly after calling getItemViewType().
	 * 
	 * @param position		Cursor position to check.
	 */
	@Override 
	public int getItemViewType(int position) {
		// If it's the same as the last call, just return.
		if (position == m_lastItemViewTypePos)
			return m_lastItemViewType;

		// Get the Event object
		BindableItemSQLiteCursor c = (BindableItemSQLiteCursor)getCursor();
		c.moveToPosition(position);
		BindableItem bindable = c.getBindableItem();

		// Use the class name to generate a layout number
		String s = bindable.getClass().toString();
		int resType;
		if (m_itemTypeLookups.containsKey(s)) {
			resType = m_itemTypeLookups.get(s);
		} else {
			m_itemTypeCount++;
			m_itemTypeLookups.put(s, m_itemTypeCount);
			resType = m_itemTypeCount;
		}
		
		//
		// Cache the recent results; this is a optimization kludge based on the fact that current code
		// always call this method before getView(...) so we can avoid one deserialization by caching 
		// here.
		//
		// NOTE: if this assumption fails, the code still works. It just runs slower.
		//
		m_lastItemViewTypePos = position;
		m_lastItemViewType = resType;
		m_lastItemViewTypeEvent = bindable;
		
		// And return
		return resType;
	}

	/**
	 * Get the (approximate, overestimate) of the number of Event subclasses in use in this list.
	 */
	@Override
	public int getViewTypeCount() {
		// Add 1 to allow for the LegacyEvent object.
		return mBinder.getBindableItemTypeCount() + 1;
	}

	@Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
		BindableItemSQLiteCursor cursor = (BindableItemSQLiteCursor) this.getCursor();
		cursor.moveToPosition(position);
		BindableItem bindable;
		// Optimization to avoid unnecessary deserializations.
		if (m_lastItemViewTypePos == position) {
			bindable = m_lastItemViewTypeEvent;
		} else {
			bindable = cursor.getBindableItem();
		}

		if(convertView == null) {
			try {
				convertView = bindable.newListItemView(mInflater, mContext, cursor, parent);
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
        }

		// Bind it, and we are done!
		mBinder.bindViewToItem(mContext, convertView, cursor, bindable);

		return convertView;
    }
	
}
