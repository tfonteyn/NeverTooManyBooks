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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import org.acra.ACRA;

import java.util.Date;

public final class Tracker {

    private static final int K_MAX_EVENTS = 100;
    private static final Event[] mEventBuffer = new Event[K_MAX_EVENTS];
    private static int mNextEventBufferPos = 0;

    private Tracker() {
    }

    /**
     * @param a                  Activity or Fragment
     * @param savedInstanceState Bundle
     */
    public static void enterOnCreate(@NonNull final Object a,
                                     @Nullable final Bundle savedInstanceState) {
        handleEvent(a, States.Enter, "onCreate");

        if (DEBUG_SWITCHES.INSTANCE_STATE && BuildConfig.DEBUG) {
            Logger.info(a, "onCreate|" + States.Enter
                    + "|savedInstanceState=" + savedInstanceState);

            if (a instanceof Activity) {
                @SuppressWarnings("UnusedAssignment")
                Bundle extras = ((Activity) a).getIntent().getExtras();
                if (extras != null) {
                    Logger.info(a, "onCreate|" + Tracker.States.Running
                            + "|extras=" + extras);
                    if (extras.containsKey(UniqueId.BKEY_BOOK_DATA)) {
                        Logger.info(a, "onCreate|" + Tracker.States.Running
                                + "|extras=" + extras.getBundle(UniqueId.BKEY_BOOK_DATA));
                    }
                }
            }
        }
    }

    /**
     * @param a Activity or Fragment
     */
    public static void exitOnCreate(@NonNull final Object a) {
        handleEvent(a, States.Exit, "onCreate");
    }

    /**
     * @param fragment           Fragment
     * @param savedInstanceState Bundle
     */
    public static void enterOnActivityCreated(@NonNull final Fragment fragment,
                                              @Nullable final Bundle savedInstanceState) {
        handleEvent(fragment, States.Enter, "onActivityCreated");
        if (DEBUG_SWITCHES.INSTANCE_STATE && BuildConfig.DEBUG) {
            Logger.info(fragment,
                        "onActivityCreated|" + States.Enter
                                + "|savedInstanceState=" + savedInstanceState);
            @SuppressWarnings("UnusedAssignment")
            Bundle args = fragment.getArguments();
            if (args != null) {
                Logger.info(fragment, "onActivityCreated|" + Tracker.States.Running
                        + "|args=" + args);
                if (args.containsKey(UniqueId.BKEY_BOOK_DATA)) {
                    Logger.info(fragment, "onActivityCreated|" + Tracker.States.Running
                            + "|args=" + args.getBundle(
                            UniqueId.BKEY_BOOK_DATA));
                }
            }

        }
    }

    public static void exitOnActivityCreated(@NonNull final Object a) {
        handleEvent(a, States.Exit, "onActivityCreated");
    }


    public static void enterOnActivityResult(@NonNull final Object a,
                                             final int requestCode,
                                             final int resultCode,
                                             @Nullable final Intent data) {
        handleEvent(a, States.Enter, "onActivityResult|" + requestCode + '|' + resultCode);

        if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
            Logger.info(a,
                        "onActivityResult|" + States.Enter
                                + "|requestCode=" + requestCode
                                + "|resultCode=" + resultCode
                                + "|data=" + data);
        }
    }

    public static void exitOnActivityResult(@NonNull final Object a) {
        handleEvent(a, States.Exit, "onActivityResult");
    }


    public static void enterOnSaveInstanceState(@NonNull final Object a,
                                                @NonNull final Bundle outState) {
        handleEvent(a, States.Enter, "onSaveInstanceState");
        if (DEBUG_SWITCHES.INSTANCE_STATE && BuildConfig.DEBUG) {
            Logger.info(a, "onSaveInstanceState|" + States.Enter
                    + "|outState=" + outState);
        }
    }

    public static void exitOnSaveInstanceState(@NonNull final Object a,
                                               @NonNull final Bundle outState) {
        handleEvent(a, States.Exit, "onSaveInstanceState");
        if (DEBUG_SWITCHES.INSTANCE_STATE && BuildConfig.DEBUG) {
            Logger.info(a, "onSaveInstanceState|" + States.Exit
                    + "|outState=" + outState);
        }
    }

    public static void enterOnLoadFieldsFromBook(@NonNull final Object a,
                                                 final long bookId) {
        handleEvent(a, States.Enter, "onLoadFieldsFromBook");
        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(a, "onLoadFieldsFromBook|" + States.Enter
                    + "|bookId=" + bookId);
        }
    }

    public static void exitOnLoadFieldsFromBook(@NonNull final Object a,
                                                final long bookId) {
        handleEvent(a, States.Exit, "onLoadFieldsFromBook");
        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(a, "onLoadFieldsFromBook|" + States.Exit
                    + "|bookId=" + bookId);
        }
    }

    public static void enterOnSaveFieldsToBook(@NonNull final Object a,
                                               final long bookId) {
        handleEvent(a, States.Enter, "onSaveFieldsToBook");
        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(a, "onSaveFieldsToBook|" + States.Enter
                    + "|bookId=" + bookId);
        }
    }

    public static void exitOnSaveFieldsToBook(@NonNull final Object a,
                                              final long bookId) {
        handleEvent(a, States.Exit, "onSaveFieldsToBook");
        if (DEBUG_SWITCHES.FIELD_BOOK_TRANSFERS && BuildConfig.DEBUG) {
            Logger.info(a, "onSaveFieldsToBook|" + States.Exit
                    + "|bookId=" + bookId);
        }
    }

    public static void enterOnDestroy(@NonNull final Object a) {
        handleEvent(a, States.Enter, "onDestroy");
    }

    public static void exitOnDestroy(@NonNull final Object a) {
        handleEvent(a, States.Exit, "onDestroy");
    }

    public static void enterOnPause(@NonNull final Object a) {
        handleEvent(a, States.Enter, "onPause");
    }

    public static void exitOnPause(@NonNull final Object a) {
        handleEvent(a, States.Exit, "onPause");
    }

    public static void enterOnResume(@NonNull final Object a) {
        handleEvent(a, States.Enter, "onResume");
    }

    public static void exitOnResume(@NonNull final Object a) {
        handleEvent(a, States.Exit, "onResume");
    }

    public static void enterFunction(@NonNull final Object a,
                                     @NonNull final String name,
                                     @NonNull final Object... params) {
        StringBuilder fullName = new StringBuilder(name + '(');
        for (Object parameter : params) {
            fullName.append(parameter).append(',');
        }
        fullName.append(')');
        String s = fullName.toString();
        if (BuildConfig.DEBUG) {
            Logger.info(a, s);
        }
        handleEvent(a, States.Enter, s);
    }

    public static void exitFunction(@NonNull final Object a,
                                    @NonNull final String s) {
        handleEvent(a, States.Exit, s);
        if (BuildConfig.DEBUG) {
            Logger.info(a, s);
        }
    }

    public static void handleEvent(@NonNull final Object o,
                                   @NonNull final States type,
                                   @NonNull final String message) {
        Event e = new Event(o, type, message);
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
                s.append('\n');
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
        final String message;
        @NonNull
        final States state;
        @NonNull
        final Date date;
        @NonNull
        final String clazz;

        Event(@NonNull final Object clazz,
              @NonNull final States state,
              @NonNull final String message) {
            this.clazz = clazz.getClass().getCanonicalName();
            this.message = message;
            this.state = state;
            date = new Date();
        }

        @NonNull
        String getInfo() {
            return DateUtils.utcSqlDateTime(date) + '|' + clazz + '|' + state + '|' + message;
        }
    }
}
