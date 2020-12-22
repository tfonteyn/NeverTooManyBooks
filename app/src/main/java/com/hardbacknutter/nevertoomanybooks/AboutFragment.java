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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAboutBinding;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

public class AboutFragment
        extends Fragment {

    public static final String TAG = "AboutFragment";

    /** View Binding. */
    private FragmentAboutBinding mVb;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentAboutBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_about);

        //noinspection ConstantConditions
        final PackageInfoWrapper packageInfoWrapper = PackageInfoWrapper.create(getContext());
        mVb.version.setText(packageInfoWrapper.getVersionName());

        mVb.btnSourcecodeUrl.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sourcecode_url)))));

        // just running this on the UI thread...
        try (DAO db = new DAO(TAG)) {
            mVb.bookCount.setText(String.valueOf(db.countBooks()));
            mVb.seriesCount.setText(String.valueOf(db.countSeries()));
            mVb.authorCount.setText(String.valueOf(db.countAuthors()));
            mVb.publisherCount.setText(String.valueOf(db.countPublishers()));
        }
    }
}
