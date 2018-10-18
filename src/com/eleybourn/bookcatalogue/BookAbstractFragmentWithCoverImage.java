package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.cropper.CropIImage;
import com.eleybourn.bookcatalogue.cropper.CropImageActivity;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.Book;
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
 * Abstract class for creating activities containing book details.
 * Here we define common method for all children: database and background initializing,
 * initializing fields and display metrics and other common tasks.
 *
 * Used by {@link BookDetailsFragment} and {@link EditBookFieldsFragment}
 *
 * @author n.silin
 */
public abstract class BookAbstractFragmentWithCoverImage extends BookAbstractFragment {

    public static final String PREF_USE_EXTERNAL_IMAGE_CROPPER = "App.UseExternalImageCropper";
    public static final String PREF_CROP_FRAME_WHOLE_IMAGE = "App.CropFrameWholeImage";
    /** Degrees by which to rotate images automatically */
    public static final String PREF_AUTOROTATE_CAMERA_IMAGES = "App.AutorotateCameraImages";
    public static final int PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT = 90;
    /** Counter used to prevent images being reused accidentally */
    private static int mTempImageCounter = 0;
    protected ImageUtils.ThumbSize mThumbSize;
    /** convenience pointer to the prefs */
    private SharedPreferences mPrefs;
    @Nullable
    private CoverBrowser mCoverBrowser = null;
    /** Used to display a hint if user rotates a camera image */
    private boolean mGotCameraImage = false;
    /** the cover image */
    private ImageView mCoverView;

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        // create the common 'Fields'
        super.onActivityCreated(savedInstanceState);

        mPrefs = BookCatalogueApp.getSharedPreferences();

