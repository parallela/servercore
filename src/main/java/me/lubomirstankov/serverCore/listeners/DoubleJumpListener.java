package me.lubomirstankov.serverCore.listeners;

import me.lubomirstankov.serverCore.ServerCore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

/**
 * Listener that handles double-jump boost with configurable effects.
 * Players double-tap space to boost forward.
 */
public class DoubleJumpListener implements Listener {
    private final ServerCore plugin;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Long> fallDamageProtection;
    private final Map<UUID, Boolean> isInAir;
    private final Map<UUID, Boolean> pluginManagedFlight; // Track if flight was enabled by THIS plugin

    private static final String CONFIG_PATH = "double-jump";
    private static final long FALL_PROTECTION_DURATION = 5000; // 5 seconds in milliseconds

    public DoubleJumpListener(ServerCore plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.fallDamageProtection = new HashMap<>();
        this.isInAir = new HashMap<>();
        this.pluginManagedFlight = new HashMap<>();
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        // Check if player is in creative/spectator mode (they should be able to fly normally)
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
            player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        // CRITICAL: Check if flight was enabled by another plugin
        // If flight is NOT managed by our plugin, let the other plugin handle it
        Boolean isManagedByUs = pluginManagedFlight.get(playerId);
        if (isManagedByUs == null || !isManagedByUs) {
            // Flight was enabled by another plugin or command - don't interfere!
            return;
        }

        // Check permission (if set)
        String permission = section.getString("permission", "");
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            return;
        }

        // Check cooldown
        long cooldownMs = section.getLong("cooldown-ms", 3000);
        long currentTime = System.currentTimeMillis();
        Long lastJump = cooldowns.get(player.getUniqueId());

        if (lastJump != null && (currentTime - lastJump) < cooldownMs) {
            return;
        }

        // Cancel the flight toggle (prevent actual flight)
        event.setCancelled(true);

        // Apply boost
        double boostStrength = section.getDouble("boost-strength", 1.5);
        double boostUpward = section.getDouble("boost-upward", 0.5);

        Vector direction = player.getLocation().getDirection().normalize();
        direction.multiply(boostStrength);
        direction.setY(boostUpward);
        player.setVelocity(direction);

        // IMMEDIATELY disable flight - no delay, no grace period
        player.setAllowFlight(false);
        player.setFlying(false);
        pluginManagedFlight.put(playerId, false); // Mark as no longer managed by us

