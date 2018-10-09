/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * TODO: remove face-detection, circular crop code from this and related classes.
 *
 * The activity can crop specific region of interest from an image.
 */
public class CropImageActivity extends CropMonitoredActivity {
    // private static final String TAG = "CropImage";

    private static final int NO_STORAGE_ERROR = -1;
    private static final int CANNOT_STAT_ERROR = -2;
    /** used to calculate free space on storage */
    private static final long ESTIMATED_PICTURE_SIZE = 400000L;

    public static final String ACTION_INLINE_DATA = "inline-data";
    private final Bitmap.CompressFormat COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG; // only used with mOptionSaveUri

    /** These are various options can be specified in the intent. */
    private Uri mOptionSaveUri = null;
    private int mOptionAspectX, mOptionAspectY;
    /** crop circle ? (default: rectangle) */
    private boolean mOptionCircleCrop = false;
    /** Output image size and whether we should scale the output to fit it (or just crop it). */
    private int mOptionOutputX, mOptionOutputY;
    /** Options indicating if default crop rect is whole image */
    private boolean mOptionCropWholeImage = false;
    private boolean mOptionScale;
    private boolean mOptionScaleUp = true;
    /** Disable face detection */
    private boolean mOptionNoFaceDetection = true;

    private final Handler mHandler = new Handler();
    private final CropBitmapManager.ThreadSet mDecodingThreads = new CropBitmapManager.ThreadSet();

    /** Whether the "save" button is already clicked. */
    boolean mSaving;

    CropHighlightView mCrop;
    private CropImageView mImageView;
    private Bitmap mBitmap;

