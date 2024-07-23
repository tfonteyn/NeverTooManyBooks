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

package com.hardbacknutter.nevertoomanybooks.covers;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CropImageContract;
import com.hardbacknutter.nevertoomanybooks.core.storage.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.Side;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImageEditorBinding;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * A minimalist editor for the cover images.
 * Limited to cropping for now, but the intention is to incorporate the rotating
 * features implemented elsewhere.
 * <p>
 * Depends on / works in conjunction with {@link CropImageView}.
 * <p>
 * FIXME: rotating the device will revert the image to the original
 */
public class CropImageFragment
        extends BaseFragment {

    private static final String TAG = "CropImageFragment";

    /** used to calculate free space on Shared Storage, 100kb per picture is an overestimation. */
    private static final long ESTIMATED_PICTURE_SIZE = 100_000L;

    /** A back-press is always a "cancel". */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    getActivity().finish();
                }
            };
    private FragmentImageEditorBinding vb;
    // do NOT delete the destination file in case source and destination was the same file
    private String destinationPath;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentImageEditorBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            final File coverDir = ServiceLocator.getInstance().getCoverStorage().getDir();
            // make an educated guess how many pics we can store.
            if (FileUtils.getFreeSpace(coverDir) / ESTIMATED_PICTURE_SIZE < 1) {
                // Shouldn't we 'finish()' the activity? i.e. handle like an exception?
                Snackbar.make(vb.coverImage0, R.string.error_insufficient_storage,
                              Snackbar.LENGTH_LONG).show();
            }

        } catch (@NonNull final CoverStorageException | IOException e) {
            // just log, do not display exception data
            LoggerFactory.getLogger().e(TAG, e);
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.error_24px)
                    .setTitle(R.string.error_storage_not_accessible)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        //noinspection DataFlowIssue
                        getActivity().finish();
                    })
                    .create()
                    .show();
            return;
        }

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Bundle args = requireArguments();

        final String srcPath = Objects.requireNonNull(args.getString(
                CropImageContract.BKEY_SOURCE), CropImageContract.BKEY_SOURCE);
        final Bitmap bitmap = getBitmap(srcPath);

        if (bitmap != null) {
            destinationPath = Objects.requireNonNull(args.getString(
                    CropImageContract.BKEY_DESTINATION), CropImageContract.BKEY_DESTINATION);

            // do NOT set a listener on the vb.bottomAppBar/vb.fab
            // The former does that automatically, and the latter is anchored to the bar.
            InsetsListenerBuilder.create()
                                 .margins()
                                 .sides(Side.All)
                                 .applyTo(vb.coverImage0);

            vb.coverImage0.setInitialBitmap(bitmap);

            // the FAB button saves the image
            vb.fab.setOnClickListener(v -> onSave());

            // FIXME: 2024-07-14: if the device is displaying a 3-button soft-nav-bar,
            //  we'll have two 'back' buttons just above each other. ...
            //  I tried to detect a) visibility of navbar; b) if gesture-nav is enable
            //  using various unsubstantiated/dated posts on the web...
            //  none of them worked. So we have two buttons.... better than none I suppose.
            // Back is cancel
            vb.bottomAppBar.setNavigationOnClickListener(v -> getActivity().finish());
            // Reset/undo but stay here editing
            vb.bottomAppBar.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.MENU_UNDO) {
                    vb.coverImage0.resetBitmap();
                    return true;
                }
                return false;
            });
        } else {
            getActivity().finish();
        }
    }

    @Nullable
    private Bitmap getBitmap(@NonNull final String srcPath) {
        Bitmap bitmap = null;
        try (InputStream is = new FileInputStream(srcPath)) {
            bitmap = BitmapFactory.decodeStream(is);
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().d(TAG, "getBitmap", e);
            }
        }
        return bitmap;
    }

    private void onSave() {
        // prevent multiple saves (cropping the bitmap might take some time)
        vb.fab.setEnabled(false);

        @Nullable
        Bitmap bitmap = vb.coverImage0.getCroppedBitmap();
        if (bitmap != null) {
            try {
                final File destination = new File(destinationPath);
                ServiceLocator.getInstance().getCoverStorage().persist(bitmap, destination);

                final Intent resultIntent = CropImageContract
                        .createResult(destinationPath);
                //noinspection DataFlowIssue
                getActivity().setResult(Activity.RESULT_OK, resultIntent);
                getActivity().finish();

            } catch (@NonNull final IOException | CoverStorageException e) {
                LoggerFactory.getLogger().e(TAG, e);
                bitmap = null;
            }
        }

        if (bitmap == null) {
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.error_24px)
                    .setTitle(R.string.action_save)
                    .setMessage(R.string.error_storage_not_writable)
                    .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                    .create()
                    .show();
        }
    }
}
