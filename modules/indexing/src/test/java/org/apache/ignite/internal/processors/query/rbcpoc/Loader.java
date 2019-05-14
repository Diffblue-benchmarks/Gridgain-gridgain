package org.apache.ignite.internal.processors.query.rbcpoc;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class Loader {
    private static final String SQL_PUBLIC_BATCH_TRADE_LOOKUP = "BATCH_TRADE_LOOKUP_CACHE";
    private static final String SQL_PUBLIC_BATCH_TRADE_FULL = "BATCH_TRADE_FULL_CACHE";
    private static final String SQL_PUBLIC_BATCH = "BATCH_CACHE";
    private static final String SQL_PUBLIC_BATCH_ONHEAP = "BATCH_ONHEAP_CACHE";
    private static final String SQL_PUBLIC_TRADE = "TRADE_CACHE";
    private static final String SQL_PUBLIC_RISK = "RISK_CACHE";
    public static final int RISKS = 200_000 ; // TODO: /100
    public static final int TRADES = 50_000; // TODO: /100
    public static final int BOOKS = 5;
    public static final int BATCHES = 100;

    public static void main(String[] args) {
        Ignition.setClientMode(true);
        Ignite ignite = Ignition.start(Server.getConfiguration());

        ignite.addCacheConfiguration(
            new CacheConfiguration<>("ONHEAP")
                .setSqlOnheapCacheEnabled(true)
                .setCopyOnRead(false)
                .setCacheMode(CacheMode.REPLICATED)
        );

        String[] ddls = readDdl();

        for (String ddl : ddls)
            runSql(ignite, ddl);

        System.out.println(">>>> Created tables");

        fill(ignite);

        System.out.println(">>>> Filled tables");

        ignite.close();
    }

    private static String[] readDdl() {
        StringBuilder sb = new StringBuilder();

        try (
            InputStream resource = new FileInputStream("/home/tledkov/work/incubator-ignite/modules/indexing/src/test/java/org/apache/ignite/internal/processors/query/rbcpoc/ddl.sql");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))
        ) {
            bufferedReader
                .lines()
                .forEachOrdered(sb::append);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        String sql = sb.toString();

        return sql.split(";");
    }

    private static void runSql(Ignite ignite, String sql) {
        IgniteCache cache = ignite.getOrCreateCache("gateway");

        cache.query(new SqlFieldsQuery(sql).setSchema("PUBLIC")).getAll();
    }

    private static void fill(Ignite ignite) {
        IgniteDataStreamer<BinaryObject, BinaryObject> tradeStream= ignite.dataStreamer(SQL_PUBLIC_TRADE);
        IgniteDataStreamer<BinaryObject, BinaryObject> riskStream = ignite.dataStreamer(SQL_PUBLIC_RISK);
        IgniteDataStreamer<BinaryObject, BinaryObject> batchStream = ignite.dataStreamer(SQL_PUBLIC_BATCH);
        IgniteDataStreamer<BinaryObject, BinaryObject> batchOnheapStream = ignite.dataStreamer(SQL_PUBLIC_BATCH_ONHEAP);
        IgniteDataStreamer<BinaryObject, BinaryObject> batchTradeLookupStream = ignite.dataStreamer(SQL_PUBLIC_BATCH_TRADE_LOOKUP);
        IgniteDataStreamer<BinaryObject, BinaryObject> batchTradeFullStream = ignite.dataStreamer(SQL_PUBLIC_BATCH_TRADE_FULL);

        tradeStream.keepBinary(true);
        riskStream.keepBinary(true);
        batchStream.keepBinary(true);
        batchOnheapStream.keepBinary(true);
        batchTradeLookupStream.keepBinary(true);
        batchTradeFullStream.keepBinary(true);

        tradeStream.allowOverwrite(false);
        riskStream.allowOverwrite(false);
        batchStream.allowOverwrite(false);
        batchOnheapStream.allowOverwrite(false);
        batchTradeLookupStream.allowOverwrite(false);
        batchTradeFullStream.allowOverwrite(false);

        for (long tradeId = 0; tradeId < TRADES; tradeId++)
            putTradeRecord(tradeStream, ignite, tradeId);

        System.out.println(">>>>>> Loaded trades");

        for (long i = 0; i < BATCHES; i++) {
            long batchKey = i;

            putBatchRecord(batchStream, ignite, batchKey);
            putBatchRecord(batchOnheapStream, ignite, batchKey);

            IntStream.range(0, RISKS / BATCHES).forEach(batchRisk -> {
                    long riskId = batchKey * RISKS / BATCHES + batchRisk;
                    long tradeId = ThreadLocalRandom.current().nextInt(TRADES);

                    putRiskRecord(riskStream, ignite, riskId, tradeId, batchKey);

                    putBatchTradeLookupRecord(batchTradeLookupStream, ignite, tradeId, batchKey);

                    putBatchTradeFullRecord(batchTradeFullStream, ignite, tradeId, batchKey);
                }
            );

            if ((batchKey + 1) % 10 == 0)
                System.out.println(">>>>>> Loaded batch " + (batchKey + 1));
        }

        tradeStream.close();
        riskStream.close();
        batchStream.close();
        batchOnheapStream.close();
        batchTradeLookupStream.close();
        batchTradeFullStream.close();
    }

    private static void putBatchTradeFullRecord(IgniteDataStreamer<BinaryObject, BinaryObject> stream, Ignite ignite,
        long tradeId, long batchKey) {
        BinaryObjectBuilder bKey = ignite.binary().builder("BATCH_TRADE_FULL_KEY");
        bKey.setField("BATCHKEY", batchKey);
        bKey.setField("TRADEIDENTIFIER", tradeId);
        bKey.setField("TRADEVERSION", 1L);

        BinaryObjectBuilder bValue = ignite.binary().builder("BATCH_TRADE_FULL_VALUE");
        long bookIdx = tradeId % BOOKS;
        bValue.setField("BOOK", "RBCEUR" + bookIdx, String.class);
        bValue.setField("ISLATEST", true, Boolean.class);
        bValue.setField("VALUE1", "tradeValue" + tradeId, String.class);

        stream.addData(bKey.build(), bValue.build());
    }

    private static void putBatchTradeLookupRecord(IgniteDataStreamer<BinaryObject, BinaryObject> stream, Ignite ignite,
        long tradeId, long batchKey) {
        BinaryObjectBuilder bKey = ignite.binary().builder("BATCH_TRADE_KEY");
        bKey.setField("BATCHKEY", batchKey);
        bKey.setField("TRADEIDENTIFIER", tradeId);
        bKey.setField("TRADEVERSION", 1L);

        BinaryObjectBuilder bValue = ignite.binary().builder("BATCH_TRADE_VALUE");
        bValue.setField("DUMMY_VALUE", 0);

        stream.addData(bKey.build(), bValue.build());
    }

    private static void putRiskRecord(IgniteDataStreamer<BinaryObject, BinaryObject> stream, Ignite ignite,
        long riskId, long tradeId, long batchKey) {
        BinaryObjectBuilder bKey = ignite.binary().builder("RISK_KEY");
        bKey.setField("RISK_ID", riskId);

        BinaryObjectBuilder bValue = ignite.binary().builder("RISK_VALUE");
        bValue.setField("TRADEIDENTIFIER", tradeId);
        bValue.setField("TRADEVERSION", 1L);
        bValue.setField("BATCHKEY", batchKey);
        bValue.setField("VALUE2", ThreadLocalRandom.current().nextDouble(), Double.class);

        stream.addData(bKey.build(), bValue.build());
    }

    private static void putTradeRecord(IgniteDataStreamer<BinaryObject, BinaryObject> stream, Ignite ignite, long tradeId) {
        BinaryObjectBuilder bKey = ignite.binary().builder("TRADE_KEY");
        bKey.setField("TRADEIDENTIFIER", tradeId);
        bKey.setField("TRADEVERSION", 1L);

        BinaryObjectBuilder bValue = ignite.binary().builder("TRADE_VALUE");
        long bookIdx = tradeId % BOOKS;
        bValue.setField("BOOK", "RBCEUR" + bookIdx, String.class);
        bValue.setField("VALUE1", "tradeValue" + tradeId, String.class);

        stream.addData(bKey.build(), bValue.build());
    }

    private static void putBatchRecord(IgniteDataStreamer<BinaryObject, BinaryObject> stream, Ignite ignite, long batchKey) {
        BinaryObjectBuilder bKey = ignite.binary().builder("BATCH_KEY");
        bKey.setField("BATCHKEY", batchKey);

        BinaryObjectBuilder bValue = ignite.binary().builder("BATCH_VALUE");
        bValue.setField("ISLATEST", true, Boolean.class);

        stream.addData(bKey.build(), bValue.build());
    }
}