        // add the rest of the fields.
        initFields();
        // and the cover image
        initCoverImageField();
    }

    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        super.onPause();

        // Close down the cover browser.
        if (mCoverBrowser != null) {
            mCoverBrowser.dismiss();
            mCoverBrowser = null;
        }
        Tracker.exitOnPause(this);
    }

    /**
     * Add all book fields with corresponding validators. Note this is NOT where we set values.
     */
    @CallSuper
    protected void initFields() {
        /* Title has some post-processing on the text, to move leading 'A', 'The' etc to the end.
         * While we could do it in a formatter, it it not really a display-oriented function and
         * is handled in pre-processing in the database layer since it also needs to be applied
         * to imported record etc.
         * Or in short: nothing special here.
         */
        mFields.add(R.id.title, UniqueId.KEY_TITLE, null);

        mFields.add(R.id.author, "", UniqueId.KEY_AUTHOR_FORMATTED, null);

        mFields.add(R.id.format, UniqueId.KEY_BOOK_FORMAT, null);
        mFields.add(R.id.genre, UniqueId.KEY_BOOK_GENRE, null);
        mFields.add(R.id.isbn, UniqueId.KEY_BOOK_ISBN, null);
        mFields.add(R.id.language, UniqueId.KEY_BOOK_LANGUAGE, null);
        mFields.add(R.id.list_price, UniqueId.KEY_BOOK_LIST_PRICE, null);
        mFields.add(R.id.pages, UniqueId.KEY_BOOK_PAGES, null);
        mFields.add(R.id.series, UniqueId.KEY_SERIES_NAME, null);
        mFields.add(R.id.description, UniqueId.KEY_DESCRIPTION, null).setShowHtml(true);
        mFields.add(R.id.bookshelf, UniqueId.BKEY_BOOKSHELF_TEXT, null).doNoFetch = true; // Output-only field

        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED, null);
    }

    @Override
    @CallSuper
    protected void onLoadBookDetails(@NonNull final Book book, final boolean setAllDone) {
        super.onLoadBookDetails(book, setAllDone);
        populateFields(book);
    }

    abstract protected void populateFields(@NonNull final Book book);

    // yes, these 2 are not actually used from the base class. Just for a reminder.
    abstract protected void populateAuthorListField(@NonNull final Book book);

    abstract protected void populateSeriesListField(@NonNull final Book book);

    /**
     * Gets all bookshelves for the book from database and populate corresponding field with them.
     *
     * @param field to populate with the shelves
     * @param book  from which the shelves will be taken
     *
     * @return <tt>true</tt>if populated, false otherwise
     */
    protected boolean populateBookshelves(@NonNull final Field field, @NonNull final Book book) {
        // Display the selected bookshelves
        String bookshelfText = book.getString(Book.BOOKSHELF_TEXT);
        field.setValue(bookshelfText);
        return !bookshelfText.isEmpty();
    }


    //<editor-fold desc="Cover init and menu handling">

    private void initCoverImageField() {
        // See how big the display is and use that to set bitmap sizes
        initThumbSize(requireActivity());

        mFields.add(R.id.image, "", UniqueId.KEY_BOOK_THUMBNAIL, null);
        initCoverImageContextMenu(mFields.getField(R.id.image));
        // need this in other parts, so cache it.
        //noinspection ConstantConditions
        mCoverView = getView().findViewById(R.id.image);

        //Allow zooming by clicking on image
        mCoverView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                ImageUtils.showZoomedThumb(requireActivity(), getCoverFile(getBook().getBookId()));
            }
        });
    }

    private void initCoverImageContextMenu(@NonNull final Field coverField) {
        coverField.getView().setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
            @Override
            @CallSuper
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
                menu.add(Menu.NONE, R.id.MENU_DELETE_THUMB, 0, R.string.menu_delete_thumb)
                        .setIcon(R.drawable.ic_mode_edit);

                SubMenu replaceThumbnailSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_REPLACE_THUMB, 2, R.string.menu_replace_thumb);
                replaceThumbnailSubmenu.setIcon(R.drawable.ic_find_replace);

                replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_FROM_CAMERA, 1, R.string.menu_add_thumb_photo)
                        .setIcon(R.drawable.ic_add_a_photo);
                replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_FROM_GALLERY, 2, R.string.menu_add_thumb_gallery)
                        .setIcon(R.drawable.ic_image);
                replaceThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_ALT_EDITIONS, 3, R.string.menu_thumb_alt_editions)
                        .setIcon(R.drawable.ic_find_replace);

                SubMenu rotateThumbnailSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_ROTATE_THUMB, 3, R.string.menu_rotate_thumb);
                rotateThumbnailSubmenu.setIcon(R.drawable.ic_rotate_right);

                rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_CW, 1, R.string.menu_rotate_thumb_cw)
                        .setIcon(R.drawable.ic_rotate_right);
                rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_CCW, 2, R.string.menu_rotate_thumb_ccw)
                        .setIcon(R.drawable.ic_rotate_left);
                rotateThumbnailSubmenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_180, 3, R.string.menu_rotate_thumb_180)
                        .setIcon(R.drawable.ic_swap_vert);
                menu.add(Menu.NONE, R.id.MENU_CROP_THUMB, 4, R.string.menu_crop_thumb)
                        .setIcon(R.drawable.ic_crop);
            }
        });
    }

    public void initThumbSize(@NonNull final Activity activity) {
        mThumbSize = ImageUtils.getThumbSizes(activity);
    }

    /**
     * Handle menu items for manipulating the Cover Image (thumbnail)
     */
    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Enter);

        try {
            File thumbFile = getCoverFile(getBook().getBookId());

            switch (item.getItemId()) {
                case R.id.MENU_DELETE_THUMB:
                    deleteCoverFile();
                    ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                    return true;

                case R.id.SUBMENU_ROTATE_THUMB:
                    // Just a submenu; skip, but display a hint if user is rotating a camera image
                    if (mGotCameraImage) {
                        HintManager.displayHint(requireActivity(), R.string.hint_autorotate_camera_images, null);
                        mGotCameraImage = false;
                    }
                    return true;

                case R.id.MENU_ROTATE_THUMB_CW:
                    rotateThumbnail(90);
                    ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                    return true;

                case R.id.MENU_ROTATE_THUMB_CCW:
                    rotateThumbnail(-90);
                    ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                    return true;

                case R.id.MENU_ROTATE_THUMB_180:
                    rotateThumbnail(180);
                    ImageUtils.fetchFileIntoImageView(mCoverView, thumbFile, mThumbSize.normal, mThumbSize.normal, true);
                    return true;

                case R.id.MENU_CROP_THUMB:
                    cropCoverImage(thumbFile);
                    return true;

                case R.id.MENU_ADD_THUMB_FROM_CAMERA:
                    getCoverFromCamera();
                    return true;

                case R.id.MENU_ADD_THUMB_FROM_GALLERY:
                    getCoverFromGallery();
                    return true;

                case R.id.MENU_ADD_THUMB_ALT_EDITIONS:
                    getCoverFromAlternativeEditions();
                    return true;
            }
            return super.onContextItemSelected(item);
        } finally {
            Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Exit);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode);
        try {
            super.onActivityResult(requestCode, resultCode, data);

            switch (requestCode) {
                case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_IMAGE_CAPTURE:
                    if (resultCode == Activity.RESULT_OK) {
                        addCoverFromCamera(requestCode, resultCode, data);
                    }
                    break;

                case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_ACTION_GET_CONTENT:
                    if (resultCode == Activity.RESULT_OK) {
                        addCoverFromGallery(data);
                    }
                    break;

                case CropImageActivity.REQUEST_CODE:
                case UniqueId.ACTIVITY_REQUEST_CODE_EXTERNAL_CROP_IMAGE: {
                    if (resultCode == Activity.RESULT_OK) {
                        File cropped = this.getCroppedTempCoverFile();
                        if (cropped.exists()) {
                            File thumbFile = getCoverFile(getBook().getBookId());
                            StorageUtils.renameFile(cropped, thumbFile);
                            // Update the ImageView with the new image
                            setCoverImage(getBook().getBookId());
                        } else {
                            Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - result OK, no image file", Tracker.States.Running);
                        }
                    } else {
                        Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - bad result", Tracker.States.Running);
                        StorageUtils.deleteFile(this.getCroppedTempCoverFile());
                    }
                    break;
                }
            }
        } finally {
            Tracker.exitOnActivityResult(this, requestCode, resultCode);
        }
    }

    //</editor-fold>

    //<editor-fold desc="Cover manipulation Operations">

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

    private void cropCoverImage(@NonNull final File thumbFile) {
        boolean external = mPrefs.getBoolean(PREF_USE_EXTERNAL_IMAGE_CROPPER, false);
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

    private void cropCoverImageInternal(@NonNull final File thumbFile) {
        boolean cropFrameWholeImage = mPrefs.getBoolean(PREF_CROP_FRAME_WHOLE_IMAGE, false);

        Intent intent = new Intent(requireActivity(), CropImageActivity.class);
        intent.putExtra(CropIImage.REQUEST_KEY_IMAGE_ABSOLUTE_PATH, thumbFile.getAbsolutePath());
        intent.putExtra(CropIImage.REQUEST_KEY_SCALE, true);
        intent.putExtra(CropIImage.REQUEST_KEY_NO_FACE_DETECTION, true);
        intent.putExtra(CropIImage.REQUEST_KEY_WHOLE_IMAGE, cropFrameWholeImage);

        // Get and set the output file spec, and make sure it does not already exist.
        File cropped = this.getCroppedTempCoverFile();
        StorageUtils.deleteFile(cropped);
        intent.putExtra(CropIImage.REQUEST_KEY_OUTPUT_ABSOLUTE_PATH, cropped.getAbsolutePath());

        startActivityForResult(intent, CropImageActivity.REQUEST_CODE);
    }

    /**
     * Experimental
     */
    private void cropCoverImageExternal(@NonNull final File thumbFile) {
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
                StandardDialogs.showBriefMessage(requireActivity(), R.string.no_external_crop_app);
            } else {
                startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_EXTERNAL_CROP_IMAGE);
            }
        } finally {
            Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Exit);
        }
    }
    //</editor-fold>

    //<editor-fold desc="Cover replacement operations">

    protected void setCoverImage(final long bookId) {
        ImageUtils.fetchFileIntoImageView(mCoverView, getCoverFile(bookId), mThumbSize.normal, mThumbSize.normal, true);
    }

    protected void setCoverImage(final long bookId, final int maxWidth, final int maxHeight) {
        ImageUtils.fetchFileIntoImageView(mCoverView, getCoverFile(bookId), maxWidth, maxHeight, true);
    }

    private void getCoverFromCamera() {
        // Increment the temp counter and cleanup the temp directory
        mTempImageCounter++;
        StorageUtils.cleanupTempDirectory();
        // Get a photo
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        /*
            We don't do this because we have no reliable way to rotate a large image
            without producing memory exhaustion; Android does not include a file-based
            image rotation.

            File f = this.getCameraTempCoverFile();
            StorageUtils.deleteFile(f);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        */
        startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_IMAGE_CAPTURE);
    }

    private void addCoverFromCamera(final int requestCode, final int resultCode, @Nullable final Intent intent) {

        //noinspection ConstantConditions
        Bitmap bitmap = (Bitmap) intent.getExtras().get(CropIImage.BKEY_DATA);
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            Matrix m = new Matrix();
            m.postRotate(mPrefs.getInt(PREF_AUTOROTATE_CAMERA_IMAGES, PREF_AUTOROTATE_CAMERA_IMAGES_DEFAULT));
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

    private void getCoverFromGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)),
                UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_ACTION_GET_CONTENT);
    }

    private void addCoverFromGallery(@Nullable final Intent intent) {
        Objects.requireNonNull(intent);

        Uri selectedImageUri = intent.getData();

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
                setCoverImage(getBook().getBookId());
            } else {
                String s = getString(R.string.could_not_copy_image) + ". " + getString(R.string.if_the_problem_persists);
                StandardDialogs.showBriefMessage(requireActivity(), s);
            }
        } else {
            /* Deal with the case where the chooser returns a null intent. This seems to happen
             * when the filename is not properly understood by the choose (eg. an apostrophe in
             * the file name confuses ES File Explorer in the current version as of 23-Sep-2012. */
            StandardDialogs.showBriefMessage(requireActivity(), R.string.could_not_copy_image);
        }
    }

    private void getCoverFromAlternativeEditions() {
        Field isbnField = mFields.getField(R.id.isbn);
        String isbn = isbnField.getValue().toString();
        if (IsbnUtils.isValid(isbn)) {
            mCoverBrowser = new CoverBrowser(requireActivity(), isbn, new OnImageSelectedListener() {
                @Override
                public void onImageSelected(@NonNull final String fileSpec) {
                    if (mCoverBrowser != null) {
                        // Get the current file
                        File bookFile = getCoverFile(getBook().getBookId());
                        File newFile = new File(fileSpec);
                        // Overwrite with new file
                        StorageUtils.renameFile(newFile, bookFile);
                        // Update the ImageView with the new image
                        setCoverImage(getBook().getBookId());
                        mCoverBrowser.dismiss();
                        mCoverBrowser = null;
                    }
                }
            });
            mCoverBrowser.showEditionCovers();
        } else {
            //Snackbar.make(isbnField.getView(), R.string.editions_require_isbn, Snackbar.LENGTH_LONG).show();
            StandardDialogs.showBriefMessage(requireActivity(), R.string.editions_require_isbn);
        }
    }

    //</editor-fold>

    //<editor-fold desc="Cover File operations">

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
    //</editor-fold>
}
