package eindhoven
import java.io.{File, FileWriter}
import java.text.Normalizer

import eindhoven.Eindhoven.{replaceFeature, _}

import scala.xml._

case class Word(word: String, lemma: String, pos: String)
{
  def matches(mp: Int, sp: Int, ssp: Int):Boolean =
  {

    mp match {
      case 0 => if (List(0,2,8,9).contains(sp)) pos.startsWith("NOU-C") &&
        !(ssp == 0 && ! pos.contains("sg")) && !(ssp == 1 && ! pos.contains("pl"))
      else pos.startsWith("NOU-P")
      case 1 => pos.startsWith("AA")
      case 2 => pos.startsWith("VRB") && !((ssp == 5 || ssp == 6) && ! pos.contains("past")) &&
        !(List(1,2,3,4).contains(ssp) && ! pos.contains("pres")) &&
        !(List(0,1,2,3).contains(sp) && ! (pos.contains("=inf") || pos.contains("part")))
      case 3 => pos.startsWith("PD") &&
        !(List(2,3).contains(sp) && ! pos.contains("poss")) &&
        !(List(4,5).contains(sp) && ! pos.contains("ref")) &&
        !(List(0).contains(sp) && ! pos.contains("per"))
      case 4 => (pos.startsWith("PD") || pos.startsWith("NUM"))  && !(pos.contains("poss"))
      case 5 => pos.startsWith("ADV")
      case 6 => pos.startsWith("ADP")
      case 7 => pos.startsWith("CONJ")
      case 8 => pos.startsWith("INT")
      case _ => true
    }
  }
}

object Eindhoven {

