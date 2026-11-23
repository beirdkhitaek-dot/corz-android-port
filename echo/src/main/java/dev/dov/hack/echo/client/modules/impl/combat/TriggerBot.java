package dev.dov.hack.echo.client.modules.impl.combat;

import dev.dov.hack.echo.client.EchoClient;
import dev.dov.hack.echo.client.init.CatalogText;
import dev.dov.hack.echo.client.event.Subscribe;
import dev.dov.hack.echo.client.event.input.AttackKeyEvent;
import dev.dov.hack.echo.client.event.network.PacketReceiveEvent;
import dev.dov.hack.echo.client.modules.Module;
import dev.dov.hack.echo.client.handlers.AttackCooldownTracker;
import dev.dov.hack.echo.client.render.bridge.MinecraftRenderState;
import dev.dov.hack.echo.client.settings.BooleanSetting;
import dev.dov.hack.echo.client.settings.ChoiceSetting;
import dev.dov.hack.echo.client.settings.FloatRangeSetting;
import dev.dov.hack.echo.client.settings.FloatSetting;
import dev.dov.hack.echo.client.settings.HotbarSlotsSetting;
import dev.dov.hack.echo.client.settings.ItemListSetting;
import dev.dov.hack.echo.client.utils.CooldownTimer;
import dev.dov.hack.echo.client.utils.strings.Concat;
import dev.dov.hack.echo.client.utils.targeting.EntityPredicates;
import dev.dov.hack.echo.client.utils.targeting.EntityRaycasts;
import dev.dov.hack.echo.client.utils.targeting.TargetSelection;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.hit.EntityHitResult;

public class TriggerBot extends Module {
    private static final CharSequence CRIT_NONE = CatalogText.literal("None");
    private static final CharSequence CRIT_PRECISE = CatalogText.literal("pCrit");
    private static final CharSequence CRIT_NORMAL = CatalogText.literal("Normal");
    private static final CharSequence EMPTY_SUFFIX = Concat.strings();
    private static ChoiceSetting critMode;
    private static BooleanSetting critOnlyOnSpace;
    private static BooleanSetting attackShields;
    private static BooleanSetting hurtTimeCheck;
    private static BooleanSetting noSweepOrPickHits;
    private static FloatRangeSetting cooldown;
    private static FloatRangeSetting critCooldown;
    private static FloatRangeSetting critRange;
    private static FloatSetting attackDistance;
    private static FloatRangeSetting reactionDelay;
    private static FloatRangeSetting attackDelay;
    private static ItemListSetting itemWhitelist;
    private static HotbarSlotsSetting hotbarWhitelist;
    private CooldownTimer reactionTimer;
    private CooldownTimer attackTimer;
    private CooldownTimer criticalWindowTimer;
    private boolean targetAcquired;
    private long lastAttackTick;
    private long reactionDelayMillis;
    private long attackDelayMillis;
    private double sampledAttackCooldown;
    private static String cooldownTooltip;
    private static String timingTooltip;
    private static String itemWhitelistTooltip;
    private static String otherConditionsTooltip;

    public TriggerBot() {
        super(new dev.dov.hack.echo.client.modules.ModuleInfo(CatalogText.literal("Trigger Bot"), CatalogText.literal("Attacks when aiming at targets"), dev.dov.hack.echo.client.modules.ModuleCategory.COMBAT));
    }

