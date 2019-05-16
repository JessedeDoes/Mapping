package posmapping
import scala.xml._
import java.io.File

import scala.util.matching.Regex._
import scala.xml._
import database.DatabaseUtilities.Select
import database.{Configuration, Database}
import posmapping.VMNWdb.QuotationReference

/*
+----------------+-------------+------+-----+---------+-------+
| Field          | Type        | Null | Key | Default | Extra |
+----------------+-------------+------+-----+---------+-------+
| citaat_id      | int(11)     | NO   | MUL | 0       |       |
| ldb_lemma_nr   | int(11)     | NO   |     | 0       |       |
| citaat_plaats  | varchar(42) | YES  |     | NULL    |       |
| woordvormtekst | varchar(80) | NO   |     |         |       |
| woordvolgnr    | int(11)     | NO   |     | 0       |       |
| doku_nr        | varchar(10) | NO   |     |         |       |
| woordvorm_nr   | int(11)     | NO   | MUL | 0       |       |
| bewijs_vanaf   | int(11)     | YES  |     | NULL    |       |
| is_attestatie  | int(1)      | YES  |     | 0       |       |
| onset          | int(11)     | YES  |     | NULL    |       |
| is_clitic      | int(1)      | YES  |     | 0       |       |
+----------------+-------------+------+-----+---------+-------+

 */

object VMNWdb {
  object vmnwconfig extends Configuration("vmnw", "svowdb02", "VMNW", "impact", "impact", driver="mysql")

  val mainPoS = List("vnw.bw.", "bw.", "bnw.", "lidw.", "ww.", "znw.", "vnw.", "vw.", "vz.", "az.", "telw.")

  case class QuotationReference(woordvorm_nr: Int, citaat_id: Int, ldb_lemma_nr: Int)


