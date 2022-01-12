//import com.google.gson.JsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import com.google.gson.Gson;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KafkaConsumerCounts {
    public static void main(String[] args) {
        Properties props = new Properties();
        Gson gson = new Gson();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-countObjects");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0");
        final JsonSerializer jsonNodeSerializer = new JsonSerializer();
        final JsonDeserializer jsonNodeDeserializer = new JsonDeserializer();
        final Serde<JsonNode> jsonNodeSerde = Serdes.serdeFrom(jsonNodeSerializer,jsonNodeDeserializer);



        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, JsonNode> source = builder.stream(AppConfig.INPUT_TOPIC, Consumed.with(Serdes.String(),jsonNodeSerde));
//        source.to("streams-pipe-output");

       source.flatMapValues(value -> Arrays.asList(gson.toJson(value))
               )
               .groupBy(new KeyValueMapper<String, String, String>() {
                   @Override
                   public String apply(String key, String value) {
                       return value;
                   }
               })
               .count(Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as("counts-store"))
               .toStream()
               .to(AppConfig.OUTPUT_TOPIC,  Produced.with(Serdes.String(), Serdes.Long()));
        final Topology topology = builder.build();
        final KafkaStreams streams = new KafkaStreams(topology, props);
        final CountDownLatch latch = new CountDownLatch(1);

// attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