    @Override
    public void postInitialize() {
        reactionTimer = new CooldownTimer();
        attackTimer = new CooldownTimer();
        criticalWindowTimer = new CooldownTimer();
        lastAttackTick = -1L;
        sampledAttackCooldown = 0.9;
        critRange.setAllowOutOfRange(true);
        attackDistance.setAllowOutOfRange(true);
        attackDistance.displayAs(0.0f, CatalogText.literal("Vanilla Reach"));
        attackDelay.description(CatalogText.literal("Delay between attacks"));
        reactionDelay.description(CatalogText.literal("Time on target before attacking"));
        attackDistance.description(CatalogText.literal("Only attack when the crosshair hit on the hitbox is within this distance, 0 uses vanilla reach"));
        if (cooldownTooltip == null) cooldownTooltip = "Timing";
        cooldown.tooltip(cooldownTooltip);
        if (timingTooltip == null) timingTooltip = "Timing";
        critCooldown.tooltip(timingTooltip);
        reactionDelay.tooltip(timingTooltip);
        attackDelay.tooltip(timingTooltip);
        if (itemWhitelistTooltip == null) itemWhitelistTooltip = "Conditions";
        itemWhitelist.tooltip(itemWhitelistTooltip);
        if (otherConditionsTooltip == null) otherConditionsTooltip = "Conditions";
        hotbarWhitelist.tooltip(otherConditionsTooltip);
        noSweepOrPickHits.tooltip(otherConditionsTooltip);
    }

    @Override
    public CharSequence getSuffix() {
        return critMode.isSelected(CRIT_NONE) ? EMPTY_SUFFIX : critMode.getValue();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reactionTimer.reset();
        attackTimer.reset();
        criticalWindowTimer.reset();
        targetAcquired = false;
        lastAttackTick = -1L;
        reactionDelayMillis = 0L;
        attackDelayMillis = 0L;
        sampleAttackCooldown();
    }

