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
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

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
    private static final String TAG = "ZoomedImageDialogFragment";
    private static final String BKEY_IMAGE_PATH = TAG + ":path";

    /** File to display. */
    private File mImageFile;

    private ImageView mImageView;

    /**
     * Syntax sugar for newInstance.
     *
     * @param fm    FragmentManager
     * @param image to display, must be valid.
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final File image) {
        newInstance(image).show(fm, TAG);
    }

    /**
     * Constructor.
     *
     * @param image to display
     *
     * @return the instance
     */
    private static ZoomedImageDialogFragment newInstance(@NonNull final File image) {
        ZoomedImageDialogFragment frag = new ZoomedImageDialogFragment();
        Bundle args = new Bundle(1);
        args.putString(BKEY_IMAGE_PATH, image.getPath());
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        String fileSpec = args.getString(BKEY_IMAGE_PATH);
        Objects.requireNonNull(fileSpec, ErrorMsg.ARGS_MISSING_IMAGE_PATH);
        mImageFile = new File(fileSpec);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_zoomed_image, container);
        mImageView = root.findViewById(R.id.coverImage0);
        mImageView.setOnClickListener(v -> dismiss());
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        // It's quite possible there are better ways of doing the below.
        // The intention is:
        // - make the image as big as possible as compared to screen size/orientation.
        // - have the dialog window wrap around the image.
        // - assume 10% of the screen used for Dialog padding

        //noinspection ConstantConditions
        final Window window = getDialog().getWindow();
        //noinspection ConstantConditions
        final Resources resources = getContext().getResources();
        final Configuration configuration = resources.getConfiguration();

        // not ideal, but it works: use 90% of the available space for our image.
        final float density = resources.getDisplayMetrics().density;
        int w = (int) (configuration.screenWidthDp * density * 0.9f);
        int h = (int) (configuration.screenHeightDp * density * 0.9f);
        int dx;
        int dy;
        switch (configuration.orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                dx = w;
                dy = h;
                //noinspection ConstantConditions
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                                 ViewGroup.LayoutParams.WRAP_CONTENT);
                break;

            case Configuration.ORIENTATION_LANDSCAPE:
                dx = h;
                dy = w;
                //noinspection ConstantConditions
                window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                                 ViewGroup.LayoutParams.MATCH_PARENT);
                break;

            default:
                // according to the Configuration class docs, there *might* be other values.
                // x/y as for portrait, but window layout on the safe side.
                dx = w;
                dy = h;
                //noinspection ConstantConditions
                window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,
                                 ViewGroup.LayoutParams.WRAP_CONTENT);
                break;
        }

        // load and resize as needed.
        new ImageUtils.ImageLoader(mImageView, mImageFile, dx, dy, true)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
