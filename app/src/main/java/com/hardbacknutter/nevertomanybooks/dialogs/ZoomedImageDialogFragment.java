/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.dialogs;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.Objects;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.ImageUtils;

/**
 * Wrapper for the zoomed image dialog.
 */
public class ZoomedImageDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    private static final String TAG = "ZoomedImageDialogFragment";

    /** File to display. */
    private File mImageFile;

    /**
     * Syntax sugar for newInstance.
     *
     * @param fm    FragmentManager
     * @param image to display
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final File image) {
        // if there is no file, just silently ignore.
        if (image.exists()) {
            if (fm.findFragmentByTag(TAG) == null) {
                newInstance(image).show(fm, TAG);
            }
        }
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
        Bundle args = new Bundle();
        args.putString(UniqueId.BKEY_FILE_SPEC, image.getPath());
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mImageFile = new File(Objects.requireNonNull(args.getString(UniqueId.BKEY_FILE_SPEC)));
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_zoomed_image, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {

        boolean upscale = PreferenceManager.getDefaultSharedPreferences(view.getContext())
                                           .getBoolean(Prefs.pk_thumbnails_zoom_upscale, true);

        DisplayMetrics metrics = getResources().getDisplayMetrics();

        ImageView imageView = view.findViewById(R.id.coverImage);
        ImageUtils.setImageView(imageView, mImageFile,
                                metrics.widthPixels,
                                metrics.heightPixels,
                                upscale);

        imageView.setOnClickListener(v -> dismiss());
    }
}
