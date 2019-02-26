package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.google.android.material.snackbar.Snackbar;

public final class UserMessage {

    private UserMessage() {
    }

    /**
     * Shielding the actual implementation of Toast/Snackbar or whatever is next.
     */
    public static void showUserMessage(@NonNull final Activity activity,
                                       @StringRes final int message) {
        if (0 == Prefs.getListPreference(R.string.pk_ui_messages_use, 0)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(activity.getWindow().getDecorView(), message,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Shielding the actual implementation of Toast/Snackbar or whatever is next.
     */
    public static void showUserMessage(@NonNull final Activity activity,
                                       @NonNull final String message) {
        if (0 == Prefs.getListPreference(R.string.pk_ui_messages_use, 0)) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Snackbar.make(activity.getWindow().getDecorView(), message,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Problem child: called from a task (thread) which has no activity/context at all.
     * Hardwired to use the application context.
     * Only called from a UI thread.
     * <p>
     * Does mean we can't use SnackBar
     */
    public static void showUserMessage(@NonNull final String message) {
        Toast.makeText(BookCatalogueApp.getAppContext(), message, Toast.LENGTH_LONG).show();
    }

//    public static void showUserMessage(@StringRes final int message) {
//        Toast.makeText(BookCatalogueApp.getAppContext(), message, Toast.LENGTH_LONG).show();
//    }
}
