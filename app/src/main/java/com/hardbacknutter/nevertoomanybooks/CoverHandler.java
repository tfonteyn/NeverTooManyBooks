/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.cropper.CropImageActivity;
import com.hardbacknutter.nevertoomanybooks.cropper.CropImageViewTouchBase;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.librarything.LibraryThingManager;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Handler for a displayed Cover ImageView element.
 * Offers context menus and all operations applicable on a Cover image.
 * <p>
 * Layer Type to use for the Cropper.
 * <p>
 * {@link CropImageViewTouchBase} We get 'unsupported feature' crashes if the option
 * to always use GL is turned on.
 * See:
 * <a href="http://developer.android.com/guide/topics/graphics/hardware-accel.html>
 * http://developer.android.com/guide/topics/graphics/hardware-accel.html</a>
 * <a href="http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath">
 * http://stackoverflow.com/questions/13676059/android-unsupportedoperationexception-at-canvas-clippath</a>
 * so for API: level > 11, we turn it off manually.
 * <p>
 * 2018-11-30: making this a configuration option.
 */
public class CoverHandler {

    /** Counter used to prevent images being reused accidentally. */
    private static int sTempImageCounter;

    @NonNull
    private final Fragment mCallerFragment;

    @NonNull
    private final FragmentManager mFragmentManager;

    @NonNull
    private final Context mContext;

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final Book mBook;
    private final ImageView mCoverView;

    /**
     * keep a reference to the ISBN Field, so we can use the *current* value
     * when we're in the book edit fragment.
     */
    private final TextView mIsbnView;

    private final int mMaxWidth;
    private final int mMaxHeight;
    @Nullable
    private CoverBrowserFragment mCoverBrowserFragment;

    /** Used to display a hint if user rotates a camera image. */
    private boolean mGotCameraImage;

    /**
     * Constructor.
     *
     * @param scale image scale factor to apply when populating the coverView.
     */
    CoverHandler(@NonNull final Fragment callerFragment,
                 @NonNull final DAO db,
                 @NonNull final Book book,
                 @NonNull final TextView isbnView,
                 @NonNull final ImageView coverView,
                 final int scale) {

        int maxSize = ImageUtils.getMaxImageSize(scale);

        mCallerFragment = callerFragment;
        //noinspection ConstantConditions
        mFragmentManager = mCallerFragment.getFragmentManager();

        //noinspection ConstantConditions
        mContext = mCallerFragment.getContext();
        mMaxWidth = maxSize;
        mMaxHeight = maxSize;
        mDb = db;
        mBook = book;
        mCoverView = coverView;
        mIsbnView = isbnView;

        // add context menu to the cover image
        mCoverView.setOnLongClickListener(v -> {
            onCreateContextMenu();
            return true;
        });

        //Allow zooming by clicking on the image
        mCoverView.setOnClickListener(
                v -> ZoomedImageDialogFragment.show(mFragmentManager, getCoverFile()));
    }

    /**
     * When the user clicks the switcher in the {@link CoverBrowserFragment},
     * we take that image and stuff it into the view.
     *
     * @param fileSpec the file
     */
    private void onImageSelected(@NonNull final String fileSpec) {
        if (mCoverBrowserFragment != null) {
            // the new file we got
            File newFile = new File(fileSpec);
            // Get the current file we want to loose
            File bookFile = getCoverFile();
            // copy new file on top of old.
            StorageUtils.renameFile(newFile, bookFile);
            // Update the ImageView with the new image
            ImageUtils.setImageView(mCoverView, getCoverFile(), mMaxWidth, mMaxHeight, true);
            // all done, get rid of the browser fragment
            mCoverBrowserFragment.dismiss();
            mCoverBrowserFragment = null;
        }
    }

    private void getCoverBrowser(@NonNull final String isbn) {
        // we must use the same fragment manager as the hosting fragment.
        mCoverBrowserFragment = (CoverBrowserFragment)
                                        mFragmentManager
                                                .findFragmentByTag(CoverBrowserFragment.TAG);
        if (mCoverBrowserFragment == null) {
            mCoverBrowserFragment = CoverBrowserFragment.newInstance(isbn, SearchSites.SEARCH_ALL);
            mCoverBrowserFragment.show(mFragmentManager, CoverBrowserFragment.TAG);
        }

        mCoverBrowserFragment.setTargetFragment(mCallerFragment, UniqueId.REQ_ALT_EDITION);
    }

