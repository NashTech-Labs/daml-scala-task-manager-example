
import sbt._

object Artifactory {

  val daResolvers: Seq[MavenRepository] = Seq(
    Resolver.bintrayRepo("digitalassetsdk", "DigitalAssetSDK"),
    Resolver.mavenLocal
  )
}
