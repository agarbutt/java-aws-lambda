/*
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
 *
 */

package io.opentracing.contrib.aws;

import com.amazonaws.services.lambda.runtime.Context;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracing request stream handler that creates a span on every invocation of a Lambda.
 *
 * <p>Implement this interface and update your AWS Lambda Handler name to reference your class name,
 * e.g., com.mycompany.HandlerClass
 */
public interface TracingRequestStreamHandler
    extends com.amazonaws.services.lambda.runtime.RequestStreamHandler {

  AtomicBoolean isColdStart = new AtomicBoolean(true);

  /**
   * Method that handles the Lambda function request.
   *
   * <p>Override this method in your code.
   *
   * @param input The Lambda Function input stream
   * @param output The Lambda Function output stream
   * @param context The Lambda execution environment context object
   */
  void doHandleRequest(InputStream input, OutputStream output, Context context);

  default void handleRequest(InputStream input, OutputStream output, Context context) {
    final Tracer tracer = GlobalTracer.get();
    final SpanContext spanContext = extractContext(tracer, input);

    try (Scope scope = tracer.buildSpan("handleRequest").asChildOf(spanContext).startActive(true)) {
      SpanUtil.setTags(scope, context, input, isColdStart);
      try {
        doHandleRequest(input, output, context);
      } catch (Throwable throwable) {
        scope.span().log(SpanUtil.createErrorAttributes(throwable));
        throw throwable;
      }
    }
  }

  /**
   * Override to extract context from Input.
   *
   * <p>Implementations should call {@link Tracer#extract(Format, Object)} and return the extracted
   * SpanContext.
   *
   * @param tracer OpenTracing tracer
   * @param input Input to Lambda function
   * @return SpanContext Extracted from input, null if there was no context or there was an issue
   *     extracting this context
   */
  default SpanContext extractContext(Tracer tracer, InputStream input) {
    return null;
  }
}