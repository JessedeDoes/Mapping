package Wp6
import scala.xml._
import utils.PostProcessXML._
import java.io.{File, PrintWriter}

import utils.PostProcessXML

import scala.collection.immutable
import scala.util.matching.Regex._


object SimplifyAndConcatenateTEI {

  // file:///home/jesse/workspace/xml2rdf/data/Missiven/7/toc.xml


  import Settings._



  def volumeNumber(f: File) = {
    val r = "_([0-9]+)_".r
    val z: Seq[String] = r.findAllIn(f.getName).toList
    //Console.err.println(z + " " + f.getName)
    z(0).replaceAll("_","").toInt
  }

  def pageNumber(f: File) : Int = {
    val r = "content_pagina-(.*)-image.tei.xml".r
    val z: Seq[String] = r.findAllIn(f.getName).toList
    //Console.err.println(z + " " + f.getName)
    val n = z(0).replaceAll("content_pagina-|-image.*","")
    if (n.matches("[0-9]+")) n.toInt else -1
  }

  def concatenatePages(n: NodeSeq): Elem = {
    <TEI>
      <text>
        <body>
        {n}
        </body>
      </text>
    </TEI>
  }

  def concat(n: (Elem,Elem)): NodeSeq = n._1 ++ n._2

  def plakFiles(): Unit = {

  }


  def loadAndInsertPageNumber(v:Integer)(f: File) = {
    val n = pageNumber(f)
    //Console.err.println("#### Page "  + n)
    val page = XML.loadFile(f)
    def insertPB(b: Elem) = b.copy(child = Seq(<pb unit='external' n={n.toString}/>) ++ b.child)
    val p1 = PostProcessXML.updateElement(page, _.label=="body", insertPB)
    val p3: Elem = PageStructure.simplifyPreTEI(MissivenPageStructure(PageStructure.zone(page), p1).basicPageStructuring())
    (p3, n)
  }


  // skip pages with just empty missiven headers

  val skipPages = List(1 -> 3, 1->16, 1->20, 1 -> 27, 1 -> 97, 1 -> 98, 1 -> 118, 1->244)

  def skipPage(v: Int, n: Int) = skipPages.contains((v,n))

  lazy val volumes: Map[Int, Elem] =
    allFiles.filter(f => doAll || volumeNumber(f) == Settings.theOneToProcess).groupBy(volumeNumber)
      .mapValues(l => l.sortBy(pageNumber).filter(pageNumber(_) >= 0))
      .map({ case (volumeNr, l) => {
        val byToc: immutable.Seq[Elem] =
          l.map(loadAndInsertPageNumber(volumeNr)).filter({case (e,k) => !skipPage(volumeNr,k)}).groupBy({ case (e, k) => MissivenMetadata.findTocItem(volumeNr, k) })
            .toList.sortBy(_._1.page).map(
            { case (t, l) => <div type="missive">
              {t.toXML}{l.flatMap(x => (x._1 \\ "body").map(_.child))}
            </div>
            }
          ).toSeq
        volumeNr -> MissivenMetadata.addHeaderForVolume(volumeNr, concatenatePages(byToc))
      }})

  val noIds = Set("hi", "lb", "teiHeader")

  def assignIds(e: Elem, prefix: String, k: Int): Elem =
  {
    val myId = s"$prefix.${e.label}.$k"

    val children: Map[String, Seq[((Node, Int), Int)]] = e.child.toList.zipWithIndex.filter(_._1.isInstanceOf[Elem]).groupBy(_._1.label).mapValues(_.zipWithIndex)

    val indexMapping: Map[Int, Int] = children.values.flatMap(x => x).map( {case ((n,i),j) => i -> j} ).toMap


    val newChildren: Seq[Node] = if (noIds.contains(e.label)) e.child else e.child.zipWithIndex.map({
      case (e1: Elem, i) => val k1 = indexMapping(i); assignIds(e1, myId, k1+1)
      case (x, y) => x
    })
    val newAtts = if (e.label == "TEI" || noIds.contains(e.label)) e.attributes else  e.attributes.filter(_.key != "id").append(new PrefixedAttribute("xml", "id", myId, Null))
    e.copy(child=newChildren, attributes = newAtts)
  }

