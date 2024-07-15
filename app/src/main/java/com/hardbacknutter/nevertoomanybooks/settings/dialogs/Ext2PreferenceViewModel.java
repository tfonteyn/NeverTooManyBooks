/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.preference.DialogPreference;

@SuppressWarnings("WeakerAccess")
public class Ext2PreferenceViewModel
        extends ViewModel {

    static final String ARG_KEY = "key";

    private boolean initDone;

    @Nullable
    private BitmapDrawable dialogIcon;
    @Nullable
    private CharSequence dialogTitle;
    @Nullable
    private CharSequence dialogMessage;
    @Nullable
    private CharSequence negativeButtonText;
    @Nullable
    private CharSequence positiveButtonText;

    /**
     * Since the preference object may not be available during fragment re-creation, the necessary
     * information for displaying the dialog is read once during the initial call.
     * Subclasses should also follow this pattern.
     * <p>
     * Note that {@link DialogPreference#getDialogLayoutResource()} is <strong>IGNORED</strong>.
     *
     * @param context    Current context
     * @param preference to display
     */
    @CallSuper
    public void init(@NonNull final Context context,
                     @NonNull final DialogPreference preference) {
        if (!initDone) {
            initDone = true;
            dialogTitle = preference.getDialogTitle();
            positiveButtonText = preference.getPositiveButtonText();
            negativeButtonText = preference.getNegativeButtonText();
            dialogMessage = preference.getDialogMessage();

            final Drawable icon = preference.getDialogIcon();
            if (icon == null || icon instanceof BitmapDrawable) {
                dialogIcon = (BitmapDrawable) icon;
            } else {
                final Bitmap bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(),
                                                          icon.getIntrinsicHeight(),
                                                          Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                icon.draw(canvas);
                dialogIcon = new BitmapDrawable(context.getResources(), bitmap);
            }
        }
    }

    @Nullable
    BitmapDrawable getDialogIcon() {
        return dialogIcon;
    }

    @Nullable
    CharSequence getDialogTitle() {
        return dialogTitle;
    }

    @Nullable
    CharSequence getDialogMessage() {
        return dialogMessage;
    }

    @Nullable
    CharSequence getNegativeButtonText() {
        return negativeButtonText;
    }

    @Nullable
    CharSequence getPositiveButtonText() {
        return positiveButtonText;
    }
}
