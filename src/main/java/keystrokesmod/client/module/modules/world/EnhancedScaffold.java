package keystrokesmod.client.module.modules.world;

import java.util.concurrent.ThreadLocalRandom;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.GameLoopEvent;
import keystrokesmod.client.event.impl.LookEvent;
import keystrokesmod.client.event.impl.MoveInputEvent;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.block.Block;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

public class EnhancedScaffold extends Module {
    
    public static DescriptionSetting desc;
    public static TickSetting eagle, safeWalk, tower, fastPlace, rotate, antiFall;
    public static TickSetting showBlockCount, showInfo, diagonalMode;
    public static ComboSetting mode, towerMode, blockSwitch;
    public static SliderSetting speed, reach, delay, towerSpeed, expandRadius;
    public static SliderSetting rotationSpeed, blockSwitchDelay;
    
    private float yaw, pitch;
    private int blockCount;
    private long lastPlaceTime = 0;
    private int lastBlockSlot = -1;
    private int towerHeight = 0;
    private boolean shouldJump = false;
    private long lastJumpTime = 0;
    
    public enum ScaffoldMode {
        NORMAL, TOWER, BRIDGE, DIAGONAL
    }
    
    public enum TowerMode {
        JUMP, MOTION, VANILLA
    }
    
    public enum BlockSwitchMode {
        NONE, SMART, SWAP, INVENTORY
    }

    public EnhancedScaffold() {
        super("EnhancedScaffold", ModuleCategory.world);
        this.registerSetting(desc = new DescriptionSetting("Advanced block placement"));
        
        // Main settings
        this.registerSetting(mode = new ComboSetting("Mode", ScaffoldMode.NORMAL));
        this.registerSetting(rotate = new TickSetting("Rotate", true));
        this.registerSetting(fastPlace = new TickSetting("Fast Place", true));
        this.registerSetting(antiFall = new TickSetting("Anti Fall", true));
        this.registerSetting(diagonalMode = new TickSetting("Diagonal Mode", false));
        
        // Movement settings
        this.registerSetting(eagle = new TickSetting("Eagle", false));
        this.registerSetting(safeWalk = new TickSetting("Safe Walk", false));
        this.registerSetting(tower = new TickSetting("Tower", false));
        this.registerSetting(towerMode = new ComboSetting("Tower Mode", TowerMode.JUMP));
        this.registerSetting(towerSpeed = new SliderSetting("Tower Speed", 1.0, 0.5, 3.0, 0.1));
        
        // Block management
        this.registerSetting(blockSwitch = new ComboSetting("Block Switch", BlockSwitchMode.SMART));
        this.registerSetting(blockSwitchDelay = new SliderSetting("Switch Delay", 100, 0, 500, 10));
        this.registerSetting(expandRadius = new SliderSetting("Expand Radius", 0, 0, 3, 1));
        
        // Speed and precision
        this.registerSetting(speed = new SliderSetting("Place Speed", 8.0, 1.0, 20.0, 1.0));
        this.registerSetting(delay = new SliderSetting("Place Delay", 50, 0, 200, 10));
        this.registerSetting(reach = new SliderSetting("Reach", 4.5, 3.0, 6.0, 0.1));
        this.registerSetting(rotationSpeed = new SliderSetting("Rotation Speed", 30, 1, 180, 1));
        
        // Visual settings
        this.registerSetting(showBlockCount = new TickSetting("Show Block Count", true));
        this.registerSetting(showInfo = new TickSetting("Show Info", true));
    }

    @Override
    public void onEnable() {
        blockCount = 0;
        lastPlaceTime = 0;
        lastBlockSlot = Utils.Player.isPlayerInGame() ? mc.thePlayer.inventory.currentItem : -1;
        if (Utils.Player.isPlayerInGame()) {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
        }
        towerHeight = 0;
        shouldJump = false;
        lastJumpTime = 0;
    }

