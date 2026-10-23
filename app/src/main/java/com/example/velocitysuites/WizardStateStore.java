package com.example.velocitysuites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Keeps an unfinished booking or reservation across the app being killed in the background. The wizard state (dates, rooms, amenities,
 * names, ages, ID choice and so on) is written to one private cache file when the screen goes away and read back when the system recreates
 * it; the file is deleted as soon as the wizard is finished or discarded. A file is only used for the same kind of wizard (booking or
 * reservation) and never for a Modify, which is rebuilt from the reservation itself.
 */
final class WizardStateStore {

    private static final String FILE = "wizard_state.bin";

    private WizardStateStore() {
    }

    private static File file(Context c) {
        return new File(c.getCacheDir(), FILE);
    }

    static void save(@NonNull Context c, @NonNull BookingWizardState state) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file(c)))) {
            out.writeObject(state);
        } catch (Exception e) {
            file(c).delete(); // a half-written file must never be read back
        }
    }

    @Nullable
    static BookingWizardState load(@NonNull Context c, @NonNull BookingWizardState.Mode mode) {
        File f = file(c);
        if (!f.exists()) return null;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            Object read = in.readObject();
            if (read instanceof BookingWizardState && ((BookingWizardState) read).mode == mode) return (BookingWizardState) read;
        } catch (Exception ignored) {
            // unreadable (an older app version wrote it): start over
        }
        return null;
    }

    static void clear(@NonNull Context c) {
        file(c).delete();
    }
}
