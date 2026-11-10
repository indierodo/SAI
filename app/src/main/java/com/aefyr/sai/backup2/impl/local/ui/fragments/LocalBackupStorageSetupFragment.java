package com.aefyr.sai.backup2.impl.local.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.aefyr.sai.R;
import com.aefyr.sai.backup2.impl.local.ui.viewmodels.LocalBackupStorageSetupViewModel;
import com.aefyr.sai.ui.fragments.SaiBaseFragment;

public class LocalBackupStorageSetupFragment extends SaiBaseFragment {
    private static final int REQUEST_CODE_SELECT_BACKUP_DIR = 1334;
    private LocalBackupStorageSetupViewModel mViewModel;

    @Override
    protected int layoutId() {
        return R.layout.fragment_local_backup_storage_setup;
    }

    public static LocalBackupStorageSetupFragment newInstance() {
        return new LocalBackupStorageSetupFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewModel = new ViewModelProvider(this).get(LocalBackupStorageSetupViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button selectDirButton = findViewById(R.id.button_lbs_select_dir);
        selectDirButton.setOnClickListener(v -> selectBackupDir());
        selectDirButton.requestFocus(); //TV fix
    }

    private void selectBackupDir() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.settings_main_pick_dir)), REQUEST_CODE_SELECT_BACKUP_DIR);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_CODE_SELECT_BACKUP_DIR || resultCode != Activity.RESULT_OK || data == null || data.getData() == null)
            return;

        Uri backupDirUri = data.getData();
        requireContext().getContentResolver().takePersistableUriPermission(backupDirUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        mViewModel.setBackupDir(backupDirUri);
    }
}
