package net.corda.plugins.cordform;

import static java.util.Collections.emptyList;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import java.util.List;
import java.util.Map;

public class CommonNode {
    protected static final String DEFAULT_HOST = "localhost";

    /**
     * Name of the node.
     */
    private String name;

    public String getName() {
        return name;
    }

    /**
     * A list of advertised services ID strings.
     */
    public List<String> advertisedServices = emptyList();

    /**
     * If running a distributed notary, a list of node addresses for joining the Raft cluster
     */
    public List<String> notaryClusterAddresses = emptyList();
    /**
     * Set the RPC users for this node. This configuration block allows arbitrary configuration.
     * The recommended current structure is:
     * [[['username': "username_here", 'password': "password_here", 'permissions': ["permissions_here"]]]
     * The above is a list to a map of keys to values using Groovy map and list shorthands.
     *
     * Incorrect configurations will not cause a DSL error.
     */
    public List<Map<String, Object>> rpcUsers = emptyList();

    protected Config config = ConfigFactory.empty();

    public Config getConfig() {
        return config;
    }

    /**
     * Set the name of the node.
     *
     * @param name The node name.
     */
    public void name(String name) {
        this.name = name;
        config = config.withValue("myLegalName", ConfigValueFactory.fromAnyRef(name));
    }

    /**
     * Set the nearest city to the node.
     *
     * @param nearestCity The name of the nearest city to the node.
     */
    public void nearestCity(String nearestCity) {
        config = config.withValue("nearestCity", ConfigValueFactory.fromAnyRef(nearestCity));
    }

    /**
     * Set the Artemis P2P port for this node.
     *
     * @param p2pPort The Artemis messaging queue port.
     */
    public void p2pPort(Integer p2pPort) {
        config = config.withValue("p2pAddress", ConfigValueFactory.fromAnyRef(DEFAULT_HOST + ':' + p2pPort));
    }

    /**
     * Set the Artemis RPC port for this node.
     *
     * @param rpcPort The Artemis RPC queue port.
     */
    public void rpcPort(Integer rpcPort) {
        config = config.withValue("rpcAddress", ConfigValueFactory.fromAnyRef(DEFAULT_HOST + ':' + rpcPort));
    }

    /**
     * Set the port which to bind the Copycat (Raft) node to
     *
     * @param notaryPort The Raft port.
     */
    public void notaryNodePort(Integer notaryPort) {
        config = config.withValue("notaryNodeAddress", ConfigValueFactory.fromAnyRef(DEFAULT_HOST + ':' + notaryPort));
    }
}