  case class LemmaWoordvorm(ldb_lemma_nr: Int, woordvorm_nr: Int, lemmatekst_cg: String, lemmatekst_vmnw: String,
                            woordsoort_afk: String, morfkode: Int, clitisch_deel_nr: Int, status_lemma_vmnw: Int, korte_bet: String, citaatLinks: Set[QuotationReference] = Set.empty)
  {
    lazy val mainPos: Option[String] = mainPoS.find(s => woordsoort_afk.contains(s))

    lazy val compatible: Boolean = {
      if (mainPos.isDefined) // ToDo also match features, e.g. relative pronoun etc
        {
          val ws = mainPos.get
          ws match {
            case "vnw.bw." => morfkode >= 600 & morfkode < 700
            case "bw." => morfkode >= 500 & morfkode < 600
            case "znw." => morfkode < 100
            case "ww." => morfkode >= 200 & morfkode < 300
            case "bnw." => morfkode >= 100 & morfkode < 200
            case "telw." => morfkode >= 300 & morfkode < 400
            case "vnw." => morfkode >= 400 & morfkode < 470 | morfkode >= 490 & morfkode < 500
            case "lidw." => morfkode >= 400 & morfkode < 490
            case "vz." => morfkode >= 700 & morfkode < 800
            case "az." => morfkode >= 700 & morfkode < 800 // ?
            case "vw." => morfkode >= 800 & morfkode < 900
            case _ => true
          }
        } else true;
    }

    /*
    +----------------+
| woordsoort_afk |
+----------------+
| aanw.vnw.      |
| aanw.vnw.bw.   |
| betr.vnw.      |
| betr.vnw.bw.   |
| bez.vnw.       |
| onbep.vnw.     |
| onbep.vnw.bw.  |
| pers.vnw.      |
| vnw.           |
| vnw.bw.        |
| vr.vnw.        |
| vr.vnw.bw.     |
| wdk.vnw.       |
| wkg.vnw.       |
+----------------+

+----------------+
| woordsoort_afk |
+----------------+
| aanw.vnw.bw.   |
| betr.vnw.bw.   |
| onbep.vnw.bw.  |
| vnw.bw.        |
| vr.vnw.bw.     |
+----------------+

     */
    lazy val featureCompatible = compatible &&
    {
      mainPos match {
        case Some("vnw.") =>
          val klopt = if (woordsoort_afk.contains("aanw")) morfkode >= 410 & morfkode < 420
          else if (woordsoort_afk.contains("betr")) morfkode >= 420 & morfkode < 440
          else if (woordsoort_afk.contains("bez")) morfkode >= 450 & morfkode < 460
          else if (woordsoort_afk.contains("onbep")) morfkode >= 440 & morfkode < 450
          else if (woordsoort_afk.contains("pers")) morfkode >= 401 & morfkode < 407
          else if (woordsoort_afk.contains("vr")) morfkode >= 420 & morfkode < 440
          else if (woordsoort_afk.contains("wdk")) morfkode >= 460 & morfkode < 470
          else if (woordsoort_afk.contains("wkg")) morfkode >= 460 & morfkode < 470
          else true
          klopt
        case Some("vnw.bw.") =>
          val klopt = if (woordsoort_afk.contains("aanw")) morfkode >= 610 & morfkode < 620
          else if (woordsoort_afk.contains("betr")) morfkode >= 620 & morfkode < 640
          //else if (woordsoort_afk.contains("bez")) morfkode >= 450 & morfkode < 460
          else if (woordsoort_afk.contains("onbep")) morfkode >= 640 & morfkode < 650
          else if (woordsoort_afk.contains("vr")) morfkode >= 620 & morfkode < 640
          else true
          klopt
        case Some("bw.") =>
          val klopt = if (woordsoort_afk.contains("aanw")) morfkode >= 510 & morfkode < 520
          else if (woordsoort_afk.contains("betr")) morfkode >= 520 & morfkode < 540
          //else if (woordsoort_afk.contains("bez")) morfkode >= 450 & morfkode < 460
          else if (woordsoort_afk.contains("onbep")) morfkode >= 540 & morfkode < 550
          else if (woordsoort_afk.contains("vr")) morfkode >= 520 & morfkode < 540
          else true
          klopt
        case _ => true
      }
    }

    lazy val supportedByAttestation = citaatLinks.exists(_.ldb_lemma_nr == this.ldb_lemma_nr)
    lazy val element = <ref type="dictionary.VMNW" citaatId={citaatLinks.headOption.map(x => Text(x.citaat_id.toString))} n={clitisch_deel_nr.toString} target={ldb_lemma_nr.toString}>{lemmatekst_vmnw} [{korte_bet}] ({woordsoort_afk} {morfkode} compatible:{compatible},{featureCompatible} status:{status_lemma_vmnw})</ref>
  }

  val db = new Database(vmnwconfig)

  lazy val lemmaWoordvormQuery = Select(r => LemmaWoordvorm(
    r.getInt("ldb_lemma_nr"),
    r.getInt("woordvorm_nr"),
    r.getString("lemmatekst_cg").replaceAll("-[Xx]$","").toLowerCase(),
    r.getString("lemmatekst_vmnw"),
    r.getString("woordsoort_afk"),
    r.getInt("morfkode"),
    r.getInt("clitisch_deel_nr"),
    r.getInt("status_lemma_vmnw"),
    r.getString("korte_bet")
  ), "lemma_woordvorm_view_alt")

  lazy val citaatLinkQuery = Select(r => QuotationReference(
    r.getInt("woordvorm_nr"),
    r.getInt("citaat_id"),
    r.getInt("ldb_lemma_nr")
  ),   "citaat_tokens where is_attestatie=true")

  lazy val citaatLinks = db.iterator(citaatLinkQuery)

  lazy val citaatLinkMap: Map[Int,Set[QuotationReference]] = citaatLinks.toSet.groupBy(_.woordvorm_nr)

  lazy val allemaal = db.iterator(lemmaWoordvormQuery)
  lazy val woordvormLemma = {
    val z = allemaal.toList.groupBy(_.woordvorm_nr)
    Console.err.println("Yoho links collected!!!")
    z
  }