    @Override
    public void onDisable() {
        if (mc.gameSettings != null && mc.gameSettings.keyBindSneak != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        if (Utils.Player.isPlayerInGame() && lastBlockSlot >= 0 && lastBlockSlot < 9) {
            mc.thePlayer.inventory.currentItem = lastBlockSlot;
        }
        lastBlockSlot = -1;
        super.onDisable();
    }

    @Subscribe
    public void onGameLoopEvent(GameLoopEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        updateBlockCount();
        handleScaffold();
        handleTower();
        handleEagle();
        handleSafeWalk();
    }

    @Subscribe
    public void onUpdateEvent(UpdateEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        if (rotate.isToggled()) {
            event.setYaw(yaw);
            event.setPitch(pitch);
        }
        
        handleAntiFall();
    }

    @Subscribe
    public void onLookEvent(LookEvent event) {
        if (!Utils.Player.isPlayerInGame() || !rotate.isToggled()) return;
        
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    @Subscribe
    public void onMoveInputEvent(MoveInputEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        if (safeWalk.isToggled() && Utils.Player.playerOverAir()) {
            // Prevent walking off edges
            event.setForward(0);
            event.setStrafe(0);
        }
    }

    @Subscribe
    public void onRender2DEvent(Render2DEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        if (showBlockCount.isToggled() || showInfo.isToggled()) {
            renderInfo();
        }
    }

    private void handleScaffold() {
        ScaffoldMode currentMode = (ScaffoldMode) mode.getMode();
        
        switch (currentMode) {
            case NORMAL:
                placeBlockUnderPlayer();
                break;
            case TOWER:
                placeBlockUnderPlayer();
                break;
            case BRIDGE:
                placeBridgeBlocks();
                break;
            case DIAGONAL:
                placeDiagonalBlocks();
                break;
        }
    }

    private void placeBlockUnderPlayer() {
        BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0, mc.thePlayer.posZ);
        
        if (!mc.theWorld.isAirBlock(under)) return;
        
        BlockPos[] targets = getExpandPositions(under);
        BlockPos bestPos = null;
        EnumFacing bestSide = null;
        EnumFacing[] checkDirs = {EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};
        
        for (BlockPos target : targets) {
            if (!mc.theWorld.isAirBlock(target)) continue;
            
            for (EnumFacing dir : checkDirs) {
                BlockPos neighbor = target.offset(dir);
                EnumFacing placeSide = dir.getOpposite();
                if (!mc.theWorld.isAirBlock(neighbor) && canPlaceBlock(neighbor, placeSide)) {
                    double distance = mc.thePlayer.getDistanceSq(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5);
                    if (distance <= reach.getInput() * reach.getInput()) {
                        bestPos = neighbor;
                        bestSide = placeSide;
                        break;
                    }
                }
            }
            if (bestPos != null) break;
        }
        
        if (bestPos != null && bestSide != null) {
            if (rotate.isToggled()) {
                calculateRotations(bestPos.getX() + 0.5, bestPos.getY() + 0.5, bestPos.getZ() + 0.5);
            }
            
            if (canPlace()) {
                placeBlock(bestPos, bestSide);
            }
        }
    }

    private void placeBridgeBlocks() {
        BlockPos front = getBlockInFront();
        if (front == null) return;
        
        BlockPos under = front.down();
        if (!mc.theWorld.isAirBlock(under)) return;
        
        // Try to place block under the front position
        BlockPos[] positions = {
            front.down(), front.north(), front.south(), front.east(), front.west(),
            front.down().north(), front.down().south(), front.down().east(), front.down().west()
        };
        
        for (BlockPos pos : positions) {
            if (!mc.theWorld.isAirBlock(pos)) {
                EnumFacing side = getSideToPlace(pos, under);
                if (side != null && canPlaceBlock(pos, side)) {
                    if (rotate.isToggled()) {
                        calculateRotations(under.getX() + 0.5, under.getY() + 0.5, under.getZ() + 0.5);
                    }
                    
                    if (canPlace()) {
                        placeBlock(pos, side);
                    }
                    break;
                }
            }
        }
    }

    private void placeDiagonalBlocks() {
        // Place blocks in diagonal pattern for diagonal movement
        Vec3 motion = mc.thePlayer.getLookVec();
        double dx = motion.xCoord;
        double dz = motion.zCoord;
        
        // Normalize diagonal movement
        if (Math.abs(dx) > 0.1 && Math.abs(dz) > 0.1) {
            BlockPos target = new BlockPos(
                mc.thePlayer.posX + Math.signum(dx),
                mc.thePlayer.posY - 1,
                mc.thePlayer.posZ + Math.signum(dz)
            );
            
            if (mc.theWorld.isAirBlock(target)) {
                placeBlockUnderPlayer();
            }
        } else {
            placeBlockUnderPlayer();
        }
    }

    private BlockPos[] getExpandPositions(BlockPos center) {
        int radius = (int) expandRadius.getInput();
        if (radius == 0) {
            return new BlockPos[]{center};
        }
        
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                positions.add(new BlockPos(center.getX() + x, center.getY(), center.getZ() + z));
            }
        }
        
