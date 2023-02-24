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

package uk.gov.hmrc.ippgateway.controllers

import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.StandardApplication
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}
import uk.gov.hmrc.ippgateway.config.AppConfig
import uk.gov.hmrc.ippgateway.{DownstreamConnector, ToggledAuthorisedFunctions}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class InsightsController @Inject()(cc: ControllerComponents, config: AppConfig, connector: DownstreamConnector, val authConnector: AuthConnector)(implicit ec: ExecutionContext)
  extends BackendController(cc) with ToggledAuthorisedFunctions {

  private val logger = Logger(this.getClass.getSimpleName)

  def any(): Action[AnyContent] = Action.async { implicit request =>
    toggledAuthorised(config.rejectInternalTraffic, AuthProviders(StandardApplication)) {
      val path = request.target.uri.toString.replace("bank-account-gateway", "bank-account-insights")
      val url = s"${config.insightsBaseUrl}$path"

      connector.forward(request, url, config.internalAuthToken, config.fullIppToken)
    }
  }

  def checkConnectivity(): Unit = {
    val url = s"${config.insightsBaseUrl}/check/insights"
    connector.checkConnectivity(url, config.internalAuthToken).map {
      result =>
        if (result) {
          logger.info("Connectivity to bank-account-insights established")
        } else {
          logger.error("ERROR: Could not connect to bank-account-insights")
        }
    }
  }

  checkConnectivity()
}
