/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.google.android.diskusage.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.ActivityCommonBinding;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.ui.common.EdgeToEdge;
import com.google.android.diskusage.ui.common.Permissions;
import splitties.toast.ToastKt;

/**
 * Makes sure the app can read the storage before {@link DiskUsage} starts.
 *
 * Only "All files access" (Android 11+) is mandatory — without it the scanner
 * sees nothing but the app's own directory. Usage access is optional (exact app
 * sizes) and is therefore not requested here any more: blocking on it confused
 * users on ROMs like HyperOS, where the toggle does not always map to the app
 * op. It can be granted later from within the graph (menu → Permissions).
 */
public class PermissionRequestActivity extends AppCompatActivity {
    private static final int DISKUSAGE_REQUEST_CODE = 10;

    private boolean forwarded = false;

    private final ActivityResultLauncher<String[]> legacyStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> forwardToDiskUsage());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityCommonBinding binding = ActivityCommonBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.applyInsets(binding.getRoot());

        Intent i = getIntent();
        final String key = i.getStringExtra(DiskUsage.KEY_KEY);
        if (key == null) {
            finish();
            return;
        }
        MountPoint mountPoint = MountPoint.getForKey(this, key);
        if (mountPoint == null) {
            finish();
            return;
        }

        if (Permissions.hasAllFilesAccess()) {
            forwardToDiskUsage();
        } else {
            showAllFilesAccessDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check instead of relying on the result callback: several ROMs do not
        // deliver it, or return the user to a different task.
        if (!forwarded && Permissions.hasAllFilesAccess()) {
            forwardToDiskUsage();
        }
    }

    private void showAllFilesAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_all_files_access_title)
                .setMessage(R.string.dialog_all_files_access_desc)
                .setPositiveButton(R.string.button_open_settings, (dialog, which) -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Permissions.openAllFilesAccessSettings(this);
                    } else {
                        legacyStoragePermissionLauncher.launch(new String[] {
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    public void forwardToDiskUsage() {
        if (forwarded) return;
        forwarded = true;
        Intent input = getIntent();
        Intent diskusage = new Intent(this, DiskUsage.class);
        diskusage.putExtra(DiskUsage.KEY_KEY, input.getStringExtra(DiskUsage.KEY_KEY));
        diskusage.putExtra(DiskUsage.STATE_KEY, input.getBundleExtra(DiskUsage.STATE_KEY));
        startActivityForResult(diskusage, DISKUSAGE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DISKUSAGE_REQUEST_CODE) {
            setResult(0, data);
            finish();
        }
    }
}
