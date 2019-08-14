/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.utils;

import android.app.Activity;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Shielding the actual implementation of Toast/Snackbar or whatever is next.
 * <p>
 * TODO: 2019... it's probably time to remove Toast.
 * <p>
 * https://developer.android.com/training/snackbar
 * <b>Note:</b> The Snackbar class supersedes Toast. While Toast is currently still supported,
 * Snackbar is now the preferred way to display brief, transient messages to the user.
 */
public final class UserMessage {

    private static final int TOAST = 0;
    private static final int SNACKBAR = 1;

    /** the default value; rex/xml/preferences.xml must be set to the same. */
    private static final int DEFAULT = SNACKBAR;

    private UserMessage() {
    }

    public static void show(@NonNull final Activity activity,
                            @StringRes final int message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, DEFAULT)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            View view = activity.getWindow().getDecorView();
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.USER_MESSAGE_STACK_TRACE) {
            Logger.debugWithStackTrace(UserMessage.class, "show1",
                                       activity.getString(message));
        }
    }

    public static void show(@NonNull final Activity activity,
                            @NonNull final String message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, DEFAULT)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            View view = activity.getWindow().getDecorView();
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.USER_MESSAGE_STACK_TRACE) {
            Logger.debugWithStackTrace(UserMessage.class, "show2", message);
        }
    }

    public static void show(@NonNull final View view,
                            @StringRes final int message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, DEFAULT)) {
            Toast.makeText(view.getContext(), message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.USER_MESSAGE_STACK_TRACE) {
            Logger.debugWithStackTrace(UserMessage.class, "show3",
                                       view.getContext().getString(message));
        }
    }

    public static void show(@NonNull final View view,
                            @NonNull final String message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, DEFAULT)) {
            Toast.makeText(view.getContext(), message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.USER_MESSAGE_STACK_TRACE) {
            Logger.debugWithStackTrace(UserMessage.class, "show4", message);
        }
    }
}
