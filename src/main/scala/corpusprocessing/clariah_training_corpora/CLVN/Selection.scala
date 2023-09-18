package corpusprocessing.clariah_training_corpora.CLVN

import corpusprocessing.clariah_training_corpora.fixTokenization.getId
import utils.PostProcessXML

import java.io.File
import scala.collection.immutable
import scala.xml._

case class Doc(n: Node) {
  val e  = n.asInstanceOf[Elem]
  def getValues(name: String): Seq[String] = ((n \\ "docInfo").head.child.filter(x => x.label == name).map(x => {  (x  \\ "value").text}))
  lazy val pid = (n \\ "docPid").text
  lazy val province = getValues("province").mkString("|")
  lazy val witness_year_from = getValues("witness_year_from").mkString("|")
  lazy  val witness_year_to  = getValues("witness_year_to").mkString("|")
  lazy val nTokens = (n \\ "lengthInTokens").text.toInt
  override def toString  = s"$pid ($nTokens tokens): $province, $witness_year_from-$witness_year_to"

  def getContent() = {
    val url = s"https://corpora.ato.ivdnt.org/blacklab-server/CLVN/docs/$pid/contents"
    XML.load(url)
  }

  def save(baseDir: String) = {
    val fName = baseDir + "/" + pid  + ".xml";
    XML.save(fName, getContent(), enc="UTF-8")
  }
}

object Selection {
   val docsURL = "https://corpora.ato.ivdnt.org/blacklab-server/CLVN/docs/?number=3000"
   lazy val docs: Seq[Doc] = XML.load(docsURL).flatMap(x => x \\ "doc").map(Doc)
   lazy val docs16 = docs.filter(x => x.witness_year_from >= "1500" && x.witness_year_to <= "1600").filter(_.province.nonEmpty)
   lazy val provinceGroups = docs16.groupBy(_.province).mapValues(l => l.filter(d => d.nTokens >= 200 && d.nTokens <= 2000)).mapValues(l => l.sortBy(_.nTokens))
   lazy val toksPerProvince = provinceGroups.map({case (p, l) => (p, l.map(_.nTokens).sum)}).filter(_._2 >= 5000)
   lazy val survivingProvinces = toksPerProvince.keySet
   lazy val selection = provinceGroups.filter({case (x, _) => survivingProvinces.contains(x)}).mapValues(l => {
     var cumul = 0;
     l.takeWhile(d => {cumul = cumul + d.nTokens; cumul <= 2750})
   })
  def main(args: Array[String])  = {
    // docs16.foreach(x => println(x))
    println(toksPerProvince)
    println(toksPerProvince.size)
    println(25000 / toksPerProvince.size)
    val baseDir = new java.io.File("/tmp/CLVNSelectie/")
    baseDir.mkdir()
    selection.foreach({case (p,l) =>
       println(s" ### $p: ${l.map(_.nTokens).sum} ### ")
       l.foreach(d => {
         println(s"\t$d")
         d.save(baseDir.getCanonicalPath)
       })
    })
  }
}

object PatchUp {

  type B = (Int, Seq[Node])

  def breidUit(z: (B, Node)): B = {
    {
      z match  { case ((l, s), n) =>
        val (n1, l1) = renumberWords(n, l)
        (l1, s ++ Seq(n1))
      }
    }
  }

  def renumberWords(node: Node, k: Int) : (Node,Int) = {
    if (Set("w", "pc").contains(node.label)) {
      val id = getId(node)
      val n1: Node = node.asInstanceOf[Elem].copy(attributes = node.attributes.filter(x => x.key != "id").append(new PrefixedAttribute("xml", "id", Text(s"${node.label}.$k"), Null)))
      (n1,k+1)
    } else if (node.isInstanceOf[Elem]) {
      val t0: B = k -> Seq[Node]()
      val t: (Int, Seq[Node]) = node.child.toSeq.foldLeft(t0)({case (t:B,n:Node) => breidUit( (t,n))})
      node.asInstanceOf[Elem].copy(child = t._2) -> t._1
    } else {
      (node, k)
    }
  }

  def renumber(e: Elem)  = renumberWords(e, 0)._1.asInstanceOf[Elem]

  def fixW(w: Elem)  = {

    def withText(t: String)  = w.copy(child={<seg>{t.trim}</seg>})
    if (w.toString().contains("<lb/>"))
      {
        val below = w.child.map(_.toString).mkString("").replaceAll("<lb[^<>]*/>", "LB_LB")
        val wordX = XML.loadString(s"<w>${below}</w>")
        val wordz = wordX.text.split("LB_LB").map(withText).toSeq
        println(wordz)
        wordz.zipWithIndex.flatMap({case (w,i) => if (i < wordz.size - 1) Seq(w, <lb has="flats"/>) else w}).toSeq
      } else if (w.text.matches(".*[a-zA-Z0-9].*,.*[a-zA-Z0-9].*"))
    {
      val wordz = w.text.split("\\s*,\\s*").map(withText)

      val metKomma: Seq[Node] = wordz.zipWithIndex.flatMap({case (w,i) => if (i < wordz.size - 1) Seq(w, <pc>,</pc>) else w}).toSeq
      metKomma
    } else withText(w.text);
  }

  val dir = "/mnt/Projecten/Corpora/Historische_Corpora/CLVN/CLVNSelectieTagged/"

  def main(args: Array[String])  = {
    new File(dir).listFiles().filter(_.getName.endsWith(".xml")).foreach(f => {
      val d = XML.loadFile(f)
      val d1 = PostProcessXML.updateElement5(d, _.label=="w", fixW).asInstanceOf[Elem]
      XML.save(dir + "Patched/" + f.getName, renumber(d1))
    })
  }
}
