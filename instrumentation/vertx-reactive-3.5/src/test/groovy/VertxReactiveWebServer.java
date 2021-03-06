/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLConnection;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxReactiveWebServer extends AbstractVerticle {

  private static final Logger log = LoggerFactory.getLogger(VertxReactiveWebServer.class);

  private static final Tracer tracer = OpenTelemetry.getTracer("test");

  private static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  private static JDBCClient client;

  public static Vertx start(final int port)
      throws ExecutionException, InterruptedException, TimeoutException {
    /* This is highly against Vertx ideas, but our tests are synchronous
    so we have to make sure server is up and running */
    CompletableFuture<Void> future = new CompletableFuture<>();

    Vertx server = Vertx.vertx(new VertxOptions());

    client =
        JDBCClient.createShared(
            server,
            new JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver"));

    log.info("Starting on port {}", port);
    server.deployVerticle(
        VertxReactiveWebServer.class.getName(),
        new DeploymentOptions().setConfig(new JsonObject().put(CONFIG_HTTP_SERVER_PORT, port)),
        res -> {
          if (!res.succeeded()) {
            RuntimeException exception =
                new RuntimeException("Cannot deploy server Verticle", res.cause());
            future.completeExceptionally(exception);
          }
          future.complete(null);
        });
    // block until vertx server is up
    future.get(30, TimeUnit.SECONDS);

    return server;
  }

  @Override
  public void start(final io.vertx.core.Future<Void> startFuture) {
    setUpInitialData(
        ready -> {
          Router router = Router.router(vertx);
          int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
          log.info("Listening on port {}", port);
          router
              .route(SUCCESS.getPath())
              .handler(
                  ctx -> ctx.response().setStatusCode(SUCCESS.getStatus()).end(SUCCESS.getBody()));

          router.route("/listProducts").handler(this::handleListProducts);

          vertx
              .createHttpServer()
              .requestHandler(router::accept)
              .listen(port, h -> startFuture.complete());
        });
  }

  private void handleListProducts(final RoutingContext routingContext) {
    Span span = tracer.spanBuilder("handleListProducts").startSpan();
    try (Scope ignored = tracer.withSpan(span)) {
      HttpServerResponse response = routingContext.response();
      Single<JsonArray> jsonArraySingle = listProducts();

      jsonArraySingle.subscribe(
          arr -> response.putHeader("content-type", "application/json").end(arr.encode()));
    } finally {
      span.end();
    }
  }

  private Single<JsonArray> listProducts() {
    Span span = tracer.spanBuilder("listProducts").startSpan();
    try (Scope ignored = tracer.withSpan(span)) {
      return client
          .rxQuery("SELECT id, name, price, weight FROM products")
          .flatMap(
              result -> {
                Thread.dumpStack();
                JsonArray arr = new JsonArray();
                result.getRows().forEach(arr::add);
                return Single.just(arr);
              });
    } finally {
      span.end();
    }
  }

  private void setUpInitialData(final Handler<Void> done) {
    client.getConnection(
        res -> {
          if (res.failed()) {
            throw new RuntimeException(res.cause());
          }

          SQLConnection conn = res.result();

          conn.execute(
              "CREATE TABLE IF NOT EXISTS products(id INT IDENTITY, name VARCHAR(255), price FLOAT, weight INT)",
              ddl -> {
                if (ddl.failed()) {
                  throw new RuntimeException(ddl.cause());
                }

                conn.execute(
                    "INSERT INTO products (name, price, weight) VALUES ('Egg Whisk', 3.99, 150), ('Tea Cosy', 5.99, 100), ('Spatula', 1.00, 80)",
                    fixtures -> {
                      if (fixtures.failed()) {
                        throw new RuntimeException(fixtures.cause());
                      }

                      done.handle(null);
                    });
              });
        });
  }
}
