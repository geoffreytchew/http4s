/*
 * Copyright 2013 http4s.org
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

import cats.effect.Sync
import cats.syntax.all._
import com.comcast.ip4s.Hostname

private[http4s] trait RequestPlatform[F[_]] { self: Request[F] =>

  def remoteHost(implicit F: Sync[F]): F[Option[Hostname]] = {
    val inetAddress = remote.map(_.host.toInetAddress)
    F.delay(inetAddress.map(_.getHostName)).map(_.flatMap(Hostname.fromString))
  }

}
