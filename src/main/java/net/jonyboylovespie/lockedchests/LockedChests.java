package net.jonyboylovespie.lockedchests;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.*;
import static net.minecraft.server.command.CommandManager.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import java.io.*;
import java.util.*;

import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LockedChests implements ModInitializer {
	public static final String MOD_ID = "locked-chests";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ChestOwnership[] removeElement(ChestOwnership[] array, int index) {
		ChestOwnership[] newArray = new ChestOwnership[array.length - 1];
		int newIndex = 0;
        for (ChestOwnership chestOwnership : array) {
            if (chestOwnership != array[index]) {
                newArray[newIndex] = chestOwnership;
                newIndex++;
            }
        }
		return newArray;
	}

	public static ChestOwnership[] addElement(ChestOwnership[] array, ChestOwnership element) {
		ChestOwnership[] newArray = Arrays.copyOf(array, array.length + 1);
		newArray[array.length] = element;
		return newArray;
	}

	public static class ChestOwnership {
		private final BlockPos blockPos;
		private final String owner;
		private String[] trustedPlayers;

		public ChestOwnership(BlockPos blockPos, String owner) {
			this(blockPos, owner, null);
		}

		public ChestOwnership(BlockPos blockPos, String owner, String[] trustedPlayers) {
			this.blockPos = blockPos;
			this.owner = owner;
			this.trustedPlayers = trustedPlayers;
		}
	}

	private static ChestOwnership[] readArrayFromFile(String filePath) {
		try (FileReader reader = new FileReader(filePath)) {
			Gson gson = new Gson();
			return gson.fromJson(reader, ChestOwnership[].class);
		} catch (Exception e) {
			System.out.println("Error reading file: " + e.getMessage());
			return new ChestOwnership[0];
		}
	}
	
	String file;
	
	private void Serialize(ChestOwnership[] Array){
		File serverDir = FabricLoader.getInstance().getGameDir().toFile();
		String file = new File(serverDir, "lockedChests.json").getPath();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(Array);
		if (Array != null){
			try (FileWriter fileWriter = new FileWriter(file)) {
				fileWriter.write(json);
			} catch (IOException e) {
				System.out.println("Error writing JSON to file: " + e.getMessage());
			}
		}
	}
	
	private BlockPos getOtherBlockPos(BlockPos pos, BlockState state){
		BlockPos otherBlockPos = pos;
		if (ChestBlock.getDoubleBlockType(state) != DoubleBlockProperties.Type.SINGLE){
			int xChange = 0;
			int zChange = 0;
			switch (ChestBlock.getFacing(state)) {
				case NORTH -> zChange = -1;
				case EAST -> xChange = 1;
				case SOUTH -> zChange = 1;
				case WEST -> xChange = -1;
			}
			otherBlockPos = new BlockPos(new Vec3i(pos.getX() + xChange, pos.getY(), pos.getZ() + zChange));
		}
		return otherBlockPos;
	}
	
	@Override
	public void onInitialize() {
		
		File serverDir = FabricLoader.getInstance().getGameDir().toFile();
		file = new File(serverDir, "lockedChests.json").getPath();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("trust")
				.then(argument("player", StringArgumentType.string())
						.then(argument("location", BlockPosArgumentType.blockPos()).executes(context -> {
							String playerToTrust = StringArgumentType.getString(context, "player");
							BlockPos location = BlockPosArgumentType.getBlockPos(context, "location");
							ServerPlayerEntity player = context.getSource().getPlayer();
							if (context.getSource().getWorld().getBlockState(location).getBlock() != Blocks.CHEST) {
								Text.literal("Block isn't a chest");
								return 0;
							}
							ChestOwnership[] lockedChests = readArrayFromFile(file);
							String[] newArray = new String[1];
                            for (ChestOwnership lockedChest : lockedChests) {
                                BlockPos blockPos = lockedChest.blockPos;
                                String owner = lockedChest.owner;
                                if (blockPos.equals(location)) {
                                    if (owner.equals(player.getName().getString())) {
                                        String[] trustedPlayers = lockedChest.trustedPlayers;
                                        if (trustedPlayers != null) {
                                            newArray = new String[trustedPlayers.length + 1];
                                            System.arraycopy(trustedPlayers, 0, newArray, 0, trustedPlayers.length);
                                        }
                                        newArray[newArray.length - 1] = playerToTrust;
                                        lockedChest.trustedPlayers = newArray;
                                        Serialize(lockedChests);
                                        context.getSource().sendFeedback(() -> Text.literal("Successfully trusted " + playerToTrust + " with access to this chest"), false);
                                        return 1;
                                    } else {
                                        context.getSource().sendFeedback(() -> Text.literal("You don't own this chest"), false);
                                    }
                                }
                            }
							return 1;
						})))));
		
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("lockchest")
				.then(argument("type", StringArgumentType.string())
						.then(argument("location", BlockPosArgumentType.blockPos())
								.executes(context -> {
									String type = StringArgumentType.getString(context, "type");
									BlockPos location = BlockPosArgumentType.getBlockPos(context, "location");
									ServerPlayerEntity player = context.getSource().getPlayer();
									if (context.getSource().getWorld().getBlockState(location).getBlock() != Blocks.CHEST) {
										Text.literal("Block isn't a chest");
										return 0;
									}
									ChestOwnership[] lockedChests = readArrayFromFile(file);
									BlockPos otherBlockPos = getOtherBlockPos(location, context.getSource().getWorld().getBlockState(location));
									if (type.equals("add")) {
										ChestOwnership[] newArray;
										if (lockedChests != null){
                                            for (ChestOwnership lockedChest : lockedChests) {
                                                BlockPos blockPos = lockedChest.blockPos;
                                                if (blockPos.toString().equals(location.toString()) || blockPos.toString().equals(otherBlockPos.toString())) {
                                                    context.getSource().sendFeedback(() -> Text.literal("This chest has already been claimed"), false);
                                                    return 0;
                                                }
                                            }
											context.getSource().sendFeedback(() -> Text.literal("Adding chest to locked chests"), false);
											newArray = addElement(lockedChests, new ChestOwnership(location, player.getName().getString()));
										}
										else{
											newArray = new ChestOwnership[] {new ChestOwnership(location, player.getName().getString())};
										}
										Serialize(newArray);
										return 1;
									}
									else if (type.equals("remove")) {
										ChestOwnership[] newArray;
										if (lockedChests != null) {
											for (int i = 0; i < lockedChests.length; i++) {
												String name = lockedChests[i].owner;
												BlockPos blockPos = lockedChests[i].blockPos;
												if (blockPos.toString().equals(location.toString()) || blockPos.toString().equals(otherBlockPos.toString())) {
													if (player.getName().getString().equals(name) || player.getServer().getPlayerManager().isOperator(player.getGameProfile())){
														context.getSource().sendFeedback(() -> Text.literal("Removing chest from locked chests"), false);
														newArray = removeElement(lockedChests, i);
														Serialize(newArray);
														return 1;
													}
													else{
														context.getSource().sendFeedback(() -> Text.literal("This chest is owned by " + name), false);
														return 0;
													}
												}
											}
										}
									}
									else {
										context.getSource().sendFeedback(() -> Text.literal("Second argument not recognized"), false);
									}
									return 0;
						})))));
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (state.getBlock() == Blocks.CHEST) {
				if (player == null) {
					return false;
				}
				BlockPos otherBlockPos = getOtherBlockPos(pos, state);
				ChestOwnership[] lockedChests = readArrayFromFile(file);
				String playerName = null;
				for (int i = 0; i < lockedChests.length; i++) {
					String name = lockedChests[i].owner;
					BlockPos blockPos = lockedChests[i].blockPos;
					if (blockPos.toString().equals(pos.toString()) || blockPos.toString().equals(otherBlockPos.toString())) {
						playerName = name;
						if (player.getName().getString().equals(name)) {
							ChestOwnership[] newArray = removeElement(lockedChests, i);
							Serialize(newArray);
							return true;
						}
					}
				}
				if (playerName != null) {
					player.sendMessage(Text.literal("Locked by " + playerName));
					return false;
				}
			}
			return true;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hand != Hand.MAIN_HAND) {
				return ActionResult.PASS;
			}

			BlockPos pos = hitResult.getBlockPos();
			Block block = world.getBlockState(pos).getBlock();
			
			if (block != Blocks.CHEST) {
				return ActionResult.PASS;
			}
			
			ChestOwnership[] lockedChests = readArrayFromFile(file);
			
			if (player.isSneaking()) {
				for (ItemStack itemStack : player.getHandItems()) {
					if (itemStack.getItem().getName().toString().contains("nugget")){
						if (itemStack.getName().getString().equalsIgnoreCase("key")){
							boolean exists = false;
                            for (ChestOwnership lockedChest : lockedChests) {
                                BlockPos blockPos = lockedChest.blockPos;
                                if (blockPos.toString().equals(pos.toString()) || blockPos.toString().equals(getOtherBlockPos(pos, world.getBlockState(pos)).toString())) {
                                    exists = true;
                                }
                            }
							if (!exists) {
								player.sendMessage(Text.literal("Adding chest to locked chests"));
								ChestOwnership[] newArray = addElement(lockedChests, new ChestOwnership(pos, player.getName().getString()));
								Serialize(newArray);
							}
							else {
								for (int i = 0; i < lockedChests.length; i++) {
									String name = lockedChests[i].owner;
									BlockPos blockPos = lockedChests[i].blockPos;
									if (blockPos.toString().equals(pos.toString()) || blockPos.toString().equals(getOtherBlockPos(pos, world.getBlockState(pos)).toString())) {
										if (player.getName().getString().equals(name)) {
											player.sendMessage(Text.literal("Removing chest from locked chests"));
											ChestOwnership[] newArray = removeElement(lockedChests, i);
											Serialize(newArray);
										}
									}
								}
							}
							break;
						}
					}
				}
			}

			BlockPos otherBlockPos = getOtherBlockPos(pos, world.getBlockState(pos));
			
			String playerName = null;
            for (ChestOwnership lockedChest : lockedChests) {
                String[] trustedPlayers = lockedChest.trustedPlayers;
                String name = lockedChest.owner;
                BlockPos blockPos = lockedChest.blockPos;
                if (pos.toString().equals(blockPos.toString()) || otherBlockPos.toString().equals(blockPos.toString())) {
                    playerName = name;
                    if (player.getName().getString().equals(name)) {
                        return ActionResult.PASS;
                    }
                    if (trustedPlayers != null) {
                        for (String trustedPlayer : trustedPlayers) {
                            if (player.getName().getString().equals(trustedPlayer)) {
                                return ActionResult.PASS;
                            }
                        }
                    }
                }
            }
			if (playerName != null) {
				player.sendMessage(Text.literal("Locked by " + playerName));
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}
}