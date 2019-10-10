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
import android.net.Uri;
import android.provider.MediaStore;
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
import java.io.IOException;
import java.io.InputStream;
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
import com.hardbacknutter.nevertoomanybooks.utils.CameraHelper;
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

    /** The cropper uses a single file. */
    private static final String CROPPED_COVER_FILENAME = "Cropped";
    private static final String IMAGE_MIME_TYPE = "image/*";

    /** The fragment hosting us. */
    @NonNull
    private final Fragment mFragment;

    /** Context from the fragment, cached as used frequently. */
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
    private CoverBrowserFragment mCoverBrowser;

    /** Used to display a hint if user rotates a camera image. */
    private boolean mShowHintAboutRotating;

    @Nullable
    private CameraHelper mCameraHelper;

    /**
     * Constructor.
     *
     * @param fragment  the hosting fragment
     * @param db        Database Access
     * @param book      the book whose cover we're handling
     * @param isbnView  the view to read the *current* ISBN from
     * @param coverView the view to populate
     * @param scale     image scale factor to apply when populating the coverView.
     */
    CoverHandler(@NonNull final Fragment fragment,
                 @NonNull final DAO db,
                 @NonNull final Book book,
                 @NonNull final TextView isbnView,
                 @NonNull final ImageView coverView,
                 @ImageUtils.Scale final int scale) {

        int maxSize = ImageUtils.getMaxImageSize(scale);

        mFragment = fragment;

        //noinspection ConstantConditions
        mContext = mFragment.getContext();
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
                v -> ZoomedImageDialogFragment.show(mFragment.getParentFragmentManager(),
                                                    getCoverFile()));
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
                           R.string.menu_cover_replace_from_camera)
                      .setIcon(R.drawable.ic_add_a_photo);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_FROM_GALLERY, 0,
                           R.string.menu_cover_replace_from_gallery)
                      .setIcon(R.drawable.ic_photo_gallery);
        replaceSubmenu.add(Menu.NONE, R.id.MENU_THUMB_ADD_ALT_EDITIONS, 0,
                           R.string.menu_cover_replace_from_alt_editions)
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
        new MenuPicker<>(mContext, title, null, menu, R.id.coverImage,
                         this::onViewContextItemSelected)
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
            case R.id.MENU_DELETE: {
                deleteCoverFile();
                return true;
            }
            case R.id.SUBMENU_THUMB_ROTATE: {
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mShowHintAboutRotating) {
                    TipManager.display(mContext,
                                       R.string.tip_autorotate_camera_images,
                                       null);
                    mShowHintAboutRotating = false;
                }
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CW: {
                //TODO: add a progress spinner while rotating
                if (ImageUtils.rotate(getCoverFile(), 90)) {
                    refreshImageView();
                }
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_CCW: {
                if (ImageUtils.rotate(getCoverFile(), -90)) {
                    refreshImageView();
                }
                return true;
            }
            case R.id.MENU_THUMB_ROTATE_180: {
                if (ImageUtils.rotate(getCoverFile(), 180)) {
                    refreshImageView();
                }
                return true;
            }
            case R.id.MENU_THUMB_CROP: {
                startCropper(getCoverFile());
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_CAMERA: {
                startCamera();
                return true;
            }
            case R.id.MENU_THUMB_ADD_FROM_GALLERY: {
                startChooser();
                return true;
            }
            case R.id.MENU_THUMB_ADD_ALT_EDITIONS: {
                startCoverBrowser();
                return true;
            }
            default: {
                return false;
            }
        }
    }

    /**
     * Handles results from Camera, Image Gallery, Cropping.
     *
     * @return {@code true} when handled, {@code false} if unknown requestCode
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        switch (requestCode) {
            case UniqueId.REQ_ACTION_COVER_BROWSER: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    processCoverBrowserResult(data);
                }
                return true;
            }
            case UniqueId.REQ_ACTION_IMAGE_CAPTURE: {
                if (resultCode == Activity.RESULT_OK) {
                    processCameraResult(data);
                }
                return true;
            }
            case UniqueId.REQ_ACTION_GET_CONTENT: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    processChooserResult(data);
                }
                return true;
            }
            case UniqueId.REQ_CROP_IMAGE_INTERNAL:
            case UniqueId.REQ_CROP_IMAGE_EXTERNAL: {
                if (resultCode == Activity.RESULT_OK) {
                    File source = StorageUtils.getTempCoverFile(CROPPED_COVER_FILENAME);
                    File destination = getCoverFile();
                    StorageUtils.renameFile(source, destination);
                    refreshImageView();
                    return true;
                }

                StorageUtils.deleteFile(StorageUtils.getTempCoverFile(CROPPED_COVER_FILENAME));
                return true;
            }
            default:
                return false;
        }
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
            Logger.warnWithStackTrace(mContext, this, "getUuid",
                                      "UUID was not available on the view");
            uuid = mDb.getBookUuid(mBook.getId());
        }
        return uuid;
    }

    /**
     * Put the cover image on screen, and update the book.
     */
    private void refreshImageView() {
        boolean isSet = ImageUtils.setImageView(mCoverView, getCoverFile(),
                                                mMaxWidth, mMaxHeight, true);
        mBook.putBoolean(UniqueId.BKEY_IMAGE, isSet);
    }

    /**
     * Get the File object for the cover of the book we are editing.
     * If the book is new (0), return the standard temporary cover file.
     * If the data is a result from a search, that standard temp file will
     * be the downloaded file.
     * <p>
     * Otherwise, return the actual cover file for the book.
     */
    @NonNull
    private File getCoverFile() {
        if (mBook.getId() == 0) {
            return StorageUtils.getTempCoverFile();
        }
        return StorageUtils.getCoverFileForUuid(getUuid());
    }

    /**
     * Delete the image.
     */
    private void deleteCoverFile() {
        try {
            StorageUtils.deleteFile(getCoverFile());
        } catch (@NonNull final RuntimeException e) {
            Logger.error(mContext, this, e);
        }
        // Ensure that the cached images for this book are deleted (if present).
        if (mBook.getId() != 0) {
            CoversDAO.delete(getUuid());
        }
        // replace the old image with a placeholder.
        refreshImageView();
    }

    private void startCamera() {
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setRotationAngle(App.getListPreference(Prefs.pk_images_rotate_auto, 0));
            mCameraHelper.setUseFullSize(true);
        }
        mCameraHelper.startCamera(mFragment, UniqueId.REQ_ACTION_IMAGE_CAPTURE);
    }

    private void processCameraResult(@Nullable final Intent data) {
        //noinspection ConstantConditions
        File file = mCameraHelper.getFile(data);
        if (file != null && file.exists()) {
            // the cropper will rename the file to what the rest of the code expects
            // and will refresh the view.
            startCropper(file);
            mShowHintAboutRotating = true;
        }
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     */
    private void startCoverBrowser() {
        // this is essential, as we only get alternative editions from LibraryThing for now.
        if (!LibraryThingManager.hasKey()) {
            LibraryThingManager.alertRegistrationNeeded(mContext, true, "cover_browser");
            return;
        }

        String isbn = mIsbnView.getText().toString().trim();
        if (ISBN.isValid(isbn)) {
            FragmentManager fm = mFragment.getParentFragmentManager();
            // we must use the same fragment manager as the hosting fragment.
            mCoverBrowser = (CoverBrowserFragment) fm.findFragmentByTag(CoverBrowserFragment.TAG);
            if (mCoverBrowser == null) {
                mCoverBrowser = CoverBrowserFragment.newInstance(isbn, SearchSites.SEARCH_ALL);
                mCoverBrowser.show(fm, CoverBrowserFragment.TAG);
            }
            mCoverBrowser.setTargetFragment(mFragment, UniqueId.REQ_ACTION_COVER_BROWSER);

        } else {
            UserMessage.show(mCoverView, R.string.warning_action_requires_isbn);
        }
    }

    /**
     * When the user clicks the switcher in the {@link CoverBrowserFragment},
     * we take that image and stuff it into the view.
     */
    private void processCoverBrowserResult(@NonNull final Intent data) {
        String fileSpec = data.getStringExtra(UniqueId.BKEY_FILE_SPEC);
        if (mCoverBrowser != null && fileSpec != null && !fileSpec.isEmpty()) {
            File source = new File(fileSpec);
            File destination = getCoverFile();
            StorageUtils.renameFile(source, destination);
            refreshImageView();
            // all done, get rid of the browser fragment
            mCoverBrowser.dismiss();
            mCoverBrowser = null;
        }
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from an external app.
     */
    private void startChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                                .setType(IMAGE_MIME_TYPE);
        mFragment.startActivityForResult(
                Intent.createChooser(intent, mContext.getString(R.string.title_select_image)),
                UniqueId.REQ_ACTION_GET_CONTENT);
    }

    /**
     * The Intent.ACTION_GET_CONTENT has provided us with an image, process it.
     */
    private void processChooserResult(@NonNull final Intent data) {
        Uri selectedImageUri = data.getData();

        if (selectedImageUri != null) {
            int bytesRead = 0;
            // If no 'content' scheme, use the content resolver.
            try (InputStream is = mContext.getContentResolver()
                                          .openInputStream(selectedImageUri)) {
                bytesRead = StorageUtils.saveInputStreamToFile(is, getCoverFile());

            } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final IOException e) {
                Logger.error(mContext, this, e, "Unable to copy content to file");
            }

            if (bytesRead > 0) {
                // Update the ImageView with the new image
                refreshImageView();
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


    private void startCropper(@NonNull final File inputFile) {
        boolean external = PreferenceManager.getDefaultSharedPreferences(mContext)
                                            .getBoolean(Prefs.pk_images_external_cropper, false);

        // Get the output file spec, and make sure it does not already exist.
        File outputFile = StorageUtils.getTempCoverFile(CROPPED_COVER_FILENAME);
        StorageUtils.deleteFile(outputFile);

        if (external) {
            startExternalCropper(inputFile, outputFile);
        } else {
            startInternalCropper(inputFile, outputFile);
        }
    }

    /**
     * Crop the image using our internal code in {@link CropImageActivity}.
     *
     * @param inputFile  to crop
     * @param outputFile where to write the result
     */
    private void startInternalCropper(@NonNull final File inputFile,
                                      @NonNull final File outputFile) {
        boolean wholeImage = PreferenceManager.getDefaultSharedPreferences(mContext)
                                              .getBoolean(Prefs.pk_images_crop_whole_image, false);

        Intent intent = new Intent(mContext, CropImageActivity.class)
                                .putExtra(CropImageActivity.BKEY_IMAGE_ABSOLUTE_PATH,
                                          inputFile.getAbsolutePath())
                                .putExtra(CropImageActivity.BKEY_OUTPUT_ABSOLUTE_PATH,
                                          outputFile.getAbsolutePath())
                                .putExtra(CropImageActivity.BKEY_WHOLE_IMAGE, wholeImage);

        mFragment.startActivityForResult(intent, UniqueId.REQ_CROP_IMAGE_INTERNAL);
    }

    /**
     * Crop the image using the standard crop action intent (the device may not support it).
     * <p>
     * "com.android.camera.action.CROP" is from the Camera2 application in Android 1.x/2.x.
     * It's no longer officially supported, but has been implemented by several other apps.
     * <p>
     * Code using hardcoded string on purpose as they are part of the Intent api.
     *
     * @param inputFile  to crop
     * @param outputFile where to write the result
     */
    private void startExternalCropper(@NonNull final File inputFile,
                                      @NonNull final File outputFile) {

        Uri inputUri = FileProvider.getUriForFile(mContext, GenericFileProvider.AUTHORITY,
                                                  inputFile);
        // using the provider is not needed. Leaving here as reminder.
        // FileProvider.getUriForFile(mContext, GenericFileProvider.AUTHORITY, outputFile);
        Uri outputUri = Uri.fromFile(outputFile);

        //call the standard crop action intent (the device may not support it)
        Intent intent = new Intent("com.android.camera.action.CROP")
                                // image Uri and type
                                .setDataAndType(inputUri, IMAGE_MIME_TYPE)
                                // not interested in faces
                                .putExtra("noFaceDetection", true)
                                // {@code true} to return a Bitmap,
                                // {@code false} to directly save the cropped image
                                .putExtra("return-data", false)
                                //indicate we want to crop
                                .putExtra("crop", true)
                                // and allow scaling
                                .putExtra("scale", true)
                                // Save output image in uri
                                .putExtra(MediaStore.EXTRA_OUTPUT, outputUri);

//                                // other options not needed for now.
//                                //indicate aspect of desired crop
//                                .putExtra("aspectX", 1)
//                                .putExtra("aspectY", 1)
//                                //indicate output X and Y
//                                .putExtra("outputX", 256)
//                                .putExtra("outputY", 256);


        List<ResolveInfo> list = mContext.getPackageManager().queryIntentActivities(intent, 0);
        if (list.isEmpty()) {
            UserMessage.show(mCoverView, R.string.error_no_external_crop_app);
        } else {
            mFragment.startActivityForResult(intent, UniqueId.REQ_CROP_IMAGE_EXTERNAL);
        }
    }
}
