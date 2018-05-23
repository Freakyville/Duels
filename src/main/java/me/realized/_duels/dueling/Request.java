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

package me.realized._duels.dueling;

import java.util.UUID;
import org.bukkit.Location;

public class Request {

    private final UUID sender;
    private final UUID target;
    private final String arena;
    private final Location base;
    private final long time;
    private String kit;

    public Request(UUID sender, UUID target, Settings settings) {
        this.sender = sender;
        this.target = target;
        this.kit = settings.getKit();
        this.arena = settings.getArena();
        this.base = settings.getBase();
        this.time = System.currentTimeMillis();
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getTarget() {
        return target;
    }

    public String getKit() {
        return kit;
    }

    public void setKit(String kit) {
        this.kit = kit;
    }

    public String getArena() {
        return arena;
    }

    public Location getBase() {
        return base;
    }

    public long getTime() {
        return time;
    }
}
