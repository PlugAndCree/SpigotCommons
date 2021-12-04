package org.spigot.commons.tests.commands;

import java.util.Arrays;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.spigot.commons.commands.Command;
import org.spigot.commons.commands.ExecutionContext;

public class ArgTabCompleteTestCommand extends Command {
	public static final List<String> CONTROL_DATA = Arrays.asList("sas1", "sas2");
	
	public ArgTabCompleteTestCommand() {
		super("testsingle", 1);
	}

	@Override
	public boolean execute(CommandSender sender, ExecutionContext context) {
		return true;
	}
	
	@Override
	public List<String> tabComplete(CommandSender sender, ExecutionContext context) {
		return CONTROL_DATA;
	}
}
