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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import java.util.ArrayList;

public interface BindableItem {
	/**
	 * Get a new View object suitable for displaying this type of event.
	 * 
	 * NOTE: A single event subclass should NOT RETURN MORE THAN ONE TYPE OF VIEW. If it needs
	 * to do this, create a new Event subclass or use a more complex view.
	 * 
	 * @param inflater	LayoutInflater for use in expanding XML resources
	 * @param context	Context that requires the view
	 * @param cursor	EventsCursor for this event, positioned at its row.
	 * @param parent	ViewGroup that will contain the new View.
	 * 
	 * @return			a new view
	 */
	View newListItemView(final LayoutInflater inflater, final Context context, final BindableItemSQLiteCursor cursor, final ViewGroup parent);
	/**
	 * Bind this Event to the passed view. The view will be one created by a call to newListItemView(...).
	 * 
	 * @param view		View to populate
	 * @param context	Context using view
	 * @param cursor	EventsCursor for this event, positioned at its row.
	 * @param appInfo	Any application-specific object the caller chooses to send. eg. a database adapter.
	 * @return
	 */
	boolean bindView(final View view, final Context context, final BindableItemSQLiteCursor cursor, final Object appInfo);
	/**
	 * Called when an item in a list has been clicked, this method should populate the passed 'items' parameter with
	 * one ContextDialogItem per operation that can be performed on this object.
	 * 
	 * @param ctx		Context resulting in Click() event
	 * @param parent	ListView (or other) that contained the item
	 * @param v			View that was clicked
	 * @param position	position in cursor of item
	 * @param id		row id of item
	 * @param items		items collection to fill
	 * @param appInfo	Any application-specific object the caller chooses to send. eg. a database adapter.
	 */
	void addContextMenuItems(final Context ctx, AdapterView<?> parent, final View v, final int position, final long id, final ArrayList<ContextDialogItem> items, final Object appInfo);
}
