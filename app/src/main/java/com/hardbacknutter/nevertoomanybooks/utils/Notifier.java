/*
 * @Copyright 2020 HardBackNutter
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

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

public class Notifier
        implements AppLocale.OnLocaleChangedListener {

    public static final int ID_GENERIC = 0;
    public static final int ID_GOODREADS = 1;

    /** IMPORTANCE_HIGH. */
    private static final String CHANNEL_ERROR = "Error";
    /** IMPORTANCE_DEFAULT. */
    private static final String CHANNEL_WARN = "Warning";
    /** IMPORTANCE_LOW. */
    private static final String CHANNEL_INFO = "Info";

    /** Singleton. */
    private static Notifier sInstance;

    /**
     * Constructor. Use {@link #getInstance(Context)}.
     *
     * @param context Current context
     */
    private Notifier(@NonNull final Context context) {
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
                    new NotificationChannel(CHANNEL_WARN,
                                            context.getString(R.string.notification_channel_warn),
                                            NotificationManager.IMPORTANCE_DEFAULT));

            notificationManager.createNotificationChannel(
                    new NotificationChannel(CHANNEL_INFO,
                                            context.getString(R.string.notification_channel_info),
                                            NotificationManager.IMPORTANCE_LOW));
        }
    }

    /**
     * Get/create the singleton instance.
     *
     * @param context Current context
     *
     * @return instance
     */
    @NonNull
    public static Notifier getInstance(@NonNull final Context context) {
        synchronized (DateParser.class) {
            if (sInstance == null) {
                sInstance = new Notifier(context);
                AppLocale.getInstance().registerOnLocaleChangedListener(sInstance);
            }
            return sInstance;
        }
    }

    /**
     * Create a PendingIntent to take the user to this Activity.
     *
     * @param context Current context
     *
     * @return PendingIntent
     */
    @NonNull
    public static PendingIntent createPendingIntent(
            @NonNull final Context context,
            @NonNull final Class<? extends Activity> activityClass) {
        final Intent notifyIntent = new Intent(context, activityClass);

        //TODO: review https://developer.android.com/training/notify-user/build-notification
        // Sets the Activity to start in a new, empty task
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        return PendingIntent.getActivity(context, 0, notifyIntent,
                                         PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Create a PendingIntent to take the user to this Activity.
     *
     * @param context Current context
     *
     * @return PendingIntent
     */
    @NonNull
    public static PendingIntent createPendingIntentWithParentStack(
            @NonNull final Context context,
            @NonNull final Class<? extends Activity> activityClass) {
        final Intent notifyIntent = new Intent(context, activityClass);

        // Create the TaskStackBuilder and add the intent, which inflates the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(notifyIntent);
        // Get the PendingIntent containing the entire back stack
        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Send an info notification.
     *
     * @param context       Current context
     * @param id            notification id
     * @param pendingIntent to use
     * @param titleId       string resource for the notification title
     * @param message       for the notification
     */
    public void sendInfo(@NonNull final Context context,
                         @NotificationId final int id,
                         @NonNull final PendingIntent pendingIntent,
                         @StringRes final int titleId,
                         @NonNull final CharSequence message) {
        final Notification notification = new NotificationCompat.Builder(context, CHANNEL_INFO)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle(context.getString(titleId))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        //noinspection ConstantConditions
        notificationManager.notify(id, notification);
    }

    /**
     * Send an warning notification.
     *
     * @param context       Current context
     * @param id            notification id
     * @param pendingIntent to use
     * @param titleId       string resource for the notification title
     * @param message       for the notification
     */
    public void sendWarn(@NonNull final Context context,
                         @NotificationId final int id,
                         @NonNull final PendingIntent pendingIntent,
                         @StringRes final int titleId,
                         @NonNull final CharSequence message) {
        final Notification notification = new NotificationCompat.Builder(context, CHANNEL_WARN)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(context.getString(titleId))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        //noinspection ConstantConditions
        notificationManager.notify(id, notification);
    }

    /**
     * Send an error notification.
     *
     * @param context       Current context
     * @param id            notification id
     * @param pendingIntent to use
     * @param titleId       string resource for the notification title
     * @param message       for the notification
     */
    public void sendError(@NonNull final Context context,
                          @NotificationId final int id,
                          @NonNull final PendingIntent pendingIntent,
                          @StringRes final int titleId,
                          @NonNull final CharSequence message) {
        final Notification notification = new NotificationCompat.Builder(context, CHANNEL_ERROR)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_error)
                .setContentTitle(context.getString(titleId))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        //noinspection ConstantConditions
        notificationManager.notify(id, notification);
    }

    @Override
    public void onLocaleChanged(@NonNull final Context context) {

        //  When the locale changes, update the NotificationManager channel names.
        if (Build.VERSION.SDK_INT >= 26) {
            final NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            //noinspection ConstantConditions
            notificationManager.getNotificationChannel(CHANNEL_ERROR)
                               .setName(context.getString(R.string.notification_channel_error));
            notificationManager.getNotificationChannel(CHANNEL_WARN)
                               .setName(context.getString(R.string.notification_channel_warn));
            notificationManager.getNotificationChannel(CHANNEL_INFO)
                               .setName(context.getString(R.string.notification_channel_info));
        }
    }

    @IntDef({ID_GENERIC, ID_GOODREADS})
    @Retention(RetentionPolicy.SOURCE)
    @interface NotificationId {

    }
}
