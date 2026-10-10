package io.casehub.qhorus.e2e;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import org.testcontainers.lifecycle.Startables;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;

public class ClusterTestHarness implements AutoCloseable {

    private static final int APP_PORT = 9741;
    private final Network network = Network.newNetwork();
    private final PostgreSQLContainer<?> postgres;
    private final Map<String, GenericContainer<?>> nodes = new LinkedHashMap<>();
    private final String internalSecret;
    private final List<String> allNodeIds;
    private ImageFromDockerfile image;

    public ClusterTestHarness(String internalSecret, String... nodeIds) {
        this.internalSecret = internalSecret;
        this.allNodeIds = List.of(nodeIds);
        this.postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("qhorus")
                .withUsername("qhorus")
                .withPassword("qhorus")
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withCommand("postgres", "-c", "wal_level=logical");
    }

    public void start() {
        postgres.start();
        List<GenericContainer<?>> containers = allNodeIds.stream()
                .map(this::buildNodeContainer).toList();
        Startables.deepStart(containers).join();
        for (int i = 0; i < allNodeIds.size(); i++) {
            nodes.put(allNodeIds.get(i), containers.get(i));
        }
    }

    public void startNode(String nodeId) {
        GenericContainer<?> container = buildNodeContainer(nodeId);
        container.start();
        nodes.put(nodeId, container);
    }

    private GenericContainer<?> buildNodeContainer(String nodeId) {
        String peers = String.join(",",
                allNodeIds.stream().map(id -> id + ":" + APP_PORT).toList());

        @SuppressWarnings("resource")
        GenericContainer<?> container = new GenericContainer<>(buildImage())
                .withNetwork(network)
                .withNetworkAliases(nodeId)
                .withExposedPorts(APP_PORT)
                .withEnv("QHORUS_HTTP_PORT", String.valueOf(APP_PORT))
                .withEnv("CASEHUB_QHORUS_RELAY_ENABLED", "true")
                .withEnv("CASEHUB_QHORUS_RELAY_NODE_ID", nodeId)
                .withEnv("CASEHUB_QHORUS_RELAY_PEERS", peers)
                .withEnv("CASEHUB_QHORUS_RELAY_ROUTING", "dynamic")
                .withEnv("CASEHUB_QHORUS_RELAY_INTERNAL_SECRET", internalSecret)
                .withEnv("CASEHUB_QHORUS_RELAY_HEARTBEAT_INTERVAL", "2s")
                .withEnv("CASEHUB_QHORUS_RELAY_HEARTBEAT_MISS_THRESHOLD", "3")
                .withEnv("CASEHUB_QHORUS_RELAY_PROXY_TIMEOUT", "3s")
                .withEnv("CASEHUB_QHORUS_CACHE_ENABLED", "true")
                .withEnv("CASEHUB_QHORUS_RELAY_OWNERSHIP_EVALUATION_INTERVAL_SECONDS", "3")
                .withEnv("QHORUS_DB_HOST", "postgres")
                .withEnv("QHORUS_DB_PORT", "5432")
                .withEnv("QHORUS_DB_NAME", "qhorus")
                .withEnv("QHORUS_DB_USER", "qhorus")
                .withEnv("QHORUS_DB_PASSWORD", "qhorus")
                .waitingFor(Wait.forHttp("/health/cluster").forPort(APP_PORT)
                        .withStartupTimeout(Duration.ofSeconds(90)));
        return container;
    }

    public void stopNode(String nodeId) {
        GenericContainer<?> c = nodes.remove(nodeId);
        if (c != null && c.isRunning()) {
            c.stop();
        }
    }

    public String nodeUrl(String nodeId) {
        GenericContainer<?> c = nodes.get(nodeId);
        if (c == null) {
            throw new IllegalArgumentException("Node not running: " + nodeId);
        }
        return "http://" + c.getHost() + ":" + c.getMappedPort(APP_PORT);
    }

