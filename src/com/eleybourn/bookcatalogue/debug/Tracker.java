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
package com.eleybourn.bookcatalogue.debug;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.utils.DateUtils;

import org.acra.ACRA;

import java.util.Date;

public class Tracker {

	public enum States {
		Enter,
		Exit,
		Running
	}
	private static class Event {
		final String activityClass;
		public final String action;
		public final States state;
		@NonNull
        public final Date date;
		public Event(Object a, String action, States state) {
			activityClass = a.getClass().getSimpleName();
			this.action = action;
			this.state = state;
			date = new Date();
		}
		
		@NonNull
        public String getInfo() {
			return DateUtils.toSqlDateTime(date) + ": " + activityClass + " " + action + " " + state;
		}
	}
	
	private final static int K_MAX_EVENTS = 100;
	private static final Event[] mEventBuffer = new Event[K_MAX_EVENTS];
	private static int mNextEventBufferPos = 0;

	public static void enterOnActivityCreated(@NonNull final Object a) {
		handleEvent(a,"OnActivityCreated (" + a + ")", States.Enter);
	}
	public static void exitOnActivityCreated(@NonNull final Object a) {
		handleEvent(a,"OnActivityCreated (" + a + ")", States.Exit);
	}

	public static void enterOnActivityResult(@NonNull final Object a, final int requestCode, final int resultCode) {
		handleEvent(a,"OnActivityResult[" + requestCode + "," + resultCode + "] (" + a + ")", States.Enter);
	}
	public static void exitOnActivityResult(@NonNull final Object a, final int requestCode, final int resultCode) {
		handleEvent(a,"OnActivityResult[" + requestCode + "," + resultCode + "] (" + a + ")", States.Exit);
	}

	public static void enterOnCreate(@NonNull final Object a) {
		handleEvent(a,"OnCreate (" + a + ")", States.Enter);
	}
	public static void exitOnCreate(@NonNull final Object a) {
		handleEvent(a,"OnCreate (" + a + ")", States.Exit);
	}
	public static void enterOnCreateView(@NonNull final Object a) {
		handleEvent(a,"OnCreateView (" + a + ")", States.Enter);
	}
	public static void exitOnCreateView(@NonNull final Object a) {
		handleEvent(a,"OnCreateView (" + a + ")", States.Exit);
	}
	public static void enterOnDestroy(@NonNull final Object a) {
		handleEvent(a,"OnDestroy", States.Enter);
	}
	public static void exitOnDestroy(@NonNull final Object a) {
		handleEvent(a,"OnDestroy", States.Exit);		
	}
	public static void enterOnPause(@NonNull final Object a) {
		handleEvent(a,"OnPause (" + a + ")", States.Enter);
	}
	public static void exitOnPause(@NonNull final Object a) {
		handleEvent(a,"OnPause (" + a + ")", States.Exit);
	}
	public static void enterOnResume(@NonNull final Object a) {
		handleEvent(a,"OnResume (" + a + ")", States.Enter);
	}
	public static void exitOnResume(@NonNull final Object a) {
		handleEvent(a,"OnResume (" + a + ")", States.Exit);
	}
	public static void enterOnSaveInstanceState(@NonNull Object a) {
		handleEvent(a,"OnSaveInstanceState", States.Enter);		
	}
	public static void exitOnSaveInstanceState(@NonNull Object a) {
		handleEvent(a,"OnSaveInstanceState", States.Exit);
	}
	public static void enterFunction(@NonNull final Object a, @NonNull final String name, @NonNull final Object... params) {
		StringBuilder fullName = new StringBuilder(name + "(");
		for (Object parameter : params) {
			fullName.append(parameter).append(",");
		}
		fullName.append(")");

        handleEvent(a,fullName.toString(), States.Enter);
    }
    public static void exitFunction(@NonNull final Object a, @NonNull final String name) {
        handleEvent(a,name, States.Exit);
    }

	public static void handleEvent(@NonNull final Object o, @NonNull final String name, @NonNull final States type) {
		Event e = new Event(o, name, type);
		mEventBuffer[mNextEventBufferPos] = e;
		ACRA.getErrorReporter().putCustomData("History-" + mNextEventBufferPos, e.getInfo());
		mNextEventBufferPos = (mNextEventBufferPos + 1) % K_MAX_EVENTS;
	}
	
	public static String getEventsInfo() {
		StringBuilder s = new StringBuilder("Recent Events:\n");
		int pos = mNextEventBufferPos;
		for(int i = 0; i < K_MAX_EVENTS; i++) {
			int index = (pos + i) % K_MAX_EVENTS;
			Event e = mEventBuffer[index];
			if (e != null) {
				s.append(e.getInfo());
				s.append("\n");
			}
		}
		return s.toString();
	}
}
