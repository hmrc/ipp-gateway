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

import play.api.Configuration
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.mvc.Results.BadRequest
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class InvalidJsonErrorHandler @Inject()(
                                         auditConnector: AuditConnector, httpAuditEvent: HttpAuditEvent, configuration: Configuration
                                       )(implicit ec: ExecutionContext)
  extends uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler(auditConnector, httpAuditEvent, configuration) {
  private val invalidPattern = "^Invalid Json:".r
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = (request, statusCode, message) match {
    case (_, 400, m) if invalidPattern.pattern.matcher(m).find() =>
      Future.successful(BadRequest(Json.parse(s"""{"code": "MALFORMED_JSON", "desc": "${filterJsonExceptionMsg(m)}"}""")).as(MimeTypes.JSON))
    case (r, s, m) =>
      super.onClientError(r, s, m)
  }

  private def filterJsonExceptionMsg(msg: String): String = {
    msg.indexOf("at [Source") match {
      case -1 => msg
      case x => msg.substring(0, x)
    }
  }
}