    /** Whether we are wait the user to pick a face. */
    boolean mWaitingToPickFace;
    /** runnable that does the actual face detection */
    private final Runnable mRunFaceDetection = new Runnable() {
        final FaceDetector.Face[] mFaces = new FaceDetector.Face[3];
        float mScale = 1F;
        Matrix mImageMatrix;
        int mNumFaces;

        // For each face, we create a CropHighlightView for it.
        private void handleFace(FaceDetector.Face face) {
            PointF midPoint = new PointF();

            int r = ((int) (face.eyesDistance() * mScale)) * 2;
            face.getMidPoint(midPoint);
            midPoint.x *= mScale;
            midPoint.y *= mScale;

            int midX = (int) midPoint.x;
            int midY = (int) midPoint.y;

            CropHighlightView hv = new CropHighlightView(mImageView);
            int width = mBitmap.getWidth();
            int height = mBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            RectF faceRect = new RectF(midX, midY, midX, midY);
            faceRect.inset(-r, -r);
            if (faceRect.left < 0) {
                faceRect.inset(-faceRect.left, -faceRect.left);
            }

            if (faceRect.top < 0) {
                faceRect.inset(-faceRect.top, -faceRect.top);
            }

            if (faceRect.right > imageRect.right) {
                faceRect.inset(faceRect.right - imageRect.right,
                        faceRect.right - imageRect.right);
            }

            if (faceRect.bottom > imageRect.bottom) {
                faceRect.inset(faceRect.bottom - imageRect.bottom,
                        faceRect.bottom - imageRect.bottom);
            }

            hv.setup(mImageMatrix, imageRect, faceRect, mOptionCircleCrop,
                    mOptionAspectX != 0 && mOptionAspectY != 0);

            mImageView.add(hv);
        }

        // Create a default HighlightView if we found no face in the picture.
        private void makeDefault() {
            CropHighlightView hv = new CropHighlightView(mImageView);

            int width = mBitmap.getWidth();
            int height = mBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            int cropWidth;
            int cropHeight;
            if (mOptionCropWholeImage) {
                cropWidth = width;
                cropHeight = height;
            } else {
                int dv = Math.min(width, height);
                cropWidth = dv;
                cropHeight = dv;
            }

            // Even though we may be set to 'crop-whole-image', we need to obey aspect ratio if set.
            if (mOptionAspectX != 0 && mOptionAspectY != 0) {
                if (mOptionAspectX > mOptionAspectY) {
                    cropHeight = cropWidth * mOptionAspectY / mOptionAspectX;
                } else {
                    cropWidth = cropHeight * mOptionAspectX / mOptionAspectY;
                }
            }

            int x = (width - cropWidth) / 2;
            int y = (height - cropHeight) / 2;

            RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
            hv.setup(mImageMatrix, imageRect, cropRect, mOptionCircleCrop,
                    mOptionAspectX != 0 && mOptionAspectY != 0);
            mImageView.add(hv);
        }

        // Scale the image down for faster face detection.
        @Nullable
        private Bitmap prepareBitmap() {
            if (mBitmap == null) {
                return null;
            }

            // 256 pixels wide is enough.
            if (mBitmap.getWidth() > 256) {
                mScale = 256.0F / mBitmap.getWidth();
            }
            Matrix matrix = new Matrix();
            matrix.setScale(mScale, mScale);
            return Bitmap.createBitmap(mBitmap, 0, 0,
                    mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
        }

        public void run() {
            mImageMatrix = mImageView.getImageMatrix();
            Bitmap faceBitmap = prepareBitmap();

            mScale = 1.0F / mScale;
            if (faceBitmap != null && !mOptionNoFaceDetection) {
                FaceDetector detector = new FaceDetector(faceBitmap.getWidth(), faceBitmap.getHeight(), mFaces.length);
                mNumFaces = detector.findFaces(faceBitmap, mFaces);
            }

            if (faceBitmap != null && faceBitmap != mBitmap) {
                faceBitmap.recycle();
            }

            mHandler.post(new Runnable() {
                public void run() {
                    mWaitingToPickFace = mNumFaces > 1;
                    if (mNumFaces > 0) {
                        for (int i = 0; i < mNumFaces; i++) {
                            handleFace(mFaces[i]);
                        }
                    } else {
                        makeDefault();
                    }
                    mImageView.invalidate();
                    if (mImageView.mHighlightViews.size() == 1) {
                        mCrop = mImageView.mHighlightViews.get(0);
                        mCrop.setFocus(true);
                    }

                    if (mNumFaces > 1) {
                        String msg = "Multi face crop help not available.";
                        StandardDialogs.showQuickNotice(CropImageActivity.this, msg);
                    }
                }
            });
        }
    };
    private CropIImage mImage;

    private static void showQuickNotice(@NonNull final Activity activity) {
        int remaining = calculatePicturesRemaining();
        String noStorageText = null;

        if (remaining == NO_STORAGE_ERROR) {
            if (Environment.MEDIA_CHECKING.equals(Environment.getExternalStorageState())) {
                noStorageText = activity.getString(R.string.storage_error_preparing_card);
            } else {
                noStorageText = activity.getString(R.string.storage_error_no_card);
            }
        } else if (remaining < 1) {
            noStorageText = activity.getString(R.string.storage_error_no_space);
        }

        if (noStorageText != null) {
            StandardDialogs.showQuickNotice(activity, noStorageText);
        }
    }

    private static int calculatePicturesRemaining() {
        try {
            long remaining = StorageUtils.getFreeSpace();
            return (int) (remaining / ESTIMATED_PICTURE_SIZE);
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many pictures are remaining.
            // It might be zero but just leave it blank since we really don't know.
            return CANNOT_STAT_ERROR;
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.cropcropimage;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // Do this first to avoid 'must be first errors'
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

        mImageView = findViewById(R.id.image);

        showQuickNotice(this);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if (extras.getString(CropIImage.BKEY_CIRCLE_CROP) != null) {
                mOptionCircleCrop = true;
                mOptionAspectX = 1;
                mOptionAspectY = 1;
            }

            String imagePath = extras.getString(CropIImage.BKEY_IMAGE_PATH);
            Objects.requireNonNull(imagePath, "imagePath was null");
            mBitmap = getBitmap(imagePath);

            // Use the "output" parameter if present, otherwise overwrite existing file
            String imgUri = extras.getString(CropIImage.BKEY_OUTPUT);
            if (imgUri == null) {
                imgUri = imagePath;
            }
            mOptionSaveUri = getImageUri(imgUri);

            mOptionAspectX = extras.getInt(CropIImage.BKEY_ASPECT_X);
            mOptionAspectY = extras.getInt(CropIImage.BKEY_ASPECT_Y);
            mOptionOutputX = extras.getInt(CropIImage.BKEY_OUTPUT_X);
            mOptionOutputY = extras.getInt(CropIImage.BKEY_OUTPUT_Y);
            mOptionScale = extras.getBoolean(CropIImage.BKEY_SCALE, true);
            mOptionScaleUp = extras.getBoolean(CropIImage.BKEY_SCALE_UP_IF_NEEDED, true);
            mOptionCropWholeImage = extras.getBoolean(CropIImage.BKEY_WHOLE_IMAGE, false);
            mOptionNoFaceDetection = extras.getBoolean(CropIImage.BKEY_NO_FACE_DETECTION, true);
        }

        if (mBitmap == null) {
            finish();
            return;
        }

        // Make UI fullscreen.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        findViewById(R.id.cancel).setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                });

        findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSaveClicked();
            }
        });
        startFaceDetection();
    }

    @NonNull
    private Uri getImageUri(@NonNull final String path) {
        return Uri.fromFile(new File(path));
    }

    @Nullable
    private Bitmap getBitmap(@NonNull final String path) {
        Uri uri = getImageUri(path);
        InputStream in;
        try {
            in = getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(in);
        } catch (FileNotFoundException ignored) {
        }
        return null;
    }

    private void startFaceDetection() {
        if (isFinishing()) {
            return;
        }

        mImageView.setImageBitmapResetBase(mBitmap, true);

        CropUtil.startBackgroundJob(this, null, "Please wait\u2026",
                new Runnable() {
                    public void run() {
                        final CountDownLatch latch = new CountDownLatch(1);
                        final Bitmap b = (mImage != null) ?
                                mImage.fullSizeBitmap(CropIImage.UNCONSTRAINED, 1024 * 1024)
                                : mBitmap;
                        mHandler.post(new Runnable() {
                            public void run() {
                                if (b != mBitmap && b != null) {
                                    // Do not recycle until mBitmap has been set
                                    // to the new bitmap!
                                    Bitmap toRecycle = mBitmap;
                                    mBitmap = b;
                                    mImageView.setImageBitmapResetBase(mBitmap,
                                            true);
                                    toRecycle.recycle();
                                }
                                if (mImageView.getScale() == 1F) {
                                    mImageView.center(true, true);
                                }
                                latch.countDown();
                            }
                        });
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        mRunFaceDetection.run();
                    }
                }, mHandler);
    }

    private void onSaveClicked() {
        // TODO this code needs to change to use the decode/crop/encode single
        // step api so that we don't require that the whole (possibly large)
        // bitmap doesn't have to be read into memory
        if (mSaving) {
            return;
        }

        if (mCrop == null) {
            return;
        }

        mSaving = true;

        Rect cropRect = mCrop.getCropRect();
        int width = cropRect.width();
        int height = cropRect.height();

        // If we are circle cropping, we want alpha channel, which is the third param here.
        Bitmap croppedImage = Bitmap.createBitmap(width, height,
                mOptionCircleCrop ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        {
            Canvas canvas = new Canvas(croppedImage);
            Rect dstRect = new Rect(0, 0, width, height);
            canvas.drawBitmap(mBitmap, cropRect, dstRect, null);
        }

        if (mOptionCircleCrop) {
            // OK, so what's all this about?
            // Bitmaps are inherently rectangular but we want to return something that's basically
            // a circle. So we fill in the area around the circle with alpha. Note the all important
            // PorterDuff.Mode.CLEAR (Destination pixels covered by the source are cleared to 0)
            Canvas canvas = new Canvas(croppedImage);
            Path path = new Path();
            path.addCircle(width / 2F, height / 2F, width / 2F, Path.Direction.CW);
            canvas.clipPath(path, Region.Op.DIFFERENCE);
            canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
        }

        /* If the output is required to a specific size then SCALE or fill */
        if (mOptionOutputX != 0 && mOptionOutputY != 0) {
            if (mOptionScale) {
                /* Scale the image to the required dimensions */
                Bitmap old = croppedImage;
                croppedImage = CropUtil.transform(new Matrix(), croppedImage,
                        mOptionOutputX, mOptionOutputY, mOptionScaleUp);
                if (old != croppedImage) {
                    old.recycle();
                }
            } else {

                /*
                 * Don't SCALE the image crop it to the size requested. Create
                 * an new image with the cropped image in the center and the
                 * extra space filled.
                 */

                // Don't SCALE the image but instead fill it so it's the required dimension
                Bitmap bitmap = Bitmap.createBitmap(mOptionOutputX, mOptionOutputY, Bitmap.Config.RGB_565);
                Canvas canvas = new Canvas(bitmap);

                Rect srcRect = mCrop.getCropRect();
                Rect dstRect = new Rect(0, 0, mOptionOutputX, mOptionOutputY);

                int dx = (srcRect.width() - dstRect.width()) / 2;
                int dy = (srcRect.height() - dstRect.height()) / 2;

                /* If the srcRect is too big, use the center part of it. */
                srcRect.inset(Math.max(0, dx), Math.max(0, dy));

                /* If the dstRect is too big, use the center part of it. */
                dstRect.inset(Math.max(0, -dx), Math.max(0, -dy));

                /* Draw the cropped bitmap in the center */
                canvas.drawBitmap(mBitmap, srcRect, dstRect, null);

                /* Set the cropped bitmap as the new bitmap */
                croppedImage.recycle();
                croppedImage = bitmap;
            }
        }

        // Return the cropped image directly or save it to the specified URI.
        Bundle myExtras = getIntent().getExtras();
        if (myExtras != null && (myExtras.getParcelable(CropIImage.BKEY_DATA) != null
                || myExtras.getBoolean(CropIImage.BKEY_RETURN_DATA))) {

            Bundle extras = new Bundle();
            extras.putParcelable(CropIImage.BKEY_DATA, croppedImage);
            setResult(RESULT_OK, (new Intent()).setAction(ACTION_INLINE_DATA).putExtras(extras));
            finish();
        } else {
            final Bitmap bitmap = croppedImage;
            CropUtil.startBackgroundJob(this, null, "Saving image",
                    new Runnable() {
                        public void run() {
                            saveOutput(bitmap);
                        }
                    }, mHandler);
        }
    }

    private void saveOutput(@NonNull final Bitmap croppedImage) {
        if (mOptionSaveUri != null) {
            try (OutputStream outputStream = getContentResolver().openOutputStream(mOptionSaveUri)) {
                if (outputStream != null) {
                    croppedImage.compress(COMPRESS_FORMAT, 75, outputStream);
                }
            } catch (IOException ex) {
                // TODO: report error to caller
                Logger.logError(ex, "Error while saving image");
            }

            Bundle extras = new Bundle();
            setResult(RESULT_OK, new Intent(mOptionSaveUri.toString()).putExtras(extras));
        }

        croppedImage.recycle();
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CropBitmapManager.instance().cancelThreadDecoding(mDecodingThreads);
        // DO NOT RECYCLE HERE; will leave mBitmap unusable after a resume.
        // mBitmap.recycle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
    }

}
