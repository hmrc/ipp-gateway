/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.ippgateway

import akka.stream.Materializer
import com.codahale.metrics.SharedMetricRegistries
import org.scalatest.matchers.must.Matchers.defined
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{HeaderNames, MimeTypes, Status}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.mvc.{AnyContent, Headers}
import play.api.mvc.Results.{InternalServerError, Ok}
import play.api.routing.sird.{POST => SPOST, _}
import play.api.test.Helpers._
import play.api.test._
import play.core.server.{Server, ServerConfig}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class DownstreamConnectorSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {
  val testPort = 11222
  val testToken = "auth-token"

  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()

    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.bank-account-insights.port" -> testPort)
      .build()
  }

  private val connector = app.injector.instanceOf[DownstreamConnector]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  private val defaultDuration = 60 seconds

  "Return true if the account is on the reject list" in {
    val response =
      """{
        |  "sortCode": "123456",
        |  "accountNumber": "12345678",
        |  "ippComponents": [
        |    {
        |      "identifier": "sa_utr",
        |      "count": 1,
        |      "componentValues": [
        |        {
        |          "value": "AB1324578724",
        |          "numOfOccurrences": 3,
        |          "lastSeen": "2023-01-10T12:35:00"
        |        }
        |      ]
        |    },
        |    {
        |      "identifier": "vrn",
        |      "count": 2,
        |      "componentValues": [
        |        {
        |          "value": "2436679346",
        |          "numOfOccurrences": 6,
        |          "lastSeen": "2023-01-05T01:35:00"
        |        },
        |        {
        |          "value": "8787948575",
        |          "numOfOccurrences": 2,
        |          "lastSeen": "2022-10-05T12:35:00"
        |        }
        |      ]
        |    }
        |  ]
        |}""".stripMargin

    Server.withRouterFromComponents(ServerConfig(port = Some(testPort))) { components =>
      import components.{defaultActionBuilder => Action}
      {
        case r @ SPOST(p"/check/insights") =>
          Action(req => {
            val reqJsonO = req.body.asJson
            reqJsonO shouldBe defined
            val reqJson = reqJsonO.get
            println(s""">>> reqJson: ${reqJson}""")
            (reqJson \ "sortCode").as[String] shouldBe "123456"
            (reqJson \ "accountNumber").as[String] shouldBe "12345678"
            (reqJson \ "fullInsightsToken").as[String] shouldBe "some-token"
            Ok(response).withHeaders("Content-Type" -> "application/json")
          })
      }
    } { _ =>
      val fakeRequest: FakeRequest[AnyContent] = FakeRequest(
        method = "POST",
        uri = s"http://localhost:$testPort/check/insights",
        headers = Headers(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON),
        body = AnyContent(Json.parse("""{"sortCode": "123456",  "accountNumber": "12345678"}""")))
      val result = connector.forward(fakeRequest, s"http://localhost:$testPort/check/insights", "1234", "some-token")
      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.parse(
        """{
          |  "sortCode": "123456",
          |  "accountNumber": "12345678",
          |  "ippComponents": [
          |    {
          |      "identifier": "sa_utr",
          |      "count": 1,
          |      "componentValues": [
          |        {
          |          "value": "AB1324578724",
          |          "numOfOccurrences": 3,
          |          "lastSeen": "2023-01-10T12:35:00"
          |        }
          |      ]
          |    },
          |    {
          |      "identifier": "vrn",
          |      "count": 2,
          |      "componentValues": [
          |        {
          |          "value": "2436679346",
          |          "numOfOccurrences": 6,
          |          "lastSeen": "2023-01-05T01:35:00"
          |        },
          |        {
          |          "value": "8787948575",
          |          "numOfOccurrences": 2,
          |          "lastSeen": "2022-10-05T12:35:00"
          |        }
          |      ]
          |    }
          |  ]
          |}""".stripMargin)
    }
  }
}
