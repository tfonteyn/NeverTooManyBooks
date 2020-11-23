/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogFragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;

public class ImportOptionsDialogFragment
        extends BaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "ImportOptionsDialogFragment";
    protected static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    private ImportViewModel mImportViewModel;
    /** View Binding. */
    private DialogImportOptionsBinding mVb;

    /**
     * No-arg constructor for OS use.
     */
    public ImportOptionsDialogFragment() {
        super(R.layout.dialog_import_options);
        setFloatingDialogWidth(R.dimen.floating_dialogs_import_options_width);
    }


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequestKey = Objects.requireNonNull(requireArguments().getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogImportOptionsBinding.bind(view);

        //noinspection ConstantConditions
        mImportViewModel = new ViewModelProvider(getActivity()).get(ImportViewModel.class);

        setupOptions();
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        sendResult(false);
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            sendResult(mImportViewModel.getImportHelper().hasEntityOption());
            return true;
        }
        return false;
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setupOptions() {
        final ImportHelper helper = mImportViewModel.getImportHelper();

        //noinspection ConstantConditions
        if (helper.isBooksOnlyContainer(getContext())) {
            // remove all non-book options if we're importing from a file/archive
            // which only contains books.
            mVb.cbxGroup.setVisibility(View.GONE);

        } else {
            mVb.cbxBooks.setChecked(helper.isOptionSet(ImportHelper.OPTIONS_BOOKS));
            mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
                helper.setOption(ImportHelper.OPTIONS_BOOKS, isChecked);
                mVb.rbBooksGroup.setEnabled(isChecked);
            });

            mVb.cbxCovers.setChecked(helper.isOptionSet(ImportHelper.OPTIONS_COVERS));
            mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                    .setOption(ImportHelper.OPTIONS_COVERS, isChecked));

            mVb.cbxPrefs.setChecked(helper.isOptionSet(ImportHelper.OPTIONS_PREFS));
            mVb.cbxPrefs.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                    .setOption(ImportHelper.OPTIONS_PREFS, isChecked));

            mVb.cbxStyles.setChecked(helper.isOptionSet(ImportHelper.OPTIONS_STYLES));
            mVb.cbxStyles.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                    .setOption(ImportHelper.OPTIONS_STYLES, isChecked));
        }

        mVb.rbUpdatedBooksSkip.setChecked(helper.isSkipUpdatedBooks());
        mVb.rbUpdatedBooksSkipInfo.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbUpdatedBooksOverwrite.setChecked(helper.isOverwriteUpdatedBook());
        mVb.rbUpdatedBooksOverwriteInfo.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbUpdatedBooksSync.setChecked(helper.isSyncUpdatedBooks());
        mVb.rbUpdatedBooksSyncInfo.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == mVb.rbUpdatedBooksSkip.getId()) {
                helper.setSkipUpdatedBooks();

            } else if (checkedId == mVb.rbUpdatedBooksOverwrite.getId()) {
                helper.setOverwriteUpdatedBook();

            } else if (checkedId == mVb.rbUpdatedBooksSync.getId()) {
                helper.setSyncUpdatedBooks();
            }
        });
    }

    /**
     * Send a {@code true} if the user wants to go ahead,
     * or a {@code false} if they cancelled.
     *
     * @param startTask flag
     */
    private void sendResult(final boolean startTask) {
        if (BuildConfig.DEBUG /* always */) {
            Logger.d(TAG, "sendResult", mImportViewModel.getImportHelper().toString());
        }
        Launcher.sendResult(this, mRequestKey, startTask);
        dismiss();
    }

    public abstract static class Launcher
            extends DialogFragmentLauncherBase {

        private static final String START_TASK = "startTask";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               final boolean startTask) {
            final Bundle result = new Bundle(1);
            result.putBoolean(START_TASK, startTask);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         */
        public void launch() {
            final Bundle args = new Bundle(1);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);

            final DialogFragment frag = new ImportOptionsDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, ImportOptionsDialogFragment.TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(result.getBoolean(START_TASK));
        }

        /**
         * Callback handler.
         *
         * @param startTask {@code true} if the user confirmed the dialog.
         *                  i.e. if the import task should be started
         */
        public abstract void onResult(boolean startTask);
    }
}
