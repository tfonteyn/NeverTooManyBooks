package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.PopupMenu;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.cropper.CropIImage;
import com.eleybourn.bookcatalogue.cropper.CropImageActivity;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.picklist.SelectOneDialog;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.widgets.CoverBrowser;
import com.eleybourn.bookcatalogue.widgets.CoverBrowser.OnImageSelectedListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Abstract class for creating activities containing book details with a Cover image.
 *
 * All Cover image manipulation methods are implemented here.
 * the only {@link Field} handled here is the cover image R.id.coverImage
 *
 * Used by
 * {@link BookDetailsFragment}
 * {@link EditBookFieldsFragment}
 */
public abstract class BookAbstractFragmentWithCoverImage extends BookAbstractFragment implements SelectOneDialog.hasViewContextMenu {

    public static final String PREF_USE_EXTERNAL_IMAGE_CROPPER = "App.UseExternalImageCropper";
    public static final String PREF_CROP_FRAME_WHOLE_IMAGE = "App.CropFrameWholeImage";
    /** Degrees by which to rotate images automatically */
    public static final String PREF_AUTOROTATE_CAMERA_IMAGES = "App.AutorotateCameraImages";
    public static final int PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT = 90;

    /** Counter used to prevent images being reused accidentally */
    private static int mTempImageCounter = 0;
    protected ImageUtils.ThumbSize mThumbSize;

    @Nullable
    private CoverBrowser mCoverBrowser = null;
    /** Used to display a hint if user rotates a camera image */
    private boolean mGotCameraImage = false;
    /** the cover image */
    private ImageView mCoverView;