    public void waitForClusterConvergence(int expectedSize, Duration timeout) {
        await().atMost(timeout).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            for (String nodeId : nodes.keySet()) {
                Response response = RestAssured.given()
                        .baseUri(nodeUrl(nodeId))
                        .get("/health/cluster");
                response.then().statusCode(200);
                int clusterSize = response.jsonPath().getInt("clusterSize");
                if (clusterSize != expectedSize) {
                    throw new AssertionError(
                            nodeId + " reports clusterSize=" + clusterSize + ", expected=" + expectedSize);
                }
            }
        });
    }

    public Response sendMessage(String nodeId, String channelId, String sender,
                                String type, String content) {
        String body = String.format(
                "{\"sender\":\"%s\",\"type\":\"%s\",\"actorType\":\"AGENT\",\"content\":\"%s\"}",
                sender, type, content);
        return RestAssured.given()
                .baseUri(nodeUrl(nodeId))
                .header("Content-Type", "application/json")
                .body(body)
                .post("/api/channels/" + channelId + "/messages");
    }

    public Response getMessages(String nodeId, String channelId) {
        return RestAssured.given()
                .baseUri(nodeUrl(nodeId))
                .get("/api/channels/" + channelId + "/messages");
    }

    public String createChannel(String nodeId, String channelName) {
        String body = String.format("{\"name\":\"%s\",\"semantic\":\"APPEND\"}", channelName);
        Response response = RestAssured.given()
                .baseUri(nodeUrl(nodeId))
                .header("Content-Type", "application/json")
                .body(body)
                .post("/api/channels");
        response.then().statusCode(201);
        return response.jsonPath().getString("channelId");
    }

    public Response getClusterHealth(String nodeId) {
        return RestAssured.given()
                .baseUri(nodeUrl(nodeId))
                .get("/health/cluster");
    }


    public ChannelOwnershipInfo getOwnership(String nodeId, String channelId) {
        Response response = RestAssured.given()
                                       .baseUri(nodeUrl(nodeId))
                                       .get("/health/cluster/ownership/" + channelId);
        response.then().statusCode(200);
        return new ChannelOwnershipInfo(
                response.jsonPath().getString("owner"),
                response.jsonPath().getString("source"),
                response.jsonPath().getLong("claimWriteCount"));
    }

    public Response getLocalClaims(String nodeId) {
        return RestAssured.given()
                          .baseUri(nodeUrl(nodeId))
                          .get("/health/ownership");
    }

    public CacheStats getCacheStats(String nodeId) {
        Response response = RestAssured.given()
                                       .baseUri(nodeUrl(nodeId))
                                       .get("/health/cache");
        response.then().statusCode(200);
        return new CacheStats(
                response.jsonPath().getString("status"),
                response.jsonPath().getInt("channelsCached"),
                response.jsonPath().getLong("messagesCached"));
    }

    public String createChannelWithId(String nodeId, String channelName, String preAssignedId) {
        String body = String.format(
                "{\"name\":\"%s\",\"semantic\":\"APPEND\",\"preAssignedId\":\"%s\"}",
                channelName, preAssignedId);
        Response response = RestAssured.given()
                                       .baseUri(nodeUrl(nodeId))
                                       .header("Content-Type", "application/json")
                                       .body(body)
                                       .post("/api/channels");
        response.then().statusCode(201);
        return response.jsonPath().getString("channelId");
    }

    public record ChannelOwnershipInfo(String owner, String source, long claimWriteCount) {}

    public record CacheStats(String status, int channelsCached, long messagesCached) {}

    @Override
    public void close() {
        nodes.values().forEach(GenericContainer::stop);
        nodes.clear();
        postgres.stop();
        network.close();
    }

    private ImageFromDockerfile buildImage() {
        if (image == null) {
            String containerMode = System.getProperty("mesh.container.mode", "jvm");
            if ("native".equals(containerMode)) {
                Path meshTarget = Path.of(System.getProperty("mesh.quarkus-app.path",
                                                             "../mesh/target"));
                java.io.File[] runners = meshTarget.toFile().listFiles(
                        (dir, name) -> name.endsWith("-runner"));
                if (runners == null || runners.length == 0) {
                    throw new IllegalStateException(
                            "Native runner not found in " + meshTarget.toAbsolutePath()
                            + " — run 'mvn package -Pnative -pl mesh -am' first");
                }
                image = new ImageFromDockerfile()
                                .withFileFromClasspath("Dockerfile", "Dockerfile.native")
                                .withFileFromPath("mesh-runner", runners[0].toPath());
            } else {
                String meshAppPath = System.getProperty("mesh.quarkus-app.path",
                                                        "../mesh/target/quarkus-app");
                Path meshTarget = Path.of(meshAppPath);
                if (!meshTarget.toFile().exists()) {
                    throw new IllegalStateException(
                            "Mesh quarkus-app not found at " + meshTarget.toAbsolutePath()
                            + " — run 'mvn package -pl mesh' first");
                }
                image = new ImageFromDockerfile()
                                .withFileFromClasspath("Dockerfile", "Dockerfile")
                                .withFileFromPath("quarkus-app", meshTarget);
            }
        }
        return image;
    }
}
