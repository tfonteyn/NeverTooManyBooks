package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ImageView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.CoverBrowser.OnImageSelectedListener;
import com.eleybourn.bookcatalogue.Fields.Field;
import com.eleybourn.bookcatalogue.cropper.CropCropImage;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.entities.BookData;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class for creating activities containing book details.
 * Here we define common method for all children: database and background initializing,
 * initializing fields and display metrics and other common tasks.
 *
 * Basically used by {@link BookDetailsFragment} and {@link EditBookFieldsFragment}
 *
 * @author n.silin
 */
public abstract class BookDetailsAbstractFragment extends EditBookAbstractFragment {

    private static final String BKEY_IMAGE_PATH = "image-path";
    private static final String BKEY_SCALE = "scale";
    private static final String BKEY_WHOLE_IMAGE = "whole-image";
    private static final String BKEY_OUTPUT = "output";
    private static final String BKEY_CROP = "crop";
    private static final String BKEY_NO_FACE_DETECTION = "noFaceDetection";
    private static final String BKEY_RETURN_DATA = "return-data";
    private static final String BKEY_DATA = "data";

    private static final String THUMBNAIL = "thumbnail";

    /** Counter used to prevent images being reused accidentally*/
    private static int mTempImageCounter = 0;

    /**
     * Listener for creating context menu for book thumbnail.
     */
    private final OnCreateContextMenuListener mCreateBookThumbContextMenuListener = new OnCreateContextMenuListener() {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            MenuItem delete = menu.add(Menu.NONE, R.id.MENU_DELETE_THUMB, 0, R.string.menu_delete_thumb);
            delete.setIcon(R.drawable.ic_mode_edit);

            // Replace thumbnail
            SubMenu replaceSubmenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_REPLACE_THUMB, 2, R.string.menu_replace_thumb);
            replaceSubmenu.setIcon(R.drawable.ic_find_replace);

            replaceSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_FROM_CAMERA, 1, R.string.menu_add_thumb_photo)
                    .setIcon(R.drawable.ic_add_a_photo);
            replaceSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_FROM_GALLERY, 2, R.string.menu_add_thumb_gallery)
                    .setIcon(R.drawable.ic_image);
            replaceSubmenu.add(Menu.NONE, R.id.MENU_ADD_THUMB_ALT_EDITIONS, 3, R.string.menu_thumb_alt_editions)
                    .setIcon(R.drawable.ic_find_replace);

            // Rotate thumbnail
            SubMenu submenu = menu.addSubMenu(Menu.NONE, R.id.SUBMENU_ROTATE_THUMB, 3, R.string.menu_rotate_thumb);
            submenu.setIcon(R.drawable.ic_rotate_right);

            submenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_CW, 1, R.string.menu_rotate_thumb_cw)
                    .setIcon(R.drawable.ic_rotate_right);
            submenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_CCW, 2, R.string.menu_rotate_thumb_ccw)
                    .setIcon(R.drawable.ic_rotate_left);
            submenu.add(Menu.NONE, R.id.MENU_ROTATE_THUMB_180, 3, R.string.menu_rotate_thumb_180)
                    .setIcon(R.drawable.ic_swap_vert);
            menu.add(Menu.NONE, R.id.MENU_CROP_THUMB, 4, R.string.menu_crop_thumb)
                    .setIcon(R.drawable.ic_crop);
        }
    };

    protected ImageUtils.ThumbSize mThumper;
    private CoverBrowser mCoverBrowser = null;

    /**
     * Handler to process a cover selected from the CoverBrowser.
     */
    private final OnImageSelectedListener mOnImageSelectedListener = new OnImageSelectedListener() {
        @Override
        public void onImageSelected(@NonNull final String fileSpec) {
            if (mCoverBrowser != null) {
                // Get the current file
                File bookFile = getCoverFile(mEditManager.getBookData().getBookId());
                File newFile = new File(fileSpec);
                // Overwrite with new file
                StorageUtils.renameFile(newFile, bookFile);
                // update current activity
                setCoverImage();
            }
            if (mCoverBrowser != null) {
                mCoverBrowser.dismiss();
            }
            mCoverBrowser = null;
        }
    };

    /** Used to display a hint if user rotates a camera image */
    private boolean mGotCameraImage = false;

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent intent) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode);
        try {
            super.onActivityResult(requestCode, resultCode, intent);
            switch (requestCode) {
                case UniqueId.ACTIVITY_REQUEST_CODE_ADD_THUMB_FROM_CAMERA:
                    if (resultCode == Activity.RESULT_OK && intent != null && intent.getExtras() != null) {
                        addFromCamera(requestCode, resultCode, intent);
                    }
                    return;

                case UniqueId.ACTIVITY_REQUEST_CODE_ADD_THUMB_FROM_GALLERY:
                    if (resultCode == Activity.RESULT_OK) {
                        addFromGallery(intent);
                    }
                    return;

                case UniqueId.ACTIVITY_REQUEST_CODE_CROP_RESULT_EXTERNAL: {
                    File thumbFile = getCoverFile(mEditManager.getBookData().getBookId());
                    File cropped = this.getCroppedTempCoverFile();
                    if (resultCode == Activity.RESULT_OK) {
                        if (cropped.exists()) {
                            StorageUtils.renameFile(cropped, thumbFile);
                            // Update the ImageView with the new image
                            setCoverImage();
                        } else {
                            Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - result OK, no image file", Tracker.States.Running);
                        }
                    } else {
                        Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - bad result", Tracker.States.Running);
                        StorageUtils.deleteFile(cropped);
                    }
                    return;
                }

                case UniqueId.ACTIVITY_REQUEST_CODE_CROP_RESULT_INTERNAL: {
                    File thumbFile = getCoverFile(mEditManager.getBookData().getBookId());
                    File cropped = this.getCroppedTempCoverFile();
                    if (resultCode == Activity.RESULT_OK) {
                        if (cropped.exists()) {
                            StorageUtils.renameFile(cropped, thumbFile);
                            // Update the ImageView with the new image
                            setCoverImage();
                        }
                    }
                    return;
                }
                default:
                    Logger.logError(new RuntimeException("unknown result code"));
            }
        } finally {
            Tracker.exitOnActivityResult(this, requestCode, resultCode);
        }
    }

    private void addFromGallery(@Nullable final Intent intent) {
        @SuppressWarnings("ConstantConditions")
        Uri selectedImageUri = intent.getData();

        if (selectedImageUri != null) {
            boolean imageOk = false;
            // If no 'content' scheme, then use the content resolver.
            try {
                InputStream in = getContext().getContentResolver().openInputStream(selectedImageUri);
                imageOk = StorageUtils.saveInputStreamToFile(in, getCoverFile(mEditManager.getBookData().getBookId()));
            } catch (FileNotFoundException e) {
                Logger.logError(e, "Unable to copy content to file");
            }
            if (imageOk) {
                // Update the ImageView with the new image
                setCoverImage();
            } else {
                String s = getResources().getString(R.string.could_not_copy_image) + ". " + getResources().getString(R.string.if_the_problem_persists);
                StandardDialogs.showQuickNotice(this.getActivity(), s);
            }
        } else {
            /* Deal with the case where the chooser returns a null intent. This seems to happen
             * when the filename is not properly understood by the choose (eg. an apostrophe in
             * the file name confuses ES File Explorer in the current version as of 23-Sep-2012. */
            StandardDialogs.showQuickNotice(this.getActivity(), R.string.could_not_copy_image);
        }
    }

    private void addFromCamera(final int requestCode, final int resultCode, @NonNull final Intent intent) {
        Bitmap bitmap = (Bitmap) intent.getExtras().get(BKEY_DATA);
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            Matrix m = new Matrix();
            m.postRotate(BCPreferences.getAutoRotateCameraImagesInDegrees());
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);

            File cameraFile = getCameraTempCoverFile();
            FileOutputStream out;
            // Create a file to copy the thumbnail into
            try {
                out = new FileOutputStream(cameraFile.getAbsoluteFile());
            } catch (FileNotFoundException e) {
                Logger.logError(e);
                return;
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

            cropCoverImage(cameraFile);
            mGotCameraImage = true;
        } else {
            Tracker.handleEvent(this, "onActivityResult(" + requestCode + "," + resultCode + ") - camera image empty", Tracker.States.Running);
        }
    }

    /* Note that you should use setContentView() method in descendant before running this.
     */
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // See how big the display is and use that to set bitmap sizes
        mThumper = ImageUtils.getThumbSizes(getActivity());

        initFields();

        //Set zooming by default on clicking on image
        getView().findViewById(R.id.image).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                long bookId = mEditManager.getBookData().getBookId();
                ImageUtils.showZoomedThumb(getActivity(), getCoverFile(bookId));
            }
        });
    }

    @Override
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

    @Override
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        Tracker.exitOnResume(this);
    }

    @Override
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        mDb.close();
        Tracker.exitOnDestroy(this);
    }

    @Override
    public boolean onContextItemSelected(@NonNull final MenuItem item) {
        Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Enter);

        try {
            ImageView thumbView = getView().findViewById(R.id.image);
            File thumbFile = getCoverFile(mEditManager.getBookData().getBookId());

            switch (item.getItemId()) {
                case R.id.MENU_DELETE_THUMB:
                    deleteThumbnail();
                    ImageUtils.fetchFileIntoImageView(thumbView, thumbFile, mThumper.normal, mThumper.normal, true);
                    return true;

                case R.id.SUBMENU_ROTATE_THUMB:
                    // Just a submenu; skip, but display a hint if user is rotating a camera image
                    if (mGotCameraImage) {
                        HintManager.displayHint(getActivity(), R.string.hint_autorotate_camera_images, null);
                        mGotCameraImage = false;
                    }
                    return true;

                case R.id.MENU_ROTATE_THUMB_CW:
                    rotateThumbnail(90);
                    ImageUtils.fetchFileIntoImageView(thumbView, thumbFile, mThumper.normal, mThumper.normal, true);
                    return true;

                case R.id.MENU_ROTATE_THUMB_CCW:
                    rotateThumbnail(-90);
                    ImageUtils.fetchFileIntoImageView(thumbView, thumbFile, mThumper.normal, mThumper.normal, true);
                    return true;

                case R.id.MENU_ROTATE_THUMB_180:
                    rotateThumbnail(180);
                    ImageUtils.fetchFileIntoImageView(thumbView, thumbFile, mThumper.normal, mThumper.normal, true);
                    return true;

                case R.id.MENU_ADD_THUMB_FROM_CAMERA:
                    // Increment the temp counter and cleanup the temp directory
                    mTempImageCounter++;
                    StorageUtils.cleanupTempDirectory();
                    // Get a photo
                    Intent pIntent = new Intent("android.media.action.IMAGE_CAPTURE");
//				We don't do this because we have no reliable way to rotate a large image
//				without producing memory exhaustion; Android does not include a file-based
//				image rotation.
//				File f = this.getCameraTempCoverFile();
//	            StorageUtils.deleteFile(f);
//				pIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                    startActivityForResult(pIntent, UniqueId.ACTIVITY_REQUEST_CODE_ADD_THUMB_FROM_CAMERA);
                    return true;

                case R.id.MENU_ADD_THUMB_FROM_GALLERY:
                    Intent gIntent = new Intent();
                    gIntent.setType("image/*");
                    gIntent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(Intent.createChooser(gIntent, getResources().getString(R.string.select_picture)),
                            UniqueId.ACTIVITY_REQUEST_CODE_ADD_THUMB_FROM_GALLERY);
                    return true;

                case R.id.MENU_CROP_THUMB:
                    cropCoverImage(thumbFile);
                    return true;

                case R.id.MENU_ADD_THUMB_ALT_EDITIONS:
                    Field isbnField = mFields.getField(R.id.isbn);
                    String isbn = isbnField.getValue().toString();
                    if (IsbnUtils.isValid(isbn)) {
                        mCoverBrowser = new CoverBrowser(getActivity(), isbn, mOnImageSelectedListener);
                        mCoverBrowser.showEditionCovers();
                    } else {
                        //Snackbar.make(isbnField.getView(), R.string.editions_require_isbn, Snackbar.LENGTH_LONG).show();
                        StandardDialogs.showQuickNotice(getActivity(),R.string.editions_require_isbn);
                    }
                    return true;
            }
            return super.onContextItemSelected(item);
        } finally {
            Tracker.handleEvent(this, "Context Menu Item " + item.getItemId(), Tracker.States.Exit);
        }
    }

    private void cropCoverImage(@NonNull final File thumbFile) {
        if (BCPreferences.getUseExternalImageCropper()) {
            cropCoverImageExternal(thumbFile);
        } else {
            cropCoverImageInternal(thumbFile);
        }
    }

    private void cropCoverImageInternal(@NonNull final File thumbFile) {
        Intent crop_intent = new Intent(getActivity(), CropCropImage.class);
        // here you have to pass absolute path to your file
        crop_intent.putExtra(BKEY_IMAGE_PATH, thumbFile.getAbsolutePath());
        crop_intent.putExtra(BKEY_SCALE, true);
        crop_intent.putExtra(BKEY_WHOLE_IMAGE, BCPreferences.getCropFrameWholeImage());
        // Get and set the output file spec, and make sure it does not already exist.
        File cropped = this.getCroppedTempCoverFile();
        StorageUtils.deleteFile(cropped);

        crop_intent.putExtra(BKEY_OUTPUT, cropped.getAbsolutePath());
        startActivityForResult(crop_intent, UniqueId.ACTIVITY_REQUEST_CODE_CROP_RESULT_INTERNAL);
    }

    private void cropCoverImageExternal(@NonNull final File thumbFile) {
        Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Enter);
        try {
            Intent intent = new Intent("com.android.camera.action.CROP");
            // this will open any image file
            intent.setDataAndType(Uri.fromFile(new File(thumbFile.getAbsolutePath())), "image/*");
            //TODO: does not seem to be used... find out what is it/was for and remove.
            intent.putExtra(BKEY_CROP, "true");

//          // This defines the aspect ratio
//			intent.putExtra("aspectX", 1);
//			intent.putExtra("aspectY", 1);
//          // This defines the output bitmap size
//			intent.putExtra("outputX", 3264);
//			intent.putExtra("outputY", 2448);
//			intent.putExtra("outputX", mThumbZoomSize*2);
//			intent.putExtra("outputY", mThumbZoomSize*2);

            intent.putExtra(BKEY_SCALE, true);
            intent.putExtra(BKEY_NO_FACE_DETECTION, true);
            // True to return a Bitmap, false to directly save the cropped image
            intent.putExtra(BKEY_RETURN_DATA, false);
            // Save output image in uri
            File cropped = this.getCroppedTempCoverFile();
            StorageUtils.deleteFile(cropped);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(cropped.getAbsolutePath())));

            List<ResolveInfo> list = getActivity().getPackageManager().queryIntentActivities(intent, 0);
            int size = list.size();
            if (size == 0) {
                StandardDialogs.showQuickNotice(getActivity(), R.string.no_external_crop_app);
            } else {
                startActivityForResult(intent, UniqueId.ACTIVITY_REQUEST_CODE_CROP_RESULT_EXTERNAL);
            }
        } finally {
            Tracker.handleEvent(this, "cropCoverImageExternal", Tracker.States.Exit);
        }
    }

    /**
     * Delete the provided thumbnail
     */
    private void deleteThumbnail() {
        try {
            File thumbFile = getCoverFile(mEditManager.getBookData().getBookId());
            StorageUtils.deleteFile(thumbFile);
        } catch (Exception e) {
            Logger.logError(e);
        }
        invalidateCachedThumbnail();
    }

    /**
     * Get the File object for the cover of the book we are editing.
     * If the book is new (0), return the standard temp file.
     */
    private File getCoverFile(final long bookId) {
        if (bookId == 0) {
            return StorageUtils.getTempCoverFile();
        } else {
            return StorageUtils.getCoverFile(mDb.getBookUuid(bookId));
        }
    }

    /**
     * Get a temp file for camera images
     */
    private File getCameraTempCoverFile() {
        return StorageUtils.getTempCoverFile("camera", "" + mTempImageCounter);
    }

    /**
     * Get a temp file for cropping output
     */
    private File getCroppedTempCoverFile() {
        return StorageUtils.getTempCoverFile("cropped", "" + mTempImageCounter);
    }

    /**
     * Populate Author field
     * If there is no data shows "Set author..."
     */
    protected void populateAuthorListField() {
        String newText = mEditManager.getBookData().getAuthorTextShort();
        if (newText == null || newText.isEmpty()) {
            newText = getResources().getString(R.string.set_authors);
        }
        mFields.getField(R.id.author).setValue(newText);
    }

    /**
     * Populate Series field
     * If there is no data shows "Set series..."
     */
    protected void populateSeriesListField() {
        String newText;
        ArrayList<Series> list = mEditManager.getBookData().getSeries();
        if (list.size() == 0) {
            newText = getResources().getString(R.string.set_series);
        } else {
            boolean trimmed = Series.pruneSeriesList(list);
            trimmed |= Utils.pruneList(mDb, list);
            if (trimmed) {
                mEditManager.getBookData().setSeriesList(list);
            }
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += " " + getResources().getString(R.string.and_others);
            }
        }
        mFields.getField(R.id.series).setValue(newText);
    }

    /**
     * Rotate the thumbnail
     *
     * @param angle by a specified amount
     */
    private void rotateThumbnail(final long angle) {
        boolean retry = true;
        while (retry) {
            try {
                File thumbFile = getCoverFile(mEditManager.getBookData().getBookId());

                Bitmap bitmap = ImageUtils.fetchFileIntoImageView(null, thumbFile,
                        mThumper.zoomed * 2, mThumper.zoomed * 2, true);
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
                    Logger.logError(e);
                    return;
                }
                rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outFos);
                rotatedBitmap.recycle();
            } catch (java.lang.OutOfMemoryError e) {
                if (retry) {
                    System.gc();
                } else {
                    throw new RuntimeException(e);
                }
            }
            retry = false;
        }
    }

    /**
     * Ensure that the cached thumbnails for this book are deleted (if present)
     */
    private void invalidateCachedThumbnail() {
        final long bookId = mEditManager.getBookData().getBookId();
        if (bookId != 0) {
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(getContext())) {
                coversDbHelper.deleteBookCover(mDb.getBookUuid(bookId));
            } catch (Exception e) {
                Logger.logError(e, "Error cleaning up cached cover images");
            }
        }
    }

    /**
     * Add all book fields with corresponding validators.
     */
    private void initFields() {
        /* Title has some post-processing on the text, to move leading 'A', 'The' etc to the end.
         * While we could do it in a formatter, it it not really a display-oriented function and
         * is handled in pre-processing in the database layer since it also needs to be applied
         * to imported record etc.
         */
        mFields.add(R.id.title, UniqueId.KEY_TITLE, null);

        /* Anthology needs special handling, and we use a formatter to do this. If the original
         * value was 0 or 1, then setting/clearing it here should just set the new value to 0 or 1.
         * However...if if the original value was 2, then we want setting/clearing to alternate
         * between 2 and 0, not 1 and 0.
         * So, despite it being a checkbox, we use an integerValidator and use a special formatter.
         * We also store it in the tag field so that it is automatically serialized with the
         * activity.
         */
        if (getView().findViewById(R.id.anthology) != null) {
            mFields.add(R.id.anthology, BookData.IS_ANTHOLOGY, null);
        }

        if (getView().findViewById(R.id.publisher) != null) {
            mFields.add(R.id.publisher, UniqueId.KEY_BOOK_PUBLISHER, null);
        }

        if (getView().findViewById(R.id.date_published) != null) {
            mFields.add(R.id.date_published, UniqueId.KEY_BOOK_DATE_PUBLISHED, UniqueId.KEY_BOOK_DATE_PUBLISHED,
                    null, new Fields.DateFieldFormatter());
        }

        mFields.add(R.id.description, UniqueId.KEY_DESCRIPTION, null).setShowHtml(true);
        mFields.add(R.id.bookshelf, UniqueId.BKEY_BOOKSHELF_TEXT, null).doNoFetch = true; // Output-only field
        mFields.add(R.id.image, "", THUMBNAIL, null);
        mFields.getField(R.id.image).getView().setOnCreateContextMenuListener(mCreateBookThumbContextMenuListener);

        mFields.add(R.id.author, "", UniqueId.KEY_AUTHOR_FORMATTED, null);
        mFields.add(R.id.isbn, UniqueId.KEY_ISBN, null);
        mFields.add(R.id.first_publication, UniqueId.KEY_FIRST_PUBLICATION, UniqueId.KEY_FIRST_PUBLICATION, null);
        mFields.add(R.id.series, UniqueId.KEY_SERIES_NAME, UniqueId.KEY_SERIES_NAME, null);
        mFields.add(R.id.list_price, UniqueId.KEY_BOOK_LIST_PRICE, null);
        mFields.add(R.id.pages, UniqueId.KEY_BOOK_PAGES, null);
        mFields.add(R.id.genre, UniqueId.KEY_BOOK_GENRE, null);
        mFields.add(R.id.language, UniqueId.KEY_BOOK_LANGUAGE, null);
        mFields.add(R.id.format, UniqueId.KEY_BOOK_FORMAT, null);
        mFields.add(R.id.signed, UniqueId.KEY_BOOK_SIGNED, null);
    }

    /**
     * Populate all fields (See {@link #mFields} ) except of authors and series fields with
     * data from database. To set authors and series fields use {@link #populateAuthorListField()}
     * and {@link #populateSeriesListField()} methods.
     * Data defined by its _id in db.
     */
    protected void populateFieldsFromBook(@NonNull final BookData bookData) {
        // From the database (edit)
        try {
            populateBookDetailsFields(bookData);
            setBookThumbnail(bookData.getBookId(), mThumper.normal, mThumper.normal);

        } catch (Exception e) {
            Logger.logError(e);
        }

        populateBookshelvesField(mFields, bookData);
    }

    /**
     * Inflates all fields with data from cursor and populates UI fields with it.
     * Also set thumbnail of the book.
     */
    protected void populateBookDetailsFields(@NonNull final BookData bookData) {
        //Set anthology field, which is only there on the Edit screen, but not on the 'Look'
        View ant = getView().findViewById(R.id.anthology);
        if (ant != null) {
            Integer val = bookData.getInt(BookData.IS_ANTHOLOGY);
            mFields.getField(R.id.anthology).setValue(val.toString()); // Set checked if ant != 07
        }
    }

    /**
     * Sets book thumbnail
     */
    protected void setBookThumbnail(final long bookId, final int maxWidth, final int maxHeight) {
        // Sets book thumbnail
        ImageView iv = getView().findViewById(R.id.image);
        ImageUtils.fetchFileIntoImageView(iv, getCoverFile(bookId), maxWidth, maxHeight, true);
    }

    /**
     * Gets all bookshelves for the book from database and populate corresponding field with them.
     *
     * @param fields   Fields containing book information
     * @param bookData the book
     *
     * @return true if populated, false otherwise
     */
    protected boolean populateBookshelvesField(@NonNull final Fields fields, @NonNull final BookData bookData) {
        boolean result = false;
        try {
            // Display the selected bookshelves
            Field bookshelfTextFe = fields.getField(R.id.bookshelf);
            String bookshelfText = bookData.getBookshelfText();
            bookshelfTextFe.setValue(bookshelfText);
            if (!bookshelfText.isEmpty()) {
                result = true;
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
        return result;
    }

    protected void setCoverImage() {
        ImageView iv = getView().findViewById(R.id.image);
        ImageUtils.fetchFileIntoImageView(iv, getCoverFile(mEditManager.getBookData().getBookId()), mThumper.normal, mThumper.normal, true);
        invalidateCachedThumbnail();
    }
}
