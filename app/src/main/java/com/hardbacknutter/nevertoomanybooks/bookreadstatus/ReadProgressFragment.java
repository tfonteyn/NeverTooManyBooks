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

package com.hardbacknutter.nevertoomanybooks.bookreadstatus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentReadProgressBinding;

/**
 * Allows setting the book status to either a percentage or a "page x of y" status.
 */
public class ReadProgressFragment
        extends Fragment {

    public static final String TAG = "ReadProgressFragment";

    private FragmentReadProgressBinding vb;

    private BookReadStatusViewModel vm;

    private final ReadingProgressDialogFragment.Launcher editLauncher =
            new ReadingProgressDialogFragment.Launcher(
                    new ReadingProgressDialogFragment.Launcher.ResultListener() {
                        @Override
                        public void onModified(@NonNull final String requestKey,
                                               final boolean read) {
                            vm.setRead(read);
                        }

                        @Override
                        public void onModified(@NonNull final String requestKey,
                                               @NonNull final ReadingProgress readingProgress) {
                            vm.setReadingProgress(readingProgress);
                        }
                    });

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(
                ReadStatusFragmentFactory.getViewModelClass(requireArguments()));

        editLauncher.registerForFragmentResult(getChildFragmentManager(), this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentReadProgressBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vm.onReadStatusChanged().observe(getViewLifecycleOwner(), aVoid -> reload());

        reload();
        vb.lblReadProgress.setOnClickListener(v -> editLauncher.launch(vm.getReadingProgress()));
    }

    public void reload() {
        final ReadingProgress readingProgress = vm.getReadingProgress();

        final int percentage = readingProgress.getPercentage();
        final String txt;
        if (readingProgress.asPercentage()) {
            txt = getString(R.string.info_progress_x_percent, percentage);
        } else {
            txt = getString(R.string.info_progress_page_x_of_y,
                            readingProgress.getCurrentPage(),
                            readingProgress.getTotalPages());
        }

        switch (percentage) {
            case 100:
                vb.lblReadProgress.setText(R.string.lbl_read);
                vb.readProgress.setVisibility(View.GONE);
                vb.readProgress.setProgress(100);
                break;
            case 0:
                vb.lblReadProgress.setText(R.string.lbl_unread);
                vb.readProgress.setVisibility(View.GONE);
                vb.readProgress.setProgress(0);
                break;
            default:
                vb.lblReadProgress.setText(txt);
                vb.readProgress.setVisibility(View.VISIBLE);
                vb.readProgress.setProgress(percentage);
                break;
        }
    }

}
