/*
 * @Copyright 2018-2025 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.identifier;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.UrlPatterns;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditIdentifierContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.settings.identifiers.IdentifiersEditorFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * This is the editor for the {@link IdentifiersEditorFragment}.
 * Can also be used for a BoB row-menu of a Identifier grouping if needed/implemented.
 *
 * <ul>
 * <li>Direct/in-place editing.</li>
 * <li>Modifications <strong>ARE STORED</strong> in the database</li>
 * <li>Returns the modified item.</li>
 * <li>Merging is NOT supported.</li>
 * </ul>
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class EditIdentifierDelegate
        implements FlexDialogDelegate {

    private static final String TAG = "EditIdentifierDelegate";
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z\\d]*");
    private final EditIdentifierViewModel vm;

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    /** View Binding. */
    private DialogEditIdentifierContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    EditIdentifierDelegate(@NonNull final DialogFragment owner,
                           @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        vm = new ViewModelProvider(owner).get(EditIdentifierViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditIdentifierContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_identifier, container, false);
        vb = DialogEditIdentifierContentBinding.bind(view.findViewById(R.id.dialog_content));
        return view;
    }

    @NonNull
    public Toolbar getToolbar() {
        return Objects.requireNonNull(toolbar, "No toolbar set");
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
        }

        TilUtil.autoRemoveError(vb.identifierName, vb.lblIdentifierName);
        TilUtil.autoRemoveError(vb.identifierKey, vb.lblIdentifierKey);
        TilUtil.autoRemoveError(vb.identifierSiteUrl, vb.lblIdentifierSiteUrl);
        TilUtil.autoRemoveError(vb.identifierBookUri, vb.lblIdentifierBookUri);
        TilUtil.autoRemoveError(vb.identifierAuthorUri, vb.lblIdentifierAuthorUri);

        modelToView();

        final InputFilter[] filterArray = {
                new InputFilter.LengthFilter(Identifier.MAX_KEY_LEN),
                (source, start, end, dest, dstart, dend) -> {
                    if (source != null && !KEY_PATTERN.matcher(source.toString()).matches()) {
                        return "";
                    }
                    //noinspection ReturnOfNull
                    return null;
                }
        };
        vb.identifierKey.setFilters(filterArray);

        vb.identifierName.requestFocus();
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        final Context context = vb.getRoot().getContext();

        final Identifier currentEdit = vm.getCurrentEdit();
        if (currentEdit.getName().isEmpty()) {
            vb.lblIdentifierName.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }
        if (currentEdit.getKey().isEmpty()) {
            vb.lblIdentifierKey.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        if (!UrlPatterns.isBlankOrValidUrl(currentEdit.getSiteUrl(context))) {
            vb.lblIdentifierSiteUrl.setError(
                    context.getString(R.string.vldt_blank_or_valid_url_required));
            return false;
        }
        if (!UrlPatterns.isBlankOrValidUriWith1s(currentEdit.getBookUri(context).orElse(null))) {
            vb.lblIdentifierBookUri.setError(
                    context.getString(R.string.vldt_blank_or_valid_uri_with_1s_param_required));
            return false;
        }
        if (!UrlPatterns.isBlankOrValidUriWith1s(currentEdit.getAuthorUri(context).orElse(null))) {
            vb.lblIdentifierAuthorUri.setError(
                    context.getString(R.string.vldt_blank_or_valid_uri_with_1s_param_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        try {
            final Optional<Identifier> existingEntity = vm.saveIfUnique(context);
            if (existingEntity.isEmpty()) {
                // Success
                EditParcelableLauncher.setEditInPlaceResult(owner, requestKey, vm.getOriginal());
                return true;
            }

            // REJECT an already existing Identifier with the same name.
            if (existingEntity.get().getName().equalsIgnoreCase(currentEdit.getName())) {
                vb.lblIdentifierName.setError(context.getString(
                        R.string.warning_x_already_exists,
                        context.getString(R.string.lbl_identifier)));
            }
            // REJECT an already existing Identifier with the same key.
            if (existingEntity.get().getKey().equalsIgnoreCase(currentEdit.getKey())) {
                vb.lblIdentifierKey.setError(context.getString(
                        R.string.warning_x_already_exists,
                        context.getString(R.string.lbl_identifier)));
            }
            return false;

        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, vm.getOriginal());
            return false;
        }
    }

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void modelToView() {
        final Context context = owner.getContext();
        final Identifier currentEdit = vm.getCurrentEdit();
        vb.identifierName.setText(currentEdit.getName());
        vb.identifierKey.setText(currentEdit.getKey());
        //noinspection DataFlowIssue
        vb.identifierSiteUrl.setText(currentEdit.getSiteUrl(context));
        vb.identifierBookUri.setText(currentEdit.getBookUri(context).orElse(""));
        vb.identifierAuthorUri.setText(currentEdit.getAuthorUri(context).orElse(""));
    }

    @SuppressWarnings("DataFlowIssue")
    private void viewToModel() {
        final Identifier currentEdit = vm.getCurrentEdit();
        currentEdit.setName(vb.identifierName.getText().toString().trim());
        currentEdit.setKey(vb.identifierKey.getText().toString().trim());
        currentEdit.setSiteUrl(vb.identifierSiteUrl.getText().toString().trim());
        currentEdit.setBookUri(vb.identifierBookUri.getText().toString().trim());
        currentEdit.setAuthorUri(vb.identifierAuthorUri.getText().toString().trim());
    }
}
