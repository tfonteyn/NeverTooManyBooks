package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.App;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shielding the actual implementation of Toast/Snackbar or whatever is next.
 *
 * TODO: 2019... it's probably time to remove Toast. But consider each usage individually!
 */
public final class UserMessage {

    private UserMessage() {
    }

    public static void showUserMessage(@NonNull final Activity activity,
                                       @StringRes final int message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, 0)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(activity.getWindow().getDecorView(), message,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    public static void showUserMessage(@NonNull final Activity activity,
                                       @NonNull final String message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, 0)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(activity.getWindow().getDecorView(), message,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    public static void showUserMessage(@NonNull final View view,
                                       @StringRes final int message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, 0)) {
            Toast.makeText(view.getContext(), message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }
    }

    public static void showUserMessage(@NonNull final View view,
                                       @NonNull final String message) {
        if (0 == App.getListPreference(Prefs.pk_ui_messages_use, 0)) {
            Toast.makeText(view.getContext(), message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }
    }
}
