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
 * Abstract base class for all Tasks.
 * 
 * A Task MUST be serializable. This means that it can not contain any references to UI components or
 * similar objects.
 * 
 * When run, it will have access to the Application context, and can use that to interact with the UI.
 * 
 * It it important to note that the run(...) method is NOT called in the main thread. Access to the main thread 
 * is provided by ...
 * 
 * @author Philip Warner
 *
 */
public abstract class Task implements Serializable, BindableItemCursorAdapter.BindableItem {

	private static final long serialVersionUID = -1735892871810069L;

	public enum TaskState {created, running, failed, successful, waiting}

	private TaskState m_state;
	private long m_id;
	private int m_retries;
	private final int m_retry_limit = 17;
	private Exception m_exception = null;
	private int m_retryDelay = 0;
	private final String m_description;
	private boolean m_abortTask = false;

	/**
	 * Return an application-defined category for the task; a default of 0 is provided.
	 * 
	 * The category can be used to lookup queued tasks based on category, for example to
	 * allow an application to ensure only one job of a particular category is queued, or
	 * to retrieve all jobs of a particular category.
	 */
	public abstract int getCategory();

	//protected abstract boolean run(Context context, int id);
	//public abstract boolean abort(int id);
	public String getDescription() {
		return m_description;
	}

	Task(String description) {
		m_state = TaskState.created;
		m_description = description;
	}

	public void setState(TaskState state) {
		m_state = state;
	}

	/**
	 * There is little that can be done to abort a task; we trust the implementations to
	 * check this flag periodically on long tasks.
	 */
	public boolean isAborting() {
		return m_abortTask;
	}
	public void abortTask() {
		m_abortTask = true;
	}

	public long getId() {
		return m_id;
	}
	public void setId(final long id) {
		m_id = id;
	}

	protected int getRetryLimit() {
		return m_retry_limit;
	}

	public int getRetryDelay() {
		return m_retryDelay;
	}
	protected void setRetryDelay(final int delay) {
		m_retryDelay = delay;
	}
	public void setRetryDelay() {
		setRetryDelay( (int)Math.pow(2, (m_retries+1)) );
	}

	public int getRetries() {
		return m_retries;
	}
	public void setRetries(final int retries) {
		m_retries = retries;
	}

	public boolean canRetry() {
		return m_retries < m_retry_limit;
	}

	public Exception getException() {
		return m_exception;
	}
	public void setException(final Exception e) {
		m_exception = e;
	}
	protected void storeEvent(Event e) {
		QueueManager.getQueueManager().storeTaskEvent(this, e);
	}
	protected void resetRetryCounter() {
		m_retries = 0;
		setRetryDelay();
	}
}
