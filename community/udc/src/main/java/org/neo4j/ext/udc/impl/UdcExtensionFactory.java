/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.ext.udc.impl;

import java.util.Timer;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.database.DatabaseManager;
import org.neo4j.kernel.extension.ExtensionFactory;
import org.neo4j.kernel.extension.context.ExtensionContext;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.udc.UsageData;

/**
 * Kernel extension for UDC, the Usage Data Collector. The UDC runs as a background
 * daemon, waking up once a day to collect basic usage information about a long
 * running graph database.
 * <p>
 * The first update is delayed to avoid needless activity during integration
 * testing and short-run applications. Subsequent updates are made at regular
 * intervals. Both times are specified in milliseconds.
 */
@ServiceProvider
public class UdcExtensionFactory extends ExtensionFactory<UdcExtensionFactory.Dependencies>
{
    static final String KEY = "udc";

    public interface Dependencies
    {
        Config config();
        DatabaseManager databaseManager();
        UsageData usageData();
    }

    public UdcExtensionFactory()
    {
        super( KEY );
    }

    @Override
    public Lifecycle newInstance( ExtensionContext extensionContext, UdcExtensionFactory.Dependencies dependencies )
    {
        Config config = dependencies.config();
        return new UdcExtension(
                config,
                dependencies.databaseManager(),
                dependencies.usageData(),
                new Timer( "Neo4j UDC Timer", true ) );
    }
}