    @Subscribe
    private void onAttackKey(AttackKeyEvent event) {
        if (isOutsideWorld() || MinecraftRenderState.getScreen() != null) return;
        if (!itemWhitelist.acceptsHeldItem() || !hotbarWhitelist.permitsCurrentSlot()) {
            resetTargetAcquisition();
            return;
        }
        float distance = attackDistance.getValue();
        EntityHitResult hit = findAttackTarget(distance);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            resetTargetAcquisition();
            return;
        }
        double squaredDistance = client.player.getEyePos().squaredDistanceTo(hit.getPos());
        if (distance > 0.0f && squaredDistance > (double) (distance * distance)) {
            resetTargetAcquisition();
            return;
        }
        if (!TargetSelection.isValidTarget(target)) {
            resetTargetAcquisition();
            return;
        }
        LivingEntity selected = TargetSelection.resolveTarget(target);
        if (selected == null || selected != target) {
            resetTargetAcquisition();
            return;
        }
        if (!targetAcquired) {
            targetAcquired = true;
            reactionTimer.reset();
            reactionDelayMillis = (long) reactionDelay.sampleGaussian();
        }
        if (hurtTimeCheck.getValue() && AttackCooldownTracker.hasAttackCooldown(target)) return;
        if (target.isDead() || !target.isAlive()) return;
        if (target instanceof PlayerEntity player && !attackShields.getValue()
                && EntityPredicates.isBlocking(player) && !EntityPredicates.isOutsideBlockingArc(player)) return;
        float tickProgress = (float) sampledAttackCooldown - (float) AttackCooldownTracker.confirmedDamageDelayTicks(target);
        if ((double) client.player.getAttackCooldownProgress(tickProgress) < sampledAttackCooldown) return;
        if (!reactionTimer.hasElapsedMillis((double) reactionDelayMillis)) return;
        if (!attackTimer.hasElapsedMillis((double) attackDelayMillis)) return;
        if (lastAttackTick == (long) client.player.age) return;
        if (!allowsCriticalTiming(squaredDistance)) return;
        if (noSweepOrPickHits.getValue() && (EntityRaycasts.wouldSweepAttack() || EntityRaycasts.wouldPickHit())) return;
        event.addClick();
        attackTimer.reset();
        attackDelayMillis = (long) attackDelay.sampleGaussian();
        sampleAttackCooldown();
        lastAttackTick = client.player.age;
    }

    private EntityHitResult findAttackTarget(float distance) {
        EntityHitResult hit;
        if (distance == 0.0f) {
            hit = client.crosshairTarget instanceof EntityHitResult entityHit ? entityHit : null;
        } else {
            hit = EntityRaycasts.raycastEntity(client.player, distance);
        }
        if (hit != null) return hit;
        SpearReach spearReach = (SpearReach) EchoClient.ModuleManager.findFeature(SpearReach.class);
        return spearReach == null ? null : spearReach.findExtendedReachTarget();
    }

    private void sampleAttackCooldown() {
        FloatRangeSetting selected = EntityRaycasts.isCriticalState() && !critMode.isSelected(CRIT_NONE)
                ? critCooldown : cooldown;
        sampledAttackCooldown = (double) selected.sampleGaussian() / 100.0;
    }

    private void resetTargetAcquisition() {
        if (targetAcquired) {
            reactionTimer.reset();
            targetAcquired = false;
        }
    }

    private boolean allowsNonCriticalTiming() {
        if (EntityRaycasts.allowsNonCriticalHit()) return true;
        return critOnlyOnSpace.getValue() && !client.options.jumpKey.isPressed() && !client.player.isOnGround();
    }

    private boolean allowsCriticalTiming(double squaredDistance) {
        float lower = critRange.getLower();
        float upper = critRange.getUpper();
        if (squaredDistance < (double) (lower * lower) || squaredDistance > (double) (upper * upper)) return true;
        boolean allow = !(critOnlyOnSpace.getValue() && client.options.jumpKey.isPressed() && client.player.isOnGround());
        boolean critical = EntityRaycasts.isCriticalState();
        if (critMode.isSelected(CRIT_PRECISE)) {
            if (critical && criticalWindowTimer.hasElapsedMillis(0.0) && !client.player.isSprinting()) {
                criticalWindowTimer.reset();
            } else {
                allow = allowsNonCriticalTiming();
            }
        } else if (critMode.isSelected(CRIT_NORMAL)) {
            if (critical && criticalWindowTimer.hasElapsedMillis(0.0)) {
                criticalWindowTimer.reset();
            } else {
                allow = allowsNonCriticalTiming();
            }
        }
        return allow;
    }

    @Subscribe
    private void onPacketReceive(PacketReceiveEvent event) {
        if (isOutsideWorld()) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof PlayerPositionLookS2CPacket || packet instanceof PlayerRespawnS2CPacket) resetTargetAcquisition();
    }

    public static void appendBootstrapSettings(TriggerBot Module) {
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, critMode);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, critOnlyOnSpace);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, attackShields);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, hurtTimeCheck);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, noSweepOrPickHits);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, cooldown);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, critCooldown);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, critRange);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, attackDistance);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, reactionDelay);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, attackDelay);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, itemWhitelist);
        dev.dov.hack.echo.client.init.CatalogSettings.append(Module, hotbarWhitelist);
    }

    public static void assignBootstrapSettings(TriggerBot Module, FloatRangeSetting preparedCooldown, ChoiceSetting preparedCritMode, FloatRangeSetting preparedReactionDelay, BooleanSetting preparedCritOnlyOnSpace, FloatRangeSetting preparedAttackDelay, ItemListSetting preparedItemWhitelist, FloatSetting preparedAttackDistance, BooleanSetting preparedNoSweepOrPickHits, HotbarSlotsSetting preparedHotbarWhitelist, BooleanSetting preparedHurtTimeCheck, BooleanSetting preparedAttackShields, FloatRangeSetting preparedCritRange, FloatRangeSetting preparedCritCooldown) {
        cooldown = preparedCooldown;
        critMode = preparedCritMode;
        reactionDelay = preparedReactionDelay;
        critOnlyOnSpace = preparedCritOnlyOnSpace;
        attackDelay = preparedAttackDelay;
        itemWhitelist = preparedItemWhitelist;
        attackDistance = preparedAttackDistance;
        noSweepOrPickHits = preparedNoSweepOrPickHits;
        hotbarWhitelist = preparedHotbarWhitelist;
        hurtTimeCheck = preparedHurtTimeCheck;
        attackShields = preparedAttackShields;
        critRange = preparedCritRange;
        critCooldown = preparedCritCooldown;
    }
}