    /**
     * Context menu for the image.
     */
    private void onCreateContextMenu() {

        Menu menu = MenuPicker.createMenu(mContext);
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        SubMenu replaceSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_THUMB_REPLACE, 0,
                                                 R.string.menu_cover_replace)
                                     .setIcon(R.drawable.ic_find_replace);

        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_CAMERA, 0,
                           R.string.menu_cover_add_from_camera)
                      .setIcon(R.drawable.ic_add_a_photo);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_GALLERY, 0,
                           R.string.menu_cover_add_from_gallery)
                      .setIcon(R.drawable.ic_photo_gallery);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_ALT_EDITIONS, 0,
                           R.string.menu_cover_search_alt_editions)
                      .setIcon(R.drawable.ic_find_replace);

        SubMenu rotateSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_THUMB_ROTATE, 0,
                                                R.string.menu_cover_rotate)
                                    .setIcon(R.drawable.ic_rotate_right);

        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CW, 0,
                          R.string.menu_cover_rotate_cw)
                     .setIcon(R.drawable.ic_rotate_right);
        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_CCW, 0,
                          R.string.menu_cover_rotate_ccw)
                     .setIcon(R.drawable.ic_rotate_left);
        rotateSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ROTATE_180, 0,
                          R.string.menu_cover_rotate_180)
                     .setIcon(R.drawable.ic_swap_vert);

        menu.add(Menu.NONE, R.id.MENU_THUMB_CROP, 0, R.string.menu_cover_crop)
            .setIcon(R.drawable.ic_crop);

        String title = mContext.getString(R.string.title_cover);
        new MenuPicker<>(LayoutInflater.from(mContext), title, menu,
                         R.id.coverImage, this::onViewContextItemSelected)
                .show();
    }

    /**
     * Using {@link ValuePicker} for context menus.
     * Reminder: the 'menuItem' here *is* the 'item'.
     *
     * @param menuItem that the user selected
     * @param fieldId  the field the menu belongs to
     *
     * @return {@code true} if handled here.
     */
    private boolean onViewContextItemSelected(@NonNull final MenuItem menuItem,
                                              @NonNull final Integer fieldId) {

        // should not happen for now, but nice to remind ourselves we *could* have multiple views.
        if (!fieldId.equals(R.id.coverImage)) {
            return false;
        }

        switch (menuItem.getItemId()) {
            case R.id.MENU_DELETE:
                deleteCoverFile();
                return true;

            case R.id.SUBMENU_THUMB_ROTATE:
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mGotCameraImage) {
                    TipManager.display(LayoutInflater.from(mContext),
                                       R.string.tip_autorotate_camera_images,
                                       null);
                    mGotCameraImage = false;
                }
                return true;

            case R.id.MENU_THUMB_ROTATE_CW:
                rotateImage(90);
                return true;

            case R.id.MENU_THUMB_ROTATE_CCW:
                rotateImage(-90);
                return true;

            case R.id.MENU_THUMB_ROTATE_180:
                rotateImage(180);
                return true;

            case R.id.MENU_THUMB_CROP:
                cropCoverImage(getCoverFile());
                return true;

            case R.id.MENU_THUMB_ADD_FROM_CAMERA:
                getCoverFromCamera();
                return true;

            case R.id.MENU_THUMB_ADD_FROM_GALLERY:
                getCoverFromGallery();
                return true;

            case R.id.MENU_THUMB_ADD_ALT_EDITIONS:
                addCoverFromAlternativeEditions();
                return true;

            default:
                return false;
        }
    }

    /**
     * Get the File object for the cover of the book we are editing.
     * If the book is new (0), return the standard temp file.
     * If the data is a result from a search, then that standard temp file will
     * be the downloaded file.
     */
    @NonNull
    private File getCoverFile() {
        if (mBook.getId() == 0) {
            return StorageUtils.getTempCoverFile();
        }
        return StorageUtils.getCoverFile(getUuid());
    }

    /**
     * We *should* have the uuid on a tag on the over field view,
     * but if not, we'll get it from the database.
     *
     * @return the uuid, or {@code null} if the book has no uuid.
     */
    @NonNull
    private String getUuid() {
        String uuid = (String) mCoverView.getTag(R.id.TAG_UUID);
        // if we forgot to set it in some bad code... log the fact, and make a trip to the db.
        if (uuid == null) {
            Logger.warnWithStackTrace(this, "getUuid", "UUID was not available on the view");
            uuid = mDb.getBookUuid(mBook.getId());
        }
        return uuid;
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     */
    private void addCoverFromAlternativeEditions() {
        // this is essential, as we only get alternative editions from LibraryThing for now.
        if (!LibraryThingManager.hasKey()) {
            LibraryThingManager.alertRegistrationNeeded(mContext, true, "cover_browser");
            return;
        }

        String isbn = mIsbnView.getText().toString().trim();

        if (ISBN.isValid(isbn)) {
            getCoverBrowser(isbn);
        } else {
            UserMessage.show(mCoverView, R.string.warning_action_requires_isbn);
        }
    }

    /**
     * Start the camera to get an image.
     */
    private void getCoverFromCamera() {
        // Increment the temp counter and cleanup the temp directory
        sTempImageCounter++;
        StorageUtils.purgeTempStorage();
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        /*
        We don't do the next bit of code here because we have no reliable way to rotate a
        large image without producing memory exhaustion.
        Android does not include a file-based image rotation.

        File f = getCameraTempCoverFile();
        StorageUtils.deleteFile(f);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        */
        mCallerFragment.startActivityForResult(intent, UniqueId.REQ_ACTION_IMAGE_CAPTURE);
    }

    /**
     * The camera has captured an image, process it.
     */
    private void addCoverFromCamera(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Bitmap bitmap) {
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(App.getListPreference(Prefs.pk_images_rotate_auto, 0));
            Bitmap result = Bitmap.createBitmap(bitmap, 0, 0,
                                                bitmap.getWidth(), bitmap.getHeight(),
                                                matrix, true);

            File cameraFile = StorageUtils.getTempCoverFile(String.valueOf(sTempImageCounter),
                                                            "_camera");
            // Create a file to copy the image into
            try (OutputStream out = new FileOutputStream(cameraFile.getAbsoluteFile())) {
                result.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
                Logger.error(this, e);
                return;
            }

            cropCoverImage(cameraFile);
            mGotCameraImage = true;

        } else {
            if (BuildConfig.DEBUG  /* always */) {
                Logger.debug(this, "addCoverFromCamera",
                             "camera image empty", "onActivityResult",
                             "requestCode=" + requestCode,
                             "resultCode=" + resultCode);
            }
        }
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from (usually) the Gallery app.
     */
    private void getCoverFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                                .setType("image/*");
        mCallerFragment.startActivityForResult(
                Intent.createChooser(intent, mContext.getString(R.string.title_select_image)),
                UniqueId.REQ_ACTION_GET_CONTENT);
    }

    /**
     * The Intent.ACTION_GET_CONTENT has provided us with an image, process it.
     */
    private void addCoverFromGallery(@NonNull final Intent data) {
        Uri selectedImageUri = data.getData();

        if (selectedImageUri != null) {
            int bytesRead = 0;
            // If no 'content' scheme, then use the content resolver.
            try (InputStream is = mContext.getContentResolver()
                                          .openInputStream(selectedImageUri)) {
                bytesRead = StorageUtils.saveInputStreamToFile(is, getCoverFile());

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
                Logger.error(this, e, "Unable to copy content to file");
            }

            if (bytesRead > 0) {
                // Update the ImageView with the new image
                ImageUtils.setImageView(mCoverView, getCoverFile(), mMaxWidth, mMaxHeight, true);
            } else {
                String msg = mContext.getString(R.string.warning_cover_copy_failed) + ". "
                             + mContext.getString(R.string.error_if_the_problem_persists);
                UserMessage.show(mCoverView, msg);
            }
        } else {
            /* Deal with the case where the chooser returns a {@code null} intent.
             * This seems to happen when the filename is not properly understood
             * by the chooser (e.g. an apostrophe in the file name confuses
             * ES File Explorer in the current version as of 23-Sep-2012. */
            UserMessage.show(mCoverView, R.string.warning_cover_copy_failed);
        }
    }

    /**
     * Rotate the image.
     * TODO: add a progress spinner
     *
     * @param angle rotate by the specified amount
     */
    private void rotateImage(final long angle) {

        File file = getCoverFile();
        if (!file.exists()) {
            return;
        }

        // We load the file and first scale it to scale 10 x the "standard" display size.
        // Keep in mind this means it could be up- or downscaled from the original !
        int imageSize = ImageUtils.getMaxImageSize(10);

        // we'll try it twice with a gc in between
        int attempts = 2;
        while (true) {
            try {
                Bitmap bm = ImageUtils.forceScaleBitmap(file.getPath(),
                                                        imageSize, imageSize, true);
                if (bm == null) {
                    return;
                }

                Matrix matrix = new Matrix();

                // alternative code sets the pivot point.
                // matrix.setRotate(angle, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
                matrix.postRotate(angle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0,
                                                           bm.getWidth(), bm.getHeight(),
                                                           matrix, true);
                // if rotation worked, clean up the old one right now to save memory.
                if (rotatedBitmap != bm) {
                    bm.recycle();
                }

                // Write back to the file
                try (OutputStream out = new FileOutputStream(file.getAbsoluteFile())) {
                    rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

                } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
                    Logger.error(this, e);
                    return;
                }

                // put the new image on screen.
                ImageUtils.setImageView(mCoverView, getCoverFile(), mMaxWidth, mMaxHeight, true);
                return;

            } catch (@NonNull final OutOfMemoryError e) {
                attempts--;
                if (attempts > 1) {
                    System.gc();
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void cropCoverImage(@NonNull final File imageFile) {
        boolean external = PreferenceManager.getDefaultSharedPreferences(mContext)
                                            .getBoolean(Prefs.pk_images_external_cropper,
                                                        false);
        if (external) {
            cropCoverImageExternal(imageFile);
        } else {
            cropCoverImageInternal(imageFile);
        }
    }

    /**
     * Crop the image using our internal code in {@link CropImageActivity}.
     *
     * @param imageFile to crop
     */
    private void cropCoverImageInternal(@NonNull final File imageFile) {
        boolean wholeImage = PreferenceManager.getDefaultSharedPreferences(mContext)
                                              .getBoolean(Prefs.pk_images_crop_whole_image,
                                                          false);

        // Get the output file spec, and make sure it does not already exist.
        File cropped = getCroppedTempCoverFile();
        StorageUtils.deleteFile(cropped);

        Intent intent = new Intent(mContext, CropImageActivity.class)
                                .putExtra(CropImageActivity.BKEY_IMAGE_ABSOLUTE_PATH,
                                          imageFile.getAbsolutePath())
                                .putExtra(CropImageActivity.BKEY_OUTPUT_ABSOLUTE_PATH,
                                          cropped.getAbsolutePath())
                                .putExtra(CropImageActivity.BKEY_WHOLE_IMAGE, wholeImage);

        mCallerFragment.startActivityForResult(intent, UniqueId.REQ_CROP_IMAGE_INTERNAL);
    }

    /**
     * Crop the image using the standard crop action intent (the device may not support it).
     * <p>
     * "com.android.camera.action.CROP" is from the Camera2 application in Android 1.x/2.x.
     * It's no longer officially supported, but has been implemented by several other apps.
     * <p>
     * Code using hardcoded string on purpose as they are part of the Intent api.
     *
     * @param imageFile to crop
     */
    private void cropCoverImageExternal(@NonNull final File imageFile) {

        Uri inputURI = FileProvider.getUriForFile(mContext,
                                                  GenericFileProvider.AUTHORITY,
                                                  imageFile);
        File cropped = getCroppedTempCoverFile();
        // make sure any left-over file is removed.
        StorageUtils.deleteFile(cropped);
        Uri outputURI = Uri.fromFile(cropped);
//        FileProvider.getUriForFile(mContext, GenericFileProvider.AUTHORITY, cropped);

        //call the standard crop action intent (the device may not support it)
        Intent intent = new Intent("com.android.camera.action.CROP")
                                // image Uri and type
                                .setDataAndType(inputURI, "image/*")
                                // not interested in faces
                                .putExtra("noFaceDetection", true)
                                // {@code true} to return a Bitmap,
                                // {@code false} to directly save the cropped image
                                .putExtra("return-data", false)
                                //indicate we want to crop
                                .putExtra("crop", true)
                                // and allow scaling
                                .putExtra("scale", true);

        // other options not needed for now.
//            //indicate aspect of desired crop
//            intent.putExtra("aspectX", 1);
//            intent.putExtra("aspectY", 1);
//            //indicate output X and Y
//            intent.putExtra("outputX", 256);
//            intent.putExtra("outputY", 256);

        // Save output image in uri
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputURI);

        List<ResolveInfo> list = mContext.getPackageManager().queryIntentActivities(intent, 0);
        if (list.isEmpty()) {
            UserMessage.show(mCoverView, R.string.error_no_external_crop_app);
        } else {
            mCallerFragment.startActivityForResult(intent, UniqueId.REQ_CROP_IMAGE_EXTERNAL);
        }

    }

    /**
     * @return a temp file for cropping result.
     */
    @NonNull
    private File getCroppedTempCoverFile() {
        return StorageUtils.getTempCoverFile(String.valueOf(sTempImageCounter), "_cropped");
    }

    /**
     * Delete the image.
     */
    private void deleteCoverFile() {
        try {
            StorageUtils.deleteFile(getCoverFile());
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
        }
        // Ensure that the cached images for this book are deleted (if present).
        if (mBook.getId() != 0) {
            CoversDAO.delete(getUuid());
        }
        // replace the old image with a placeholder.
        ImageUtils.setImageView(mCoverView, getCoverFile(), mMaxWidth, mMaxHeight, true);
    }

    /**
     * Handles results from Camera, Image Gallery, Cropping.
     * <p>
     * <b>Note:</b> rotating is done locally in {@link #rotateImage(long)}.
     *
     * @return {@code true} when handled, {@code false} if unknown requestCode
     */
    public boolean onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        switch (requestCode) {
            // coming back from CoverBrowserFragment with the selected image.
            case UniqueId.REQ_ALT_EDITION:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    onImageSelected(data.getStringExtra(UniqueId.BKEY_FILE_SPEC));
                }
                return true;

            case UniqueId.REQ_ACTION_IMAGE_CAPTURE:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    Bitmap bitmap = data.getParcelableExtra(CropImageActivity.BKEY_DATA);
                    addCoverFromCamera(requestCode, resultCode, bitmap);
                }
                return true;

            case UniqueId.REQ_ACTION_GET_CONTENT:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    addCoverFromGallery(data);
                }
                return true;

            case UniqueId.REQ_CROP_IMAGE_INTERNAL:
            case UniqueId.REQ_CROP_IMAGE_EXTERNAL:
                if (resultCode == Activity.RESULT_OK) {
                    File cropped = getCroppedTempCoverFile();
                    if (cropped.exists()) {
                        File destination = getCoverFile();
                        StorageUtils.renameFile(cropped, destination);
                        // Update the ImageView with the new image
                        ImageUtils.setImageView(mCoverView, getCoverFile(),
                                                mMaxWidth, mMaxHeight, true);
                    } else {
                        if (BuildConfig.DEBUG /* always */) {
                            Logger.debug(this, "onActivityResult",
                                         "RESULT_OK, but no image file?",
                                         "requestCode=" + requestCode,
                                         "resultCode=" + resultCode);
                        }
                    }
                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        Logger.debug(this, "onActivityResult",
                                     "FAILED",
                                     "requestCode=" + requestCode,
                                     "resultCode=" + resultCode);
                    }
                    StorageUtils.deleteFile(getCroppedTempCoverFile());
                }
                return true;

            default:
                return false;
        }
    }
}