  def prefer[T](s: Set[T], f: T => Boolean) = if (s.exists(f)) s.filter(f) else s

  def preferList[T](s0: Set[T], fs: List[T => Boolean]) = fs.foldLeft(s0)({case (s,f) => prefer[T](s,f)})

  def findDictionaryLinks(wordId: String) =
  {
    val woordvorm_nr = wordId.replaceAll("[^0-9]", "").toInt
    val candidates = woordvormLemma.get(woordvorm_nr).getOrElse(Set())

    val filtered0 = candidates.map(_.copy(citaatLinks = citaatLinkMap.getOrElse(woordvorm_nr, Set()))).toSet

    val wouldBeNice:List[LemmaWoordvorm => Boolean] =
      List(_.compatible,
        _.status_lemma_vmnw == 0,
        _.supportedByAttestation,
        _.featureCompatible)

    preferList(filtered0, wouldBeNice)
  }

  def linkXML(wordId: String) =
  {
    val l = findDictionaryLinks(wordId)
    val n = l.size
    if (l.isEmpty) <nolink/> else <xr type="dictionaryLinks" extent={n.toString}>{l.map(_.element).toSeq}</xr>
  }

  def main(args: Array[String]): Unit = {
    allemaal.filter(x => x.mainPos.isEmpty).foreach(x => println(s"$x -->  ${x.mainPos}"))
  }
}

object mapMiddelnederlandseTags extends mapMiddelnederlandseTagsClass(false)
object mapMiddelnederlandseTagsGys extends mapMiddelnederlandseTagsClass(true)

class mapMiddelnederlandseTagsClass(gysMode: Boolean) {
  val rearrangeCorresp = gysMode

  val squareCup = "⊔"

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

  val gysParticles = io.Source.fromFile("data/Gys/separates.corr.txt").getLines.map(l => l.split("\\t")).map(l => l(1) -> l(2) ).toMap

  val gysParticleLemmata = io.Source.fromFile("data/Gys/separates.corr.txt").getLines
    .map(l => l.split("\\t"))
    .map(l => {
      val lem = l(4)
      val deel = l(2)
      val lemPatched = if (deel.contains("ww") || deel.contains("bw")) lem.replaceAll("\\|","") else lem.replaceAll("\\|","-")
      l(1) -> lemPatched }  )
    .toMap

  def gysFixPartLemma(n: String):Option[String] =
  {
    gysParticleLemmata.get(n)
  }

  def morfcode2tag(morfcode: String, isPart: Boolean, n: String):String =
    {
      val s0 = morfcode.replaceAll("\\{.*", "").replaceAll("ongeanalyseerd", "999").replaceAll("[A-Za-z]","")

      val s1 =  "0" * Math.max(0, 3 - s0.length) + s0

      val pos = tagMapping.getOrElse(s1, tagMapping("999")) // s"MISSING_MAPPING($s/($s1))")

      val posAdapted = // pas op deze code werkt alleen voor CRM!!!
        if (!isPart) pos else if (!gysMode) {
          if (pos.contains("WW")) {
            if (morfcode.equals("285")) "ADV(bw-deel-ww)" else pos.replaceAll("\\)", ",hoofddeel-ww)")
          } else if (pos.contains("BW"))
            { if (morfcode.equals("655")) "BW(adv-pron,vz-deel-bw)" else pos.replaceAll("\\)", ",hoofddeel-bw)") }
          else if (pos.contains("VZ"))
            pos.replaceAll("\\)", ",vz-deel-bw)")
          else if (pos.contains("ADJ")) pos.replaceAll("\\)", ",bw-deel-ww)") // PAS OP, CHECK DEZE
          else pos
        } else { // FOUT kijken WELK stukje de part heeft (of mogelijk hebben)
          val deelSoort = gysParticles.get(n)
          if (deelSoort.isEmpty || !morfcode.contains("{"))
            {
              // Console.err.println(s"Geen deelinfo gevonden voor $n!!!!")
              pos
            } else {
            val soort = deelSoort.get
            if (pos.contains("WW")) {
              if (soort == "bw-deel-ww") "ADV(bw-deel-ww)" else pos.replaceAll("\\)", ",hoofddeel-ww)")
            } else if (pos.contains("BW")) {
              if (soort == "vz-deel-bw") "BW(adv-pron,vz-deel-bw)" else pos.replaceAll("\\)", ",hoofddeel-bw)")
            } else pos.replaceAll("\\)", "," + "deel" + ")").replaceAll("\\(,", "(")
          }
        }

      posAdapted
    }