        // Force disable again after 1 tick to ensure it's disabled
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && pluginManagedFlight.getOrDefault(playerId, false)) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }, 1L);

        // Force disable again after 5 ticks as final safeguard
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() &&
                player.getGameMode() != org.bukkit.GameMode.CREATIVE &&
                player.getGameMode() != org.bukkit.GameMode.SPECTATOR &&
                pluginManagedFlight.getOrDefault(playerId, false)) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }, 5L);

        // Update cooldown
        cooldowns.put(player.getUniqueId(), currentTime);

        // Add fall damage protection for 5 seconds after double jump
        fallDamageProtection.put(player.getUniqueId(), currentTime);

        // Reset fall distance to prevent fall damage from the boost
        player.setFallDistance(0f);

        // Apply all configured effects
        ConfigurationSection effectsSection = section.getConfigurationSection("effects");
        if (effectsSection != null) {
            applyEffects(player, effectsSection);
        }
    }

    private void applyEffects(Player player, ConfigurationSection effectsSection) {
        // Fireworks
        ConfigurationSection fireworkSection = effectsSection.getConfigurationSection("fireworks");
        if (fireworkSection != null && fireworkSection.getBoolean("enabled", false)) {
            spawnFireworks(player, fireworkSection);
        }

        // Particles
        ConfigurationSection particleSection = effectsSection.getConfigurationSection("particles");
        if (particleSection != null && particleSection.getBoolean("enabled", false)) {
            spawnParticles(player, particleSection);
        }

        // Potion Effects
        ConfigurationSection potionSection = effectsSection.getConfigurationSection("potion-effects");
        if (potionSection != null && potionSection.getBoolean("enabled", false)) {
            applyPotionEffects(player, potionSection);
        }

        // Action Bar
        ConfigurationSection actionBarSection = effectsSection.getConfigurationSection("action-bar");
        if (actionBarSection != null && actionBarSection.getBoolean("enabled", false)) {
            showActionBar(player, actionBarSection);
        }

        // Title
        ConfigurationSection titleSection = effectsSection.getConfigurationSection("title");
        if (titleSection != null && titleSection.getBoolean("enabled", false)) {
            showTitle(player, titleSection);
        }

        // Sound
        ConfigurationSection soundSection = effectsSection.getConfigurationSection("sound");
        if (soundSection != null && soundSection.getBoolean("enabled", false)) {
            playSound(player, soundSection);
        }
    }

    private void spawnParticles(Player player, ConfigurationSection particleSection) {
        try {
            String particleType = particleSection.getString("type", "FIREWORKS_SPARK");
            int count = particleSection.getInt("count", 30);
            double spread = particleSection.getDouble("spread", 0.5);
            double speed = particleSection.getDouble("speed", 0.1);

            Particle particle = Particle.valueOf(particleType);
            player.getWorld().spawnParticle(
                    particle,
                    player.getLocation(),
                    count,
                    spread, spread, spread,
                    speed
            );
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type: " + e.getMessage());
        }
    }

    private void applyPotionEffects(Player player, ConfigurationSection potionSection) {
        List<Map<?, ?>> effects = potionSection.getMapList("effects");

        for (Map<?, ?> effectMap : effects) {
            try {
                String type = (String) effectMap.get("type");
                Object durationObj = effectMap.get("duration");
                Object amplifierObj = effectMap.get("amplifier");

                int duration = (durationObj instanceof Number) ? ((Number) durationObj).intValue() : 40;
                int amplifier = (amplifierObj instanceof Number) ? ((Number) amplifierObj).intValue() : 0;

                PotionEffectType potionType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(type.toLowerCase()));
                if (potionType != null) {
                    player.addPotionEffect(new PotionEffect(potionType, duration, amplifier, false, false, true));
                } else {
                    plugin.getLogger().warning("Invalid potion effect type: " + type);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error applying potion effect: " + e.getMessage());
            }
        }
    }

    private void showActionBar(Player player, ConfigurationSection actionBarSection) {
        String message = actionBarSection.getString("message", "<green>Double Jump!</green>");
        Component component = MiniMessage.miniMessage().deserialize(message);
        player.sendActionBar(component);
    }

    private void showTitle(Player player, ConfigurationSection titleSection) {
        String titleText = titleSection.getString("title", "");
        String subtitleText = titleSection.getString("subtitle", "");
        int fadeIn = titleSection.getInt("fade-in", 5);
        int stay = titleSection.getInt("stay", 15);
        int fadeOut = titleSection.getInt("fade-out", 10);

        Component title = MiniMessage.miniMessage().deserialize(titleText);
        Component subtitle = MiniMessage.miniMessage().deserialize(subtitleText);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );

        player.showTitle(Title.title(title, subtitle, times));
    }

    private void spawnFireworks(Player player, ConfigurationSection fireworkSection) {
        int count = fireworkSection.getInt("count", 3);
        List<String> colorStrings = fireworkSection.getStringList("colors");
        String effectType = fireworkSection.getString("type", "BALL_LARGE");

        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(effectType);
        } catch (IllegalArgumentException e) {
            type = FireworkEffect.Type.BALL_LARGE;
            plugin.getLogger().warning("Invalid firework type: " + effectType);
        }

        // Parse colors
        Color[] colors = colorStrings.stream()
                .map(this::parseColor)
                .filter(Objects::nonNull)
                .toArray(Color[]::new);

        if (colors.length == 0) {
            colors = new Color[]{Color.RED, Color.BLUE, Color.GREEN};
        }

        FireworkEffect effect = FireworkEffect.builder()
                .with(type)
                .withColor(colors)
                .withFlicker()
                .build();

        for (int i = 0; i < count; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(effect);
                meta.setPower(0);
                firework.setFireworkMeta(meta);

                // Detonate immediately for visual effect
                plugin.getServer().getScheduler().runTaskLater(plugin, firework::detonate, 1L);
            }, i * 2L);
        }
    }

    private void playSound(Player player, ConfigurationSection soundSection) {
        try {
            String soundType = soundSection.getString("type", "ENTITY_FIREWORK_ROCKET_LAUNCH");
            float volume = (float) soundSection.getDouble("volume", 0.8);
            float pitch = (float) soundSection.getDouble("pitch", 1.2);

            // Convert ENTITY_FIREWORK_ROCKET_LAUNCH to entity.firework_rocket.launch format
            String soundKey = soundType.toLowerCase().replace("_", ".");

            Sound sound = Sound.sound(Key.key(soundKey), Sound.Source.PLAYER, volume, pitch);
            player.playSound(sound);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound type in double-jump config: " + e.getMessage());
        }
    }

    private Color parseColor(String hex) {
        try {
            hex = hex.replace("#", "");
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid color format: " + hex);
            return null;
        }
    }

    /**
     * Enable flight for players when they leave the ground (for double jump detection)
     * Flight is ONLY enabled when jumping, and IMMEDIATELY disabled when landing
     */
    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        // Allow creative/spectator mode to fly normally
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
            player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        // Check permission (if set)
        String permission = section.getString("permission", "");
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            return;
        }

        // Check if player is on ground
        boolean currentlyOnGround = isPlayerOnGround(player);
        Boolean wasInAir = isInAir.getOrDefault(playerId, false);

        // Player just left the ground (jumped) - enable flight for double-jump detection
        if (!currentlyOnGround && !wasInAir) {
            // Before enabling flight, check if it was already enabled by another plugin
            if (!player.getAllowFlight()) {
                // Flight is not enabled, we can safely enable it for double jump
                isInAir.put(playerId, true);
                player.setAllowFlight(true);
                pluginManagedFlight.put(playerId, true); // Mark as managed by us
            } else {
                // Flight is already enabled (by another plugin) - don't track this as our flight
                isInAir.put(playerId, false);
                pluginManagedFlight.put(playerId, false);
            }
        }

        // Player just landed - ONLY disable flight if WE enabled it
        if (currentlyOnGround && wasInAir) {
            isInAir.put(playerId, false);

            // Only disable if we're managing the flight
            if (pluginManagedFlight.getOrDefault(playerId, false)) {
                player.setAllowFlight(false);
                player.setFlying(false);
                pluginManagedFlight.put(playerId, false);
            }
        }

        // AGGRESSIVE CHECK: Only if we're managing the flight
        if (pluginManagedFlight.getOrDefault(playerId, false)) {
            // If player is somehow flying and shouldn't be, force disable
            if (player.isFlying() && !player.getAllowFlight()) {
                player.setFlying(false);
            }

            // Extra safeguard: If player is on ground and has flight enabled by us, disable it
            if (currentlyOnGround && player.getAllowFlight()) {
                player.setAllowFlight(false);
                player.setFlying(false);
                isInAir.put(playerId, false);
                pluginManagedFlight.put(playerId, false);
            }
        }
    }

    /**
     * Check if player is on the ground
     * @param player the player to check
     * @return true if player is on ground, false otherwise
     */
    private boolean isPlayerOnGround(Player player) {
        org.bukkit.Location loc = player.getLocation().clone();

        // Check multiple points below the player for better accuracy
        // (handles edge cases like standing on edges)
        for (double offset = 0.0; offset <= 0.5; offset += 0.1) {
            org.bukkit.block.Block blockBelow = loc.clone().subtract(0, offset, 0).getBlock();
            if (blockBelow.getType().isSolid()) {
                return true;
            }
        }

        // Check if player is in liquid (water/lava) - they shouldn't have flight enabled
        if (player.isInWater() || player.isInLava()) {
            return true;
        }

        // Check vertical velocity - if nearly zero, player is likely on ground or platform
        double velocityY = player.getVelocity().getY();
        if (Math.abs(velocityY) < 0.001) {
            return true;
        }

        // Check if velocity is exactly 0 and player is descending (on platform/ground)
        if (velocityY <= 0 && velocityY > -0.1) {
            org.bukkit.block.Block blockBelow = loc.clone().subtract(0, 0.2, 0).getBlock();
            if (blockBelow.getType().isSolid()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Prevent fall damage and firework explosion damage for players who recently used double jump
     */
    @EventHandler
    public void onPlayerDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Check if this is damage we want to prevent (fall, explosion, entity explosion from fireworks)
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
                && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        // Check if player has damage protection from double jump
        Long protectionTime = fallDamageProtection.get(player.getUniqueId());
        if (protectionTime != null) {
            long currentTime = System.currentTimeMillis();

            // If protection is still active, cancel the damage
            if ((currentTime - protectionTime) < FALL_PROTECTION_DURATION) {
                event.setCancelled(true);
            } else {
                // Protection expired, remove it
                fallDamageProtection.remove(player.getUniqueId());
            }
        }
    }

    /**
     * Clean up tracking data when player quits to prevent memory leaks
     * and ensure flight is properly disabled (only if managed by us)
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Only disable flight if WE enabled it
        if (pluginManagedFlight.getOrDefault(playerId, false)) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        // Clean up all tracking data
        cooldowns.remove(playerId);
        fallDamageProtection.remove(playerId);
        isInAir.remove(playerId);
        pluginManagedFlight.remove(playerId);
    }

    // Easter egg
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if ((e.getDamager() instanceof Player)&&(e.getEntity() instanceof Player)) {
            Player damaged = (Player) e.getEntity();
            Player damager = (Player) e.getDamager();
            if (damaged.getName().equals("Th3TrOLLeR")) {
                damager.sendMessage("Don't hit Th3TrOLLeR!!!");
                e.setCancelled(true);
            }

        }
    }
}

