/**
 * Cross-cutting observability: what 2doai tells the shared Grafana Cloud Loki stack (thedariusz-ops,
 * central logging) beyond its ordinary log lines.
 *
 * <p>Shipping is configuration, not code: {@code logback-spring.xml} attaches the loki4j appender
 * ({@code logback-loki.xml}) under the {@code prod} profile only. The two level helpers here exist
 * because the shared contract wants {@code level} lowercase both as a Loki label and as a JSON key,
 * and Logback renders it uppercase.
 *
 * <p>Structured fields travel as SLF4J key-value pairs and come out as top-level JSON keys:
 * {@code log.atInfo().addKeyValue("event", "heartbeat").log("heartbeat")}. Alert rules key on
 * {@code event}; a {@code user_id} is the account's UUID, never an address, a name or anything a
 * user typed.
 */
package com.thedariusz.todoai.observability;
