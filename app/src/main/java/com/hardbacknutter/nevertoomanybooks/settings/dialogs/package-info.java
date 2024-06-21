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

/**
 * These are extensions to the dialogs used by a {@link androidx.preference.DialogPreference}.
 * They use the {@link com.google.android.material.dialog.MaterialAlertDialogBuilder}
 * instead of the system {@link androidx.appcompat.app.AlertDialog}.
 * <p>
 * See {@link com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment#onDisplayPreferenceDialog(androidx.preference.Preference)}
 * and {@link com.hardbacknutter.nevertoomanybooks.settings.dialogs.PreferenceDialogFactory}
 * how they are used instead of the defaults.
 */
package com.hardbacknutter.nevertoomanybooks.settings.dialogs;
