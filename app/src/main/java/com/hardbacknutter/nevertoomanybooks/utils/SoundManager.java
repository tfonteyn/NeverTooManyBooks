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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.zxing.client.android.BeepManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class SoundManager {

    public static final int POSITIVE = ToneGenerator.TONE_PROP_ACK;
    public static final int NEGATIVE = ToneGenerator.TONE_PROP_NACK;
    private static final int MAX_LEN = 500;

    private SoundManager() {
    }

    /**
     * Play a generic tone. If as tone is longer than 500ms, it will be cutoff.
     *
     * @param tone one of {@link #POSITIVE} or {@link #NEGATIVE}
     *             or any of the {@link ToneGenerator} predefined tones.
     */
    public static void beep(@Tone final int tone) {
        try {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION,
                                                       ToneGenerator.MAX_VOLUME);
            tg.startTone(tone);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                tg.stopTone();
                tg.release();
            }, MAX_LEN);

        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception ignore) {
            // No sound is critical.
        }
    }

    /**
     * Play the builtin ZXing beep.
     */
    public static void zxingBeep(@NonNull final FragmentActivity activity) {
        final MediaPlayer mp = new BeepManager(activity).playBeepSound();
        if (mp != null) {
            new Handler(Looper.getMainLooper()).postDelayed(mp::release, MAX_LEN);
        }
    }

    @IntDef({
            POSITIVE,
            NEGATIVE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Tone {

    }
}
