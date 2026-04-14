package com.example.verson1;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Consistent sign-out confirmation and toolbar wiring across the app.
 */
public final class LogoutUiHelper {

    private LogoutUiHelper() {}

    /** Shows confirmation, then {@link SessionManager#logoutToLogin(android.content.Context)}. */
    public static void showConfirmLogout(@NonNull AppCompatActivity activity) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.yes, (d, w) -> SessionManager.logoutToLogin(activity))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Handles overflow item {@link R.id#action_logout}. Use with
     * {@link MaterialToolbar#setOnMenuItemClickListener}.
     */
    public static boolean onMenuItemLogout(@NonNull AppCompatActivity activity, int menuItemId) {
        if (menuItemId == R.id.action_logout) {
            showConfirmLogout(activity);
            return true;
        }
        return false;
    }

    /**
     * Attach listener for {@code app:menu="@menu/menu_toolbar_sign_out"} (or any menu containing
     * {@code R.id.action_logout}).
     */
    public static void attachSignOutMenu(@NonNull AppCompatActivity activity, @NonNull MaterialToolbar toolbar) {
        toolbar.setOnMenuItemClickListener(item -> onMenuItemLogout(activity, item.getItemId()));
    }
}
