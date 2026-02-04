package de.mcjunky33;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

public class SpawnElytraCraftattack implements ModInitializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/elytra_areas.json");

    private static final Map<String, ElytraArea> AREAS = new LinkedHashMap<>();
    public static final Map<UUID, PlayerFlightData> PLAYER_FLIGHTS = new HashMap<>();
    private static final Map<UUID, Long> PLAYER_INAIR = new HashMap<>();

    @Override
    public void onInitialize() {
        loadAreas();
        registerCommands();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                handlePlayerFlight(player);
            }
        });
    }

    // -------------------- AREA AND FLIGHT CLASSES --------------------
    private static class ElytraArea {
        String name;
        boolean isRadius;
        boolean isWorldspawn;
        double x1, y1, z1, x2, y2, z2;
        int maxBoosts;

        public ElytraArea(String name, double x, double y, double z, double radius, int maxBoosts, boolean worldspawn) {
            this.name = name;
            this.isRadius = true;
            this.isWorldspawn = worldspawn;
            this.x1 = x;
            this.y1 = y;
            this.z1 = z;
            this.x2 = radius;
            this.maxBoosts = maxBoosts;
        }

        public ElytraArea(String name, double x1, double y1, double z1, double x2, double y2, double z2, int maxBoosts) {
            this.name = name;
            this.isRadius = false;
            this.isWorldspawn = false;
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
            this.z2 = Math.max(z1, z2);
            this.maxBoosts = maxBoosts;
        }

        public boolean contains(ServerPlayer player) {
            Vec3 pos = player.position();
            if (isRadius) {
                double dx = pos.x - x1;
                double dy = pos.y - y1;
                double dz = pos.z - z1;
                return dx * dx + dy * dy + dz * dz <= x2 * x2;
            } else {
                return pos.x >= x1 && pos.x <= x2
                        && pos.y >= y1 && pos.y <= y2
                        && pos.z >= z1 && pos.z <= z2;
            }
        }
    }

    private static class PlayerFlightData {
        int boostsRemaining;
        String areaName;
        boolean inFlight;
        boolean wasSneaking = false;

        int boostTicks = 0;
        Vec3 boostVector = Vec3.ZERO;

        PlayerFlightData(String areaName, int boosts) {
            this.areaName = areaName;
            this.boostsRemaining = boosts;
            this.inFlight = false;
        }
    }

    // -------------------- FLIGHT HANDLING --------------------
    private void handlePlayerFlight(ServerPlayer player) {
        GameType gm = player.gameMode.getGameModeForPlayer();
        if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) return;

        UUID uuid = player.getUUID();
        PlayerFlightData flightData = PLAYER_FLIGHTS.get(uuid);

        // 1. Prüfen, ob der Spieler in einer Area ist
        ElytraArea currentArea = null;
        for (ElytraArea area : AREAS.values()) {
            if (area.contains(player)) {
                currentArea = area;
                break;
            }
        }

        boolean onGround = player.onGround();
        boolean inWater = player.isInWater() || player.isInLava();
        boolean isRiding = player.getVehicle() != null;

        // 2. LANDUNG: Hier wird der Mod-Status gelöscht
        if (onGround || inWater || isRiding) {
            if (flightData != null) {
                PLAYER_FLIGHTS.remove(uuid);
                player.stopFallFlying(); // Animation beenden
            }
            PLAYER_INAIR.remove(uuid);
            return;
        }

        // 3. FLUG-ERHALTUNG & SCHUTZ (Auch außerhalb der Area!)
        if (flightData != null && flightData.inFlight) {
            // Das hier verhindert, dass Minecraft den Flug abbricht, wenn keine Elytra getragen wird
            player.startFallFlying();

            // Kein Falldamage, solange dieser Status aktiv ist
            player.fallDistance = 0;
        }

        // 4. START-LOGIK: Nur innerhalb einer Area möglich
        if (currentArea != null && flightData == null) {
            if (!onGround) {
                if (!PLAYER_INAIR.containsKey(uuid)) {
                    PLAYER_INAIR.put(uuid, System.currentTimeMillis());
                }

                long elapsed = System.currentTimeMillis() - PLAYER_INAIR.get(uuid);
                if (elapsed >= 500) {
                    // Flug initialisieren
                    flightData = new PlayerFlightData(currentArea.name, currentArea.maxBoosts);
                    flightData.inFlight = true;
                    PLAYER_FLIGHTS.put(uuid, flightData);
                    player.startFallFlying();
                }
            } else {
                PLAYER_INAIR.remove(uuid);
            }
        } else if (currentArea == null && flightData == null) {
            // Außerhalb der Area ohne aktiven Flug -> Timer weg
            PLAYER_INAIR.remove(uuid);
        }

        // 5. BOOSTS
        handlePlayerBoost(player, flightData);
    }

    private void handlePlayerBoost(ServerPlayer player, PlayerFlightData flightData) {
        if (flightData == null || !flightData.inFlight || flightData.boostsRemaining <= 0) return;

        boolean isSneaking = player.isCrouching();

        if (isSneaking && !flightData.wasSneaking) {
            flightData.wasSneaking = true;
            flightData.boostsRemaining--;

            Vec3 look = player.getLookAngle().normalize();

            double horizontal = 1.5;
            double vertical = 1.0;

            Vec3 boost = new Vec3(
                    look.x * horizontal,
                    vertical,
                    look.z * horizontal
            );

            player.setDeltaMovement(player.getDeltaMovement().add(boost));

            player.hurtMarked = true;

            player.displayClientMessage(
                    Component.literal("Boosts remaining: " + flightData.boostsRemaining),
                    true
            );
        } else if (!isSneaking) {
            flightData.wasSneaking = false;
        }
    }



    // -------------------- COMMANDS --------------------
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("selytra")
                    .requires(src -> {
                        if (src.getPlayer() != null) {

                            com.mojang.authlib.GameProfile profile = src.getPlayer().getGameProfile();

                            net.minecraft.server.players.NameAndId nameAndId = new net.minecraft.server.players.NameAndId(profile);

                            return src.getServer().getPlayerList().isOp(nameAndId);
                        }
                        return true;
                    })
                    .then(Commands.literal("addarea")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .then(Commands.literal("worldspawn")
                                            .then(Commands.argument("radius", DoubleArgumentType.doubleArg())
                                                    .executes(this::addWorldspawnArea)
                                            )
                                    )
                                    .then(Commands.literal("radius")
                                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                            .then(Commands.argument("z", DoubleArgumentType.doubleArg()
                                                                    ).then(Commands.argument("radius", DoubleArgumentType.doubleArg())
                                                                            .executes(this::addRadiusArea)
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                                    .then(Commands.literal("box")
                                            .then(Commands.argument("x1", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("y1", DoubleArgumentType.doubleArg())
                                                            .then(Commands.argument("z1", DoubleArgumentType.doubleArg())
                                                                    .then(Commands.argument("x2", DoubleArgumentType.doubleArg())
                                                                            .then(Commands.argument("y2", DoubleArgumentType.doubleArg())
                                                                                    .then(Commands.argument("z2", DoubleArgumentType.doubleArg())
                                                                                            .executes(this::addBoxArea)
                                                                                    )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )

                    .then(Commands.literal("editarea")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .then(Commands.literal("worldspawn")
                                            .then(Commands.argument("radius", DoubleArgumentType.doubleArg())
                                                    .executes(this::editWorldspawnArea)
                                            )
                                    )
                                    .then(Commands.literal("radius")
                                            .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg())
                                                                            .executes(this::editRadiusArea)
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                                    .then(Commands.literal("box")
                                            .then(Commands.argument("x1", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("y1", DoubleArgumentType.doubleArg())
                                                            .then(Commands.argument("z1", DoubleArgumentType.doubleArg())
                                                                    .then(Commands.argument("x2", DoubleArgumentType.doubleArg())
                                                                            .then(Commands.argument("y2", DoubleArgumentType.doubleArg())
                                                                                    .then(Commands.argument("z2", DoubleArgumentType.doubleArg()
                                                                                            ).executes(this::editBoxArea)
                                                                                    )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )

                    .then(Commands.literal("editmaxboosts")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                            .executes(this::editMaxBoosts)
                                    )
                            )
                    )

                    .then(Commands.literal("removearea")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .executes(this::removeArea)
                            )
                    )

                    .then(Commands.literal("listarea")
                            .executes(this::listAreas)
                    )
            );
        });
    }

    // -------------------- COMMAND METHODS --------------------
    private int addWorldspawnArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        ElytraArea area = new ElytraArea(name, 0, 0, 0, radius, 3, true);
        AREAS.put(name, area);
        saveAreas();
        ctx.getSource().sendSuccess(() -> Component.literal("Area " + name + " added around worldspawn with radius " + radius), true);
        return 1;
    }

    private int addRadiusArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        ElytraArea area = new ElytraArea(name, x, y, z, radius, 3, false);
        AREAS.put(name, area);
        saveAreas();
        ctx.getSource().sendSuccess(() -> Component.literal("Area " + name + " added at " + x + "," + y + "," + z + " with radius " + radius), true);
        return 1;
    }

    private int addBoxArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        double x1 = DoubleArgumentType.getDouble(ctx, "x1");
        double y1 = DoubleArgumentType.getDouble(ctx, "y1");
        double z1 = DoubleArgumentType.getDouble(ctx, "z1");
        double x2 = DoubleArgumentType.getDouble(ctx, "x2");
        double y2 = DoubleArgumentType.getDouble(ctx, "y2");
        double z2 = DoubleArgumentType.getDouble(ctx, "z2");
        ElytraArea area = new ElytraArea(name, x1, y1, z1, x2, y2, z2, 3);
        AREAS.put(name, area);
        saveAreas();
        ctx.getSource().sendSuccess(() -> Component.literal("Box area " + name + " added."), true);
        return 1;
    }

    private int editWorldspawnArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        ElytraArea area = AREAS.get(name);
        if (area != null) {
            area.isRadius = true;
            area.isWorldspawn = true;
            area.x1 = 0;
            area.y1 = 0;
            area.z1 = 0;
            area.x2 = radius;
            saveAreas();
            ctx.getSource().sendSuccess(() -> Component.literal("Edited worldspawn area " + name), true);
        } else ctx.getSource().sendFailure(Component.literal("Area not found"));
        return 1;
    }

    private int editRadiusArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        ElytraArea area = AREAS.get(name);
        if (area != null) {
            area.isRadius = true;
            area.isWorldspawn = false;
            area.x1 = x;
            area.y1 = y;
            area.z1 = z;
            area.x2 = radius;
            saveAreas();
            ctx.getSource().sendSuccess(() -> Component.literal("Edited radius area " + name), true);
        } else ctx.getSource().sendFailure(Component.literal("Area not found"));
        return 1;
    }

    private int editBoxArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        double x1 = DoubleArgumentType.getDouble(ctx, "x1");
        double y1 = DoubleArgumentType.getDouble(ctx, "y1");
        double z1 = DoubleArgumentType.getDouble(ctx, "z1");
        double x2 = DoubleArgumentType.getDouble(ctx, "x2");
        double y2 = DoubleArgumentType.getDouble(ctx, "y2");
        double z2 = DoubleArgumentType.getDouble(ctx, "z2");
        ElytraArea area = AREAS.get(name);
        if (area != null) {
            area.isRadius = false;
            area.isWorldspawn = false;
            area.x1 = Math.min(x1, x2);
            area.y1 = Math.min(y1, y2);
            area.z1 = Math.min(z1, z2);
            area.x2 = Math.max(x1, x2);
            area.y2 = Math.max(y1, y2);
            area.z2 = Math.max(z1, z2);
            saveAreas();
            ctx.getSource().sendSuccess(() -> Component.literal("Edited box area " + name), true);
        } else ctx.getSource().sendFailure(Component.literal("Area not found"));
        return 1;
    }

    private int editMaxBoosts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        ElytraArea area = AREAS.get(name);
        if (area != null) {
            area.maxBoosts = value;
            saveAreas();
            ctx.getSource().sendSuccess(() -> Component.literal("Max boosts for " + name + " set to " + value), true);
        } else ctx.getSource().sendFailure(Component.literal("Area not found"));
        return 1;
    }

    private int removeArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        if (AREAS.remove(name) != null) {
            saveAreas();
            ctx.getSource().sendSuccess(() -> Component.literal("Area " + name + " removed"), true);
        } else ctx.getSource().sendFailure(Component.literal("Area not found"));
        return 1;
    }

    private int listAreas(CommandContext<CommandSourceStack> ctx) {
        if (AREAS.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No areas defined"), false);
        } else {
            AREAS.values().forEach(a -> ctx.getSource().sendSuccess(() ->
                    Component.literal(a.name + (a.isRadius ? " (Radius)" : " (Box)") + " MaxBoosts: " + a.maxBoosts), false));
        }
        return 1;
    }

    // -------------------- SAVE/LOAD --------------------
    private void saveAreas() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            FileWriter fw = new FileWriter(CONFIG_FILE);
            GSON.toJson(AREAS.values(), fw);
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadAreas() {
        try {
            if (!CONFIG_FILE.exists()) return;
            FileReader fr = new FileReader(CONFIG_FILE);
            ElytraArea[] arr = GSON.fromJson(fr, ElytraArea[].class);
            fr.close();
            if (arr != null) {
                for (ElytraArea a : arr) AREAS.put(a.name, a);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
