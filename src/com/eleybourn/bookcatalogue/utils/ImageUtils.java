package com.eleybourn.bookcatalogue.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.GetThumbnailTask;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.ThumbnailCacheWriterTask;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.BasicDialog;
import com.eleybourn.bookcatalogue.searches.SearchManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_THUMBNAIL;

public class ImageUtils {
    /**
     * Used as: if (DEBUG && BuildConfig.DEBUG) { ... }
     */
    private static final boolean DEBUG = false;

    private ImageUtils() {
    }

    // Target size of a thumbnail in edit dialog and zoom dialog
    private static final int MAX_EDIT_THUMBNAIL_SIZE = 256;
    private static final int MAX_ZOOM_THUMBNAIL_SIZE = 1024;

    public static DisplayMetrics getDisplayMetrics(Activity activity) {
        DisplayMetrics metrics = new android.util.DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }

    /**
     *
     * @return an array with two elements: 0=EditThumbnailSize; 1=ZoomThumbnailSize
     */
     public static Integer[] getThumbSizes(Activity activity) {
        DisplayMetrics metrics = getDisplayMetrics(activity);
        Integer[] sizes = new Integer[2];
        sizes[0] = Math.min(MAX_EDIT_THUMBNAIL_SIZE, Math.max(metrics.widthPixels, metrics.heightPixels) / 3);
        sizes[1] = Math.min(MAX_ZOOM_THUMBNAIL_SIZE, Math.max(metrics.widthPixels, metrics.heightPixels));
        return sizes;
    }

    /**
     * Shrinks the image in the passed file to the specified dimensions, and places the image
     * in the passed view.
     *
     * @return The bitmap, or null
     */
    public static Bitmap fetchFileIntoImageView(File file, ImageView destView, int maxWidth, int maxHeight, boolean exact) {
        // Get the file, if it exists. Otherwise set 'help' icon and exit.
        if (!file.exists()) {
            if (destView != null)
                destView.setImageResource(android.R.drawable.ic_menu_help);
            return null;
        }

        return shrinkFileIntoImageView(destView, file.getPath(), maxWidth, maxHeight, exact);
    }

    /**
     * Called in the UI thread, will either use a cached cover OR start a background task to create and load it.
     * <p>
     * If a cached image is used a background task is still started to check the file date vs the cache date. If the
     * cached image date is < the file, it is rebuilt.
     *
     * @param destView        View to populate
     * @param maxWidth        Max width of resulting image
     * @param maxHeight       Max height of resulting image
     * @param exact           Whether to fit dimensions exactly
     * @param hash            ID of book to retrieve.
     * @param checkCache      Indicates if cache should be checked for this cover
     * @param allowBackground Indicates if request can be put in background task.
     *
     * @return Bitmap (if cached) or null (if done in background)
     */
    @Nullable
    public static Bitmap fetchBookCoverIntoImageView(final ImageView destView,
                                                     int maxWidth, int maxHeight,
                                                     final boolean exact,
                                                     final String hash,
                                                     final boolean checkCache, final boolean allowBackground) {

        //* Get the original file so we can use the modification date, path etc */
        File coverFile = fetchThumbnailByUuid(hash);
        Bitmap bm = null;
        boolean cacheWasChecked = false;

        /* If we want to check the cache, AND we don't have cache building happening, then check it. */
        if (checkCache && !GetThumbnailTask.hasActiveTasks() && !ThumbnailCacheWriterTask.hasActiveTasks()) {
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(destView.getContext())) {
                bm = coversDbHelper.fetchCachedImageIntoImageView(coverFile, destView, hash, maxWidth, maxHeight);
            }
            cacheWasChecked = true;
        }

        if (bm != null)
            return bm;

        // If we get here, the image is not in the cache but the original exists. See if we can queue it.
        if (allowBackground) {
            destView.setImageBitmap(null);
            GetThumbnailTask.getThumbnail(destView.getContext(), hash, destView, maxWidth, maxHeight, cacheWasChecked);
            return null;
        }

