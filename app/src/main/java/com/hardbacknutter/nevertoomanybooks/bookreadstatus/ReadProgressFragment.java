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

import com.hardbacknutter.nevertoomanybooks.databinding.FragmentReadProgressBinding;

/**
 * Allows setting the book status to either a percentage or a "page x of y" status.
 */
public class ReadProgressFragment
        extends Fragment {

    /** Fragment/Log tag. */
    public static final String TAG = "ReadProgressFragment";

    private FragmentReadProgressBinding vb;

    private BookReadStatusViewModel vm;

    private ReadingProgressLauncher editLauncher;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = ReadStatusFragmentFactory.getViewModel(this, requireArguments());

        //noinspection DataFlowIssue
        editLauncher = new ReadingProgressLauncher(
                getActivity(),
                read -> vm.setRead(read),
                readingProgress -> vm.setReadingProgress(readingProgress));
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
        vb.btnReadProgress.setOnClickListener(v -> editLauncher.launch(vm.getReadingProgress()));
    }

    private void reload() {
        final ReadingProgress readingProgress = vm.getReadingProgress();

        //noinspection DataFlowIssue
        vb.lblReadProgress.setText(readingProgress.format(getContext()));

        final int percentage = readingProgress.getPercentage();
        vb.readProgressBar.setProgress(percentage);
        vb.readProgressBar.setVisibility(percentage == 100 || percentage == 0 ? View.GONE
                                                                              : View.VISIBLE);
    }
}
