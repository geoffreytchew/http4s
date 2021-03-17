/*
 * Copyright 2018 http4s.org
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

package org.http4s.metrics.dropwizard

import cats.effect.{Clock, IO, Sync}
import com.codahale.metrics.MetricRegistry
import fs2.Stream
import java.io.IOException
import java.util.concurrent.{TimeUnit, TimeoutException}
import org.http4s.{Request, Response}
import org.http4s.dsl.io._
import scala.concurrent.duration.TimeUnit

object util {
  def stub: PartialFunction[Request[IO], IO[Response[IO]]] = {
    case (Get | Post | Put | Delete) -> Root / "ok" =>
      Ok("200 OK")
    case Get -> Root / "bad-request" =>
      BadRequest("400 Bad Request")
    case Get -> Root / "internal-server-error" =>
      InternalServerError("500 Internal Server Error")
    case Get -> Root / "error" =>
      IO.raiseError[Response[IO]](new IOException("error"))
    case Get -> Root / "timeout" =>
      IO.raiseError[Response[IO]](new TimeoutException("request timed out"))
    case Get -> Root / "abnormal-termination" =>
      Ok("200 OK").map(
        _.withBodyStream(Stream.raiseError[IO](new RuntimeException("Abnormal termination"))))
    case _ =>
      NotFound("404 Not Found")
  }

  def count(registry: MetricRegistry, counter: Counter): Long =
    registry.getCounters.get(counter.value).getCount

  def count(registry: MetricRegistry, timer: Timer): Long =
    registry.getTimers.get(timer.value).getCount

  def valuesOf(registry: MetricRegistry, timer: Timer): Option[Array[Long]] =
    Option(registry.getTimers().get(timer.value)).map(_.getSnapshot.getValues)

  case class Counter(value: String)
  case class Timer(value: String)

  object FakeClock {
    def apply[F[_]: Sync] =
      new Clock[F] {
        private var count = 0L

        override def realTime(unit: TimeUnit): F[Long] =
          Sync[F].delay {
            count += 50
            unit.convert(count, TimeUnit.MILLISECONDS)
          }

        override def monotonic(unit: TimeUnit): F[Long] =
          Sync[F].delay {
            count += 50
            unit.convert(count, TimeUnit.MILLISECONDS)
          }
      }
  }
}
