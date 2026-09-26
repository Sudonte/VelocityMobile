package com.example.velocitysuites;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

public abstract class BaseNavigationActivity extends AppCompatActivity {

    protected DrawerLayout drawerLayout;
    protected NavigationView navigationView;
    private View offlineBanner;
    private ConnectivityManager.NetworkCallback networkCallback;

    protected void setupGuestNavigation(int selectedId) {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        attachOfflineBanner();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END);
                } else {
                    setEnabled(false);
                    BaseNavigationActivity.super.onBackPressed();
                    setEnabled(true);
                }
            }
        });

        if (navigationView != null) {
            if (selectedId != View.NO_ID && selectedId != 0) {
                navigationView.setCheckedItem(selectedId);
            }
            
            // Style Logout Item specifically: Red and Bold
            android.view.MenuItem logoutItem = navigationView.getMenu().findItem(R.id.nav_logout);
            if (logoutItem != null) {
                android.text.SpannableString s = new android.text.SpannableString(logoutItem.getTitle());
                s.setSpan(new android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.velocity_red_primary)), 0, s.length(), 0);
                s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, s.length(), 0);
                logoutItem.setTitle(s);
                
                android.graphics.drawable.Drawable icon = logoutItem.getIcon();
                if (icon != null) {
                    icon.setTint(ContextCompat.getColor(this, R.color.velocity_red_primary));
                }
            }

            // Set header dynamic name if view exists
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                android.widget.TextView appNameLabel = headerView.findViewById(R.id.appNameLabel);
                android.widget.TextView taglineLabel = headerView.findViewById(R.id.taglineLabel);
                if (taglineLabel == null) {
                    taglineLabel = headerView.findViewById(R.id.hotelTagline);
                }
                SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
                String currentUserName = prefs.getString("userName", "");
                if (currentUserName != null && !currentUserName.isEmpty() && taglineLabel != null) {
                    taglineLabel.setText(getString(R.string.hello_user, currentUserName));
                }
            }

            navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_logout) {
                    showLogoutConfirmation();
                } else {
                    handleNavigation(itemId);
                }
                drawerLayout.postDelayed(() -> drawerLayout.closeDrawer(GravityCompat.END), 250);
                return true;
            });
        }

        // Menu icon + "Menu" label are one combined clickable control
        // (menuButtonControl wraps both in guest_header.xml) - a single
        // listener here, not one per child, avoids opening the drawer twice.
        View menuButtonControl = findViewById(R.id.menuButtonControl);
        if (menuButtonControl != null && drawerLayout != null) {
            menuButtonControl.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        }

        // Header notification icon navigates to the Notifications screen
        // consistently across every guest screen that includes guest_header.xml.
        View headerNotification = findViewById(R.id.headerNotificationContainer);
        if (headerNotification != null) {
            headerNotification.setOnClickListener(v -> openScreen(NotificationActivity.class));
        }

        // Header profile thumbnail navigates to Profile Management, same
        // destination as tapping it anywhere else it appears.
        View headerProfile = findViewById(R.id.headerProfileContainer);
        if (headerProfile != null) {
            headerProfile.setOnClickListener(v -> openScreen(ProfileManagementActivity.class));
        }

        // Align IDs for guest_header.xml if used
        refreshHeader();
    }

    /**
     * Injects a persistent "No Internet Connection" banner pinned to the top
     * of the Activity's own decor content, above whatever the subclass's own
     * setContentView layout is - a single hook shared by every guest screen
     * instead of adding this view to 10 separate layout files.
     */
    private void attachOfflineBanner() {
        ViewGroup contentRoot = findViewById(android.R.id.content);
        if (contentRoot == null) return;
        offlineBanner = LayoutInflater.from(this).inflate(R.layout.view_offline_banner, contentRoot, false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.TOP;
        contentRoot.addView(offlineBanner, params);
        offlineBanner.setVisibility(NetworkUtils.isOnline(this) ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (offlineBanner == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> updateOfflineBannerVisibility());
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> updateOfflineBannerVisibility());
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                runOnUiThread(() -> updateOfflineBannerVisibility());
            }
        };
        cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkCallback);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered (e.g. callback never actually attached to a live network).
            }
        }
        networkCallback = null;
    }

    private void updateOfflineBannerVisibility() {
        if (offlineBanner == null) return;
        offlineBanner.setVisibility(NetworkUtils.isOnline(this) ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotificationBadge();
        // Picture may have changed since this screen was last on top (e.g.
        // the guest just edited it in Profile Management) - keep it fresh,
        // same reasoning as the notification badge refresh above.
        refreshHeader();
    }

    /**
     * Pulls the latest notifications and repaints the header's unread badge.
     * Shown on every guest screen that includes guest_header.xml; a no-op
     * on screens without the badge view.
     */
    protected void refreshNotificationBadge() {
        updateNotificationBadge();
        RoomRepository.getInstance(this).refreshNotifications(
                new RoomRepository.RepositoryCallback<java.util.List<Notification>>() {
                    @Override
                    public void onSuccess(java.util.List<Notification> result) {
                        updateNotificationBadge();
                    }

                    @Override
                    public void onError(String message) {
                        // Keep whatever count we already had; no badge change on failure.
                    }
                });
    }

    protected void updateNotificationBadge() {
        View badgeView = findViewById(R.id.headerNotificationBadge);
        if (!(badgeView instanceof android.widget.TextView)) {
            return;
        }
        int unread = 0;
        for (Notification notification : RoomRepository.getInstance(this).getNotifications()) {
            if (!notification.isRead()) {
                unread++;
            }
        }
        android.widget.TextView badge = (android.widget.TextView) badgeView;
        if (unread > 0) {
            badge.setText(unread > 99 ? getString(R.string.unread_badge_overflow) : String.valueOf(unread));
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    protected void refreshHeader() {
        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        String currentUserName = prefs.getString("userName", "");
        String profilePictureUrl = com.example.velocitysuites.network.SessionManager.getProfilePictureUrl(this);

        // Update header greeting in the activity layout
        android.widget.TextView headerTagline = findViewById(R.id.headerTagline);
        if (headerTagline != null && currentUserName != null && !currentUserName.isEmpty()) {
            headerTagline.setText(getString(R.string.hello_user, currentUserName));
        }

        // Header profile thumbnail - same source/fallback as every other
        // place this picture appears (e.g. DashboardActivity's Welcome/
        // Account Summary cards); a no-op on any screen without the view.
        ImageView headerProfileImage = findViewById(R.id.headerProfileImage);
        if (headerProfileImage != null) {
            if (profilePictureUrl == null || profilePictureUrl.isEmpty()) {
                headerProfileImage.setImageResource(R.drawable.img_profile_placeholder);
            } else {
                com.bumptech.glide.Glide.with(this)
                        .load(profilePictureUrl)
                        .circleCrop()
                        .placeholder(R.drawable.img_profile_placeholder)
                        .error(R.drawable.img_profile_placeholder)
                        .into(headerProfileImage);
            }
        }

        // Update greeting in the navigation drawer header
        if (navigationView != null) {
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                android.widget.TextView navTaglineLabel = headerView.findViewById(R.id.hotelTagline);
                if (navTaglineLabel != null && currentUserName != null && !currentUserName.isEmpty()) {
                    navTaglineLabel.setText(getString(R.string.hello_user, currentUserName));
                }
            }
        }
    }

    private void showLogoutConfirmation() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of your session?")
                .setPositiveButton("Log Out", (dialog, which) -> logout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleNavigation(int itemId) {
        Class<?> destination = null;
        if (itemId == R.id.nav_dashboard) destination = DashboardActivity.class;
        else if (itemId == R.id.nav_room_browsing) destination = RoomBrowsingActivity.class;
        else if (itemId == R.id.nav_booking_reservation) destination = BookingAndReservationActivity.class;
        else if (itemId == R.id.nav_payment) destination = PaymentActivity.class;
        else if (itemId == R.id.nav_transaction_history) destination = TransactionHistoryActivity.class;
        else if (itemId == R.id.nav_profile_management) destination = ProfileManagementActivity.class;

        if (destination != null) {
            openScreen(destination);
        }
    }

    protected void setDestinationClick(int viewId, Class<?> destination) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(v -> openScreen(destination));
        }
    }

    protected void openScreen(Class<?> destination) {
        if (destination == null || getClass().equals(destination)) {
            return;
        }
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in_slide_up, R.anim.fade_out_scale);
    }

    protected void animateScreenContent() {
        View screenContent = findViewById(R.id.screenContent);
        if (screenContent == null) {
            return;
        }
        screenContent.setAlpha(0f);
        screenContent.setTranslationY(18f);
        screenContent.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280L)
                .start();
    }

    /**
     * Dismisses a dialog shown around an async operation (Retrofit callback, background
     * Thread + runOnUiThread) - safe to call even if the guest has already navigated away
     * or the Activity was destroyed while the request was in flight. Every subclass here
     * (BookingAndReservationActivity/TransactionListActivity's delete flows,
     * ProfileManagementActivity's account-delete/profile-save/password-reset,
     * TransactionHistoryActivity's export) shares this one implementation rather than
     * each keeping its own copy - mirrors PaymentActivity's/WizardStepFragment's
     * identical helper for the two call sites that aren't part of this hierarchy.
     */
    protected void dismissSafely(@Nullable AlertDialog dialog) {
        if (dialog == null || isFinishing() || isDestroyed()) return;
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (IllegalArgumentException ignored) {
            // Window already gone - nobody is looking at this dialog either way.
        }
    }

    protected void logout() {
        com.example.velocitysuites.network.ApiClient.getService(this).logout().enqueue(
                new retrofit2.Callback<com.example.velocitysuites.network.dto.ApiMessage>() {
                    @Override
                    public void onResponse(retrofit2.Call<com.example.velocitysuites.network.dto.ApiMessage> call,
                                            retrofit2.Response<com.example.velocitysuites.network.dto.ApiMessage> response) {
                    }

                    @Override
                    public void onFailure(retrofit2.Call<com.example.velocitysuites.network.dto.ApiMessage> call, Throwable t) {
                    }
                });

        SharedPreferences prefs = getSharedPreferences("VelocityPrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        // The RoomRepository singleton otherwise survives a logout/login within the
        // same process - without this, the next account's data could momentarily
        // fall back to this account's stale cached bookings/notifications if its
        // first refresh call hits a transient error (see clearAccountSpecificCache()'s own doc).
        RoomRepository.getInstance(this).clearAccountSpecificCache();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

