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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

import org.acra.ACRA;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.utils.DateUtils;

public final class Tracker {

    private static final int K_MAX_EVENTS = 100;
    private static final Event[] M_EVENT_BUFFER = new Event[K_MAX_EVENTS];
    private static int sNextEventBufferPos;

    private Tracker() {
    }

    /**
     * @param a                  Activity or Fragment
     * @param savedInstanceState Bundle
     */
    public static void enterOnCreate(@NonNull final Object a,
                                     @Nullable final Bundle savedInstanceState) {
        createEvent(a, State.Enter, "onCreate");

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_INSTANCE_STATE) {
            Logger.debugEnter(a, "onCreate", "savedInstanceState=" + savedInstanceState);

            Logger.debugExtras(a, "onCreate");
        }
    }

    /**
     * @param a Activity or Fragment
     */
    public static void exitOnCreate(@NonNull final Object a) {
        createEvent(a, State.Exit, "onCreate");
    }

    /**
     * @param a                  Activity or Fragment
     * @param savedInstanceState Bundle
     */
    public static void enterOnCreateDialog(@NonNull final Object a,
                                           @Nullable final Bundle savedInstanceState) {
        createEvent(a, State.Enter, "onCreateDialog");

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_INSTANCE_STATE) {
            Logger.debugEnter(a, "onCreateDialog", "savedInstanceState=" + savedInstanceState);
            Logger.debugExtras(a, "onCreateDialog");
        }
    }

    /**
     * @param a Activity or Fragment
     */
    public static void exitOnCreateDialog(@NonNull final Object a) {
        createEvent(a, State.Exit, "onCreateDialog");
    }

    /**
     * @param a                  Fragment
     * @param savedInstanceState Bundle
     */
    public static void enterOnActivityCreated(@NonNull final Object a,
                                              @Nullable final Bundle savedInstanceState) {
        createEvent(a, State.Enter, "onActivityCreated");

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_INSTANCE_STATE) {
            Logger.debugEnter(a, "onActivityCreated", "savedInstanceState=" + savedInstanceState);
            Logger.debugExtras(a, "onCreateDialog");
        }
    }

    public static void exitOnActivityCreated(@NonNull final Object a) {
        createEvent(a, State.Exit, "onActivityCreated");
    }


    public static void enterOnActivityResult(@NonNull final Object a,
                                             final int requestCode,
                                             final int resultCode,
                                             @Nullable final Intent data) {
        createEvent(a, State.Enter, "onActivityResult" + requestCode + '|' + resultCode);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.debugEnter(a, "onActivityResult",
                              "requestCode=" + requestCode,
                              "resultCode=" + resultCode,
                              "data=" + data);
        }
    }

    public static void exitOnActivityResult(@NonNull final Object a) {
        createEvent(a, State.Exit, "onActivityResult");
    }

    public static void enterOnSaveInstanceState(@NonNull final Object a) {
        createEvent(a, State.Enter, "onSaveInstanceState");
    }

    public static void exitOnSaveInstanceState(@NonNull final Object a) {
        createEvent(a, State.Exit, "onSaveInstanceState");
    }

    public static void enterOnLoadFieldsFromBook(@NonNull final Object a) {
        createEvent(a, State.Enter, "onLoadFieldsFromBook");
    }

    public static void exitOnLoadFieldsFromBook(@NonNull final Object a) {
        createEvent(a, State.Exit, "onLoadFieldsFromBook");
    }

    public static void enterOnSaveFieldsToBook(@NonNull final Object a) {
        createEvent(a, State.Enter, "onSaveFieldsToBook");
    }

    public static void exitOnSaveFieldsToBook(@NonNull final Object a) {
        createEvent(a, State.Exit, "onSaveFieldsToBook");
    }

    public static void enterOnAttach(@NonNull final Object a) {
        createEvent(a, State.Enter, "onAttach");
    }

    public static void exitOnAttach(@NonNull final Object a) {
        createEvent(a, State.Exit, "onAttach");
    }

    public static void enterOnDestroy(@NonNull final Object a) {
        createEvent(a, State.Enter, "onDestroy");
    }

    public static void exitOnDestroy(@NonNull final Object a) {
        createEvent(a, State.Exit, "onDestroy");
    }

    public static void enterOnPause(@NonNull final Object a) {
        createEvent(a, State.Enter, "onPause");
    }

    public static void exitOnPause(@NonNull final Object a) {
        createEvent(a, State.Exit, "onPause");
    }

    public static void enterOnResume(@NonNull final Object a) {
        createEvent(a, State.Enter, "onResume");
    }

    public static void exitOnResume(@NonNull final Object a) {
        createEvent(a, State.Exit, "onResume");
    }

    private static void createEvent(@NonNull final Object o,
                                    @NonNull final State type,
                                    @NonNull final String message) {
        Event e = new Event(o, type, message);
        M_EVENT_BUFFER[sNextEventBufferPos] = e;
        ACRA.getErrorReporter().putCustomData("History-" + sNextEventBufferPos, e.getInfo());
        sNextEventBufferPos = (sNextEventBufferPos + 1) % K_MAX_EVENTS;
    }

    @NonNull
    public static String getEventsInfo() {
        StringBuilder s = new StringBuilder("Recent Events:\n");
        int pos = sNextEventBufferPos;
        for (int i = 0; i < K_MAX_EVENTS; i++) {
            int index = (pos + i) % K_MAX_EVENTS;
            Event e = M_EVENT_BUFFER[index];
            if (e != null) {
                s.append(e.getInfo());
                s.append('\n');
            }
        }
        return s.toString();
    }


    public enum State {
        Enter,
        Exit,
        Running;

        @NonNull
        @Override
        public String toString() {
            switch (this) {
                case Enter:
                    return "Enter";
                case Exit:
                    return "Exit";
                case Running:
                    return "Running";
                //noinspection UnnecessaryDefault
                default:
                    return "";
            }
        }
    }

    private static class Event {

        @NonNull
        private final String mMessage;
        @NonNull
        private final State mState;
        @NonNull
        private final Date mDate;
        @NonNull
        private final String mClazz;

        Event(@NonNull final Object clazz,
              @NonNull final State state,
              @NonNull final String message) {
            //noinspection ConstantConditions
            mClazz = clazz.getClass().getCanonicalName();
            mMessage = message;
            mState = state;
            mDate = new Date();
        }

        @NonNull
        String getInfo() {
            return DateUtils.utcSqlDateTime(mDate) + '|' + mClazz + '|' + mState + '|' + mMessage;
        }
    }
}
