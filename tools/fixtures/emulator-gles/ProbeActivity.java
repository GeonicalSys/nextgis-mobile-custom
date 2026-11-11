package org.geonical.emulator.glesprobe;
import android.app.Activity;
import android.os.Bundle;
import java.io.PrintStream;
import org.json.JSONObject;

public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PrintStream previous = System.out;
        try (PrintStream output = new PrintStream(openFileOutput("proof.json", MODE_PRIVATE), true, "UTF-8")) {
            System.setOut(output);
            try {
                GlesUboProbe.main(new String[0]);
            } catch (Throwable failure) {
                output.println(new JSONObject().put("error", failure.toString()).toString());
            } finally {
                System.setOut(previous);
            }
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        finish();
    }
}
