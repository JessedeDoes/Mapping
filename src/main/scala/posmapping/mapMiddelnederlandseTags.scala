package posmapping
import scala.xml._
import java.io.File
import scala.util.matching.Regex._
import scala.xml._

object mapMiddelnederlandseTags {
  val rearrangeCorresp = false

  def Ѧ(n:String, v: String) = new UnprefixedAttribute(n,v,Null)

  def updateElement(e: Elem, condition: Elem=>Boolean, f: Elem => Elem):Elem =
  {
    if (condition(e))
      f(e)
    else
      e.copy(child = e.child.map({
        {
          case e1: Elem => updateElement(e1,condition,f)
          case n:Node => n
        }
      }))
  }

  val tagMapping  = scala.io.Source.fromFile("data/getalletjes2cgn.txt").getLines().toStream
    .map(s => s.split("\\t")).map(x => x(0) -> x(1)).toMap

  def mapTag(s: String):String =
    {
      val s0 = s.replaceAll("\\{.*", "").replaceAll("ongeanalyseerd", "999").replaceAll("[A-Za-z]","")

      val s1 =  "0" * Math.max(0, 3 - s0.length) + s0

      tagMapping.getOrElse(s1, tagMapping("999")) // s"MISSING_MAPPING($s/($s1))")
    }

  val morfcodeAttribuut = "@type"
  val msdAttribuut = "msd"
  val posAttribuut = "pos"
  val functionAttribuut = "function"
  val maartenVersie = false

  val wegVoorMaarten = Set(posAttribuut, functionAttribuut, msdAttribuut)
  val wegVoorNederlab = Set(msdAttribuut, functionAttribuut)

  val weg = if (maartenVersie) wegVoorMaarten else wegVoorNederlab

  def getId(e: Node) = e.attributes.filter(_.key == "id").value.text

  val stermat = "\\{([#*])([0-9]+)\\}".r

  case class SterretjeMatje(typ: String, id: String, lemma: String)
  {
    def attribute = new UnprefixedAttribute("corresp", "#" + id, Null)
  }

  def sterretjeMatje(w: Node) =
  {
    val code = (w \ morfcodeAttribuut).text
    val lemma = (w \ "@lemma").text
    stermat.findFirstMatchIn(code).map(m => SterretjeMatje(m.group(1), m.group(2), lemma))
  }

  def updateTag(e: Elem):Elem =
  {
    val morfcodes = (e \ morfcodeAttribuut).text.split("\\+").toList

    val newPoSAttribuut = {val f = (e \ "@function").text; if (f.isEmpty) None else Some(Ѧ("pos", f))}

    val n = (e \ "@n").text

    val lemmata = (e \ "@lemma").text.split("\\+").toList

    val newId = new PrefixedAttribute("xml", "id", "w." + n, Null)

    val cgnTags:List[String] = morfcodes.map(mapTag)

    val lemmataPatched = if (cgnTags.size <= lemmata.size) lemmata else {
      val d = cgnTags.size - lemmata.size
      val extra = (0 until d).map(x => "ZZZ")
      lemmata ++ extra
    }

    val cgnTag = cgnTags.mkString("+")

    val newMSDAttribute = new UnprefixedAttribute(msdAttribuut, cgnTag, Null)

    val newatts0 = {
      val a = e.attributes.filter(a => !weg.contains(a.key)).append(newMSDAttribute)
      if (getId(e).nonEmpty || n.isEmpty) a else a.append(newId)
    }

    val stm = sterretjeMatje(e)
    val newAtts = if (newPoSAttribuut.isEmpty || maartenVersie) newatts0 else newatts0.append(newPoSAttribuut.get)
    val afterStm = stm.map(x => newAtts.append(x.attribute)).getOrElse(newAtts)

    val featureStructures = cgnTags.map(s => CGNMiddleDutch.CGNMiddleDutchTagset.asTEIFeatureStructure(s)).zipWithIndex
      .map({ case (fs,i) => fs.copy(child=fs.child ++ <f name="lemma"><string>{lemmataPatched(i)}</string></f>, attributes=fs.attributes.append(Ѧ("n", i.toString) ))} )
    e.copy(attributes = afterStm, child = e.child ++ featureStructures)
  }

  def show(w: Node) = s"(${w.text},${(w \ "@lemma").text},${(w \ "@msd").text}, ${sterretjeMatje(w)})"

  def queryLemma(w: Node)  = s"[lemma='${w \ "@lemma"}']"

  def replaceAtt(m: MetaData, name: String, value: String) = m.filter(a => a.key != name).append(Ѧ(name, value))

  def fixEm(d: Elem):Elem =
    {
      val f1 = updateElement(d, _.label=="w", updateTag)


      if (rearrangeCorresp) {
        val stermatten = (f1 \\ "w").filter(x => (x \ "@corresp").nonEmpty).groupBy(e => (e \ "@corresp").text)
        stermatten.values.foreach(l => Console.err.println(l.sortBy(e => (e \ "@n").text.toInt).map(show(_))))

        val sterMatMap = stermatten.mapValues(l => l.map(getId))

        def newCorresp(w: Elem): Elem = {
          if ((w \ "@corresp").nonEmpty) {
            val cor = (w \ "@corresp").text
            val id = getId(w)
            val setje = sterMatMap(cor).toSet.diff(Set(id))
            val newCor = setje.map(x => s"#$x").mkString(" ")
            val newAtts = replaceAtt(w.attributes, "corresp", newCor)
            w.copy(attributes = newAtts)
          } else w
        }

        updateElement(f1, _.label == "w", newCorresp)
      } else f1
    }


  def fixFile(in: String, out:String) = XML.save(out, fixEm(XML.load(in)),  enc="UTF-8")

  def main(args: Array[String]) = utils.ProcessFolder.processFolder(new File(args(0)), new File(args(1)), fixFile)
}



