package dev.hollowatlas.combat;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Human road brigands with readable, telegraphed patterns.
 * <ul>
 *   <li>{@code brigand} (melee): closes in, then either a telegraphed RUSH (crouch + dash) or a raised
 *       HEAVY SWING; raises a guard when pressed. Heavy attacks break its guard.</li>
 *   <li>{@code slinger} (ranged/evasive): keeps its distance, backs off and strafes when approached,
 *       winds up visibly before each sling stone.</li>
 * </ul>
 * Telegraph state is synced to clients so the model visibly raises its arms before a strike.
 */
public class EnemyEntity extends HostileEntity {
    public static final int POSE_IDLE = 0, POSE_WINDUP = 1, POSE_BLOCK = 2, POSE_STAGGER = 3, POSE_AIM = 4;
    private static final TrackedData<String> KIND = DataTracker.registerData(EnemyEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> POSE_STATE = DataTracker.registerData(EnemyEntity.class, TrackedDataHandlerRegistry.INTEGER);

    String encounterId = "";
    int staggerTicks;
    int blockTicks;
    int guardCooldown;

    public EnemyEntity(EntityType<? extends EnemyEntity> type, World world) {
        super(type, world);
        experiencePoints = 5;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 26.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(KIND, "brigand");
        builder.add(POSE_STATE, POSE_IDLE);
    }

    public void setKind(String kind) {
        dataTracker.set(KIND, kind);
        if (kind.equals("slinger")) {
            getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(18.0);
            setHealth(18f);
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else {
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        }
        setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0f);
    }

    public String kind() { return dataTracker.get(KIND); }
    public int poseState() { return dataTracker.get(POSE_STATE); }
    void setPoseState(int s) { dataTracker.set(POSE_STATE, s); }
    public String encounterId() { return encounterId; }

    public boolean isBlocking() { return blockTicks > 0; }

    public void guardBreak() {
        blockTicks = 0;
        guardCooldown = 80;
        stagger(30);
        getWorld().playSound(null, getBlockPos(), SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE, 1f, 1f);
    }

    public void stagger(int ticks) {
        staggerTicks = Math.max(staggerTicks, ticks);
        setPoseState(POSE_STAGGER);
        getNavigation().stop();
        if (getWorld() instanceof ServerWorld sw) sw.spawnParticles(ParticleTypes.CRIT, getX(), getY() + 2.1, getZ(), 8, 0.3, 0.1, 0.3, 0.1);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new PatternGoal(this));
        goalSelector.add(8, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                p -> !((PlayerEntity) p).isCreative() && !p.isSpectator()));
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (staggerTicks > 0 && --staggerTicks == 0) setPoseState(POSE_IDLE);
        if (blockTicks > 0 && --blockTicks == 0 && poseState() == POSE_BLOCK) setPoseState(POSE_IDLE);
        if (guardCooldown > 0) guardCooldown--;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && kind().equals("brigand") && source.getAttacker() instanceof PlayerEntity
                && blockTicks == 0 && guardCooldown == 0 && staggerTicks == 0 && poseState() != POSE_WINDUP && random.nextFloat() < 0.35f) {
            // raises a guard in response to pressure; the NEXT hits are reduced until broken
            blockTicks = 40;
            guardCooldown = 120;
            setPoseState(POSE_BLOCK);
        }
        if (blockTicks > 0 && source.getAttacker() instanceof PlayerEntity) {
            getWorld().playSound(null, getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.8f, 1.1f);
            amount *= 0.3f;
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    protected boolean isDisallowedInPeaceful() { return false; }

    // ---------------------------------------------------------------- AI

    /** One goal that sequences approach -> telegraph -> strike -> recover, per enemy kind. */
    static final class PatternGoal extends Goal {
        private final EnemyEntity mob;
        private int cooldown = 20;
        private int windup = 0;
        private String move = "";
        private int strafeDir = 1;
        private int strafeTimer = 0;

        PatternGoal(EnemyEntity mob) {
            this.mob = mob;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() { return mob.getTarget() != null && mob.getTarget().isAlive(); }
        @Override public boolean shouldContinue() { return canStart(); }
        @Override public boolean shouldRunEveryTick() { return true; }
        @Override public void stop() { windup = 0; move = ""; mob.setPoseState(POSE_IDLE); }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().lookAt(t, 30f, 30f);
            if (mob.staggerTicks > 0) return;
            if (cooldown > 0) cooldown--;
            double d = mob.distanceTo(t);
            if (mob.kind().equals("slinger")) slinger(t, d); else brigand(t, d);
        }

        private void brigand(LivingEntity t, double d) {
            if (windup > 0) {
                windup--;
                mob.getNavigation().stop();
                if (mob.getWorld() instanceof ServerWorld sw && windup % 4 == 0)
                    sw.spawnParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + 2.2, mob.getZ(), 1, 0.1, 0.1, 0.1, 0);
                if (windup == 0) strike(t, d);
                return;
            }
            if (mob.isBlocking()) { mob.getNavigation().startMovingTo(t, 0.6); return; }
            if (cooldown == 0 && d < 7.5 && d > 3.5 && mob.random.nextFloat() < 0.5f) {
                begin("rush", 14);
            } else if (cooldown == 0 && d <= 3.2) {
                begin(mob.random.nextFloat() < 0.6f ? "heavy" : "jab", mob.random.nextFloat() < 0.6f ? 18 : 6);
            } else {
                mob.getNavigation().startMovingTo(t, d > 6 ? 1.15 : 0.95);
            }
        }

        private void begin(String m, int ticks) {
            move = m;
            windup = ticks;
            mob.setPoseState(m.equals("jab") ? POSE_IDLE : POSE_WINDUP);
            mob.getWorld().playSound(null, mob.getBlockPos(), m.equals("rush") ? SoundEvents.ENTITY_RAVAGER_ROAR : SoundEvents.ENTITY_VINDICATOR_AMBIENT,
                    SoundCategory.HOSTILE, m.equals("rush") ? 0.35f : 0.8f, m.equals("rush") ? 1.8f : 0.8f);
        }

        private void strike(LivingEntity t, double d) {
            mob.setPoseState(POSE_IDLE);
            mob.swingHand(Hand.MAIN_HAND);
            ServerWorld sw = (ServerWorld) mob.getWorld();
            switch (move) {
                case "rush" -> {
                    Vec3d dir = t.getPos().subtract(mob.getPos()).multiply(1, 0, 1).normalize();
                    mob.setVelocity(dir.x * 1.3, 0.15, dir.z * 1.3);
                    mob.velocityModified = true;
                    // hit resolves shortly after the dash if still close
                    mob.pendingRushTicks = 6;
                    cooldown = 45;
                }
                case "heavy" -> {
                    if (d < 3.4 && inArc(t)) {
                        t.damage(sw.getDamageSources().mobAttack(mob), 7f);
                        t.takeKnockback(0.8, mob.getX() - t.getX(), mob.getZ() - t.getZ());
                    }
                    sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
                    sw.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1f, 0.7f);
                    cooldown = 40;
                }
                default -> {
                    if (d < 2.8) t.damage(sw.getDamageSources().mobAttack(mob), 3f);
                    cooldown = 22;
                }
            }
            move = "";
        }

        private boolean inArc(LivingEntity t) {
            Vec3d look = mob.getRotationVec(1f).multiply(1, 0, 1).normalize();
            Vec3d to = t.getPos().subtract(mob.getPos()).multiply(1, 0, 1).normalize();
            return look.dotProduct(to) > 0.3;
        }

        private void slinger(LivingEntity t, double d) {
            if (windup > 0) {
                windup--;
                mob.getNavigation().stop();
                if (windup == 0) {
                    mob.setPoseState(POSE_IDLE);
                    mob.swingHand(Hand.MAIN_HAND);
                    SlingStoneEntity stone = new SlingStoneEntity(mob.getWorld(), mob);
                    Vec3d aim = t.getPos().add(t.getVelocity().multiply(6)).add(0, t.getHeight() * 0.6, 0).subtract(mob.getEyePos());
                    stone.setVelocity(aim.x, aim.y + aim.horizontalLength() * 0.08, aim.z, 1.35f, 2.5f);
                    mob.getWorld().spawnEntity(stone);
                    mob.getWorld().playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.HOSTILE, 1f, 0.6f);
                    cooldown = 38 + mob.random.nextInt(20);
                }
                return;
            }
            if (d < 5.0) {
                // evasive: back away from the player
                Vec3d away = mob.getPos().subtract(t.getPos()).multiply(1, 0, 1).normalize().multiply(5);
                mob.getNavigation().startMovingTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.25);
                return;
            }
            if (d > 13) { mob.getNavigation().startMovingTo(t, 1.0); return; }
            if (cooldown == 0 && mob.canSee(t)) {
                windup = 12;
                mob.setPoseState(POSE_AIM);
                mob.getWorld().playSound(null, mob.getBlockPos(), SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE.value(), SoundCategory.HOSTILE, 0.9f, 1.2f);
                return;
            }
            if (--strafeTimer <= 0) { strafeDir = -strafeDir; strafeTimer = 25 + mob.random.nextInt(30); }
            mob.getMoveControl().strafeTo(0f, 0.45f * strafeDir);
        }
    }

    int pendingRushTicks = 0;

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && pendingRushTicks > 0 && --pendingRushTicks == 0) {
            LivingEntity t = getTarget();
            if (t != null && distanceTo(t) < 2.4) {
                t.damage(getWorld().getDamageSources().mobAttack(this), 6f);
                t.takeKnockback(1.0, getX() - t.getX(), getZ() - t.getZ());
            }
        }
    }
}
