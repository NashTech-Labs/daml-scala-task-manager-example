

object Versions {

  private val daSdkVersionKey = "da.sdk.version"

  val daSdkVersion: String = sys.props.get(daSdkVersionKey).getOrElse(sdkVersionFromFile())
  println(s"$daSdkVersionKey = ${daSdkVersion: String}")

  private def sdkVersionFromFile(): String =
    "10" + sbt.IO.read(new sbt.File("./SDK_VERSION").getAbsoluteFile).trim
}
