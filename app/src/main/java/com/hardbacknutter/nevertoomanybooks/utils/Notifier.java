/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;

public final class Notifier {

    /** IMPORTANCE_HIGH. */
    public static final String CHANNEL_ERROR = "Error";
    /** IMPORTANCE_DEFAULT. */
    public static final String CHANNEL_INFO = "Info";

    /** Steady increment. */
    private static final AtomicInteger NOTIFICATION_ID = new AtomicInteger();

    private Notifier() {
    }

    /**
     * Create the Notification channels we need.
     *
     * This is called during startup.
     *
     * @param context Current context
     */
    public static void init(@NonNull final Context context) {
        //Notification channels should only be created for devices running Android 26
        if (Build.VERSION.SDK_INT >= 26) {
            final NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            //noinspection ConstantConditions
            notificationManager.createNotificationChannel(
                    new NotificationChannel(CHANNEL_ERROR,
                                            context.getString(R.string.notification_channel_error),
                                            NotificationManager.IMPORTANCE_HIGH));

            notificationManager.createNotificationChannel(
                    new NotificationChannel(CHANNEL_INFO,
                                            context.getString(R.string.notification_channel_info),
                                            NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    /**
     * Show a notification while this app is running.
     *
     * @param context   Current context
     * @param channelId to use
     * @param title     the title to display
     * @param message   the message to display
     */
    public static void show(@NonNull final Context context,
                            @NonNull final String channelId,
                            @NonNull final CharSequence title,
                            @NonNull final CharSequence message) {

        final Intent intent = new Intent(context, StartupActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);

        // The PendingIntent to launch our activity if the user selects this notification
        final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            // Slight overkill. We update the channel name here for the chance that the locale
            // changed. This is unlikely to happen frequently, but does no harm.
            //noinspection ConstantConditions
            NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
            //noinspection SwitchStatementWithoutDefaultBranch
            switch (channelId) {
                case CHANNEL_ERROR:
                    channel.setName(context.getString(R.string.notification_channel_error));
                    break;
                case CHANNEL_INFO:
                    channel.setName(context.getString(R.string.notification_channel_info));
                    break;
            }
        }

        int icon;
        switch (channelId) {
            case CHANNEL_ERROR:
                icon = R.drawable.ic_warning;
                break;
            case CHANNEL_INFO:
            default:
                icon = R.drawable.ic_info_outline;
                break;
        }

        final Notification notification = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        //noinspection ConstantConditions
        notificationManager.notify(NOTIFICATION_ID.incrementAndGet(), notification);
    }
}
