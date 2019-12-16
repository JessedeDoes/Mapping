package Wp6

import java.io.{File, PrintWriter}

import Wp6.Settings.XMLDirectory
import utils.PostProcessXML.updateElement

import scala.xml.{Elem, Node, XML}
import scala.util.matching.Regex
import scala.util.{Success, Try}
object MissivenMetadata {
  def pushOptionInside(o: Option[(Node,Int)]):(Option[Node], Int) =
    o.map(x => (Some(x._1).asInstanceOf[Option[Node]],x._2)).getOrElse( (None, 0) )

  def groupWithFirst[T](l: Seq[T], f: T => Boolean): Seq[Seq[T]] =
  {
    val numberedChild:List[(Node, Int)] = l.toList.zipWithIndex
    def lastBefore(i:Int):(Option[Node],Int) = pushOptionInside(numberedChild.filter({case (n,j) => j <= i && f(n)}).lastOption)
    val grouped = numberedChild.groupBy({case (n,i) => lastBefore(i)})
    grouped.keySet.toList.sortBy(_._2).map(grouped).map(l => l.map(_._1)) // ahem, unorded...
  }


  val pretty = new scala.xml.PrettyPrinter(300, 4)

  val maanden: List[(String, Int)] = List("jan", "feb", "maart", "apr", "mei", "jun", "jul", "aug", "sep", "o[kc]t", "nov", "dec").zipWithIndex

  def parseMonth(s: String): Option[Int] = maanden.find(x => s.toLowerCase.matches(x._1 + ".*")).map(_._2 + 1)

  def tryOrNone[T](x: () => T): Option[T]  = Try(x()) match {
    case Success(z) =>  Some(z)
    case _ => None
  }

  val tocjes: Array[(Int, File)] = new File(XMLDirectory).listFiles.filter(_.getName.matches("[0-9]+"))
    .map(f => (f.getName.toInt ->  new File(f.getCanonicalPath + "/" + "toc.xml")))

  case class TocItem(volume: Int, pageNumber: String, title: String, level: Int, parent: Option[TocItem]  = None)
  {
    lazy val page: Int = pageNumber.toInt
    private lazy val titleZonderGeheim = title.replaceAll(",\\s*geheim\\s*$", "")
    lazy val date: String = titleZonderGeheim.replaceAll(".*, *", "").trim // dit werkt niet altijd...
    lazy val year: Option[Int] = "[0-9]{4}".r.findAllIn(date).toList.headOption.map(_.toInt)
    lazy val day: Option[Int] = tryOrNone(() => date.replaceAll("\\s.*","").toInt)
    lazy val month: Option[Int] = parseMonth(date.replaceAll("[0-9]+", "").trim)

    lazy val author: String = titleZonderGeheim.replaceAll(",[^,]*$","")
    lazy val authors: Array[String] = author.split("\\s*(,|\\sen\\s)\\s*")
    def interp[T](name: String, value: Option[T]): Seq[Node] = if (value.isEmpty) Seq() else  <interpGrp inst={inst} type={name}><interp>{value.get.toString}</interp></interpGrp>

    def toXML: Elem = <bibl><level>{level}</level><volume>{volume}</volume><page>{page}</page><author>{author}</author><date>{date}</date><title>{title}</title></bibl>

    def isIndex: Boolean = title.trim.toLowerCase().startsWith("index")

    def toTEI: Elem = if (isIndex || level == 0)
      <listBibl><bibl level={level.toString} inst={inst}>
        {interp("page", Some(page))}() =>
        <interpGrp inst={inst} type="titleLevel1"><interp>{title}</interp></interpGrp>
      </bibl></listBibl>
      else
      <listBibl><bibl level={level.toString} inst={inst}>
        {interp("page", Some(page) )}
      <interpGrp inst={inst} type="titleLevel1"><interp>{title}</interp></interpGrp>
      <interpGrp inst={inst} type="dateLevel1"><interp>{date}</interp></interpGrp>
      {interp(value=Some(volume), name = "volume")}
      {interp("witnessYearLevel1_from", year )}
      {interp("witnessYearLevel1_to", year )}
      {interp("witnessMonthLevel1_to", month )}
      {interp("witnessMonthLevel1_from", month )}
      {interp("witnessDayLevel1_to", day )}
      {interp("witnessDayLevel1_from", day )}
      <interpGrp inst={inst} type="authorLevel1">{authors.map(z => <interp>{z}</interp>)}</interpGrp>
      <interpGrp inst={inst} type="pid"><interp>{pid}</interp></interpGrp>
    </bibl></listBibl>

    def uuid():String =
    {
      val source = this.toString
      val bytes = source.getBytes("UTF-8")
      java.util.UUID.nameUUIDFromBytes(bytes).toString
    }

    lazy val pid = s"INT_${uuid()}"

    def inst  = s"#$pid"
  }

