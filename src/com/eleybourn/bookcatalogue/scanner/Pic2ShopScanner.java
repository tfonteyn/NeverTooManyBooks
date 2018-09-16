package com.eleybourn.bookcatalogue.scanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * Based on the pic2shop client code at github, this object will start pic2shop and extract the data
 * from the resulting intent when the activity completes.
 *
 * It also has a static method to check if the intent is present.
 *
 * @author pjw
 */
public class Pic2ShopScanner implements Scanner {
    public static final String ACTION = Free.PACKAGE + ".SCAN";
    /**
     * When a barcode is read, pic2shop returns Activity.RESULT_OK in
     * onActivityResult() of the activity which requested the scan using
     * startActivityForResult(). The read barcode can be retrieved with
     * intent.getStringExtra(BARCODE).
     * If the user exits pic2shop by pressing Back before a barcode is read, the
     * result code will be Activity.RESULT_CANCELED in onActivityResult().
     */
    private static final String BARCODE = "BARCODE";
    private static final String BARCODE_FORMAT = "EAN13";
    private static final String FORMATS = "formats";
    private static final String FORMAT = "format";

    /**
     * Check if we have a valid intent available.
     *
     * @return true if present
     */
    public static boolean isIntentAvailable() {
        return isFreeScannerAppInstalled(BookCatalogueApp.getAppContext())
                || isProScannerAppInstalled(BookCatalogueApp.getAppContext());
    }

    private static boolean isIntentAvailable(@NonNull final Context ctx, @NonNull final String action) {
        Intent test = new Intent(action);
        return ctx.getPackageManager().resolveActivity(test, 0) != null;
    }

    private static boolean isFreeScannerAppInstalled(@NonNull final Context ctx) {
        return isIntentAvailable(ctx, ACTION);
    }

    private static boolean isProScannerAppInstalled(@NonNull final Context ctx) {
        return isIntentAvailable(ctx, ACTION);
    }

    /**
     * Start the activity with the passed request code.
     *
     * Note that we always send an intent; the caller should have checked that
     * one of the intents is valid, or catch the resulting errors.
     */
    @Override
    public void startActivityForResult(@NonNull final  Activity a, final int requestCode) {
        Intent i;
        if (isFreeScannerAppInstalled(a)) {
            i = new Intent(ACTION);
            //i.putExtra(Pro.FORMATS, BARCODE_FORMAT);
        } else {
            i = new Intent(ACTION);
            i.putExtra(FORMATS, BARCODE_FORMAT);
        }
        a.startActivityForResult(i, requestCode);
    }

    /**
     * Extract the barcode from the result
     */
    @Override
    public String getBarcode(@NonNull final Intent intent) {
        String barcode = intent.getStringExtra(BARCODE);
        String barcodeFormat = intent.getStringExtra(FORMAT);
        if (barcodeFormat != null && !barcodeFormat.equalsIgnoreCase(BARCODE_FORMAT)) {
            throw new RuntimeException("Unexpected format for barcode: " + barcodeFormat);
        }
        return barcode;
    }

    public interface Free {
        String PACKAGE = "com.visionsmarts.pic2shop";
    }

//    public interface Pro {
//        String PACKAGE = "com.visionsmarts.pic2shoppro";
//    }

//	public static void launchMarketToInstallFreeScannerApp(@NonNull final Context ctx) {
//		launchMarketToInstallApp(ctx, PACKAGE);
//	}
//
//	public static void launchMarketToInstallProScannerApp(@NonNull final Context ctx) {
//		launchMarketToInstallApp(ctx, Pro.PACKAGE);
//	}
//
//	public static void launchMarketToInstallApp(@NonNull final Context ctx, @NonNull final String pkgName) {
//		try {
//			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName));
//			ctx.startActivity(intent);
//		} catch (ActivityNotFoundException e) {
//			Log.e(Utils.class.getName(), "Android Market aka Play Market not installed.", e);
//			//Toast.makeText(ctx, R.string.error, Toast.LENGTH_SHORT).show();
//		}
//	}
}
