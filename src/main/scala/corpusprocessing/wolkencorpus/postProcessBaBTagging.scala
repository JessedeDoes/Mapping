package corpusprocessing.wolkencorpus

import corpusprocessing.brievenalsbuit.{LemPos, TagStuff}
import utils.{PostProcessXML, ProcessFolder}

import java.io.File
import scala.xml.{Elem, Null, UnprefixedAttribute, XML}

object postProcessBaBTagging {

  val inDir = "/mnt/Projecten/Corpora/Historische_Corpora/DBNL/Tagged/"
  val outDir = "/mnt/Projecten/Corpora/Historische_Corpora/DBNL/PostProcessed/"

  def fixW(e: Elem) = {
    val pos = (e \ "@type").text
    val lem = (e \ "@lemma").text
    val word = e.text.trim
    val lp: LemPos = TagStuff.parseLemPos(lem, pos).toUD.toStrings

    val lemma: String = if (lp.lemma.nonEmpty) lp.lemma else word
    val newAtts =
      e.attributes.filter(a => !Set("type", "lemma").contains(a.key))
        .append(new UnprefixedAttribute("pos", lp.pos, Null))
        .append(new UnprefixedAttribute("lemma", lemma, Null))

    e.copy(attributes = newAtts)
  }

  def fixDocje(d: Elem) = {
    val d1 = PostProcessXML.updateElement(d, _.label == "w", fixW)
    d1
  }

  def main(args: Array[String]): Unit = {
    ProcessFolder.processFolder(new File(inDir), new File(outDir), { case (i, o) =>
      if (i.endsWith(".xml")) {
        Console.err.println(s"Processing $i")
        val inDoc = XML.load(i)
        val outDoc = fixDocje(inDoc)
        XML.save(o, outDoc, "UTF-8")
      }
    })
    //val x = fixDocje(bartje)
  }
}