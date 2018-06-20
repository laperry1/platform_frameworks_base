/*
 * Copyright (C) 2018 AOSiP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.settingslib;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import java.util.List;
import java.util.Arrays;
import com.android.internal.R;


public class ThemeUtils {
    private static final String TAG = "ThemeUtils";

    private static final IOverlayManager mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));

    private static final int mCurrentUserId = UserHandle.USER_CURRENT;

    private static final String ACCENTS_PACKAGE_NAME_PREFIX = "com.accents.";
    private static final String ACCENTS[] = { "red", "pink", "purple", "deeppurple", "indigo",
            "blue", "lightblue", "cyan", "teal", "green", "lightgreen", "lime",
            "yellow", "amber", "orange", "deeporange", "brown", "grey",
            "bluegrey", canUseBlackAccent() ? "black" : "white" };
    private static final String DARK_THEME_PACKAGES[] = { "com.android.system.theme.dark",
            "com.android.settings.theme.dark", "com.android.dui.theme.dark",
            "com.android.settings.gboard.dark", "com.android.updater.theme.dark" };
    private static final String BLACK_THEME_PACKAGES[] = { "com.android.system.theme.blackaf",
            "com.android.settings.theme.blackaf", "com.android.dui.theme.blackaf",
            "com.android.settings.gboard.blackaf", "com.android.updater.theme.blackaf" };

    private static final String SUBSTRATUM_VERSION_METADATA = "Substratum_Version";
    private static final String[] WHITELISTED_OVERLAYS = { "android.auto_generated_rro__" };

    private static final String[] getClocks(Context ctx) {
        final String list = ctx.getResources().getString(R.string.custom_clock_styles);
        return list.split(",");
    }

    private static boolean isSubstratumOverlay( Context mContext, String packageName) {
        try {
            ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                    packageName, PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                int returnMetadata = appInfo.metaData.getInt(SUBSTRATUM_VERSION_METADATA);
                if (String.valueOf(returnMetadata) != null) {
                    return true;
                }
            }
        } catch (final Exception ignored) {
        }
        return false;
    }

    public static boolean isSubstratumOverlayInstalled(Context mContext) {
        try {
            List<OverlayInfo> overlayInfoList =
                    mOverlayManager.getOverlayInfosForTarget("android", mCurrentUserId);
            for (OverlayInfo overlay : overlayInfoList) {
                if (Arrays.asList(WHITELISTED_OVERLAYS).contains(overlay.packageName)) continue;
                if (isSubstratumOverlay(mContext, overlay.packageName))
                    return true;
            }
        } catch (final RemoteException ignored) {
        }
        return false;
    }

     // Check if black accent should be used
    public static boolean canUseBlackAccent() {
        return !isUsingDarkTheme() && !isUsingBlackAFTheme();
    }

    public static boolean isUsingDarkTheme() {
        return isOverlayEnabled("com.android.system.theme.dark");
    }

    private static boolean isUsingBlackAFTheme() {
        return isOverlayEnabled("com.android.system.theme.blackaf");
    }

    // Checks if the overlay is enabled
    private static boolean isOverlayEnabled(String packageName) {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = mOverlayManager.getOverlayInfo(packageName,
                    mCurrentUserId);
        } catch (final RemoteException e) {
            e.printStackTrace();
        }
        return themeInfo != null && themeInfo.isEnabled();
    }

    // Switches theme accent from to another or back to stock
    public static void updateAccents(Context mContext) {
        int accentSetting = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.ACCENT_PICKER, 0, mCurrentUserId);

        if (accentSetting == 0) {
            unloadAccents(mContext);
        } else {
            try {
                mOverlayManager.setEnabled(
                    ACCENTS_PACKAGE_NAME_PREFIX + ACCENTS[accentSetting - 1],
                    true, mCurrentUserId);
            } catch (RemoteException | IndexOutOfBoundsException e) {
                Log.w(TAG, "Can't change theme", e);
            }
        }
    }

    // Unload all the theme accents
    public static void unloadAccents(Context mContext) {
        try {
            for (String accent : ACCENTS) {
                mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + accent,
                        false, mCurrentUserId);
            }
        } catch (final RemoteException e) {
            e.printStackTrace();
        }
    }

    // Check for black and white accent overlays
    public static void unfuckBlackWhiteAccent() {
        OverlayInfo themeInfo = null;
        try {
             if (!canUseBlackAccent()) {
                themeInfo = mOverlayManager.getOverlayInfo(ACCENTS_PACKAGE_NAME_PREFIX + "black",
                        mCurrentUserId);
                if (themeInfo != null && themeInfo.isEnabled()) {
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "black",
                            false /*disable*/, mCurrentUserId);
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "white",
                            true, mCurrentUserId);
                }
            } else {
                themeInfo = mOverlayManager.getOverlayInfo(ACCENTS_PACKAGE_NAME_PREFIX + "white",
                        mCurrentUserId);
                if (themeInfo != null && themeInfo.isEnabled()) {
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "white",
                            false /*disable*/, mCurrentUserId);
                    mOverlayManager.setEnabled(ACCENTS_PACKAGE_NAME_PREFIX + "black",
                            true, mCurrentUserId);
                }
            }
        } catch (final RemoteException e) {
            e.printStackTrace();
        }
    }

    // Unloads the stock dark theme
    private static void unloadStockDarkTheme() {
        OverlayInfo themeInfo = null;
        try {
            themeInfo = mOverlayManager.getOverlayInfo("com.android.systemui.theme.dark",
                    mCurrentUserId);
            if (themeInfo != null && themeInfo.isEnabled()) {
                mOverlayManager.setEnabled("com.android.systemui.theme.dark",
                        false /*disable*/, mCurrentUserId);
            }
        } catch (final RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void handleDarkThemeEnablement(boolean useDarkTheme, boolean useBlackAFTheme) {
        // Check for black and white accent so we don't end up
        // with white on white or black on black
        unfuckBlackWhiteAccent();
        if (useDarkTheme || useBlackAFTheme) unloadStockDarkTheme();
        if (isUsingDarkTheme() != useDarkTheme) {
            for (String overlay : DARK_THEME_PACKAGES) {
                try {
                    mOverlayManager.setEnabled(overlay, useDarkTheme, mCurrentUserId);
                } catch (final RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
            }
        }
        if (isUsingBlackAFTheme() != useBlackAFTheme) {
            for (String overlay: BLACK_THEME_PACKAGES) {
                try {
                    mOverlayManager.setEnabled(overlay, useBlackAFTheme, mCurrentUserId);
                } catch (final RemoteException e) {
                    Log.w(TAG, "Can't change theme", e);
                }
            }
        }
    }

    // Switches the analog clock from one to another or back to stock
    public static void updateClocks(IOverlayManager om, int userId, int clockSetting, Context ctx) {
        // all clock already unloaded due to StatusBar observer unloadClocks call
        // set the custom analog clock overlay
        if (clockSetting > 4) {
            try {
                final String[] clocks = getClocks(ctx);
                om.setEnabled(clocks[clockSetting],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change analog clocks", e);
            }
        }
    }

    // Unload all the analog clocks
    public static void unloadClocks(IOverlayManager om, int userId, Context ctx) {
        // skip index 0
        final String[] clocks = getClocks(ctx);
        for (int i = 1; i < clocks.length; i++) {
            String clock = clocks[i];
            try {
                om.setEnabled(clock,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
