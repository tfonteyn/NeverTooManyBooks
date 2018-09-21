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

import com.eleybourn.bookcatalogue.widgets.BindableItemCursorAdapter;

import java.io.Serializable;


/**
 * Base class for capturing and storing exceptions that occur during task processing but which
 * do not prevent the task from completing. Examples might include a long running export job
 * in which 3 items fail, but 1000 succeed -- in this case it is useful to export the successful
 * ones and report the failures later.
 * 
 * The Task object has a 'saveException()' method that stores the exception in the database
 * for later retrieval.
 * 
 * Client applications should consider subclassing this object.
 * 
 * @author Philip Warner
 *
 */
public abstract class Event implements Serializable, BindableItemCursorAdapter.BindableItem {
	private static final long serialVersionUID = 5209097408979831308L;

	private final String m_description;
	private long m_id = 0;
	private Exception m_exception = null;

	public Event(String description) {
		m_description = description;
	}
	public Event(String description, Exception e) {
		m_description = description;
		m_exception = e;
	}
	public String getDescription() {
		return m_description;
	}
	public Exception getException() {
		return m_exception;
	}

	public long getId() {
		return m_id;
	}
	public void setId(long id) {
		m_id = id;
	}
}
