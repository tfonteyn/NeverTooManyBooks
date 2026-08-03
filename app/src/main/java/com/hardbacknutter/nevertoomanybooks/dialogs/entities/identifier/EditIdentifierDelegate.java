/*
 * @Copyright 2018-2026 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditInPlaceParcelableOutput;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditParcelableInput;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.settings.identifiers.IdentifiersEditorFragment;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * This is the editor for the {@link IdentifiersEditorFragment}.
 * Can also be used for a BoB row-menu of an Identifier grouping if needed/implemented.
 *
 * <ul>
 * <li>{@link EditInPlaceParcelableLauncher}</li>
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

    /**
     * Constructor.
     * <p>
     * Class output: {@link EditInPlaceParcelableOutput}.
     *
     * @param owner hosting Fragment
     * @param args  all arguments
     */
    EditIdentifierDelegate(@NonNull final DialogFragment owner,
                           @NonNull final EditParcelableInput<Identifier> args) {
        this.owner = owner;
        requestKey = args.getRequestKey();
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
        TilUtil.autoRemoveError(vb.identifierBookUri, vb.lblIdentifierUri);

        if (vm.getOriginal().getEntityType() == Identifier.EntityType.Author) {
            vb.identifierWikidataClaim.setVisibility(View.VISIBLE);
        } else {
            vb.identifierWikidataClaim.setVisibility(View.GONE);
        }
        modelToView();

        // Force lower-case a-z and digits only
        final InputFilter[] filterArray = {
                (source, start, end, dest, dstart, dend) -> {
                    if (source != null && !KEY_PATTERN.matcher(source.toString()).matches()) {
                        // Remove invalid character
                        // (we could add code to lowercase etc... but this is good enough
                        return "";
                    }
                    // Return original
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
        boolean hasError = false;
        final Identifier currentEdit = vm.getCurrentEdit();
        if (currentEdit.getName().isEmpty()) {
            vb.lblIdentifierName.setError(context.getString(R.string.vldt_non_blank_required));
            hasError = true;
        }
        if (currentEdit.getKey().isEmpty()) {
            vb.lblIdentifierKey.setError(context.getString(R.string.vldt_non_blank_required));
            hasError = true;
        }
        if (!UrlPatterns.isBlankOrValidUrl(currentEdit.getSiteUrl())) {
            vb.lblIdentifierSiteUrl.setError(
                    context.getString(R.string.vldt_blank_or_valid_url_required));
            hasError = true;
        }
        if (!UrlPatterns.isBlankOrValidUriWith1s(currentEdit.getRawUri().orElse(null))) {
            vb.lblIdentifierUri.setError(
                    context.getString(R.string.vldt_blank_or_valid_uri_with_1s_param_required));
            hasError = true;
        }

        if (hasError) {
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        try {
            final Optional<Identifier> existingEntity = vm.saveIfUnique();
            if (existingEntity.isEmpty()) {
                // Success
                new EditInPlaceParcelableOutput<>(vm.getOriginal())
                        .send(owner, requestKey);
                return true;
            }
            // Note that the EntityType of the existingEntity will
            // always be the same as the currentEdit.

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
        final Identifier currentEdit = vm.getCurrentEdit();
        vb.identifierName.setText(currentEdit.getName());
        vb.identifierKey.setText(currentEdit.getKey());
        vb.identifierSiteUrl.setText(currentEdit.getSiteUrl());
        vb.identifierBookUri.setText(currentEdit.getRawUri().orElse(""));

        // Remove the "P" prefix for easier editing
        String wdp = currentEdit.getWikidataClaim().orElse("");
        if (wdp.startsWith("P")) {
            wdp = wdp.substring(1);
        }
        vb.identifierWikidataClaim.setText(wdp);
    }

    @SuppressWarnings("DataFlowIssue")
    private void viewToModel() {
        final Identifier currentEdit = vm.getCurrentEdit();
        currentEdit.setName(vb.identifierName.getText().toString().strip());
        currentEdit.setKey(vb.identifierKey.getText().toString().strip());
        currentEdit.setSiteUrl(vb.identifierSiteUrl.getText().toString().strip());
        currentEdit.setRawUri(vb.identifierBookUri.getText().toString().strip());

        // "P" prefix will be added internally
        currentEdit.setWikidataClaim(vb.identifierWikidataClaim.getText().toString().strip());
    }
}
