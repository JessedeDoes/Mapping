package corpusprocessing.clariah_training_corpora
import scala.xml._
import fixTokenization.{bartje, getId}

import java.io.{File, PrintWriter}
import utils.{PostProcessXML, ProcessFolder}

import java.util.zip.GZIPOutputStream

// {"id":"0","tokens":["@paulwalk","It","'s","the","view","from","where","I","'m","living","for","two","weeks",".","Empire","State","Building","=","ESB",".","Pretty","bad","storm","here","last","evening","."],"ner_tags":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,7,8,8,0,7,0,0,0,0,0,0,0,0]}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write



trait tei_to_huggingface_trait {
  trait Sentence {
    def toTSV() : String = {
      this match {
        case s:BasicSentence => {
          s"#### ${s.file} ####\n" +
          s.tokens.indices.map(i => List(s.tokens(i), s.tags(i), s.lemmata(i)).mkString("\t")).mkString("\n")
        }
        case s:PimpedSentence => {
          s"#### ${s.file} ####\n" +
            s.tokens.indices.map(i => List(s.tokens(i), s.tags(i), s.lemmata(i)).mkString("\t")).mkString("\n")
        }
      }
    }
  }
  case class PimpedSentence(
                       id: String,
                       tokens: List[String],
                       tags: List[String],
                       lemmata: List[String],
                       xml_ids: List[String]  = List(),
                       relevances: List[String]  = List(),
                       hilex_pos : List[String]  = List(),
                       file: String = "unknown",
                       partition: String = "unknown"// pas op andere dingen hebben dit niet
                     ) extends Sentence

  case class BasicSentence(
                             id: String,
                             tokens: List[String],
                             tags: List[String],
                             lemmata: List[String],
                             xml_ids: List[String] = List(),
                             file: String = "unknown",
                           ) extends Sentence
  val sentence_element="q"
  val pos_attribute = "@pos"
  val chunkSize= 50
  val default_test_train_rate = 0.2
  val split_test_train_on_document_level = false
  val output_folder= "/tmp"
  val output_prefix = "tei_to_huggingface"
  val enhance : Boolean = false
  val addStuff: Boolean = false

  implicit val formats = DefaultFormats

  def sentence(s: Node, f: String): Sentence = {

    def getN(n: Node) =  (n \ "@n").text

    val tokenElements = s.descendant.toList.filter(n => Set("w", "pc").contains(n.label))
    val indexedTokenElements = tokenElements.zipWithIndex
    val tokens = tokenElements.map(x => if ( (x \\ "seg").nonEmpty) (x \\ "seg").text  else x.text.trim)
    val tags = tokenElements.map(x => (x \ pos_attribute).headOption.getOrElse(x \ "@type").text.trim)
    val lemmata = tokenElements.map(x => (x \ "@lemma").headOption.map(_.text.trim).getOrElse(""))
    val relevances =  tokenElements.map(x => (x \ "@sense-id").nonEmpty).map(x => if (x) "yes" else "no")
    val hilex_pos = tokenElements.map(x => (x \ "@hilex-pos").headOption.map(_.text.trim).getOrElse("unk"))

    //System.err.println(relevances)

    val xml_ids =  tokenElements.map(x => getId(x))

    def enhancePos(w: Node, i: Int) = {
      val p =  (w \ "@pos").headOption.getOrElse(w \ "@type").text.trim
      if ((w \ "@type").text=="multiw") {
        println(w)
        val n = getN(w)
        val hasPrev = indexedTokenElements.exists({case (w,i1) => getN(w) == n && i1 < i })
        val hasNext = indexedTokenElements.exists({case (w,i1) => getN(w) == n && i1 > i })

        val bio =
          (hasPrev,hasNext) match {
            case (true,true) => "i"
            case (true,false) => "f"
            case (false,true) => "b"
            case _ => "o"
          }
        val t = p + "_" + bio
        println(t)
        t
      } else p
    }

    val enhancedTags = indexedTokenElements.map({case (x,y) => enhancePos(x,y)})
    val partition = (s \ "@ana").headOption.map(_.text.replaceAll("#","")).getOrElse("unknown")

    // println(s.asInstanceOf[Elem].copy(child=Seq()))
    // println(s.attributes.toString + "->" + partition)
    val r = if (addStuff)
      PimpedSentence("",tokens, if (enhance) enhancedTags else tags, lemmata, xml_ids, file=f, relevances=relevances,hilex_pos=hilex_pos,partition = partition)
    else BasicSentence("",tokens, if (enhance) enhancedTags else tags, lemmata, xml_ids, file=f)
    r
  }

