/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.commandline;

import org.apache.ignite.internal.commandline.cache.CacheCommands;

/**
 * High-level commands.
 */
public enum CommandList {
    /** */
    ACTIVATE("--activate", new ActivateCommand()),

    /** */
    DEACTIVATE("--deactivate", new DeactivateCommand()),

    /** */
    STATE("--state", new StateCommand()),

    /** */
    BASELINE("--baseline", new BaselineCommand()),

    /** */
    TX("--tx", new TxCommands()),

    /** */
    CACHE("--cache", new CacheCommands()),

    /** */
    WAL("--wal", new WalCommands());

    /** Private values copy so there's no need in cloning it every time. */
    private static final CommandList[] VALUES = CommandList.values();

    /** */
    private final String text;

    /** Command implementation. */
    private final Command command;

    /**
     * @param text Text.
     * @param command Command implementation.
     */
    CommandList(String text, Command command) {
        this.text = text;
        this.command = command;
    }

    /**
     * @param text Command text.
     * @return Command for the text.
     */
    public static CommandList of(String text) {
        for (CommandList cmd : VALUES) {
            if (cmd.text().equalsIgnoreCase(text))
                return cmd;
        }

        return null;
    }

    /**
     * @return Command text.
     */
    public String text() {
        return text;
    }

    /**
     * @return Command implementation.
     */
    public Command command() {
        return command;
    }

    /** {@inheritDoc} */
    @Override public String toString() { 
        return text; 
    }
}
