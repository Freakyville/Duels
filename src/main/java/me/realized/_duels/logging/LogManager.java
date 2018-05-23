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

package me.realized._duels.logging;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import me.realized._duels.Core;

public class LogManager {

    private static final Logger logger = Logger.getAnonymousLogger();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
    private final Core instance;

    public LogManager(Core instance) {
        this.instance = instance;
    }

    public void init() {
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);

        for (Handler h : logger.getHandlers()) {
            h.close();
        }

        if (!instance.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            instance.getDataFolder().mkdir();
        }

        File folder = new File(instance.getDataFolder(), "logs");
        boolean created = folder.mkdir();

        if (created) {
            instance.info("Generated logs folder.");
        }

        File file = new File(folder, DATE_FORMAT.format(new Date()) + ".log");

        try {
            //noinspection ResultOfMethodCallIgnored
            file.createNewFile();

            FileHandler handler = new FileHandler(file.getCanonicalPath(), true);
            handler.setLevel(Level.ALL);
            handler.setFormatter(new LogFormatter());

            logger.addHandler(handler);

            for (int i = 0; i < 9; i++) {
                switch (i) {
                    case 4:
                        logger.info("Logger was successfully initialized.");
                        break;
                    case 0:
                    case 8:
                        logger.info("------------------------------------------");
                        break;
                    default:
                        logger.info(" ");
                }
            }
        } catch (IOException e) {
            instance.warn("Error while initializing LogManager: " + e.getMessage());
        }
    }

    public void handleDisable() {
        for (Handler h : logger.getHandlers()) {
            h.close();
        }

        logger.info("Plugin is disabling, all handlers were closed.");
    }

    public Logger getLogger() {
        return logger;
    }

    private class LogFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {
            return "[" +
                TIMESTAMP_FORMAT.format(new Date(record.getMillis()))
                + "] [" +
                record.getLevel().getLocalizedName()
                + "] " +
                record.getMessage()
                + '\n';
        }
    }
}
