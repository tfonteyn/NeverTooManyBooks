package com.hardbacknutter.nevertomanybooks.debug;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.database.DBHelper;
import com.hardbacknutter.nevertomanybooks.scanner.Pic2ShopScanner;
import com.hardbacknutter.nevertomanybooks.scanner.ZxingScanner;
import com.hardbacknutter.nevertomanybooks.searches.amazon.AmazonManager;
import com.hardbacknutter.nevertomanybooks.searches.googlebooks.GoogleBooksManager;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

public final class DebugReport {

    /** files with these prefixes will be bundled in the report. */
    private static final String[] FILE_PREFIXES = new String[]{
            "DbUpgrade", "DbExport", "error.log", "export.csv"};

    private DebugReport() {
    }

    /**
     * Return the MD5 hash of the public key that signed this app, or a useful
     * text message if an error or other problem occurred.
     */
    public static String signedBy(@NonNull final Context context) {
        StringBuilder signedBy = new StringBuilder();

        try {
            // Get app info
            PackageManager manager = context.getPackageManager();
            // deprecated... but replacing it fails entirely in API: 21. I presume doc-error.
            @SuppressLint("PackageManagerGetSignatures")
            PackageInfo appInfo = manager.getPackageInfo(context.getPackageName(),
                                                         PackageManager.GET_SIGNATURES);

            // Each sig is a PK of the signer:
            //  https://groups.google.com/forum/?fromgroups=#!topic/android-developers/fPtdt6zDzns
            for (Signature sig : appInfo.signatures) {
                if (sig != null) {
                    final MessageDigest sha1 = MessageDigest.getInstance("MD5");
                    final byte[] publicKey = sha1.digest(sig.toByteArray());
                    // Turn the hex bytes into a more traditional MD5 string representation.
                    final StringBuilder hexString = new StringBuilder();
                    boolean first = true;
                    for (byte aPublicKey : publicKey) {
                        if (!first) {
                            hexString.append(':');
                        } else {
                            first = false;
                        }
                        String byteString = Integer.toHexString(0xFF & aPublicKey);
                        if (byteString.length() == 1) {
                            hexString.append('0');
                        }
                        hexString.append(byteString);
                    }
                    String fingerprint = hexString.toString();

                    // Append as needed (theoretically could have more than one sig */
                    if (signedBy.length() == 0) {
                        signedBy.append(fingerprint);
                    } else {
                        signedBy.append('/').append(fingerprint);
                    }
                }
            }
        } catch (@NonNull final PackageManager.NameNotFoundException
                | NoSuchAlgorithmException
                | RuntimeException e) {
            return e.getLocalizedMessage();
        }
        return signedBy.toString();
    }

    /**
     * Collect and send debug info to a support email address.
     * <p>
     * THIS SHOULD NOT BE A PUBLICLY AVAILABLE MAILING LIST OR FORUM!
     */
    public static boolean sendDebugInfo(@NonNull final Context context) {

        // Create a temp file, set to auto-delete at app close
        File tmpDbFile = StorageUtils.getFile("DbExport-tmp.db");
        tmpDbFile.deleteOnExit();
        StorageUtils.exportFile(DBHelper.getDatabasePath(), tmpDbFile.getName());

        // setup the mail message
        String subject = '[' + context.getString(R.string.app_name) + "] "
                + context.getString(R.string.debug_subject);
        final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("plain/text")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_EMAIL, context.getString(R.string.email_debug)
                                                     .split(";"));
        StringBuilder message = new StringBuilder();


        // Get app info
        PackageInfo appInfo = App.getPackageInfo(0);
        if (appInfo != null) {
            message.append("App: ")
                   .append(appInfo.packageName).append('\n')
                   .append("Version: ")
                   .append(appInfo.versionName)
                   // versionCode deprecated and new method in API: 28, till then ignore...
                   .append(" (").append(appInfo.versionCode).append(")\n");
        } else {
            message.append("PackageInfo == null?");
        }

