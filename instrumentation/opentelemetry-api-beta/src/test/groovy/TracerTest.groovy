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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes
import unshaded.io.grpc.Context
import unshaded.io.opentelemetry.OpenTelemetry
import unshaded.io.opentelemetry.context.Scope
import unshaded.io.opentelemetry.trace.DefaultSpan
import unshaded.io.opentelemetry.trace.Span
import unshaded.io.opentelemetry.trace.Status

import static unshaded.io.opentelemetry.context.ContextUtils.withScopedContext
import static unshaded.io.opentelemetry.trace.Span.Kind.PRODUCER
import static unshaded.io.opentelemetry.trace.TracingContextUtils.currentContextWith
import static unshaded.io.opentelemetry.trace.TracingContextUtils.getCurrentSpan
import static unshaded.io.opentelemetry.trace.TracingContextUtils.getSpan
import static unshaded.io.opentelemetry.trace.TracingContextUtils.withSpan

class TracerTest extends AgentTestRunner {

  def "capture span, kind, attributes, and status"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.setAttribute("string", "1")
    testSpan.setAttribute("long", 2)
    testSpan.setAttribute("double", 3.0)
    testSpan.setAttribute("boolean", true)
    testSpan.setStatus(Status.UNKNOWN)
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test"
          spanKind io.opentelemetry.trace.Span.Kind.PRODUCER
          parent()
          status io.opentelemetry.trace.Status.UNKNOWN
          attributes {
            "string" "1"
            "long" 2
            "double" 3.0
            "boolean" true
          }
        }
      }
    }
  }

  def "capture span with implicit parent using Tracer.withSpan()"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    Scope parentScope = tracer.withSpan(parentSpan)

    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.end()

    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          attributes {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with implicit parent using TracingContextUtils.currentContextWith()"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    Scope parentScope = currentContextWith(parentSpan)

    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.end()

    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          attributes {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with implicit parent using TracingContextUtils.withSpan and ContextUtils.withScopedContext()"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    Span parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentContext = withSpan(parentSpan, Context.current())
    Scope parentScope = withScopedContext(parentContext)

    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.end()

    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          attributes {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with explicit parent"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def testSpan = tracer.spanBuilder("test").setParent(parentSpan).startSpan()
    testSpan.end()
    parentSpan.end()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          attributes {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with explicit parent from context"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def context = withSpan(parentSpan, Context.current())
    def testSpan = tracer.spanBuilder("test").setParent(context).startSpan()
    testSpan.end()
    parentSpan.end()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          attributes {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture span with explicit no parent"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def parentScope = currentContextWith(parentSpan)
    def testSpan = tracer.spanBuilder("test").setNoParent().startSpan()
    testSpan.end()
    parentSpan.end()
    parentScope.close()

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "parent"
          parent()
          attributes {
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "test"
          parent()
          attributes {
          }
        }
      }
    }
  }

  def "capture span with remote parent"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def parentSpan = tracer.spanBuilder("parent").startSpan()
    def testSpan = tracer.spanBuilder("test").setParent(parentSpan.getContext()).startSpan()
    testSpan.end()
    parentSpan.end()

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
          attributes {
          }
        }
        span(1) {
          operationName "test"
          childOf span(0)
          attributes {
          }
        }
      }
    }
  }

  def "capture name update"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.updateName("test2")
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test2"
          parent()
          attributes {
          }
        }
      }
    }
  }

  def "capture exception()"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    testSpan.recordException(new IllegalStateException())
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test"
          event(0) {
            eventName("exception")
            attributes {
              "${SemanticAttributes.EXCEPTION_TYPE.key()}" "java.lang.IllegalStateException"
              "${SemanticAttributes.EXCEPTION_STACKTRACE.key()}" String
            }
          }
          attributes {
          }
        }
      }
    }
  }

  def "capture name update using TracingContextUtils.getCurrentSpan()"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def testScope = tracer.withSpan(testSpan)
    getCurrentSpan().updateName("test2")
    testScope.close()
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test2"
          parent()
          attributes {
          }
        }
      }
    }
  }

  def "capture name update using TracingContextUtils.getSpan(Context.current())"() {
    when:
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def testSpan = tracer.spanBuilder("test").startSpan()
    def testScope = tracer.withSpan(testSpan)
    getSpan(Context.current()).updateName("test2")
    testScope.close()
    testSpan.end()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test2"
          parent()
          attributes {
          }
        }
      }
    }
  }

  def "add DefaultSpan to context"() {
    when:
    // Lazy way to get a span context
    def tracer = OpenTelemetry.getTracerProvider().get("test")
    def testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan()
    testSpan.end()

    def span = DefaultSpan.create(testSpan.getContext())
    def context = withSpan(span, Context.current())

    then:
    getSpan(context).getContext().getSpanId() == span.getContext().getSpanId()
  }
}
