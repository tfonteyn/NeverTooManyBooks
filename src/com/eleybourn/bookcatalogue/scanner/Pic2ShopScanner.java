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
    /**
     * When a barcode is read, pic2shop returns Activity.RESULT_OK in onActivityResult()
     * of the activity which requested the scan using startActivityForResult().
     * The read barcode can be retrieved with intent.getStringExtra(BARCODE).
     *
     * If the user exits pic2shop by pressing Back before a barcode is read, the
     * result code will be Activity.RESULT_CANCELED in onActivityResult().
     */
    private static final String BARCODE_FORMAT = "EAN13";
    private static final String FORMATS = "formats";

    /**
     * Check if we have a valid intent available.
     *
     * @return true if present
     */
    static boolean isIntentAvailable() {
        return isFreeScannerAppInstalled(BookCatalogueApp.getAppContext())
                || isProScannerAppInstalled(BookCatalogueApp.getAppContext());
    }

    private static boolean isIntentAvailable(@NonNull final Context context, @NonNull final String action) {
        Intent test = new Intent(action);
        return context.getPackageManager().resolveActivity(test, 0) != null;
    }

    private static boolean isFreeScannerAppInstalled(@NonNull final Context context) {
        return isIntentAvailable(context, Free.ACTION);
    }

    private static boolean isProScannerAppInstalled(@NonNull final Context context) {
        return isIntentAvailable(context, Pro.ACTION);
    }

    /**
     * Start the activity with the passed request code.
     *
     * Note that we always send an intent; the caller should have checked that
     * one of the intents is valid, or catch the resulting errors.
     */
    @Override
    public void startActivityForResult(@NonNull final  Activity activity, final int requestCode) {
        Intent intent;
        if (isFreeScannerAppInstalled(activity)) {
            intent = new Intent(Free.ACTION);
            //i.putExtra(FORMATS, BARCODE_FORMAT);
        } else {
            intent = new Intent(Pro.ACTION);
            //intent.putExtra(FORMATS, BARCODE_FORMAT);
        }
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Extract the barcode from the result
     */
    @Override
    public String getBarcode(@NonNull final Intent intent) {
        String barcode = intent.getStringExtra("BARCODE");
        String barcodeFormat = intent.getStringExtra("format");
        if (barcodeFormat != null && !barcodeFormat.equalsIgnoreCase(BARCODE_FORMAT)) {
            throw new RuntimeException("Unexpected format for barcode: " + barcodeFormat);
        }
        return barcode;
    }

    public interface Free {
        String ACTION = "com.visionsmarts.pic2shop.SCAN";
    }

    public interface Pro {
        String ACTION = "com.visionsmarts.pic2shoppro.SCAN";
    }

//	public static void launchMarketToInstallFreeScannerApp(@NonNull final Context ctx) {
//		launchMarketToInstallApp(ctx, ACTION);
//	}
//
//	public static void launchMarketToInstallProScannerApp(@NonNull final Context ctx) {
//		launchMarketToInstallApp(ctx, Pro.ACTION);
//	}
//
//	public static void launchMarketToInstallApp(@NonNull final Context ctx, @NonNull final String pkgName) {
//		try {
//			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName));
//			ctx.startActivity(intent);
//		} catch (ActivityNotFoundException e) {
//			Logger.showError(e, Utils.class.getName() + ": Google Play not installed.");
//			//Toast.makeText(ctx, R.string.error, Toast.LENGTH_SHORT).show();
//		}
//	}
}
