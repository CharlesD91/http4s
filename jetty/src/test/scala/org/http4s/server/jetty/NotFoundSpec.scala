package org.http4s
package server
package jetty

import java.net.{HttpURLConnection, URL}

import cats.effect.IO
import org.http4s.dsl.io._
import org.specs2.concurrent.ExecutionEnv

import scala.io.Source
import scala.util.Try

class NotFoundSpec(implicit ee: ExecutionEnv) extends Http4sSpec {

  private val notFoundResponse: Request[IO] => Response[IO] =
    req => Response[IO](Status.NotFound).withEntity(s"Not Found ${req.uri.path}")

  private val serverR =
    JettyBuilder[IO]
      .bindAny()
      .withNotFoundResponse(notFoundResponse)
      .mountService(
        HttpRoutes.of {
          case GET -> Root / "hello" => Ok("hello")
        },
        "/"
      )
      .resource

  withResource(serverR) { server =>
    def get(path: String): IO[(Status, String)] = {
      val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
      for {
        conn <- IO(url.openConnection().asInstanceOf[HttpURLConnection])
        _ <- IO(conn.setRequestMethod("GET"))
        isTry <- IO(Try(conn.getInputStream))
        // if getInputStream throws, then switch to getErrorStream
        is <- IO(isTry.fold(_ => conn.getErrorStream, is => is))
        resp <- IO(Source.fromInputStream(is).mkString)
        status <- IO.fromEither(Status.fromInt(conn.getResponseCode))
      } yield (status, resp)
    }

    "Server" should {
      "return hello on hello endpoint" in {
        get("/hello") must returnValue((Status.Ok, "hello"))
      }

      "return custom NotFound on non-implemented endpoint /foo" in {
        get("/foo") must returnValue((Status.NotFound, "Not Found /foo"))
      }

      "return custom NotFound on non-implemented endpoint /bar" in {
        get("/bar") must returnValue((Status.NotFound, "Not Found /bar"))
      }
    }
  }
}
