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

package com.hardbacknutter.nevertoomanybooks.bookdetails;

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
import com.hardbacknutter.nevertoomanybooks.dialogs.ReadProgressDialogFragment;

/**
 * Allows setting the book status to either a percentage or a "page x of y" status.
 */
public class ReadProgressFragment
        extends Fragment {

    static final String TAG = "ReadProgressFragment";

    private ShowBookDetailsViewModel vm;

    private final ReadProgressDialogFragment.Launcher editLauncher =
            new ReadProgressDialogFragment.Launcher(
                    new ReadProgressDialogFragment.Launcher.OnModifiedCallback() {
                        @Override
                        public void onModified(@NonNull final String requestKey,
                                               final boolean read) {
                            vm.setRead(read);
                        }

                        @Override
                        public void onModified(@NonNull final String requestKey,
                                               @NonNull final ReadProgress readProgress) {
                            vm.setReadProgress(readProgress);
                        }
                    });

    private FragmentReadProgressBinding vb;

    @NonNull
    static Fragment create() {
        return new ReadProgressFragment();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(ShowBookDetailsViewModel.class);

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
        vb.lblReadProgress.setOnClickListener(v -> editLauncher.launch(vm.getBook()));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    void reload() {
        final ReadProgress readProgress = vm.getBook().getReadProgress();

        final int percentage = readProgress.getPercentage();
        final String txt;
        if (readProgress.asPercentage()) {
            txt = getString(R.string.info_progress_x_percent, percentage);
        } else {
            txt = getString(R.string.info_progress_page_x_of_y,
                            readProgress.getCurrentPage(),
                            readProgress.getTotalPages());
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