  def decentSentence(s: Sentence, b: Boolean)  = true


  def Nodes2JSON(documents: Iterator[(String, Elem)], fout_train: String, fout_test: String="", sentence_element:String=sentence_element): Unit =
  {

    Console.err.println(s"Output to: $fout_test, $fout_train")
    val pwTrain = new PrintWriter(new GZIPOutputStream(new java.io.FileOutputStream(fout_train + ".train.json.gz")))
    val pwTest = new PrintWriter(new GZIPOutputStream(new java.io.FileOutputStream((if (fout_test.nonEmpty) fout_test else fout_train)  + ".test.json.gz")))

    val pwTrainTSV = new PrintWriter(new GZIPOutputStream(new java.io.FileOutputStream(fout_train + ".train.tsv.gz")))
    val pwTestTSV = new PrintWriter(new GZIPOutputStream(new java.io.FileOutputStream((if (fout_test.nonEmpty) fout_test else fout_train) + ".test.tsv.gz")))

    val esjes: Iterator[(String, Node, Boolean)] = documents.flatMap({

      case (f: String, x: Node) => {
        val doc_in_test = Math.random() < default_test_train_rate
        if ((x \\ sentence_element).nonEmpty)
          (x \\ sentence_element).iterator.map(s => (f,s,doc_in_test))
        else {
          val chunks = (x \\ "w").grouped(chunkSize).toList.map(chunk => {
            <s ana="#chunk">
              {chunk}
            </s>
          })
          chunks.iterator.map(c =>  (f,c,doc_in_test))
        }
      }
    })

    val sentences: Iterator[(Sentence,Boolean)] = esjes.map({case (f,s,is_test_doc) => sentence(s,f) -> is_test_doc}).filter({case (s,is_test_doc) => decentSentence(s,is_test_doc)})

    val sampled: Iterator[(Sentence, Boolean)] = sample(sentences)

    //val words = (d \\ "w").size
    //println("Sentences:" + s0.size  + " Words: " + words)

    val s1: Iterator[(Sentence, Boolean)] = sampled.zipWithIndex.map({
      case ((s:PimpedSentence,b),i) => s.copy(id=i.toString) -> b
      case ((s:BasicSentence,b),i) => s.copy(id=i.toString) -> b
    })

    val jsons: Iterator[(String, String, Boolean)] = s1.map({case (s,b) => (write(s), s.toTSV(), b)})

    jsons.foreach({case (json,tsv, b) => if (b)
    {
      pwTestTSV.println("")
      pwTestTSV.println(tsv)
      pwTest.println(json)
    }
    else {
      pwTrainTSV.println("")
      pwTrainTSV.println(tsv)
      pwTrain.println(json)}}
    )

    pwTest.close()
    pwTrain.close()
    pwTestTSV.close()
    pwTrainTSV.close()
  }


  def always_sampled(s: Sentence) = true

  def sample(sentences: Iterator[(Sentence,Boolean)], sample_rate: Double = 0.05, rate_test_train: Double = default_test_train_rate): Iterator[(Sentence, Boolean)] = {

    def  selected(s: Sentence) = (Math.random() < sample_rate) || always_sampled(s)

    sentences.filter({ case (s,b) => selected(s)}).map({ case (s,b) => {
      if (split_test_train_on_document_level && b || (!split_test_train_on_document_level && Math.random() < rate_test_train)) (s, true) else (s,false)
    }})
  }

  def toJSON(f: Seq[String], fout: String, preprocess: Elem=>Elem = x => x): Unit = Nodes2JSON(f.iterator.map(x => x -> preprocess(XML.load(x))), fout)

