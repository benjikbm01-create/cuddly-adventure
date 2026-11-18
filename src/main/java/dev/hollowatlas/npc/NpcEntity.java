package dev.hollowatlas.npc;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Live, disposable body of an authored resident. Never saved to chunks; {@link NpcDirector}
 * (re)creates it from the persistent {@code NpcRecord}.
 */
public class NpcEntity extends PathAwareEntity {
    private static final TrackedData<String> NPC_ID = DataTracker.registerData(NpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> SKIN = DataTracker.registerData(NpcEntity.class, TrackedDataHandlerRegistry.STRING);

    private NpcBrain brain;

    public NpcEntity(EntityType<? extends NpcEntity> type, World world) {
        super(type, world);
        setInvulnerable(true);
        setPersistent();
        if (getNavigation() instanceof MobNavigation nav) {
            nav.setCanPathThroughDoors(true);
            nav.setCanEnterOpenDoors(true);
        }
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(NPC_ID, "");
        builder.add(SKIN, "tester");
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new LongDoorInteractGoal(this, true));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f, 0.04f));
    }

    public void bind(NpcDefinition def) {
        dataTracker.set(NPC_ID, def.id);
        dataTracker.set(SKIN, def.skin == null ? def.id : def.skin);
        setCustomName(Text.literal(def.name));
        setCustomNameVisible(false);
        brain = new NpcBrain(this, def);
    }

    public String npcId() { return dataTracker.get(NPC_ID); }

    public String skin() { return dataTracker.get(SKIN); }

    public NpcBrain npcBrain() { return brain; }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (brain != null) brain.tick();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp && brain != null) {
            NpcInteractions.onInteract(sp, this);
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Residents can't be harmed in v0.1; only the void can remove them.
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) return super.damage(source, amount);
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    protected boolean isDisallowedInPeaceful() { return false; }
}
