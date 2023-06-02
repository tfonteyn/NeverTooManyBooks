/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class SoundManager {

    public static final int POSITIVE = ToneGenerator.TONE_PROP_ACK;
    public static final int NEGATIVE = ToneGenerator.TONE_PROP_NACK;
    public static final int EVENT = ToneGenerator.TONE_PROP_BEEP;

    public static final String PK_SOUNDS_SCAN_ISBN_VALID = "sounds.scan.isbn.valid";
    public static final String PK_SOUNDS_SCAN_ISBN_INVALID = "sounds.scan.isbn.invalid";
    public static final String PK_SOUNDS_SCAN_FOUND_BARCODE = "sounds.scan.barcode.found";

    private static final int MAX_LEN = 500;

    private SoundManager() {
    }

    /**
     * Play a generic tone. If as tone is longer than 500ms, it will be cutoff.
     *
     * @param tone one of {@link #POSITIVE}, {@link #NEGATIVE} or {@link #EVENT}
     *             or any of the {@link ToneGenerator} predefined tones.
     */
    public static void beep(@Tone final int tone) {
        //noinspection CheckStyle
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
     * Optionally beep if the scanned barcode represents a valid ISBN.
     *
     * @param context Current context
     */
    public static void beepOnValidIsbn(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(PK_SOUNDS_SCAN_ISBN_VALID, false)) {
            beep(POSITIVE);
        }
    }

    /**
     * Optionally beep if the scanned barcode is not a valid ISBN.
     *
     * @param context Current context
     */
    public static void beepOnInvalidIsbn(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(PK_SOUNDS_SCAN_ISBN_INVALID, true)) {
            beep(NEGATIVE);
        }
    }

    /**
     * Optionally beep if the scan produced a valid barcode (of any type).
     *
     * @param context Current context
     */
    public static void beepOnBarcodeFound(@NonNull final Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(PK_SOUNDS_SCAN_FOUND_BARCODE, true)) {
            beep(EVENT);
        }
    }

    @IntDef({
            POSITIVE,
            NEGATIVE,
            EVENT
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Tone {

    }
}