        // File is not in cache, original exists, we are in the background task (or not allowed to queue request)
        return shrinkFileIntoImageView(destView, coverFile.getPath(), maxWidth, maxHeight, exact);
    }

    /**
     * Shrinks the passed image file spec into the specified dimensions, and returns the bitmap. If the view
     * is non-null, the image is also placed in the view.
     */
    @SuppressWarnings("WeakerAccess")
    public static Bitmap shrinkFileIntoImageView(ImageView destView, String fileSpec, int maxWidth, int maxHeight, boolean exact) {
        Bitmap bm;

        // Read the file to get file size
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        if (new File(fileSpec).exists()) {
            BitmapFactory.decodeFile(fileSpec, opt);
        }

        // If no size info, or a single pixel, assume file bad and set the 'alert' icon
        if (opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight == 1 && opt.outWidth == 1)) {
            if (destView != null)
                destView.setImageResource(android.R.drawable.ic_dialog_alert);
            return null;
        }

        // Next time we don't just want the bounds, we want the file itself
        opt.inJustDecodeBounds = false;

        // Work out how to scale the file to fit in required box
        float widthRatio = (float) maxWidth / opt.outWidth;
        float heightRatio = (float) maxHeight / opt.outHeight;

        // Work out scale so that it fit exactly
        float ratio = widthRatio < heightRatio ? widthRatio : heightRatio;

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        int idealSampleSize = (int) Math.ceil(1 / ratio); // This is the sample size we want to use
        // Get the nearest *bigger* power of 2.
        int samplePow2 = (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

        if (DEBUG && BuildConfig.DEBUG) {
            System.out.println("IU.shrinkFileIntoImageView:\n" +
                    " filename = " + fileSpec + "\n" +
                    "  exact       = " + exact + "\n" +
                    "  maxWidth    = " + maxWidth + ", opt.outWidth = " + opt.outWidth + ", widthRatio   = " + widthRatio + "\n" +
                    "  maxHeight   = " + maxHeight + ", opt.outHeight= " + opt.outHeight + ",  heightRatio = " + heightRatio + "\n" +
                    "  ratio            = " + ratio + "\n" +
                    "  idealSampleSize  = " + idealSampleSize + "\n" +
                    "  samplePow2       = " + samplePow2);
        }
        try {
            if (exact) {
                // Create one bigger than needed and scale it; this is an attempt to improve quality.
                opt.inSampleSize = samplePow2 / 2;
                if (opt.inSampleSize < 1) {
                    opt.inSampleSize = 1;
                }

                Bitmap tmpBm = BitmapFactory.decodeFile(fileSpec, opt);
                if (tmpBm == null) {
                    // We ran out of memory, most likely
                    // TODO: Need a way to try loading images after GC(), or something. Otherwise, covers in cover browser wil stay blank.
                    Logger.logError(new RuntimeException("Unexpectedly failed to decode bitmap; memory exhausted?"));
                    return null;
                }

                android.graphics.Matrix matrix = new android.graphics.Matrix();
                // Fixup ratio based on new sample size and scale it.
                ratio = ratio / (1.0f / opt.inSampleSize);
                matrix.postScale(ratio, ratio);
                bm = Bitmap.createBitmap(tmpBm, 0, 0, opt.outWidth, opt.outHeight, matrix, true);
                // Recycle if original was not returned
                if (bm != tmpBm) {
                    tmpBm.recycle();
                }
            } else {
                // Use a scale that will make image *no larger than* the desired size
                if (ratio < 1.0f) {
                    opt.inSampleSize = samplePow2;
                }
                bm = BitmapFactory.decodeFile(fileSpec, opt);
            }
        } catch (OutOfMemoryError e) {
            return null;
        }
        if (DEBUG && BuildConfig.DEBUG) {
            System.out.println("\n" +
                    "bm.width = " + bm.getWidth() + "\n" +
                    "bm.height = " + bm.getHeight() + "\n"
            );
        }

        // Set ImageView and return bitmap
        if (destView != null) {
            destView.setImageBitmap(bm);
        }

        return bm;
    }

    /**
     * return the thumbnail (as a File object) for the given hash
     *
     * @param uuid The uuid of the book
     *
     * @return The File object
     */
    public static File fetchThumbnailByUuid(String uuid) {
        return fetchThumbnailByUuid(uuid, "");
    }

    /**
     * return the thumbnail (as a File object) for the given id.
     *
     * @param prefix Optional on the file name.
     * @param suffix Optional on the file name.
     *
     * @return The File object
     */
    public static File fetchThumbnailByName(String prefix, String suffix) {
        if (suffix == null)
            suffix = "";

        if (prefix == null || prefix.isEmpty()) {
            return getTempThumbnail(suffix);
        } else {
            File file = StorageUtils.getFile(prefix + suffix + ".jpg");
            if (!file.exists()) {
                File png = StorageUtils.getFile(prefix + suffix + ".png");
                if (png.exists())
                    return png;
                else
                    return file;
            } else {
                return file;
            }
        }
    }

    /**
     * return the thumbnail (as a File object) for the given id.
     *
     * @param uuid   The id of the book
     * @param suffix Optionally use a suffix on the file name.
     *
     * @return The File object
     */
    private static File fetchThumbnailByUuid(String uuid, @SuppressWarnings("SameParameterValue") String suffix) {
        return fetchThumbnailByName(uuid, suffix);
    }

    /**
     * Given a URL, get an image and save to a file, optionally appending a suffix to the file.
     *
     * @param urlText        Image file URL
     * @param filenameSuffix Suffix to add
     *
     * @return Downloaded fileSpec
     */
    public static String saveThumbnailFromUrl(String urlText, String filenameSuffix) {
        // Get the URL
        URL u;
        try {
            u = new URL(urlText);
        } catch (MalformedURLException e) {
            Logger.logError(e);
            return "";
        }
        // Turn the URL into an InputStream
        InputStream in;
        try {
            HttpGet httpRequest;

            httpRequest = new HttpGet(u.toURI());

            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response = httpclient.execute(httpRequest);

            HttpEntity entity = response.getEntity();
            BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
            in = bufHttpEntity.getContent();

            // The default URL fetcher does not cope well with pages that have not content
            // header (including goodreads images!). So use the more advanced one.
            //c = (HttpURLConnection) u.openConnection();
            //c.setConnectTimeout(30000);
            //c.setRequestMethod("GET");
            //c.setDoOutput(true);
            //c.connect();
            //in = c.getInputStream();
        } catch (IOException | URISyntaxException e) {
            Logger.logError(e);
            return "";
        }

        // Get the output file
        File file = getTempThumbnail(filenameSuffix);
        // Save to file
        Utils.saveInputToFile(in, file);
        // Return new file path
        return file.getAbsolutePath();
    }

    /**
     * Get the 'standard' temp file name for new books
     */
    public static File getTempThumbnail() {
        return getTempThumbnail("");
    }

    /**
     * Get the 'standard' temp file name for new books, including a suffix
     */
    @SuppressWarnings("WeakerAccess")
    public static File getTempThumbnail(String suffix) {
        return StorageUtils.getFile("tmp" + suffix + ".jpg");
    }

    /**
     * Given byte array that represents an image (jpg, png etc), return as a bitmap.
     *
     * @param bytes Raw byte data
     *
     * @return bitmap
     */
    public static Bitmap getBitmapFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return null;

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, new BitmapFactory.Options());

        if (BuildConfig.DEBUG) {
            System.out.println("Array " + bytes.length + " bytes, bitmap " + bitmap.getHeight() + "x" + bitmap.getWidth());
        }
        return bitmap;
    }

    /**
     * If there is a '__thumbnails' key, pick the largest image, rename it
     * and delete the others. Finally, remove the key.
     */
    public static void cleanupThumbnails(Bundle result) {
        if (result != null && result.containsKey(SearchManager.BKEY_THUMBNAIL_SEARCHES)) {
            // Parse the list
            String s = result.getString(SearchManager.BKEY_THUMBNAIL_SEARCHES);
            if (s != null) {
                ArrayList<String> files = ArrayUtils.decodeList('|', s);

                long best = -1;
                int bestFile = -1;

                // Just read the image files to get file size
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inJustDecodeBounds = true;

                // Scan, finding biggest
                for (int i = 0; i < files.size(); i++) {
                    String filespec = files.get(i);
                    File file = new File(filespec);
                    if (file.exists()) {
                        BitmapFactory.decodeFile(filespec, opt);
                        // If no size info, assume file bad and skip
                        if (opt.outHeight > 0 && opt.outWidth > 0) {
                            long size = opt.outHeight * opt.outWidth;
                            if (size > best) {
                                best = size;
                                bestFile = i;
                            }
                        }
                    }
                }

                // Delete all but the best one. Note there *may* be no best one,
                // so all would be deleted. We do this first in case the list
                // contains a file with the same name as the target of our
                // rename.
                for (int i = 0; i < files.size(); i++) {
                    if (i != bestFile) {
                        File file = new File(files.get(i));
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
                // Get the best file (if present) and rename it.
                if (bestFile >= 0) {
                    File file = new File(files.get(bestFile));
                    //noinspection ResultOfMethodCallIgnored
                    file.renameTo(getTempThumbnail());
                }
                // Finally, cleanup the data
                result.remove(SearchManager.BKEY_THUMBNAIL_SEARCHES);
                result.putBoolean(KEY_THUMBNAIL, true);
            }
        }
    }

    /**
     * Given a URL, get an image and return as a byte array.
     *
     * @param urlText Image file URL
     *
     * @return Downloaded byte[]
     */
    public static byte[] getBytesFromUrl(String urlText) {
        URL u;
        try {
            u = new URL(urlText);
        } catch (MalformedURLException e) {
            Logger.logError(e);
            return null;
        }
        // Request it from the network
        HttpURLConnection c;
        InputStream in;
        try {
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(30000);
            c.setReadTimeout(30000);
            c.setRequestMethod("GET");
            c.setDoInput(true);
            c.setUseCaches(false);
            c.connect();
            in = c.getInputStream();
            if (c.getResponseCode() >= 300) {
                Logger.logError(new RuntimeException("URL lookup failed: " + c.getResponseCode() + " " + c.getResponseMessage() + ", URL: " + u.toString()));
                return null;
            }
        } catch (IOException e) {
            Logger.logError(e);
            return null;
        }

        // Save the output to a byte output stream
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[65536];
            int len1;
            while ((len1 = in.read(buffer)) >= 0) {
                f.write(buffer, 0, len1);
            }
            f.close();
        } catch (IOException e) {
            Logger.logError(e);
            return null;
        }
        return f.toByteArray();
    }

    /**
     * Show zoomed thumbnail in dialog. Closed by click on image area.
     */
    public static void showZoomedThumb(Activity activity, File thumbFile) {

        Integer[] sizes = getThumbSizes(activity);
        Integer thumbZoomSize = sizes[0];

        // Check if we have a file and/or it is valid
        if (thumbFile == null || !thumbFile.exists()) {
            Toast.makeText(activity, R.string.cover_not_set, Toast.LENGTH_SHORT).show();
        } else {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), opt);

            // If no size info, assume file bad and return appropriate icon
            if (opt.outHeight <= 0 || opt.outWidth <= 0) {
                Toast.makeText(activity, R.string.cover_corrupt, Toast.LENGTH_LONG).show();
            } else {
                final Dialog dialog = new BasicDialog(activity, false);
                dialog.setContentView(R.layout.dialog_zoom_thumb);

                ImageView cover = new ImageView(activity);
                fetchFileIntoImageView(thumbFile, cover, thumbZoomSize, thumbZoomSize, true);
                cover.setAdjustViewBounds(true);
                cover.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.addContentView(cover, lp);
                dialog.show();
            }
        }
    }