  def createSubdivs(div: Elem) = {
    val groepjes: Seq[Seq[Node]] = PostProcessXML.groupWithFirst(div.child, x => x.label == "head")

    def possiblyMerge(x: Seq[Seq[Node]], y: Seq[Node]) = {
      if (x.isEmpty) Seq(y)
      else {
        val z = x.last
        if (z.exists(_.label == "p")) x ++ Seq(y)
        else x.take(x.size -1) ++ Seq(z ++ y)
      }
    }

    val groepjes1 = groepjes.foldLeft(Seq[Seq[Node]]())(possiblyMerge)

    if (groepjes1.count(g => g.exists(_.label=="p")) > 1) {
      div.copy(child = groepjes1.map(g => {
        <div type="subdivision_for_sake_of_validation">{g}</div>
      }))
    } else div
  }

  def stuffBeforeHead(div : Elem) = {

   val allDescendants = div.descendant.zipWithIndex.toList
   val firstHead: Option[(Node, Int)] = allDescendants.find(_._1.label == "head")
   val unPeeMe: Seq[Elem] = firstHead.map({case (n,k) => k}).map(k => allDescendants.filter({case (x,l) => k > l && x.label == "p"  && 300 > x.text.length})).getOrElse(List()).map(_._1.asInstanceOf[Elem])
   if (unPeeMe.nonEmpty)
   {
     Console.err.println(s"p before head: ${unPeeMe.text}")
     val z = PostProcessXML.updateElement(div, x => unPeeMe.contains(x), x => {  x.copy(label="fw_extra") } )
     //println(z \\ "fw_extra")
     z
   } else div
  }

  def cleanupTEI(tei: Elem, pid: String) = {
    val e0 = PostProcessXML.updateElement3(tei, e => true, e => {
      e.copy(attributes = e.attributes.filter(a => a.key != "inst" && !(e.label == "pb" && a.key=="unit")))
    })
    val e1 = updateElement(e0, _.label == "div" , createSubdivs)
    val withIds = assignIds(e1, pid, 1)
    val heads = (withIds \\ "head").map(h => PageStructure.getId(h)).toList.drop(1)
    if (heads.nonEmpty)
      {
        println(s"More thane one head: $heads in $pid")
      }
    /*
    val t1 = updateElement(withIds, x => heads.contains(PageStructure.getId(x)), x => x.copy(label="p", attributes =  x.attributes.append(
      new UnprefixedAttribute("rendition", "nonfirst_head", Null))))
    */
    //t1
    updateElement(withIds, _.label == "div" , createSubdivs)
    // withIds
  }

  def splitVolume(volumeNumber: Int, volume: Node): Unit = {
    val dir = outputDirectory + s"split/$volumeNumber"
    new File(dir).mkdir()

    val divjes = (volume \\ "div")
    divjes.foreach(
      div => {
        val id = PageStructure.getId(div)
        if (id.nonEmpty)
          {
            //Console.err.println(s"id=$id")
            val bibl = ((volume \ "teiHeader") \\ "bibl").find(x => (x \ "@inst").text == "#" + id.get)
            if (bibl.nonEmpty) {
              //Console.err.println(s"bibl=${bibl.get}")
              val header = MissivenMetadata.createHeaderForMissive(volumeNumber, bibl.get)
              val tei = <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id.get}>
                {header}
                <text>
                  <body>
                    {div}
                  </body>
                </text>
              </TEI>
              XML.save(dir + "/" + s"${id.get}.xml", cleanupTEI(tei, id.get), "utf-8")
            }
          }
      }
    )
  }

  def main(args: Array[String]): Unit = {
    // MissivenMetadata.tocItemsPerVolume(6).foreach(println)
    val testje = assignIds(<TEI xmlns="aapje"><div><p></p><p></p></div></TEI>, "xx", 1)
    XML.save("/tmp/testje.xml", testje, "utf-8")

    val u = new PrintWriter("/tmp/unused_tocitems.txt")
    volumes.par.foreach({ case (n, v) =>
        val z = MissivenMetadata.unusedTocItemsForVolume(n,v)
        u.println(s"Unused items for volume $n: ${z.size}")
        z.foreach(t => u.println("Unused: " + t.pid + " " + t.pageNumber + " " + t.title))
        XML.save(outputDirectory + s"pervolume/missiven-v$n.xml", v,"utf-8")
        splitVolume(n, v)
    })
    u.close()
  }
}
