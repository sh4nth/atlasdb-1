/**
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.cli.command;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

import org.apache.cassandra.tools.NodeProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.palantir.atlasdb.cassandra.CassandraJmxCompactionConfig;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfig;
import com.palantir.atlasdb.cli.services.AtlasDbServices;

import io.airlift.airline.Command;
import io.airlift.airline.Option;

@Command(name = "snapshot", description = "Take a snapshot of a Cassandra KVS")
public class SnapshotCommand extends SingleBackendCommand {

    private static Logger log = LoggerFactory.getLogger(SnapshotCommand.class);

    @Option(name = {"-t", "--tag"},
            description = "Tag the snapshot with this string")
    String name;

    @Override
    public int execute(AtlasDbServices services) {
        if (CassandraKeyValueServiceConfig.TYPE.equals(services.getAtlasDbConfig().keyValueService().type())) {
            //Backup
            CassandraKeyValueServiceConfig config = (CassandraKeyValueServiceConfig) services.getAtlasDbConfig().keyValueService();
            //Set JMX SSL properties
//            CassandraJmxCompaction.createJmxCompactionManager(CassandraKeyValueServiceConfigManager.createSimpleManager(config));
            CassandraJmxCompactionConfig jmxConfig = config.jmx().get();

            String keyStoreFile = jmxConfig.keystore();
            String keyStorePassword = jmxConfig.keystorePassword();
            String trustStoreFile = jmxConfig.truststore();
            String trustStorePassword = jmxConfig.truststorePassword();
            Preconditions.checkState((new File(keyStoreFile)).exists(), "file: '%s' does not exist!", keyStoreFile);
            Preconditions.checkState((new File(trustStoreFile)).exists(), "file: '%s' does not exist!", trustStoreFile);

            if (jmxConfig.ssl()) {
                System.setProperty("javax.net.ssl.keyStore", keyStoreFile);
                System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);
                System.setProperty("javax.net.ssl.trustStore", trustStoreFile);
                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
                System.setProperty("com.sun.management.jmxremote.ssl.need.client.auth", "true");
                System.setProperty("com.sun.management.jmxremote.registry.ssl", "true");
                System.setProperty("ssl.enable", "true");
            }
            Set<InetSocketAddress> cassandraServers = config.servers();
            for (InetSocketAddress addr : cassandraServers) {

                log.info("Connecting to {}:{}", addr.getHostString(), jmxConfig.port());

                try (NodeProbe nodeProbe = new NodeProbe(addr.getHostString(), jmxConfig.port())) {
                    nodeProbe.takeSnapshot(name, null, config.keyspace());
                    log.info("Taken snapshot of {}:{}", addr.getHostString(), jmxConfig.port());
                } catch (IOException e) {
                    throw new RuntimeException(String.format("Error attempting to take snapshot on node %s", addr.getHostString()), e);
                }
            }
            log.info("SNAPSHOTTING DONE");
        }
        return 0;
    }
}
