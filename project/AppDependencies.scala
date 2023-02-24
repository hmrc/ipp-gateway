import sbt._

object AppDependencies {

  private val bootstrapVersion = "7.13.0"
  

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVersion
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion            % "test, it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"                    % "test, it"

  )
}
