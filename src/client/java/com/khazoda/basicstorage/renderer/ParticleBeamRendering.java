package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.packet.StationBeamPayload;
import com.khazoda.basicstorage.registry.ParticleRegistry;
import com.khazoda.basicstorage.registry.SoundRegistry;
import com.khazoda.basicstorage.sound.WhooshSoundInstance;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleBeamRendering {
  public static final ParticleBeamRendering INSTANCE = new ParticleBeamRendering();

  private ParticleBeamRendering() {
  }

  public static final int BEAM_DURATION_TICKS = 6;
  public static final int HIGHLIGHT_DURATION_TICKS = 20;

  private final List<Beam> activeBeams = new ArrayList<>();
  public static final Map<BlockPos, Integer> highlightedCrates = new ConcurrentHashMap<>();
  public static final Map<BlockPos, List<ActiveBeam>> activeBeamSnapshots = new ConcurrentHashMap<>();
  public static final Map<BlockPos, Float> rollingItemCounts = new ConcurrentHashMap<>();
  public static final Map<BlockPos, Integer> knownItemCounts = new ConcurrentHashMap<>();

  public static class ActiveBeam {
    public int ticksLeft;
    public final int amount;

    public ActiveBeam(int ticksLeft, int amount) {
      this.ticksLeft = ticksLeft;
      this.amount = amount;
    }
  }

  public void registerParticleClientEvents() {
    ClientPlayNetworking.registerGlobalReceiver(StationBeamPayload.ID, (payload, context) -> context.client().execute(() -> {
      ClientLevel level = context.client().level;
      if (level == null) return;

      BlockPos origin = payload.origin();
      List<StationBeamPayload.Target> targets = payload.targets();
      for (int i = 0; i < targets.size(); i++) {
        StationBeamPayload.Target target = targets.get(i);
        int delay = targets.size() > 1 ? (i * 20) / (targets.size() - 1) : 0;
        createBeam(level, origin, target.pos(), target.amount(), delay, i == 0);
      }
    }));

    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
      activeBeams.clear();
      highlightedCrates.clear();
      rollingItemCounts.clear();
      knownItemCounts.clear();
    });

    /* Tick active beams & crate highlight animations */
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.level != null) {

        // Tick beams
        if (!activeBeams.isEmpty()) {
          activeBeams.removeIf(Beam::tick);
        }

        // Tick highlights
        if (!highlightedCrates.isEmpty()) {
          Iterator<Map.Entry<BlockPos, Integer>> it = highlightedCrates.entrySet().iterator();
          while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            int ticks = entry.getValue();
            if (ticks <= 0) {
              it.remove();
            } else {
              entry.setValue(ticks - 1);
            }
          }
        }

        // Tick active beam snapshots
        if (!activeBeamSnapshots.isEmpty()) {
          Iterator<Map.Entry<BlockPos, List<ActiveBeam>>> it = activeBeamSnapshots.entrySet().iterator();
          while (it.hasNext()) {
            Map.Entry<BlockPos, List<ActiveBeam>> entry = it.next();
            List<ActiveBeam> list = entry.getValue();
            Iterator<ActiveBeam> listIt = list.iterator();
            while (listIt.hasNext()) {
              ActiveBeam item = listIt.next();
              item.ticksLeft--;
              if (item.ticksLeft <= 0) {
                listIt.remove();
              }
            }
            if (list.isEmpty()) {
              it.remove();
            }
          }
        }

        // Tick rolling item counts
        if (!rollingItemCounts.isEmpty()) {
          Iterator<Map.Entry<BlockPos, Float>> it = rollingItemCounts.entrySet().iterator();
          while (it.hasNext()) {
            Map.Entry<BlockPos, Float> entry = it.next();
            BlockPos pos = entry.getKey();

            if (client.level.getBlockEntity(pos) instanceof CrateBlockEntity cbe) {
              float current = entry.getValue();

              int incomingSum = 0;
              List<ActiveBeam> incoming = activeBeamSnapshots.get(pos);
              if (incoming != null) {
                for (ActiveBeam b : incoming) incomingSum += b.amount;
              }
              float target = (float) Math.max(0, cbe.storage.getAmount() - incomingSum);
              float currentReal = (float) cbe.storage.getAmount();

              /*
              If Real < Current, items were removed. Allow dropping.
              If Real >= Current, the crate is being filled. Prevent dropping below current.
              */
              if (currentReal >= current) {
                target = Math.max(current, target);
              }

              float diff = target - current;

              // Snap to target (prevent float issues)
              if (Math.abs(diff) < 0.1f) {
                entry.setValue(target);
              } else {
                float move = diff * 0.5f;
                float minVel = 0.4f;
                if (Math.abs(move) < minVel) move = Math.signum(diff) * minVel;

                // Overshoot protection
                float nextVal = current + move;
                if ((diff > 0 && nextVal > target) || (diff < 0 && nextVal < target)) {
                  nextVal = target;
                }

                entry.setValue(nextVal);
              }

              /* Only remove animation if there are no incoming beams and the target has been reached */
              if (incomingSum == 0 && Math.abs(entry.getValue() - target) < 0.05f) {
                it.remove();
              }
            } else {
              it.remove();
            }
          }
        }
      }
    });
  }

  private void createBeam(ClientLevel level, BlockPos from, BlockPos to, int amount, int delay, boolean playSound) {
    Beam beam = new Beam(level, Vec3.atCenterOf(from), Vec3.atCenterOf(to), BEAM_DURATION_TICKS, to, delay, from);

    /* Snapshot current count to prevent visual jumps until beam hits */
    int currentVisibleCount = 0;
    if (level.getBlockEntity(to) instanceof CrateBlockEntity cbe) {
      currentVisibleCount = Math.toIntExact(cbe.storage.getAmount());
    }

    rollingItemCounts.putIfAbsent(to, (float) currentVisibleCount);
    activeBeamSnapshots.computeIfAbsent(to, k -> new ArrayList<>()).add(new ActiveBeam(BEAM_DURATION_TICKS + delay, amount));
    activeBeams.add(beam);

    if (playSound) {
      Minecraft.getInstance().getSoundManager().play(new WhooshSoundInstance(SoundRegistry.WHOOSH, beam.beamVectors, BEAM_DURATION_TICKS, delay, level.random));
    }
  }

  private static class Beam {
    final ClientLevel level;
    final BlockPos targetPos;
    final int duration;
    final int totalSteps;
    final int delay;
    int age;
    final Vec3[] beamVectors;
    final BlockPos originPos;

    public Beam(ClientLevel level, Vec3 start, Vec3 end, int duration, BlockPos targetPos, int delay, BlockPos originPos) {
      this.level = level;
      this.targetPos = targetPos;
      this.delay = delay;
      this.originPos = originPos;
      this.duration = duration;
      this.age = 0;

      Vec3 diff = end.subtract(start);
      double dist = diff.length();
      this.totalSteps = (int) (dist * 10);

      double arcScale = dist * 0.5;
      Vec3 p1 = start.add(diff.scale(0.33)).add((Math.random() - 0.5) * arcScale, (Math.random() * arcScale), (Math.random() - 0.5) * arcScale);
      Vec3 p2 = start.add(diff.scale(0.66)).add((Math.random() - 0.5) * arcScale, (Math.random() * arcScale), (Math.random() - 0.5) * arcScale);

      double frequency = 2.0 + Math.random() * 3.0;
      double amplitude = 0.3 + Math.random() * 0.5;
      double phase = Math.random() * Math.PI * 2;
      boolean isSpiral = Math.random() > 0.5;

      /* Calculate perpendicular vectors for offset */
      Vec3 dir = diff.normalize();
      Vec3 approxUp = new Vec3(0, 1, 0);
      if (Math.abs(dir.dot(approxUp)) > 0.9) approxUp = new Vec3(1, 0, 0); // Handle vertical beams
      Vec3 right = dir.cross(approxUp).normalize();
      Vec3 upVec = right.cross(dir).normalize();

      /* Pre-calculate beam paths */
      this.beamVectors = new Vec3[totalSteps];
      for (int i = 0; i < totalSteps; i++) {
        double t = (double) i / totalSteps;
        double invT = 1.0 - t;
        double t2 = t * t;
        double invT2 = invT * invT;

        // Base Bezier Path
        double bx = (invT2 * invT * start.x) + (3 * invT2 * t * p1.x) + (3 * invT * t2 * p2.x) + (t2 * t * end.x);
        double by = (invT2 * invT * start.y) + (3 * invT2 * t * p1.y) + (3 * invT * t2 * p2.y) + (t2 * t * end.y);
        double bz = (invT2 * invT * start.z) + (3 * invT2 * t * p1.z) + (3 * invT * t2 * p2.z) + (t2 * t * end.z);

        // Sine wave & spiraling offset
        double angle = t * frequency * Math.PI * 2 + phase;
        double offset1 = Math.sin(angle) * amplitude;
        double offset2 = isSpiral ? Math.cos(angle) * amplitude : 0;

        // Taper amplitude at start and end so beam connects to station and crate properly
        double fade = Math.sin(t * Math.PI);
        offset1 *= fade;
        offset2 *= fade;

        double x = bx + right.x * offset1 + upVec.x * offset2;
        double y = by + right.y * offset1 + upVec.y * offset2;
        double z = bz + right.z * offset1 + upVec.z * offset2;

        this.beamVectors[i] = new Vec3(x, y, z);
      }
    }

    public boolean tick() {
      if (age == delay) {
        level.playLocalSound(originPos.getX() + 0.5, originPos.getY() + 0.5, originPos.getZ() + 0.5, SoundEvents.ENDER_PEARL_THROW, SoundSource.BLOCKS, 0.05f, 1.5f + level.random.nextFloat() * 0.5f, false);
      }

      if (age < delay) {
        age++;
        return false;
      }

      int adjustedAge = age - delay;
      double prevProgress = (double) adjustedAge / duration;
      age++;
      double currentProgress = (double) (age - delay) / duration;

      spawnParticles(prevProgress, currentProgress);

      if (adjustedAge >= duration) {
        /* Trigger highlight once beam hits crate */
        highlightedCrates.put(targetPos, HIGHLIGHT_DURATION_TICKS);
        return true;
      }
      return false;
    }

    public void spawnParticles(double startProgress, double endProgress) {
      int startStep = (int) (totalSteps * startProgress);
      int endStep = (int) (totalSteps * endProgress);

      if (startStep == endStep && startProgress != endProgress) endStep++;
      if (endStep > totalSteps) endStep = totalSteps;

      for (int i = startStep; i < endStep; i++) {
        Vec3 pos = beamVectors[i];
        if (level.random.nextFloat() < 0.05f) {
          level.addParticle(ParticleRegistry.TWINKLE, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
        }
        if (level.random.nextFloat() < 0.85f) {
          level.addParticle(ParticleRegistry.VOIDY, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
        }
      }
    }
  }
}