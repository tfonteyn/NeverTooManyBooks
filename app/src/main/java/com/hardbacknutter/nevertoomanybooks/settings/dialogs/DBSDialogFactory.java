/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;
import com.hardbacknutter.prefslib.Setting;
import com.hardbacknutter.prefslib.SettingsDialogFactory;

public class DBSDialogFactory
        implements SettingsDialogFactory {

    private static final String ERROR_UNKNOWN_TYPE = "Unsupported Setting type: ";

    /**
     * Create a new instance.
     * Provides M3 (floating) dialogs and full support for BottomSheets.
     * <p>
     * Fullscreen dialogs (for devices with small screens) are NOT implemented.
     *
     * @param context       Current context
     * @param setting       to provide the dialog for
     * @param dialogMessage optional message
     *
     * @return new instance
     *
     * @throws IllegalArgumentException (debug) unsupported {@link Setting.Type}
     */
    @Override
    @NonNull
    public DialogFragment create(@NonNull final Context context,
                                 @NonNull final Setting setting,
                                 @Nullable final String dialogMessage) {
        final DialogFragment fragment;
        // Do NOT move this to the constructor.
        // The mode can be changed in the settings DURING this session,
        // hence, we ALWAYS need to read the current setting.
        final DialogMode dialogMode = DialogMode.getMode(context);
        final Setting.Type type = setting.getType();
        switch (dialogMode) {
            case Dialog: {
                switch (type) {
                    case String:
                        fragment = new EditStringDialogFragment();
                        break;
                    case SingleChoice:
                        fragment = new SingleChoiceDialogFragment();
                        break;
                    case MultiChoice:
                        fragment = new MultiChoiceDialogFragment();
                        break;
                    default:
                        throw new IllegalArgumentException(ERROR_UNKNOWN_TYPE + type);
                }
                break;
            }
            case BottomSheet: {
                switch (type) {
                    case String:
                        fragment = new EditStringBottomSheet();
                        break;
                    case SingleChoice:
                        fragment = new SingleChoiceBottomSheet();
                        break;
                    case MultiChoice:
                        fragment = new MultiChoiceBottomSheet();
                        break;
                    default:
                        throw new IllegalArgumentException(ERROR_UNKNOWN_TYPE + type);
                }
                break;
            }
            default: {
                throw new IllegalArgumentException("dialogMode=" + dialogMode
                                                   + ", preference=" + setting);
            }
        }

        final DialogInput args = new DialogInput(setting.getKey(), dialogMessage);
        fragment.setArguments(args.toBundle());
        return fragment;
    }
}
