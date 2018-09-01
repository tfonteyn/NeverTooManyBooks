package com.eleybourn.bookcatalogue.debug;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.scanner.ScannerManager;
import com.eleybourn.bookcatalogue.scanner.ZxingScanner;
import com.eleybourn.bookcatalogue.scanner.pic2shop.Scan;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class DebugReport {
    private static final String[] FILE_PREFIXES = new String[]{
            StorageUtils.DIRECTORY_NAME + "DbUpgrade",
            StorageUtils.DIRECTORY_NAME + "DbExport",
            "error.log",
            "export.csv"};

    /**
     * Return the MD5 hash of the public key that signed this app, or a useful
     * text message if an error or other problem occurred.
     * <p>
     * No longer caching as only needed at a crash anyhow
     */
    public static String signedBy(Context context) {
        StringBuilder signedBy = new StringBuilder();

        try {
            // Get app info
            PackageManager manager = context.getPackageManager();
            PackageInfo appInfo = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);

            // Each sig is a PK of the signer:
            //     https://groups.google.com/forum/?fromgroups=#!topic/android-developers/fPtdt6zDzns
            for (Signature sig : appInfo.signatures) {
                if (sig != null) {
                    final MessageDigest sha1 = MessageDigest.getInstance("MD5");
                    final byte[] publicKey = sha1.digest(sig.toByteArray());
                    // Turn the hex bytes into a more traditional MD5 string representation.
                    final StringBuilder hexString = new StringBuilder();
                    boolean first = true;
                    for (byte aPublicKey : publicKey) {
                        if (!first) {
                            hexString.append(":");
                        } else {
                            first = false;
                        }
                        String byteString = Integer.toHexString(0xFF & aPublicKey);
                        if (byteString.length() == 1)
                            hexString.append("0");
                        hexString.append(byteString);
                    }
                    String fingerprint = hexString.toString();

                    // Append as needed (theoretically could have more than one sig */
                    if (signedBy.length() == 0)
                        signedBy.append(fingerprint);
                    else
                        signedBy.append("/").append(fingerprint);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Default if package not found...kind of unlikely
            return "NOPACKAGE";

        } catch (Exception e) {
            // Default if we die
            return e.getMessage();
        }

        return signedBy.toString();
    }

    /**
     * Collect and send com.eleybourn.bookcatalogue.debug info to a support email address.
     * <p>
     * THIS SHOULD NOT BE A PUBLICLY AVAILABLE MAILING LIST OR FORUM!
     */
    public static void sendDebugInfo(Context context, CatalogueDBAdapter dbHelper) {
        // Create a temp DB copy.
        String tmpFileName = StorageUtils.DIRECTORY_NAME + "DbExport-tmp.db";
        dbHelper.backupDbFile(tmpFileName);
        File dbFile = StorageUtils.getFile(tmpFileName);
        dbFile.deleteOnExit();
        // setup the mail message
        final Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, context.getString(R.string.debug_email).split(";"));
        String subject = "[" + context.getString(R.string.app_name) + "] " + context.getString(R.string.debug_subject);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        StringBuilder message = new StringBuilder();

        try {
            // Get app info
            PackageManager manager = context.getPackageManager();
            PackageInfo appInfo = manager.getPackageInfo(context.getPackageName(), 0);
            message.append("App: ").append(appInfo.packageName).append("\n")
                    .append("Version: ").append(appInfo.versionName).append(" (").append(appInfo.versionCode).append(")\n");
        } catch (Exception ignore) {
            // Not much we can do inside error logger...
        }


        message.append("SDK: ").append(Build.VERSION.RELEASE).append(" (").append(Build.VERSION.SDK_INT).append(" ").append(Build.TAGS).append(")\n")
                .append("Phone Model: ").append(Build.MODEL).append("\n")
                .append("Phone Manufacturer: ").append(Build.MANUFACTURER).append("\n")
                .append("Phone Device: ").append(Build.DEVICE).append("\n")
                .append("Phone Product: ").append(Build.PRODUCT).append("\n")
                .append("Phone Brand: ").append(Build.BRAND).append("\n")
                .append("Phone ID: ").append(Build.ID).append("\n")
                .append("Signed-By: ").append(signedBy(context)).append("\n")
                .append("\nHistory:\n").append(Tracker.getEventsInfo()).append("\n");

        // Scanners installed
        try {
            message.append("Pref. Scanner: ").append(BookCataloguePreferences.getInt(ScannerManager.PREF_PREFERRED_SCANNER, -1)).append("\n");
            String[] scanners = new String[]{ZxingScanner.ACTION, Scan.ACTION, Scan.Pro.ACTION};
            for (String scanner : scanners) {
                message.append("Scanner [").append(scanner).append("]:\n");
                final Intent mainIntent = new Intent(scanner, null);
                final List<ResolveInfo> resolved = context.getPackageManager().queryIntentActivities(mainIntent, 0);
                if (resolved.size() > 0) {
                    for (ResolveInfo r : resolved) {
                        message.append("    ");
                        // Could be activity or service...
                        if (r.activityInfo != null) {
                            message.append(r.activityInfo.packageName);
                        } else if (r.serviceInfo != null) {
                            message.append(r.serviceInfo.packageName);
                        } else {
                            message.append("UNKNOWN");
                        }
                        message.append(" (priority ").append(r.priority).append(", preference ").append(r.preferredOrder).append(", match ").append(r.match).append(", default=").append(r.isDefault).append(")\n");
                    }
                } else {
                    message.append("    No packages found\n");
                }
            }
        } catch (Exception e) {
            // Don't lose the other debug info if scanner data dies for some reason
            message.append("Scanner failure: ").append(e.getMessage()).append("\n");
        }
        message.append("\n");

        message.append("Details:\n\n").append(context.getString(R.string.debug_body).toUpperCase()).append("\n\n");

        Logger.logError(new RuntimeException("DEBUG"), message.toString());

        emailIntent.putExtra(Intent.EXTRA_TEXT, message.toString());
        //has to be an ArrayList
        ArrayList<Uri> uris = new ArrayList<>();
        //convert from paths to Android friendly Parcelable Uri's
        ArrayList<String> files = new ArrayList<>();

        // Find all files of interest to send
        File dir = StorageUtils.getSharedStorage();
        try {
            for (String name : dir.list()) {
                boolean send = false;
                for (String prefix : FILE_PREFIXES)
                    if (name.startsWith(prefix)) {
                        send = true;
                        break;
                    }
                if (send)
                    files.add(name);
            }

            // Build the attachment list
            for (String file : files) {
                File fileIn = StorageUtils.getFile(file);
                if (fileIn.exists() && fileIn.length() > 0) {
                    Uri u = Uri.fromFile(fileIn);
                    uris.add(u);
                }
            }

            // We used to only send it if there are any files to send, but later versions added
            // useful debugging info. So now we always send.
            emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            context.startActivity(Intent.createChooser(emailIntent, "Send mail..."));

        } catch (NullPointerException e) {
            Logger.logError(e);
            Toast.makeText(context, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
        }
    }
}
