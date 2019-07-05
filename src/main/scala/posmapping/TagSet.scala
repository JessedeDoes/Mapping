package posmapping

import propositional.Proposition

import scala.xml._
import org.json4s.JsonDSL._
import org.json4s.Xml.{toJson, toXml}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.writePretty
import posmapping.CHNStyleTags.parser
import sparql2xquery.ValueRestrictionSet



case class Feature(name: String, value: String)
{
  override def toString: String = s"$name=$value"
}

abstract class Tag(tag: String, tagset: TagSet)
{
  val pos:String
  val features:List[Feature]
  def parseFeature(f:String):Feature
  def proposition:Proposition
}


trait TagParser
{
  def parseTag(ts: TagSet, t: String): Tag
}

object  defaultTagParser extends TagParser
{
  override def parseTag(ts: TagSet, t: String): Tag = new CGNStyleTag(t, ts)
}


object MolexTagSet extends TagSet("molex")
{
  override def fromProposition(p:Proposition, posIsSpecial: Boolean = false):Tag = UDTagSet.fromProposition(p)
  override def fromString(t: String): Tag = new UDStyleTag(t, this)
}

object TagSet
{
  val pretty = new scala.xml.PrettyPrinter(300, 4)

  def fromXML(d: Elem):TagSet =
  {
    val prefix = (d \ "prefix").text
    val posTags = (d \\ "pos").map(_.text.trim).toSet.toList
    val partitions = (d \\ "partitions" \ "feature").map(f => (f \ "name").text -> (f \\ "value").map(_.text).toSet ).toMap
    val pos2partitions = (d \\ "constraints" \ "constraint").map(f => (f \ "pos").text -> (f \\ "feature").map(_.text).toList ).toMap
    TagSet(prefix, posTags, partitions, pos2partitions)
  }

  def fromXML(f: String):TagSet = fromXML(XML.load(f))

  def fromJSON(json: String) = {
    val xml = <tagset>{toXml(parse(json))}</tagset>
    println(pretty.format(xml))
  }

  def tagsetFromSetOfTags(prefix: String, tags: Set[Tag]) = {
    val posTags = tags.map(_.pos).toList
    val partitions:Map[String,Set[String]] = tags.flatMap(_.features).groupBy(_.name).mapValues(x => x.map(f => f.value))
    val pos2partitions:Map[String, List[String]] = tags.groupBy(_.pos).mapValues(l => l.flatMap(p => p.features.map(f => f.name))).mapValues(s => s.toList)
    val valueRestrictions:List[ValueRestriction] =
      tags.groupBy(_.pos).flatMap(
        { case (p,thisPos) => {
          val features = thisPos.flatMap(_.features.toSet.toList)
          features.groupBy(_.name).mapValues(f => f.map(_.value)).map({case (fn,l) => ValueRestriction(p, fn, l.toList)})

        } }
      ).toList
    Console.err.println(valueRestrictions)
    TagSet(prefix, posTags, partitions, pos2partitions, parser, List(), valueRestrictions)
  }

  def main(args: Array[String]): Unit = {
    val test = "data/CRM/tagset.json"
    fromJSON(io.Source.fromFile(test).getLines().mkString("\n"))
  }
}

case class PartitionCondition(pos: String, featureName: String, conditions: List[String])
case class ValueRestriction(pos: String, featureName: String, values: List[String])

