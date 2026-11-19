package com.google.android.diskusage.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import com.google.android.diskusage.R;
import com.google.android.diskusage.databinding.ActivityPreferencesBinding;
import com.google.android.diskusage.filesystem.entity.FileSystemEntry;
import com.google.android.diskusage.filesystem.mnt.MountPoint;
import com.google.android.diskusage.filesystem.mnt.RootMountPoint;
import com.google.android.diskusage.ui.DiskUsage.FileSystemStats;
import com.google.android.diskusage.ui.common.EdgeToEdge;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selects which (root-only) mount points are offered in the volume chooser.
 * Migrated from the framework {@code PreferenceActivity}, which is deprecated
 * since API 28 and does not support dark mode or edge-to-edge layouts.
 */
public class ShowHideMountPointsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ActivityPreferencesBinding binding = ActivityPreferencesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.applyInsets(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setTitle(R.string.app_name);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new MountPointsFragment())
                    .commit();
        }
    }

    public static class MountPointsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.mount_points_ignore_list, rootKey);
            getPreferenceScreen().setOrderingAsAdded(true);
        }

        @Override
        public void onResume() {
            super.onResume();
            Context context = requireContext();
            List<MountPoint> mountPoints = RootMountPoint.getRootedMountPoints(context);
            PreferenceScreen prefs = getPreferenceScreen();
            prefs.removeAll();
            SharedPreferences shprefs =
                    context.getSharedPreferences("ignore_list", Context.MODE_PRIVATE);
            Map<String, ?> ignoreList = shprefs.getAll();
            Set<String> ignores = ignoreList.keySet();

            for (MountPoint mountPoint : mountPoints) {
                CheckBoxPreference pref = new CheckBoxPreference(context);
                FileSystemEntry.setupStrings(context);
                FileSystemStats stats = new FileSystemStats(mountPoint);
                pref.setSummary(stats.formatUsageInfo(context));
                pref.setTitle(mountPoint.getRoot());
                pref.setChecked(!ignores.contains(mountPoint.getRoot()));
                prefs.addPreference(pref);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            PreferenceScreen prefs = getPreferenceScreen();
            SharedPreferences shprefs =
                    requireContext().getSharedPreferences("ignore_list", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = shprefs.edit();
            editor.clear();

            for (int i = 0; i < prefs.getPreferenceCount(); i++) {
                CheckBoxPreference pref = (CheckBoxPreference) prefs.getPreference(i);
                String root = pref.getTitle().toString();
                if (!pref.isChecked()) {
                    editor.putBoolean(root, true);
                }
            }
            editor.apply();
        }
    }
}
