/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

public final class TipManager {
    private static final TipManager INSTANCE = new TipManager();

    /** Track tips displayed during the current app session. */
    private final Set<Tip> hasBeenDisplayed = EnumSet.noneOf(Tip.class);

    private TipManager() {
    }

    /**
     * Retrieve the singleton instance.
     *
     * @return singleton
     */
    @NonNull
    public static TipManager getInstance() {
        return INSTANCE;
    }

    /**
     * Reset all tips so that they will be displayed again.
     *
     */
    public void reset() {
        // remove all. This has the benefit of removing any obsolete keys.
        reset(Tip.PK_TIP);
        hasBeenDisplayed.clear();
    }

    /**
     * Reset a sub set of tips, all starting (in preferences) with the given prefix.
     *
     * @param prefix to match
     */
    public void reset(@NonNull final String prefix) {
        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
        final SharedPreferences.Editor ed = prefs.edit();
        prefs.getAll()
             .keySet()
             .stream()
             .filter(key -> key.toLowerCase(Locale.ENGLISH)
                               .startsWith(prefix.toLowerCase(Locale.ENGLISH)))
             .forEach(ed::remove);
        ed.apply();
    }

    private boolean shouldBeDisplayed(@NonNull final Tip tip) {
        return !hasBeenDisplayed.contains(tip) && tip.isEnabled();
    }

    /**
     * Create the required tip, if the user has not disabled it and it's not been shown
     * before during this app run.
     *
     * @param context Current context
     * @param tip     the tip
     */
    public void show(@NonNull final Context context,
                     @NonNull final Tip tip) {
        show(context, tip, null, (Object[]) null);
    }

    /**
     * Create the required tip, if the user has not disabled it and it's not been shown
     * before during this app run.
     *
     * @param context  Current context
     * @param tip      the tip
     * @param postRun  Optional Runnable to run after the tip was dismissed.
     *                 IMPORTANT: if this method return no dialog,
     *                 the postRun <strong>is executed immediately</strong>
     * @param textArgs Optional arguments for the tip string
     */
    public void show(@NonNull final Context context,
                     @NonNull final Tip tip,
                     @Nullable final Runnable postRun,
                     @Nullable final Object... textArgs) {
        if (shouldBeDisplayed(tip)) {
            tip.create(context, postRun, textArgs)
               .show();
            hasBeenDisplayed.add(tip);
        } else {
            if (postRun != null) {
                postRun.run();
            }
        }
    }
}
