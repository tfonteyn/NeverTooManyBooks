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
import android.os.AsyncTask;
import android.os.Bundle;
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
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

/**
 * Wrapper for the zoomed image dialog.
 */
public class ZoomedImageDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "ZoomedImageDialogFragment";
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
        Objects.requireNonNull(fileSpec, ErrorMsg.ARGS_MISSING_IMAGE_PATH);
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

        //noinspection ConstantConditions
        final Resources resources = getContext().getResources();
        final Configuration configuration = resources.getConfiguration();
        final float density = resources.getDisplayMetrics().density;

        final int width;
        final int height;

        if (resources.getBoolean(R.bool.isLargeScreen)) {
            // Pixel2: w411dp h659dp
            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                width = (int) (420 * density);
                height = (int) (660 * density);
            } else {
                width = (int) (660 * density);
                height = (int) (420 * density);
            }

        } else {
            // Use 95% of the available space for our image.
            width = (int) (configuration.screenWidthDp * density * 0.9f);
            height = (int) (configuration.screenHeightDp * density * 0.9f);
        }
        //noinspection ConstantConditions
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                                          ViewGroup.LayoutParams.WRAP_CONTENT);

        // load and resize as needed.
        new ImageUtils.ImageLoader(mImageView, mImageFile, width, height, null)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
