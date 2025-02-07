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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.MaintenanceFragment;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

public final class CatastropheDialog {

    private CatastropheDialog() {
    }

    public static void show(@NonNull final Context context,
                            @NonNull final Throwable e,
                            @NonNull final Runnable onConfirm,
                            @NonNull final Runnable onDismiss) {
        String msg = ExMsg
                .map(context, e)
                .orElseGet(() -> ExMsg.getUnexpectedErrorMessage(context));

        if (BuildConfig.DEBUG /* always */) {
            msg += "\n" + Arrays.toString(e.getStackTrace());
        }

        final ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(context.getString(R.string.app_name), msg);
        clipboard.setPrimaryClip(clip);

        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.error_24px)
                .setTitle(R.string.app_name)
                .setMessage(msg)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> onDismiss.run())
                .setOnDismissListener(d -> onDismiss.run())
                .setPositiveButton(R.string.option_bug_report, (d, w) -> {
                    d.dismiss();
                    // We'll TRY to start the maintenance fragment
                    // and take the user directly to the debug report
                    context.startActivity(MaintenanceFragment.createDebugReportIntent(context));
                    onConfirm.run();
                })
                .create()
                .show();
    }
}
