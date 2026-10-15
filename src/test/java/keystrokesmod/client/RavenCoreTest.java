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

    @Test
    public void testRGBSettingResetClampAndJsonRoundTrip() {
        keystrokesmod.client.module.setting.impl.RGBSetting rgb =
                new keystrokesmod.client.module.setting.impl.RGBSetting("Color", 10, 20, 30);
        assertEquals(10, rgb.getRed());
        assertEquals(20, rgb.getGreen());
        assertEquals(30, rgb.getBlue());
        int defaultPacked = new java.awt.Color(10, 20, 30).getRGB();
        assertEquals(defaultPacked, rgb.getRGB());

        // setColors clamps out-of-range values and updates packed RGB
        rgb.setColors(new int[]{300, -15, 128});
        assertEquals(255, rgb.getRed());
        assertEquals(0, rgb.getGreen());
        assertEquals(128, rgb.getBlue());
        assertEquals(new java.awt.Color(255, 0, 128).getRGB(), rgb.getRGB());

        // resetToDefaults must not throw ArrayIndexOutOfBoundsException and must restore packed RGB
        rgb.resetToDefaults();
        assertEquals(10, rgb.getRed());
        assertEquals(20, rgb.getGreen());
        assertEquals(30, rgb.getBlue());
        assertEquals(defaultPacked, rgb.getRGB());

        // JSON round-trip and malformed JSON resilience
        rgb.setColor(0, 100);
        rgb.setColor(1, 150);
        rgb.setColor(2, 200);
        JsonObject saved = rgb.getConfigAsJson();
        rgb.resetToDefaults();
        rgb.applyConfigFromJson(saved);
        assertEquals(100, rgb.getRed());
        assertEquals(150, rgb.getGreen());
        assertEquals(200, rgb.getBlue());
        assertEquals(new java.awt.Color(100, 150, 200).getRGB(), rgb.getRGB());

        rgb.applyConfigFromJson(null);
        rgb.applyConfigFromJson(new JsonObject());
        assertEquals(100, rgb.getRed());
    }

    @Test
    public void testSliderSettingRoundingClampAndReset() {
        keystrokesmod.client.module.setting.impl.SliderSetting slider =
                new keystrokesmod.client.module.setting.impl.SliderSetting("Reach", 3.2, 3.0, 6.0, 0.05);
        assertEquals(3.2, slider.getInput(), 1e-6);

        slider.setValue(9.5);
        assertEquals(6.0, slider.getInput(), 1e-6);

        slider.setValue(1.0);
        assertEquals(3.0, slider.getInput(), 1e-6);

        JsonObject json = slider.getConfigAsJson();
        assertEquals("slider", json.get("type").getAsString());
        json.addProperty("value", 4.45);
        slider.applyConfigFromJson(json);
        assertEquals(4.45, slider.getInput(), 1e-6);

        slider.resetToDefaults();
        assertEquals(3.2, slider.getInput(), 1e-6);
    }

    @Test
    public void testTickSettingAndDescriptionSettingJsonRoundTrip() {
        keystrokesmod.client.module.setting.impl.TickSetting tick =
                new keystrokesmod.client.module.setting.impl.TickSetting("Silent", false);
        assertFalse(tick.isToggled());
        tick.enable();
        assertTrue(tick.isToggled());

        JsonObject json = tick.getConfigAsJson();
        assertTrue(json.get("value").getAsBoolean());

        tick.resetToDefaults();
        assertFalse(tick.isToggled());

        tick.applyConfigFromJson(json);
        assertTrue(tick.isToggled());

        keystrokesmod.client.module.setting.impl.DescriptionSetting desc =
                new keystrokesmod.client.module.setting.impl.DescriptionSetting("Info text");
        assertEquals("Info text", desc.getDesc());
        desc.setDesc("Modified");
        desc.resetToDefaults();
        assertEquals("Info text", desc.getDesc());
    }

    @Test
    public void testHudPositionModeQuadrantsAndMidpoints() {
        double width = 800.0;
        double height = 600.0;

        // Top-left quadrant (marginX <= width/2, marginY <= height/2)
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.UPLEFT,
                keystrokesmod.client.utils.Utils.HUD.getPostitionMode(100, 150, width, height));

        // Bottom-left quadrant (marginX <= width/2, marginY > height/2)
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.DOWNLEFT,
                keystrokesmod.client.utils.Utils.HUD.getPostitionMode(100, 450, width, height));

        // Top-right quadrant (marginX > width/2, marginY <= height/2)
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.UPRIGHT,
                keystrokesmod.client.utils.Utils.HUD.getPostitionMode(700, 150, width, height));

        // Bottom-right quadrant (marginX > width/2, marginY > height/2)
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.DOWNRIGHT,
                keystrokesmod.client.utils.Utils.HUD.getPostitionMode(700, 450, width, height));

        // Exact center and midpoint boundaries (never returns null)
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.UPLEFT,
                keystrokesmod.client.utils.Utils.HUD.getPostitionMode(400, 200, width, height));
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.DOWNLEFT,
                keystrokesmod.client.utils.Utils.HUD.getPostitionMode(400, 400, width, height));
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.UPLEFT,
                keystrokesmod.client.utils.Utils.HUD.getPostitionMode(400, 300, width, height));
    }

    @Test
    public void testHudCoordinatesStringSerializationRoundTrip() {
        keystrokesmod.client.module.modules.HUD.hudX = 12;
        keystrokesmod.client.module.modules.HUD.hudY = 34;
        keystrokesmod.client.module.modules.HUD.watermarkX = 56;
        keystrokesmod.client.module.modules.HUD.watermarkY = 78;
        keystrokesmod.client.module.modules.HUD.keybindsX = 90;
        keystrokesmod.client.module.modules.HUD.keybindsY = 112;
        keystrokesmod.client.module.modules.HUD.targetHUDX = 134;
        keystrokesmod.client.module.modules.HUD.targetHUDY = 156;
        keystrokesmod.client.module.modules.render.Radar.radarX = 178;
        keystrokesmod.client.module.modules.render.Radar.radarY = 190;
        keystrokesmod.client.module.modules.HUD.positionMode =
                keystrokesmod.client.utils.Utils.HUD.PositionMode.UPRIGHT;

        String serialized = keystrokesmod.client.module.modules.HUD.buildHudDataString();

        // Corrupt in-memory values before restoring
        keystrokesmod.client.module.modules.HUD.hudX = 0;
        keystrokesmod.client.module.modules.HUD.hudY = 0;
        keystrokesmod.client.module.modules.HUD.watermarkX = 0;
        keystrokesmod.client.module.modules.HUD.watermarkY = 0;
        keystrokesmod.client.module.modules.HUD.keybindsX = 0;
        keystrokesmod.client.module.modules.HUD.keybindsY = 0;
        keystrokesmod.client.module.modules.HUD.targetHUDX = 0;
        keystrokesmod.client.module.modules.HUD.targetHUDY = 0;
        keystrokesmod.client.module.modules.render.Radar.radarX = 0;
        keystrokesmod.client.module.modules.render.Radar.radarY = 0;
        keystrokesmod.client.module.modules.HUD.positionMode =
                keystrokesmod.client.utils.Utils.HUD.PositionMode.DOWNLEFT;

        keystrokesmod.client.module.modules.HUD.parseHudDataString(serialized);

        assertEquals(12, keystrokesmod.client.module.modules.HUD.hudX);
        assertEquals(34, keystrokesmod.client.module.modules.HUD.hudY);
        assertEquals(56, keystrokesmod.client.module.modules.HUD.watermarkX);
        assertEquals(78, keystrokesmod.client.module.modules.HUD.watermarkY);
        assertEquals(90, keystrokesmod.client.module.modules.HUD.keybindsX);
        assertEquals(112, keystrokesmod.client.module.modules.HUD.keybindsY);
        assertEquals(134, keystrokesmod.client.module.modules.HUD.targetHUDX);
        assertEquals(156, keystrokesmod.client.module.modules.HUD.targetHUDY);
        assertEquals(178, keystrokesmod.client.module.modules.render.Radar.radarX);
        assertEquals(190, keystrokesmod.client.module.modules.render.Radar.radarY);
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.UPRIGHT,
                keystrokesmod.client.module.modules.HUD.positionMode);

        // Malformed coordinate entry should be skipped without aborting valid entries
        keystrokesmod.client.module.modules.HUD.parseHudDataString("hudX,bad,34;watermarkX,77,88;");
        assertEquals(12, keystrokesmod.client.module.modules.HUD.hudX);
        assertEquals(77, keystrokesmod.client.module.modules.HUD.watermarkX);
        assertEquals(88, keystrokesmod.client.module.modules.HUD.watermarkY);

        // setHudX / setHudY and resetAllPositions must invalidate e and reset positionMode / Radar
        keystrokesmod.client.module.modules.HUD.e = true;
        keystrokesmod.client.module.modules.HUD.setHudX(300);
        assertFalse(keystrokesmod.client.module.modules.HUD.e);

        keystrokesmod.client.module.modules.HUD.e = true;
        keystrokesmod.client.module.modules.HUD.resetAllPositions();
        assertFalse(keystrokesmod.client.module.modules.HUD.e);
        assertEquals(5, keystrokesmod.client.module.modules.HUD.hudX);
        assertEquals(70, keystrokesmod.client.module.modules.HUD.hudY);
        assertEquals(10, keystrokesmod.client.module.modules.render.Radar.radarX);
        assertEquals(120, keystrokesmod.client.module.modules.render.Radar.radarY);
        assertEquals(keystrokesmod.client.utils.Utils.HUD.PositionMode.UPLEFT,
                keystrokesmod.client.module.modules.HUD.positionMode);
    }

    @Test
    public void testColorBetweenAndKopamedColoursZeroSpeedSafety() {
        // max <= min must not throw ArithmeticException (/ by zero)
        assertEquals(100, keystrokesmod.client.utils.Utils.Client.getColorBetween(100, 100, 10));
        assertEquals(200, keystrokesmod.client.utils.Utils.Client.getColorBetween(200, 100, 10));

        int blended = keystrokesmod.client.utils.Utils.Client.getColorBetween(20, 200, 250);
        assertTrue(blended >= 20 && blended <= 200);

        // yOffset < yTotal and yTotal == 0 must not throw ArithmeticException (/ by zero)
        int kopamed1 = keystrokesmod.client.utils.Utils.Client.kopamedColoursDraw(0, 5);
        int kopamed2 = keystrokesmod.client.utils.Utils.Client.kopamedColoursDraw(5, 0);
        int astolfoZeroSpeed = keystrokesmod.client.utils.Utils.Client.astolfoColorsDraw(0, 10, 0.0f);
        int rainbowZeroSpeed = keystrokesmod.client.utils.Utils.Client.rainbowDraw(0L, 0L);
        int rainbowLargeSpeed = keystrokesmod.client.utils.Utils.Client.rainbowDraw(50000L, -999999999L);
        assertTrue(kopamed1 != 0 || kopamed2 != 0 || astolfoZeroSpeed != 0);
        assertTrue(rainbowZeroSpeed != 0 || rainbowLargeSpeed != 0);
    }

    @Test
    public void testJavaStringListAndRandomUtilitiesEdgeCases() {
        JsonObject obj = new JsonObject();
        obj.addProperty("num", "42");
        obj.add("nullVal", com.google.gson.JsonNull.INSTANCE);

        assertEquals(42, keystrokesmod.client.utils.Utils.Java.getValue(obj, "num"));
        assertEquals(0, keystrokesmod.client.utils.Utils.Java.getValue(obj, "nullVal"));
        assertEquals(0, keystrokesmod.client.utils.Utils.Java.getValue(obj, "missing"));
        assertEquals(0, keystrokesmod.client.utils.Utils.Java.getValue(null, "num"));

        assertEquals(1, keystrokesmod.client.utils.Utils.Java.indexOf("b", new String[]{"a", "b", "c"}));
        assertEquals(-1, keystrokesmod.client.utils.Utils.Java.indexOf("z", new String[]{"a", "b"}));
        assertEquals(-1, keystrokesmod.client.utils.Utils.Java.indexOf(null, new String[]{"a"}));

        assertEquals("Raven B Plus", keystrokesmod.client.utils.Utils.Java.capitalizeWord("raven B Plus"));
        assertEquals("", keystrokesmod.client.utils.Utils.Java.capitalizeWord(""));
        assertEquals("", keystrokesmod.client.utils.Utils.Java.capitalizeWord(null));

        assertTrue(keystrokesmod.client.utils.Utils.Java.toArrayList(null).isEmpty());
        assertTrue(keystrokesmod.client.utils.Utils.Java.StringListToList(null).isEmpty());
        assertEquals("", keystrokesmod.client.utils.Utils.Java.randomChoice(new String[0]));
        assertEquals("only", keystrokesmod.client.utils.Utils.Java.randomChoice(new String[]{"only"}));
    }

    @Test
    public void testUrlValidationAndPasteeIdExtraction() {
        assertFalse(keystrokesmod.client.utils.Utils.URLS.isLink(null));
        assertFalse(keystrokesmod.client.utils.Utils.URLS.isLink(""));
        assertFalse(keystrokesmod.client.utils.Utils.URLS.isLink("ftp://example.com"));
        assertTrue(keystrokesmod.client.utils.Utils.URLS.isLink("https://paste.ee/p/abcde"));

        assertEquals("https://api.paste.ee/v1/pastes/abcde",
                keystrokesmod.client.utils.Utils.URLS.makeRawPasteePaste("https://paste.ee/p/abcde"));
        assertEquals("", keystrokesmod.client.utils.Utils.URLS.makeRawPasteePaste("https://paste.ee/p/"));
        assertEquals("", keystrokesmod.client.utils.Utils.URLS.makeRawPasteePaste("/"));
        assertEquals("", keystrokesmod.client.utils.Utils.URLS.makeRawPasteePaste(null));
        assertEquals("", keystrokesmod.client.utils.Utils.URLS.makeRawPasteePaste("   "));
    }

    @Test
    public void testHypixelProfileParsingAndPlayerProfileSafety() {
        String validJson = "{\"success\":true,\"player\":{\"displayname\":\"TestUser\",\"stats\":{\"Duels\":{"
                + "\"wins\":120,\"losses\":30,\"current_winstreak\":15,"
                + "\"bridge_duel_wins\":45,\"bridge_duel_losses\":5,\"current_winstreak_mode_bridge_duel\":9"
                + "}}}}";

        int[] overall = keystrokesmod.client.utils.Utils.Profiles.getHypixelStats(
                validJson, keystrokesmod.client.utils.Utils.Profiles.DuelsStatsMode.OVERALL);
        assertArrayEquals(new int[]{120, 30, 15}, overall);

        int[] bridge = keystrokesmod.client.utils.Utils.Profiles.getHypixelStats(
                validJson, keystrokesmod.client.utils.Utils.Profiles.DuelsStatsMode.BRIDGE);
        assertArrayEquals(new int[]{45, 5, 9}, bridge);

        // Null DuelsStatsMode must not throw NullPointerException
        assertArrayEquals(new int[]{0, 0, 0},
                keystrokesmod.client.utils.Utils.Profiles.getHypixelStats(validJson, null));

        // Nicked / null player / malformed JSON handling
        assertArrayEquals(new int[]{-1, 0, 0}, keystrokesmod.client.utils.Utils.Profiles.getHypixelStats(
                "{\"success\":true,\"player\":null}", keystrokesmod.client.utils.Utils.Profiles.DuelsStatsMode.OVERALL));
        assertArrayEquals(new int[]{0, 0, 0}, keystrokesmod.client.utils.Utils.Profiles.getHypixelStats(
                "{invalid-json", keystrokesmod.client.utils.Utils.Profiles.DuelsStatsMode.OVERALL));

        keystrokesmod.client.utils.profile.PlayerProfile profile =
                new keystrokesmod.client.utils.profile.PlayerProfile("TestUser",
                        keystrokesmod.client.utils.Utils.Profiles.DuelsStatsMode.BRIDGE);
        profile.populateStatsFromJson(validJson);
        assertFalse(profile.nicked);
        assertEquals("TestUser", profile.inGameName);
        assertEquals(45, profile.wins);
        assertEquals(5, profile.losses);
        assertEquals(9, profile.winStreak);

        keystrokesmod.client.utils.profile.PlayerProfile nickedProfile =
                new keystrokesmod.client.utils.profile.PlayerProfile("NickedUser",
                        keystrokesmod.client.utils.Utils.Profiles.DuelsStatsMode.OVERALL);
        nickedProfile.populateStatsFromJson("{\"success\":true, \"player\": null}");
        assertTrue(nickedProfile.nicked);
    }

    @Test
    public void testBowAimbotBallisticTrajectoryCalculation() {
        // Zero horizontal distance must not produce NaN or Infinity
        float[] zeroDist = keystrokesmod.client.module.modules.combat.BowAimbot.calculateBowAngles(
                0.0, 5.0, 0.0, 0.0, 0.0, 20, false);
        assertFalse(Float.isNaN(zeroDist[0]) || Float.isInfinite(zeroDist[0]));
        assertFalse(Float.isNaN(zeroDist[1]) || Float.isInfinite(zeroDist[1]));
        assertEquals(-90.0f, zeroDist[1], 1e-4f);

        // Standard horizontal target at 20 blocks
        float[] fullDraw = keystrokesmod.client.module.modules.combat.BowAimbot.calculateBowAngles(
                20.0, 0.0, 0.0, 0.0, 0.0, 20, false);
        float[] halfDraw = keystrokesmod.client.module.modules.combat.BowAimbot.calculateBowAngles(
                20.0, 0.0, 0.0, 0.0, 0.0, 10, false);
        assertEquals(-90.0f, fullDraw[0], 1e-3f);
        // Half draw has lower arrow velocity, so it requires a steeper upward pitch (more negative pitch)
        assertTrue("Half draw should aim higher (more negative pitch) than full draw", halfDraw[1] < fullDraw[1]);
        assertTrue(halfDraw[1] >= -90.0f && halfDraw[1] <= 90.0f);
    }

    @Test
    public void testBridgeInfoOpponentNameParsing() {
        assertEquals("Technoblade",
                keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("   Opponent: Technoblade  "));
        assertEquals("Technoblade",
                keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("   Opponent: [MVP+] Technoblade  "));
        assertEquals("Technoblade",
                keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("[12:34:56] Opponent: Technoblade"));
        assertEquals("Technoblade",
                keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("[12:34:56] Opponent: [MVP+] Technoblade"));
        assertEquals("Player123",
                keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("Opponent:Player123"));
        assertNull(keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("Opponent: [VIP]   "));
        assertNull(keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("Opponent:   "));
        assertNull(keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName("Random chat message"));
        assertNull(keystrokesmod.client.module.modules.minigames.BridgeInfo.parseOpponentName(null));
    }

    @Test
    public void testEnhancedScaffoldSideToPlaceDirectionality() {
        net.minecraft.util.BlockPos target = new net.minecraft.util.BlockPos(10, 64, 10);
        for (net.minecraft.util.EnumFacing dir : net.minecraft.util.EnumFacing.values()) {
            net.minecraft.util.BlockPos supportBlock = target.offset(dir);
            net.minecraft.util.EnumFacing placeFace =
                    keystrokesmod.client.module.modules.world.EnhancedScaffold.getSideToPlace(supportBlock, target);
            assertNotNull("Adjacent support block must yield a valid placement face", placeFace);
            assertEquals("Clicking supportBlock on placeFace must offset to target",
                    target, supportBlock.offset(placeFace));
        }
        assertNull(keystrokesmod.client.module.modules.world.EnhancedScaffold.getSideToPlace(target, target));
        assertNull(keystrokesmod.client.module.modules.world.EnhancedScaffold.getSideToPlace(null, target));
    }

    @Test
    public void testMouseManagerClickCounterExpirationAndSeparation() {
        keystrokesmod.client.utils.MouseManager.clearClicks();
        assertEquals(0, keystrokesmod.client.utils.MouseManager.getLeftClickCounter());
        assertEquals(0, keystrokesmod.client.utils.MouseManager.getRightClickCounter());

        keystrokesmod.client.utils.MouseManager.addLeftClick();
        keystrokesmod.client.utils.MouseManager.addLeftClick();
        keystrokesmod.client.utils.MouseManager.addRightClick();

        assertEquals(2, keystrokesmod.client.utils.MouseManager.getLeftClickCounter());
        assertEquals(1, keystrokesmod.client.utils.MouseManager.getRightClickCounter());

        keystrokesmod.client.utils.MouseManager.clearClicks();
        assertEquals(0, keystrokesmod.client.utils.MouseManager.getLeftClickCounter());
        assertEquals(0, keystrokesmod.client.utils.MouseManager.getRightClickCounter());
    }
}
