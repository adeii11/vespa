// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.metric.Metric;
import ai.vespa.metricsproxy.metric.Metrics;
import org.junit.Test;

import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static org.junit.Assert.assertEquals;

/**
 */
public class MetricsFetcherTest {

    private static final int port = 9;  //port number is not used in this test
    private static final double EPSILON = 0.00000000001;

    private class MetricsConsumer implements MetricsParser.Consumer {
        Metrics metrics = new Metrics();
        @Override
        public void consume(Metric metric) {
            metrics.add(metric);
        }
    }
    Metrics fetch(String data) {
        RemoteMetricsFetcher fetcher = new RemoteMetricsFetcher(new DummyService(0, "dummy/id/0"), port);
        MetricsConsumer consumer = new MetricsConsumer();
        fetcher.createMetrics(data, consumer, 0);
        return consumer.metrics;
    }

    @Test
    public void testStateFormatMetricsParse() {
        String jsonData = TestUtil.getFileContents("metrics-state.json");
        Metrics metrics = fetch(jsonData);
        assertEquals(10, metrics.size());
        assertEquals(28, getMetric("query_hits.count", metrics).getValue());
        assertEquals(0.4667, getMetric("queries.rate", metrics).getValue());
        assertEquals(1334134700L, metrics.getTimeStamp());
    }

    @Test
    public void testEmptyJson() {
        String  jsonData = "{}";
        Metrics metrics = fetch(jsonData);
        assertEquals(0, metrics.size());
    }

    @Test
    public void testErrors() {
        String jsonData;
        Metrics metrics;

        jsonData = "";
        metrics = fetch(jsonData);
        assertEquals(0, metrics.size());

        jsonData = "{\n" +
                "\"status\" : {\n" +
                "  \"code\" : \"up\",\n" +
                "  \"message\" : \"Everything ok here\"\n" +
                "}\n" +
                "}";
        metrics = fetch(jsonData);
        assertEquals(0, metrics.size());

        jsonData = "{\n" +
                "\"status\" : {\n" +
                "  \"code\" : \"up\",\n" +
                "  \"message\" : \"Everything ok here\"\n" +
                "},\n" +
                "\"metrics\" : {\n" +
                "  \"snapshot\" : {\n" +
                "    \"from\" : 1334134640.089,\n" +
                "    \"to\" : 1334134700.088\n" +
                "  },\n" +
                "  \"values\" : [\n" +
                "   {\n" +
                "     \"name\" : \"queries\",\n" +
                "     \"description\" : \"Number of queries executed during snapshot interval\",\n" +
                "     \"values\" : {\n" +
                "       \"count\" : null,\n" +
                "       \"rate\" : 0.4667\n" +
                "     },\n" +
                "     \"dimensions\" : {\n" +
                "      \"searcherid\" : \"x\"\n" +
                "     }\n" +
                "   }\n" + "" +
                " ]\n" +
                "}\n" +
                "}";

        metrics = fetch(jsonData);
        assertEquals(0, metrics.size());
    }

    public Metric getMetric(String metric, Metrics metrics) {
        for (Metric m: metrics.list()) {
            if (m.getName().equals(toMetricId(metric)))
                return m;
        }
        return null;
    }

}
