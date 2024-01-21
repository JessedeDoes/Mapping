package corpusprocessing.GCND
import scala.xml._
import database.DatabaseUtilities.{AlmostQuery, Select, doeHet}
import database._
import org.json4s._
import org.json4s.jackson.Serialization._

import scala.xml.PrettyPrinter
import corpusprocessing.clariah_training_corpora.moderne_tagging.lassy.conll_u.{AlpinoSentence, AlpinoToken}

import java.io.PrintWriter
import scala.xml.dtd.DocType


case class Transcription(transcriptie_id: Int) {
  lazy val elanQ = Select(r => ElanAnnotation(
    r.getInt("elan_annotatie_id"),
    r.getInt("transcriptie_id"),
    r.getString("annotatie_code"),
    r.getInt("opname__persoon_id"),
    r.getString("tekst_lv"),
    r.getString("tekst_zv"),
    r.getInt("starttijd"),
    r.getInt("eindtijd"),
    r.getString("tokens"),
    this
  ), "elan_annotatie")

  def alpinosForTranscriptionId(id: Int) = {
    println(s"Get alpinos for transcription $id")
    val alpinoQ: Select[AlpinoAnnotation] = Select(
      r => AlpinoAnnotation(
        r.getInt("alpino_annotatie_id"),
        r.getInt("transcriptie_id"),
        r.getString("annotatie_code"),
        r.getInt("opname__persoon_id"),
        r.getString("tekst_lv"),
        r.getString("tekst_zv"),
        r.getString("alpino_xml"),
        r.getString("tokens"),
        r.getInt("starttijd"),
        r.getInt("eindtijd"), this), "alpino_annotatie where transcriptie_id=" + id)
     GCNDDatabase.db.slurp(alpinoQ).sortBy(x => x.sortKey)
  }

  lazy val alpinoAnnotations = alpinosForTranscriptionId(transcriptie_id)

  lazy val elanAnnotations = {
    val q = elanQ.copy(from = s"elan_annotatie where transcriptie_id=$transcriptie_id")
    GCNDDatabase.db.slurp(q).sortBy(x => x.starttijd + x.eindtijd)
  }

  lazy val doublyBooked: Map[Int, List[ElanAnnotation]] =
    elanAnnotations.flatMap(x => x.overLappingAlpinoAnnotations.map(a => a.alpino_annotatie_id ->x)).groupBy(_._1).toList.filter(x => x._2.size > 0).toMap.mapValues(v => v.map(_._2))

  lazy val alpinoBelongsTo: Map[Int, ElanAnnotation] = doublyBooked.mapValues(_.head)
  val allowablePairings: Set[(Int, Int)] = alpinoBelongsTo.toList.map({case (a, e) => (a, e.elan_annotatie_id)}).toSet

  println(allowablePairings)
  lazy val elanAnnotationsWithoutDoubleBookedAlpinos =
    elanAnnotations.map(e => e.copy(allowablePairs = allowablePairings))


  lazy val stillDoublyBooked: Map[Int, List[ElanAnnotation]] =
    elanAnnotationsWithoutDoubleBookedAlpinos
      .flatMap(x => x.alpinoAnnotations.map(a => a.alpino_annotatie_id -> x))
      .groupBy(_._1).toList
      .filter(x => x._2.size > 1)
      .toMap
      .mapValues(v => v.map(_._2))

  if (false && stillDoublyBooked.nonEmpty) {
    Console.err.println(s"Still multiply used alpinos: ${stillDoublyBooked.size} ${doublyBooked.size} !")
    Console.err.println(stillDoublyBooked.keySet)
    //stillDoublyBooked.foreach({case (a,e) => println(s"$a -> ${e.map(x => x.elan_annotatie_id -> x.predefinedAlpinoAnnotations.contains(a))} should just be ${alpinoBelongsTo(a).elan_annotatie_id}")})
    System.exit(1)
  }

  lazy val pseudoFoLiAForElanAnnotations =
    <FoLiA xml:id={"gcnd.transcriptie." + transcriptie_id} version="2.5.1" xmlns:folia="http://ilk.uvt.nl/folia" xmlns="http://ilk.uvt.nl/folia">
      <metadata type="internal" xmlns="http://ilk.uvt.nl/folia">
        <annotations>
          <pos-annotation set="hdl:1839/00-SCHM-0000-0000-000B-9"/>
          <lemma-annotation set="hdl:1839/00-SCHM-0000-0000-000E-3"/>
          <syntax-annotation set="lassy.syntax.annotation"/>
          <syntax-annotation set="ud.syntax.annotation"/>
          <division-annotation set="gcnd_div_classes"/>
          <timesegment-annotation set="cgn"/>
          <text-annotation set="https://raw.githubusercontent.com/proycon/folia/master/setdefinitions/text.foliaset.ttl"/>
          <token-annotation/>
          <sentence-annotation set="gcnd.sentence"/>
          <dependency-annotation set="gcnd.dependency"/>
        </annotations>
        <foreign-data>
          {Metadata.getMetadata(this)}
        </foreign-data>
      </metadata>{elanAnnotations.sortBy(_.starttijd).map(x => x.pseudoFolia)}
    </FoLiA>

  lazy val about = Map(
    "transcriptie_id" -> transcriptie_id,
    "aantal alpino-annotaties" -> alpinoAnnotations.size,
    "aantal tokens" -> (pseudoFoLiAForElanAnnotations \\ "w").size,
    "aantal tokens met PoS en lemma" -> (pseudoFoLiAForElanAnnotations \\ "pos").size,
    "aantal elan-annotaties" -> elanAnnotations.size,
    "aantal elan-annotaties van spreker" -> (pseudoFoLiAForElanAnnotations \\ "speech").filter(x => (x \ "@tag").text == "spreker").size,
    "aantal elan-annotaties van spreker die geen gealigneerde tokens krijgen"  -> ((pseudoFoLiAForElanAnnotations \\ "speech").filter(x => (x \ "@tag").text == "spreker").size - elanAnnotations.filter(_.useAlpino).size  - elanAnnotations.filter(_.useAlignment).size),
    "aantal elan-annotaties met een gekoppelde alpino annotatie" -> elanAnnotations.filter(_.useAlpino).size,
    "aantal transcripties met alpino-annotatie" -> (if (elanAnnotations.filter(_.useAlpino).size > 0) 1 else 0),
    "aantal transcripties" -> 1,
    "aantal elan-annotaties met gealigneerde tokens, zonder gekoppelde alpino-annotatie" -> elanAnnotations.filter(_.useAlignment).size)
}
