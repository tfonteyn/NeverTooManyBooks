/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.io.File;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageLoader;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

/**
 * Wrapper for the zoomed image dialog.
 */
public class ZoomedImageDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "ZoomedImageDialogFrag";
    private static final String BKEY_IMAGE_PATH = TAG + ":path";

    /** File to display. */
    private File mImageFile;

    private ImageView mImageView;

    /**
     * Constructor.
     *
     * @param image to display
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final File image) {
        final DialogFragment frag = new ZoomedImageDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(BKEY_IMAGE_PATH, image.getPath());
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        final String fileSpec = args.getString(BKEY_IMAGE_PATH);
        Objects.requireNonNull(fileSpec, ErrorMsg.NULL_IMAGE_PATH);
        mImageFile = new File(fileSpec);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.dialog_zoomed_image, container);
        mImageView = root.findViewById(R.id.coverImage0);
        mImageView.setOnClickListener(v -> dismiss());
        return root;
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

        final Resources resources = getResources();
        final Configuration configuration = resources.getConfiguration();
        final DisplayMetrics metrics = resources.getDisplayMetrics();

        final double screenHwRatio = ((float) configuration.screenHeightDp)
                                     / ((float) configuration.screenWidthDp);

        // screen space we use is depending on the screen size...
        // or we end up with pixelated overblown images.
        final int percentage = resources.getInteger(R.integer.cover_zoom_screen_percentage);
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

        //noinspection ConstantConditions
        getDialog().getWindow().setLayout(maxWidth, maxHeight);
        getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // load and resize as needed.
        new ImageLoader(mImageView, mImageFile, maxWidth, maxHeight, null)
                .execute();
    }
}
