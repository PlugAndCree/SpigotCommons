package org.spigot.commons.commands;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigot.commons.commands.annotations.Inherit;
import org.spigot.commons.commands.annotations.NoInherit;
import org.spigot.commons.util.CommonReflection;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a basic executable command or subcommand, without any logic
 * other than argument parsing and subcommand chain calling <br><br>
 * 
 * A basic usage may be just implementing the {@link #execute(CommandSender, ExecutionContext)}
 * method and registering the command with the {@link #register(JavaPlugin)} method
 * 
 * @author MRtecno98
 * @since 2.0.0
 */
@Getter
@NoInherit
@RequiredArgsConstructor
public abstract class Command implements CommandExecutor {
	private final String label;
	private Collection<Command> subcommands = new ArrayList<>();
	
	/**
	 * Runs all the command logic other than argument parsing and the calling of subcommands,
	 * it is called automatically by the onCommand method if correctly registered to Bukkit.
	 * 
	 * @see CommandExecutor#onCommand(CommandSender, org.bukkit.command.Command, String, String[])
	 * @param sender an object representing the source of the command
	 * @param context a data instance containing various context information about this particular execution
	 * @return If true, stops the command chain call even if there are more subcommands entered by the user
	 */
	public abstract boolean execute(CommandSender sender, ExecutionContext context);
	
	@Override
	public boolean onCommand(CommandSender sender, org.bukkit.command.Command bukkitCommand, String label, String[] args) {
		List<String> arguments = Arrays.asList(args);
		
		String nextLabel = null;
		Optional<Command> nextCommand = Optional.empty();
		List<String> callArguments = new ArrayList<>();

		int i;
		for (i = 0; i < arguments.size(); i++) {
			String arg = arguments.get(i);
			nextCommand = getSubcommands().stream()
					.filter((cmd) -> cmd.checkLabel(arg)).findAny();

			if (nextCommand.isPresent()) {
				nextLabel = arg;
				break;
			} else callArguments.add(arg);
		}
		
		final int finalIndex = i;
		final String finalNextLabel = nextLabel;

		ExecutionContext context = new ExecutionContext(label, bukkitCommand, arguments.subList(0, finalIndex), !nextCommand.isPresent());
		if(execute(sender, context)) return true;

		nextCommand.ifPresent((next) -> {
			try {
				for (Field f : getClass().getDeclaredFields()) {
					boolean inherit = !getClass().isAnnotationPresent(NoInherit.class);
					inherit &= !f.isAnnotationPresent(NoInherit.class);
					inherit |= f.isAnnotationPresent(Inherit.class);
					
					if (!inherit || f.getName().contains("$"))
						continue;
					
					Field nextF;
					try {
						nextF = next.getClass().getDeclaredField(f.getName());
					} catch(NoSuchFieldException exc) {
						continue;
					}
					
					// Copy all data to inherited fields of next subcommand
					nextF.setAccessible(true);
					nextF.set(next, f.get(this));
					
					// Tried to reset values after transfer, bad idea
					// f.set(this, CommonReflection.getDefaultValue(f.getType()));
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			
			// Cut away all arguments we already processed AND the subcommand label
			// Delimiters are i + 1(to cut away the label) and the list size
			// (a.k.a. end of the list), so final size will be the subtraction.
			String[] subArgs = arguments.subList(finalIndex + 1, arguments.size())
					.toArray(new String[arguments.size() - finalIndex - 1]);
			
			next.onCommand(sender, bukkitCommand, finalNextLabel, subArgs);
		});

		return true;
	}

	/**
	 * Registers this Command instance to the Bukkit {@link CommandMap} singleton instance.<br>
	 * Should not be ran if you don't expect this Command to be executed by itself without a parent supercommand.
	 * 
	 * @param plugin a plugin instance to link this command to, if the plugin 
	 * doesn't have a command entry in its <code>plugin.yml</code><br>
	 * for this command label, this will try to create one on-the-fly using reflection.
	 * 
	 * @see #unregister(JavaPlugin)
	 * @see #getPluginCommand(JavaPlugin)
	 * @see CommonReflection#registerToCommandMap(JavaPlugin, org.bukkit.command.Command)
	 */
	public void register(JavaPlugin plugin) {
		getPluginCommand(plugin).setExecutor(this);
	}
	
	/**
	 * If previously registered, unregisters this command from the Bukkit {@link CommandMap}
	 * 
	 * @param plugin the plugin instance this command was registered with
	 * @see #register(JavaPlugin)
	 */
	public void unregister(JavaPlugin plugin) {
		try {
			getPluginCommand(plugin).unregister((SimpleCommandMap) CommonReflection
					.getPrivateField(plugin.getServer().getPluginManager(), "commandMap"));
		} catch (SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Provides an instance of {@link PluginCommand} for this command linked to the provided plugin, <br>
	 * either using a <code>plugin.yml</code> entry or by constructing one from scratch on-the-fly using reflection.
	 * 
	 * @param pl the plugin instance to link this entry to
	 * @return An instance of {@link PluginCommand} specific for this command
	 * 
	 * @see CommonReflection#constructPluginCommand(String, org.bukkit.plugin.Plugin)
	 */
	public PluginCommand getPluginCommand(JavaPlugin pl) {
		return providePluginCommand(pl, getLabel());
	}
	
	/**
	 * Checks if the given label identifies this command, a unique match is not required
	 * 
	 * @param target a {@link String} to check for
	 * @return <code>true</code> if this label does correspond to this command, <code>false</code> otherwise
	 */
	public boolean checkLabel(String target) {
		return getLabel().equalsIgnoreCase(target);
	}
	
	/**
	 * Register the specified {@link Command} as a subcommand of this command, 
	 * making this instance its parent(or <i>supercommand</i>)
	 * 
	 * @param cmd the instance to register as subcommand
	 * @return This command instance
	 */
	public Command registerSubcommand(Command cmd) {
		getSubcommands().add(cmd);
		return this;
	}
	
	/**
	 * If a PluginCommand is available for this label, returns it, otherwise constructs it using reflection
	 * 
	 * @param plugin the plugin instance linked to this label
	 * @param name a command label to get the PluginCommand for
	 * @return A {@link PluginCommand} instance
	 */
	public static PluginCommand providePluginCommand(JavaPlugin plugin, String name) {
		PluginCommand pcommand = plugin.getCommand(name);

		if (pcommand == null)
			pcommand = CommonReflection.constructPluginCommand(name, plugin);

		return pcommand;
	}
}