    /**
     * 2018-11-03: explicitly moved ALL fields (except cover image) to the concrete classes.
     * Even with duplication, this brings this class back to what
     * it should be: COVER IMAGE support
     */
    @CallSuper
    protected void initFields() {
        super.initFields();

        // See how big the display is and use that to set bitmap sizes
        initThumbSize(requireActivity());

        // add the cover image.
        mFields.add(R.id.coverImage, "", UniqueId.KEY_BOOK_THUMBNAIL);
        // We need the view in many places. Cache it, to avoid clutter & casting
        mCoverView = mFields.getField(R.id.coverImage).getView();
        if (mFields.getField(R.id.coverImage).visible) {
            // add context menu to the cover image
            initViewContextMenuListener(requireContext(), mCoverView);
            //Allow zooming by clicking on the image
            mCoverView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ImageUtils.showZoomedThumb(requireActivity(), getCoverFile(getBook().getBookId()));
                }
            });
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }
        switch (requestCode) {
            case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_IMAGE_CAPTURE: /* 0b7027eb-a9da-469b-8ba7-2122f1006e92 */
                if (resultCode == Activity.RESULT_OK) {
                    /* there *has* to be 'data' */
                    Objects.requireNonNull(data);
                    Bundle extras = data.getExtras();
                    /* there *has* to be 'extras' */
                    Objects.requireNonNull(extras);
                    addCoverFromCamera(requestCode, resultCode, extras);
                }
                return;

            case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_ACTION_GET_CONTENT: /* 27ecaa27-5ed8-4670-8947-112ab4ab0098 */
                if (resultCode == Activity.RESULT_OK) {
                    /* there *has* to be 'data' */
                    Objects.requireNonNull(data);
                    addCoverFromGallery(data);
                }
                return;

            case CropImageActivity.REQUEST_CODE: /* 31c90366-d352-496f-9b7d-3237dd199a77 */
            case UniqueId.ACTIVITY_REQUEST_CODE_EXTERNAL_CROP_IMAGE: /* 28ec93b0-24fb-4a81-ae6d-a282f3a7b918 */ {
                if (resultCode == Activity.RESULT_OK) {
                    File cropped = this.getCroppedTempCoverFile();
                    if (cropped.exists()) {
                        File thumbFile = getCoverFile(getBook().getBookId());
                        StorageUtils.renameFile(cropped, thumbFile);
                        // Update the ImageView with the new image
                        populateCoverImage(getBook().getBookId());
                    } else {
                        Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - result OK, but no image file", Tracker.States.Running);
                    }
                } else {
                    Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - bad result", Tracker.States.Running);
                    StorageUtils.deleteFile(this.getCroppedTempCoverFile());
                }
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    public void onCreateOptionsMenu(final @NonNull Menu menu, final @NonNull MenuInflater inflater) {
        if (mFields.getField(R.id.coverImage).visible) {
            menu.add(Menu.NONE, R.id.SUBMENU_REPLACE_THUMB, 0, R.string.cover_options_cc_ellipsis)
                    .setIcon(R.drawable.ic_add_a_photo)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * This will be called when a menu item is selected.
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.SUBMENU_REPLACE_THUMB:
                // Show the context menu for the cover thumbnail
                prepareCoverImageViewContextMenu(mCoverView);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void initViewContextMenuListener(final @NonNull Context context, final @NonNull View view) {
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final @NonNull View view) {
                prepareCoverImageViewContextMenu(view);
                return true;
            }
        });
    }

    /**
     * external from {@link #initViewContextMenuListener} as we also need this from {@link #onOptionsItemSelected}
     */
    private void prepareCoverImageViewContextMenu(final @NonNull View view) {

        String menuTitle = getString(R.string.thumbnail);

        // legal trick to get an instance of Menu.
        Menu menu = new PopupMenu(BookAbstractFragmentWithCoverImage.this.getContext(), null).getMenu();
        // custom menuInfo
        SelectOneDialog.SimpleDialogMenuInfo menuInfo = new SelectOneDialog.SimpleDialogMenuInfo(menuTitle, view, 0);
        // populate the menu

        menu.add(Menu.NONE, R.id.MENU_DELETE_THUMB, 0, R.string.menu_delete_thumb)
                .setIcon(R.drawable.ic_delete);

        SubMenu replaceThumbnailSubmenu = menu.addSubMenu(Menu.NONE,
                R.id.SUBMENU_REPLACE_THUMB, 2, R.string.menu_replace_thumb);
        replaceThumbnailSubmenu.setIcon(R.drawable.ic_find_replace);

        replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_FROM_CAMERA, 1, R.string.menu_add_thumb_photo)
                .setIcon(R.drawable.ic_add_a_photo);
        replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_FROM_GALLERY, 2, R.string.menu_add_thumb_gallery)
                .setIcon(R.drawable.ic_image);
        replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_ALT_EDITIONS, 3, R.string.menu_thumb_alt_editions)
                .setIcon(R.drawable.ic_find_replace);

        SubMenu rotateThumbnailSubmenu = menu.addSubMenu(Menu.NONE,
                R.id.SUBMENU_ROTATE_THUMB, 3, R.string.menu_rotate_thumb);
        rotateThumbnailSubmenu.setIcon(R.drawable.ic_rotate_right);

        rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_CW, 1, R.string.menu_rotate_thumb_cw)
                .setIcon(R.drawable.ic_rotate_right);
        rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_CCW, 2, R.string.menu_rotate_thumb_ccw)
                .setIcon(R.drawable.ic_rotate_left);
        rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_180, 3, R.string.menu_rotate_thumb_180)
                .setIcon(R.drawable.ic_swap_vert);
        menu.add(Menu.NONE, R.id.MENU_CROP_THUMB, 4, R.string.menu_crop_thumb)
                .setIcon(R.drawable.ic_crop);

        // display
        onCreateViewContextMenu(menu, view, menuInfo);
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @Override
    public void onCreateViewContextMenu(final @NonNull Menu menu,
                                        final @NonNull View view,
                                        final @NonNull SelectOneDialog.SimpleDialogMenuInfo menuInfo) {

        if (menu.size() > 0) {
            SelectOneDialog.showContextMenuDialog(getLayoutInflater(), menuInfo, menu,
                    new SelectOneDialog.SimpleDialogOnClickListener() {
                        @Override
                        public void onClick(final @NonNull SelectOneDialog.SimpleDialogItem item) {
                            MenuItem menuItem = ((SelectOneDialog.SimpleDialogMenuItem) item).getMenuItem();
                            if (menuItem.hasSubMenu()) {
                                menuInfo.title = menuItem.getTitle().toString();
                                onCreateViewContextMenu(menuItem.getSubMenu(), view, menuInfo);
                            } else {
                                onViewContextItemSelected(menuItem, view);
                            }
                        }
                    });
        }
    }

    /**
     * Using {@link SelectOneDialog#showContextMenuDialog} for context menus
     */
    @Override
    public boolean onViewContextItemSelected(final @NonNull MenuItem menuItem,
                                             final @NonNull View view) {

        // should not happen for now, but nice to remind ourselves we *could* have multiple views.
        if (view.getId() != R.id.coverImage) {
            return false;
        }

        File thumbFile = getCoverFile(getBook().getBookId());

        switch (menuItem.getItemId()) {
            case R.id.MENU_DELETE_THUMB: {
                deleteCoverFile();
                ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                return true;
            }
            case R.id.SUBMENU_ROTATE_THUMB: {
                // Just a submenu; skip, but display a hint if user is rotating a camera image
                if (mGotCameraImage) {
                    HintManager.displayHint(requireActivity().getLayoutInflater(), R.string.hint_autorotate_camera_images, null);
                    mGotCameraImage = false;
                }
                return true;
            }
            case R.id.MENU_ROTATE_THUMB_CW: {
                rotateThumbnail(90);
                ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                return true;
            }
            case R.id.MENU_ROTATE_THUMB_CCW: {
                rotateThumbnail(-90);
                ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                return true;
            }
            case R.id.MENU_ROTATE_THUMB_180: {
                rotateThumbnail(180);
                ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                return true;
            }
            case R.id.MENU_CROP_THUMB: {
                cropCoverImage(thumbFile);
                return true;
            }
            case R.id.MENU_ADD_THUMB_FROM_CAMERA: {
                getCoverFromCamera();
                return true;
            }
            case R.id.MENU_ADD_THUMB_FROM_GALLERY: {
                getCoverFromGallery();
                return true;
            }
            case R.id.MENU_ADD_THUMB_ALT_EDITIONS: {
                getCoverFromAlternativeEditions();
                return true;
            }
        }

        return false;
    }

    /**
     * @param activity which will determine the actual size for the thumbnails
     */
    public void initThumbSize(final @NonNull Activity activity) {
        mThumbSize = ImageUtils.getThumbSizes(activity);
    }

    /**
     * Load the image into the view, using preset {@link ImageUtils.ThumbSize#normal} dimensions
     *
     * @param bookId to retrieve image
     */
    protected void populateCoverImage(final long bookId) {
        ImageUtils.fetchFileIntoImageView(mCoverView, getCoverFile(bookId), mThumbSize.normal, mThumbSize.normal, true);
    }

    /**
     * Load the image into the view, using custom dimensions
     *
     * @param bookId to retrieve image
     */
    protected void populateCoverImage(final long bookId, final int maxWidth, final int maxHeight) {
        ImageUtils.fetchFileIntoImageView(mCoverView, getCoverFile(bookId), maxWidth, maxHeight, true);
    }

    /**
     * Start the camera to get an image
     */
    private void getCoverFromCamera() {
        // Increment the temp counter and cleanup the temp directory
        mTempImageCounter++;
        StorageUtils.cleanupTempDirectory();
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        /*
            We don't do the next bit of code here because we have no reliable way to rotate a
            large image without producing memory exhaustion.
            Android does not include a file-based image rotation.

            File f = this.getCameraTempCoverFile();
            StorageUtils.deleteFile(f);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        */
        startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_IMAGE_CAPTURE); /* 0b7027eb-a9da-469b-8ba7-2122f1006e92 */
    }

    /**
     * Call out the Intent.ACTION_GET_CONTENT to get an image from (usually) the Gallery app.
     */
    private void getCoverFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)),
                UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_ACTION_GET_CONTENT); /* 27ecaa27-5ed8-4670-8947-112ab4ab0098 */
    }

    /**
     * Use the isbn to fetch other possible images from the internet and present to the user to choose one
     */
    private void getCoverFromAlternativeEditions() {
        Field isbnField = mFields.getField(R.id.isbn);
        String isbn = isbnField.getValue().toString();
        if (IsbnUtils.isValid(isbn)) {
            mCoverBrowser = new CoverBrowser(requireActivity(), isbn, new OnImageSelectedListener() {
                @Override
                public void onImageSelected(final @NonNull String fileSpec) {
                    if (mCoverBrowser != null) {
                        // Get the current file
                        File bookFile = getCoverFile(getBook().getBookId());
                        File newFile = new File(fileSpec);
                        // Overwrite with new file
                        StorageUtils.renameFile(newFile, bookFile);
                        // Update the ImageView with the new image
                        populateCoverImage(getBook().getBookId());
                        mCoverBrowser.dismiss();
                        mCoverBrowser = null;
                    }
                }
            });
            mCoverBrowser.showEditionCovers();
        } else {
            //Snackbar.make(isbnField.getView(), R.string.editions_require_isbn, Snackbar.LENGTH_LONG).show();
            StandardDialogs.showUserMessage(requireActivity(), R.string.editions_require_isbn);
        }
    }


    /**
     * The camera has captured an image, process it.
     */
    private void addCoverFromCamera(final int requestCode, final int resultCode, final @NonNull Bundle bundle) {
        Bitmap bitmap = (Bitmap) bundle.get(CropIImage.BKEY_DATA);
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            Matrix m = new Matrix();
            m.postRotate(getPrefs().getInt(PREF_AUTOROTATE_CAMERA_IMAGES, PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT));
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

            File cameraFile = StorageUtils.getTempCoverFile("camera", "" + mTempImageCounter);
            FileOutputStream out;
            // Create a file to copy the thumbnail into
            try {
                out = new FileOutputStream(cameraFile.getAbsoluteFile());
            } catch (FileNotFoundException e) {
                Logger.error(e);
                return;
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

            cropCoverImage(cameraFile);
            mGotCameraImage = true;
        } else {
            Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - camera image empty", Tracker.States.Running);
        }
    }


    /**
     * The Intent.ACTION_GET_CONTENT has provided us with an image, process it.
     */
    private void addCoverFromGallery(final @NonNull Intent data) {
        Uri selectedImageUri = data.getData();

        if (selectedImageUri != null) {
            boolean imageOk = false;
            // If no 'content' scheme, then use the content resolver.
            try {
                InputStream in = requireContext().getContentResolver().openInputStream(selectedImageUri);
                imageOk = StorageUtils.saveInputStreamToFile(in, getCoverFile(getBook().getBookId()));
            } catch (FileNotFoundException e) {
                Logger.error(e, "Unable to copy content to file");
            }
            if (imageOk) {
                // Update the ImageView with the new image
                populateCoverImage(getBook().getBookId());
            } else {
                String s = getString(R.string.could_not_copy_image) + ". " + getString(R.string.if_the_problem_persists);
                StandardDialogs.showUserMessage(requireActivity(), s);
            }
        } else {
            /* Deal with the case where the chooser returns a null intent. This seems to happen
             * when the filename is not properly understood by the choose (eg. an apostrophe in
             * the file name confuses ES File Explorer in the current version as of 23-Sep-2012. */
            StandardDialogs.showUserMessage(requireActivity(), R.string.could_not_copy_image);
        }
    }

    /**
     * Rotate the thumbnail
     *
     * @param angle by a specified amount
     */
    private void rotateThumbnail(final long angle) {
        // we'll try it twice with a gc in between
        int attempts = 2;
        while (true) {
            try {
                File thumbFile = getCoverFile(getBook().getBookId());

                Bitmap bitmap = ImageUtils.fetchFileIntoImageView(null, thumbFile,
                        mThumbSize.zoomed * 2, mThumbSize.zoomed * 2, true);
                if (bitmap == null) {
                    return;
                }

                Matrix matrix = new Matrix();
                matrix.postRotate(angle);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle();
                }

                /* Create a file to copy the thumbnail into */
                FileOutputStream outFos;
                try {
                    outFos = new FileOutputStream(thumbFile.getAbsoluteFile());
                } catch (FileNotFoundException e) {
                    Logger.error(e);
                    return;
                }
                rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outFos);
                rotatedBitmap.recycle();
                return;
            } catch (java.lang.OutOfMemoryError e) {
                attempts--;
                if (attempts > 1) {
                    System.gc();
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void cropCoverImage(final @NonNull File thumbFile) {
        boolean external = getPrefs().getBoolean(PREF_USE_EXTERNAL_IMAGE_CROPPER, false);
        if (external) {
            cropCoverImageExternal(thumbFile);
        } else {
            cropCoverImageInternal(thumbFile);
        }
    }

    /**
     * Get a temp file for cropping output
     */
    @NonNull
    private File getCroppedTempCoverFile() {
        return StorageUtils.getTempCoverFile("cropped", "" + mTempImageCounter);
    }

    private void cropCoverImageInternal(final @NonNull File thumbFile) {
        boolean cropFrameWholeImage = getPrefs().getBoolean(PREF_CROP_FRAME_WHOLE_IMAGE, false);

        Intent intent = new Intent(requireActivity(), CropImageActivity.class);
        intent.putExtra(CropIImage.REQUEST_KEY_IMAGE_ABSOLUTE_PATH, thumbFile.getAbsolutePath());
        intent.putExtra(CropIImage.REQUEST_KEY_SCALE, true);
        intent.putExtra(CropIImage.REQUEST_KEY_NO_FACE_DETECTION, true);
        intent.putExtra(CropIImage.REQUEST_KEY_WHOLE_IMAGE, cropFrameWholeImage);

        // Get and set the output file spec, and make sure it does not already exist.
        File cropped = this.getCroppedTempCoverFile();
        StorageUtils.deleteFile(cropped);
        intent.putExtra(CropIImage.REQUEST_KEY_OUTPUT_ABSOLUTE_PATH, cropped.getAbsolutePath());

        startActivityForResult(intent, CropImageActivity.REQUEST_CODE); /* 31c90366-d352-496f-9b7d-3237dd199a77 */
    }

    /**
     * Experimental
     */
    private void cropCoverImageExternal(final @NonNull File thumbFile) {
        Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Enter);
        try {

            // this is actually not an official interface; theoretically this might not be there 'tomorrow'
            // all extras are hardcoded strings on purpose
            Intent intent = new Intent("com.android.camera.action.CROP");

            // this will open any image file
            intent.setDataAndType(Uri.fromFile(new File(thumbFile.getAbsolutePath())), "image/*");
            intent.putExtra("crop", true);
            intent.putExtra("scale", true);
            intent.putExtra("noFaceDetection", true);
            // True to return a Bitmap, false to directly save the cropped image
            intent.putExtra("return-data", false);

            // Save output image in uri
            File cropped = this.getCroppedTempCoverFile();
            StorageUtils.deleteFile(cropped);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(cropped.getAbsolutePath())));

            List<ResolveInfo> list = requireActivity().getPackageManager().queryIntentActivities(intent, 0);
            if (list.size() == 0) {
                StandardDialogs.showUserMessage(requireActivity(), R.string.error_no_external_crop_app);
            } else {
                startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_EXTERNAL_CROP_IMAGE); /* 28ec93b0-24fb-4a81-ae6d-a282f3a7b918 */
            }
        } finally {
            Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Exit);
        }
    }

    /**
     * Delete the provided thumbnail
     */
    private void deleteCoverFile() {
        try {
            File thumbFile = getCoverFile(getBook().getBookId());
            StorageUtils.deleteFile(thumbFile);
        } catch (Exception e) {
            Logger.error(e);
        }
        invalidateCachedThumbnail();
    }

    /**
     * Ensure that the cached thumbnails for this book are deleted (if present)
     */
    private void invalidateCachedThumbnail() {
        final long bookId = getBook().getBookId();
        if (bookId != 0) {
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(requireContext())) {
                coversDbHelper.deleteBookCover(mDb.getBookUuid(bookId));
            } catch (Exception e) {
                Logger.error(e, "Error cleaning up cached cover images");
            }
        }
    }

    /**
     * Get the File object for the cover of the book we are editing.
     * If the book is new (0), return the standard temp file.
     */
    @NonNull
    private File getCoverFile(final long bookId) {
        if (bookId == 0) {
            return StorageUtils.getTempCoverFile();
        } else {
            return StorageUtils.getCoverFile(mDb.getBookUuid(bookId));
        }
    }

    @Override
    @CallSuper
    public void onPause() {
        // Close down the cover browser.
        if (mCoverBrowser != null) {
            mCoverBrowser.dismiss();
            mCoverBrowser = null;
        }
        super.onPause();
    }
}
