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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookBaseFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.RequestCode;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;

/**
 * Handler for a displayed Cover ImageView element.
 * Offers context menus and all operations applicable on a Cover image.
 * <p>
 * The fragment using this must implement {@link HostingFragment} if it hosts more than 1
 * instance of this class. See {@link #onContextItemSelected}.
 * It informs the fragment of the cover image index to use in its
 * {@link Fragment#onActivityResult(int, int, Intent)}
 */
public class CoverHandler {

    /** Log tag. */
    private static final String TAG = "CoverHandler";

    /** FragmentResultListener request key. Append the mCIdx value! */
    private static final String RK_MENU_PICKER = MenuPickerDialogFragment.TAG + ":rk:";

    /**
     * After taking a picture, do nothing. Never change the value.
     * This is stored in user preferences.
     */
    private static final int ACTION_DONE = 0;
    /** After taking a picture, crop. */
    private static final int ACTION_CROP = 1;
    /** After taking a picture, edit. */
    private static final int ACTION_EDIT = 2;

    private static final String IMAGE_MIME_TYPE = "image/*";
    /** The file name we'll use with the external editor/crop. */
    private final String TEMP_COVER_FILENAME;

    /** The fragment hosting us. Should implement HostingFragment. */
    @NonNull
    private final Fragment mFragment;
    @NonNull
    private final ImageView mCoverView;
    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 1)
    private final int mCIdx;
    /** Context from the fragment, cached as used frequently. */
    @NonNull
    private final Context mContext;

    /**
     * keep a reference to the ISBN Field, so we can use the *current* value
     * when we're in the book edit fragment.
     */
    @NonNull
    private final TextView mIsbnView;
    @Nullable
    private final ProgressBar mProgressBar;
    private final int mMaxWidth;
    private final int mMaxHeight;
    private final TransFormTaskViewModel mTransFormTaskViewModel;
    @NonNull
    private final BookViewModel mBookViewModel;
    /** Used to display a hint if user rotates a camera image. */
    private boolean mShowHintAboutRotating;
    @Nullable
    private CameraHelper mCameraHelper;

    /**
     * Constructor.
     *
     * <strong>Why isbnView</strong>: using the view will allow this code to work
     * with both read-only screens (passing the isbn itself would be enough)
     * AND with edit-screens (need the CURRENT code).
     *
     * @param fragment      the hosting fragment, should implement {@link HostingFragment}
     * @param bookViewModel access to the book
     * @param cIdx          0..n image index
     * @param isbnView      the view to read the *current* ISBN from
     * @param coverView     the views to populate
     * @param maxHeight     the maximum height (==width) for the cover
     * @param progressBar   (optional) a progress bar
     */
    public CoverHandler(@NonNull final Fragment fragment,
                        @NonNull final BookViewModel bookViewModel,
                        @IntRange(from = 0, to = 1) final int cIdx,
                        @NonNull final TextView isbnView,
                        @NonNull final ImageView coverView,
                        final int maxHeight,
                        @Nullable final ProgressBar progressBar) {

        TEMP_COVER_FILENAME = TAG + "_" + cIdx + ".jpg";

        mFragment = fragment;
        mContext = mFragment.requireContext();

        mBookViewModel = bookViewModel;

        mProgressBar = progressBar;
        mIsbnView = isbnView;
        mCIdx = cIdx;
        mCoverView = coverView;
        // we use a square, and adjust when we get the image/placeholder sizes figured out
        //noinspection SuspiciousNameCombination
        mMaxWidth = maxHeight;
        mMaxHeight = maxHeight;

        // Allow zooming by clicking on the image;
        mCoverView.setOnClickListener(v -> {
            final File uuidCoverFile = mBookViewModel.getCoverFile(mContext, mCIdx);
            if (uuidCoverFile != null) {
                ZoomedImageDialogFragment
                        .newInstance(uuidCoverFile)
                        .show(fragment.getChildFragmentManager(), ZoomedImageDialogFragment.TAG);
            }
            //else {
            // If there is no actual image, bring up the context menu instead.
            // 2020-08-03: disabled, since making the placeholder bigger it
            // was to easy to accidentally trigger this
            //onCreateContextMenu();
            //}
        });

        mCoverView.setOnLongClickListener(v -> {
            onCreateContextMenu();
            return true;
        });

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mFragment.getChildFragmentManager().setFragmentResultListener(
                    RK_MENU_PICKER + mCIdx, mFragment,
                    (MenuPickerDialogFragment.OnResultListener) this::onContextItemSelected);
        }

        mTransFormTaskViewModel = new ViewModelProvider(fragment)
                .get(String.valueOf(cIdx), TransFormTaskViewModel.class);
        mTransFormTaskViewModel.onFinished().observe(mFragment.getViewLifecycleOwner(), event -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "mTransFormTaskViewModel.onFinished()"
                           + "|event=" + event);
            }
            showProgress(false);
            if (event.isNewEvent()) {
                Objects.requireNonNull(event.result, FinishedMessage.MISSING_TASK_RESULTS);
                onAfterTransform(event.result);
            }
        });

        // finally load the image.
        final File uuidCoverFile = mBookViewModel.getCoverFile(mContext, mCIdx);
        if (uuidCoverFile != null) {
            new ImageLoader(mCoverView, uuidCoverFile, mMaxWidth, mMaxHeight, null)
                    .execute();
            mCoverView.setBackground(null);

        } else {
            setPlaceholder();
        }
    }

    /**
     * Context menu for the image.
     */
    private void onCreateContextMenu() {
        Menu menu = MenuPicker.createMenu(mContext);
        new MenuInflater(mContext).inflate(R.menu.image, menu);

        final String title;
        final File uuidCoverFile = mBookViewModel.getCoverFile(mContext, mCIdx);
        if (uuidCoverFile != null) {
            if (BuildConfig.DEBUG /* always */) {
                // show the size of the image in the title bar
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(uuidCoverFile.getAbsolutePath(), opts);
                title = "" + opts.outWidth + "x" + opts.outHeight;
            } else {
                title = mContext.getString(R.string.lbl_cover_long);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = menu.findItem(R.id.SUBMENU_THUMB_REPLACE);
            menu = menuItem.getSubMenu();
            title = menuItem.getTitle().toString();
        }

        // we only support alternative edition covers for the front cover.
        menu.findItem(R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS).setVisible(mCIdx == 0);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            MenuPickerDialogFragment
                    .newInstance(RK_MENU_PICKER + mCIdx, title, menu, mCIdx)
                    .show(mFragment.getChildFragmentManager(), MenuPickerDialogFragment.TAG);
        } else {
            new MenuPicker(mContext, title, menu, mCIdx, this::onContextItemSelected)
                    .show();
        }
    }

    private void showProgress(final boolean show) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list (i.e. mCIdx)
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {

        if (mFragment instanceof HostingFragment) {
            ((HostingFragment) mFragment).setCurrentCoverIndex(mCIdx);
        }

        switch (menuItem) {
            case R.id.MENU_DELETE: {
                mBookViewModel.setCover(mContext, mCIdx, null);
                setPlaceholder();
                return true;
            }
            case R.id.SUBMENU_THUMB_ROTATE: {
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mShowHintAboutRotating) {
                    TipManager.display(mContext, R.string.tip_autorotate_camera_images, null);
                    mShowHintAboutRotating = false;
                }
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CW: {
                showProgress(true);
                startRotation(90);
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CCW: {
                showProgress(true);
                startRotation(-90);
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_180: {
                showProgress(true);
                startRotation(180);
                return true;
            }
            case R.id.MENU_THUMB_CROP: {
                final File srcFile;
                try {
                    srcFile = mBookViewModel.createTempCoverFile(mContext, mCIdx);
                } catch (@NonNull final IOException e) {
                    Snackbar.make(mCoverView, R.string.error_storage_not_writable,
                                  Snackbar.LENGTH_LONG).show();
                    return true;
                }
                startCropper(srcFile);
                return true;
            }
            case R.id.MENU_EDIT: {
                final File srcFile;
                try {
                    srcFile = mBookViewModel.createTempCoverFile(mContext, mCIdx);
                } catch (@NonNull final IOException e) {
                    Snackbar.make(mCoverView, R.string.error_storage_not_writable,
                                  Snackbar.LENGTH_LONG).show();
                    return true;
                }
                startEditor(srcFile);
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_CAMERA: {
                startCamera();
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_FILE_SYSTEM: {
                startChooser();
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS: {
                startCoverBrowser();
                return true;
            }
            default: {
                return false;
            }
        }
    }


    /**
     * Crop the image using our internal code in {@link CropImageActivity}.
     * The user can choose the use an external crop tool by using the external editor.
     *
     * @param srcFile to crop; the file will not be modified
     */
    private void startCropper(@NonNull final File srcFile) {
        // use the fixed name destination.
        final File dstFile = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);
        FileUtils.delete(dstFile);

        final Intent intent = new Intent(mContext, CropImageActivity.class)
                .putExtra(CropImageActivity.BKEY_SOURCE, srcFile.getAbsolutePath())
                .putExtra(CropImageActivity.BKEY_DESTINATION, dstFile.getAbsolutePath());

        mFragment.startActivityForResult(intent, RequestCode.CROP_IMAGE);
    }

    /**
     * Rotate the image by the given angle.
     *
     * @param angle to rotate.
     */
    private void startRotation(final int angle) {
        final File srcFile;
        try {
            srcFile = mBookViewModel.createTempCoverFile(mContext, mCIdx);
        } catch (@NonNull final IOException e) {
            Snackbar.make(mCoverView, R.string.error_storage_not_writable,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        showProgress(true);
        mTransFormTaskViewModel.startTask(
                new TransFormTaskViewModel.Transformation(srcFile)
                        .setRotate(angle));
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from an external app.
     */
    private void startChooser() {
        // use the fixed name destination.
        final File dstFile = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);
        FileUtils.delete(dstFile);

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType(IMAGE_MIME_TYPE);
        mFragment.startActivityForResult(
                Intent.createChooser(intent, mContext.getString(R.string.lbl_select_image)),
                RequestCode.ACTION_GET_CONTENT);
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     * <p>
     * The results comes back in {@link #onFileSelected(int, String)}
     */
    private void startCoverBrowser() {
        final String isbnStr = mIsbnView.getText().toString();
        if (!isbnStr.isEmpty()) {
            final ISBN isbn = ISBN.createISBN(isbnStr);
            if (isbn.isValid(true)) {
                CoverBrowserDialogFragment
                        .newInstance(BookBaseFragment.RK_COVER_BROWSER, isbn.asText(), mCIdx)
                        .show(mFragment.getChildFragmentManager(), CoverBrowserDialogFragment.TAG);
                return;
            }
        }

        Snackbar.make(mCoverView, R.string.warning_requires_isbn,
                      Snackbar.LENGTH_LONG).show();
    }

    private void startCamera() {
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
        }
        mCameraHelper.startCamera(mFragment, RequestCode.ACTION_IMAGE_CAPTURE);
    }

    /**
     * Edit the image using an external application.
     *
     * @param srcFile to use
     */
    private void startEditor(@NonNull final File srcFile) {
        // use the fixed name destination.
        final File dstFile = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);
        FileUtils.delete(dstFile);

        //TODO: we really should revoke the permissions afterwards
        final Uri srcUri = GenericFileProvider.getUriForFile(mContext, srcFile);
        final Uri dstUri = GenericFileProvider.getUriForFile(mContext, dstFile);

        final Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(srcUri, IMAGE_MIME_TYPE)
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // write access see below
                .putExtra(MediaStore.EXTRA_OUTPUT, dstUri);

        final List<ResolveInfo> resInfoList =
                mContext.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!resInfoList.isEmpty()) {
            // We do not know which app will be used, so need to grant permission to all.
            for (ResolveInfo resolveInfo : resInfoList) {
                final String packageName = resolveInfo.activityInfo.packageName;
                mContext.grantUriPermission(packageName, dstUri,
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            mFragment.startActivityForResult(
                    Intent.createChooser(intent, mContext.getString(R.string.action_edit)),
                    RequestCode.EDIT_IMAGE);

        } else {
            Snackbar.make(mCoverView, mContext.getString(R.string.error_no_image_editor),
                          Snackbar.LENGTH_LONG).show();
        }
    }


    /**
     * Handles results from Camera, Picture Gallery and editing (incl. internal cropper).
     *
     * @return {@code true} when handled, {@code false} if unknown requestCode
     */
    public boolean onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {

        switch (requestCode) {
            case RequestCode.ACTION_GET_CONTENT:
            case RequestCode.CROP_IMAGE: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, "data");
                    final Uri uri = data.getData();
                    if (uri != null) {
                        File file = null;
                        try (InputStream is = mContext.getContentResolver().openInputStream(uri)) {
                            file = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);
                            // copy the data, and retrieve the (potentially) resolved file
                            file = FileUtils.copyInputStream(mContext, is, file);
                        } catch (@NonNull final IOException e) {
                            if (BuildConfig.DEBUG /* always */) {
                                Log.d(TAG, "Unable to copy content to file", e);
                            }
                        }

                        if (file != null) {
                            showProgress(true);
                            mTransFormTaskViewModel.startTask(
                                    new TransFormTaskViewModel.Transformation(file)
                                            .setScale(true));
                            return true;
                        }
                    }
                    StandardDialogs.showError(mContext, R.string.warning_image_copy_failed);
                }
                return true;
            }
            case RequestCode.ACTION_IMAGE_CAPTURE: {
                Objects.requireNonNull(mCameraHelper, "mCameraHelper");
                if (resultCode == Activity.RESULT_OK) {
                    final File srcFile = mCameraHelper.getFile(mContext);
                    if (srcFile != null && srcFile.exists()) {
                        final SharedPreferences prefs = PreferenceManager
                                .getDefaultSharedPreferences(mContext);
                        // Should we apply an explicit rotation angle?
                        // (which would overrule the setWindowManager call)
                        final int angle = Prefs
                                .getIntListPref(prefs, Prefs.pk_camera_image_autorotate, 0);

                        // What action (if any) should we take after we're done?
                        @NextAction
                        final int action = Prefs
                                .getIntListPref(prefs, Prefs.pk_camera_image_action, ACTION_DONE);

                        showProgress(true);
                        //noinspection ConstantConditions
                        mTransFormTaskViewModel.startTask(
                                new TransFormTaskViewModel.Transformation(srcFile)
                                        .setScale(true)
                                        // we'll try to guess a rotation angle
                                        .setWindowManager(
                                                mFragment.getActivity().getWindowManager())
                                        // or apply an explicit angle
                                        .setRotate(angle)
                                        .setReturnCode(action));
                        return true;
                    }
                }
                // remove orphan
                mCameraHelper.cleanup(mContext);
                return true;
            }
            case RequestCode.EDIT_IMAGE: {
                if (resultCode == Activity.RESULT_OK) {
                    final File file = AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME);
                    showProgress(true);
                    mTransFormTaskViewModel.startTask(
                            new TransFormTaskViewModel.Transformation(file)
                                    .setScale(true));
                    return true;
                }
                // remove any orphaned file
                FileUtils.delete(AppDir.Cache.getFile(mContext, TEMP_COVER_FILENAME));
                return true;
            }
            default:
                return false;
        }
    }

    private void onAfterTransform(@NonNull final TransFormTaskViewModel.TransformedData result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "onAfterTransform"
                       + "|returnCode=" + result.getReturnCode()
                       + "|bitmap=" + result.getBitmap()
                       + "|file=" + result.getFile().getAbsolutePath());
        }

        // The bitmap != null decides if the operation was successful.
        if (result.getBitmap() != null) {
            // sanity check: if the bitmap was good, the file will be good.
            Objects.requireNonNull(result.getFile(), "file");

            switch (result.getReturnCode()) {
                case ACTION_CROP:
                    startCropper(result.getFile());
                    break;

                case ACTION_EDIT:
                    startEditor(result.getFile());
                    break;

                case ACTION_DONE:
                default:
                    mBookViewModel.setCover(mContext, mCIdx, result.getFile());
                    // must use a post to force the View to update.
                    mCoverView.post(() -> {
                        ImageUtils.setImageView(mCoverView, mMaxWidth, mMaxHeight,
                                                result.getBitmap(), 0);
                        mCoverView.setBackground(null);
                    });
                    break;
            }

        } else {
            // transformation failed
            mBookViewModel.setCover(mContext, mCIdx, null);
            // must use a post to force the View to update.
            mCoverView.post(() -> setPlaceholder(result.getFile()));
        }
    }

    /**
     * Called when the user clicks the large preview in the {@link CoverBrowserDialogFragment}.
     *
     * @param cIdx     0..n image index
     * @param fileSpec the selected image
     */
    public void onFileSelected(@IntRange(from = 0, to = 1) final int cIdx,
                               @NonNull final String fileSpec) {
        if (cIdx != mCIdx) {
            throw new IllegalStateException("cIdx=" + cIdx + "|mCIdx=" + mCIdx);
        }
        SanityCheck.requireValue(fileSpec, "fileSpec");

        File srcFile = new File(fileSpec);
        if (srcFile.exists()) {
            srcFile = mBookViewModel.setCover(mContext, cIdx, srcFile);
            if (srcFile != null) {
                new ImageLoader(mCoverView, srcFile, mMaxWidth, mMaxHeight, null)
                        .execute();
                mCoverView.setBackground(null);
                return;
            }
        }

        mBookViewModel.setCover(mContext, cIdx, null);
        setPlaceholder();
    }


    /**
     * Put a placeholder on screen.
     *
     * @param file to determine the type of placeholder to use
     */
    private void setPlaceholder(@Nullable final File file) {
        if (file == null || file.length() == 0) {
            setPlaceholder();
        } else {
            ImageUtils.setPlaceholder(mCoverView, R.drawable.ic_broken_image,
                                      R.drawable.outline_rounded,
                                      (int) (mMaxWidth * ImageUtils.HW_RATIO),
                                      mMaxHeight);
        }
    }

    /**
     * Put a standard placeholder.
     */
    private void setPlaceholder() {
        ImageUtils.setPlaceholder(mCoverView, R.drawable.ic_add_a_photo,
                                  R.drawable.outline_rounded,
                                  (int) (mMaxWidth * ImageUtils.HW_RATIO),
                                  mMaxHeight);
    }

    public interface HostingFragment {

        /**
         * Prepare the fragment for results.
         * This is needed as for example when we start the camera, we can't tell afterwards
         * which index we're handling. So let the fragment know BEFORE.
         *
         * @param cIdx 0..n image index
         */
        void setCurrentCoverIndex(@IntRange(from = 0, to = 1) int cIdx);
    }

    @IntDef({ACTION_DONE, ACTION_CROP, ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    @interface NextAction {

    }
}