  val openDBNL = "/mnt/Projecten/Corpora/Historische_Corpora/DBNL/Tagged/"
  lazy val ideeen: Seq[String] = new java.io.File(openDBNL).listFiles().filter(_.getName.contains("mult")).toSeq.map(_.getCanonicalPath)

  val example = "data/20220421_cobalt/CobaltServeExport/docpid_1.xml"

  val BaB = "/mnt/Projecten/Corpora/Historische_Corpora/BrievenAlsBuit/2.7CHN/" // ai heeft geen zinnen..... Willekeurig aanmaken?
  val dbnl19 = "/mnt/Projecten/Corpora/Historische_Corpora/DBNL/Selectie19"
  val dbnl18 = "/mnt/Projecten/Corpora/Historische_Corpora/DBNL/Selectie18"


  val default_dataset = ideeen
  def default_process(e: Elem)  = e

  val default_folder = "hadjememaar"
  def main(args0: Array[String]): Unit = {

    val args = if (args0.size > 0) args0 else Array(default_folder)

    val filesToProcess: Seq[String] = args.toSeq.flatMap(x => {
      val f = new java.io.File(x)
      if (f.isFile) Seq(f.getCanonicalPath) else f.listFiles.toSeq.map(_.getCanonicalPath)
     })

    println(filesToProcess)
    toJSON(filesToProcess, output_folder + "/" + output_prefix, preprocess = default_process)
  }
}

object tei_to_huggingface extends tei_to_huggingface_trait {
}

object clariah_19 extends  tei_to_huggingface_trait {
  override val default_folder = "../nephomant/data/nederval/19_thomas/"
  override val output_folder = "/tmp/"
  override val output_prefix = "nederval_19"
}

object clariah_16 extends  tei_to_huggingface_trait {
  override val default_folder = "../nephomant/data/nederval/16/CobaltServeExport/"
  override val output_folder = "/tmp/"
  override val output_prefix = "nederval_16"
}

object clariah_15 extends  tei_to_huggingface_trait {
  override val default_folder = "../nephomant/data/nederval/15/CobaltServeExport/"
  override val output_folder = "/tmp/"
  override val output_prefix = "nederval_15"
}

object gtbcit_to_huggingface extends tei_to_huggingface_trait {
  val gtbCit = "/mnt/Projecten/Corpora/Historische_Corpora/Wolkencorpus/GTB/CitatenTDN2/Refurbished/"

  override  def always_sampled(s1: Sentence) = {
    val s = s1.asInstanceOf[PimpedSentence]
    s.hilex_pos.indices.exists(i => s.relevances(i) == "yes" && s.hilex_pos(i).matches(".*(PD|CON|ADP|NUM|INT)"))
  }

  def setPos(w: Elem, p:String) = w.copy(attributes =  w.attributes.append(new UnprefixedAttribute("hilex-pos", p, Null)))

  override def decentSentence(s: Sentence, b: Boolean)  = s.asInstanceOf[PimpedSentence].hilex_pos.exists(x => x != "unk")

  def propagateHilexPos(d: Elem): Elem = {
    PostProcessXML.updateElement(d,_.label=="cit", cit =>  {
      val pos = (cit \ "@pos").text
      // System.err.println(pos)
      PostProcessXML.updateElement(cit,_.label=="w", w => setPos(w,pos))
    })
  }

  override def default_process(e: Elem): Elem = propagateHilexPos(e)

}

object ofr_to_huggingface extends tei_to_huggingface_trait {
  override val pos_attribute = "@type"
  override   val default_folder = "/mnt/Projecten/Corpora/Historische_Corpora/OudFries/RitaVdPoel/corpusfiles/"
  override val split_test_train_on_document_level = true
  override def decentSentence(s: Sentence, b: Boolean)  =  {
    val tags =  s.asInstanceOf[BasicSentence].tags
    tags.count(_.nonEmpty) > 0.7 * tags.size
  }
}

object bab_to_huggingface extends tei_to_huggingface_trait {
  override val split_test_train_on_document_level: Boolean = true
  override val output_prefix: String = "bab"
  override val default_folder = "/mnt/Projecten/Corpora/Historische_Corpora/BrievenAlsBuit/2.8TDN/"
}


