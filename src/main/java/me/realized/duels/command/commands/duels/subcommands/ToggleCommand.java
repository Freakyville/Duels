/*
 * This file is part of Duels, licensed under the MIT License.
 *
 * Copyright (c) Realized
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.realized.duels.command.commands.duels.subcommands;

import java.util.Optional;
import me.realized.duels.DuelsPlugin;
import me.realized.duels.arena.Arena;
import me.realized.duels.command.BaseCommand;
import org.apache.commons.lang.StringUtils;
import org.bukkit.command.CommandSender;

public class ToggleCommand extends BaseCommand {

    public ToggleCommand(final DuelsPlugin plugin) {
        super(plugin, "toggle", "toggle [name]", "Enables or disables an arena.", null, 2, false);
    }

    @Override
    protected void execute(final CommandSender sender, final String label, final String[] args) {
        final Optional<Arena> result = arenaManager.get(StringUtils.join(args, " ", 1, args.length));

        if (!result.isPresent()) {
            // send msg
            return;
        }

        final Arena arena = result.get();
        arena.setDisabled(!arena.isDisabled());
        sender.sendMessage("Arena '" + arena.getName() + "' is now " + (arena.isDisabled() ? "disabled" : "enabled") + "!");
    }
}
