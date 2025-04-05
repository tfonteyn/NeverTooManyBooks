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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;

public class EngineRegistration {

    /**
     * Bring up an Alert to the user for sites where registration
     * is beneficial or required.
     *
     * @param context    Current context
     * @param sites      the list to check
     * @param onFinished (optional) Runnable to call when all sites have been processed.
     */
    public static void prompt(@NonNull final Context context,
                              @NonNull final Collection<Site> sites,
                              @Nullable final Runnable onFinished) {

        // collect engines which require/benefit from registration only.
        final Deque<EngineId> stack = sites
                .stream()
                .filter(Site::isActive)
                .map(Site::getEngineId)
                .filter(engineId -> engineId.supports(SearchEngine.UserRegistration.class))
                .filter(engineId -> shouldPrompt(context, engineId))
                .collect(Collectors.toCollection(ArrayDeque::new));

        if (stack.isEmpty()) {
            if (onFinished != null) {
                onFinished.run();
            }
            return;
        }
        prompt(context, stack, onFinished);
    }

    /**
     * Recursive stack-based prompt for registration.
     *
     * @param context    Current context
     * @param engineIds  the stack of engines to check
     * @param onFinished (optional) Runnable to call when all sites have been processed.
     */
    private static void prompt(@NonNull final Context context,
                               @NonNull final Deque<EngineId> engineIds,
                               @Nullable final Runnable onFinished) {
        if (!engineIds.isEmpty()) {
            final EngineId engineId = engineIds.poll();
            //noinspection DataFlowIssue
            final SearchEngine.UserRegistration searchEngine =
                    (SearchEngine.UserRegistration) engineId.createSearchEngine(context);

            showRegistrationDialog(context, searchEngine, action -> {
                switch (action) {
                    case Register:
                        context.startActivity(FragmentHostActivity.createIntent(
                                context, searchEngine.getPreferenceFragmentClass()));
                        return;

                    case NotNow:
                    case Never:
                        // restart the loop with the remaining sites to check.
                        prompt(context, engineIds, onFinished);
                        return;

                    case Cancelled:
                        // user explicitly cancelled, we're done here
                        if (onFinished != null) {
                            onFinished.run();
                        }
                        break;
                }
            });
            // we are showing a registration dialog, quit now
            return;
        }

        // all engines have registration, or were dismissed.
        if (onFinished != null) {
            onFinished.run();
        }
    }


    /**
     * Show a registration request dialog.
     *
     * @param context      Current context
     * @param searchEngine to register
     * @param onResult     called after user selects an outcome
     */
    @UiThread
    static void showRegistrationDialog(@NonNull final Context context,
                                       @NonNull final SearchEngine.UserRegistration searchEngine,
                                       @NonNull final Consumer<RegistrationAction> onResult) {

        final AlertDialog.Builder dialogBuilder = new MaterialAlertDialogBuilder(context)
                .setIcon(searchEngine.isRegistrationRequired() ? R.drawable.warning_24px
                                                               : R.drawable.info_24px)
                .setTitle(searchEngine.getEngineId().getLabelResId())
                .setOnCancelListener(d -> onResult
                        .accept(RegistrationAction.Cancelled))
                .setNeutralButton(R.string.action_disable_message, (d, w) -> onResult
                        .accept(RegistrationAction.Never))
                .setNegativeButton(R.string.action_not_now, (d, w) -> onResult
                        .accept(RegistrationAction.NotNow))
                .setPositiveButton(R.string.lbl_credentials, (d, w) -> onResult
                        .accept(RegistrationAction.Register)
                );

        // Use the Dialog's themed context!
        final TextView messageView = new TextView(dialogBuilder.getContext());
        messageView.setText(searchEngine.getRegistrationInfo(context));
        messageView.setAutoLinkMask(Linkify.WEB_URLS);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());

        dialogBuilder.setView(messageView)
                     .create()
                     .show();
    }

    private static boolean shouldPrompt(@NonNull final Context context,
                                        @NonNull final EngineId engineId) {
        final String key = engineId.getPreferenceKey() + ".registration.prompt";
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(key, true);
    }

    private static void setShouldPrompt(@NonNull final Context context,
                                        @NonNull final EngineId engineId,
                                        final boolean flag) {
        final String key = engineId.getPreferenceKey() + ".registration.prompt";
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putBoolean(key, flag)
                         .apply();
    }

    enum RegistrationAction {
        Register,
        NotNow,
        Never,
        Cancelled
    }
}
