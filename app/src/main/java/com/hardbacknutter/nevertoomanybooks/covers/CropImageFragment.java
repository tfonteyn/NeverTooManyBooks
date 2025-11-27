/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImageEditorBinding;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;
import com.hardbacknutter.util.insets.Side;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * A minimalist cropping-editor for the cover images.
 * <p>
 * Depends on / works in conjunction with {@link CropImageView}.
 * <p>
 * FIXME: rotating the device will revert the image to the original
 *  A fix will require the custom view to be able to preserve state of cropping/bitmap.
 */
public class CropImageFragment
        extends BaseFragment {

    private static final String TAG = "CropImageFragment";

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

    private CropImageViewModel vm;

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

        // do NOT set a listener on vb.bottomAppBar/vb.fab
        // The AppBar does that automatically, and the FAB is anchored to that bar.
        InsetsListenerBuilder.create(vb.coverImage0)
                             .margins(Side.Start, Side.Top, Side.End, Side.Bottom)
                             .systemBars()
                             .displayCutout()
                             .systemGestures()
                             .apply();

        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(viewLifecycleOwner, backPressedCallback);

        vm = new ViewModelProvider(this).get(CropImageViewModel.class);
        vm.init(requireArguments());

        // no storage space left, quit
        vm.onInsufficientStorage().observe(viewLifecycleOwner, message ->
                message.process(aVoid -> showMessageAndFinishActivity(
                        getString(R.string.error_insufficient_storage))));

        vm.onError().observe(viewLifecycleOwner, message ->
                message.process(this::onError));

        // Load the initial bitmap
        vm.onBitmap().observe(viewLifecycleOwner, message ->
                message.process(bitmap -> vb.coverImage0.setInitialBitmap(bitmap)));

        // After a successful save
        vm.onSaved().observe(viewLifecycleOwner, message ->
                message.process(resultIntent -> {
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }));

        initListeners();
    }

    private void initListeners() {
        // the FAB button saves the image
        vb.fab.setOnClickListener(v -> onSave());

        // FIXME: 2024-07-14: if the device is displaying a 3-button soft-nav-bar,
        //  we'll have two 'back' buttons just above each other. ...
        //  I tried to detect a) visibility of navbar; b) if gesture-nav is enable
        //  using various unsubstantiated/dated posts on the web...
        //  none of them worked. So we have two buttons.... better than none I suppose.
        // Back is cancel
        //noinspection DataFlowIssue
        vb.bottomAppBar.setNavigationOnClickListener(v -> getActivity().finish());

        // Reset/undo
        vb.bottomAppBar.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.MENU_UNDO) {
                vb.coverImage0.resetBitmap();
                return true;
            }
            return false;
        });
    }

    private void onSave() {
        // prevent multiple saves (cropping the bitmap might take some time)
        vb.fab.setEnabled(false);

        @Nullable
        final Bitmap bitmap = vb.coverImage0.getCroppedBitmap();
        if (bitmap == null) {
            onError(null);
        } else {
            vm.save(bitmap);
        }
    }

    @UiThread
    private void onError(@Nullable final Throwable e) {
        if (e != null) {
            LoggerFactory.getLogger().e(TAG, e);
        }

        // Getting the cover-dir and calculating free-space failed: unlikely
        // Loading the initial bitmap failed: unlikely
        // "Save" failed. This probable the only time we'll get here... flw
        // ... so the generic message is good enough.

        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.error_24px)
                .setMessage(R.string.error_storage_not_writable)
                .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                .create()
                .show();
    }
}