  val morfcodeAttribuut = "@type"
  val msdAttribuut = "msd"
  val posAttribuut = "pos"
  val functionAttribuut = "function"
  val maartenVersie = false

  val wegVoorMaarten = Set(posAttribuut, functionAttribuut, msdAttribuut)
  val wegVoorNederlab = Set(msdAttribuut, functionAttribuut)

  val weg = if (maartenVersie) wegVoorMaarten else wegVoorNederlab

  def getId(e: Node) = e.attributes.filter(_.key == "id").headOption.map(_.value.text)

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

    val partPart:Option[Int] = morfcodes.zipWithIndex.find(_._1.contains("{")).map(_._2)

    val lemmata = (e \ "@lemma").text.split("\\+").toList

    // TODO aanvullen van de lemmata alleen bij scheidbare WW en ADV en directe opeenvolgingen?
    val completedLemmata = lemmata.zipWithIndex.map({case (l,i) =>
      if (partPart.map(_ == i) == Some(true)) gysFixPartLemma(n).getOrElse(l) else l
    })

    val newId = new PrefixedAttribute("xml", "id", "w." + n, Null)

    val isPartOfSomethingGreater = (e \ "@corresp").nonEmpty || morfcodes.exists(_.contains("{"))

    val cgnTags:List[String] = morfcodes.map(m => morfcode2tag(m, isPartOfSomethingGreater, n))

    val lemmataPatched = if (cgnTags.size <= lemmata.size) lemmata else {
      val d = cgnTags.size - lemmata.size
      val extra = (0 until d).map(x => "ZZZ")
      lemmata ++ extra
    }

    val completedLemmataPatched = if (cgnTags.size <= completedLemmata.size) completedLemmata else {
      val d = cgnTags.size - completedLemmata.size
      val extra = (0 until d).map(x => "ZZZ")
      completedLemmata ++ extra
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

    val withCompletedLemma = afterStm.filter(_.key != "lemma").append(Ѧ("lemma", completedLemmataPatched.mkString("+")))

    val gysTagFS = (e \ "@function").text.split("\\+").toList.map(s => CHNStyleTags.parseTag(s)).map(t => CHNStyleTags.gysTagset.asTEIFeatureStructure(t))
    val cgnTagFs = cgnTags.map(s => CGNMiddleDutch.CGNMiddleDutchTagset.asTEIFeatureStructure(s))

    def makeFS(n: Seq[Elem]) = n
      .zipWithIndex
      .map({ case (fs,i) => fs.copy(
        child=fs.child ++ <f name="lemma"><string>{completedLemmataPatched(i)}</string></f> ++
          (if (lemmataPatched(i) != completedLemmataPatched(i)) <f name="deellemma"><string>{lemmataPatched(i).replaceAll("-","")}</string></f> else Seq()),
        attributes=fs.attributes.append(Ѧ("n", i.toString) ))} )

    val featureStructures = makeFS(cgnTagFs) ++ makeFS(gysTagFS)

    val dictionaryLinks:Seq[Node] = if (gysMode) VMNWdb.linkXML(newId.value.text) else Seq[Node]()

    e.copy(attributes = withCompletedLemma, child = e.child ++ featureStructures ++ dictionaryLinks)
  }

  def show(w: Node) = s"(${w.text},${(w \ "@lemma").text},${(w \ "@msd").text}, ${sterretjeMatje(w)})"

