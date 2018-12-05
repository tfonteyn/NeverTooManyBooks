package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;

public class SoundManager {
    private static final String TAG = "SoundManager.";
    public static final String PREF_BEEP_IF_SCANNED_ISBN_INVALID = TAG + "BeepIfScannedIsbnInvalid";
    public static final String PREF_BEEP_IF_SCANNED_ISBN_VALID = TAG + "BeepIfScannedIsbnValid";

    private SoundManager() {
    }

    public static void beepLow(final @NonNull Context context) {
        if (BookCatalogueApp.getBooleanPreference(PREF_BEEP_IF_SCANNED_ISBN_INVALID, true)) {
            playFile(context, R.raw.beep_low);
        }
    }

    public static void beepHigh(final @NonNull Context context) {
        if (BookCatalogueApp.getBooleanPreference(PREF_BEEP_IF_SCANNED_ISBN_VALID, false)) {
            playFile(context, R.raw.beep_high);
        }
    }

    private static void playFile(final @NonNull Context context, final @RawRes int resId) {
        try {
            AssetFileDescriptor file = context.getResources().openRawResourceFd(resId);
            MediaPlayer player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // When the beep has finished playing, rewind to queue up another one.
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(final @NonNull MediaPlayer player) {
                    player.release();
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