  def tocItemFromBibl(b: Elem): TocItem =  { //
    val volume = (b \ "volume").text.toInt
    val pageNumber = (b \ "page").text
    val title =  (b \ "title").text
    val level = (b \ "level").text.toInt
    TocItem(volume, pageNumber, title, level)
  }


  def uuidForVolume(n: Int): String = {
    val source = "INT-generalemissiven-deeltje" + n
    val bytes = source.getBytes("UTF-8")
    java.util.UUID.nameUUIDFromBytes(bytes).toString
  }


  lazy val tocItems_unsorted: Map[Int, Array[TocItem]] = tocjes.flatMap(
    { case (n, f) => (XML.loadFile(f) \\ "item").map(
      item => TocItem(n, (item \\ "page").text, (item \\ "title").text, (item \ "@level").text.toInt)
    )}
  ).groupBy(_.volume)

  def findParents(l: Seq[TocItem]) = {
    l.zipWithIndex
  }
  lazy val tocItems: Map[Int, Array[TocItem]] =  tocItems_unsorted.mapValues(l => l.filter(_.pageNumber.matches("[0-9]+")).sortBy(x => 10000 * x.page + x.level)) // kies liever een level 1 item (als laatste)

  def findTocItem(v: Int, p: Int): TocItem =
  {
    val bestMatch = tocItems(v).filter(_.page <= p).lastOption
    bestMatch.getOrElse(TocItem(0, "0", "no match", 0))
  }

  def createHeader(v: Int, d: Elem): Elem = {
    val allTocItems = (d \\ "bibl").map(b => tocItemFromBibl(b.asInstanceOf[Elem]))

    val header = <teiHeader>
      <fileDesc>
        <titleStmt>
          <title>Tweede boek, deel I</title>
        </titleStmt>
        <publicationStmt>
          <p>
            <date></date>
            <idno type="sourceID">missiven:vol{v}</idno>
            <idno type="pid">INT_{uuidForVolume(v)}</idno>
          </p>
        </publicationStmt>
        <notesStmt>
          <note/>
        </notesStmt>
        <sourceDesc>
          <listBibl xml:id="inlMetadata">
            <bibl>
            </bibl>
          </listBibl>
          {allTocItems.map(t => t.toTEI)}
        </sourceDesc>
      </fileDesc>
    </teiHeader>

    def fixDivje(d: Node) = {
      val t = tocItemFromBibl((d \ "bibl").head.asInstanceOf[Elem])
      <div xml:id={t.pid}>
        {d.child.filter(_.label != "bibl")}
      </div>
    }
    val d1 = updateElement(d, x => x.label == "div" && (x \ "@type").text == "missive", fixDivje)

    <TEI>
      {header}
      {d1.child}
    </TEI>
  }

  def main(args: Array[String]): Unit = {
    val p = new PrintWriter("/tmp/bibls.xml")
    p.println("<bibls>")
    tocItems.toList.sortBy(_._1).foreach(
      {
        case (v, l) =>
          val volumeXML = <volume n={v.toString}>{l.map(_.toTEI)}</volume>
          p.println(pretty.format(volumeXML))
      }
    )
    p.println("</bibls>")
    p.close()
  }
}
