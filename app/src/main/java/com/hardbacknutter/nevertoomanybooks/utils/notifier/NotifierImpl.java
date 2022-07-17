/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.notifier;

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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Isolates the mechanism to send notifications to allow test mocking. */
public final class NotifierImpl
        implements Notifier {

    private boolean channelsCreated;

    @Override
    public void send(@NonNull final Context context,
                     @NonNull final Channel channel,
                     @NonNull final Intent intent,
                     @NonNull final CharSequence message,
                     @StringRes final int dialogTitleResId,
                     final int notificationId,
                     final int requestCode,
                     final boolean withParentStack) {

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

            // Apps targeting Android 12 and higher must specify either FLAG_IMMUTABLE
            // or FLAG_MUTABLE when constructing a PendingIntent.
            pendingIntent = PendingIntent.getActivity(context, requestCode, intent,
                                                      PendingIntent.FLAG_IMMUTABLE
                                                      | PendingIntent.FLAG_UPDATE_CURRENT);
        }

        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (!channelsCreated) {
            createChannels(context, notificationManager);
            channelsCreated = true;
        }

        final Notification notification = new NotificationCompat.Builder(context, channel.id)
                .setPriority(channel.priority)
                .setSmallIcon(channel.drawableId)
                .setContentTitle(context.getString(dialogTitleResId))
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        notificationManager.notify(notificationId, notification);
    }

    private void createChannels(@NonNull final Context context,
                                @NonNull final NotificationManager notificationManager) {

        final List<NotificationChannel> channels =
                Arrays.stream(Channel.values())
                      .map(channel -> new NotificationChannel(
                              channel.id, context.getString(channel.stringId), channel.importance))
                      .collect(Collectors.toList());

        notificationManager.createNotificationChannels(channels);
    }

    @Override
    public void onLocaleChanged(@NonNull final Context context) {
        //  When the locale changes, update the NotificationManager channel names.
        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Arrays.stream(Channel.values())
              .forEach(channel -> notificationManager.getNotificationChannel(channel.id)
                                                     .setName(context.getString(channel.stringId)));
    }
}
