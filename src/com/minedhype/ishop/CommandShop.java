package com.minedhype.ishop;

import java.net.URL;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import com.minedhype.ishop.inventories.InvShop;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import com.minedhype.ishop.inventories.InvAdminShop;
import com.minedhype.ishop.inventories.InvShopList;
import com.minedhype.ishop.inventories.InvStock;

public class CommandShop implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(sender instanceof ConsoleCommandSender && args.length > 0) {
			if(args[0].equalsIgnoreCase("reload")) {
				reloadShop(null);
				return true;
			}
			else {
				sender.sendMessage(Messages.NOT_A_PLAYER.toString());
				return false;
			}
		}
		if(!(sender instanceof Player)) {
			sender.sendMessage(Messages.NOT_A_PLAYER.toString());
			return false;
		}

		Player player = (Player) sender;
		if(args.length == 0)
			listSubCmd(player, label);
		else if(args[0].equalsIgnoreCase("adminshop"))
			adminShop(player);
		else if(args[0].equalsIgnoreCase("create"))
			createStore(player);
		else if(args[0].equalsIgnoreCase("createshop") && args.length >= 2)
			createShop(player, args[1]);
		else if(args[0].equalsIgnoreCase("delete"))
			deleteShop(player);
		else if(args[0].equalsIgnoreCase("deleteid") && args.length >= 2)
			deleteShopID(player, args[1]);
		else if(args[0].equalsIgnoreCase("list") && args.length == 1)
			Bukkit.getServer().getScheduler().runTaskAsynchronously(iShop.getPlugin(), () -> listShops(player, null));
		else if(args[0].equalsIgnoreCase("list") && args.length >= 2)
			Bukkit.getServer().getScheduler().runTaskAsynchronously(iShop.getPlugin(), () -> listShops(player, args[1]));
		else if(args[0].equalsIgnoreCase("listadmin"))
			Bukkit.getServer().getScheduler().runTaskAsynchronously(iShop.getPlugin(), () -> listAdminShops(player));
		else if(args[0].equalsIgnoreCase("manage") && args.length >= 2)
			shopManage(player, args[1]);
		else if(args[0].equalsIgnoreCase("managestock") && args.length >= 2)
			manageStock(player, args[1]);
		else if(args[0].equalsIgnoreCase("reload"))
			reloadShop(player);
		else if(args[0].equalsIgnoreCase("shops"))
			listAllShops(player);
		else if(args[0].equalsIgnoreCase("stock"))
			stockShop(player);
		else if(args[0].equalsIgnoreCase("view") && args.length >= 2)
			viewShop(player, args[1]);
		else
			listSubCmd(player, label);

		return true;
	}

	private void listSubCmd(Player player, String label) {
		player.sendMessage(ChatColor.GOLD + "iShop Commands:");
		player.sendMessage(ChatColor.GRAY + "/" + label + " create");
		player.sendMessage(ChatColor.GRAY + "/" + label + " delete");
		player.sendMessage(ChatColor.GRAY + "/" + label + " deleteid <id>");
		player.sendMessage(ChatColor.GRAY + "/" + label + " list");
		if(iShop.config.getBoolean("publicListCommand") || player.hasPermission(Permission.SHOP_ADMIN.toString()))
			player.sendMessage(ChatColor.GRAY + "/" + label + " list <player>");
		if(iShop.config.getBoolean("publicShopListCommand") || player.hasPermission(Permission.SHOP_ADMIN.toString()))
			player.sendMessage(ChatColor.GRAY + "/" + label + " shops");
		player.sendMessage(ChatColor.GRAY + "/" + label + " manage <id>");
		player.sendMessage(ChatColor.GRAY + "/" + label + " stock");
		player.sendMessage(ChatColor.GRAY + "/" + label + " view <id>");
		if(player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(ChatColor.GRAY + "/" + label + " adminshop");
			player.sendMessage(ChatColor.GRAY + "/" + label + " createshop <player>");
			player.sendMessage(ChatColor.GRAY + "/" + label + " listadmin");
			player.sendMessage(ChatColor.GRAY + "/" + label + " managestock <player>");
			player.sendMessage(ChatColor.GRAY + "/" + label + " reload");
		}
	}

	private void createStore(Player player) {
		if(iShop.config.getBoolean("usePermissions") && !player.hasPermission(Permission.SHOP_CREATE.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}
		if(!iShop.config.getBoolean("enableShopBlock")) {
			player.sendMessage(Messages.DISABLED_SHOP_BLOCK.toString());
			return;
		}

		Block block = player.getTargetBlock(null, 5);
		String shopBlock = iShop.config.getString("shopBlock");
		Material match = Material.matchMaterial(shopBlock);
		if(match == null) {
			try {
				match = Material.matchMaterial(shopBlock.split("minecraft:")[1].toUpperCase());
			} catch(Exception ignored) { }

			if(match == null)
				match = Material.BARREL;
		}
		if(!block.getType().equals(match)) {
			player.sendMessage(Messages.TARGET_MISMATCH.toString());
			return;
		}

		boolean isShopLoc;
		if(iShop.wgLoader != null)
			isShopLoc = iShop.wgLoader.checkRegion(block);
		else
			isShopLoc = true;

		Optional<Shop> shop = Shop.getShopByLocation(block.getLocation());
		if(shop.isPresent() || !isShopLoc) {
			player.sendMessage(Messages.EXISTING_SHOP.toString());
			return;
		}

		boolean limitShops;
		int numShops = Shop.getNumShops(player.getUniqueId());
		if(iShop.config.getBoolean("usePermissions")) {
			int maxShops = 0;
			String permPrefix = Permission.SHOP_LIMIT_PREFIX.toString();
			for(PermissionAttachmentInfo attInfo : player.getEffectivePermissions()) {
				String perm = attInfo.getPermission();
				if(perm.startsWith(permPrefix)) {
					int num;
					try {
						num = Integer.parseInt(perm.substring(perm.lastIndexOf(".")+1));
					} catch(Exception e) { num = 0; }
					if(num > maxShops)
						maxShops = num;
				}
			}
			limitShops = numShops >= maxShops;
		}
		else {
			int numConfig = iShop.config.getInt("defaultShopLimit");
			limitShops = numShops >= numConfig && numConfig >= 0;
		}

		if(player.hasPermission(Permission.SHOP_LIMIT_BYPASS.toString()))
			limitShops = false;

		if(limitShops) {
			player.sendMessage(Messages.SHOP_MAX.toString());
			return;
		}

		double cost = iShop.config.getDouble("createCost");
		Optional<Economy> economy = iShop.getEconomy();
		if(cost > 0 && economy.isPresent()) {
			OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player.getUniqueId());
			EconomyResponse res = economy.get().withdrawPlayer(offPlayer, cost);
			if(!res.transactionSuccess()) {
				player.sendMessage(Messages.SHOP_CREATE_NO_MONEY.toString()+cost);
				return;
			}
		}

		Shop newShop = Shop.createShop(block.getLocation(), player.getUniqueId());
		player.sendMessage(Messages.SHOP_CREATED.toString());
		Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(iShop.getPlugin(), () -> {
			Optional<Shop> shops = Shop.getShopByLocation(block.getLocation());
			Shop.shopList.put(shops.get().shopId(), player.getUniqueId());
		}, 20);
		InvAdminShop inv = new InvAdminShop(newShop);
		inv.open(player, newShop.getOwner());
	}

	private void createShop(Player player, String playerShop) {
		if(!player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}
		if(!iShop.config.getBoolean("enableShopBlock")) {
			player.sendMessage(Messages.DISABLED_SHOP_BLOCK.toString());
			return;
		}

		Block block = player.getTargetBlock(null, 5);
		String shopBlock = iShop.config.getString("shopBlock");
		Material match = Material.matchMaterial(shopBlock);
		if(match == null) {
			try {
				match = Material.matchMaterial(shopBlock.split("minecraft:")[1].toUpperCase());
			} catch(Exception ignored) { }

			if(match == null) {
				match = Material.BARREL;
			}
		}

		if(!block.getType().equals(match)) {
			player.sendMessage(Messages.TARGET_MISMATCH.toString());
			return;
		}

		UUID shopOwner;
		if(playerShop == null) {
			player.sendMessage(Messages.NO_PLAYER_FOUND.toString());
			return;
		} else {
			try {
				shopOwner = getUUID(playerShop);
			} catch (Exception e) {
				player.sendMessage(Messages.NO_PLAYER_FOUND.toString());
				Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[iShop] " +  Messages.NO_PLAYER_FOUND.toString());
				return;
			}
		}

		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(shopOwner);
		if(!offlinePlayer.hasPlayedBefore()) {
			player.sendMessage(Messages.NO_PLAYER_FOUND.toString());
			return;
		}

		Optional<Shop> shop = Shop.getShopByLocation(block.getLocation());
		if(!shop.isPresent()) {
			Shop.createShop(block.getLocation(), shopOwner);
			player.sendMessage(Messages.PLAYER_SHOP_CREATED.toString()
					.replaceAll("%p", playerShop));
			Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(iShop.getPlugin(), () -> {
				Optional<Shop> shops = Shop.getShopByLocation(block.getLocation());
				Shop.shopList.put(shops.get().shopId(), shopOwner);
			}, 20);
		} else { player.sendMessage(Messages.EXISTING_SHOP.toString()); }
	}

	private void adminShop(Player player) {
		if(!player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}

		if(!EventShop.adminShopEnabled) {
			player.sendMessage(Messages.ADMIN_SHOP_DISABLED.toString());
			return;
		}

		Block block = player.getTargetBlock(null, 5);
		String shopBlock = iShop.config.getString("shopBlock");
		Material match = Material.matchMaterial(shopBlock);
		if(match == null) {
			try {
				match = Material.matchMaterial(shopBlock.split("minecraft:")[1].toUpperCase());
			} catch(Exception ignored) { }

			if(match == null)
				match = Material.BARREL;
		}

		if(!block.getType().equals(match)) {
			player.sendMessage(Messages.TARGET_MISMATCH.toString());
			return;
		}

		Optional<Shop> shop = Shop.getShopByLocation(block.getLocation());
		if(shop.isPresent()) {
			player.sendMessage(Messages.EXISTING_SHOP.toString());
			return;
		}

		Shop newShop = Shop.createShop(block.getLocation(), UUID.fromString("00000000-0000-0000-0000-000000000000"), true);
		player.sendMessage(Messages.SHOP_CREATED.toString());
		if(iShop.config.getBoolean("adminShopPublic")) {
			Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(iShop.getPlugin(), () -> {
				Optional<Shop> shops = Shop.getShopByLocation(block.getLocation());
				Shop.shopList.put(shops.get().shopId(), UUID.fromString("00000000-0000-0000-0000-000000000000"));
			}, 20);
		}
		InvAdminShop inv = new InvAdminShop(newShop);
		inv.open(player, newShop.getOwner());
	}

	private void deleteShop(Player player) {
		Block block = player.getTargetBlock(null, 5);
		Optional<Shop> shop = Shop.getShopByLocation(block.getLocation());
		if(!shop.isPresent()) {
			player.sendMessage(Messages.SHOP_NOT_FOUND.toString());
			return;
		}

		if(!shop.get().isOwner(player.getUniqueId()) && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.SHOP_NO_SELF.toString());
			return;
		}

		if(shop.get().isAdmin() && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}

		if(InvStock.inShopInv.containsValue(shop.get().getOwner())) {
			player.sendMessage(Messages.SHOP_BUSY.toString());
			return;
		}

		double cost = iShop.config.getDouble("returnAmount");
		Optional<Economy> economy = iShop.getEconomy();
		if(cost > 0 && economy.isPresent()) {
			OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(shop.get().getOwner());
			economy.get().depositPlayer(offPlayer, cost);
		}

		Shop.shopList.remove(shop.get().shopId());
		shop.get().deleteShop();
		player.sendMessage(Messages.SHOP_DELETED.toString());
	}

	private void deleteShopID(Player player, String shopId) {
		int sID;
		try {
			sID = Integer.parseInt(shopId);
		} catch (Exception e) { sID = -1; }

		if(sID < 0) {
			player.sendMessage(Messages.SHOP_ID_INTEGER.toString());
			return;
		}

		Optional<Shop> shop = Shop.getShopById(sID);
		if(!shop.isPresent()) {
			player.sendMessage(Messages.SHOP_NOT_FOUND.toString());
			return;
		}

		if(!shop.get().isOwner(player.getUniqueId()) && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.SHOP_NO_SELF.toString());
			return;
		}

		if(shop.get().isAdmin() && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}

		if(InvStock.inShopInv.containsValue(shop.get().getOwner())) {
			player.sendMessage(Messages.SHOP_BUSY.toString());
			return;
		}

		double cost = iShop.config.getDouble("returnAmount");
		Optional<Economy> economy = iShop.getEconomy();
		if(cost > 0 && economy.isPresent()) {
			OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(shop.get().getOwner());
			economy.get().depositPlayer(offPlayer, cost);
		}

		Shop.shopList.remove(shop.get().shopId());
		shop.get().deleteShop();
		player.sendMessage(Messages.SHOP_IDDELETED.toString()
				.replaceAll("%id", shopId));
	}

	private void listShops(Player player, String playerName) {
		if(playerName != null && !iShop.config.getBoolean("publicListCommand") && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}

		UUID sOwner;
		if(playerName == null) {
			sOwner = player.getUniqueId();
			playerName = player.getDisplayName();
		} else {
			try {
				sOwner = getUUID(playerName);
			} catch (Exception e) {
				player.sendMessage(Messages.NO_PLAYER_SHOP.toString());
				Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[iShop] " +  Messages.NO_PLAYER_SHOP.toString());
				return;
			}
		}
		Shop.getShopList(player, sOwner, playerName);
	}

	private void listAdminShops(Player player) {
		if(!player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}
		Shop.getAdminShopList(player);
	}

	private void listAllShops(Player player) {
		if(!InvShop.listAllShops && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.SHOP_LIST_DISABLED.toString());
			return;
		}

		InvShopList inv = InvShopList.setShopTitle(Messages.SHOP_LIST_ALL.toString());
		inv.setPag(0);
		inv.open(player);
	}

	private void stockShop(Player player) {
		if(!iShop.config.getBoolean("enableStockCommand") && !player.hasPermission(Permission.SHOP_ADMIN.toString()) && !player.hasPermission(Permission.SHOP_STOCK.toString())) {
			player.sendMessage(Messages.STOCK_COMMAND_DISABLED.toString());
			return;
		}

		if(Shop.getNumShops(player.getUniqueId()) < 1 && iShop.config.getBoolean("mustOwnShopForStock")) {
			player.sendMessage(Messages.NO_SHOP_STOCK.toString());
			return;
		}

		if(InvStock.inShopInv.containsValue(player.getUniqueId())) {
			player.sendMessage(Messages.SHOP_BUSY.toString());
			return;
		} else { InvStock.inShopInv.put(player, player.getUniqueId()); }

		InvStock inv = InvStock.getInvStock(player.getUniqueId());
		inv.setPag(0);
		inv.open(player);
	}

	private void reloadShop(Player player) {
		if(player != null && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}

		iShop plugin = (iShop) Bukkit.getPluginManager().getPlugin("iShop");
		if(plugin != null)
			plugin.createConfig();

		if(player != null)
			player.sendMessage(Messages.SHOP_RELOAD.toString());
		else
			Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[iShop] " + Messages.SHOP_RELOAD.toString());
		EventShop.adminShopEnabled = iShop.config.getBoolean("enableAdminShop");
		EventShop.noShopNoStock = iShop.config.getBoolean("mustOwnShopForStock");
		EventShop.shopBlock = iShop.config.getString("shopBlock");
		EventShop.stockBlock = iShop.config.getString("stockBlock");
		EventShop.stockEnabled = iShop.config.getBoolean("enableStockBlock");
		EventShop.shopEnabled = iShop.config.getBoolean("enableShopBlock");
		EventShop.shopBlk = Material.matchMaterial(EventShop.shopBlock);
		EventShop.stockBlk = Material.matchMaterial(EventShop.stockBlock);
		InvAdminShop.remoteManage = iShop.config.getBoolean("remoteManage");
		InvShop.listAllShops = iShop.config.getBoolean("publicShopListCommand");
		InvShopList.showOwnedShops = iShop.config.getBoolean("publicShopListShowsOwned");
		Shop.shopEnabled = iShop.config.getBoolean("enableShopBlock");
		Shop.shopNotifications = iShop.config.getBoolean("enableShopNotifications");
		Shop.shopOutStock = iShop.config.getBoolean("enableOutOfStockMessages");
		Shop.particleEffects = iShop.config.getBoolean("showParticles");
		Shop.maxDays = iShop.config.getInt("maxInactiveDays");
	}

	private static UUID getUUID(String name) throws Exception {
		Scanner scanner = new Scanner(new URL("https://api.mojang.com/users/profiles/minecraft/" + name).openStream());
		String input = scanner.nextLine();
		scanner.close();
		JSONObject UUIDObject = (JSONObject) JSONValue.parseWithException(input);
		String uuidString = UUIDObject.get("id").toString();
		String uuidSeparation = uuidString.replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]+)", "$1-$2-$3-$4-$5");
		return UUID.fromString(uuidSeparation);
	}

	private static void shopManage(Player player, String shopID) {
		if(!InvAdminShop.remoteManage && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.SHOP_REMOTE.toString());
			return;
		}

		int shopId;
		try {
			shopId = Integer.parseInt(shopID);
		} catch (Exception e) { shopId = -1; }

		if(shopId < 0) {
			player.sendMessage(Messages.SHOP_NO_SELF.toString());
			return;
		}

		Optional<Shop> shop = Shop.getShopById(shopId);
		if(!shop.isPresent() || (!shop.get().isOwner(player.getUniqueId()) && !player.hasPermission(Permission.SHOP_ADMIN.toString()))) {
			player.sendMessage(Messages.SHOP_NO_SELF.toString());
			return;
		}

		if(shop.get().isAdmin()) {
			if(!EventShop.adminShopEnabled) {
				player.sendMessage(Messages.ADMIN_SHOP_DISABLED.toString());
				return;
			}
			if(!player.hasPermission(Permission.SHOP_ADMIN.toString())) {
				player.sendMessage(Messages.NO_PERMISSION.toString());
				return;
			}
		}

		if(InvStock.inShopInv.containsValue(shop.get().getOwner())) {
			player.sendMessage(Messages.SHOP_BUSY.toString());
			return;
		}

		InvAdminShop inv = new InvAdminShop(shop.get());
		inv.open(player, shop.get().getOwner());
	}

	private static void viewShop(Player player, String shopId) {
		if(!iShop.config.getBoolean("remoteShopping") && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.SHOP_NO_REMOTE.toString());
			return;
		}

		int sID;
		try {
			sID = Integer.parseInt(shopId);
		} catch (Exception e) { sID = -1; }

		if(sID < 0) {
			player.sendMessage(Messages.SHOP_ID_INTEGER.toString());
			return;
		}

		Optional<Shop> shop = Shop.getShopById(sID);
		if(!shop.isPresent()) {
			player.sendMessage(Messages.SHOP_NOT_FOUND.toString());
			return;
		}

		if(shop.get().getOwner().equals(player.getUniqueId()) && !InvAdminShop.remoteManage && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.SHOP_REMOTE.toString());
			return;
		}

		if(shop.get().isAdmin() && !EventShop.adminShopEnabled) {
			player.sendMessage(Messages.ADMIN_SHOP_DISABLED.toString());
			return;
		}

		if(InvStock.inShopInv.containsValue(shop.get().getOwner())) {
			player.sendMessage(Messages.SHOP_BUSY.toString());
			return;
		}

		if((shop.get().isAdmin() && player.hasPermission(Permission.SHOP_ADMIN.toString())) || shop.get().isOwner(player.getUniqueId())) {
			InvAdminShop inv = new InvAdminShop(shop.get());
			inv.open(player, shop.get().getOwner());
		} else {
			InvShop inv = new InvShop(shop.get());
			inv.open(player, shop.get().getOwner());
		}
	}

	private void manageStock(Player player, String stockOwner) {
		if(!player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			player.sendMessage(Messages.NO_PERMISSION.toString());
			return;
		}

		UUID sOwner;
		if(stockOwner == null) {
			player.sendMessage(Messages.NO_PLAYER_FOUND.toString());
			return;
		} else {
			try {
				sOwner = getUUID(stockOwner);
			} catch (Exception e) {
				player.sendMessage(Messages.NO_PLAYER_FOUND.toString());
				Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[iShop] " +  Messages.NO_PLAYER_FOUND.toString());
				return;
			}
		}

		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(sOwner);
		if(!offlinePlayer.hasPlayedBefore()) {
			player.sendMessage(Messages.NO_PLAYER_FOUND.toString());
			return;
		}

		if(InvStock.inShopInv.containsValue(sOwner)) {
			player.sendMessage(Messages.SHOP_BUSY.toString());
			return;
		} else { InvStock.inShopInv.put(player, sOwner); }

		InvStock inv = InvStock.getInvStock(sOwner);
		inv.setPag(0);
		inv.open(player);
	}
}