//    /**
//     * Given a URL, get an image and return as a bitmap.
//     *
//     * @param urlText Image file URL
//     *
//     * @return Downloaded bitmap
//     */
//    public static Bitmap getBitmapFromUrl(String urlText) {
//        return getBitmapFromBytes(getBytesFromUrl(urlText));
//    }

//    /**
//     * return the thumbnail (as a File object) for the given id
//     *
//     * @param id The id of the book
//     *
//     * @return The File object
//     */
//    public static File fetchThumbnailById(long id) {
//        return fetchThumbnailById(id, "");
//    }
//
//    /*
//     * return the thumbnail (as a File object) for the given id. Optionally use a suffix
//     * on the file name.
//     *
//     * @param id The id of the book
//     * @return The File object
//     */
//    @SuppressWarnings("WeakerAccess")
//    public static File fetchThumbnailById(long id, String suffix) {
//        return fetchThumbnailByName(Long.toString(id), suffix);
//    }

//    /**
//     * This function will load the thumbnail bitmap with a guaranteed maximum size; it
//     * prevents OutOfMemory exceptions on large files and reduces memory usage in lists.
//     * It can also scale images to the exact requested size.
//     *
//     * @param uuid      The id of the book
//     * @param destView  The ImageView to load with the bitmap or an appropriate icon
//     * @param maxWidth  Maximum desired width of the image
//     * @param maxHeight Maximum desired height of the image
//     * @param exact     if true, the image will be propertionally scaled to fit bbox.
//     *
//     * @return The scaled bitmap for the file, or null if no file or bad file.
//     */
//    public static Bitmap fetchThumbnailIntoImageView(String uuid, ImageView destView, int maxWidth, int maxHeight, boolean exact) {
//        // Get the file, if it exists. Otherwise set 'help' icon and exit.
//        Bitmap image = null;
//        try {
//            File file = fetchThumbnailByUuid(uuid);
//            image = fetchFileIntoImageView(file, destView, maxWidth, maxHeight, exact);
//        } catch (IllegalArgumentException e) {
//            Logger.logError(e);
//        }
//        return image;
//    }
}
