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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;

/** Isolates the mechanism to send notifications to allow test mocking. */
public final class NotifierImpl
        implements Notifier {

    private boolean mChannelsCreated;

    @Override
    public void send(@NonNull final Context context,
                     @Notifier.NotificationId final int id,
                     @Notifier.Channel final String channel,
                     @NonNull final Intent intent,
                     final int requestCode,
                     final boolean withParentStack,
                     @StringRes final int titleId,
                     @NonNull final CharSequence message) {

        final PendingIntent pendingIntent;
        if (withParentStack) {
            pendingIntent = TaskStackBuilder
                    .create(context)
                    .addNextIntentWithParentStack(intent)
                    .getPendingIntent(requestCode, PendingIntent.FLAG_UPDATE_CURRENT);

        } else {
            //TODO: review https://developer.android.com/training/notify-user/build-notification
            // Sets the Activity to start in a new, empty task
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            pendingIntent = PendingIntent.getActivity(context, requestCode, intent,
                                                      PendingIntent.FLAG_UPDATE_CURRENT);
        }

        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channel);

        switch (channel) {
            case Notifier.CHANNEL_ERROR:
                builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                       .setSmallIcon(R.drawable.ic_baseline_error_24);
                break;

            case Notifier.CHANNEL_WARNING:
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                       .setSmallIcon(R.drawable.ic_baseline_warning_24);
                break;

            case Notifier.CHANNEL_INFO:
                builder.setPriority(NotificationCompat.PRIORITY_LOW)
                       .setSmallIcon(R.drawable.ic_baseline_info_24);
                break;

            default:
                throw new IllegalArgumentException("channel=" + channel);
        }

        final Notification notification = builder
                .setContentTitle(context.getString(titleId))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (!mChannelsCreated) {
            createChannels(context, notificationManager);
            mChannelsCreated = true;
        }

        notificationManager.notify(id, notification);
    }

    private void createChannels(@NonNull final Context context,
                                @NonNull final NotificationManager notificationManager) {
        final List<NotificationChannel> channels = new ArrayList<>();
        channels.add(new NotificationChannel(
                Notifier.CHANNEL_ERROR,
                context.getString(R.string.notification_channel_error),
                NotificationManager.IMPORTANCE_HIGH));
        channels.add(new NotificationChannel(
                Notifier.CHANNEL_WARNING,
                context.getString(R.string.notification_channel_warn),
                NotificationManager.IMPORTANCE_DEFAULT));
        channels.add(new NotificationChannel(
                Notifier.CHANNEL_INFO,
                context.getString(R.string.notification_channel_info),
                NotificationManager.IMPORTANCE_LOW));

        notificationManager.createNotificationChannels(channels);
    }

    @Override
    public void onLocaleChanged(@NonNull final Context context) {
        //  When the locale changes, update the NotificationManager channel names.
        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.getNotificationChannel(Notifier.CHANNEL_ERROR)
                           .setName(context.getString(R.string.notification_channel_error));
        notificationManager.getNotificationChannel(Notifier.CHANNEL_WARNING)
                           .setName(context.getString(R.string.notification_channel_warn));
        notificationManager.getNotificationChannel(Notifier.CHANNEL_INFO)
                           .setName(context.getString(R.string.notification_channel_info));
    }
}
