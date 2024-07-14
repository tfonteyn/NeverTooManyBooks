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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;

import com.google.android.material.appbar.AppBarLayout;

import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.MarginWindowInsetListener;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAboutBinding;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

public class AboutFragment
        extends BaseFragment {

    /** View Binding. */
    private FragmentAboutBinding vb;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentAboutBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_about);

        // Don't scroll
        final AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams)
                toolbar.getLayoutParams();
        lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);

        //noinspection DataFlowIssue
        final PackageInfoWrapper packageInfoWrapper =
                PackageInfoWrapper.createWithSignatures(getContext());
        // show the version in the header
        vb.version.setText(packageInfoWrapper.getVersionName());

        // show the full version + build date in the bottom corner
        final String code = "a" + packageInfoWrapper.getVersionCode()
                            + " d" + DBHelper.DATABASE_VERSION
                            + " b" + BuildConfig.TIMESTAMP
                            + (packageInfoWrapper.getSignedBy().isPresent() ? " s" : "");
        vb.debugVersion.setText(code);
        // edge2edge: Make sure it's not hidden by the bottom-system-bar
        ViewCompat.setOnApplyWindowInsetsListener(vb.debugVersion, new MarginWindowInsetListener(
                vb.debugVersion, true, false, true, true));

        vb.btnSourcecodeUrl.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_project_url)))));

        // just running these on the UI thread...
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        vb.bookCount.setText(String.valueOf(serviceLocator.getBookDao().count()));
        vb.seriesCount.setText(String.valueOf(serviceLocator.getSeriesDao().count()));
        vb.authorCount.setText(String.valueOf(serviceLocator.getAuthorDao().count()));
        vb.publisherCount.setText(String.valueOf(serviceLocator.getPublisherDao().count()));
    }
}
