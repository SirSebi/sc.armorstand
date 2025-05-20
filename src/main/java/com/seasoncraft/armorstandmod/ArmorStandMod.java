package com.seasoncraft.armorstandmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import java.util.UUID;
import java.util.Map;
import java.nio.file.*;
import java.io.*;
import com.google.gson.*;
import java.util.concurrent.*;
import net.minecraft.util.math.Box;

public class ArmorStandMod implements ModInitializer {
    private static final String LINK_FILE = "armorstand_links.json";
    private final static Map<UUID, UUID> playerArmorStandMap = new ConcurrentHashMap<>();
    private final static Map<UUID, Integer> armorStandIdMap = new ConcurrentHashMap<>();
    private static int tickCounter = 0;
    private static final int ROTATION_UPDATE_INTERVAL = 5;
    
    @Override
    public void onInitialize() {
        System.out.println("[ArmorStandMod] Initializing mod...");
        loadLinks();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerEntity player = handler.getPlayer();
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                UUID playerUUID = player.getUuid();
                UUID armorStandUUID = playerArmorStandMap.get(playerUUID);
                if (armorStandUUID == null || findArmorStandByUUID(player.getWorld(), armorStandUUID) == null) {
                    ArmorStandEntity armorStand = spawnArmorStand(player);
                    playerArmorStandMap.put(playerUUID, armorStand.getUuid());
                    armorStandIdMap.put(playerUUID, armorStand.getId());
                    saveLinks();
                    new Thread(() -> {
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        String command = String.format("ride %s mount %s", armorStand.getUuid(), player.getUuid());
                        player.getServer().getCommandManager().executeWithPrefix(
                            player.getServer().getCommandSource(),
                            command
                        );
                    }).start();
                }
            }).start();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerEntity player = handler.getPlayer();
            UUID playerUUID = player.getUuid();
            UUID armorStandUUID = playerArmorStandMap.get(playerUUID);
            if (armorStandUUID != null) {
                ArmorStandEntity armorStand = findArmorStandByUUID(player.getWorld(), armorStandUUID);
                if (armorStand != null) {
                    armorStand.discard();
                }
                playerArmorStandMap.remove(playerUUID);
                armorStandIdMap.remove(playerUUID);
                saveLinks();
            }
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            updateArmorStandHead(player);
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % ROTATION_UPDATE_INTERVAL != 0) return;

            for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
                for (PlayerEntity player : world.getPlayers()) {
                    UUID playerUUID = player.getUuid();
                    Integer armorStandId = armorStandIdMap.get(playerUUID);
                    ArmorStandEntity armorStand = null;

                    if (armorStandId != null) {
                        armorStand = findArmorStandById(world, armorStandId);
                    }

                    if (armorStand == null) {
                        UUID armorStandUUID = playerArmorStandMap.get(playerUUID);
                        if (armorStandUUID != null) {
                            armorStand = findArmorStandByUUID(world, armorStandUUID);
                            if (armorStand != null) {
                                armorStandIdMap.put(playerUUID, armorStand.getId());
                            }
                        }
                    }

                    if (armorStand != null) {
                        ItemStack headItem = armorStand.getEquippedStack(EquipmentSlot.HEAD);
                        if (!headItem.isEmpty()) {
                            float playerBodyYaw = player.getBodyYaw();
                            float playerHeadYaw = player.getHeadYaw();

                            if (armorStand.getBodyYaw() != playerBodyYaw) {
                                armorStand.setBodyYaw(playerBodyYaw);
                            }
                            if (armorStand.getHeadYaw() != playerHeadYaw) {
                                armorStand.setHeadYaw(playerHeadYaw);
                            }
                        }
                    }
                }
            }
        });
    }

    private ArmorStandEntity spawnArmorStand(PlayerEntity player) {
        World world = player.getWorld();
        ArmorStandEntity armorStand = new ArmorStandEntity(world, player.getX(), player.getY(), player.getZ());
        armorStand.setInvisible(true);
        armorStand.setNoGravity(true);
        armorStand.setShowArms(false);
        armorStand.setHideBasePlate(true);
        
        ItemStack chestplate = player.getInventory().getArmorStack(2);
        if (hasCustomLoreStatic(chestplate)) {
            System.out.println("[ArmorStandMod] Setze Item mit Lore auf ArmorStand-Kopf beim Spawn für Spieler: " + player.getName().getString());
            armorStand.equipStack(EquipmentSlot.HEAD, chestplate.copy());
        } else {
            System.out.println("[ArmorStandMod] Keine Lore beim Spawn, Head-Slot bleibt leer für Spieler: " + player.getName().getString());
            armorStand.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        
        world.spawnEntity(armorStand);
        return armorStand;
    }

    private void updateArmorStandHead(PlayerEntity player) {
        UUID armorStandUUID = playerArmorStandMap.get(player.getUuid());
        if (armorStandUUID != null) {
            World world = player.getWorld();
            Integer armorStandId = armorStandIdMap.get(player.getUuid());
            ArmorStandEntity armorStand = null;

            if (armorStandId != null) {
                armorStand = findArmorStandById(world, armorStandId);
            }

            if (armorStand == null) {
                armorStand = findArmorStandByUUID(world, armorStandUUID);
                if (armorStand != null) {
                    armorStandIdMap.put(player.getUuid(), armorStand.getId());
                }
            }

            if (armorStand != null) {
                ItemStack chestplate = player.getInventory().getArmorStack(2);
                armorStand.equipStack(EquipmentSlot.HEAD, chestplate.copy());
            }
        }
    }

    private static ArmorStandEntity findArmorStandById(World world, int id) {
        Entity entity = world.getEntityById(id);
        return entity instanceof ArmorStandEntity ? (ArmorStandEntity) entity : null;
    }

    private static ArmorStandEntity findArmorStandByUUID(World world, UUID uuid) {
        Box box = new Box(-30000000, 0, -30000000, 30000000, 256, 30000000);
        for (Entity e : world.getEntitiesByClass(ArmorStandEntity.class, box, entity -> true)) {
            if (e.getUuid().equals(uuid)) {
                return (ArmorStandEntity) e;
            }
        }
        return null;
    }

    private void saveLinks() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(LINK_FILE))) {
            new Gson().toJson(playerArmorStandMap, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLinks() {
        Path path = Paths.get(LINK_FILE);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Map<String, String> map = new Gson().fromJson(reader, Map.class);
                playerArmorStandMap.clear();
                if (map != null) {
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        playerArmorStandMap.put(UUID.fromString(entry.getKey()), UUID.fromString(entry.getValue()));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateArmorStandHeadForPlayer(PlayerEntity player, ItemStack chestplate) {
        UUID armorStandUUID = playerArmorStandMap.get(player.getUuid());
        if (armorStandUUID != null) {
            World world = player.getWorld();
            Integer armorStandId = armorStandIdMap.get(player.getUuid());
            ArmorStandEntity armorStand = null;

            if (armorStandId != null) {
                armorStand = findArmorStandById(world, armorStandId);
            }

            if (armorStand == null) {
                armorStand = findArmorStandByUUID(world, armorStandUUID);
                if (armorStand != null) {
                    armorStandIdMap.put(player.getUuid(), armorStand.getId());
                }
            }

            if (armorStand != null) {
                if (hasCustomLoreStatic(chestplate)) {
                    System.out.println("[ArmorStandMod] Setze Item mit Lore auf ArmorStand-Kopf für Spieler: " + player.getName().getString() + ", Item: " + chestplate);
                    armorStand.equipStack(EquipmentSlot.HEAD, chestplate.copy());
                } else {
                    ItemStack currentHead = armorStand.getEquippedStack(EquipmentSlot.HEAD);
                    if (!currentHead.isEmpty()) {
                        System.out.println("[ArmorStandMod] Keine Lore, leere Head-Slot für Spieler: " + player.getName().getString());
                        armorStand.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    } else {
                        System.out.println("[ArmorStandMod] Keine Lore, Head-Slot ist bereits leer für Spieler: " + player.getName().getString());
                    }
                }
            } else {
                System.out.println("[ArmorStandMod] Kein ArmorStand gefunden für Spieler: " + player.getName().getString());
            }
        } else {
            System.out.println("[ArmorStandMod] Kein ArmorStand-UUID für Spieler: " + player.getName().getString());
        }
    }

    private static boolean hasCustomLoreStatic(ItemStack item) {
        if (item.isEmpty()) return false;
        LoreComponent lore = item.get(DataComponentTypes.LORE);
        return lore != null && !lore.lines().isEmpty();
    }
} 