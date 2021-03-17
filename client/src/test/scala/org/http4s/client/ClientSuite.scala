/*
 * Copyright 2014 http4s.org
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

package org.http4s
package client

import cats.effect.concurrent.Deferred
import cats.effect._
import cats.syntax.all._
import java.io.IOException
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Host
import org.http4s.server.middleware.VirtualHost
import org.http4s.server.middleware.VirtualHost.exact
import org.http4s.syntax.AllSyntax

class ClientSuite extends Http4sSuite with Http4sDsl[IO] with AllSyntax {
  private val app = HttpApp[IO] { r =>
    Response[IO](Ok).withEntity(r.body).pure[IO]
  }
  val client: Client[IO] = Client.fromHttpApp(app)

  test("mock client should read body before dispose") {
    client.expect[String](Request[IO](Post).withEntity("foo")).assertEquals("foo")
  }

  test("mock client should fail to read body after dispose") {
    Request[IO](Post)
      .withEntity("foo")
      .pure[IO]
      .flatMap { req =>
        // This is bad. Don't do this.
        client.run(req).use(IO.pure).flatMap(_.as[String])
      }
      .attempt
      .map {
        case Left(e: IOException) => e.getMessage == "response was disposed"
        case _ => false
      }
  }

  test("mock client should include a Host header in requests whose URIs are absolute") {
    val hostClient = Client.fromHttpApp(HttpApp[IO] { r =>
      Ok(r.headers.get[Host].map(_.value).getOrElse("None"))
    })

    hostClient
      .expect[String](Request[IO](Get, uri"https://http4s.org/"))
      .assertEquals("http4s.org")
  }

  test("mock client should include a Host header with a port when the port is non-standard") {
    val hostClient = Client.fromHttpApp(HttpApp[IO] { r =>
      Ok(r.headers.get[Host].map(_.value).getOrElse("None"))
    })

    hostClient
      .expect[String](Request[IO](Get, uri"https://http4s.org:1983/"))
      .assertEquals("http4s.org:1983")
  }

  test("mock client should cooperate with the VirtualHost server middleware") {
    val routes = HttpRoutes.of[IO] { case r =>
      Ok(r.headers.get[Host].map(_.value).getOrElse("None"))
    }

    val hostClient = Client.fromHttpApp(VirtualHost(exact(routes, "http4s.org")).orNotFound)

    hostClient
      .expect[String](Request[IO](Get, uri"https://http4s.org/"))
      .assertEquals("http4s.org")
  }

  test("mock client should allow request to be canceled") {

    Deferred[IO, Unit]
      .flatMap { cancelSignal =>
        val routes = HttpRoutes.of[IO] { case _ =>
          cancelSignal.complete(()) >> IO.never
        }

        val cancelClient = Client.fromHttpApp(routes.orNotFound)

        Deferred[IO, ExitCase[Throwable]]
          .flatTap { exitCase =>
            cancelClient
              .expect[String](Request[IO](Get, uri"https://http4s.org/"))
              .guaranteeCase(exitCase.complete)
              .start
              .flatTap(fiber =>
                cancelSignal.get >> fiber.cancel) // don't cancel until the returned resource is in use
          }
          .flatMap(_.get)
      }
      .assertEquals(ExitCase.Canceled)
  }
}
