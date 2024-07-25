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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Dimension;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.Side;
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
    private static final int INSETS_TYPE_MASK = WindowInsetsCompat.Type.statusBars()
                                                | WindowInsetsCompat.Type.displayCutout();

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
        InsetsListenerBuilder.create(view)
                             .margins(Side.Left, Side.Top, Side.Right, Side.Bottom)
                             .systemBars()
                             .displayCutout()
                             .systemGestures()
                             .apply();

        imageView = view.findViewById(R.id.cover_image_0);
        imageView.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onResume() {
        super.onResume();

        @Dimension
        final float windowWidthInPx;
        @Dimension
        final float windowHeightInPx;

        //noinspection DataFlowIssue
        final WindowMetrics windowMetrics = WindowMetricsCalculator
                .getOrCreate()
                .computeCurrentWindowMetrics(getContext());
        final Rect bounds = windowMetrics.getBounds();

        // The WindowMetricsCalculator only provides insets when API >= 30
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final Insets insets = windowMetrics.getWindowInsets().getInsets(INSETS_TYPE_MASK);
            windowWidthInPx = bounds.width() - (insets.left + insets.right);
            windowHeightInPx = bounds.height() - (insets.top + insets.bottom);
        } else {
            windowWidthInPx = bounds.width();
            windowHeightInPx = bounds.height();
        }

        final double screenHwRatio = windowHeightInPx / windowWidthInPx;

        final Resources res = getResources();

        // Use a percentage of the total screen space, to create a (dimmed) border
        @IntRange(from = 0, to = 100)
        final int percentage = res.getInteger(R.integer.cover_zoom_screen_percentage);
        final float multiplier = (float) percentage / 100;
        @Dimension
        final int maxWidth;
        @Dimension
        final int maxHeight;

        // Depending on screen orientation, use one dimension as a fixed value,
        // and calculate the other one based on the screen height/width ratio.
        if (res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            maxWidth = (int) (multiplier * windowWidthInPx);
            maxHeight = (int) (maxWidth * screenHwRatio);
        } else {
            maxHeight = (int) (multiplier * windowHeightInPx);
            maxWidth = (int) (maxHeight / screenHwRatio);
        }

        //noinspection DataFlowIssue
        getDialog().getWindow().setLayout(maxWidth, maxHeight);
        getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // load and resize as needed.
        new ImageViewLoader(ASyncExecutor.MAIN,
                            ImageView.ScaleType.FIT_CENTER,
                            ImageViewLoader.MaxSize.Enforce,
                            maxWidth, maxHeight)
                .fromFile(imageView, imageFile, null, null);
    }
}
