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

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import org.acra.ACRA;

import java.util.Date;

public class Tracker {

    private final static int K_MAX_EVENTS = 100;
    private static final Event[] mEventBuffer = new Event[K_MAX_EVENTS];
    private static int mNextEventBufferPos = 0;

    public static void enterOnActivityCreated(final @NonNull Object a) {
        handleEvent(a, "OnActivityCreated (" + a + ")", States.Enter);
    }

    public static void exitOnActivityCreated(final @NonNull Object a) {
        handleEvent(a, "OnActivityCreated (" + a + ")", States.Exit);
    }

    public static void enterOnActivityResult(final @NonNull Object a, final int requestCode, final int resultCode) {
        if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
            Logger.info(a, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        handleEvent(a, "OnActivityResult[" + requestCode + "," + resultCode + "] (" + a + ")", States.Enter);
    }

    public static void exitOnActivityResult(final @NonNull Object a, final int requestCode, final int resultCode) {
        handleEvent(a, "OnActivityResult[" + requestCode + "," + resultCode + "] (" + a + ")", States.Exit);
    }

    public static void enterOnCreate(final @NonNull Object a) {
        handleEvent(a, "OnCreate (" + a + ")", States.Enter);
    }

    public static void exitOnCreate(final @NonNull Object a) {
        handleEvent(a, "OnCreate (" + a + ")", States.Exit);
    }

    public static void enterOnCreateView(final @NonNull Object a) {
        handleEvent(a, "OnCreateView (" + a + ")", States.Enter);
    }

    public static void exitOnCreateView(final @NonNull Object a) {
        handleEvent(a, "OnCreateView (" + a + ")", States.Exit);
    }

    public static void enterOnDestroy(final @NonNull Object a) {
        handleEvent(a, "OnDestroy", States.Enter);
    }

    public static void exitOnDestroy(final @NonNull Object a) {
        handleEvent(a, "OnDestroy", States.Exit);
    }

    public static void enterOnPause(final @NonNull Object a) {
        handleEvent(a, "OnPause (" + a + ")", States.Enter);
    }

    public static void exitOnPause(final @NonNull Object a) {
        handleEvent(a, "OnPause (" + a + ")", States.Exit);
    }

    public static void enterOnResume(final @NonNull Object a) {
        handleEvent(a, "OnResume (" + a + ")", States.Enter);
    }

    public static void exitOnResume(final @NonNull Object a) {
        handleEvent(a, "OnResume (" + a + ")", States.Exit);
    }

    public static void enterOnLoadFieldsFromBook(final @NonNull Object a, final long bookId) {
        handleEvent(a, "onLoadFieldsFromBook: " + bookId, States.Enter);
    }
    public static void exitOnLoadFieldsFromBook(final @NonNull Object a, final long bookId) {
        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(a, "onLoadFieldsFromBook done: " + bookId);
        }
        handleEvent(a, "onLoadFieldsFromBook: " + bookId, States.Exit);
    }

    public static void enterOnSaveFieldsToBook(final @NonNull Object a, final long bookId) {
        handleEvent(a, "onSaveFieldsToBook: " + bookId, States.Enter);
    }
    public static void exitOnSaveFieldsToBook(final @NonNull Object a, final long bookId) {
        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(a, "onSaveFieldsToBook done: " + bookId);
        }
        handleEvent(a, "onSaveFieldsToBook: " + bookId, States.Exit);
    }

    public static void enterFunction(final @NonNull Object a, final @NonNull String name, final @NonNull Object... params) {
        StringBuilder fullName = new StringBuilder(name + "(");
        for (Object parameter : params) {
            fullName.append(parameter).append(",");
        }
        fullName.append(")");
        String s = fullName.toString();
        if (BuildConfig.DEBUG) {
            Logger.info(a, s);
        }
        handleEvent(a, s, States.Enter);
    }

    public static void exitFunction(final @NonNull Object a, final @NonNull String name, final @NonNull Object... params) {
        StringBuilder fullName = new StringBuilder(name + "(");
        for (Object parameter : params) {
            fullName.append(parameter).append(",");
        }
        fullName.append(")");
        String s = fullName.toString();
        if (BuildConfig.DEBUG) {
            Logger.info(a, s);
        }
        handleEvent(a, s, States.Exit);
    }

    public static void handleEvent(final @NonNull Object o, final @NonNull String message, final @NonNull States type) {
        Event e = new Event(o, message, type);
        mEventBuffer[mNextEventBufferPos] = e;
        ACRA.getErrorReporter().putCustomData("History-" + mNextEventBufferPos, e.getInfo());
        mNextEventBufferPos = (mNextEventBufferPos + 1) % K_MAX_EVENTS;
    }

    @NonNull
    public static String getEventsInfo() {
        StringBuilder s = new StringBuilder("Recent Events:\n");
        int pos = mNextEventBufferPos;
        for (int i = 0; i < K_MAX_EVENTS; i++) {
            int index = (pos + i) % K_MAX_EVENTS;
            Event e = mEventBuffer[index];
            if (e != null) {
                s.append(e.getInfo());
                s.append("\n");
            }
        }
        return s.toString();
    }

    public enum States {
        Enter,
        Exit,
        Running
    }

    private static class Event {
        @NonNull
        public final String message;
        @NonNull
        public final States state;
        @NonNull
        public final Date date;
        @NonNull
        final String clazz;

        public Event(final @NonNull Object a, final @NonNull String message, final @NonNull States state) {
            clazz = a.getClass().getCanonicalName();
            this.message = message;
            this.state = state;
            date = new Date();
        }

        @NonNull
        public String getInfo() {
            return DateUtils.utcSqlDateTime(date) + "|" + clazz + "|" + state + "|" + message ;
        }
    }
}
