package corpusprocessing.GCND
import scala.xml._
import database.DatabaseUtilities.Select
import database._
import org.json4s._
import org.json4s.jackson.Serialization._
import scala.xml.PrettyPrinter
import corpusprocessing.clariah_training_corpora.moderne_tagging.lassy.conll_u.{AlpinoSentence, AlpinoToken}
import java.io.PrintWriter


object GCNDDatabase {
  lazy val pretty = new PrettyPrinter(100,4)
  val config = new Configuration(name="gcnd", server="svowdb20.ivdnt.loc", user="postgres", password="inl", database = "gcnd")
  val db = new Database(config)

  implicit lazy val serializationFormats: Formats = DefaultFormats

  case class Token(text_zv: String, text_lv: String)

  lazy val elanQ = Select(r => ElanAnnotation(
    r.getInt("elan_annotatie_id"),
    r.getInt("transcriptie_id"),
    r.getString("annotatie_code"),
    r.getInt("opname__persoon_id"),
    r.getString("tekst_lv"),
    r.getString("tekst_zv"),
    r.getInt("starttijd"),
    r.getInt("eindtijd"),
  ), "elan_annotatie")

  lazy val alpinoQ = Select(
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
      r.getInt("eindtijd")), "alpino_annotatie")

  lazy val alpinos: Seq[AlpinoAnnotation] = db.slurp(alpinoQ).sortBy(x => x.starttijd + x.eindtijd)
  lazy val elans: Seq[ElanAnnotation] = db.slurp(elanQ).sortBy(_.starttijd)

  lazy val pseudoTEI = <TEI>
    <teiHeader/>
    <text>
    <body><div>{alpinos.map(_.pseudoTEI)}</div></body>
    </text>
  </TEI>

  lazy val pseudoFolia = <FoLiA>
    <metadata src="fv701244.imdi" type="imdi" xmlns="http://ilk.uvt.nl/folia">
      <annotations>
        <pos-annotation set="hdl:1839/00-SCHM-0000-0000-000B-9"/>
        <lemma-annotation set="hdl:1839/00-SCHM-0000-0000-000E-3"/>
        <phon-annotation set="cgn"/>
        <timesegment-annotation set="cgn"/>
        <entity-annotation set="http://ilk.uvt.nl/folia/sets/frog-mwu-nl"/>
      </annotations>
    </metadata>{alpinos.map(_.pseudoFolia)}
  </FoLiA>

  def main(args: Array[String])  = {

    val out = new PrintWriter("/tmp/gcnd.test.tei.xml")
    out.println(pretty.format(pseudoTEI))
    out.close()

    val out1 = new PrintWriter("/tmp/gcnd.test.folia.xml")
    out1.println(pretty.format(pseudoFolia))
    out1.close()

    alpinos.foreach(x => {
      println(s"### ${x.starttijd} --- ${x.eindtijd} ###########")

      println(pretty.format(x.pseudoFolia))
    })
  }
}


/*

gcnd=# \d alpino_annotatie;
                                                Table "public.alpino_annotatie"
       Column        |       Type        | Collation | Nullable |                            Default
---------------------+-------------------+-----------+----------+---------------------------------------------------------------
 alpino_annotatie_id | integer           |           | not null | nextval('alpino_annotatie_alpino_annotatie_id_seq'::regclass)
 transcriptie_id     | integer           |           | not null |
 annotatie_code      | character varying |           | not null |
 opname__persoon_id  | integer           |           | not null |
 tekst_lv            | text              |           |          |
 tekst_zv            | text              |           | not null |
 alpino_xml          | text              |           | not null |
 tokens              | text              |           |          |
 starttijd           | integer           |           | not null |
 eindtijd            | integer           |           | not null |

Column       |       Type        | Collation | Nullable |                          Default
--------------------+-------------------+-----------+----------+-----------------------------------------------------------
elan_annotatie_id  | integer           |           | not null | nextval('elan_annotatie_elan_annotatie_id_seq'::regclass)
transcriptie_id    | integer           |           | not null |
annotatie_code     | character varying |           | not null |
opname__persoon_id | integer           |           |          |
tekst_lv           | text              |           |          |
tekst_zv           | text              |           | not null |
starttijd          | integer           |           | not null |
eindtijd           | integer           |           | not null |


 */