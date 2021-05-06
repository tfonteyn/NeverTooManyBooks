/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.StringRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface Notifier
        extends AppLocale.OnLocaleChangedListener {

    /** Notification id. */
    int ID_GENERIC = 0;

    /** RequestCode. For now, we always use 0. */
    int RC_DEFAULT = 0;

    /** IMPORTANCE_HIGH. */
    String CHANNEL_ERROR = "Error";
    /** IMPORTANCE_DEFAULT. */
    String CHANNEL_WARNING = "Warning";
    /** IMPORTANCE_LOW. */
    String CHANNEL_INFO = "Info";

    /**
     * Convenience wrapper which uses some sensible defaults for a generic error.
     *
     * @param context Current context
     * @param intent  to use for the {@link PendingIntent}
     * @param message to show
     */
    default void sendError(@NonNull final Context context,
                           @NonNull final Intent intent,
                           @NonNull final CharSequence message) {
        send(context, ID_GENERIC, CHANNEL_ERROR, intent, RC_DEFAULT, false,
             android.R.string.dialog_alert_title, message);
    }

    default void sendError(@NonNull final Context context,
                           @Notifier.NotificationId final int id,
                           @NonNull final Intent intent,
                           final boolean withParentStack,
                           @StringRes final int titleId,
                           @NonNull final CharSequence message) {
        send(context, id, CHANNEL_ERROR, intent, RC_DEFAULT, withParentStack, titleId, message);
    }

    default void sendWarning(@NonNull final Context context,
                             @Notifier.NotificationId final int id,
                             @NonNull final Intent intent,
                             final boolean withParentStack,
                             @StringRes final int titleId,
                             @NonNull final CharSequence message) {
        send(context, id, CHANNEL_WARNING, intent, RC_DEFAULT, withParentStack, titleId, message);
    }

    default void sendInfo(@NonNull final Context context,
                          @Notifier.NotificationId final int id,
                          @NonNull final Intent intent,
                          final boolean withParentStack,
                          @StringRes final int titleId,
                          @NonNull final CharSequence message) {
        send(context, id, CHANNEL_INFO, intent, RC_DEFAULT, withParentStack, titleId, message);
    }

    /**
     * Send a notification.
     *
     * @param context Current context
     * @param id      notification id
     * @param channel to use
     * @param intent  to use for the {@link PendingIntent}
     * @param titleId string resource for the notification title
     * @param message to show
     */
    void send(@NonNull Context context,
              @Notifier.NotificationId int id,
              @Notifier.Channel String channel,
              @NonNull Intent intent,
              int requestCode,
              boolean withParentStack,
              @StringRes int titleId,
              @NonNull CharSequence message);

    @IntDef(ID_GENERIC)
    @Retention(RetentionPolicy.SOURCE)
    @interface NotificationId {

    }

    @StringDef({CHANNEL_ERROR, CHANNEL_WARNING, CHANNEL_INFO})
    @Retention(RetentionPolicy.SOURCE)
    @interface Channel {

    }
}
