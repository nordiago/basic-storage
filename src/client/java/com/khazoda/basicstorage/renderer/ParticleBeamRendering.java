package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.packet.StationBeamPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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

  public static final int BEAM_DURATION_TICKS = 4;
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
      for (StationBeamPayload.Target target : payload.targets()) {
        createBeam(level, origin, target.pos(), target.amount());
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

  private void createBeam(ClientLevel level, BlockPos from, BlockPos to, int amount) {
    Beam beam = new Beam(level, Vec3.atCenterOf(from), Vec3.atCenterOf(to), BEAM_DURATION_TICKS, to);

    /* Snapshot current count to prevent visual jumps until beam hits */
    int currentVisibleCount = 0;
    if (level.getBlockEntity(to) instanceof CrateBlockEntity cbe) {
      currentVisibleCount = Math.toIntExact(cbe.storage.getAmount());
    }

    rollingItemCounts.putIfAbsent(to, (float) currentVisibleCount);
    activeBeamSnapshots.computeIfAbsent(to, k -> new ArrayList<>()).add(new ActiveBeam(BEAM_DURATION_TICKS, amount));
    activeBeams.add(beam);
  }

  private static class Beam {
    final ClientLevel level;
    final Vec3 start, end, p1, p2;
    final BlockPos targetPos;
    final int duration;
    final int totalSteps;
    int age;

    public Beam(ClientLevel level, Vec3 start, Vec3 end, int duration, BlockPos targetPos) {
      this.level = level;
      this.start = start;
      this.end = end;
      this.duration = duration;
      this.targetPos = targetPos;
      this.age = 0;

      Vec3 diff = end.subtract(start);
      double dist = diff.length();
      this.totalSteps = (int) (dist * 4);

      double arcScale = dist * 0.25;
      this.p1 = start.add(diff.scale(0.33)).add((Math.random() - 0.5) * arcScale, (Math.random() * arcScale * 0.5), (Math.random() - 0.5) * arcScale);
      this.p2 = start.add(diff.scale(0.66)).add((Math.random() - 0.5) * arcScale, (Math.random() * arcScale * 0.5), (Math.random() - 0.5) * arcScale);
    }

    public boolean tick() {
      double prevProgress = (double) age / duration;
      age++;
      double currentProgress = (double) age / duration;

      spawnParticles(prevProgress, currentProgress);

      if (age >= duration) {
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
        double t = (double) i / totalSteps;
        double invT = 1.0 - t;
        double t2 = t * t;
        double invT2 = invT * invT;

        double x = (invT2 * invT * start.x) + (3 * invT2 * t * p1.x) + (3 * invT * t2 * p2.x) + (t2 * t * end.x);
        double y = (invT2 * invT * start.y) + (3 * invT2 * t * p1.y) + (3 * invT * t2 * p2.y) + (t2 * t * end.y);
        double z = (invT2 * invT * start.z) + (3 * invT2 * t * p1.z) + (3 * invT * t2 * p2.z) + (t2 * t * end.z);

        // Particle density
        if (Math.random() < 0.40) {
          double jitter = 0.05;
          level.addParticle(ParticleTypes.END_ROD, x + (Math.random() - 0.5) * jitter, y + (Math.random() - 0.5) * jitter, z + (Math.random() - 0.5) * jitter, 0, 0, 0);
        }
        if (Math.random() < 0.20) {
          double jitter = 0.025;
          level.addParticle(ParticleTypes.WARPED_SPORE, x + (Math.random() - 0.5) * jitter, y + (Math.random() - 0.5) * jitter, z + (Math.random() - 0.5) * jitter, 0, 0, 0);
        }
      }
    }
  }
}