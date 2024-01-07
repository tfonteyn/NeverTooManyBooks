/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.covers.ImageViewLoader;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

/**
 * Wrapper for the zoomed image dialog.
 */
public class ZoomedImageDialogFragment
        extends DialogFragment {

    /** Log tag. */
    private static final String TAG = "ZoomedImageDialogFrag";
    private static final String BKEY_IMAGE_PATH = TAG + ":path";

    /** File to display. */
    private File imageFile;

    private ImageView imageView;

    /**
     * No-arg constructor for OS use.
     */
    public ZoomedImageDialogFragment() {
        super(R.layout.dialog_zoomed_image);
    }

    /**
     * Constructor.
     *
     * @param fm    The FragmentManager this fragment will be added to.
     * @param image to display
     */
    public static void launch(@NonNull final FragmentManager fm,
                              @NonNull final File image) {

        final Bundle args = new Bundle(1);
        args.putString(BKEY_IMAGE_PATH, image.getPath());

        final DialogFragment frag = new ZoomedImageDialogFragment();
        frag.setArguments(args);
        frag.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        final String fileSpec = SanityCheck.requireValue(args.getString(BKEY_IMAGE_PATH),
                                                         BKEY_IMAGE_PATH);
        imageFile = new File(fileSpec);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        imageView = view.findViewById(R.id.cover_image_0);
        imageView.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onResume() {
        super.onResume();

        // IMPORTANT reminder:
        // DisplayMetrics gives us TOTAL screen size
        // Configuration gives us the AVAILABLE size. <== what we need.

        // Example:
        // |metrics.widthPixels=1080|metrics.heightPixels=1920
        // |configuration.screenWidth=1080.0|configuration.screenHeight=1848.0
        // |configuration.screenWidthDp=360|configuration.screenHeightDp=616
        // |screenHwRatio=1.711111068725586
        // |maxWidth=1026|maxHeight=1755

        final Resources res = getResources();
        final Configuration configuration = res.getConfiguration();
        final DisplayMetrics metrics = res.getDisplayMetrics();

        final double screenHwRatio = ((float) configuration.screenHeightDp)
                                     / ((float) configuration.screenWidthDp);

        // Use a percentage of the total screen space, to create a (dimmed) border
        final int percentage = res.getInteger(R.integer.cover_zoom_screen_percentage);
        final float multiplier = metrics.density * ((float) percentage / 100);
        final int maxWidth;
        final int maxHeight;

        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            maxWidth = (int) (multiplier * configuration.screenWidthDp);
            maxHeight = (int) (maxWidth * screenHwRatio);
        } else {
            maxHeight = (int) (multiplier * configuration.screenHeightDp);
            maxWidth = (int) (maxHeight / screenHwRatio);
        }

        //noinspection DataFlowIssue
        getDialog().getWindow().setLayout(maxWidth, maxHeight);
        getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // load and resize as needed.
        new ImageViewLoader(ASyncExecutor.MAIN,
                            ImageView.ScaleType.FIT_CENTER,
                            ImageViewLoader.MaxSize.Unlimited,
                            maxWidth, maxHeight)
                .fromFile(imageView, imageFile, null);
    }
}
