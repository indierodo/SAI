package com.aefyr.sai.ui.fragments;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aefyr.sai.R;
import com.aefyr.sai.adapters.SaiPiSessionsAdapter;
import com.aefyr.sai.ui.dialogs.AppInstalledDialogFragment;
import com.aefyr.sai.ui.dialogs.DarkLightThemeSelectionDialogFragment;
import com.aefyr.sai.ui.dialogs.DonationSuggestionDialogFragment;
import com.aefyr.sai.ui.dialogs.ErrorLogDialogFragment2;
import com.aefyr.sai.ui.dialogs.InstallationConfirmationDialogFragment;
import com.aefyr.sai.ui.dialogs.InstallerXDialogFragment;
import com.aefyr.sai.ui.dialogs.ThemeSelectionDialogFragment;
import com.aefyr.sai.ui.recycler.RecyclerPaddingDecoration;
import com.aefyr.sai.utils.AlertsUtils;
import com.aefyr.sai.utils.PermissionsUtils;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.Theme;
import com.aefyr.sai.utils.Utils;
import com.aefyr.sai.utils.saf.SafUtils;
import com.aefyr.sai.viewmodels.InstallerViewModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Installer2Fragment extends InstallerFragment implements InstallationConfirmationDialogFragment.ConfirmationListener, SaiPiSessionsAdapter.ActionDelegate {
    private static final String TAG = "Installer2Fragment";

    private static final int REQUEST_CODE_GET_FILES = 337;

    private InstallerViewModel mViewModel;

    private RecyclerView mSessionsRecycler;
    private ViewGroup mPlaceholderContainer;

    private PreferencesHelper mHelper;

    private Uri mPendingActionViewUri;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = PreferencesHelper.getInstance(getContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPlaceholderContainer = findViewById(R.id.container_installer_placeholder);

        mSessionsRecycler = findViewById(R.id.rv_installer_sessions);
        mSessionsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        SaiPiSessionsAdapter sessionsAdapter = new SaiPiSessionsAdapter(requireContext());
        sessionsAdapter.setActionsDelegate(this);

        mSessionsRecycler.setAdapter(sessionsAdapter);
        mSessionsRecycler.addItemDecoration(new RecyclerPaddingDecoration(0, requireContext().getResources().getDimensionPixelSize(R.dimen.installer_sessions_recycler_top_padding), 0, requireContext().getResources().getDimensionPixelSize(R.dimen.installer_sessions_recycler_bottom_padding)));

        mViewModel = new ViewModelProvider(this).get(InstallerViewModel.class);
        mViewModel.getEvents().observe(getViewLifecycleOwner(), (event) -> {
            if (event.isConsumed())
                return;

            //For some reason this observer gets called after state save on some devices
            if (isStateSaved())
                return;

            if (event.type().equals(InstallerViewModel.EVENT_PACKAGE_INSTALLED))
                DonationSuggestionDialogFragment.showIfNeeded(requireContext(), getChildFragmentManager());

            if (!mHelper.showInstallerDialogs()) {
                event.consume();
                return;
            }

            switch (event.type()) {
                case InstallerViewModel.EVENT_PACKAGE_INSTALLED:
                    showPackageInstalledAlert(event.consume());
                    break;
                case InstallerViewModel.EVENT_INSTALLATION_FAILED:
                    String[] errors = event.consume();
                    ErrorLogDialogFragment2.newInstance(getString(R.string.installer_installation_failed), errors[0], errors[1], false).show(getChildFragmentManager(), "installation_error_dialog");
                    break;
            }
        });
        mViewModel.getSessions().observe(getViewLifecycleOwner(), (sessions) -> {
            setPlaceholderShown(sessions.size() == 0);
            sessionsAdapter.setData(sessions);
        });

        findViewById(R.id.ib_toggle_theme).setOnClickListener((v -> {
            if (Theme.getInstance(requireContext()).getThemeMode() == Theme.Mode.AUTO_LIGHT_DARK) {
                DarkLightThemeSelectionDialogFragment.newInstance(DarkLightThemeSelectionDialogFragment.MODE_APPLY).show(getChildFragmentManager(), null);
            } else {
                ThemeSelectionDialogFragment.newInstance(requireContext()).show(getChildFragmentManager(), "theme_selection_dialog");
            }
        }));
        findViewById(R.id.ib_help).setOnClickListener((v) -> AlertsUtils.showAlert(this, R.string.help, R.string.installer_help));

        Button installButtton = findViewById(R.id.button_install);
        installButtton.setOnClickListener((v) -> checkPermissionsAndPickFiles());

        if (mPendingActionViewUri != null) {
            handleActionView(mPendingActionViewUri);
            mPendingActionViewUri = null;
        }
    }

    @Override
    public void handleActionView(Uri uri) {
        if (!isAdded()) {
            mPendingActionViewUri = uri;
            return;
        }

        if (mHelper.isInstallerXEnabled()) {
            openInstallerXDialog(uri);
        } else {
            DialogFragment existingDialog = (DialogFragment) getChildFragmentManager().findFragmentByTag("installation_confirmation_dialog");
            if (existingDialog != null)
                existingDialog.dismiss();

            InstallationConfirmationDialogFragment.newInstance(uri).show(getChildFragmentManager(), "installation_confirmation_dialog");
        }

    }

    private void setPlaceholderShown(boolean shown) {
        if (shown) {
            mPlaceholderContainer.setVisibility(View.VISIBLE);
            mSessionsRecycler.setVisibility(View.GONE);
        } else {
            mPlaceholderContainer.setVisibility(View.GONE);
            mSessionsRecycler.setVisibility(View.VISIBLE);
        }
    }

    private void openInstallerXDialog(@Nullable Uri apkSourceUri) {
        openInstallerXDialog(apkSourceUri == null ? Collections.emptyList() : Collections.singletonList(apkSourceUri));
    }

    private void openInstallerXDialog(List<Uri> apkSourceUris) {
        DialogFragment existingDialog = (DialogFragment) getChildFragmentManager().findFragmentByTag("installerx_dialog");
        if (existingDialog != null)
            existingDialog.dismiss();

        InstallerXDialogFragment.newInstance(apkSourceUris, null).show(getChildFragmentManager(), "installerx_dialog");
    }

    private boolean pickFilesWithSaf() {
        Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.setType("*/*");
        getContentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        getContentIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(getContentIntent, getString(R.string.installer_pick_apks)), REQUEST_CODE_GET_FILES);

        return true;
    }

    /**
     * InstallerX opens archive sources as files through /proc/self/fd.  A URI grant from the
     * system picker alone is not enough for that path on scoped-storage devices, so without
     * storage access AndroidUriHost has to copy the source into the cache directory.
     */
    private void checkPermissionsAndPickFiles() {
        if (PermissionsUtils.checkAndRequestStoragePermissions(this))
            pickFilesWithSaf();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != PermissionsUtils.REQUEST_CODE_STORAGE_PERMISSIONS)
            return;

        if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED) {
            AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_storage);
        } else {
            pickFilesWithSaf();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_GET_FILES) {
            if (resultCode != Activity.RESULT_OK || data == null)
                return;

            List<Uri> apkUris = new ArrayList<>();
            if (data.getData() != null) {
                apkUris.add(data.getData());
            } else if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                apkUris = new ArrayList<>(clipData.getItemCount());

                for (int i = 0; i < clipData.getItemCount(); i++)
                    apkUris.add(clipData.getItemAt(i).getUri());
            }

            if (apkUris.isEmpty())
                return;

            if (mHelper.isInstallerXEnabled()) {
                openInstallerXDialog(apkUris);
            } else if (apkUris.size() == 1) {
                mViewModel.installPackagesFromContentProviderZip(apkUris.get(0));
            } else {
                mViewModel.installPackagesFromContentProviderUris(apkUris);
            }
        }
    }

    private void showPackageInstalledAlert(String packageName) {
        AppInstalledDialogFragment.newInstance(packageName).show(getChildFragmentManager(), "dialog_app_installed");
    }

    @Override
    public void onConfirmed(Uri apksFileUri) {
        String fileName = SafUtils.getFileNameFromContentUri(requireContext(), apksFileUri);
        if (fileName == null) {
            Log.w(TAG, String.format("Unable to get file name from uri %s, assuming it's a .apks file", apksFileUri.toString()));
            mViewModel.installPackagesFromContentProviderZip(apksFileUri);
            return;
        }

        String fileExtension = Utils.getExtension(fileName);
        if (fileExtension == null) {
            Log.w(TAG, String.format("Unable to get extension from uri %s, assuming it's a .apks file", apksFileUri.toString()));
            mViewModel.installPackagesFromContentProviderZip(apksFileUri);
            return;
        }

        switch (fileExtension.toLowerCase()) {
            case "apks":
                mViewModel.installPackagesFromContentProviderZip(apksFileUri);
                break;
            case "apk":
                mViewModel.installPackagesFromContentProviderUris(Collections.singletonList(apksFileUri));
                break;
            default:
                Log.w(TAG, String.format("Uri %s has unexpected extension - %s, assuming it's a .apks file", apksFileUri.toString(), fileExtension));
                mViewModel.installPackagesFromContentProviderZip(apksFileUri);
                break;
        }
    }

    @Override
    protected int layoutId() {
        return R.layout.fragment_installer2;
    }

    @Override
    public void launchApp(String packageName) {
        try {
            PackageManager pm = requireContext().getPackageManager();
            Intent appLaunchIntent = pm.getLaunchIntentForPackage(packageName);
            Objects.requireNonNull(appLaunchIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(appLaunchIntent);
        } catch (Exception e) {
            Log.w("SAI", e);
            Toast.makeText(requireContext(), R.string.installer_unable_to_launch_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void showError(String shortError, String fullError) {
        ErrorLogDialogFragment2.newInstance(getString(R.string.installer_installation_failed), shortError, fullError, false).show(getChildFragmentManager(), "installation_error_dialog");
    }
}
