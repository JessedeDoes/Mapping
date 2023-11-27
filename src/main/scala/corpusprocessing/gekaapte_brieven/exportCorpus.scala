package corpusprocessing.gekaapte_brieven
import java.io.PrintWriter
import scala.xml._
import Settings._
import database.DatabaseUtilities._
import scala.util.{Success, Try}
object exportCorpus {

  val n_to_export = Integer.MAX_VALUE

  def main(args: Array[String])  = {
    lazy val fieldInfo = briefdb.iterator(briefdb.allRecords("public.monster_field_info")).toList
    lazy val exportFields: Set[String] = fieldInfo.filter(x => x("exported").toLowerCase.contains("t")).map(x => x("column_name")).toSet ++ Set("xml", "brief_id")
    println(exportFields)

    briefdb.runStatement(makeArticleTable)

    def exportFilter(m: Map[String,String])  = true // m("brief_id").contains("2056")

    val articleGroups: List[List[Article]] =
      briefdb.iterator(briefdb.allRecords(articleTable))
        .filter(exportFilter)
        .map(x => x.filter(y => exportFields.contains(y._1)))
        .map(x => Article(x))
        .take(n_to_export)
        .toList
        .groupBy(_.id)
        .values
        .toList

    val articles = articleGroups.map(a => Article.groupArticles(a))
    val groupMetaMap: Map[String, Metadata] = articles.map(_.metadata).groupBy(x => x("groepID_INT")).mapValues(Metadata.groupMetadata)
    val articlesWithGroupMetadata = articles.filter(a => a.metadata.contains("groepID_INT")).map(a => a.copy(groupMetadata = groupMetaMap.get(a -> "groepID_INT")))

    articlesWithGroupMetadata.foreach(x => {
        XML.save(exportDataTo + x.fields("brief_id") + ".xml", x.prettyXML)
        println(x.fields("brief_id"))
      })
  }
}
