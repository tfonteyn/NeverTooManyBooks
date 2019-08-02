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
package com.hardbacknutter.nevertomanybooks.debug;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Date;

import org.acra.ACRA;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;

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

        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onCreate");
        } else if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_INSTANCE_STATE) {
            Logger.debugEnter(a, "onCreate",
                              "savedInstanceState=" + savedInstanceState);
            Logger.debugArguments(a, "onCreate");
        }
    }

    /**
     * @param a Activity or Fragment
     */
    public static void exitOnCreate(@NonNull final Object a) {
        createEvent(a, State.Exit, "onCreate");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onCreate");
        }
    }

    /**
     * @param dialogFragment     DialogFragment
     * @param savedInstanceState Bundle
     */
    public static void enterOnCreateDialog(@NonNull final DialogFragment dialogFragment,
                                           @Nullable final Bundle savedInstanceState) {
        createEvent(dialogFragment, State.Enter, "onCreateDialog");

        if (BuildConfig.DEBUG) {
            Logger.debugEnter(dialogFragment, "onCreateDialog");
        } else if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_INSTANCE_STATE) {
            Logger.debugEnter(dialogFragment, "onCreateDialog",
                              "savedInstanceState=" + savedInstanceState);
            Logger.debugArguments(dialogFragment, "onCreateDialog");
        }
    }

    /**
     * @param dialogFragment Fragment
     */
    public static void exitOnCreateDialog(@NonNull final DialogFragment dialogFragment) {
        createEvent(dialogFragment, State.Exit, "onCreateDialog");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(dialogFragment, "onCreateDialog");
        }
    }

    /**
     * @param fragment           Fragment
     * @param savedInstanceState Bundle
     */
    public static void enterOnActivityCreated(@NonNull final Fragment fragment,
                                              @Nullable final Bundle savedInstanceState) {
        createEvent(fragment, State.Enter, "onActivityCreated");

        if (BuildConfig.DEBUG) {
            Logger.debugEnter(fragment, "onActivityCreated");
        } else if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_INSTANCE_STATE) {
            Logger.debugEnter(fragment, "onActivityCreated",
                              "savedInstanceState=" + savedInstanceState);
            Logger.debugArguments(fragment, "onActivityCreated");
        }
    }

    public static void exitOnActivityCreated(@NonNull final Fragment fragment) {
        createEvent(fragment, State.Exit, "onActivityCreated");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(fragment, "onActivityCreated");
        }
    }

    /**
     * @param a Activity or Fragment
     */
    public static void enterOnActivityResult(@NonNull final Object a,
                                             final int requestCode,
                                             final int resultCode,
                                             @Nullable final Intent data) {
        createEvent(a, State.Enter, "onActivityResult" + requestCode + '|' + resultCode);


        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onActivityResult");
        } else if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.debugEnter(a, "onActivityResult",
                              "requestCode=" + requestCode,
                              "resultCode=" + resultCode,
                              "data=" + data);
        }
    }

    /**
     * @param a Activity or Fragment
     */
    public static void exitOnActivityResult(@NonNull final Object a) {
        createEvent(a, State.Exit, "onActivityResult");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onActivityResult");
        }
    }

    public static void enterOnSaveInstanceState(@NonNull final Object a) {
        createEvent(a, State.Enter, "onSaveInstanceState");
        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onSaveInstanceState");
        }
    }

    public static void exitOnSaveInstanceState(@NonNull final Object a) {
        createEvent(a, State.Exit, "onSaveInstanceState");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onSaveInstanceState");
        }
    }

    public static void enterOnLoadFieldsFromBook(@NonNull final Object a) {
        createEvent(a, State.Enter, "onLoadFieldsFromBook");
        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onLoadFieldsFromBook");
        }
    }

    public static void exitOnLoadFieldsFromBook(@NonNull final Object a) {
        createEvent(a, State.Exit, "onLoadFieldsFromBook");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "exitOnLoadFieldsFromBook");
        }
    }

    public static void enterOnSaveFieldsToBook(@NonNull final Object a) {
        createEvent(a, State.Enter, "onSaveFieldsToBook");
        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onSaveFieldsToBook");
        }
    }

    public static void exitOnSaveFieldsToBook(@NonNull final Object a) {
        createEvent(a, State.Exit, "onSaveFieldsToBook");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onSaveFieldsToBook");
        }
    }

    public static void enterOnAttach(@NonNull final Object a) {
        createEvent(a, State.Enter, "onAttach");
        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onAttach");
        }
    }

    public static void exitOnAttach(@NonNull final Object a) {
        createEvent(a, State.Exit, "onAttach");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onAttach");
        }
    }

    public static void enterOnDestroy(@NonNull final Object a) {
        createEvent(a, State.Enter, "onDestroy");
        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onDestroy");
        }
    }

    public static void exitOnDestroy(@NonNull final Object a) {
        createEvent(a, State.Exit, "onDestroy");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onDestroy");
        }
    }

    public static void enterOnPause(@NonNull final Object a) {
        createEvent(a, State.Enter, "onPause");
        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onPause");
        }
    }

    public static void exitOnPause(@NonNull final Object a) {
        createEvent(a, State.Exit, "onPause");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onPause");
        }
    }

    public static void enterOnResume(@NonNull final Object a) {
        createEvent(a, State.Enter, "onResume");
        if (BuildConfig.DEBUG) {
            Logger.debugEnter(a, "onResume");
        }
    }

    public static void exitOnResume(@NonNull final Object a) {
        createEvent(a, State.Exit, "onResume");
        if (BuildConfig.DEBUG) {
            Logger.debugExit(a, "onResume");
        }
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
            }

            return null;
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
