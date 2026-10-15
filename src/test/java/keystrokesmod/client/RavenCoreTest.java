package keystrokesmod.client;

import com.google.gson.JsonObject;
import keystrokesmod.client.config.Config;
import keystrokesmod.client.config.ConfigManager;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.Timer;
import keystrokesmod.client.utils.version.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.*;

public class RavenCoreTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testVersionComparisonAndEdgeCases() {
        Version v120 = new Version("1.2.0", "stable", 10);
        Version v130 = new Version("1.3.0", "stable", 12);
        Version v130Dup = new Version("1-3-0", "beta", 15);
        Version v2Segment = new Version("1.3", "stable", 1);
        Version v4Segment = new Version("1.3.0.1", "stable", 1);

        assertTrue(v130.isNewerThan(v120));
        assertFalse(v120.isNewerThan(v130));
        assertFalse(v130.isNewerThan(v130Dup));
        assertTrue(v130.equals(v130Dup));
        assertEquals((Object) v130, (Object) v130Dup);
        assertEquals(v130.hashCode(), v130Dup.hashCode());

        // 2-segment "1.3" should normalize to 1.3.0
        assertTrue(v130.equals(v2Segment));
        // 4-segment "1.3.0.1" should be newer than "1.3.0"
        assertTrue(v4Segment.isNewerThan(v130));
        assertFalse(v130.isNewerThan(v4Segment));

        // Null and malformed inputs should not throw
        Version malformed = new Version("invalid.version", null, 0);
        assertEquals(Integer.valueOf(0), malformed.getVersionNumbers().get(0));
        assertTrue(v120.isNewerThan(malformed));
        assertTrue(v120.isNewerThan(null));
        assertFalse(v120.equals((Version) null));
    }

    @Test
    public void testConfigPersistenceAndOutdatedCheck() throws Exception {
        File cfgFile = new File(tempFolder.getRoot(), "test.bplus");
        assertTrue("Non-existent file should be considered outdated", ConfigManager.isOutdated(cfgFile));

        Config cfg = new Config(cfgFile);
        assertEquals("test", cfg.getName());
        assertTrue("Newly created empty file should be considered outdated until saved", ConfigManager.isOutdated(cfgFile));
        assertNull("Empty file getData() should return null without throwing", cfg.getData());

        JsonObject payload = new JsonObject();
        payload.addProperty("author", "tester");
        cfg.save(payload);

        assertFalse("Saved config should not be outdated", ConfigManager.isOutdated(cfgFile));
        JsonObject loaded = cfg.getData();
        assertNotNull(loaded);
        assertEquals("tester", loaded.get("author").getAsString());
        assertEquals(cfg.creationDate, loaded.get("creationTime").getAsLong());

        // Re-opening existing config preserves creationDate
        Config reopened = new Config(cfgFile);
        assertEquals(cfg.creationDate, reopened.creationDate);

        // Corrupt JSON file is safely detected as outdated
        File corruptFile = new File(tempFolder.getRoot(), "corrupt.bplus");
        try (FileWriter writer = new FileWriter(corruptFile)) {
            writer.write("{not-valid-json");
        }
        assertTrue(ConfigManager.isOutdated(corruptFile));
    }

    @Test
    public void testCoolDownLifecycle() throws Exception {
        CoolDown cd = new CoolDown(50);
        assertEquals(50L, cd.getCooldownTime());
        cd.start();
        assertFalse(cd.hasFinished());
        assertTrue(cd.getTimeLeft() > 0);

        cd.setCooldown(0);
        assertTrue(cd.hasFinished());
        assertTrue("First call to firstFinish() after expiry should return true", cd.firstFinish());
        assertFalse("Subsequent call to firstFinish() without restart should return false", cd.firstFinish());
        assertEquals(0L, cd.getTimeLeft());
    }

    @Test
    public void testTimerInterpolationIncreasingAndDecreasing() {
        for (int type = 0; type <= 3; type++) {
            Timer timerInc = new Timer(1000.0F);
            timerInc.start();
            float incVal = timerInc.getValueFloat(10.0F, 50.0F, type);
            assertTrue("Increasing timer value should stay within [10, 50]", incVal >= 10.0F && incVal <= 50.0F);

            Timer timerDec = new Timer(1000.0F);
            timerDec.start();
            float decVal = timerDec.getValueFloat(50.0F, 10.0F, type);
            assertTrue("Decreasing timer value should start near 50 and stay within [10, 50]", decVal > 10.0F && decVal <= 50.0F);
        }
    }

    private enum SampleMode {
        FIRST {
            @Override
            public String toString() {
                return "FIRST";
            }
        },
        SECOND,
        THIRD
    }

    @Test
    public void testDoubleSliderSettingRangeResetAndJsonClamping() {
        keystrokesmod.client.module.setting.impl.DoubleSliderSetting slider =
                new keystrokesmod.client.module.setting.impl.DoubleSliderSetting("Delay", 10.0, 20.0, 0.0, 100.0, 1.0);
        assertEquals(10.0, slider.getInputMin(), 1e-6);
        assertEquals(20.0, slider.getInputMax(), 1e-6);

        // Move range to [80, 90] (disjoint from default [10, 20])
        JsonObject moved = new JsonObject();
        moved.addProperty("type", "doubleslider");
        moved.addProperty("valueMin", 80.0);
        moved.addProperty("valueMax", 90.0);
        slider.applyConfigFromJson(moved);
        assertEquals(80.0, slider.getInputMin(), 1e-6);
        assertEquals(90.0, slider.getInputMax(), 1e-6);

        // Resetting back to default [10, 20] must not clamp min to stale valMax (90) or max to stale valMin (80)
        slider.resetToDefaults();
        assertEquals(10.0, slider.getInputMin(), 1e-6);
        assertEquals(20.0, slider.getInputMax(), 1e-6);

        // Applying JSON with inverted min/max should swap and clamp within [0, 100]
        JsonObject inverted = new JsonObject();
        inverted.addProperty("type", "doubleslider");
        inverted.addProperty("valueMin", 75.0);
        inverted.addProperty("valueMax", 25.0);
        slider.applyConfigFromJson(inverted);
        assertEquals(25.0, slider.getInputMin(), 1e-6);
        assertEquals(75.0, slider.getInputMax(), 1e-6);
    }

    @Test
    public void testComboSettingWithAnonymousEnumConstant() {
        keystrokesmod.client.module.setting.impl.ComboSetting<SampleMode> combo =
                new keystrokesmod.client.module.setting.impl.ComboSetting<>("Mode", SampleMode.FIRST);
        assertEquals(SampleMode.FIRST, combo.getMode());
        combo.nextMode();
        assertEquals(SampleMode.SECOND, combo.getMode());
        combo.prevMode();
        assertEquals(SampleMode.FIRST, combo.getMode());

        JsonObject json = combo.getConfigAsJson();
        assertEquals("mode", json.get("type").getAsString());
        json.addProperty("value", "THIRD");
        combo.applyConfigFromJson(json);
        assertEquals(SampleMode.THIRD, combo.getMode());

        combo.resetToDefaults();
        assertEquals(SampleMode.FIRST, combo.getMode());
    }
}
