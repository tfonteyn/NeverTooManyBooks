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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BookshelfFiltersBottomSheet;
import com.hardbacknutter.nevertoomanybooks.BookshelfFiltersDialogFragment;
import com.hardbacknutter.nevertoomanybooks.StylePickerBottomSheet;
import com.hardbacknutter.nevertoomanybooks.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditColorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditFormatDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditGenreDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLanguageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLocationDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditPublisherDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesBottomSheet;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;

public abstract class DialogLauncher
        implements FragmentResultListener {

    public static final String RK_STYLE_PICKER = "RK_STYLE_PICKER";
    public static final String RK_FILTERS = "RK_FILTERS";

    private static final Map<String, Supplier<DialogFragment>> BOTTOM_SHEET =
            Map.ofEntries(
                    Map.entry(DBKey.FK_BOOKSHELF, EditBookshelfBottomSheet::new),
                    Map.entry(DBKey.FK_AUTHOR, EditAuthorBottomSheet::new),
                    Map.entry(DBKey.FK_SERIES, EditSeriesBottomSheet::new),
                    Map.entry(DBKey.FK_PUBLISHER, EditBookshelfBottomSheet::new),
                    Map.entry(DBKey.LOANEE_NAME, EditLenderBottomSheet::new),

                    Map.entry(DBKey.COLOR, EditColorDialogFragment::new),
                    Map.entry(DBKey.FORMAT, EditFormatDialogFragment::new),
                    Map.entry(DBKey.GENRE, EditGenreDialogFragment::new),
                    Map.entry(DBKey.LANGUAGE, EditLanguageDialogFragment::new),
                    Map.entry(DBKey.LOCATION, EditLocationDialogFragment::new),

                    Map.entry(RK_STYLE_PICKER, StylePickerBottomSheet::new),
                    Map.entry(RK_FILTERS, BookshelfFiltersBottomSheet::new)
            );
    private static final Map<String, Supplier<DialogFragment>> DIALOG =
            Map.ofEntries(
                    Map.entry(DBKey.FK_BOOKSHELF, EditBookshelfDialogFragment::new),
                    Map.entry(DBKey.FK_AUTHOR, EditAuthorDialogFragment::new),
                    Map.entry(DBKey.FK_SERIES, EditSeriesDialogFragment::new),
                    Map.entry(DBKey.FK_PUBLISHER, EditPublisherDialogFragment::new),
                    Map.entry(DBKey.LOANEE_NAME, EditLenderDialogFragment::new),

                    Map.entry(DBKey.COLOR, EditColorDialogFragment::new),
                    Map.entry(DBKey.FORMAT, EditFormatDialogFragment::new),
                    Map.entry(DBKey.GENRE, EditGenreDialogFragment::new),
                    Map.entry(DBKey.LANGUAGE, EditLanguageDialogFragment::new),
                    Map.entry(DBKey.LOCATION, EditLocationDialogFragment::new),

                    Map.entry(RK_STYLE_PICKER, StylePickerDialogFragment::new),
                    Map.entry(RK_FILTERS, BookshelfFiltersDialogFragment::new)
            );
    private static final String TAG = "DialogLauncher";
    /**
     * The bundle key to pass the {@link #requestKey} around.
     * Keep in mind this value is irrelevant to the Android OS.
     * Only the actual {@link #requestKey} is relevant to Android.
     */
    public static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /**
     * FragmentResultListener request key to use for our response.
     * Doubles up as the fragment TAG
     */
    @NonNull
    private final String requestKey;

    @NonNull
    private final Supplier<DialogFragment> dialogFragmentSupplier;
    @Nullable
    private FragmentManager fragmentManager;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param dialogSupplier a supplier for a new DialogFragment
     */
    protected DialogLauncher(@NonNull final String requestKey,
                             @NonNull final Supplier<DialogFragment> dialogSupplier) {
        this.requestKey = requestKey;
        this.dialogFragmentSupplier = dialogSupplier;
    }

    /**
     * Constructor.
     *
     * @param activity       hosting Activity
     * @param requestKey     FragmentResultListener request key to use for our response.
     */
    protected DialogLauncher(@NonNull final FragmentActivity activity,
                             @NonNull final String requestKey) {
        this.requestKey = requestKey;
        this.dialogFragmentSupplier = getDialogSupplier(activity, requestKey);
    }

    @NonNull
    private Supplier<DialogFragment> getDialogSupplier(
            @NonNull final FragmentActivity activity,
            @NonNull final String requestKey) {

        if (WindowSizeClass.getWidth(activity) == WindowSizeClass.Expanded) {
            // Tablets use a Dialog
            return Objects.requireNonNull(DIALOG.get(requestKey), requestKey);

        } else {
            // Phones use a BottomSheet
            return Objects.requireNonNull(BOTTOM_SHEET.get(requestKey), requestKey);
        }
    }

    /**
     * Register this object for receiving Fragment results.
     *
     * @param fragmentManager typically the {@link Fragment#getChildFragmentManager()}
     *                        or the {@link AppCompatActivity#getSupportFragmentManager()}
     * @param lifecycleOwner  typically the {@link Fragment} or the {@link AppCompatActivity}
     */
    public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        this.fragmentManager = fragmentManager;
        this.fragmentManager.setFragmentResultListener(requestKey, lifecycleOwner, this);
    }

    /**
     * Create the dialog, setup the arguments adding the requestKey and show it.
     *
     * @param args to pass
     */
    protected void createDialog(@NonNull final Bundle args) {
        Objects.requireNonNull(fragmentManager, "fragmentManager");

        args.putString(BKEY_REQUEST_KEY, requestKey);

        final DialogFragment dialogFragment = dialogFragmentSupplier.get();
        dialogFragment.setArguments(args);
        // using the requestKey as the fragment tag.
        dialogFragment.show(fragmentManager, requestKey);
    }
}