  def noAccents(s: String):String = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase.trim
  val dir = "/home/jesse/data/Eindhoven"
  val files = new java.io.File(dir).listFiles.toStream.filter(f => f.getName().endsWith(("xml")))
  val goodies = scala.io.Source.fromFile(dir + "/" + "goede-woorden-uit-molex-postgres.csv").getLines.toList.map(l => l.split(";"))
    .map(r => r.map(_.trim.replaceAll(""""""", ""))).map(r => Word(r(1), r(0), r(2))
  )

  val tagMapping = io.Source.fromFile("data/vu.taginfo.csv").getLines().map(l => l.split("\\s+")).filter(_.size >=2)
    .map(l => l(0) -> l(1).replaceAll("\\s.*","")).toMap

  def mapTag(t: String)  = tagMapping.getOrElse(t,s"UNDEF($t)")

  // ‹vu 000›‹hvh-kort N(soort,ev,neut) ›‹hvh-lang N(com,numgen=singn,case=unm,Psynuse=nom)›
  val hvh_kort = "‹hvh-kort\\s*(.*?)\\s*›".r
  val hvh_lang = "‹hvh-lang\\s*(.*?)\\s*›".r
  val vu_pos = "‹vu\\s*(.*?)\\s*›".r

  def hvhKort(e: Node):Option[String]  = (e \\ "@pos").flatMap( a =>
    hvh_kort.findFirstMatchIn(a.text).map(_.group(1))
  ).headOption.map(x => x.replaceAll(":.*",  ""))

  def hvhLang(e: Node):Option[String]  = (e \\ "@pos").flatMap( a =>
    hvh_lang.findFirstMatchIn(a.text).map(_.group(1))
  ).headOption


  def vuPos(e: Node):Option[String]  = (e \\ "@pos").flatMap( a =>
    vu_pos.findFirstMatchIn(a.text).map(_.group(1))
  ).headOption



  val goodMap:Map[String,List[Word]] = goodies.groupBy(w => noAccents(w.word))

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

  def capFirst(s: String) = s.substring(0,1).toUpperCase + s.substring(1, s.length)

  def vuPatchP(d: Elem):Elem =
  {
    val numberedChildren = d.child.zipWithIndex
    val firstWordIndex:Int = numberedChildren.find(_._1.label=="w").get._2
    val firstWord:Elem = numberedChildren(firstWordIndex)._1.asInstanceOf[Elem]
    val newFirstWord = firstWord.copy(child = Text(capFirst(firstWord.text)))
    //val newChild = .map({case (e,i) => if (i==firstWord) e.copy(child = Seq())}
    //)
    val newChildren = numberedChildren.map({
      case (c,i) => if (i==firstWordIndex) newFirstWord else c
    })
    d.copy(child=newChildren)
  }


  def vuPatch(d: Elem) = updateElement(d, x => x.label=="p" && (x \ "w").nonEmpty, vuPatchP)

  def useAutomaticTagging(d: Elem, f: File) =
  {
    val f1 = f.getParent() + "/reTagged/" + f.getName()
    val d1 = XML.load(f1)
    val mappie = (d1 \\ "w").map(w => {
      val id = getId(w)
      id.get -> w.asInstanceOf[Elem]
    }).toMap

    def doW(w: Elem) =
    {
      val id = getId(w).get
      val w1:Elem = mappie(id)
      val pos = (w \ "@pos").text
      val w1Pos = (w1 \ "@pos").text
      val newPos =
      {
        if (pos.matches("ADJ.*gewoon.*") && w1Pos.matches(".*prenom.*")) replaceFeature(pos, "gewoon", "x-prenom")
        else if (pos.matches("ADJ.*gewoon.*") && w1Pos.matches("=adv.*")) replaceFeature(pos, "gewoon", "x-vrij,pred+")
        else pos
      }
      w.copy(attributes = w.attributes.filter(_.key != "pos").append(new UnprefixedAttribute("pos", newPos, Null)))
    }

    updateElement(d, _.label == "w", doW)
  }

  def getId(n: Node):Option[String] = n.attributes.filter(a => a.prefixedKey.endsWith(":id") ||
    a.key.equals("id")).map(a => a.value.toString).headOption

  def doFile(f: File) =
  {
    val doc = XML.loadFile(f)
    (doc \\ "w").map(_.asInstanceOf[Elem])
    val d1 = updateElement(doc, _.label == "w", doWord)
    val d2 = updateElement(d1, _.label == "name", doName)
    //val d2a = updateElement(d1, _.label == "p", p=> p.copy())
    val d3 = if (Set("camb.xml", "cgtl.xml").contains(f.getName))
      vuPatch(d2) else d2
    val d4 = createElementIds(updateElement(d3, _.label == "p", e => e.copy(child = <s>{e.child}</s>)), "EC")

    val d5 = useAutomaticTagging(d4.asInstanceOf[Elem], f)
    XML.save(f.getParent + "/Patched/" + f.getName, d5, "UTF-8")
  }

  val m0 = <x y="1"/>.attributes.filter(p => p.key != "y")
  println(m0)

  def append(m: MetaData, l:List[UnprefixedAttribute]):MetaData =
  {
    val m1 = if (m==Null) m0 else m
    

    l.foldLeft(m1)( (m,u) => m.append(u))
  }

  import util.matching.Regex._

  def findVuPos(pos: String):(Int,Int,Int) =
  {
    val vuPos0:Int = "vu ([0-9]{3})".r.findFirstMatchIn(pos).map(m => m.group(1)).getOrElse("999").toInt

    val vuPos = vuPos0 % 1000
    val vuMainPos = (vuPos - vuPos % 100) / 100
    val vuSub1 = vuPos - 100 * vuMainPos
    val vuSubPos = (vuSub1 - vuSub1 % 10) / 10
    val vuSubSubPos = vuPos - 100 * vuMainPos - 10 * vuSubPos

    (vuMainPos, vuSubPos, vuSubSubPos)
  }

  def doWord(w: Elem):Elem = {
    val word = w.text

    val lemma0 = (w \\ "@lemma").text
    val lemma1 = if (lemma0 == "_") "" else lemma0

    val pos = (w \\ "@pos").text

    val (vuMainPos, vuSubPos, vuSubSubPos) = findVuPos(pos)

    val de_vu_pos = List(vuMainPos,vuSubPos,vuSubSubPos).map(_.toString).mkString

    val (lemma, supply):(String, Boolean) =  { if (lemma1 == "" && vuMainPos == 0 && vuSubSubPos == 0) (word,true); else (lemma1,false) }


    val lemmaIsWord = new UnprefixedAttribute("lemma", lemma, Null)

    //Console.err.println(s"$vuPos $vuMainPos:$vuSubPos:$vuSubSubPos")

    val cert: UnprefixedAttribute = new UnprefixedAttribute("maybenot", "true", Null)

    val candidates = goodMap.get(word.toLowerCase())

    val mappedPoS = mapTag(de_vu_pos)
    val cleanedAttributes = w.attributes.filter(a => !(a.key == "pos") && !(a.key=="lemma" && a.value.text=="_")).append(
      new UnprefixedAttribute("pos", mappedPoS, Null)
    ).append(new UnprefixedAttribute("type", de_vu_pos, Null))

    Console.err.println(cleanedAttributes)

    val extraAttributes: List[UnprefixedAttribute] =
      if (candidates.isDefined) {
        val c = candidates.get.filter(w => (lemma.isEmpty || w.lemma == lemma) && w.matches(vuMainPos, vuSubPos, vuSubSubPos))

        val molexPos = c.map(_.pos)

        val molexPosAttribute = if (molexPos.isEmpty || {val noMolexPos = true; noMolexPos}) List() else List(new UnprefixedAttribute("molex_pos", molexPos.mkString("|"), Null))

        val lemmaCandidates = c.filter(w => lemma.isEmpty).map(_.lemma).toSet

        val lemmaAttribute = new UnprefixedAttribute("lemma", lemmaCandidates.mkString("|"), Null)
        val lAdd = if (lemmaCandidates.isEmpty)
          if (supply) List(lemmaIsWord) else List()
        else List(lemmaAttribute)

        val withAccent = c.filter(w => noAccents(w.word) != w.word.toLowerCase())
        val withoutAccent = c.filter(w => noAccents(w.word) == w.word.toLowerCase())

        if (withAccent.nonEmpty) {
          val a0: UnprefixedAttribute = new UnprefixedAttribute("corr", withAccent.head.word, Null)

          Console.err.println(s"$word ($lemma) => $withAccent")

          if (withoutAccent.isEmpty) List(a0) ++ lAdd ++ molexPosAttribute
          else {
            List(a0, cert) ++ lAdd ++ molexPosAttribute
          }
        } else lAdd ++ molexPosAttribute
      } else if (supply) List(lemmaIsWord) else List.empty[UnprefixedAttribute]
    addDetailsToPos(w.copy(attributes = append(cleanedAttributes,extraAttributes)))
  }

  def addDetailsToPos(w: Elem):Elem =
  {
    val word = w.text
    val lemma = (w \  "@lemma").text
    val pos = (w \  "@pos").text

    w.copy(attributes = w.attributes.filter(_.key != "pos").append(new UnprefixedAttribute("pos", addDetailsToPos(word,lemma,pos),Null)) )
  }

  def removeFeature(tag: String, feature: String) = tag.replaceAll(s"([,(])$feature([,)])","$1$2")
    .replaceAll(",+",",").replaceAll(",\\)",")")

  def replaceFeature(tag: String, f1: String, f2: String) = tag.replaceAll(s"([,(])$f1([,)])",s"$$1$f2$$2")

  def removeFeatures(tag: String, features: Set[String]) = features.foldLeft(tag)(removeFeature)

  val gradables = """veel
    weinig
    beide
    meer
    teveel
    minder
    keiveel
    meeste
    evenveel
    zoveel
    mindere
    vele
    superveel
    keiweinig
    allerminst
    minst
    veels
    zovele
    vaak""".split("\\s+").toSet // waarom 'beide' grad??

  def addDetailsToPos(word: String, lemma: String, tag: String):String =
  {
    if (tag.matches(".*WW.*pv.*tgw.*") && tag.matches(".*[23].*") && lemma.endsWith("n"))
      {
        if (word.endsWith("t") && !lemma.endsWith("ten"))
          {
            return tag.replaceAll("\\)$", ",met-t)")
          }
      }
    if (tag.matches("VNW.*refl.*") && tag.matches(".*pr.*"))
      {
        if (lemma.contains("kaar") || word.contains("kander") || word.contains("kaar"))
          return tag.replaceAll("\\(.*?,", "(recip,")
        if (lemma.contains("zich"))
          return tag.replaceAll("\\(.*?,", "(refl,")
        else
          return tag.replaceAll("\\(.*?,", "(pr,")
      }
    if (tag.matches("VNW.*aanw.*prenom.*") && (lemma == "het" || lemma == "de"))
      {
        val t1 = removeFeatures(tag.replaceAll("VNW", "LID").replaceAll("aanw", "bep"), Set("prenom","det"))
        return t1
      }
    if (tag.matches("VNW.*onbep.*prenom.*") && lemma == "een")
      {
        val t1 = removeFeatures(tag.replaceAll("VNW", "LID"), Set("prenom","det"))
        return t1
      }
    if (tag.matches("VNW.*det.*") && gradables.contains(lemma))
      return tag.replaceAll("det", "grad")
    tag
  }

  def doName(n: Elem) =
  {
    n.copy(child = n.child.map(
      c => c match
        {
        case w: Elem if (w.label == "w") =>
          w.copy(attributes = w.attributes.filter(_.key != "pos").append(new UnprefixedAttribute("pos", "SPEC(deeleigen", Null)))
        case x:Any => x
      }
    ))
  }

  def createElementIds(n: Node, path: String):Node = {
    val id = new PrefixedAttribute("xml", "id", path, Null)

    n match {
      case e: Elem =>
        e.copy(attributes = e.attributes.append(id), child = e.child.zipWithIndex.map({ case (c, i) => createElementIds(c, s"${c.label}.$path.$i") }))
      case n: Node => n
    }
  }

  def main(args: Array[String]) =
  {
    val d = new File(dir)
    d.listFiles.foreach(println)
    files.foreach(doFile)
  }
}

object allTags
{
  case class TaggedWord(word: String, kort: Option[String], lang: Option[String], vu: Option[String])
  {
    override def toString() = s"$word,${vu.getOrElse("_")},${kort.getOrElse("_")},${lang.getOrElse("_")}"
  }

  def listAllTags() = files.flatMap(f => (XML.loadFile(f) \\ "w").map(n => TaggedWord(n.text, hvhKort(n), hvhLang(n), vuPos(n))))

  def vert() = listAllTags().foreach({ case TaggedWord(w,k,l,v) =>
    println(s"$w\t${k.getOrElse("_")}\t${l.getOrElse("_")}\t${v.getOrElse("_")}") })

  def byVuTag() = listAllTags().filter(w => w.vu.isDefined&& w.vu.get.length == 3).groupBy(_.vu).mapValues(l => scala.util.Random.shuffle(l).toSet)
    //.map({ case (w,k,l,v) =>  s"$w\t${k}\t${l.getOrElse("_")}\t${v.getOrElse("_")}" }
  // )

  def byKorteTag = listAllTags().groupBy(_.kort).mapValues(l => scala.util.Random.shuffle(l).toSet)

  def splitLangetags:Stream[TaggedWord] = listAllTags().filter(_.lang.isDefined).flatMap(w => w.lang.getOrElse("").split("\\|").toList.map(l
  => w.copy(lang = Some(l.replaceAll("\\{.*?\\}", "").replaceAll("\\[.*?\\]", "").replaceAll("MTU.[0-9]*_L[0-9]+_", "")  ))))

  // splitLangetags.foreach(println)

  def byLangeTag = splitLangetags.groupBy(_.lang).mapValues(l => scala.util.Random.shuffle(l).toSet)


  def main(args: Array[String]) =
  {
     val files = List("vu", "kort", "lang").map(s => new FileWriter("/tmp/" + s + ".taginfo.txt"))
     val (vuFile,kortFile, langFile) = (files(0), files(1), files(2))

    byVuTag.toList.sortBy(_._1).foreach(
       {
         case (v, w) if (v.isDefined && v.get.length < 4) =>
           {
             val allekortjes = w.filter(_.kort.isDefined).map(_.kort.get).toSet.mkString(",")
             vuFile.write(s"${v.get}\t${w.size}\t${w.take(5).map(_.word).mkString("  ")}\n")
           }

         case _ =>
       }
     )

    byKorteTag.toList.sortBy(_._1).foreach(
      {
        case (v, w) =>
        {
          kortFile.write(s"${v.getOrElse("_")}\t${w.size}\t${w.take(5).map(_.toString).mkString("  ")}\n")
        }

        case _ =>
      }
    )

    byLangeTag.toList.sortBy(_._1).foreach(
      {
        case (v, w) =>
        {
          langFile.write(s"${v.getOrElse("_")}\t${w.size}\t${w.take(5).map(_.toString).mkString("  ")}\n")
        }

        case _ =>
      }
    )
    files.foreach(_.close())

  }
}

// leeuwenberg et al 2016 minimally supervised approach for  synonym extraction with word embeddings