/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;

public abstract class BRBaseActivity
        extends BaseActivity {

    /** FIXME: this is duplication from the model. */
    private final ArrayList<BRBaseActivity.FileDetails> mFileDetails = new ArrayList<>();
    protected ProgressDialogFragment mProgressDialog;
    RecyclerView mListView;
    /** The ViewModel. */
    BRBaseModel mModel;
    /** User clicks on the 'up' button. */
    private final View.OnClickListener onPathUpClickListener = view -> {
        //noinspection ConstantConditions
        String parent = mModel.getRootDir().getParent();
        if (parent == null) {
            UserMessage.show(view, R.string.warning_no_parent_directory_found);
            return;
        }
        mModel.onPathChanged(new File(parent));
    };
    private FileDetailsAdapter mAdapter;
    private TextView mCurrentFolderView;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_backup_restore;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(BRBaseModel.class);
        mModel.init(this);
        mModel.getFileDetails().observe(this, this::onGotFileList);

        mCurrentFolderView = findViewById(R.id.current_folder);
        mCurrentFolderView.setOnClickListener(onPathUpClickListener);
        findViewById(R.id.btn_path_up).setOnClickListener(onPathUpClickListener);

        mListView = findViewById(android.R.id.list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mListView.setLayoutManager(linearLayoutManager);
        mListView.addItemDecoration(
                new DividerItemDecoration(this, linearLayoutManager.getOrientation()));
        mListView.setHasFixedSize(true);

        mAdapter = new FileDetailsAdapter(getLayoutInflater());
        mListView.setAdapter(mAdapter);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                                     | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /**
     * The user selected a file.
     *
     * @param file selected
     */
    protected abstract void onFileSelected(@NonNull File file);

    /**
     * Display the list.
     * Can be called from the background task, or from onCreate after a re-create.
     *
     * @param fileDetails List of FileDetails
     */
    private void onGotFileList(@NonNull final ArrayList<FileDetails> fileDetails) {
        //noinspection ConstantConditions
        mCurrentFolderView.setText(mModel.getRootDir().getAbsolutePath());

        mFileDetails.clear();
        mFileDetails.addAll(fileDetails);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Common for Backup and Restore activity; handles options-model results.
     */
    protected void onTaskProgressMessage(@NonNull final TaskListener.TaskProgressMessage message) {
        if (mProgressDialog != null) {
            mProgressDialog.onProgress(message);
        }
    }

    /**
     * Common for Backup and Restore activity; handles options-model results.
     */
    protected void onTaskCancelledMessage(@SuppressWarnings("unused") final Integer taskId) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        UserMessage.show(this, R.string.progress_end_cancelled);
    }

    /**
     * Interface for details of mFileDetails in current directory.
     */
    public interface FileDetails {

        /** Get the underlying File object. */
        @NonNull
        File getFile();

        void onBindViewHolder(@NonNull Holder holder,
                              @NonNull Context context);
    }

    public static class Holder
            extends RecyclerView.ViewHolder {

        final TextView filenameView;
        final ImageView imageView;

        final androidx.constraintlayout.widget.Group fileDetails;
        final TextView fileContentView;
        final TextView dateView;
        final TextView sizeView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            filenameView = itemView.findViewById(R.id.filename);
            imageView = itemView.findViewById(R.id.icon);

            fileDetails = itemView.findViewById(R.id.file_details);
            fileContentView = itemView.findViewById(R.id.file_content);
            dateView = itemView.findViewById(R.id.date);
            sizeView = itemView.findViewById(R.id.size);
        }
    }

    /**
     * List Adapter for FileDetails objects.
     */
    class FileDetailsAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param inflater LayoutInflater to use
         */
        FileDetailsAdapter(@NonNull final LayoutInflater inflater) {
            mInflater = inflater;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View view = mInflater.inflate(R.layout.row_file_chooser, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            FileDetails item = mFileDetails.get(position);
            item.onBindViewHolder(holder, mInflater.getContext());

            File file = item.getFile();

            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    // descend into the selected directory
                    mModel.onPathChanged(file);
                } else {
                    onFileSelected(file);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mFileDetails.size();
        }
    }
}