        return positions.toArray(new BlockPos[0]);
    }

    private BlockPos getBlockInFront() {
        Vec3 look = mc.thePlayer.getLookVec();
        double distance = 1.0;
        
        return new BlockPos(
            mc.thePlayer.posX + look.xCoord * distance,
            mc.thePlayer.posY,
            mc.thePlayer.posZ + look.zCoord * distance
        );
    }

    public static EnumFacing getSideToPlace(BlockPos from, BlockPos to) {
        if (from == null || to == null) return null;
        if (from.up().equals(to)) return EnumFacing.UP;
        if (from.down().equals(to)) return EnumFacing.DOWN;
        if (from.north().equals(to)) return EnumFacing.NORTH;
        if (from.south().equals(to)) return EnumFacing.SOUTH;
        if (from.east().equals(to)) return EnumFacing.EAST;
        if (from.west().equals(to)) return EnumFacing.WEST;
        return null;
    }

    private void handleTower() {
        if (!tower.isToggled()) return;
        
        TowerMode currentMode = (TowerMode) towerMode.getMode();
        
        switch (currentMode) {
            case JUMP:
                handleJumpTower();
                break;
            case MOTION:
                handleMotionTower();
                break;
            case VANILLA:
                handleVanillaTower();
                break;
        }
    }

    private void handleJumpTower() {
        if (mc.thePlayer.onGround && mc.gameSettings.keyBindJump.isKeyDown()) {
            long currentTime = System.currentTimeMillis();
            
            if (currentTime - lastJumpTime > 100) {
                mc.thePlayer.jump();
                lastJumpTime = currentTime;
                
                // Apply tower speed
                mc.thePlayer.motionY = 0.42f * (float) towerSpeed.getInput();
            }
        }
    }

    private void handleMotionTower() {
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.thePlayer.motionY = 0.42f * (float) towerSpeed.getInput();
        }
    }

    private void handleVanillaTower() {
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.thePlayer.motionY = 0.42f;
        }
    }

    private void handleEagle() {
        if (!eagle.isToggled()) return;
        
        boolean shouldSneak = Utils.Player.playerOverAir() && 
                           (mc.thePlayer.motionY < 0 || mc.gameSettings.keyBindJump.isKeyDown());
        
        if (shouldSneak) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
    }

    private void handleSafeWalk() {
        if (!safeWalk.isToggled()) return;
        
        // This is handled in MoveInputEvent
    }

    private void handleAntiFall() {
        if (!antiFall.isToggled()) return;
        
        if (mc.thePlayer.fallDistance > 2.0f && !mc.thePlayer.onGround) {
            BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0, mc.thePlayer.posZ);
            
            if (mc.theWorld.isAirBlock(under)) {
                // Try to place block to prevent fall damage
                placeBlockUnderPlayer();
            }
        }
    }

    private boolean canPlace() {
        long currentTime = System.currentTimeMillis();
        return currentTime - lastPlaceTime >= delay.getInput();
    }

    private boolean canPlaceBlock(BlockPos pos, EnumFacing side) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == null || block == Blocks.air || block.getMaterial().isLiquid()) {
            return false;
        }
        BlockPos offsetPos = pos.offset(side);
        return mc.theWorld.isAirBlock(offsetPos);
    }

    private void placeBlock(BlockPos pos, EnumFacing side) {
        if (!switchToBestBlock()) return;
        
        mc.playerController.onPlayerRightClick(
            mc.thePlayer,
            mc.theWorld,
            mc.thePlayer.getHeldItem(),
            pos,
            side,
            new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
        );
        
        mc.thePlayer.swingItem();
        lastPlaceTime = System.currentTimeMillis();
    }

    private boolean switchToBestBlock() {
        BlockSwitchMode switchMode = (BlockSwitchMode) blockSwitch.getMode();
        
        switch (switchMode) {
            case NONE:
                return mc.thePlayer.getHeldItem() != null && 
                       isBlock(mc.thePlayer.getHeldItem().getItem());
                       
            case SMART:
                return smartBlockSwitch();
                
            case SWAP:
                return swapToBestBlock();
                
            case INVENTORY:
                return inventoryBlockSwitch();
                
            default:
                return false;
        }
    }

    private boolean smartBlockSwitch() {
        int bestSlot = findBestBlockSlot();
        if (bestSlot == -1) return false;
        
        if (bestSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = bestSlot;
        }
        
        return true;
    }

    private boolean swapToBestBlock() {
        int bestSlot = findBestBlockSlot();
        if (bestSlot == -1) return false;
        
        if (bestSlot != mc.thePlayer.inventory.currentItem) {
            // Swap with current slot
            ItemStack current = mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem);
            ItemStack best = mc.thePlayer.inventory.getStackInSlot(bestSlot);
            
            mc.thePlayer.inventory.setInventorySlotContents(mc.thePlayer.inventory.currentItem, best);
            mc.thePlayer.inventory.setInventorySlotContents(bestSlot, current);
        }
        
        return true;
    }

    private boolean inventoryBlockSwitch() {
        // More complex inventory management - simplified for now
        return smartBlockSwitch();
    }

    private int findBestBlockSlot() {
        int bestSlot = -1;
        int bestCount = 0;
        
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && isBlock(stack.getItem())) {
                int count = stack.stackSize;
                if (count > bestCount) {
                    bestCount = count;
                    bestSlot = i;
                }
            }
        }
        
        return bestSlot;
    }

    private boolean isBlock(net.minecraft.item.Item item) {
        return item instanceof net.minecraft.item.ItemBlock;
    }

    private void calculateRotations(double x, double y, double z) {
        double dx = x - mc.thePlayer.posX;
        double dy = y - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = z - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, distance)));
        
        // Smooth rotation
        float maxStep = (float) rotationSpeed.getInput();
        if (maxStep > 0f && maxStep < 180f) {
            float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - yaw);
            float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - pitch);
            yaw += MathHelper.clamp_float(yawDiff, -maxStep, maxStep);
            pitch = MathHelper.clamp_float(pitch + MathHelper.clamp_float(pitchDiff, -maxStep, maxStep), -90.0f, 90.0f);
        } else {
            yaw = targetYaw;
            pitch = MathHelper.clamp_float(targetPitch, -90.0f, 90.0f);
        }
    }

    private void updateBlockCount() {
        blockCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && isBlock(stack.getItem())) {
                blockCount += stack.stackSize;
            }
        }
    }

    private void renderInfo() {
        ScaledResolution sr = new ScaledResolution(mc);
        int x = 5;
        int y = sr.getScaledHeight() - 50;
        
        if (showBlockCount.isToggled()) {
            String countText = "Blocks: " + blockCount;
            int color = blockCount <= 16 ? 0xFFFF0000 : 0xFF00FF00;
            FontUtil.normal.drawString(countText, x, y, color);
            y += 12;
        }
        
        if (showInfo.isToggled()) {
            ScaffoldMode currentMode = (ScaffoldMode) mode.getMode();
            String modeText = "Mode: " + currentMode.name();
            FontUtil.normal.drawString(modeText, x, y, 0xFFFFFF);
            y += 12;
            
            if (tower.isToggled()) {
                TowerMode towerMode = (TowerMode) this.towerMode.getMode();
                String towerText = "Tower: " + towerMode.name();
                FontUtil.normal.drawString(towerText, x, y, 0xFFFFFF);
            }
        }
    }
}
