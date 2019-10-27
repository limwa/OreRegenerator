/**
 * Simple Non Code License (SNCL)
 *
 * Version 2.3.0
 * Copyright © 2019 André Lima
 *
 * The original creator(s) of the data or text under this license is thereby called the licensor.
 * The physical or juridical person obtaining a copy of the data or text under this license is thereby called the licensee.
 * The data, source code or text under this license is therefore called the object.
 *
 * 1. The licensee's rights and obligations
 *
 * 	1.1. The licensee has the right to obtain a free copy of the object.
 * 	1.2. It is the right of the licensee to redistribute unaltered copies of the object although commercial use is utterly forbidden (except with the original licensor's express written consent).
 * 	1.3. The licensee is given the right to adapt or modify the object to suit their needs and to redistribute the modified version subject to the following conditions:
 *
 * 		1.3.1. You must add the following notice in any copy of the object that you may create: Originally written by André Lima in 2019.
 * 		1.3.2. You must not remove the license information (e.g., a header in source code) present in the object.
 * 		1.3.3. The modified version of the object is subjected to the clauses 1.1 and 1.2 of this license.
 * 		1.3.3. You must include the following notice in any object-modified copies you redistribute: This document or data is a derivative of OreRegenerator and the information contained here may or may not represent the original document or data.
 * 		1.3.4. You must include this license along with any object-modified copies you redistribute.
 * 		1.3.5. In case of juridical issues that may arise from licensee edits the licensee is liable instead of the licensor.
 *
 * 2. Liability of the licensor and of the licensee
 *
 * 	2.1. The licensor offers the object as-is and as-available, and makes no representations or warranties of any kind concerning the object. Thus, the licensor is not liable for any use made of the object.
 * 	2.2. The licensee only is liable for any juridical issue related to the use of the object, edited by third parties or not.
 *
 * 3. Termination
 *
 * 	3.1. All of the clauses stated in section 1 are void if the licensee fails to accomplish their obligations established in section 1.
 * 	3.2. If the clause 3.1 becomes true the licensee must pay for any costs the licensor may have with juridical actions against him.
 *
 * 4. Other terms and conditions
 *
 * 	4.1. The licensor shall not be bound by any additional or different terms or conditions communicated by the licensee unless expressly agreed.
 * 	4.2. The licensor has the right to edit at any time the content of this license, however, its effects will not be retroactive.
 * 	4.3. Any modification made by the licensor shall not affect the already published versions of the object, only the future ones.
 */
package me.PixelLima.OreRegenerator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;
import me.PixelLima.OreRegenerator.ranges.RegenerationRange;

public class OreRegenerator extends JavaPlugin implements Listener {

	public static int FIRST_REGEN_MAX = 0;
	public static int FIRST_REGEN_MIN = 0;
	public static int OTHER_REGENS_MAX = 0;
	public static int OTHER_REGENS_MIN = 0;
	public static Plugin plugin;
	
	public static THashSet<RegenerationRange> probabilities = new THashSet<RegenerationRange>();
	private THashSet<Location> activeBlocks = new THashSet<>();
	public static THashSet<Location> scheduledBlocks = new THashSet<>();
	
	private THashSet<UUID> operators = new THashSet<>();
	
	@Override
	public void onEnable() {
		plugin = this;
		saveDefaultConfig();

		reloadData();
		
		Bukkit.getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		saveData();
	}
	
	public void saveData() {
		getConfig().set("active-blocks", null);
		
		activeBlocks.forEach(new TObjectProcedure<Location>() {
			
			int id = 0;
			
			@Override
			public boolean execute(Location location) {
				getConfig().set("active-blocks." + id++, location.serialize());
				return true;
			}
		});
		
		saveConfig();
	}

	@SuppressWarnings("unchecked")
	public void reloadData() {
		FIRST_REGEN_MAX = getConfig().getInt("regeneration.delays.first-regeneration.maximum");
		FIRST_REGEN_MIN = getConfig().getInt("regeneration.delays.first-regeneration.minimum");
		
		if(FIRST_REGEN_MIN > FIRST_REGEN_MAX) {
			
		}
		
		OTHER_REGENS_MAX = getConfig().getInt("regeneration.delays.other-regenerations.maximum");
		OTHER_REGENS_MIN = getConfig().getInt("regeneration.delays.other-regenerations.minimum");
		
		if(OTHER_REGENS_MIN > OTHER_REGENS_MAX) {
			
		}
		
		probabilities.clear();
		Map<String, Object> probs = getConfig().getConfigurationSection("probabilities").getValues(false);
	
		probs.forEach(new BiConsumer<String, Object>() {
			
			float lastProb = 0F;
			
			@Override
			public void accept(String material, Object probability) {
				Material m = Material.getMaterial(material);
				if(m != null) {
					if(probability instanceof Float)
						probabilities.add(new RegenerationRange(material, lastProb, lastProb += (float) probability));
				} else if(material.toUpperCase().equals("SELF")) {
					if(probability instanceof Float)
						probabilities.add(new RegenerationRange("SELF", lastProb, lastProb += (float) probability));					
				}
			}
		});
		
		activeBlocks.clear();
		Map<String, Object> blocks = getConfig().getConfigurationSection("active-blocks").getValues(false);
		
		blocks.forEach((id, location) -> {
			if(location instanceof Map) {
				try {
					if(!activeBlocks.add(Location.deserialize((Map<String, Object>) location).toBlockLocation())) {
						getConfig().set(id, null);
						
					}
				} catch(ClassCastException e) {
					getConfig().set(id, null);
					
				}
			}
		});
		
		saveConfig();
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreakRegen(BlockBreakEvent e) {
		CompletableFuture.runAsync(() -> {
			boolean contains = false;
			synchronized (activeBlocks) {
				contains = activeBlocks.contains(e.getBlock().getLocation()) && !scheduledBlocks.contains(e.getBlock().getLocation());
			}
			if(contains) {
				new RegenerationTask(FIRST_REGEN_MIN, FIRST_REGEN_MAX, e.getBlock());
			}
		});
	}
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onBlockBreakOperate(BlockBreakEvent e) {
		if(operators.contains(e.getPlayer().getUniqueId())) {
			if(activeBlocks.contains(e.getBlock().getLocation())) {
				activeBlocks.remove(e.getBlock().getLocation());
			} else {
				activeBlocks.add(e.getBlock().getLocation());
			}
			e.setCancelled(true);
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(command.getName().equalsIgnoreCase("oreregenerator")) {
			if (args.length == 0) {
				if (sender instanceof Player) {
					Player p = (Player) sender;
					if (operators.contains(p.getUniqueId()))
						operators.remove(p.getUniqueId());
					else
						operators.add(p.getUniqueId());
				}
			} else if(args.length == 1) {
				if(args[0].equalsIgnoreCase("reload")) {
					reloadData();
				} else if(args[0].equalsIgnoreCase("save")) {
					saveData();
				} else {
					
				}
			} else {
				
			}
		}
		return false;
	}
}