        message.append("SDK: ").append(Build.VERSION.RELEASE)
               .append(" (").append(Build.VERSION.SDK_INT)
               .append(' ').append(Build.TAGS).append(")\n")
               .append("Phone Model: ").append(Build.MODEL).append('\n')
               .append("Phone Manufacturer: ").append(Build.MANUFACTURER).append('\n')
               .append("Phone Device: ").append(Build.DEVICE).append('\n')
               .append("Phone Product: ").append(Build.PRODUCT).append('\n')
               .append("Phone Brand: ").append(Build.BRAND).append('\n')
               .append("Phone ID: ").append(Build.ID).append('\n')
               .append("Signed-By: ").append(signedBy(context)).append('\n')
               .append("\nHistory:\n").append(Tracker.getEventsInfo()).append('\n');

        // Scanners installed
        try {
            message.append("Pref. Scanner: ")
                   .append(App.getListPreference(Prefs.pk_scanning_preferred_scanner, -1))
                   .append('\n');
            String[] scanners = new String[]{
                    ZxingScanner.ACTION,
                    Pic2ShopScanner.Free.ACTION,
                    Pic2ShopScanner.Pro.ACTION,
                    };
            for (String scanner : scanners) {
                message.append("Scanner [").append(scanner).append("]:\n");
                final Intent mainIntent = new Intent(scanner, null);
                final List<ResolveInfo> resolved =
                        context.getPackageManager().queryIntentActivities(mainIntent, 0);

                if (!resolved.isEmpty()) {
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
                        message.append(" (priority ").append(r.priority)
                               .append(", preference ").append(r.preferredOrder)
                               .append(", match ").append(r.match)
                               .append(", default=").append(r.isDefault)
                               .append(")\n");
                    }
                } else {
                    message.append("    No packages found\n");
                }
            }
        } catch (@NonNull final RuntimeException e) {
            // Don't lose the other debug info if scanner data dies for some reason
            message.append("Scanner failure: ").append(e.getLocalizedMessage()).append('\n');
        }
        message.append('\n');

        //  urls
        message.append("Customizable Search sites URL:\n");
        message.append(AmazonManager.getBaseURL()).append('\n');
        message.append(GoogleBooksManager.getBaseURL()).append('\n');

        message.append("Details:\n\n")
               .append(context.getString(R.string.debug_body)).append("\n\n");

        if (BuildConfig.DEBUG /* always */) {
            Logger.debug(DebugReport.class, "sendDebugInfo", message);
        }

        ArrayList<String> extraText = new ArrayList<>();
        extraText.add(message.toString());

        intent.putExtra(Intent.EXTRA_TEXT, extraText);

        try {
            // Find all files of interest to send, root and log dirs
            List<String> files =
                    collectFiles(StorageUtils.getSharedStorage(), StorageUtils.getLogStorage());

            // Build the attachment list
            ArrayList<Uri> attachmentUris = new ArrayList<>();
            for (String fileSpec : files) {
                File file = StorageUtils.getFile(fileSpec);
                if (file.exists() && file.length() > 0) {
                    attachmentUris.add(FileProvider.getUriForFile(context,
                                                                  GenericFileProvider.AUTHORITY,
                                                                  file));
                }
            }

            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachmentUris);
            context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.title_send_mail)));

            return true;

        } catch (@NonNull final NullPointerException e) {
            Logger.error(DebugReport.class, e);
            return false;
        }
    }

    private static List<String> collectFiles(@NonNull final File... dirs) {
        List<String> files = new ArrayList<>();
        for (File dir : dirs) {
            for (String name : dir.list()) {
                boolean send = false;
                for (String prefix : FILE_PREFIXES) {
                    if (name.startsWith(prefix)) {
                        send = true;
                        break;
                    }
                }
                if (send) {
                    files.add(name);
                }
            }
        }
        return files;
    }
}
