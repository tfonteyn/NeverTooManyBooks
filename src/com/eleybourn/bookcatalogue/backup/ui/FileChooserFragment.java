/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Fragment to display a simple directory/file browser.
 *
 * @author pjw
 */
public class FileChooserFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = FileChooserFragment.class.getSimpleName();

    private static final String BKEY_ROOT_PATH = TAG + ":root";
    private static final String BKEY_FILE_LIST = TAG + ":list";

    /** Value holder for the root and its content. */
    @Nullable
    private FileChooserFragment.DirectoryContent mDir;
    private EditText mFilenameView;
    private TextView mCurrentFolderView;
    private final TaskListener<Object, FileChooserFragment.DirectoryContent> mListener =
            new TaskListener<Object, DirectoryContent>() {

                @Override
                public void onTaskFinished(final int taskId,
                                           final boolean success,
                                           @Nullable final FileChooserFragment.DirectoryContent result,
                                           @Nullable final Exception e) {

                    //noinspection SwitchStatementWithTooFewBranches
                    switch (taskId) {
                        case R.id.TASK_ID_FILE_LISTER:
                            //noinspection ConstantConditions
                            onGotFileList(result);
                            break;

                        default:
                            Logger.warnWithStackTrace(this, "Unknown taskId=" + taskId);
                            break;
                    }
                }
            };
    /** User clicks on the 'up' button. */
    private final OnClickListener onPathUpClickListener = v -> {
        String parent = mDir.root.getParent();
        if (parent == null) {
            UserMessage.showUserMessage(v, R.string.warning_no_parent_directory_found);
            return;
        }
        mDir = new FileChooserFragment.DirectoryContent(parent);
        onPathChanged(mDir.root);
    };

    /**
     * Constructor.
     *
     * @param root            directory root to display
     * @param defaultFileName filename to put in the user-entry field
     *
     * @return the instance
     */
    @NonNull
    public static FileChooserFragment newInstance(@NonNull final File root,
                                                  @NonNull final String defaultFileName) {
        String path;
        // Turn the passed File into a directory name
        if (root.isDirectory()) {
            path = root.getAbsolutePath();
        } else {
            path = root.getParent();
        }

        FileChooserFragment frag = new FileChooserFragment();
        Bundle args = new Bundle();
        args.putString(BKEY_ROOT_PATH, path);
        args.putString(UniqueId.BKEY_FILE_SPEC, defaultFileName);
        frag.setArguments(args);
        return frag;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_chooser, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = getView();
        //noinspection ConstantConditions
        mCurrentFolderView = view.findViewById(R.id.current_folder);
        mFilenameView = view.findViewById(R.id.file_name);

        Bundle args = requireArguments();

        if (savedInstanceState != null) {
            // get the path, with fallback to the standard args.
            String path = savedInstanceState.getString(BKEY_ROOT_PATH,
                                                       args.getString(BKEY_ROOT_PATH));
            // either way, must not be null.
            Objects.requireNonNull(path);
            // can be null
            ArrayList<FileDetails> list = savedInstanceState.getParcelableArrayList(BKEY_FILE_LIST);

            mDir = new DirectoryContent(path, list);
            // populate with the existing content.
            onGotFileList(mDir);

        } else {
            String path = Objects.requireNonNull(args.getString(BKEY_ROOT_PATH));
            mDir = new DirectoryContent(path);
            mCurrentFolderView.setText(mDir.root.getAbsolutePath());
            mFilenameView.setText(args.getString(UniqueId.BKEY_FILE_SPEC));
            // start the task to get the content
            onPathChanged(mDir.root);
        }

        // 'up' directory
        view.findViewById(R.id.btn_path_up).setOnClickListener(onPathUpClickListener);
        mCurrentFolderView.setOnClickListener(onPathUpClickListener);

        //noinspection ConstantConditions
        getActivity().getWindow()
                     .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                                               | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /**
     * @return the selected file
     */
    @NonNull
    File getSelectedFile() {
        //noinspection ConstantConditions
        return new File(mDir.root.getAbsolutePath() +
                                File.separator + mFilenameView.getText().toString().trim());
    }

    /**
     * Rebuild the file list in background.
     */
    private void onPathChanged(@Nullable final File root) {
        if (root == null || !root.isDirectory()) {
            return;
        }

        //noinspection ConstantConditions
        UserMessage.showUserMessage(getView(), R.string.progress_msg_reading_directory);
        new FileListerTask(root, mListener).execute();
    }

    /**
     * Display the list.
     * Can be called from the background task, or from onCreate after a re-create.
     *
     * @param directoryContent List of FileDetails
     */
    private void onGotFileList(@NonNull final FileChooserFragment.DirectoryContent directoryContent) {
        mDir = directoryContent;
        mCurrentFolderView.setText(mDir.root.getAbsolutePath());

        @SuppressWarnings("ConstantConditions")
        FileDetailsAdapter adapter = new FileDetailsAdapter(getContext(), mDir);
        @SuppressWarnings("ConstantConditions")
        RecyclerView listView = getView().findViewById(android.R.id.list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        listView.setLayoutManager(linearLayoutManager);
        listView.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        listView.setHasFixedSize(true);
        listView.setAdapter(adapter);
    }

    /**
     * Save our root path and list.
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mDir != null) {
            outState.putString(BKEY_ROOT_PATH, mDir.root.getAbsolutePath());
            outState.putParcelableArrayList(BKEY_FILE_LIST, mDir.files);
        }
    }

    /**
     * Interface for details of files in current directory.
     */
    public interface FileDetails
            extends Parcelable {

        /** Get the underlying File object. */
        @NonNull
        File getFile();

        void onBindViewHolder(@NonNull final Holder holder,
                              @NonNull Resources resources);
    }

    /**
     * Value class holding a root directory + the files in that root.
     */
    static class DirectoryContent {

        @NonNull
        final File root;
        @NonNull
        final ArrayList<FileDetails> files;

        DirectoryContent(@NonNull final File root) {
            this.root = root;
            files = new ArrayList<>();
        }

        DirectoryContent(@NonNull final String fileSpec) {
            root = new File(fileSpec);
            files = new ArrayList<>();
        }

        DirectoryContent(@NonNull final String fileSpec,
                         @Nullable final ArrayList<FileDetails> files) {
            root = new File(fileSpec);
            this.files = files != null ? files : new ArrayList<>();
        }
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
    protected class FileDetailsAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final FileChooserFragment.DirectoryContent mItems;

        FileDetailsAdapter(@NonNull final Context context,
                           @NonNull final FileChooserFragment.DirectoryContent items) {

            mInflater = LayoutInflater.from(context);
            mItems = items;
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

            final FileDetails item = mItems.files.get(position);

            item.onBindViewHolder(holder, getResources());

            holder.itemView.setOnClickListener(v -> {
                if (item.getFile().isDirectory()) {
                    // descend into the selected directory
                    mDir = new DirectoryContent(item.getFile());
                    onPathChanged(mDir.root);
                } else {
                    // Put the name of the selected file into the filename field
                    mFilenameView.setText(item.getFile().getName());
                }
            });
        }

        @Override
        public int getItemCount() {
            return mItems.files.size();
        }
    }
}
