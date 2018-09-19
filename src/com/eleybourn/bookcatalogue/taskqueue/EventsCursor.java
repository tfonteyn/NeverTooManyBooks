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

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import com.eleybourn.bookcatalogue.utils.SerializationUtils;

import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

import static com.eleybourn.bookcatalogue.utils.SerializationUtils.deserializeObject;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_EVENT;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_EVENT_DATE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_ID;

/**
 * Cursor subclass used to make accessing TaskExceptions a little easier.
 * 
 * @author Philip Warner
 *
 */
public class EventsCursor extends BindableItemSQLiteCursor {

	private final Map<Long,Boolean> m_selections = new Hashtable<>();

	/**
	 * Constructor, based on SQLiteCursor constructor
	 */
	EventsCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
		super(driver, editTable, query);
	}

	/** Column number of ID column. */
	private static int m_idCol = -1;
	/**
	 * Accessor for ID field.
	 * 
	 * @return	row id
	 */
	public long getId() {
		if (m_idCol == -1)
			m_idCol = this.getColumnIndex(DOM_ID);
		return getLong(m_idCol);
	}


	/** Column number of date column. */
	private static int m_dateCol = -1;
	/**
	 * Accessor for Exception date field.
	 * 
	 * @return	Exception date
	 */
	public Date getEventDate() {
		if (m_dateCol == -1)
			m_dateCol = this.getColumnIndex(DOM_EVENT_DATE);
		return Utils.string2date(getString(m_dateCol));
	}

	/** Column number of Exception column. */
	private static int m_eventCol = -1;
	/**
	 * Accessor for Exception field.
	 * 
	 * @return	TaskException object
	 */
	public Event getEvent()  {
		if (m_eventCol == -1)
			m_eventCol = this.getColumnIndex(DOM_EVENT);
		byte[] blob = getBlob(m_eventCol);
		Event e;
		try {
			e = deserializeObject(blob);
		} catch (SerializationUtils.DeserializationException de) {
			e = QueueManager.getQueueManager().newLegacyEvent(blob);
		}
		e.setId(this.getId());
		return e;
	}

	/**
	 * Fake attribute to handle multi-select ListViews. if we ever do them.
	 * 
	 * @return	Flag indicating if current row has been marked as 'selected'.
	 */
	public boolean getIsSelected() {
		return getIsSelected(getId());
	}

	private boolean getIsSelected(long id) {
		if (m_selections.containsKey(id)) {
			return m_selections.get(id);			
		} else {
			return false;
		}
	}
	public void setIsSelected(long id, boolean selected) {
		m_selections.put(id, selected);
	}

	@Override
	public BindableItem getBindableItem() {
		return getEvent();
	}
}
