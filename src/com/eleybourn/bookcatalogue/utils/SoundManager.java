package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.settings.Prefs;

public final class SoundManager {

    private SoundManager() {
    }

    public static void beepLow(@NonNull final Context context) {
        if (App.getPrefs().getBoolean(Prefs.pk_scanning_beep_if_isbn_invalid, true)) {
            playFile(context, R.raw.beep_low);
        }
    }

    public static void beepHigh(@NonNull final Context context) {
        if (App.getPrefs().getBoolean(Prefs.pk_scanning_beep_if_isbn_valid, false)) {
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
            Logger.error(SoundManager.class, e);
        }
    }
}
