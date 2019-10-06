/*
 * @Copyright 2019 HardBackNutter
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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class SoundManager {

    private SoundManager() {
    }

    public static void beepLow(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.pk_scanner_beep_if_invalid, true)) {
            playFile(context, R.raw.beep_low);
        }
    }

    public static void beepHigh(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(Prefs.pk_scanner_beep_if_valid, false)) {
            playFile(context, R.raw.beep_high);
        }
    }

    private static void playFile(@NonNull final Context context,
                                 @RawRes final int resId) {
        try {
            AssetFileDescriptor file = context.getResources().openRawResourceFd(resId);
            MediaPlayer player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // When the beep has finished playing, rewind to queue up another one.
            player.setOnCompletionListener(MediaPlayer::release);
            player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            file.close();
            player.setVolume(0.2f, 0.2f);
            player.prepare();
            player.start();
        } catch (@NonNull final Resources.NotFoundException e) {
            throw new IllegalStateException(e);
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
            // No sound is critical.
            Logger.warn(context, SoundManager.class, "playFile", e);
        }
    }
}
