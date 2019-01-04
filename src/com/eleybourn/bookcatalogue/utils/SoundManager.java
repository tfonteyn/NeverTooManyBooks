package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;

public final class SoundManager {
    private SoundManager() {
    }

    public static void beepLow(@NonNull final Context context) {
        if (Prefs.getBoolean(R.string.pk_scanning_beep_if_isbn_invalid, true)) {
            playFile(context, R.raw.beep_low);
        }
    }

    public static void beepHigh(@NonNull final Context context) {
        if (Prefs.getBoolean(R.string.pk_scanning_beep_if_isbn_valid, false)) {
            playFile(context, R.raw.beep_high);
        }
    }

    private static void playFile(@NonNull final Context context, final @RawRes int resId) {
        try {
            AssetFileDescriptor file = context.getResources().openRawResourceFd(resId);
            MediaPlayer player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // When the beep has finished playing, rewind to queue up another one.
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(@NonNull final MediaPlayer mp) {
                    mp.release();
                }
            });
            player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            file.close();
            player.setVolume(0.2f, 0.2f);
            player.prepare();
            player.start();
        } catch (Exception e) {
            // No sound is critical. Just log errors
            Logger.error(e);
        }
    }
}
