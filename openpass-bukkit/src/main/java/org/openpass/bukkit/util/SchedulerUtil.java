package org.openpass.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class SchedulerUtil {
    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        IS_FOLIA = folia;
    }

    public interface TaskWrapper {
        void cancel();
    }

    public static TaskWrapper runAsync(Plugin plugin, Runnable runnable) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
            return () -> {};
        } else {
            var task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
            return task::cancel;
        }
    }

    public static TaskWrapper runAsyncRepeating(Plugin plugin, Runnable runnable, long initialDelaySeconds, long periodSeconds) {
        if (IS_FOLIA) {
            var scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(), initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
            return scheduledTask::cancel;
        } else {
            var task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, initialDelaySeconds * 20L, periodSeconds * 20L);
            return task::cancel;
        }
    }

    public static TaskWrapper runEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, task -> runnable.run(), null);
            return () -> {};
        } else {
            var task = Bukkit.getScheduler().runTask(plugin, runnable);
            return task::cancel;
        }
    }

    public static TaskWrapper runEntityRepeating(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            var scheduledTask = entity.getScheduler().runAtFixedRate(plugin, task -> runnable.run(), null, delayTicks, periodTicks);
            return () -> {
                if (scheduledTask != null) {
                    scheduledTask.cancel();
                }
            };
        } else {
            var task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
            return task::cancel;
        }
    }

    public static TaskWrapper runGlobal(Plugin plugin, Runnable runnable) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run());
            return () -> {};
        } else {
            var task = Bukkit.getScheduler().runTask(plugin, runnable);
            return task::cancel;
        }
    }
}