case class TagSet(prefix: String,
                  val posTags: List[String] = List.empty,
                  partitions:Map[String,Set[String]] = Map.empty,
                  pos2partitions: Map[String,List[String]] = Map.empty, parser: TagParser=defaultTagParser,
                  partitionConditions: List[PartitionCondition] = List.empty,
                  valueRestrictions: List[ValueRestriction] = List.empty)
{

  def toXML =
    <tagset>
      <prefix>{prefix}</prefix>
      <mainPoS>
        {posTags.map(p => <pos>{p}</pos>)}
      </mainPoS>
      <partitions>
        {partitions.map( { case (f, vs) => <feature><name>{f}</name><values>{vs.map(v => <value>{v}</value>)}</values></feature>} )}
      </partitions>
      <constraints>
        {pos2partitions.map( { case (p, fs) => <constraint><pos>{p}</pos><features>{fs.map(f => <feature>{f}</feature>)}</features></constraint>} )}
      </constraints>
    </tagset>

  def elem(label: String, child: Seq[Node]) = Elem(null, label, Null, scala.xml.TopScope, child: _*)

  def blacklabName(n: String) = n.replaceAll("[-.]", "_")

  def asJSON =
  {

    import org.json4s.Extraction

    def encodeJson(src: Any): JValue = {
      import org.json4s.{ Extraction, NoTypeHints }
      import org.json4s.JsonDSL.WithDouble._
      import org.json4s.jackson.Serialization
      implicit val formats = Serialization.formats(NoTypeHints)
      Extraction.decompose(src)
    }

    def pos2J(p: String) =
      p -> Map(
        "value" -> p,
        "displayName" -> p,
        "subAnnotationIds" -> pos2partitions(p).filter(_ != "pos").map(p => prefix + "_" + blacklabName(p)))

    val values = "values" -> posTags.map(pos2J).toMap
    val json = encodeJson(values)
    pretty(render(json))

    val subannotations = partitions.map({case (f, vals) => s"${prefix}_$f" -> Map("id" -> s"${prefix}_$f",
      "values" -> vals.toList.sorted.map(v => Map("value" -> v, "displayName" -> v, "pos" -> this.posForFeature(f,v)))).toMap
    })

    val JSON =
      Map("values" -> posTags.toList.sorted.map(pos2J).toList.sortBy(_._1).toMap, // hier stond nog een toMap bij??
       "subAnnotations" -> subannotations)


    pretty(render(encodeJson(JSON)))
    //pretty(render(toJson(xml)))
  }

  def asPlainText =
    s"""
       |[features]
       |${partitions.map( {case (f,vs) => s"$f=${vs.mkString(", ")}" } ).mkString("\n")}
       |
       |[constraints]
       |${pos2partitions.map( {case (p,fs) => s"$p=${fs.mkString(", ")}" } ).mkString("\n")}
     """.stripMargin

  def forBlacklab = partitions.keySet.map( p =>
    s"""
       |      - name: ${blacklabName(p)}
       |        displayName: $p
       |        uiType: select
       |        multipleValues: true
     """.stripMargin).mkString("\n")

  def inSubsets(f:String):List[String] = partitions.filter({ case (s, v) => v.contains(f) }).toList.map(_._1)

  def fromPropositionCGN(p:Proposition, posIsSpecial: Boolean = false):Tag =
  {
    val vars = p.varsIn

    val pos = vars.find(v => v.startsWith(s"${this.prefix}:pos=")).getOrElse("UNK").replaceAll(".*=", "")
    val features = vars.filter(f => !f.startsWith(s"${this.prefix}:pos=")).toList
      .sortBy(f => {
        val n = f.replaceAll(s"^$prefix:feat.|=.*","")
        //Console.err.println(v)
        val l = pos2partitions(pos)
        val i = l.zipWithIndex.find({case (s,i) => s == n}).map({case (s,i) => i})
        i.getOrElse(0)
      })
      .map(v => v.replaceAll(s"^.*=", "")).mkString(",")

    var x = new CGNStyleTag(s"$pos($features)", this)
    //Console.err.println(s"Proposition to tag: $p->$x")
    x
  }

  def fromProposition(p:Proposition, posIsSpecial: Boolean = false):Tag = fromPropositionCGN(p,posIsSpecial)


  def featuresFromProposition(p:Proposition): Set[Feature] =
  {
    val vars = p.varsIn
    val features = vars.map(v => v.replaceAll(s"^${this.prefix}:", "")).map(x => {val r = x.split("="); Feature(r(0), r(1))})
    features
  }

  def fromString(t: String):Tag = parser.parseTag(this,t)

  def asTEIFeatureStructure(s: String):Elem = asTEIFeatureStructure(fromString(s))

  def asTEIFeatureStructure(t:Tag):Elem = <fs type={prefix}>
    {t.features.map(f => <f name={f.name}><symbol value={f.value}/></f>)}
  </fs>

  def consistentPartitions(pos: String, n: String, f: List[String]) = true

  def posForFeature(n: String, v: String) =
    {

      val r= valueRestrictions.filter(r => r.featureName == n && r.values.contains(v)).map(_.pos)
      Console.err.println(s"Pos for $n $v: $r")
      r
    }
}