  def queryLemma(w: Node)  = s"[lemma='${w \ "@lemma"}']"

  def replaceAtt(m: MetaData, name: String, value: String) = m.filter(a => a.key != name).append(Ѧ(name, value))

  def fixType(w: Elem): Elem = // haal de sterretjematjes uit het attribuut en maak overal drie cijfers van
  {
    val normType = (w \ "@type").text
      .replaceAll("\\{.*?\\}","")
      .split("\\+")
      .map(t => t.replaceAll("[^0-9]",""))
      .map(t => (0 until Math.max(0, 3 - t.length)).map(x => "0").mkString + t)
      .mkString("+")
    val newType = replaceAtt(w.attributes, "type", normType)
    w.copy(attributes = newType)
  }

  def fixEm(d: Elem):Elem =
    {
      val f1 = updateElement(d, _.label=="w", updateTag)


      if (rearrangeCorresp) {
        val stermatten = (f1 \\ "w").filter(x => (x \ "@corresp").nonEmpty).groupBy(e => (e \ "@corresp").text)
        stermatten.values.foreach(l => Console.err.println(l.sortBy(e => (e \ "@n").text.toInt).map(show(_))))

        val sterMatMap = stermatten.mapValues(l => l.map(x => getId(x).get))

        def newCorresp(w: Elem): Elem = {
          if ((w \ "@corresp").nonEmpty) {
            val cor = (w \ "@corresp").text
            val moi = getId(w).get

            val lesAutres = sterMatMap(cor).toSet.diff(Set(moi))

            if (lesAutres.isEmpty)
              w else {
              val newCor = lesAutres.map(x => s"#$x").mkString(" ")
              val newAtts = replaceAtt(w.attributes, "corresp", newCor)
              w.copy(attributes = newAtts)
            }
          } else w
        }

        updateElement(f1, _.label == "w", w => wrapContentInSeg(fixType(newCorresp(w))))
      } else updateElement(f1, _.label == "w", w => wrapContentInSeg(w))
    }

  def makeGroupx[T](s: Seq[T], currentGroup:List[T], f: T=>Boolean):Stream[List[T]] =
  {
    if (s.isEmpty) Stream(currentGroup)
    else if (f(s.head))
      Stream.cons(currentGroup, makeGroupx(s.tail, List(s.head), f))
    else
      makeGroupx(s.tail, currentGroup :+ s.head, f)
  }

  def makeGroup[T](s: Seq[T], f: T=>Boolean):Seq[List[T]] =
  {
    makeGroupx[T](s, List.empty, f).filter(_.nonEmpty)
  }

  def removeUselessWhite(n: Seq[(Node,Int)]) = n.filter({ case (x,i) =>  !(x.isInstanceOf[Text] && x.text.trim.isEmpty  &&  (i==0 || i == n.size-1)) })


  def wrapContentInSeg(w: Elem): Elem =
  {
    val children = w.child.zipWithIndex

    val groupedChildren = makeGroup[(Node,Int)](children, {case (c,i) => !(c.isInstanceOf[Text] || c.label == "hi" || c.label == "expan" || c.label=="c")})

    val newChildren = groupedChildren.flatMap(
      g => {

        if (g.map(_._1.text).mkString.trim.nonEmpty && g.forall( {case (c,i) => c.isInstanceOf[Text] || c.label == "hi" || c.label == "expan" || c.label == "c"})) {

          val contents = removeUselessWhite(g).map(_._1)
          val ctext:String = contents.text.trim.replaceAll("\\s+", " ")

          <seg type='orth'>{contents}</seg>

        }
        else g.map(_._1)
      }
    )
    w.copy(child = newChildren)
  }


  def fixFile(in: String, out:String) = XML.save(out, wordSplitting.splitWords(fixEm(XML.load(in))),  enc="UTF-8")

  def main(args: Array[String]) = utils.ProcessFolder.processFolder(new File(args(0)), new File(args(1)), fixFile)
}



