package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.backup.ui.BRBaseActivity.FileDetails;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Implementation of {@link FileDetails} that collects data about backup mFileDetails
 * in a background thread.
 *
 * @author pjw
 */
public class BackupFileDetails
        implements FileDetails, Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<BackupFileDetails> CREATOR =
            new Creator<BackupFileDetails>() {
                public BackupFileDetails createFromParcel(@NonNull final Parcel source) {
                    return new BackupFileDetails(source);
                }

                public BackupFileDetails[] newArray(final int size) {
                    return new BackupFileDetails[size];
                }
            };

    /** File for this item. */
    @NonNull
    private final File mFile;
    /** The BackupInfo we use when displaying the object. */
    @Nullable
    private BackupInfo mInfo;

    /**
     * Constructor.
     */
    BackupFileDetails(@NonNull final File file) {
        mFile = file;
    }

    /**
     * Constructor, using a Parcel as source.
     */
    private BackupFileDetails(@NonNull final Parcel in) {
        mFile = new File(in.readString());
        // flag to indicate the Parcel has the info bundle
        boolean hasInfo = in.readInt() != 0;
        if (hasInfo) {
            mInfo = in.readParcelable(getClass().getClassLoader());
        } else {
            mInfo = null;
        }
    }

    public void setInfo(@NonNull final BackupInfo info) {
        mInfo = info;
    }

    @NonNull
    @Override
    public File getFile() {
        return mFile;
    }

    @Override
    public void onBindViewHolder(@NonNull final BRBaseActivity.Holder holder,
                                 @NonNull final Context context) {

        holder.filenameView.setText(mFile.getName());

        // For directories, hide the extra data
        if (mFile.isDirectory()) {
            holder.imageView.setImageDrawable(context.getDrawable(R.drawable.ic_folder));
            holder.fileDetails.setVisibility(View.GONE);
        } else {
            // Display details
            holder.imageView.setImageDrawable(context.getDrawable(R.drawable.ic_archive));
            holder.fileDetails.setVisibility(View.VISIBLE);

            holder.sizeView.setText(Utils.formatFileSize(context, mFile.length()));


            Locale locale = LocaleUtils.from(context);
            if (mInfo != null) {
                List<String> args = new ArrayList<>();
                if (mInfo.hasBookCount()) {
                    args.add(context.getResources().getQuantityString(R.plurals.n_books,
                                                                      mInfo.getBookCount(),
                                                                      mInfo.getBookCount()));
                } else if (mInfo.hasBooks()) {
                    args.add(context.getString(R.string.lbl_books));
                }
                if (mInfo.hasCoverCount()) {
                    args.add(context.getResources().getQuantityString(R.plurals.n_covers,
                                                                      mInfo.getCoverCount(),
                                                                      mInfo.getCoverCount()));
                } else if (mInfo.hasCovers()) {
                    args.add(context.getString(R.string.lbl_covers));
                }

                if (mInfo.hasPreferences()) {
                    args.add(context.getString(R.string.lbl_settings));
                }
                if (mInfo.hasBooklistStyles()) {
                    args.add(context.getString(R.string.lbl_styles));
                }

                // needs RTL
                holder.fileContentView.setText(TextUtils.join(", ", args));

                Date creationDate = mInfo.getCreationDate();
                if (creationDate != null) {
                    holder.dateView.setText(DateUtils.toPrettyDateTime(locale, creationDate));
                }
                holder.fileContentView.setVisibility(View.VISIBLE);
            } else {
                holder.dateView.setText(DateUtils.toPrettyDateTime(locale,
                                                                   new Date(mFile.lastModified())));
                holder.fileContentView.setVisibility(View.GONE);
            }
        }
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Save all fields that must be persisted.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mFile.getAbsolutePath());
        if (mInfo != null) {
            // flag to indicate the Parcel has the info bundle
            dest.writeInt(1);
            dest.writeParcelable(mInfo, flags);
        } else {
            dest.writeInt(0);
        }
    }
}
